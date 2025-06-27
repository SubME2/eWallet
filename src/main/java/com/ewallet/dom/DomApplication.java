package com.ewallet.dom;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@OpenAPIDefinition(
		info = @Info(title = "My Secured API", version = "1.0"),
		security = @SecurityRequirement(name = "basicAuth") // Reference the defined scheme
)
public class DomApplication {

	public static void main(String[] args) {
		SpringApplication.run(DomApplication.class, args);
	}

}
