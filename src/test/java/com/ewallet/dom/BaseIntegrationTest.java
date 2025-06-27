package com.ewallet.dom;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles(value = "test")
@SpringBootTest(classes = DomApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
public abstract class BaseIntegrationTest {

    // Define a PostgreSQL container
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    // Dynamically set properties for the Spring Boot application
    @DynamicPropertySource
    static void setDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");// Ensures a clean schema for each test run
       // registry.add("spring.datasource.hikari.auto-commit",() -> "false");

//
//        registry.add("spring.datasource.hikari.minimumIdle", () -> 5);
//        registry.add("spring.datasource.hikari.maximumPoolSize", () -> 30);
//        registry.add("spring.datasource.hikari.idleTimeout", () -> 300000);
//        registry.add("spring.datasource.hikari.connectionTimeout", () -> 300000);
//        registry.add("spring.datasource.hikari.leakDetectionThreshold", () -> 300000);

////
//        registry.add("spring.datasource.hikari.minimum-idle", () -> 2);
//        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 300);
//        registry.add("spring.datasource.hikari.idle-timeout", () -> 30000000);
//        registry.add("spring.datasource.hikari.connection-timeout", () -> 30000000);
//        registry.add("spring.datasource.hikari.leak_detection_threshold", () -> 30000000);

    }
}
