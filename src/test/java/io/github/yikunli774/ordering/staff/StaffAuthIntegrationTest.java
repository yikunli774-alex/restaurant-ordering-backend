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
        String token = login("manager", "manager123");

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
    void logoutRevokesTheSessionImmediately() {
        String token = login("manager", "manager123");

        // Works before logout.
        RestAssured.given().port(port).header("Authorization", "Bearer " + token)
                .when().get("/api/v1/staff/me").then().statusCode(200);

        // Log out deletes the Redis session.
        RestAssured.given().port(port).header("Authorization", "Bearer " + token)
                .when().post("/api/v1/staff/auth/logout").then().statusCode(204);

        // The same still-unexpired, still-signature-valid token is now rejected.
        RestAssured.given().port(port).header("Authorization", "Bearer " + token)
                .when().get("/api/v1/staff/me").then().statusCode(401);
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

    private String login(String username, String password) {
        return RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/v1/staff/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .extract().path("accessToken");
    }
}
