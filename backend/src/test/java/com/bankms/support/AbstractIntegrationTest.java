package com.bankms.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base for integration tests: the full Spring context against a real MySQL 8.4.
 * <p>
 * One container is started for the whole test run (singleton container pattern) and shared by
 * every IT class; Flyway builds the schema exactly as in production. Setting IT_DB_URL (plus
 * IT_DB_USERNAME / IT_DB_PASSWORD) points the tests at an existing MySQL instead, for machines
 * without Docker. Tests create their own uniquely-named data, so they never depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDataFactory.class)
public abstract class AbstractIntegrationTest {

    private static final String EXTERNAL_DB = System.getenv("IT_DB_URL");
    private static final MySQLContainer<?> MYSQL;

    static {
        if (EXTERNAL_DB == null) {
            MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("bankdb")
                    .withUsername("bank")
                    .withPassword("bank");
            MYSQL.start();
        } else {
            MYSQL = null;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (MYSQL != null) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("IT_DB_USERNAME", "bank"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "bank"));
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected TestDataFactory data;

    protected String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, password))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Login failed for " + email + ": " + result.getResponse().getContentAsString());
        }
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected String toJson(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private record Credentials(String email, String password) {
    }
}
