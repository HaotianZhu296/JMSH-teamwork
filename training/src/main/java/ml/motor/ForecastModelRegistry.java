package ml.motor;
import java.nio.file.*;
import java.util.*;

/** Selection is frozen before the independent test run. Relative paths keep bundles portable. */
public final class ForecastModelRegistry implements AutoCloseable {
    private final Map<Integer,HorizonForecaster> models=new LinkedHashMap<>();
    private final Map<Integer,String> errors=new LinkedHashMap<>();
    public Map<Integer,HorizonForecaster> models(){return Collections.unmodifiableMap(models);}
    public Map<Integer,String> errors(){return Collections.unmodifiableMap(errors);}
    public static HorizonForecaster loadOne(Path root,int h,String type,String path)throws Exception {
        Path dir=root.resolve(path).normalize();if(!dir.startsWith(root.normalize()))throw new IllegalArgumentException("Model path escapes bundle");
        return switch(type){case "GRU"->GruHorizonModel.load(dir,h);case "GBDT"->TreeHorizonModel.load(dir,h);case "Trend10"->new BaselineForecaster(h,true);case "Persistence"->new BaselineForecaster(h,false);default->throw new IllegalArgumentException("Unknown model "+type);};
    }
    public static ForecastModelRegistry load(Path root)throws Exception {
        ForecastModelRegistry registry=new ForecastModelRegistry();
        Properties p=new Properties();
        try(var r=Files.newBufferedReader(root.resolve("selection.properties"))){p.load(r);}
        catch(java.io.IOException e){for(int h:ForecastWindows.HORIZONS)registry.errors.put(h,"Selection manifest unavailable: "+e.getClass().getSimpleName());return registry;}
        for(int h:ForecastWindows.HORIZONS) try {registry.models.put(h,loadOne(root,h,p.getProperty("h"+h+".type"),p.getProperty("h"+h+".path",".")));}
        catch(Exception e){registry.errors.put(h,e.getClass().getSimpleName()+": "+e.getMessage());}
        return registry;
    }
    public void close(){models.values().forEach(HorizonForecaster::close);}
}
