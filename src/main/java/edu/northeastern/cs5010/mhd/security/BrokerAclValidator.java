package edu.northeastern.cs5010.mhd.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BrokerAclValidator {

    public enum Permission { READ, WRITE, READWRITE, DENY }

    record AclEntry(String user, String topic, Permission permission) {}

    private final List<AclEntry> entries = new ArrayList<>();

    public void loadAclFile(Path aclFile) throws IOException {
        entries.clear();
        String currentUser = null;
        try (BufferedReader br = Files.newBufferedReader(aclFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("user ")) {
                    currentUser = line.substring(5).trim();
                } else if (line.startsWith("topic ") && currentUser != null) {
                    String rest = line.substring(6).trim();
                    Permission perm = Permission.READWRITE;
                    if (rest.startsWith("read ")) {
                        perm = Permission.READ;
                        rest = rest.substring(5).trim();
                    } else if (rest.startsWith("write ")) {
                        perm = Permission.WRITE;
                        rest = rest.substring(6).trim();
                    } else if (rest.startsWith("readwrite ")) {
                        perm = Permission.READWRITE;
                        rest = rest.substring(10).trim();
                    } else if (rest.startsWith("deny ")) {
                        perm = Permission.DENY;
                        rest = rest.substring(5).trim();
                    }
                    entries.add(new AclEntry(currentUser, rest, perm));
                }
            }
        }
    }

    public List<AclEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean canPublish(String user, String topic) {
        return entries.stream()
                .filter(e -> e.user().equals(user) && topicMatches(e.topic(), topic))
                .anyMatch(e -> e.permission() == Permission.WRITE
                        || e.permission() == Permission.READWRITE);
    }

    public boolean canSubscribe(String user, String topic) {
        return entries.stream()
                .filter(e -> e.user().equals(user) && topicMatches(e.topic(), topic))
                .anyMatch(e -> e.permission() == Permission.READ
                        || e.permission() == Permission.READWRITE);
    }

    static boolean topicMatches(String pattern, String topic) {
        if (pattern.equals(topic)) return true;
        if (pattern.endsWith("/#")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return topic.startsWith(prefix) || topic.equals(prefix.substring(0, prefix.length() - 1));
        }
        if (pattern.contains("+")) {
            String[] patParts = pattern.split("/");
            String[] topParts = topic.split("/");
            if (patParts.length != topParts.length) return false;
            for (int i = 0; i < patParts.length; i++) {
                if (!patParts[i].equals("+") && !patParts[i].equals(topParts[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
