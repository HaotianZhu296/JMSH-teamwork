package edu.northeastern.cs5010.mhd.store;

import edu.northeastern.cs5010.mhd.model.AlertRecord;
import edu.northeastern.cs5010.mhd.model.SensorReading;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventStore implements AutoCloseable {

    private final Connection conn;

    public EventStore(String dbPath) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        conn.setAutoCommit(true);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS readings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp_ms INTEGER NOT NULL,
                    machine_id TEXT NOT NULL,
                    air_temp_k REAL NOT NULL,
                    process_temp_k REAL NOT NULL,
                    rotational_speed_rpm REAL NOT NULL,
                    torque_nm REAL NOT NULL,
                    tool_wear_min REAL NOT NULL
                )""");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS alerts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp_ms INTEGER NOT NULL,
                    machine_id TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    detector_name TEXT NOT NULL
                )""");
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_readings_machine
                ON readings (machine_id, timestamp_ms)""");
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_alerts_machine
                ON alerts (machine_id, timestamp_ms)""");
        }
    }

    public void insertReading(SensorReading r) throws SQLException {
        String sql = "INSERT INTO readings "
                + "(timestamp_ms, machine_id, air_temp_k, process_temp_k, "
                + "rotational_speed_rpm, torque_nm, tool_wear_min) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, r.getTimestamp());
            ps.setString(2, r.getMachineId());
            ps.setDouble(3, r.getAirTempK());
            ps.setDouble(4, r.getProcessTempK());
            ps.setDouble(5, r.getRotationalSpeedRpm());
            ps.setDouble(6, r.getTorqueNm());
            ps.setDouble(7, r.getToolWearMin());
            ps.executeUpdate();
        }
    }

    public void insertReadingsBatch(List<SensorReading> readings) throws SQLException {
        String sql = "INSERT INTO readings "
                + "(timestamp_ms, machine_id, air_temp_k, process_temp_k, "
                + "rotational_speed_rpm, torque_nm, tool_wear_min) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (SensorReading r : readings) {
                ps.setLong(1, r.getTimestamp());
                ps.setString(2, r.getMachineId());
                ps.setDouble(3, r.getAirTempK());
                ps.setDouble(4, r.getProcessTempK());
                ps.setDouble(5, r.getRotationalSpeedRpm());
                ps.setDouble(6, r.getTorqueNm());
                ps.setDouble(7, r.getToolWearMin());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public void insertAlert(AlertRecord a) throws SQLException {
        String sql = "INSERT INTO alerts "
                + "(timestamp_ms, machine_id, severity, reason, detector_name) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, a.getTimestamp());
            ps.setString(2, a.getMachineId());
            ps.setString(3, a.getSeverity().name());
            ps.setString(4, a.getReason());
            ps.setString(5, a.getDetectorName());
            ps.executeUpdate();
        }
    }

    public int getReadingCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM readings")) {
            return rs.getInt(1);
        }
    }

    public int getAlertCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM alerts")) {
            return rs.getInt(1);
        }
    }

    public List<SensorReading> getRecentReadings(String machineId, int limit) throws SQLException {
        String sql = "SELECT timestamp_ms, machine_id, air_temp_k, process_temp_k, "
                + "rotational_speed_rpm, torque_nm, tool_wear_min "
                + "FROM readings WHERE machine_id = ? "
                + "ORDER BY timestamp_ms DESC LIMIT ?";
        List<SensorReading> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, machineId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SensorReading(
                            rs.getLong("timestamp_ms"),
                            rs.getString("machine_id"),
                            rs.getDouble("air_temp_k"),
                            rs.getDouble("process_temp_k"),
                            rs.getDouble("rotational_speed_rpm"),
                            rs.getDouble("torque_nm"),
                            rs.getDouble("tool_wear_min")));
                }
            }
        }
        return result;
    }

    public List<AlertRecord> getRecentAlerts(int limit) throws SQLException {
        String sql = "SELECT timestamp_ms, machine_id, severity, reason, detector_name "
                + "FROM alerts ORDER BY timestamp_ms DESC LIMIT ?";
        List<AlertRecord> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AlertRecord(
                            rs.getLong("timestamp_ms"),
                            rs.getString("machine_id"),
                            AlertRecord.Severity.valueOf(rs.getString("severity")),
                            rs.getString("reason"),
                            rs.getString("detector_name")));
                }
            }
        }
        return result;
    }

    public List<String> getMachineIds() throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DISTINCT machine_id FROM readings ORDER BY machine_id")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
