package ml.motor;

import java.nio.file.*;
import java.util.*;

/** Post-hoc validation diagnostics only. Future operating changes are NEVER model inputs. */
public final class ForecastDiagnosticsMain {
    public static void main(String[] args)throws Exception {
        Path run=Path.of(args[1]);var split=new ProfileSplitter().split(new MotorDataReader().loadData(args[0],2),42);
        var refs=ForecastWindows.sample(split.validation(),5,8000,43);
        List<String> bootstrap=new ArrayList<>(),regimes=new ArrayList<>();
        bootstrap.add("horizon,profiles,bootstrap_repetitions,seed,mae_gain_c_low95,mae_gain_c_high95,interpretation");
        regimes.add("horizon,regime,count,mae_c,baseline_mae_c,mean_abs_future_speed_change_rpm,mean_abs_future_torque_change");
        try(var registry=ForecastModelRegistry.load(run)) {
            for(int h:ForecastWindows.HORIZONS) {
                double[] pred=registry.models().get(h).predictAll(refs);Map<Integer,double[]> clusters=new TreeMap<>();Map<String,double[]> groups=new TreeMap<>();
                for(int i=0;i<refs.size();i++) {
                    var r=refs.get(i);double err=Math.abs(pred[i]-r.target(h)),base=Math.abs(r.delta(h));
                    double[] c=clusters.computeIfAbsent(r.now().profileId(),id->new double[2]);c[0]+=base-err;c[1]++;
                    var future=r.profile().get(r.index()+h);double speed=Math.abs(future.motorSpeed()-r.now().motorSpeed()),torque=Math.abs(future.torque()-r.now().torque());
                    String label=speed>=500||torque>=20?"future_load_change":"future_load_stable";
                    double[] g=groups.computeIfAbsent(label,k->new double[5]);g[0]++;g[1]+=err;g[2]+=base;g[3]+=speed;g[4]+=torque;
                }
                // Resample whole profiles rather than treating overlapping windows as independent.
                var cs=new ArrayList<>(clusters.values());Random random=new Random(4242);double[] gains=new double[2000];
                for(int b=0;b<gains.length;b++){double sum=0,n=0;for(int j=0;j<cs.size();j++){double[] c=cs.get(random.nextInt(cs.size()));sum+=c[0];n+=c[1];}gains[b]=sum/n;}
                Arrays.sort(gains);bootstrap.add(h+","+cs.size()+",2000,4242,"+gains[49]+","+gains[1949]+",positive means improvement over persistence");
                for(var e:groups.entrySet()){double[] g=e.getValue();regimes.add(h+","+e.getKey()+","+(int)g[0]+","+g[1]/g[0]+","+g[2]/g[0]+","+g[3]/g[0]+","+g[4]/g[0]);}
            }
        }
        Files.write(run.resolve("persistence_bootstrap.csv"),bootstrap);Files.write(run.resolve("validation_regimes.csv"),regimes);
    }
}
