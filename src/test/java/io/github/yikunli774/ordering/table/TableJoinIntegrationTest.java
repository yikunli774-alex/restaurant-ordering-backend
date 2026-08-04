package io.github.yikunli774.ordering.table;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import io.restassured.RestAssured;
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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

class TableJoinIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TableCodeSigner signer;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        io.github.yikunli774.ordering.support.TestData.resetSessions(jdbc);
    }

    @Test
    void joinCreatesSessionAndIssuesParticipantToken() {
        RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"tableToken\":\"" + signer.sign("T02") + "\"}")
                .when()
                .post("/api/v1/table-sessions/join")
                .then()
                .statusCode(200)
                .body("sessionId", notNullValue())
                .body("participantToken", notNullValue())
                .body("tableCode", equalTo("T02"));
    }

    @Test
    void secondJoinOnSameTableReusesTheSession() {
        String token = signer.sign("T02");
        long first = joinSessionId(token);
        long second = joinSessionId(token);

        assertThat(first).isEqualTo(second);
        Integer participants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM participant WHERE table_session_id = ?", Integer.class, first);
        assertThat(participants).isEqualTo(2);
    }

    @Test
    void tamperedTableTokenIsRejected() {
        RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"tableToken\":\"T02.not-a-real-signature\"}")
                .when()
                .post("/api/v1/table-sessions/join")
                .then()
                .statusCode(400)
                .body("code", equalTo("TABLE_CODE_INVALID"));
    }

    @Test
    void validlySignedButUnknownTableIsNotFound() {
        RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"tableToken\":\"" + signer.sign("T99") + "\"}")
                .when()
                .post("/api/v1/table-sessions/join")
                .then()
                .statusCode(404);
    }

    @Test
    void getSessionReturnsStatusForItsParticipant() {
        Joined joined = join("T02");
        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", joined.participantToken())
                .when()
                .get("/api/v1/table-sessions/" + joined.sessionId())
                .then()
                .statusCode(200)
                .body("tableCode", equalTo("T02"))
                .body("status", equalTo("OPEN"));
    }

    @Test
    void getSessionWithoutParticipantTokenIsUnauthorized() {
        Joined joined = join("T02");
        RestAssured.given()
                .port(port)
                .when()
                .get("/api/v1/table-sessions/" + joined.sessionId())
                .then()
                .statusCode(401);
    }

    @Test
    void participantCannotViewAnotherTablesSession() {
        Joined mine = join("T02");
        Joined other = join("T01");
        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", mine.participantToken())
                .when()
                .get("/api/v1/table-sessions/" + other.sessionId())
                .then()
                .statusCode(403)
                .body("code", equalTo("PARTICIPANT_FORBIDDEN"));
    }

    @Test
    void staffTablesEndpointNeedsAuthAndReturnsQrTokens() {
        // Without a staff token: rejected.
        RestAssured.given().port(port)
                .when().get("/api/v1/staff/tables")
                .then().statusCode(401);

        // With a manager token: lists tables and their signed QR tokens.
        String jwt = login("manager", "manager123");
        RestAssured.given()
                .port(port)
                .header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/v1/staff/tables")
                .then()
                .statusCode(200)
                .body("code", hasItems("T01", "T02"))
                .body("qrToken", everyItem(notNullValue()));
    }

    @Test
    void participantTokenCannotAccessStaffEndpoints() {
        Joined joined = join("T02");
        RestAssured.given()
                .port(port)
                .header("X-Participant-Token", joined.participantToken())
                .when()
                .get("/api/v1/staff/tables")
                .then()
                .statusCode(403);
    }

    @Test
    void concurrentJoinsProduceExactlyOneSession() throws Exception {
        String token = signer.sign("T02");
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return joinSessionId(token);
            }));
        }
        start.countDown(); // release all at once to maximize contention

        Set<Long> sessionIds = new HashSet<>();
        for (Future<Long> future : futures) {
            sessionIds.add(future.get());
        }
        pool.shutdown();

        // Every concurrent join landed in the SAME single session.
        assertThat(sessionIds).hasSize(1);
        long tableId = jdbc.queryForObject("SELECT id FROM dining_table WHERE code = 'T02'", Long.class);
        Integer activeSessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM table_session WHERE dining_table_id = ? AND status IN ('OPEN','PENDING_PAYMENT')",
                Integer.class, tableId);
        Integer participants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM participant WHERE table_session_id = ?",
                Integer.class, sessionIds.iterator().next());
        assertThat(activeSessions).isEqualTo(1);
        assertThat(participants).isEqualTo(threads);
    }

    private Joined join(String tableCode) {
        var jsonPath = RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"tableToken\":\"" + signer.sign(tableCode) + "\"}")
                .when()
                .post("/api/v1/table-sessions/join")
                .then()
                .statusCode(200)
                .extract().jsonPath();
        return new Joined(jsonPath.getLong("sessionId"), jsonPath.getString("participantToken"));
    }

    private record Joined(long sessionId, String participantToken) {
    }

    private long joinSessionId(String tableToken) {
        return RestAssured.given()
                .port(port)
                .contentType("application/json")
                .body("{\"tableToken\":\"" + tableToken + "\"}")
                .when()
                .post("/api/v1/table-sessions/join")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("sessionId");
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
