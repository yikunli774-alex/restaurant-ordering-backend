package io.github.yikunli774.ordering.staff;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Verifies staff credentials, opens a server-side Redis session, and mints a
 * short-lived JWT that carries only the session id (sid) — not the authorities.
 */
@Service
public class StaffAuthService {

    private final StaffRepository repository;
    private final StaffSessionStore sessionStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long accessTtlSeconds;

    public StaffAuthService(
            StaffRepository repository,
            StaffSessionStore sessionStore,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.access-ttl-seconds}") long accessTtlSeconds) {
        this.repository = repository;
        this.sessionStore = sessionStore;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public record LoginResult(String accessToken, long expiresInSeconds) {
    }

    public LoginResult login(String username, String password) {
        // Same error whether the user is missing or the password is wrong, so we
        // never reveal which usernames exist.
        StaffRepository.StaffAuth staff = repository.findByUsername(username)
                .filter(s -> "ACTIVE".equals(s.status()))
                .filter(s -> passwordEncoder.matches(password, s.passwordHash()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ApiErrorCode.STAFF_CREDENTIALS_INVALID,
                        "Invalid username or password"));

        List<String> authorities = repository.findAuthorities(staff.id());
        String sessionId = sessionStore.create(staff.id(), authorities);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(staff.id()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSeconds))
                .claim("sid", sessionId)
                .build();
        String token = jwtEncoder.encode(
                        JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new LoginResult(token, accessTtlSeconds);
    }

    public void logout(String sessionId) {
        sessionStore.delete(sessionId);
    }
}
