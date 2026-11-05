package edu.northeastern.cs5010.mhd.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BrokerAclValidatorTest {

    private BrokerAclValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new BrokerAclValidator();
        Path aclFile = Path.of("config/acl.conf");
        if (Files.exists(aclFile)) {
            validator.loadAclFile(aclFile);
        } else {
            Path temp = Files.createTempFile("acl-", ".conf");
            Files.writeString(temp, """
                    user simulator
                    topic write sensors/+/telemetry

                    user detector
                    topic read sensors/#
                    topic write alerts/#

                    user eventstore
                    topic read alerts/#
                    topic read sensors/#

                    user dashboard
                    topic read sensors/#
                    topic read alerts/#
                    """);
            validator.loadAclFile(temp);
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void simulatorCanPublishTelemetry() {
        assertTrue(validator.canPublish("simulator", "sensors/m1/telemetry"));
    }

    @Test
    void simulatorCannotReadAnything() {
        assertFalse(validator.canSubscribe("simulator", "sensors/m1/telemetry"));
        assertFalse(validator.canSubscribe("simulator", "alerts/m1"));
    }

    @Test
    void simulatorCannotPublishAlerts() {
        assertFalse(validator.canPublish("simulator", "alerts/m1"));
    }

    @Test
    void detectorCanReadTelemetry() {
        assertTrue(validator.canSubscribe("detector", "sensors/m1/telemetry"));
        assertTrue(validator.canSubscribe("detector", "sensors/m2/telemetry"));
    }

    @Test
    void detectorCanPublishAlerts() {
        assertTrue(validator.canPublish("detector", "alerts/m1"));
    }

    @Test
    void detectorCannotPublishTelemetry() {
        assertFalse(validator.canPublish("detector", "sensors/m1/telemetry"));
    }

    @Test
    void dashboardReadOnlyAccess() {
        assertTrue(validator.canSubscribe("dashboard", "sensors/m1/telemetry"));
        assertTrue(validator.canSubscribe("dashboard", "alerts/m1"));
        assertFalse(validator.canPublish("dashboard", "sensors/m1/telemetry"));
        assertFalse(validator.canPublish("dashboard", "alerts/m1"));
    }

    @Test
    void eventstoreCanReadAlertsAndSensors() {
        assertTrue(validator.canSubscribe("eventstore", "alerts/m1"));
        assertTrue(validator.canSubscribe("eventstore", "sensors/m1/telemetry"));
        assertFalse(validator.canPublish("eventstore", "alerts/m1"));
    }

    @Test
    void topicMatchesExact() {
        assertTrue(BrokerAclValidator.topicMatches("alerts/m1", "alerts/m1"));
        assertFalse(BrokerAclValidator.topicMatches("alerts/m1", "alerts/m2"));
    }

    @Test
    void topicMatchesWildcardHash() {
        assertTrue(BrokerAclValidator.topicMatches("sensors/#", "sensors/m1/telemetry"));
        assertTrue(BrokerAclValidator.topicMatches("sensors/#", "sensors/m1"));
        assertFalse(BrokerAclValidator.topicMatches("sensors/#", "alerts/m1"));
    }

    @Test
    void topicMatchesSingleLevelPlus() {
        assertTrue(BrokerAclValidator.topicMatches("sensors/+/telemetry", "sensors/m1/telemetry"));
        assertFalse(BrokerAclValidator.topicMatches("sensors/+/telemetry", "sensors/m1/other"));
        assertFalse(BrokerAclValidator.topicMatches("sensors/+/telemetry", "sensors/m1/m2/telemetry"));
    }
}
