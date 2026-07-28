package io.github.yikunli774.ordering.staff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side staff sessions in Redis. A JWT only carries a session id (sid);
 * the real authorities live here, so logout / disable / role change can revoke
 * access immediately by deleting the session — a JWT alone cannot be un-issued.
 */
@Component
public class StaffSessionStore {

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public StaffSessionStore(
            StringRedisTemplate redis,
            @Value("${security.session.ttl-seconds}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public record StaffSession(long staffId, List<String> authorities) {
    }

    public String create(long staffId, List<String> authorities) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String key = key(sessionId);
        redis.opsForHash().put(key, "staffId", String.valueOf(staffId));
        redis.opsForHash().put(key, "authorities", String.join(",", authorities));
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
        return sessionId;
    }

    public Optional<StaffSession> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        String key = key(sessionId);
        Object staffId = redis.opsForHash().get(key, "staffId");
        if (staffId == null) {
            return Optional.empty();
        }
        Object authoritiesCsv = redis.opsForHash().get(key, "authorities");
        String csv = authoritiesCsv == null ? "" : authoritiesCsv.toString();
        List<String> authorities = csv.isEmpty() ? List.of() : List.of(csv.split(","));
        return Optional.of(new StaffSession(Long.parseLong(staffId.toString()), authorities));
    }

    public void delete(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            redis.delete(key(sessionId));
        }
    }

    private static String key(String sessionId) {
        return "staff:session:" + sessionId;
    }
}
