package ml.motor;

import java.util.List;

public interface HorizonForecaster extends AutoCloseable {
    int horizon();
    String name();
    double predict(List<MotorReading> history);
    default double[] predictAll(List<ForecastWindows.Ref> refs) {
        double[] result=new double[refs.size()];
        for(int i=0;i<result.length;i++) result[i]=predict(refs.get(i).history());
        return result;
    }
    @Override default void close() {}
}
