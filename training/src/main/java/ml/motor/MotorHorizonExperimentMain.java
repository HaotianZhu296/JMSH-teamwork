package ml.motor;

import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Java-only reproducible experiment. train never evaluates testing profiles; test is a separate command. */
public final class MotorHorizonExperimentMain {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: train|test data.csv results/run-id");
        Path csv=Path.of(args[1]),out=Path.of(args[2]);boolean test=args[0].equals("test");
        if(!test&&!args[0].equals("train"))throw new IllegalArgumentException("Unknown mode");
        if(!test&&Files.exists(out))throw new IllegalArgumentException("Use a NEW run directory; never overwrite experiments");
        Files.createDirectories(out);
        var data=new MotorDataReader().loadData(csv.toString(),2);var split=new ProfileSplitter().split(data,42);
        if(split.train().size()!=48||split.validation().size()!=10||split.test().size()!=11)throw new IllegalStateException("Expected 48/10/11 profiles");
        System.out.println("Profiles: "+split.train().size()+"/"+split.validation().size()+"/"+split.test().size());
        if(test){test(out,split,csv);return;}
        int trainN=Integer.getInteger("train.windows",24000),valN=Integer.getInteger("validation.windows",8000);
        var train=ForecastWindows.sample(split.train(),5,trainN,42);var val=ForecastWindows.sample(split.validation(),5,valN,43);
        if(train.isEmpty()||val.isEmpty())throw new IllegalStateException("No windows");
        Properties config=new Properties();config.setProperty("dataset.sha256",sha256(csv));config.setProperty("split.seed","42");
        config.setProperty("train.profiles",split.train().keySet().toString());config.setProperty("validation.profiles",split.validation().keySet().toString());config.setProperty("test.profiles",split.test().keySet().toString());
        config.setProperty("train.windows",""+train.size());config.setProperty("validation.windows",""+val.size());
        config.setProperty("sampling","stride2; all horizons share origins; step5 seconds; shuffled without replacement; train seed42 val43 test44");
        config.setProperty("hidden",System.getProperty("gru.hidden","32"));config.setProperty("dense","16");
        config.setProperty("learning.rate",System.getProperty("gru.lr","0.001"));config.setProperty("epochs",System.getProperty("gru.epochs","18"));config.setProperty("patience","4");
        config.setProperty("batch.size","128");config.setProperty("loss","MSE normalized delta");config.setProperty("selection","lowest mean validation MAE across seeds by family; deploy seed42; baseline included");
        config.setProperty("seeds",System.getProperty("seeds","42,2026"));config.setProperty("java.version",System.getProperty("java.version"));
        writeProperties(out.resolve("experiment.properties"),config);
        List<String> splits=new ArrayList<>();splits.add("profile_id,split");
        split.train().keySet().forEach(id->splits.add(id+",train"));split.validation().keySet().forEach(id->splits.add(id+",validation"));split.test().keySet().forEach(id->splits.add(id+",test"));Files.write(out.resolve("profile_split.csv"),splits);
        windows(out,"train",train);windows(out,"validation",val);
        Properties selection=new Properties();List<String> rows=new ArrayList<>(),selectedRows=new ArrayList<>();rows.add(ForecastEvaluation.header());selectedRows.add(ForecastEvaluation.header());
        List<ForecastEvaluation.Metrics> selectedMetrics=new ArrayList<>(),baselines=new ArrayList<>();
        int[] seeds=Arrays.stream(config.getProperty("seeds").split(",")).mapToInt(Integer::parseInt).toArray();
        if(seeds[0]!=42)throw new IllegalArgumentException("First deployment seed must be 42");
        for(int h:ForecastWindows.HORIZONS) {
            Map<String,Double> scores=new LinkedHashMap<>();Map<String,String> paths=new HashMap<>();
            var base=ForecastEvaluation.metrics(val,h,new BaselineForecaster(h,false).predictAll(val));baselines.add(base);
            for(boolean trend:new boolean[]{false,true}) {
                var m=new BaselineForecaster(h,trend);var metric=ForecastEvaluation.metrics(val,h,m.predictAll(val));
                scores.put(m.name(),metric.mae());paths.put(m.name(),".");rows.add(ForecastEvaluation.row("validation",h,m.name(),metric,base));
            }
            double treeSum=0,gruSum=0;
            for(int seed:seeds) {
                Properties treeConfig=new Properties();treeConfig.putAll(config);
                treeConfig.setProperty("smile.gradient_boost.trees","200");treeConfig.setProperty("smile.gradient_boost.loss","LeastSquares");
                treeConfig.setProperty("smile.gradient_boost.max_depth","6");treeConfig.setProperty("smile.gradient_boost.max_nodes","24");
                treeConfig.setProperty("smile.gradient_boost.node_size","10");treeConfig.setProperty("smile.gradient_boost.shrinkage","0.05");treeConfig.setProperty("smile.gradient_boost.sampling_rate","0.8");treeConfig.setProperty("seed",""+seed);
                Path treeDir=out.resolve("models_gbdt_h"+h+"_s"+seed);System.out.println("Training GBDT h="+h+" seed="+seed);
                try(var tree=TreeHorizonModel.train(train,h,seed,treeConfig)) {
                    double before=tree.predict(val.get(0).history());tree.save(treeDir,treeConfig);
                    try(var loaded=TreeHorizonModel.load(treeDir,h)) {
                        double diff=Math.abs(before-loaded.predict(val.get(0).history()));if(diff>1e-10)throw new IllegalStateException("Tree reload mismatch");
                        Files.writeString(treeDir.resolve("reload_check.csv"),"absolute_difference_c\n"+diff+"\n");
                        var metric=ForecastEvaluation.metrics(val,h,loaded.predictAll(val));treeSum+=metric.mae();
                        rows.add(ForecastEvaluation.row("validation",h,loaded.name(),metric,base));
                        Files.write(treeDir.resolve("validation_metrics.csv"),List.of(ForecastEvaluation.header(),ForecastEvaluation.row("validation",h,loaded.name(),metric,base)));
                        System.out.printf(Locale.ROOT,"GBDT h=%d seed=%d MAE=%.6f baseline=%.6f%n",h,seed,metric.mae(),base.mae());
                    }
                }
                Path gruDir=out.resolve("models_gru_h"+h+"_s"+seed);
                GruHorizonModel.train(gruDir,train,val,h,seed,config);
                try(var gru=GruHorizonModel.load(gruDir,h)) {
                    var metric=ForecastEvaluation.metrics(val,h,gru.predictAll(val));gruSum+=metric.mae();rows.add(ForecastEvaluation.row("validation",h,gru.name(),metric,base));
                    Files.write(gruDir.resolve("validation_metrics.csv"),List.of(ForecastEvaluation.header(),ForecastEvaluation.row("validation",h,gru.name(),metric,base)));
                }
                Files.copy(gruDir.resolve("loss.csv"),out.resolve("loss_gru_h"+h+"_s"+seed+".csv"));
                Files.copy(gruDir.resolve("loss.png"),out.resolve("loss_gru_h"+h+"_s"+seed+".png"));
                Files.write(out.resolve("candidates.csv"),rows);
            }
            scores.put("GBDT",treeSum/seeds.length);scores.put("GRU",gruSum/seeds.length);
            paths.put("GBDT","models_gbdt_h"+h+"_s42");paths.put("GRU","models_gru_h"+h+"_s42");
            String best=scores.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow().getKey();
            selection.setProperty("h"+h+".type",best);selection.setProperty("h"+h+".path",paths.get(best));selection.setProperty("h"+h+".family.mean.mae",""+scores.get(best));
            try(var model=ForecastModelRegistry.loadOne(out,h,best,paths.get(best))) {
                double[] p=model.predictAll(val);var metric=ForecastEvaluation.metrics(val,h,p);selectedMetrics.add(metric);
                selectedRows.add(ForecastEvaluation.row("validation",h,model.name(),metric,base));ForecastEvaluation.details(out,"validation",h,model.name(),val,p);
            }
            Files.write(out.resolve("validation.csv"),selectedRows);writeProperties(out.resolve("selection.properties"),selection);
            System.out.println("Selected h="+h+": "+best+" family MAEs "+scores);
        }
        ForecastEvaluation.horizonChart(out,"validation",selectedMetrics,baselines);
        Files.writeString(out.resolve("TRAINING_COMPLETE"),"Selection frozen. Test set not evaluated.\n");
    }
    private static void test(Path out,ProfileSplitter.Split split,Path csv)throws Exception {
        if(!Files.exists(out.resolve("TRAINING_COMPLETE")))throw new IllegalStateException("Training incomplete");
        Properties config=new Properties();try(var r=Files.newBufferedReader(out.resolve("experiment.properties"))){config.load(r);}
        if(!sha256(csv).equals(config.getProperty("dataset.sha256")))throw new IllegalStateException("Dataset changed");
        // CREATE_NEW prevents silently reusing the test set for iterative model selection.
        Files.writeString(out.resolve("test.lock"),"Selection SHA256="+sha256(out.resolve("selection.properties"))+"\n",StandardOpenOption.CREATE_NEW);
        var refs=ForecastWindows.sample(split.test(),5,20000,44);windows(out,"test",refs);
        List<String> rows=new ArrayList<>();rows.add(ForecastEvaluation.header());List<ForecastEvaluation.Metrics> ms=new ArrayList<>(),bs=new ArrayList<>();
        try(var registry=ForecastModelRegistry.load(out)) {
            if(!registry.errors().isEmpty())throw new IllegalStateException("Models unavailable: "+registry.errors());
            for(int h:ForecastWindows.HORIZONS) {
                var m=registry.models().get(h);double[] p=m.predictAll(refs);var metric=ForecastEvaluation.metrics(refs,h,p);
                var b=ForecastEvaluation.metrics(refs,h,new BaselineForecaster(h,false).predictAll(refs));ms.add(metric);bs.add(b);
                rows.add(ForecastEvaluation.row("test",h,m.name(),metric,b));ForecastEvaluation.details(out,"test",h,m.name(),refs,p);
            }
        }
        Files.write(out.resolve("test.csv"),rows);ForecastEvaluation.horizonChart(out,"test",ms,bs);System.out.println(String.join("\n",rows));
    }
    static void writeProperties(Path path,Properties p)throws Exception{try(var w=Files.newBufferedWriter(path)){p.store(w,"Motor rolling forecast experiment");}}
    static void windows(Path out,String split,List<ForecastWindows.Ref> refs)throws Exception {
        List<String> rows=new ArrayList<>();rows.add("profile_id,origin_sample_index");for(var r:refs)rows.add(r.now().profileId()+","+r.now().sampleIndex());Files.write(out.resolve("windows_"+split+".csv"),rows);
    }
    public static String sha256(Path file)throws Exception {
        var md=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)md.update(buf,0,n);}return HexFormat.of().formatHex(md.digest());
    }
}
