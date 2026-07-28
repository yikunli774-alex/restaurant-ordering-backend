package io.github.yikunli774.ordering.staff;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Turns a signature-valid JWT into an authenticated principal, but ONLY if its
 * server-side Redis session still exists. Authorities come from the live session,
 * so revocation (logout / disable / role change) takes effect immediately.
 * If the session is gone, authentication fails (401) — it fails closed.
 *
 * Deliberately NOT a @Component: it is built by SecurityConfig. As a Spring
 * {@code Converter}, a @Component version would be pulled into @WebMvcTest slices
 * and drag in Redis dependencies those slices do not have.
 */
public class StaffJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final StaffSessionStore sessionStore;

    public StaffJwtAuthenticationConverter(StaffSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        StaffSessionStore.StaffSession session = sessionStore.find(jwt.getClaimAsString("sid"))
                .orElseThrow(() -> new InvalidBearerTokenException("Session revoked or expired"));
        List<GrantedAuthority> authorities = session.authorities().stream()
                .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
