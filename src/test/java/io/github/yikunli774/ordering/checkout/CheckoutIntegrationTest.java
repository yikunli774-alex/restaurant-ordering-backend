package io.github.yikunli774.ordering.checkout;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class CheckoutIntegrationTest extends AbstractIntegrationTest {

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
    void checkoutComputesBillFromRoundsAndPendsSession() {
        Session s = join("T02");
        addRound(s, "K1", "D01", 2); // 76
        addRound(s, "K2", "D03", 1); // 3

        JsonPath bill = checkout(s);
        assertThat(bill.getString("sessionStatus")).isEqualTo("PENDING_PAYMENT");
        assertThat(bill.getDouble("billAmount")).isEqualTo(79.0);
        assertThat(bill.getList("rounds")).hasSize(2);
        assertThat(bill.getString("payment.status")).isEqualTo("PENDING");
    }

    @Test
    void payingClosesTheSessionAndSucceedsThePayment() {
        Session s = join("T02");
        addRound(s, "K1", "D01", 1); // 38
        checkout(s);

        JsonPath paid = pay(s);
        assertThat(paid.getString("sessionStatus")).isEqualTo("CLOSED");
        assertThat(paid.getString("payment.status")).isEqualTo("SUCCEEDED");
    }

    @Test
    void checkoutWithNoRoundsIsRejected() {
        Session s = join("T02");
        RestAssured.given().port(port).header("X-Participant-Token", s.token())
                .when().post(url(s, "checkout"))
                .then().statusCode(409).body("code", equalTo("NO_BILLABLE_ROUNDS"));
    }

    @Test
    void cannotAddARoundWhileCheckingOut() {
        Session s = join("T02");
        addRound(s, "K1", "D01", 1);
        checkout(s);

        addToCart(s, "D03", 1);
        RestAssured.given().port(port)
                .header("X-Participant-Token", s.token())
                .header("Idempotency-Key", "K2")
                .when().post(url(s, "orders"))
                .then().statusCode(409).body("code", equalTo("SESSION_IN_CHECKOUT"));
    }

    @Test
    void concurrentCheckoutsProduceOneBillAndOnePayment() throws Exception {
        Session s = join("T02");
        addRound(s, "K1", "D01", 2); // 76

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Double>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return checkout(s).getDouble("billAmount");
            }));
        }
        start.countDown();
        Set<Double> amounts = new HashSet<>();
        for (Future<Double> f : futures) {
            amounts.add(f.get());
        }
        pool.shutdown();

        assertThat(amounts).containsExactly(76.0);
        Integer payments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE table_session_id = ?", Integer.class, s.sessionId());
        assertThat(payments).isEqualTo(1);
    }

    @Test
    void afterPaymentTheTableCanHostANewSession() {
        Session first = join("T02");
        addRound(first, "K1", "D01", 1);
        checkout(first);
        pay(first);

        Session second = join("T02");
        assertThat(second.sessionId()).isNotEqualTo(first.sessionId());
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

    private void addToCart(Session s, String menuCode, int delta) {
        RestAssured.given().port(port)
                .header("X-Participant-Token", s.token())
                .contentType("application/json")
                .body("{\"quantityDelta\":" + delta + "}")
                .when().put("/api/v1/table-sessions/" + s.sessionId() + "/cart/items/" + menuId(menuCode))
                .then().statusCode(200);
    }

    private void addRound(Session s, String idempotencyKey, String menuCode, int quantity) {
        addToCart(s, menuCode, quantity);
        RestAssured.given().port(port)
                .header("X-Participant-Token", s.token())
                .header("Idempotency-Key", idempotencyKey)
                .when().post(url(s, "orders"))
                .then().statusCode(200);
    }

    private JsonPath checkout(Session s) {
        return RestAssured.given().port(port).header("X-Participant-Token", s.token())
                .when().post(url(s, "checkout"))
                .then().statusCode(200).extract().jsonPath();
    }

    private JsonPath pay(Session s) {
        return RestAssured.given().port(port).header("X-Participant-Token", s.token())
                .when().post(url(s, "pay"))
                .then().statusCode(200).extract().jsonPath();
    }

    private String url(Session s, String suffix) {
        return "/api/v1/table-sessions/" + s.sessionId() + "/" + suffix;
    }

    private long menuId(String code) {
        return jdbc.queryForObject("SELECT id FROM menu_item WHERE code = ?", Long.class, code);
    }
}
