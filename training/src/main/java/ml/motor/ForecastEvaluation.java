package ml.motor;

import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.SeriesMarkers;
import java.nio.file.*;
import java.util.*;

/** Evaluation is always in Celsius, at identical origins, with a paired persistence comparison. */
public final class ForecastEvaluation {
    private ForecastEvaluation() {}
    public record Metrics(int count,double mae,double mse,double rmse,double p95,double within02,int highCount,Double highMae) {}
    public static Metrics metrics(List<ForecastWindows.Ref> refs,int h,double[] predictions) {
        if(refs.size()!=predictions.length || refs.isEmpty()) throw new IllegalArgumentException("Invalid evaluation size");
        double sum=0,sq=0,hi=0;int within=0,nh=0;double[] abs=new double[refs.size()];
        for(int i=0;i<abs.length;i++) {
            double y=refs.get(i).target(h),p=predictions[i];
            if(!Double.isFinite(p)) throw new IllegalArgumentException("Non-finite prediction");
            double e=p-y;abs[i]=Math.abs(e);sum+=abs[i];sq+=e*e;
            if(abs[i]<=0.2) within++;if(y>=100){hi+=abs[i];nh++;}
        }
        Arrays.sort(abs);int n=abs.length;
        return new Metrics(n,sum/n,sq/n,Math.sqrt(sq/n),abs[(int)Math.ceil(.95*n)-1],within/(double)n,nh,nh==0?null:hi/nh);
    }
    public static String header(){return "split,horizon,model,count,mae_c,mse_c2,rmse_c,p95_absolute_error_c,within_0_2_fraction,high_count,high_mae_c,baseline_mae_c,baseline_rmse_c,mae_improvement_pct,rmse_improvement_pct";}
    public static String row(String split,int h,String name,Metrics m,Metrics b) {
        return String.format(Locale.ROOT,"%s,%d,%s,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%d,%s,%.9f,%.9f,%.6f,%.6f",
                split,h,name,m.count,m.mae,m.mse,m.rmse,m.p95,m.within02,m.highCount,m.highMae==null?"":m.highMae.toString(),
                b.mae,b.rmse,improvement(m.mae,b.mae),improvement(m.rmse,b.rmse));
    }
    public static double improvement(double model,double baseline) {return baseline==0?Double.NaN:100*(baseline-model)/baseline;}
    public static void details(Path dir,String split,int h,String model,List<ForecastWindows.Ref> refs,double[] pred) throws Exception {
        Files.createDirectories(dir);String key=split+"_h"+h+"_"+model;
        List<String> lines=new ArrayList<>();lines.add("profile_id,origin_sample_index,origin_seconds,target_seconds,current_c,actual_future_c,predicted_c,error_c,baseline_error_c");
        Map<Integer,List<Integer>> byProfile=new TreeMap<>();
        for(int i=0;i<refs.size();i++) {
            var r=refs.get(i);double y=r.target(h),cur=r.now().statorWinding();
            lines.add(String.format(Locale.ROOT,"%d,%d,%.1f,%.1f,%.9f,%.9f,%.9f,%.9f,%.9f",r.now().profileId(),r.now().sampleIndex(),r.now().sampleIndex()/2.0,r.now().sampleIndex()/2.0+h,cur,y,pred[i],pred[i]-y,cur-y));
            byProfile.computeIfAbsent(r.now().profileId(),k->new ArrayList<>()).add(i);
        }
        Files.write(dir.resolve("predictions_"+key+".csv"),lines);
        List<String> per=new ArrayList<>();per.add("profile_id,count,mae_c,rmse_c,baseline_mae_c,baseline_rmse_c,mae_improvement_pct,high_count,high_mae_c");
        for(var entry:byProfile.entrySet()) {
            var subset=entry.getValue().stream().map(refs::get).toList();double[] pp=entry.getValue().stream().mapToDouble(i->pred[i]).toArray();
            Metrics m=metrics(subset,h,pp),b=metrics(subset,h,new BaselineForecaster(h,false).predictAll(subset));
            per.add(entry.getKey()+","+m.count+","+m.mae+","+m.rmse+","+b.mae+","+b.rmse+","+improvement(m.mae,b.mae)+","+m.highCount+","+(m.highMae==null?"":m.highMae));
        }
        Files.write(dir.resolve("profiles_"+key+".csv"),per);
        // Empirical signed-error histogram with overflow bins. All samples represented.
        double[] errors=new double[pred.length];
        for(int i=0;i<pred.length;i++) errors[i]=pred[i]-refs.get(i).target(h);
        errorChart(dir,key,h,errors);
        XYChart chart;
        // One contiguous profile only; never join different profiles into a curve.
        int profile=byProfile.keySet().iterator().next();var order=new ArrayList<>(byProfile.get(profile));
        order.sort(Comparator.comparingLong(i->refs.get(i).now().sampleIndex()));
        int n=Math.min(500,order.size());double[] time=new double[n],actual=new double[n],prediction=new double[n],current=new double[n];
        for(int k=0;k<n;k++){int i=order.get(k);var r=refs.get(i);time[k]=r.now().sampleIndex()/2.0+h;actual[k]=r.target(h);prediction[k]=pred[i];current[k]=r.now().statorWinding();}
        chart=chart(key+" profile "+profile,"Target time (s)","Temperature (C)");
        chart.addSeries("Actual future",time,actual);chart.addSeries("Forecast",time,prediction);chart.addSeries("Persistence",time,current);save(chart,dir.resolve("curve_"+key+".png"));
    }
    private static void errorChart(Path dir,String key,int h,double[] errors)throws Exception {
        double width=h<=5?.025:h<=10?.1:.5;
        double[] centers=new double[43],counts=new double[43];
        for(int b=0;b<43;b++) centers[b]=(b-21)*width;
        for(double e:errors) {int b=(int)Math.floor(e/width+21.5);counts[Math.max(0,Math.min(42,b))]++;}
        List<String> hist=new ArrayList<>();hist.add("bin_center_c,count,overflow");
        for(int b=0;b<43;b++) hist.add(centers[b]+","+counts[b]+","+(b==0?"lower":b==42?"upper":""));
        Files.write(dir.resolve("errors_"+key+".csv"),hist);
        XYChart chart=chart("Error distribution: "+key,"Signed error (C); end bins include overflow","Count");chart.addSeries("error",centers,counts);save(chart,dir.resolve("errors_"+key+".png"));
    }
    /** Re-render stored errors only. Does not load data/models or repeat test evaluation. */
    public static void main(String[] args)throws Exception {
        Path dir=Path.of(args[0]);
        try(var files=Files.newDirectoryStream(dir,"predictions_*.csv")) {
            for(Path csv:files) {
                String key=csv.getFileName().toString().replace("predictions_","").replace(".csv","");
                int h=Integer.parseInt(key.split("_")[1].substring(1));var lines=Files.readAllLines(csv);
                double[] errors=new double[lines.size()-1];for(int i=1;i<lines.size();i++)errors[i-1]=Double.parseDouble(lines.get(i).split(",")[7]);
                errorChart(dir,key,h,errors);
            }
        }
    }
    private static XYChart chart(String title,String x,String y){var c=new XYChartBuilder().width(1000).height(600).title(title).xAxisTitle(x).yAxisTitle(y).build();c.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);return c;}
    private static void save(XYChart c,Path path)throws Exception{for(var s:c.getSeriesCollection())s.setMarker(SeriesMarkers.NONE);ChartEncoder.saveChart(c,path.toString().replaceAll("\\.png$", ""),"png");}
    public static void lossChart(Path csv,Path png)throws Exception {
        var lines=Files.readAllLines(csv);double[] x=new double[lines.size()-1],tr=x.clone(),va=x.clone();
        for(int i=1;i<lines.size();i++){String[] c=lines.get(i).split(",");x[i-1]=Double.parseDouble(c[0]);tr[i-1]=Double.parseDouble(c[2]);va[i-1]=Double.parseDouble(c[4]);}
        var chart=chart("GRU loss: "+csv.getParent().getFileName(),"Epoch","MSE (C squared)");chart.addSeries("Train diagnostic (up to 4096)",x,tr);chart.addSeries("Validation",x,va);save(chart,png);
    }
    public static void horizonChart(Path dir,String split,List<Metrics> model,List<Metrics> base)throws Exception {
        double[] x=Arrays.stream(ForecastWindows.HORIZONS).asDoubleStream().toArray();
        var c=chart(split+": selected forecast vs persistence","Horizon (s)","MAE (C)");
        c.addSeries("Selected",x,model.stream().mapToDouble(Metrics::mae).toArray());c.addSeries("Persistence",x,base.stream().mapToDouble(Metrics::mae).toArray());save(c,dir.resolve(split+"_horizons.png"));
    }
}
