package ml.motor;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Fitted exclusively on the sampled training windows, shared by training and inference. */
public record ForecastNormalizer(double[] mean, double[] std, double targetMean, double targetStd) {
    public ForecastNormalizer {
        mean = mean.clone(); std = std.clone();
        if (mean.length != 13 || std.length != 13 || !Double.isFinite(targetMean) || !Double.isFinite(targetStd) || targetStd <= 0)
            throw new IllegalArgumentException("Invalid normalization");
        for (int j=0;j<13;j++) if (!Double.isFinite(mean[j]) || !Double.isFinite(std[j]) || std[j]<=0)
            throw new IllegalArgumentException("Invalid feature normalization");
    }
    public static ForecastNormalizer fit(List<ForecastWindows.Ref> train, int horizon) {
        if (train.isEmpty()) throw new IllegalArgumentException("Empty training set");
        double[] mean=new double[13], m2=new double[13], std=new double[13];
        float[] f=new float[13]; long n=0; double tm=0, t2=0; int tn=0;
        for (var ref : train) {
            for(int t=0;t<61;t++) {
                MotorGruFeatures.fill(ref.profile(),ref.index(),t,f); n++;
                for(int j=0;j<13;j++) { double d=f[j]-mean[j]; mean[j]+=d/n; m2[j]+=d*(f[j]-mean[j]); }
            }
            double y=ref.delta(horizon), d=y-tm; tm+=d/++tn; t2+=d*(y-tm);
        }
        for(int j=0;j<13;j++) std[j]=Math.max(1e-6,Math.sqrt(m2[j]/n));
        return new ForecastNormalizer(mean,std,tm,Math.max(1e-6,Math.sqrt(t2/tn)));
    }
    public float[] encode(List<MotorReading> p, int end) {
        float[] x=new float[61*13], f=new float[13];
        for(int t=0;t<61;t++) {
            MotorGruFeatures.fill(p,end,t,f);
            for(int j=0;j<13;j++) {
                x[t*13+j]=(float)((f[j]-mean[j])/std[j]);
                if (!Float.isFinite(x[t*13+j])) throw new IllegalArgumentException("Invalid normalized feature");
            }
        }
        return x;
    }
    public float normalizeTarget(double delta) { return (float)((delta-targetMean)/targetStd); }
    public double restoreDelta(double normalized) { return normalized*targetStd+targetMean; }
    public void save(Path dir, int horizon, Properties config) throws IOException {
        Properties p=new Properties(); p.putAll(config);
        p.setProperty("schema.version","rolling-v1"); p.setProperty("target","future_minus_current");
        p.setProperty("forecast.seconds",""+horizon); p.setProperty("window.size","61"); p.setProperty("sampling.hz","1");
        p.setProperty("feature.count","13"); p.setProperty("feature.names",String.join(",",MotorGruFeatures.FEATURE_NAMES));
        p.setProperty("target.mean",""+targetMean); p.setProperty("target.std",""+targetStd);
        for(int j=0;j<13;j++) { p.setProperty("feature.mean."+j,""+mean[j]); p.setProperty("feature.std."+j,""+std[j]); }
        Files.createDirectories(dir);
        try(var w=Files.newBufferedWriter(dir.resolve("normalization.properties"))) { p.store(w,"Training-only statistics; delta in Celsius"); }
    }
    public static ForecastNormalizer load(Path dir, int horizon) throws IOException {
        Properties p=properties(dir);
        if (!"rolling-v1".equals(p.getProperty("schema.version")) || !"future_minus_current".equals(p.getProperty("target"))
                || !"61".equals(p.getProperty("window.size")) || !"1".equals(p.getProperty("sampling.hz"))
                || !"13".equals(p.getProperty("feature.count")) || !Integer.toString(horizon).equals(p.getProperty("forecast.seconds"))
                || !String.join(",",MotorGruFeatures.FEATURE_NAMES).equals(p.getProperty("feature.names")))
            throw new IOException("Incompatible forecast schema: "+dir);
        try {
            double[] m=new double[13],s=new double[13];
            for(int j=0;j<13;j++) { m[j]=Double.parseDouble(p.getProperty("feature.mean."+j));s[j]=Double.parseDouble(p.getProperty("feature.std."+j)); }
            return new ForecastNormalizer(m,s,Double.parseDouble(p.getProperty("target.mean")),Double.parseDouble(p.getProperty("target.std")));
        } catch(RuntimeException e) { throw new IOException("Invalid normalization metadata",e); }
    }
    public static Properties properties(Path dir) throws IOException {
        Properties p=new Properties();try(var r=Files.newBufferedReader(dir.resolve("normalization.properties"))) {p.load(r);}return p;
    }
}
