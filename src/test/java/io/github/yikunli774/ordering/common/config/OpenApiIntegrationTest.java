package io.github.yikunli774.ordering.common.config;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.hamcrest.Matchers.equalTo;

class OpenApiIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void publishesVersionedOpenApiMetadata() {
        RestAssured.given()
                .port(port)
                .when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body("info.title", equalTo("Restaurant Ordering API"))
                .body("info.version", equalTo("v1"));
    }
}
