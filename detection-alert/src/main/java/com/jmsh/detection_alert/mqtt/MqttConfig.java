package com.jmsh.detection_alert.mqtt;

import com.jmsh.detection_alert.dto.SensorReading;
import com.jmsh.detection_alert.service.DetectionService;
import lombok.AllArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@AllArgsConstructor
public class MqttConfig {

    // TODO: Move these to application.properties or environment variables for better configuration management
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "spring-detection-service";
    private static final String TOPIC = "sensors/+/detections";

    private final DetectionService detectionService;
    private final ObjectMapper objectMapper;

    // Configure connection settings for Mosquitto
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{ BROKER_URL });
        options.setCleanSession(true);
        // TODO: Add authentication if Mosquitto later
        // options.setUserName("username");
        // options.setPassword("password".toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    // Configure inbound message channel adapter
    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(MqttPahoClientFactory clientFactory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(CLIENT_ID, clientFactory, TOPIC);
        adapter.setCompletionTimeout(5000);
        adapter.setQos(1);
        return adapter;
    }

    // Define the integration processing pipeline
    @Bean
    public IntegrationFlow mqttInboundFlow(MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter) {
        return IntegrationFlow.from(mqttInboundAdapter)
                .handle((payload, headers) -> {
                    try {
                        String jsonPayload = payload.toString();

                        SensorReading sensorReading = objectMapper.readValue(jsonPayload, SensorReading.class);

                        detectionService.processSensorReading(sensorReading);

                    } catch (Exception e) {
                        System.err.println("Failed to process MQTT message: " + e.getMessage());
                    }
                    return null;
                })
                .get();
    }
}
