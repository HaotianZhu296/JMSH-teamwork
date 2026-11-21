package ml.motor;

import java.util.*;
import java.util.function.ToDoubleFunction;

/** Bounded per-machine histories; no fitting or disk IO in the hot path. */
public final class RollingForecastService {
    private static final class State {final Deque<MotorReading> history=new ArrayDeque<>();long timestamp;}
    private final LinkedHashMap<String,State> machines=new LinkedHashMap<>(16,.75f,true);
    private final Map<Integer,HorizonForecaster> models;
    private final ToDoubleFunction<MotorReading> anomaly;
    private final DetectionConfig config;
    private final CurrentThermalDetector thermal;
    private final int maxMachines;
    private final long timestampToleranceMs;
    public RollingForecastService(Map<Integer,HorizonForecaster> models,ToDoubleFunction<MotorReading> anomaly,DetectionConfig config,int maxMachines,long toleranceMs) {
        if(maxMachines<1||toleranceMs<0||toleranceMs>=500)throw new IllegalArgumentException("Invalid history configuration");
        this.models=Map.copyOf(models);this.anomaly=anomaly;this.config=config;this.maxMachines=maxMachines;this.timestampToleranceMs=toleranceMs;
        models.forEach((h,m)->{ForecastWindows.requireHorizon(h);if(m.horizon()!=h)throw new IllegalArgumentException("Horizon/model mismatch");});
        thermal=new CurrentThermalDetector(config.warningTemperatureC(),config.criticalTemperatureC(),config.maxRiseRateCPerSecond());
    }
    public synchronized void reset(String machineId){machines.remove(machineId);}
    public synchronized int machineCount(){return machines.size();}
    public synchronized ForecastEnvelope onReading(ForecastTelemetry input) {
        long start=System.nanoTime();String id=input.machineId();MotorReading r=input.reading();
        if(id==null||!id.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("Invalid machineId");
        List<String> reasons=new ArrayList<>();Map<String,ForecastEnvelope.Point> points=new LinkedHashMap<>();
        if(!ForecastWindows.finite(r)||input.timestampMs()<0||input.timestampMs()>Long.MAX_VALUE-60000||r.sampleIndex()<0) {
            reset(id);return unavailable(input,"INVALID_INPUT",0,"Invalid sensor input; history cleared",start);
        }
        State s=machines.get(id);
        if(s==null) {
            if(machines.size()>=maxMachines)machines.remove(machines.keySet().iterator().next());
            s=new State();machines.put(id,s);
        }
        MotorReading previous=s.history.peekLast();
        if(previous!=null) {
            long dt=input.timestampMs()-s.timestamp;
            // Exact duplicate QoS1 packets do not advance or destroy history.
            if(dt==0&&previous.equals(r))return unavailable(input,"DUPLICATE",s.history.size(),"Duplicate ignored",start);
            if(!ForecastWindows.continuous(previous,r)||Math.abs(dt-1000)>timestampToleranceMs) {
                s.history.clear();previous=null;reasons.add("History reset: profile, index or timestamp discontinuity");
            }
        }
        s.history.addLast(r);if(s.history.size()>61)s.history.removeFirst();s.timestamp=input.timestampMs();
        String currentStatus=thermal.detect(previous,r);Double score=null;Boolean anomalous=null;
        if(anomaly!=null) try {
            score=anomaly.applyAsDouble(r);if(!Double.isFinite(score))throw new IllegalStateException("Invalid anomaly score");
            anomalous=score>config.anomalyThreshold();
        } catch(RuntimeException e) {score=null;reasons.add("Anomaly model unavailable: "+e.getClass().getSimpleName());}
        boolean warning=false;int available=0;
        for(int h:ForecastWindows.HORIZONS) {
            var model=models.get(h);Double predicted=null;String state=s.history.size()<61?"WARMING_UP":model==null?"MODEL_UNAVAILABLE":"READY";
            if(state.equals("READY")) try {
                predicted=model.predict(List.copyOf(s.history));
                if(!Double.isFinite(predicted))throw new IllegalStateException("Non-finite output");available++;
                if(h>=30&&r.statorWinding()<config.warningTemperatureC()&&predicted>=config.futureWarningTemperatureC())warning=true;
            } catch(RuntimeException e) {predicted=null;state="INFERENCE_ERROR";reasons.add("h"+h+": "+e.getClass().getSimpleName());}
            points.put(""+h,new ForecastEnvelope.Point(h,input.timestampMs()+h*1000L,predicted,model==null?null:model.name(),state));
        }
        String overall=currentStatus;if(warning&&"SAFE".equals(overall))overall="WARNING";
        if(warning)reasons.add("30/60s forecast crosses configured early-warning threshold");
        if(Boolean.TRUE.equals(anomalous))reasons.add("Isolation Forest detected unusual operating state");
        String fs=s.history.size()<61?"WARMING_UP":available==5?"READY":available==0?"UNAVAILABLE":"PARTIAL";
        return new ForecastEnvelope(1,id,r.profileId(),r.sampleIndex(),input.timestampMs(),r.statorWinding(),currentStatus,overall,score,anomalous,warning,fs,s.history.size(),points,reasons,(System.nanoTime()-start)/1e6);
    }
    public ForecastEnvelope invalid(String id,long timestamp,String reason) {
        reset(id);Map<String,ForecastEnvelope.Point> points=new LinkedHashMap<>();
        for(int h:ForecastWindows.HORIZONS)points.put(""+h,new ForecastEnvelope.Point(h,timestamp+h*1000L,null,null,"INVALID_INPUT"));
        return new ForecastEnvelope(1,id,-1,-1,timestamp,null,"UNAVAILABLE","UNAVAILABLE",null,null,false,"INVALID_INPUT",0,points,List.of(reason),0);
    }
    private ForecastEnvelope unavailable(ForecastTelemetry t,String status,int count,String reason,long start) {
        Map<String,ForecastEnvelope.Point> points=new LinkedHashMap<>();
        for(int h:ForecastWindows.HORIZONS)points.put(""+h,new ForecastEnvelope.Point(h,t.timestampMs()+h*1000L,null,null,status));
        Double current=Double.isFinite(t.reading().statorWinding())?t.reading().statorWinding():null;
        return new ForecastEnvelope(1,t.machineId(),t.reading().profileId(),t.reading().sampleIndex(),t.timestampMs(),current,"UNAVAILABLE","UNAVAILABLE",null,null,false,status,count,points,List.of(reason),(System.nanoTime()-start)/1e6);
    }
}
