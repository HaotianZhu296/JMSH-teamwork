package edu.northeastern.cs5010.mhd.dashboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.northeastern.cs5010.mhd.model.AlertRecord;
import edu.northeastern.cs5010.mhd.model.SensorReading;
import edu.northeastern.cs5010.mhd.store.EventStore;

import java.sql.SQLException;
import java.util.List;

public class DashboardDataProvider {

    private static final Gson GSON = new Gson();
    private final EventStore store;

    public DashboardDataProvider(EventStore store) {
        this.store = store;
    }

    public String getSummaryJson() throws SQLException {
        JsonObject json = new JsonObject();
        json.addProperty("readingCount", store.getReadingCount());
        json.addProperty("alertCount", store.getAlertCount());

        JsonArray machines = new JsonArray();
        for (String id : store.getMachineIds()) {
            machines.add(id);
        }
        json.add("machineIds", machines);
        return GSON.toJson(json);
    }

    public String getRecentReadingsJson(String machineId, int limit) throws SQLException {
        List<SensorReading> readings = store.getRecentReadings(machineId, limit);
        JsonArray arr = new JsonArray();
        for (SensorReading r : readings) {
            JsonObject obj = new JsonObject();
            obj.addProperty("timestamp", r.getTimestamp());
            obj.addProperty("machineId", r.getMachineId());
            obj.addProperty("airTempK", r.getAirTempK());
            obj.addProperty("processTempK", r.getProcessTempK());
            obj.addProperty("rpm", r.getRotationalSpeedRpm());
            obj.addProperty("torqueNm", r.getTorqueNm());
            obj.addProperty("toolWearMin", r.getToolWearMin());
            obj.addProperty("tempDiffK", r.getTempDiffK());
            arr.add(obj);
        }
        return GSON.toJson(arr);
    }

    public String getRecentAlertsJson(int limit) throws SQLException {
        List<AlertRecord> alerts = store.getRecentAlerts(limit);
        JsonArray arr = new JsonArray();
        for (AlertRecord a : alerts) {
            JsonObject obj = new JsonObject();
            obj.addProperty("timestamp", a.getTimestamp());
            obj.addProperty("machineId", a.getMachineId());
            obj.addProperty("severity", a.getSeverity().name());
            obj.addProperty("reason", a.getReason());
            obj.addProperty("detector", a.getDetectorName());
            arr.add(obj);
        }
        return GSON.toJson(arr);
    }
}
