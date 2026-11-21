package ml.motor;
import java.util.List;

public record BaselineForecaster(int horizon, boolean trend) implements HorizonForecaster {
    public BaselineForecaster {ForecastWindows.requireHorizon(horizon);}
    public String name(){return trend?"Trend10":"Persistence";}
    public double predict(List<MotorReading> history) {
        ForecastWindows.validateHistory(history);double current=history.get(60).statorWinding();
        return current+(trend?horizon*(current-history.get(50).statorWinding())/10:0);
    }
}
