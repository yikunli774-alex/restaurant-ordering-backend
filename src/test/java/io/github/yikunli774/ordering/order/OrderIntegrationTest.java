package io.github.yikunli774.ordering.order;

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

class OrderIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TableCodeSigner signer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void clean() {
        io.github.yikunli774.ordering.support.TestData.resetSessions(jdbc);
        jdbc.update("UPDATE menu_item SET status = 'AVAILABLE' WHERE code IN ('D01','D02','D03','D04')");
        jdbc.update("UPDATE inventory SET available = 100, reserved = 0");
    }

    @Test
    void submitRoundCreatesRoundClearsCartAndReservesStock() {
        Session session = join("T02");
        long d01 = menuId("D01");
        addToCart(session, d01, 2);

        JsonPath round = submit(session, "K1");
        assertThat(round.getInt("roundNo")).isEqualTo(1);
        assertThat(round.getString("status")).isEqualTo("CONFIRMED");
        assertThat(round.getDouble("amount")).isEqualTo(76.0);
        assertThat(round.getList("items")).hasSize(1);

        assertThat(getCart(session).getList("items")).isEmpty();
        assertThat(available(d01)).isEqualTo(98);
    }

    @Test
    void secondRoundGetsRoundNoTwo() {
        Session session = join("T02");
        long d01 = menuId("D01");
        addToCart(session, d01, 1);
        submit(session, "K1");
        addToCart(session, d01, 1);

        assertThat(submit(session, "K2").getInt("roundNo")).isEqualTo(2);
    }

    @Test
    void replayingIdempotencyKeyReturnsSameRoundWithoutDoubling() {
        Session session = join("T02");
        long d01 = menuId("D01");
        addToCart(session, d01, 2);

        long firstRoundId = submit(session, "K1").getLong("roundId");
        long replayRoundId = submit(session, "K1").getLong("roundId");

        assertThat(replayRoundId).isEqualTo(firstRoundId);
        assertThat(roundCount(session.sessionId())).isEqualTo(1);
        assertThat(available(d01)).isEqualTo(98); // reserved once, not twice
    }

    @Test
    void submittingEmptyCartIsRejected() {
        Session session = join("T02");
        RestAssured.given().port(port)
                .header("X-Participant-Token", session.token())
                .header("Idempotency-Key", "K1")
                .when().post(ordersUrl(session.sessionId()))
                .then().statusCode(409).body("code", equalTo("EMPTY_CART"));
    }

    @Test
    void insufficientStockIsRejectedAndCartRestored() {
        Session session = join("T02");
        long d01 = menuId("D01");
        jdbc.update("UPDATE inventory SET available = 1 WHERE menu_item_id = ?", d01);
        addToCart(session, d01, 2);

        RestAssured.given().port(port)
                .header("X-Participant-Token", session.token())
                .header("Idempotency-Key", "K1")
                .when().post(ordersUrl(session.sessionId()))
                .then().statusCode(409).body("code", equalTo("INSUFFICIENT_STOCK"));

        // The cart is handed back, and stock is untouched.
        assertThat(getCart(session).getInt("items[0].quantity")).isEqualTo(2);
        assertThat(available(d01)).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        Session session = join("T02");
        addToCart(session, menuId("D01"), 1);
        RestAssured.given().port(port)
                .header("X-Participant-Token", session.token())
                .when().post(ordersUrl(session.sessionId()))
                .then().statusCode(400);
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        long d01 = menuId("D01");
        jdbc.update("UPDATE inventory SET available = 5, reserved = 0 WHERE menu_item_id = ?", d01);

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                return inventoryRepository.reserve(d01, 1, "op-" + idx);
            }));
        }
        start.countDown();

        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(5);            // only as many as there was stock
        assertThat(available(d01)).isEqualTo(0);       // never negative
        assertThat(reserved(d01)).isEqualTo(5);
    }

    // ---- helpers ----

    private record Session(long sessionId, String token) {
    }

    private Session join(String tableCode) {
        JsonPath jp = RestAssured.given().port(port).contentType("application/json")
                .body("{\"tableToken\":\"" + signer.sign(tableCode) + "\"}")
                .when().post("/api/v1/table-sessions/join")
                .then().statusCode(200).extract().jsonPath();
        return new Session(jp.getLong("sessionId"), jp.getString("participantToken"));
    }

    private void addToCart(Session session, long menuItemId, int delta) {
        RestAssured.given().port(port)
                .header("X-Participant-Token", session.token())
                .contentType("application/json")
                .body("{\"quantityDelta\":" + delta + "}")
                .when().put("/api/v1/table-sessions/" + session.sessionId() + "/cart/items/" + menuItemId)
                .then().statusCode(200);
    }

    private JsonPath submit(Session session, String idempotencyKey) {
        return RestAssured.given().port(port)
                .header("X-Participant-Token", session.token())
                .header("Idempotency-Key", idempotencyKey)
                .when().post(ordersUrl(session.sessionId()))
                .then().statusCode(200).extract().jsonPath();
    }

    private JsonPath getCart(Session session) {
        return RestAssured.given().port(port)
                .header("X-Participant-Token", session.token())
                .when().get("/api/v1/table-sessions/" + session.sessionId() + "/cart")
                .then().statusCode(200).extract().jsonPath();
    }

    private String ordersUrl(long sessionId) {
        return "/api/v1/table-sessions/" + sessionId + "/orders";
    }

    private long menuId(String code) {
        return jdbc.queryForObject("SELECT id FROM menu_item WHERE code = ?", Long.class, code);
    }

    private int available(long menuItemId) {
        return jdbc.queryForObject("SELECT available FROM inventory WHERE menu_item_id = ?", Integer.class, menuItemId);
    }

    private int reserved(long menuItemId) {
        return jdbc.queryForObject("SELECT reserved FROM inventory WHERE menu_item_id = ?", Integer.class, menuItemId);
    }

    private int roundCount(long sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_round WHERE table_session_id = ?", Integer.class, sessionId);
    }
}
