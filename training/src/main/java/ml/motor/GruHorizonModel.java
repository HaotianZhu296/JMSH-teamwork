package ml.motor;

import ai.djl.*;
import ai.djl.ndarray.*;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.*;
import ai.djl.nn.core.Linear;
import ai.djl.nn.recurrent.GRU;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.training.*;
import ai.djl.training.dataset.*;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import ai.djl.training.ParameterStore;
import java.nio.file.*;
import java.util.*;

/** DJL/PyTorch CPU model. One cached native model; synchronized inference protects native state. */
public final class GruHorizonModel implements HorizonForecaster {
    private final Model model;
    private final ForecastNormalizer scale;
    private final int horizon;
    private final String name;
    public GruHorizonModel(Model model, ForecastNormalizer scale, int horizon, String name) {
        this.model=model;this.scale=scale;this.horizon=horizon;this.name=name;
    }
    public int horizon() {return horizon;}
    public String name() {return name;}
    public static SequentialBlock network(int hidden, int dense) {
        return new SequentialBlock().add(GRU.builder().setStateSize(hidden).setNumLayers(1).optBatchFirst(true).build())
                .add(LambdaBlock.singleton(a->a.get(new NDIndex(":, -1, :")),"lastTimeStep"))
                .add(Linear.builder().setUnits(dense).build()).add(Activation::relu).add(Linear.builder().setUnits(1).build());
    }
    public synchronized double predict(List<MotorReading> history) {
        ForecastWindows.validateHistory(history);
        return history.get(60).statorWinding()+forward(scale.encode(history,60),1)[0];
    }
    private double[] forward(float[] x,int n) {
        try(NDManager mgr=model.getNDManager().newSubManager()) {
            NDList input=new NDList(mgr.create(x,new Shape(n,61,13)));
            float[] raw=model.getBlock().forward(new ParameterStore(mgr,false),input,false).head().toFloatArray();
            double[] result=new double[n];
            for(int i=0;i<n;i++) {
                result[i]=scale.restoreDelta(raw[i]);
                if(!Double.isFinite(result[i])) throw new IllegalStateException("Non-finite GRU prediction");
            }
            return result;
        }
    }
    @Override public synchronized double[] predictAll(List<ForecastWindows.Ref> refs) {
        double[] out=new double[refs.size()];
        for(int start=0;start<refs.size();start+=256) {
            int n=Math.min(256,refs.size()-start); float[] x=new float[n*61*13];
            for(int k=0;k<n;k++) {var r=refs.get(start+k);System.arraycopy(scale.encode(r.profile(),r.index()),0,x,k*61*13,61*13);}
            double[] delta=forward(x,n);
            for(int k=0;k<n;k++) out[start+k]=refs.get(start+k).now().statorWinding()+delta[k];
        }
        return out;
    }
    public synchronized void close() {model.close();}
    public static GruHorizonModel load(Path dir,int horizon) throws Exception {
        var scale=ForecastNormalizer.load(dir,horizon);var p=ForecastNormalizer.properties(dir);
        Model model=Model.newInstance("motor_gru","PyTorch");
        try {
            model.setBlock(network(Integer.parseInt(p.getProperty("hidden")),Integer.parseInt(p.getProperty("dense"))));
            model.load(dir,"motor_gru");
            return new GruHorizonModel(model,scale,horizon,p.getProperty("model.name"));
        } catch(Exception e) {model.close();throw e;}
    }
    private static ArrayDataset dataset(NDManager mgr,List<ForecastWindows.Ref> refs,ForecastNormalizer s,int h,boolean shuffle,int batch) {
        float[] x=new float[refs.size()*61*13],y=new float[refs.size()];
        for(int k=0;k<refs.size();k++) {
            var r=refs.get(k);System.arraycopy(s.encode(r.profile(),r.index()),0,x,k*61*13,61*13);y[k]=s.normalizeTarget(r.delta(h));
        }
        return new ArrayDataset.Builder().setData(mgr.create(x,new Shape(refs.size(),61,13)))
                .optLabels(mgr.create(y,new Shape(refs.size(),1))).setSampling(batch,shuffle).build();
    }
    private static double[] evaluate(Trainer trainer,ArrayDataset data,ForecastNormalizer s) throws Exception {
        double abs=0,sq=0;long n=0;
        for(Batch batch:trainer.iterateDataset(data)) try(batch) {
            float[] p=trainer.evaluate(batch.getData()).head().toFloatArray(),y=batch.getLabels().head().toFloatArray();
            for(int i=0;i<p.length;i++) {double e=(p[i]-y[i])*s.targetStd();abs+=Math.abs(e);sq+=e*e;n++;}
        }
        return new double[]{abs/n,sq/n};
    }
    public static void train(Path dir,List<ForecastWindows.Ref> train,List<ForecastWindows.Ref> val,int h,int seed,Properties settings) throws Exception {
        Files.createDirectories(dir);
        var scale=ForecastNormalizer.fit(train,h);Properties p=new Properties();p.putAll(settings);
        p.setProperty("seed",""+seed);p.setProperty("model.name","GRU_seed"+seed);
        scale.save(dir,h,p);
        ai.djl.engine.Engine.getEngine("PyTorch").setRandomSeed(seed);
        ai.djl.util.RandomUtils.RANDOM.setSeed(seed);
        int hidden=Integer.parseInt(p.getProperty("hidden")),dense=Integer.parseInt(p.getProperty("dense"));
        int epochs=Integer.parseInt(p.getProperty("epochs")),patience=Integer.parseInt(p.getProperty("patience"));
        float lr=Float.parseFloat(p.getProperty("learning.rate"));
        List<String> loss=new ArrayList<>();loss.add("epoch,train_mae_c,train_mse_c2,validation_mae_c,validation_mse_c2");
        double best=Double.POSITIVE_INFINITY;int stale=0,bestEpoch=0;
        double beforeSave=0;List<MotorReading> probe=val.get(0).history();
        try(Model model=Model.newInstance("motor_gru","PyTorch")) {
            model.setBlock(network(hidden,dense));
            var config=new DefaultTrainingConfig(Loss.l2Loss()).optOptimizer(Optimizer.adam()
                    .optLearningRateTracker(Tracker.fixed(lr)).build()).optDevices(new Device[]{Device.cpu()});
            try(Trainer trainer=model.newTrainer(config)) {
                trainer.initialize(new Shape(1,61,13));NDManager mgr=model.getNDManager();
                var training=dataset(mgr,train,scale,h,true,128);
                var trainEval=dataset(mgr,train.subList(0,Math.min(4096,train.size())),scale,h,false,256);
                var validation=dataset(mgr,val,scale,h,false,256);
                var wrapper=new GruHorizonModel(model,scale,h,"training");
                for(int epoch=1;epoch<=epochs;epoch++) {
                    long start=System.nanoTime();
                    for(Batch batch:trainer.iterateDataset(training)) try(batch) {EasyTrain.trainBatch(trainer,batch);trainer.step();}
                    double[] tr=evaluate(trainer,trainEval,scale),va=evaluate(trainer,validation,scale);
                    if(!Double.isFinite(va[0])) throw new IllegalStateException("Training diverged");
                    loss.add(String.format(Locale.ROOT,"%d,%.9f,%.9f,%.9f,%.9f",epoch,tr[0],tr[1],va[0],va[1]));
                    Files.write(dir.resolve("loss.csv"),loss);
                    System.out.printf(Locale.ROOT,"GRU h=%d seed=%d epoch=%d valMAE=%.6f RMSE=%.6f %.1fs%n",h,seed,epoch,va[0],Math.sqrt(va[1]),(System.nanoTime()-start)/1e9);
                    // Selection objective is validation MAE. Never inspect test during tuning.
                    if(va[0]<best-1e-7) {
                        best=va[0];stale=0;bestEpoch=epoch;
                        beforeSave=wrapper.predict(probe);model.save(dir,"motor_gru");
                    } else stale++;
                    if(stale>=patience) break;
                }
            }
        }
        p.setProperty("best.epoch",""+bestEpoch);p.setProperty("validation.mae",""+best);scale.save(dir,h,p);
        // Real lifecycle test: old native model is closed before reloading the checkpoint.
        try(var reloaded=load(dir,h)) {
            double after=reloaded.predict(probe),error=Math.abs(after-beforeSave);
            Files.writeString(dir.resolve("reload_check.csv"),"before_c,after_c,absolute_difference_c,tolerance_c\n"+beforeSave+","+after+","+error+",0.00001\n");
            if(error>1e-5) throw new IllegalStateException("GRU save/load mismatch: "+error);
        }
        ForecastEvaluation.lossChart(dir.resolve("loss.csv"),dir.resolve("loss.png"));
    }
}
