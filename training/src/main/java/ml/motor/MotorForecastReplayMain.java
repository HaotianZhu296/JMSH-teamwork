package ml.motor;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real validation-profile replay; source timestamps advance at 1 Hz even in accelerated test mode. */
public final class MotorForecastReplayMain {
    public static JsonObject telemetry(String machine,MotorReading r,long timestamp) {
        JsonObject o=new JsonObject();o.addProperty("machineId",machine);o.addProperty("timestampMs",timestamp);o.addProperty("sensorType","electric_motor");
        o.addProperty("profileId",r.profileId());o.addProperty("sampleIndex",r.sampleIndex());o.addProperty("processTemperature",r.statorWinding());
        o.addProperty("airTemperature",r.ambient());o.addProperty("coolantC",r.coolant());o.addProperty("rotationalSpeed",r.motorSpeed());o.addProperty("torque",r.torque());
        o.addProperty("iD",r.iD());o.addProperty("iQ",r.iQ());o.addProperty("uD",r.uD());o.addProperty("uQ",r.uQ());o.addProperty("toolWear",0);return o;
    }
    public static MqttConnectOptions options(String usernameEnv,String passwordEnv) {
        var o=new MqttConnectOptions();o.setCleanSession(true);String user=System.getenv(usernameEnv);if(user!=null){o.setUserName(user);o.setPassword(System.getenv().getOrDefault(passwordEnv,"").toCharArray());}return o;
    }
    public static void main(String[] args)throws Exception {
        if(args.length<2)throw new IllegalArgumentException("Usage: data.csv broker [count=180] [interval-ms=1000]");
        var split=new ProfileSplitter().split(new MotorDataReader().loadData(args[0],2),42);var p=split.validation().get(79);
        int count=args.length>2?Integer.parseInt(args[2]):180,interval=args.length>3?Integer.parseInt(args[3]):1000;
        try(var client=new MqttClient(args[1],"forecast-real-replay-"+UUID.randomUUID(),new MemoryPersistence())) {
            client.connect(options("SIM_MQTT_USERNAME","SIM_MQTT_PASSWORD"));long start=System.currentTimeMillis();
            for(int i=0;i<Math.min(count,p.size());i++) {String json=telemetry("motor-live",p.get(i),start+i*1000L).toString();client.publish("sensors/motor-live/telemetry",json.getBytes(StandardCharsets.UTF_8),1,false);if(interval>0)Thread.sleep(interval);}
            client.disconnect();
        }
    }
}
