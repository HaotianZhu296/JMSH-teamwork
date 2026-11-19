package ml.motor;

import com.google.gson.*;

/** Adapter for the team's existing TelemetryJson schema plus the compact ML schema. */
public record ForecastTelemetry(String machineId,long timestampMs,MotorReading reading) {
    public static ForecastTelemetry parse(String json) {
        JsonObject o=JsonParser.parseString(json).getAsJsonObject();
        String machine=o.get("machineId").getAsString();
        if(!machine.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("Invalid machineId");
        int profile=Math.toIntExact(integer(o,"profileId"));long sample=integer(o,"sampleIndex"),time=integer(o,"timestampMs");
        if(sample<0||time<0||time>Long.MAX_VALUE-60000)throw new IllegalArgumentException("Invalid time/index");
        MotorSensorMessage m=new MotorSensorMessage(machine,profile,sample,number(o,"statorWinding","processTemperature"),number(o,"coolant","coolantC"),
                number(o,"ambient","airTemperature"),number(o,"motorSpeed","rotationalSpeed"),number(o,"torque","torque"),
                number(o,"iD","i_d"),number(o,"iQ","i_q"),number(o,"uD","u_d"),number(o,"uQ","u_q"));
        return new ForecastTelemetry(machine,time,m.toMotorReading());
    }
    private static long integer(JsonObject o,String name) {return o.get(name).getAsBigDecimal().longValueExact();}
    private static double number(JsonObject o,String name,String alias) {
        JsonElement e=o.has(name)?o.get(name):o.get(alias);
        if(e==null||e.isJsonNull()||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Missing/non-numeric "+name);
        double n=e.getAsDouble();if(!Double.isFinite(n))throw new IllegalArgumentException("Invalid "+name);return n;
    }
}
