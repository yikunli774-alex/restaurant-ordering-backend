package io.github.yikunli774.ordering.menu;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import io.github.yikunli774.ordering.table.TableCodeSigner;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

class MenuIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TableCodeSigner signer;

    @Test
    void customerWithParticipantTokenCanListMenu() {
        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", participantToken("T02"))
                .when()
                .get("/api/v1/menu-items")
                .then()
                .statusCode(200)
                .body("code", hasItems("D01", "D02", "D03", "D04"));
    }

    @Test
    void menuRequiresParticipantToken() {
        RestAssured.given().port(port)
                .when().get("/api/v1/menu-items")
                .then().statusCode(401);
    }

    @Test
    void managerCanCreateMenuItemAndCustomerSeesIt() {
        String jwt = login("manager", "manager123");
        RestAssured.given()
                .port(port)
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body("{\"code\":\"D99\",\"name\":\"测试菜\",\"category\":\"热菜\",\"price\":50.00,\"initialStock\":10}")
                .when()
                .post("/api/v1/management/menu-items")
                .then()
                .statusCode(200)
                .body("id", notNullValue());

        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", participantToken("T02"))
                .when()
                .get("/api/v1/menu-items")
                .then()
                .statusCode(200)
                .body("code", hasItems("D99"));
    }

    @Test
    void kitchenStaffCannotManageMenu() {
        String jwt = login("kitchen", "kitchen123");
        RestAssured.given()
                .port(port)
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body("{\"code\":\"D98\",\"name\":\"x\",\"price\":1.00,\"initialStock\":1}")
                .when()
                .post("/api/v1/management/menu-items")
                .then()
                .statusCode(403);
    }

    @Test
    void participantCannotManageMenu() {
        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", participantToken("T02"))
                .contentType("application/json")
                .body("{\"code\":\"D97\",\"name\":\"x\",\"price\":1.00,\"initialStock\":1}")
                .when()
                .post("/api/v1/management/menu-items")
                .then()
                .statusCode(403);
    }

    @Test
    void managerCanSetInventory() {
        String jwt = login("manager", "manager123");
        int menuItemId = RestAssured.given()
                .port(port)
                .header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/v1/management/menu-items")
                .then()
                .statusCode(200)
                .extract().jsonPath().getInt("find { it.code == 'D01' }.id");

        RestAssured.given()
                .port(port)
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body("{\"available\":50}")
                .when()
                .put("/api/v1/management/inventory/" + menuItemId)
                .then()
                .statusCode(204);
    }

    private String participantToken(String tableCode) {
        return RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"tableToken\":\"" + signer.sign(tableCode) + "\"}")
                .when()
                .post("/api/v1/table-sessions/join")
                .then()
                .statusCode(200)
                .extract().path("participantToken");
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
                .extract().path("accessToken");
    }
}
