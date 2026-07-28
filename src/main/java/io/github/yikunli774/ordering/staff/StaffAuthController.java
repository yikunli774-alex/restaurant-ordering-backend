package io.github.yikunli774.ordering.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffAuthController {

    private final StaffAuthService authService;

    public StaffAuthController(StaffAuthService authService) {
        this.authService = authService;
    }

    /** Public: exchange username + password for a signed access token. */
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        StaffAuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
    }

    /** Protected: echoes who the caller is, proving the token works. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(Long.parseLong(jwt.getSubject()), jwt.getClaimAsStringList("authorities"));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }

    public record MeResponse(long staffId, List<String> authorities) {
    }
}
