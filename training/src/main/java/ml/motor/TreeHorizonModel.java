package ml.motor;

import smile.data.*;
import smile.data.formula.Formula;
import smile.regression.GradientTreeBoost;
import smile.math.MathEx;
import smile.io.*;
import java.nio.file.*;
import java.util.*;

/** V2 thermal summaries plus causal short-term rates; all derive from the same 61 rows. */
public final class TreeHorizonModel implements HorizonForecaster {
    public static final String[] NAMES;
    static {
        NAMES=Arrays.copyOf(MotorForecastV2.FEATURE_NAMES,33);
        NAMES[30]="rate1";NAMES[31]="rate5";NAMES[32]="rate_acceleration";
    }
    private final GradientTreeBoost tree;private final int h;private final String name;
    public TreeHorizonModel(GradientTreeBoost tree,int h,String name) {this.tree=tree;this.h=h;this.name=name;}
    public int horizon(){return h;}public String name(){return name;}
    public static double[] features(List<MotorReading> p,int i) {
        double[] f=Arrays.copyOf(MotorForecastV2.features(p,i),33);
        f[30]=p.get(i).statorWinding()-p.get(i-1).statorWinding();
        f[31]=(p.get(i).statorWinding()-p.get(i-5).statorWinding())/5;
        f[32]=f[31]-(p.get(i-5).statorWinding()-p.get(i-10).statorWinding())/5;
        return f;
    }
    public double predict(List<MotorReading> history) {
        ForecastWindows.validateHistory(history);
        double pred=history.get(60).statorWinding()+tree.predict(DataFrame.of(new double[][]{features(history,60)},NAMES).get(0));
        if(!Double.isFinite(pred)) throw new IllegalStateException("Non-finite tree prediction");
        return pred;
    }
    /** Diagnostic predictions after each boosting stage; no fitting or model mutation. */
    public double[][] predictStages(List<ForecastWindows.Ref> refs) {
        double[][] x = new double[refs.size()][];
        for (int i = 0; i < refs.size(); i++) x[i] = features(refs.get(i).profile(), refs.get(i).index());
        double[][] predictions = tree.test(DataFrame.of(x, NAMES));
        for (double[] stage : predictions)
            for (int i = 0; i < refs.size(); i++) stage[i] += refs.get(i).now().statorWinding();
        return predictions;
    }
    public void save(Path dir,Properties config) throws Exception {
        Files.createDirectories(dir);Write.object(tree,dir.resolve("gbdt.ser"));
        Properties p=new Properties();p.putAll(config);p.setProperty("model.name",name);p.setProperty("feature.names",String.join(",",NAMES));
        p.setProperty("forecast.seconds",""+h);p.setProperty("window.size","61");p.setProperty("target","future_minus_current");
        p.setProperty("smile.version","3.1.1");
        try(var w=Files.newBufferedWriter(dir.resolve("tree.properties"))){p.store(w,"GBDT rolling-v1");}
    }
    public static TreeHorizonModel load(Path dir,int h) throws Exception {
        Properties p=new Properties();try(var r=Files.newBufferedReader(dir.resolve("tree.properties"))){p.load(r);}
        if(!String.join(",",NAMES).equals(p.getProperty("feature.names")) || !Integer.toString(h).equals(p.getProperty("forecast.seconds"))
                || !"61".equals(p.getProperty("window.size")) || !"future_minus_current".equals(p.getProperty("target"))
                || !"3.1.1".equals(p.getProperty("smile.version"))) throw new IllegalArgumentException("Tree schema mismatch");
        return new TreeHorizonModel((GradientTreeBoost)Read.object(dir.resolve("gbdt.ser")),h,p.getProperty("model.name"));
    }
    public static TreeHorizonModel train(List<ForecastWindows.Ref> refs,int h,int seed,Properties options) {
        double[][] rows=new double[refs.size()][34];String[] names=Arrays.copyOf(NAMES,34);names[33]="delta";
        for(int k=0;k<refs.size();k++) {var r=refs.get(k);System.arraycopy(features(r.profile(),r.index()),0,rows[k],0,33);rows[k][33]=r.delta(h);}
        MathEx.setSeed(seed);
        return new TreeHorizonModel(GradientTreeBoost.fit(Formula.lhs("delta"),DataFrame.of(rows,names),options),h,
                options.getProperty("model.name", "GBDT_seed"+seed));
    }
}
