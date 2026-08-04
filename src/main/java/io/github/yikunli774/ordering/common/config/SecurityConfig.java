package io.github.yikunli774.ordering.common.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.yikunli774.ordering.staff.StaffJwtAuthenticationConverter;
import io.github.yikunli774.ordering.staff.StaffSessionStore;
import io.github.yikunli774.ordering.table.ParticipantAuthenticationFilter;
import io.github.yikunli774.ordering.table.TableSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Staff security wiring:
 *   - password hashing ({@link PasswordEncoder});
 *   - JWT signing/verification ({@link JwtEncoder}/{@link JwtDecoder}, HMAC-SHA256);
 *   - which endpoints are public vs. require a valid token (the filter chain);
 *   - authentication delegated to {@link StaffJwtAuthenticationConverter}, which also
 *     checks the Redis session so revoked tokens are rejected immediately.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            StaffSessionStore sessionStore,
            TableSessionRepository tableSessionRepository) throws Exception {
        StaffJwtAuthenticationConverter jwtAuthenticationConverter =
                new StaffJwtAuthenticationConverter(sessionStore);
        ParticipantAuthenticationFilter participantAuthenticationFilter =
                new ParticipantAuthenticationFilter(tableSessionRepository);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/staff/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/table-sessions/join").permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Customers (participant token) vs staff (JWT) are kept strictly separate.
                        .requestMatchers("/api/v1/table-sessions/**").hasAuthority("ROLE_PARTICIPANT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu-items").hasAuthority("ROLE_PARTICIPANT")
                        .requestMatchers("/api/v1/staff/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_KITCHEN")
                        .requestMatchers("/api/v1/management/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_KITCHEN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterBefore(participantAuthenticationFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${security.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey(secret)));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(hmacKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKeySpec hmacKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
