package ml.motor;
import java.util.*;

/** Additive MQTT schema. Null means unavailable; never substitute 0 C. */
public record ForecastEnvelope(int schemaVersion,String machineId,int profileId,long sampleIndex,long originTimestampMs,
                               Double currentTemperatureC,String currentStatus,String status,
                               Double anomalyScore,Boolean statisticalAnomaly,boolean predictiveWarning,
                               String forecastStatus,int historyCount,Map<String,Point> forecasts,List<String> reasons,double inferenceLatencyMs) {
    public record Point(int horizonSeconds,long targetTimestampMs,Double temperatureC,String model,String status) {}
    public ForecastEnvelope {forecasts=Collections.unmodifiableMap(new LinkedHashMap<>(forecasts));reasons=List.copyOf(reasons);}
}
