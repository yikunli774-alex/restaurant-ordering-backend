package io.github.yikunli774.ordering.kitchen;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import io.github.yikunli774.ordering.support.TestData;
import io.github.yikunli774.ordering.table.TableCodeSigner;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

class KitchenIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TableCodeSigner signer;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        TestData.resetSessions(jdbc);
        jdbc.update("UPDATE menu_item SET status = 'AVAILABLE' WHERE code LIKE 'D%'");
        jdbc.update("UPDATE inventory SET available = 100, reserved = 0");
    }

    @Test
    void kitchenSeesQueueAndAdvancesRoundThroughStates() {
        long roundId = joinAndOrder("T02", "D01", 1, "K1");
        String jwt = login("kitchen", "kitchen123");

        RestAssured.given().port(port).header("Authorization", "Bearer " + jwt)
                .when().get("/api/v1/kitchen/orders")
                .then().statusCode(200)
                .body("find { it.roundId == " + roundId + " }.status", equalTo("CONFIRMED"))
                .body("find { it.roundId == " + roundId + " }.tableCode", equalTo("T02"));

        advance(jwt, roundId, "prepare");
        advance(jwt, roundId, "ready");
        advance(jwt, roundId, "complete");

        // A completed round drops out of the kitchen queue.
        RestAssured.given().port(port).header("Authorization", "Bearer " + jwt)
                .when().get("/api/v1/kitchen/orders")
                .then().statusCode(200)
                .body("findAll { it.roundId == " + roundId + " }", hasSize(0));
    }

    @Test
    void skippingAStateIsRejected() {
        long roundId = joinAndOrder("T02", "D01", 1, "K1");
        String jwt = login("kitchen", "kitchen123");

        RestAssured.given().port(port).header("Authorization", "Bearer " + jwt)
                .when().post("/api/v1/kitchen/orders/" + roundId + "/complete") // CONFIRMED -> COMPLETED skips states
                .then().statusCode(409).body("code", equalTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    void cancellingARoundReturnsItsStock() {
        long roundId = joinAndOrder("T02", "D01", 2, "K1");
        long d01 = menuId("D01");
        assertThat(available(d01)).isEqualTo(98); // 2 reserved by the order

        String jwt = login("kitchen", "kitchen123");
        RestAssured.given().port(port).header("Authorization", "Bearer " + jwt)
                .when().post("/api/v1/kitchen/orders/" + roundId + "/cancel")
                .then().statusCode(204);

        assertThat(available(d01)).isEqualTo(100); // released back
    }

    @Test
    void participantCannotAccessKitchen() {
        RestAssured.given().port(port).header("X-Participant-Token", join("T02").token())
                .when().get("/api/v1/kitchen/orders").then().statusCode(403);
    }

    @Test
    void unauthenticatedCannotAccessKitchen() {
        RestAssured.given().port(port).when().get("/api/v1/kitchen/orders").then().statusCode(401);
    }

    // ---- helpers ----

    private record Participant(long sessionId, String token) {
    }

    private Participant join(String tableCode) {
        JsonPath jp = RestAssured.given().port(port).contentType("application/json")
                .body("{\"tableToken\":\"" + signer.sign(tableCode) + "\"}")
                .when().post("/api/v1/table-sessions/join")
                .then().statusCode(200).extract().jsonPath();
        return new Participant(jp.getLong("sessionId"), jp.getString("participantToken"));
    }

    private long joinAndOrder(String tableCode, String menuCode, int qty, String idempotencyKey) {
        Participant p = join(tableCode);
        RestAssured.given().port(port).header("X-Participant-Token", p.token())
                .contentType("application/json").body("{\"quantityDelta\":" + qty + "}")
                .when().put("/api/v1/table-sessions/" + p.sessionId() + "/cart/items/" + menuId(menuCode))
                .then().statusCode(200);
        return RestAssured.given().port(port)
                .header("X-Participant-Token", p.token()).header("Idempotency-Key", idempotencyKey)
                .when().post("/api/v1/table-sessions/" + p.sessionId() + "/orders")
                .then().statusCode(200).extract().jsonPath().getLong("roundId");
    }

    private void advance(String jwt, long roundId, String action) {
        RestAssured.given().port(port).header("Authorization", "Bearer " + jwt)
                .when().post("/api/v1/kitchen/orders/" + roundId + "/" + action)
                .then().statusCode(204);
    }

    private String login(String username, String password) {
        return RestAssured.given().port(port).contentType("application/json")
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .when().post("/api/v1/staff/auth/login")
                .then().statusCode(200).extract().path("accessToken");
    }

    private long menuId(String code) {
        return jdbc.queryForObject("SELECT id FROM menu_item WHERE code = ?", Long.class, code);
    }

    private int available(long menuItemId) {
        return jdbc.queryForObject("SELECT available FROM inventory WHERE menu_item_id = ?", Integer.class, menuItemId);
    }
}
