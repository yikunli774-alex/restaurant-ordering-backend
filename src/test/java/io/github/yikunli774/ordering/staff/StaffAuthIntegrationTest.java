package io.github.yikunli774.ordering.staff;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

class StaffAuthIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void loginIssuesTokenThatUnlocksProtectedEndpoint() {
        String token = RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"username\":\"manager\",\"password\":\"manager123\"}")
                .when()
                .post("/api/v1/staff/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .extract().path("accessToken");

        RestAssured.given()
                .port(port)
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/staff/me")
                .then()
                .statusCode(200)
                .body("staffId", notNullValue())
                .body("authorities", hasItems("menu:manage", "ROLE_MANAGER"));
    }

    @Test
    void wrongPasswordIsRejected() {
        RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"username\":\"manager\",\"password\":\"wrong-password\"}")
                .when()
                .post("/api/v1/staff/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("STAFF_CREDENTIALS_INVALID"));
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() {
        RestAssured.given()
                .port(port)
                .when()
                .get("/api/v1/staff/me")
                .then()
                .statusCode(401);
    }
}
