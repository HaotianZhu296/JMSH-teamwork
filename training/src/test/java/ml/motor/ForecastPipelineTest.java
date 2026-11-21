package ml.motor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForecastPipelineTest {
    @TempDir Path temp;
    static MotorReading reading(int profile,long index,double temp) {return new MotorReading(profile,index,8,2,temp,7,0,4,5,6,0,0,3,9);}
    static List<MotorReading> profile(int id,int count) {var p=new ArrayList<MotorReading>();for(int i=0;i<count;i++)p.add(reading(id,i*2L,20+i*.1));return p;}
    static Map<Integer,HorizonForecaster> models() {Map<Integer,HorizonForecaster> m=new LinkedHashMap<>();for(int h:ForecastWindows.HORIZONS)m.put(h,new BaselineForecaster(h,true));return m;}
    static RollingForecastService service() {return new RollingForecastService(models(),r->.75,DetectionConfig.experimental(.7),10,100);}
    static ForecastTelemetry input(String id,int profile,int i) {return new ForecastTelemetry(id,100000+i*1000L,reading(profile,i*2L,20+i*.1));}

    @Test void featureOrderAndCausalLookback() {
        var p=profile(1,122);float[] f=new float[13];MotorGruFeatures.fill(p,60,60,f);
        assertArrayEquals(new float[]{26,2,3,4,9,5,6,7,8,.1f,24,61,124.5f},f,1e-5f);
        assertEquals(13,MotorGruFeatures.FEATURE_NAMES.length);assertEquals("temperature_rate_10s",MotorGruFeatures.FEATURE_NAMES[9]);
        float[] expected=f.clone();p.set(61,reading(1,122,10000));MotorGruFeatures.fill(p,60,60,f);assertArrayEquals(expected,f);
        MotorGruFeatures.fill(p,60,0,f);assertEquals(0,f[9]);
        // Runtime's exact 61 rows must encode identically to full offline profile.
        for(int t=0;t<61;t++){float[] offline=new float[13],live=new float[13];MotorGruFeatures.fill(p,65,t,offline);MotorGruFeatures.fill(p.subList(5,66),60,t,live);assertArrayEquals(offline,live);}
    }
    @ParameterizedTest @ValueSource(ints={1,5,10,30,60}) void targetsMatchSeconds(int h) {
        var ref=new ForecastWindows.Ref(profile(1,121),60);assertEquals(26+h*.1,ref.target(h),1e-9);assertEquals(h*.1,ref.delta(h),1e-9);
    }
    @Test void splitNoLeakageAndReproducible() {
        Map<Integer,List<MotorReading>> data=new LinkedHashMap<>();for(int i=1;i<=69;i++)data.put(i,profile(i,121));
        var s=new ProfileSplitter().split(data,42);assertEquals(48,s.train().size());assertEquals(10,s.validation().size());assertEquals(11,s.test().size());
        assertTrue(Collections.disjoint(s.train().keySet(),s.validation().keySet()));assertTrue(Collections.disjoint(s.train().keySet(),s.test().keySet()));assertTrue(Collections.disjoint(s.validation().keySet(),s.test().keySet()));
        assertEquals(s,new ProfileSplitter().split(data,42));
    }
    @Test void everyEdgeCheckedNotOnlyEndpoints() {
        var p=profile(1,121);assertEquals(1,ForecastWindows.sample(Map.of(1,p),1,100,42).size());
        p.set(20,reading(1,39,22));assertTrue(ForecastWindows.sample(Map.of(1,p),1,100,42).isEmpty());
        assertThrows(IllegalArgumentException.class,()->ForecastWindows.validateHistory(p.subList(0,61)));
    }
    @Test void normalizationTrainingOnlyAndRoundTrip()throws Exception {
        var p=profile(1,130);var refs=List.of(new ForecastWindows.Ref(p,60),new ForecastWindows.Ref(p,65));var s=ForecastNormalizer.fit(refs,1);
        double mean=s.mean()[0];var unrelated=profile(2,130);unrelated.set(80,reading(2,160,99999));assertEquals(mean,s.mean()[0]);
        assertEquals(.3,s.restoreDelta(s.normalizeTarget(.3)),1e-7);s.save(temp,1,new Properties());var loaded=ForecastNormalizer.load(temp,1);
        assertArrayEquals(s.encode(p,60),loaded.encode(p,60));assertThrows(java.io.IOException.class,()->ForecastNormalizer.load(temp,5));
    }
    @Test void machineIsolationWarmupAndRollingTargets() {
        var s=service();ForecastEnvelope a=null;
        for(int i=0;i<61;i++){a=s.onReading(input("a",1,i));s.onReading(input("b",2,i/2));}
        assertEquals("READY",a.forecastStatus());assertEquals(61,a.historyCount());assertTrue(a.statisticalAnomaly());assertEquals("SAFE",a.currentStatus());
        var next=s.onReading(input("a",1,61));assertEquals(a.forecasts().get("60").targetTimestampMs()+1000,next.forecasts().get("60").targetTimestampMs());
        assertEquals(61,next.historyCount());assertEquals(2,s.machineCount());
        assertTrue(Double.isFinite(next.forecasts().get("1").temperatureC()));
    }
    @Test void insufficientHistoryIsNullNotZero() {
        var r=service().onReading(input("a",1,0));assertEquals("WARMING_UP",r.forecastStatus());r.forecasts().values().forEach(p->assertNull(p.temperatureC()));
    }
    @Test void profileSwitchGapInvalidAndOutOfOrderReset() {
        var s=service();for(int i=0;i<61;i++)s.onReading(input("a",1,i));
        assertEquals(1,s.onReading(input("a",2,61)).historyCount());
        assertEquals(1,s.onReading(input("a",2,63)).historyCount());
        assertEquals(1,s.onReading(new ForecastTelemetry("a",999999,reading(2,128,30))).historyCount());
        var invalid=s.onReading(new ForecastTelemetry("a",1000000,reading(2,130,Double.NaN)));assertEquals("INVALID_INPUT",invalid.forecastStatus());assertEquals(0,invalid.historyCount());
        assertEquals(1,s.onReading(input("a",2,65)).historyCount());assertEquals(1,s.onReading(input("a",2,64)).historyCount());
    }
    @Test void missingModelsAndNonFiniteOutputsUnavailable() {
        var broken=new HorizonForecaster(){public int horizon(){return 1;}public String name(){return "bad";}public double predict(List<MotorReading> h){return Double.NaN;}};
        var s=new RollingForecastService(Map.of(1,broken),null,DetectionConfig.experimental(.6),1,0);ForecastEnvelope r=null;
        for(int i=0;i<61;i++)r=s.onReading(input("a",1,i));assertEquals("UNAVAILABLE",r.forecastStatus());
        assertEquals("INFERENCE_ERROR",r.forecasts().get("1").status());assertNull(r.forecasts().get("1").temperatureC());assertNull(r.anomalyScore());
        s.onReading(input("b",2,0));assertEquals(1,s.machineCount());assertEquals(1,s.onReading(input("a",1,61)).historyCount());
    }
    @Test void longHorizonWarningPreservesCurrentCritical() {
        var s=new RollingForecastService(models(),null,new DetectionConfig(80,95,.5,80,.6),2,0);ForecastEnvelope r=null;
        for(int i=0;i<61;i++)r=s.onReading(new ForecastTelemetry("a",i*1000,reading(1,i*2,68+i*.15)));
        assertEquals("SAFE",r.currentStatus());assertEquals("WARNING",r.status());assertTrue(r.predictiveWarning());
        r=s.onReading(new ForecastTelemetry("a",61000,reading(1,122,96)));assertEquals("CRITICAL",r.status());
    }
    @Test void jsonSchemaAndNullAreExplicit() {
        String json="{\"timestampMs\":100000,\"machineId\":\"motor-1\",\"profileId\":1,\"sampleIndex\":0,\"processTemperature\":26,\"airTemperature\":3,\"rotationalSpeed\":4,\"coolantC\":2,\"torque\":9,\"iD\":5,\"iQ\":6,\"uD\":7,\"uQ\":8}";
        var t=ForecastTelemetry.parse(json);assertEquals(26,t.reading().statorWinding());
        var output=MotorForecastMqttMain.JSON.toJsonTree(service().onReading(t)).getAsJsonObject();
        assertEquals(26,output.get("currentTemperatureC").getAsDouble());assertTrue(output.getAsJsonObject("forecasts").getAsJsonObject("1").get("temperatureC").isJsonNull());
        assertEquals(101000,output.getAsJsonObject("forecasts").getAsJsonObject("1").get("targetTimestampMs").getAsLong());
        assertThrows(RuntimeException.class,()->ForecastTelemetry.parse(json.replace("\"coolantC\":2,","")));
        assertThrows(RuntimeException.class,()->ForecastTelemetry.parse(json.replace("\"sampleIndex\":0","\"sampleIndex\":0.5")));
    }
    @Test void evaluationCelsiusPercentileAndBaseline() {
        var refs=List.of(new ForecastWindows.Ref(profile(1,121),60));var m=ForecastEvaluation.metrics(refs,1,new double[]{26});
        assertEquals(.1,m.mae(),1e-10);assertEquals(.01,m.mse(),1e-10);assertEquals(1,m.within02());assertNull(m.highMae());assertEquals(50,ForecastEvaluation.improvement(.1,.2));
    }
    @Test void nativeModelCloseReloadConsistent()throws Exception {
        var refs=List.of(new ForecastWindows.Ref(profile(1,121),60));Properties cfg=new Properties();
        cfg.setProperty("hidden","4");cfg.setProperty("dense","4");cfg.setProperty("epochs","1");cfg.setProperty("patience","1");cfg.setProperty("learning.rate","0.001");
        GruHorizonModel.train(temp,refs,refs,1,42,cfg);
        assertTrue(Files.exists(temp.resolve("reload_check.csv")));
        try(var loaded=GruHorizonModel.load(temp,1)){assertTrue(Double.isFinite(loaded.predict(refs.get(0).history())));}
    }
    @Test void missingBundleIsExplicitlyUnavailable()throws Exception {
        try(var r=ForecastModelRegistry.load(temp)){assertTrue(r.models().isEmpty());assertEquals(5,r.errors().size());}
    }
}
