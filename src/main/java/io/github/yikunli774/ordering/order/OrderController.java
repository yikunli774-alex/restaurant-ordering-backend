package io.github.yikunli774.ordering.order;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import io.github.yikunli774.ordering.table.ParticipantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Submitting rounds (加菜) and listing this session's rounds. Participant-scoped. */
@RestController
@RequestMapping("/api/v1/table-sessions/{sessionId}")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public RoundResponse submit(@PathVariable long sessionId,
                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        return toResponse(orderService.submitRound(sessionId, idempotencyKey));
    }

    @GetMapping("/rounds")
    public List<RoundResponse> rounds(@PathVariable long sessionId,
                                      @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        return orderService.roundsForSession(sessionId).stream().map(OrderController::toResponse).toList();
    }

    private static void requireOwnSession(ParticipantPrincipal participant, long sessionId) {
        if (participant.sessionId() != sessionId) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.PARTICIPANT_FORBIDDEN,
                    "This session does not belong to you");
        }
    }

    private static RoundResponse toResponse(OrderService.RoundResult result) {
        List<RoundItemResponse> items = result.items().stream()
                .map(l -> new RoundItemResponse(l.menuItemId(), l.name(), l.unitPrice(), l.quantity(), l.lineTotal()))
                .toList();
        return new RoundResponse(result.roundId(), result.roundNo(), result.status(), result.amount(), items);
    }

    public record RoundItemResponse(long menuItemId, String name, BigDecimal unitPrice,
                                    int quantity, BigDecimal lineTotal) {
    }

    public record RoundResponse(long roundId, int roundNo, String status, BigDecimal amount,
                                List<RoundItemResponse> items) {
    }
}
