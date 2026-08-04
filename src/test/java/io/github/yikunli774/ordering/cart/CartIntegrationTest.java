package io.github.yikunli774.ordering.cart;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import io.github.yikunli774.ordering.table.TableCodeSigner;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class CartIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TableCodeSigner signer;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        io.github.yikunli774.ordering.support.TestData.resetSessions(jdbc);
        jdbc.update("UPDATE menu_item SET status = 'AVAILABLE' WHERE code IN ('D01','D02','D03','D04')");
    }

    @Test
    void addItemAppearsInCartWithTotal() {
        Session session = join("T02");
        JsonPath cart = changeItem(session, menuId("D01"), 2);

        assertThat(cart.getList("items")).hasSize(1);
        assertThat(cart.getInt("items[0].quantity")).isEqualTo(2);
        assertThat(cart.getDouble("total")).isEqualTo(76.0); // 38.00 * 2
    }

    @Test
    void addingSameItemAccumulates() {
        Session session = join("T02");
        long d01 = menuId("D01");
        changeItem(session, d01, 2);
        JsonPath cart = changeItem(session, d01, 1);

        assertThat(cart.getInt("items[0].quantity")).isEqualTo(3);
    }

    @Test
    void negativeDeltaRemovesTheLineAtZero() {
        Session session = join("T02");
        long d01 = menuId("D01");
        changeItem(session, d01, 1);
        JsonPath cart = changeItem(session, d01, -1);

        assertThat(cart.getList("items")).isEmpty();
        assertThat(cart.getDouble("total")).isEqualTo(0.0);
    }

    @Test
    void clearEmptiesCart() {
        Session session = join("T02");
        changeItem(session, menuId("D01"), 2);

        RestAssured.given().port(port).header("X-Participant-Token", session.token())
                .when().delete(cartUrl(session.sessionId()))
                .then().statusCode(204);

        assertThat(getCart(session).getList("items")).isEmpty();
    }

    @Test
    void soldOutItemCannotBeAdded() {
        Session session = join("T02");
        long d02 = menuId("D02");
        jdbc.update("UPDATE menu_item SET status = 'SOLD_OUT' WHERE id = ?", d02);

        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", session.token())
                .contentType("application/json")
                .body("{\"quantityDelta\":1}")
                .when()
                .put(cartUrl(session.sessionId()) + "/items/" + d02)
                .then()
                .statusCode(409)
                .body("code", equalTo("ITEM_UNAVAILABLE"));
    }

    @Test
    void cannotEditAnotherSessionsCart() {
        Session a = join("T02");
        Session b = join("T01");

        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", a.token())
                .contentType("application/json")
                .body("{\"quantityDelta\":1}")
                .when()
                .put(cartUrl(b.sessionId()) + "/items/" + menuId("D01"))
                .then()
                .statusCode(403);
    }

    @Test
    void concurrentAddsAreAtomic() throws Exception {
        Session session = join("T02");
        long d01 = menuId("D01");
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                changeItem(session, d01, 1);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        // Every concurrent +1 counted exactly once thanks to the atomic Lua mutation.
        assertThat(getCart(session).getInt("items[0].quantity")).isEqualTo(threads);
    }

    private record Session(long sessionId, String token) {
    }

    private Session join(String tableCode) {
        JsonPath jp = RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"tableToken\":\"" + signer.sign(tableCode) + "\"}")
                .when()
                .post("/api/v1/table-sessions/join")
                .then()
                .statusCode(200)
                .extract().jsonPath();
        return new Session(jp.getLong("sessionId"), jp.getString("participantToken"));
    }

    private long menuId(String code) {
        return jdbc.queryForObject("SELECT id FROM menu_item WHERE code = ?", Long.class, code);
    }

    private String cartUrl(long sessionId) {
        return "/api/v1/table-sessions/" + sessionId + "/cart";
    }

    private JsonPath changeItem(Session session, long menuItemId, int delta) {
        return RestAssured.given()
                .port(port)
                .header("X-Participant-Token", session.token())
                .contentType("application/json")
                .body("{\"quantityDelta\":" + delta + "}")
                .when()
                .put(cartUrl(session.sessionId()) + "/items/" + menuItemId)
                .then()
                .statusCode(200)
                .extract().jsonPath();
    }

    private JsonPath getCart(Session session) {
        return RestAssured.given()
                .port(port)
                .header("X-Participant-Token", session.token())
                .when()
                .get(cartUrl(session.sessionId()))
                .then()
                .statusCode(200)
                .extract().jsonPath();
    }
}
