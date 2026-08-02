package io.github.yikunli774.ordering.table;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Anonymous customer entry point: scan a table QR to join its session. */
@RestController
@RequestMapping("/api/v1/table-sessions")
public class TableSessionController {

    private final TableSessionService service;

    public TableSessionController(TableSessionService service) {
        this.service = service;
    }

    @PostMapping("/join")
    public JoinResponse join(@Valid @RequestBody JoinRequest request) {
        TableSessionService.JoinResult result = service.join(request.tableToken());
        return new JoinResponse(
                result.sessionId(), result.participantId(),
                result.participantToken(), result.tableCode());
    }

    @GetMapping("/{sessionId}")
    public SessionResponse get(@PathVariable long sessionId) {
        TableSessionRepository.SessionView view = service.getSession(sessionId);
        return new SessionResponse(view.id(), view.tableCode(), view.status(), view.billAmount());
    }

    public record JoinRequest(@NotBlank String tableToken) {
    }

    public record JoinResponse(long sessionId, long participantId, String participantToken, String tableCode) {
    }

    public record SessionResponse(long sessionId, String tableCode, String status, BigDecimal billAmount) {
    }
}
