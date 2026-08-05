package io.github.yikunli774.ordering.checkout;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import io.github.yikunli774.ordering.table.ParticipantPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Checkout (结账): request the bill, view it, and pay it once for the whole table. */
@RestController
@RequestMapping("/api/v1/table-sessions/{sessionId}")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    public BillResponse checkout(@PathVariable long sessionId,
                                 @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        return toResponse(checkoutService.checkout(sessionId));
    }

    @GetMapping("/bill")
    public BillResponse bill(@PathVariable long sessionId,
                             @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        return toResponse(checkoutService.bill(sessionId));
    }

    @PostMapping("/pay")
    public BillResponse pay(@PathVariable long sessionId,
                            @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        return toResponse(checkoutService.pay(sessionId));
    }

    private static void requireOwnSession(ParticipantPrincipal participant, long sessionId) {
        if (participant.sessionId() != sessionId) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.PARTICIPANT_FORBIDDEN,
                    "This session does not belong to you");
        }
    }

    private static BillResponse toResponse(CheckoutService.Bill bill) {
        List<BillRound> rounds = bill.rounds().stream()
                .map(r -> new BillRound(r.roundNo(), r.status(), r.amount()))
                .toList();
        PaymentResponse payment = bill.payment() == null ? null
                : new PaymentResponse(bill.payment().id(), bill.payment().status(), bill.payment().amount());
        return new BillResponse(bill.sessionId(), bill.status(), bill.billAmount(), rounds, payment);
    }

    public record BillRound(int roundNo, String status, BigDecimal amount) {
    }

    public record PaymentResponse(long paymentId, String status, BigDecimal amount) {
    }

    public record BillResponse(long sessionId, String sessionStatus, BigDecimal billAmount,
                               List<BillRound> rounds, PaymentResponse payment) {
    }
}
