package com.jmsh.detection_alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.config.EnableIntegration;

@SpringBootApplication
@EnableIntegration
public class DetectionAlertApplication {

	public static void main(String[] args) {
		SpringApplication.run(DetectionAlertApplication.class, args);
	}

}
