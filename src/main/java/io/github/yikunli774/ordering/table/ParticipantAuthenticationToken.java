package io.github.yikunli774.ordering.table;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Spring Security authentication for an anonymous customer, carrying only ROLE_PARTICIPANT. */
public class ParticipantAuthenticationToken extends AbstractAuthenticationToken {

    private final ParticipantPrincipal principal;

    public ParticipantAuthenticationToken(ParticipantPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public ParticipantPrincipal getPrincipal() {
        return principal;
    }
}
