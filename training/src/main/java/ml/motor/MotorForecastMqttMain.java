package ml.motor;

import com.google.gson.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.ToDoubleFunction;

/** Runs alongside the existing Spring detector; publishes forecasts, never rewrites telemetry or alerts. */
public final class MotorForecastMqttMain {
    public static final Gson JSON=new GsonBuilder().serializeNulls().create();
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: MotorForecastMqttMain config.properties");
        Properties p=new Properties();try(var r=Files.newBufferedReader(Path.of(args[0]))){p.load(r);}
        try(var registry=ForecastModelRegistry.load(Path.of(p.getProperty("models.directory")))) {
            ToDoubleFunction<MotorReading> anomaly=null;double threshold=Double.parseDouble(p.getProperty("anomaly.threshold","0.6"));
            if(p.containsKey("legacy.models.directory")) try {
                var legacy=MotorModelStore.load(Path.of(p.getProperty("legacy.models.directory")));
                anomaly=r->legacy.anomalyModel().score(r.anomalyFeatures());threshold=legacy.config().anomalyThreshold();
            } catch(Exception e) {System.err.println("Legacy anomaly model unavailable: "+e.getClass().getSimpleName());}
            var cfg=new DetectionConfig(Double.parseDouble(p.getProperty("warning.temperature","80")),Double.parseDouble(p.getProperty("critical.temperature","95")),
                    Double.parseDouble(p.getProperty("max.rise.rate","0.5")),Double.parseDouble(p.getProperty("future.warning.temperature","80")),threshold);
            var service=new RollingForecastService(registry.models(),anomaly,cfg,Integer.parseInt(p.getProperty("max.machines","128")),Long.parseLong(p.getProperty("timestamp.tolerance.ms","250")));
            System.out.println("Cached models="+registry.models().keySet()+" unavailable="+registry.errors()+" anomaly="+(anomaly!=null));
            String broker=p.getProperty("mqtt.broker","tcp://127.0.0.1:1883"),client=p.getProperty("mqtt.client.id","motor-forecast");
            var options=new MqttConnectOptions();options.setAutomaticReconnect(true);options.setCleanSession(true);options.setConnectionTimeout(10);
            String user=System.getenv("FORECAST_MQTT_USERNAME"),password=System.getenv("FORECAST_MQTT_PASSWORD");
            if(user!=null&&!user.isBlank()){options.setUserName(user);options.setPassword(password==null?new char[0]:password.toCharArray());}
            try(var sender=new MqttClient(broker,client+"-out",new MemoryPersistence());var receiver=new MqttClient(broker,client+"-in",new MemoryPersistence())) {
                sender.connect(options);
                receiver.setCallback(new MqttCallbackExtended() {
                    public void connectComplete(boolean reconnect,String uri){try{receiver.subscribe("sensors/+/telemetry",1);}catch(MqttException e){System.err.println("Subscription failed: "+e);}}
                    public void connectionLost(Throwable e){System.err.println("MQTT disconnected; histories cleared on timestamp gaps");}
                    public void deliveryComplete(IMqttDeliveryToken token){}
                    public void messageArrived(String topic,MqttMessage message)throws Exception {
                        String[] parts=topic.split("/");String machine=parts.length==3?parts[1]:"";
                        if(!machine.matches("[A-Za-z0-9_.-]{1,128}"))return;
                        ForecastEnvelope result;
                        try {
                            var reading=ForecastTelemetry.parse(new String(message.getPayload(),StandardCharsets.UTF_8));
                            if(!machine.equals(reading.machineId()))throw new IllegalArgumentException("Topic/payload machine mismatch");
                            result=service.onReading(reading);
                            if(result.forecastStatus().equals("DUPLICATE"))return;
                        } catch(RuntimeException e){result=service.invalid(machine,System.currentTimeMillis(),"Invalid telemetry: "+e.getClass().getSimpleName());}
                        // Separate publishing client avoids QoS1 callback deadlock. Non-retained prevents stale startup forecasts.
                        sender.publish("forecasts/"+machine,JSON.toJson(result).getBytes(StandardCharsets.UTF_8),1,false);
                        System.out.printf(Locale.ROOT,"%s index=%d history=%d %s %.3fms%n",machine,result.sampleIndex(),result.historyCount(),result.forecastStatus(),result.inferenceLatencyMs());
                    }
                });
                var stop=new CountDownLatch(1);Runtime.getRuntime().addShutdownHook(new Thread(stop::countDown));
                receiver.connect(options);System.out.println("Forecast MQTT service ready");stop.await();
                receiver.disconnect();sender.disconnect();
            }
        }
    }
}
