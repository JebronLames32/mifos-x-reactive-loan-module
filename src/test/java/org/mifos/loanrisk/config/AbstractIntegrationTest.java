package org.mifos.loanrisk.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

public abstract class AbstractIntegrationTest {

    private static final String POSTGRES_IMAGE = "postgres:15-alpine";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.0.1"; // Using Confluent image as Bitnami Kafka 4.0 might not be directly available or stable in Testcontainers

    protected static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                    .withDatabaseName("loanrisk")
                    .withUsername("mifos")
                    .withPassword("password");

    protected static final KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));

    @BeforeAll
    static void beforeAll() {
        Startables.deepStart(Stream.of(postgresContainer, kafkaContainer)).join();
    }

    @AfterAll
    static void afterAll() {
        // Containers will be stopped automatically by Ryuk if not running in daemon mode
        // but explicit stop can be added if needed, especially for resource cleanup in specific scenarios.
        // kafkaContainer.stop();
        // postgresContainer.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgresContainer.getHost(),
                postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                postgresContainer.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgresContainer::getUsername);
        registry.add("spring.r2dbc.password", postgresContainer::getPassword);

        registry.add("spring.liquibase.url", postgresContainer::getJdbcUrl);
        registry.add("spring.liquibase.user", postgresContainer::getUsername);
        registry.add("spring.liquibase.password", postgresContainer::getPassword);

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        // Ensure other necessary Kafka properties for tests are set if they differ from application.properties
        // For example, if you need a specific group-id for tests or different deserializers.
    }
}
