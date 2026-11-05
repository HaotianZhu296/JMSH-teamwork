package com.jmsh.detection_alert.mqtt;

import com.jmsh.detection_alert.dto.SensorReading;
import com.jmsh.detection_alert.service.DetectionService;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${mqtt.qos:1}")
    private int qos;

    @Value("${mqtt.completion-timeout:5000}")
    private int completionTimeout;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    private final DetectionService detectionService;
    private final ObjectMapper objectMapper;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{ brokerUrl });
        options.setCleanSession(true);
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        factory.setConnectionOptions(options);
        return factory;
    }

    // Configure inbound message channel adapter
    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(MqttPahoClientFactory clientFactory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, clientFactory, topic);
        adapter.setCompletionTimeout(completionTimeout);
        adapter.setQos(qos);
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
