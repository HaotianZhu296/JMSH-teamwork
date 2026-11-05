package com.jmsh.detection_alert.mqtt;

import com.jmsh.detection_alert.domain.AlertStatus;
import com.jmsh.detection_alert.dto.EvaluationReturn;
import com.jmsh.detection_alert.dto.SensorReading;
import com.jmsh.detection_alert.service.DetectionService;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
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

    @Bean
    public MessageChannel alertOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow alertOutboundFlow(MqttPahoClientFactory clientFactory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                clientId + "-publisher", clientFactory);
        handler.setAsync(true);
        handler.setDefaultQos(qos);
        return IntegrationFlow.from(alertOutboundChannel())
                .handle(handler)
                .get();
    }

    @Bean
    public IntegrationFlow mqttInboundFlow() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topic);
        adapter.setCompletionTimeout(completionTimeout);
        adapter.setQos(qos);

        MessageChannel alerts = alertOutboundChannel();

        return IntegrationFlow.from(adapter)
                .handle((payload, headers) -> {
                    try {
                        String jsonPayload = payload.toString();
                        SensorReading reading = objectMapper.readValue(
                                jsonPayload, SensorReading.class);
                        EvaluationReturn result = detectionService.processSensorReading(reading);

                        if (result.alertStatus() != AlertStatus.SAFE) {
                            String alertJson = objectMapper.writeValueAsString(result);
                            alerts.send(
                                    MessageBuilder.withPayload(alertJson)
                                            .setHeader(MqttHeaders.TOPIC,
                                                    "alerts/" + result.machineId())
                                            .build());
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to process MQTT message: " + e.getMessage());
                    }
                    return null;
                })
                .get();
    }
}
