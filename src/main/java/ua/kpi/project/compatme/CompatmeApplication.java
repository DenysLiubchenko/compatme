package ua.kpi.project.compatme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CompatmeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompatmeApplication.class, args);
	}

}
