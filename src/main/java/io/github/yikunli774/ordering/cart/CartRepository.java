package io.github.yikunli774.ordering.cart;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared cart for a table session, stored in Redis. One Lua script performs
 * each quantity change atomically (add delta, clamp at 0, drop the row when it
 * hits 0, bump a version) so concurrent edits by several participants at the same
 * table cannot lose updates.
 */
@Repository
public class CartRepository {

    private static final String MUTATE_LUA = """
            local current = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            local newQty = current + tonumber(ARGV[2])
            if newQty <= 0 then
              redis.call('HDEL', KEYS[1], ARGV[1])
              newQty = 0
            else
              redis.call('HSET', KEYS[1], ARGV[1], newQty)
            end
            local version = redis.call('HINCRBY', KEYS[2], 'version', 1)
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return {newQty, version}
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<List> mutateScript;
    private final long ttlSeconds;

    public CartRepository(StringRedisTemplate redis, @Value("${cart.ttl-seconds}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
        this.mutateScript = RedisScript.of(MUTATE_LUA, List.class);
    }

    public record Mutation(int quantity, long version) {
    }

    public Mutation mutate(long sessionId, long menuItemId, int delta) {
        List<?> result = redis.execute(mutateScript,
                List.of(cartKey(sessionId), metaKey(sessionId)),
                String.valueOf(menuItemId), String.valueOf(delta), String.valueOf(ttlSeconds));
        return new Mutation(
                ((Number) result.get(0)).intValue(),
                ((Number) result.get(1)).longValue());
    }

    public Map<Long, Integer> getCart(long sessionId) {
        Map<Object, Object> raw = redis.opsForHash().entries(cartKey(sessionId));
        Map<Long, Integer> cart = new LinkedHashMap<>();
        raw.forEach((k, v) -> cart.put(Long.parseLong(k.toString()), Integer.parseInt(v.toString())));
        return cart;
    }

    public void clear(long sessionId) {
        redis.delete(List.of(cartKey(sessionId), metaKey(sessionId)));
    }

    private static String cartKey(long sessionId) {
        return "cart:" + sessionId;
    }

    private static String metaKey(long sessionId) {
        return "cart:meta:" + sessionId;
    }
}
