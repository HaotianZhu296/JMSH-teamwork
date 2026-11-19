package edu.northeastern.cs5010.mhd.store;

import edu.northeastern.cs5010.mhd.model.AlertRecord;
import edu.northeastern.cs5010.mhd.model.AnomalyScore;
import edu.northeastern.cs5010.mhd.model.SensorReading;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class EventStoreMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: EventStoreMain <command> <args...>");
            System.err.println("Commands:");
            System.err.println("  import-readings <csv_file> [machine_id] [db_path]");
            System.err.println("  import-scores   <csv_file> [db_path]");
            System.err.println("  import-alerts   <csv_file> [db_path]");
            System.err.println("  summary         [db_path]");
            System.err.println("  recent-scores   <machine_id> [limit] [db_path]");
            System.err.println("  recent-alerts   [limit] [db_path]");
            System.exit(1);
        }

        String command = args[0];
        String dbPath = "events.db";

        switch (command) {
            case "import-readings" -> {
                Path csvFile = Paths.get(args[1]);
                String machineId = args.length > 2 ? args[2] : "machine_001";
                dbPath = args.length > 3 ? args[3] : dbPath;
                try (EventStore store = new EventStore(dbPath)) {
                    CsvImporter importer = new CsvImporter(store);
                    int count = importer.importReadings(csvFile, machineId);
                    System.out.printf("Imported %d readings from %s into %s%n",
                            count, csvFile, dbPath);
                }
            }
            case "import-scores" -> {
                Path scoreFile = Paths.get(args[1]);
                dbPath = args.length > 2 ? args[2] : dbPath;
                try (EventStore store = new EventStore(dbPath)) {
                    CsvImporter importer = new CsvImporter(store);
                    int count = importer.importScores(scoreFile);
                    System.out.printf("Imported %d scores from %s into %s%n",
                            count, scoreFile, dbPath);
                }
            }
            case "import-alerts" -> {
                Path csvFile = Paths.get(args[1]);
                dbPath = args.length > 2 ? args[2] : dbPath;
                try (EventStore store = new EventStore(dbPath)) {
                    CsvImporter importer = new CsvImporter(store);
                    int count = importer.importAlerts(csvFile);
                    System.out.printf("Imported %d alerts from %s into %s%n",
                            count, csvFile, dbPath);
                }
            }
            case "summary" -> {
                dbPath = args.length > 1 ? args[1] : dbPath;
                try (EventStore store = new EventStore(dbPath)) {
                    System.out.println("=== Event Store Summary ===");
                    System.out.printf("Readings: %d%n", store.getReadingCount());
                    System.out.printf("Scores:   %d%n", store.getScoreCount());
                    System.out.printf("Alerts:   %d%n", store.getAlertCount());
                    System.out.printf("Machines: %s%n", store.getMachineIds());
                }
            }
            case "recent-scores" -> {
                String machineId = args[1];
                int scoreLimit = args.length > 2 ? Integer.parseInt(args[2]) : 10;
                dbPath = args.length > 3 ? args[3] : dbPath;
                try (EventStore store = new EventStore(dbPath)) {
                    List<AnomalyScore> scores = store.getRecentScores(machineId, scoreLimit);
                    System.out.printf("=== Last %d Scores for %s ===%n", scoreLimit, machineId);
                    for (AnomalyScore s : scores) {
                        System.out.println(s);
                    }
                    if (scores.isEmpty()) {
                        System.out.println("(no scores)");
                    }
                }
            }
            case "recent-alerts" -> {
                int limit = args.length > 1 ? Integer.parseInt(args[1]) : 10;
                dbPath = args.length > 2 ? args[2] : dbPath;
                try (EventStore store = new EventStore(dbPath)) {
                    List<AlertRecord> alerts = store.getRecentAlerts(limit);
                    System.out.printf("=== Last %d Alerts ===%n", limit);
                    for (AlertRecord a : alerts) {
                        System.out.println(a);
                    }
                    if (alerts.isEmpty()) {
                        System.out.println("(no alerts)");
                    }
                }
            }
            default -> {
                System.err.println("Unknown command: " + command);
                System.exit(1);
            }
        }
    }
}
