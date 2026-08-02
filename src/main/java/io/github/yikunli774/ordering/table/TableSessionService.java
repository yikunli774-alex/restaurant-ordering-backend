package io.github.yikunli774.ordering.table;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Verifies a scanned table code, joins/creates its active session, and issues a participant token. */
@Service
public class TableSessionService {

    private final TableCodeSigner signer;
    private final TableSessionRepository repository;
    private final SecureRandom random = new SecureRandom();

    public TableSessionService(TableCodeSigner signer, TableSessionRepository repository) {
        this.signer = signer;
        this.repository = repository;
    }

    public record JoinResult(long sessionId, long participantId, String participantToken, String tableCode) {
    }

    // READ_COMMITTED so the loser of a create-session race re-reads and SEES the
    // winner's just-committed session (REPEATABLE READ's snapshot would hide it).
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JoinResult join(String signedTableToken) {
        String code = signer.verify(signedTableToken);
        TableSessionRepository.DiningTable table = repository.findTableByCode(code)
                .filter(t -> "ACTIVE".equals(t.status()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Table not found"));

        long sessionId = repository.findOrCreateActiveSession(table.id());
        String rawToken = randomToken();
        long participantId = repository.insertParticipant(sessionId, sha256Hex(rawToken));
        return new JoinResult(sessionId, participantId, rawToken, code);
    }

    public TableSessionRepository.SessionView getSession(long sessionId) {
        return repository.findSession(sessionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Session not found"));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
