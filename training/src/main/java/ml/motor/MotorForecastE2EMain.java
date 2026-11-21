package ml.motor;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** External-broker acceptance test. Requires running MotorForecastMqttMain. No mocked model outputs. */
public final class MotorForecastE2EMain {
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: data.csv broker results-dir");
        Path out=Path.of(args[2]);Files.createDirectories(out);
        var split=new ProfileSplitter().split(new MotorDataReader().loadData(args[0],2),42);var profile=split.validation().get(79);
        BlockingQueue<JsonObject> queue=new LinkedBlockingQueue<>();List<String> trace=new ArrayList<>();List<Double> latency=new ArrayList<>();
        try(var subscriber=new MqttClient(args[1],"forecast-e2e-in",new MemoryPersistence());var publisher=new MqttClient(args[1],"forecast-e2e-out",new MemoryPersistence())) {
            subscriber.setCallback(new MqttCallback(){public void connectionLost(Throwable e){}public void deliveryComplete(IMqttDeliveryToken t){}public void messageArrived(String t,MqttMessage m){queue.add(JsonParser.parseString(new String(m.getPayload(),StandardCharsets.UTF_8)).getAsJsonObject());}});
            subscriber.connect(MotorForecastReplayMain.options("DASH_MQTT_USERNAME","DASH_MQTT_PASSWORD"));subscriber.subscribe("forecasts/+",1);
            publisher.connect(MotorForecastReplayMain.options("SIM_MQTT_USERNAME","SIM_MQTT_PASSWORD"));long start=System.currentTimeMillis();
            for(int i=0;i<65;i++) {
                long tick=System.nanoTime();
                for(String machine:List.of("e2e-a","e2e-b")) {
                    var payload=MotorForecastReplayMain.telemetry(machine,profile.get(i),start+i*1000L);
                    var r=exchange(publisher,queue,payload,trace);
                    require(r.get("historyCount").getAsInt()==Math.min(61,i+1),"History size");
                    require(r.get("forecastStatus").getAsString().equals(i<60?"WARMING_UP":"READY"),"Readiness");
                    for(int h:ForecastWindows.HORIZONS) {
                        var f=r.getAsJsonObject("forecasts").getAsJsonObject(""+h);
                        require(f.get("targetTimestampMs").getAsLong()==start+(i+h)*1000L,"Rolling target timestamp");
                        if(i<60)require(f.get("temperatureC").isJsonNull(),"Warmup must be null");else require(Double.isFinite(f.get("temperatureC").getAsDouble()),"Finite model output");
                    }
                    require(!r.get("anomalyScore").isJsonNull(),"Real Isolation Forest available");
                    if(i>=60)latency.add(r.get("inferenceLatencyMs").getAsDouble());
                }
                long remain=1000-(System.nanoTime()-tick)/1000000;if(remain>0)Thread.sleep(remain);
            }
            var bad=MotorForecastReplayMain.telemetry("e2e-a",profile.get(65),start+65000);bad.remove("coolantC");
            require(exchange(publisher,queue,bad,trace).get("forecastStatus").getAsString().equals("INVALID_INPUT"),"Invalid input clear");
            require(exchange(publisher,queue,MotorForecastReplayMain.telemetry("e2e-b",profile.get(65),start+65000),trace).get("forecastStatus").getAsString().equals("READY"),"Other machine unaffected");
            require(exchange(publisher,queue,MotorForecastReplayMain.telemetry("e2e-a",profile.get(66),start+66000),trace).get("historyCount").getAsInt()==1,"Invalid reset");
            require(exchange(publisher,queue,MotorForecastReplayMain.telemetry("e2e-b",profile.get(67),start+67000),trace).get("historyCount").getAsInt()==1,"Gap reset");
            var changed=MotorForecastReplayMain.telemetry("e2e-b",split.validation().get(3).get(0),start+68000);
            require(exchange(publisher,queue,changed,trace).get("historyCount").getAsInt()==1,"Profile reset");
            for(int i=0;i<3;i++) {double temp=new double[]{40,85,105}[i];var p=MotorForecastReplayMain.telemetry("e2e-threshold",profile.get(i),start+i*1000L);p.addProperty("processTemperature",temp);
                var r=exchange(publisher,queue,p,trace);require(r.get("currentStatus").getAsString().equals(new String[]{"SAFE","WARNING","CRITICAL"}[i]),"Thermal status");}
            publisher.disconnect();subscriber.disconnect();
        }
        Files.write(out.resolve("mqtt_trace.jsonl"),trace);Collections.sort(latency);
        Files.writeString(out.resolve("acceptance.txt"),"PASS: 65 seconds at 1 Hz, two machines, warmup, five real model outputs, rolling targets, Isolation Forest, invalid/gap/profile resets, 40/85/105 C thresholds.\nmessages="+trace.size()+"\nready_latency_median_ms="+latency.get(latency.size()/2)+"\nready_latency_max_ms="+latency.get(latency.size()-1)+"\n");
        System.out.println(Files.readString(out.resolve("acceptance.txt")));
    }
    static JsonObject exchange(MqttClient client,BlockingQueue<JsonObject> queue,JsonObject payload,List<String> trace)throws Exception {
        String id=payload.get("machineId").getAsString();client.publish("sensors/"+id+"/telemetry",payload.toString().getBytes(StandardCharsets.UTF_8),1,false);
        JsonObject result=queue.poll(10,TimeUnit.SECONDS);require(result!=null,"MQTT forecast timed out");require(id.equals(result.get("machineId").getAsString()),"Unexpected machine");trace.add(result.toString());return result;
    }
}
