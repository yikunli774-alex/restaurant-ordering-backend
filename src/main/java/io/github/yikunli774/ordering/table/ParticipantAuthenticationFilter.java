package io.github.yikunli774.ordering.table;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates anonymous customers by the {@code X-Participant-Token} header:
 * hash the token, look up the participant's session, and (if found) mark the
 * request as ROLE_PARTICIPANT. A missing/invalid token leaves the request
 * unauthenticated, so a protected customer endpoint then returns 401.
 *
 * Not a @Component: built by SecurityConfig, so it is not pulled into @WebMvcTest
 * slices (which auto-load Filter beans and lack this filter's DB dependency).
 */
public class ParticipantAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Participant-Token";

    private final TableSessionRepository repository;

    public ParticipantAuthenticationFilter(TableSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        boolean unauthenticated = existing == null || existing instanceof AnonymousAuthenticationToken;
        if (token != null && !token.isBlank() && unauthenticated) {
            String tokenHash = TableSessionService.sha256Hex(token);
            repository.findParticipant(tokenHash).ifPresent(identity -> {
                ParticipantPrincipal principal =
                        new ParticipantPrincipal(identity.participantId(), identity.sessionId());
                SecurityContextHolder.getContext()
                        .setAuthentication(new ParticipantAuthenticationToken(principal));
            });
        }
        filterChain.doFilter(request, response);
    }
}
