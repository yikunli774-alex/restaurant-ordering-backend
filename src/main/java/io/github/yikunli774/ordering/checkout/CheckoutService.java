package io.github.yikunli774.ordering.checkout;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import io.github.yikunli774.ordering.order.OrderService;
import io.github.yikunli774.ordering.table.TableSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Checkout and simulated payment for the whole table bill (一客一结账). */
@Service
public class CheckoutService {

    private final CheckoutRepository checkoutRepository;
    private final TableSessionRepository sessionRepository;
    private final OrderService orderService;

    public CheckoutService(CheckoutRepository checkoutRepository,
                           TableSessionRepository sessionRepository, OrderService orderService) {
        this.checkoutRepository = checkoutRepository;
        this.sessionRepository = sessionRepository;
        this.orderService = orderService;
    }

    public record Bill(long sessionId, String status, BigDecimal billAmount,
                       List<OrderService.RoundResult> rounds, CheckoutRepository.PaymentInfo payment) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Bill checkout(long sessionId) {
        String status = requireSession(sessionId);
        if ("OPEN".equals(status)) {
            if (checkoutRepository.billableRoundCount(sessionId) == 0) {
                throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.NO_BILLABLE_ROUNDS,
                        "There is nothing to check out yet");
            }
            BigDecimal amount = checkoutRepository.billableAmount(sessionId);
            // CAS: only the first concurrent checkout moves OPEN -> PENDING_PAYMENT.
            if (checkoutRepository.startCheckout(sessionId, amount) == 1) {
                checkoutRepository.insertPayment(sessionId, amount);
            }
            // else: another request won the race; the bill is already pending.
        } else if (!"PENDING_PAYMENT".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Session is already closed");
        }
        return buildBill(sessionId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Bill pay(long sessionId) {
        String status = requireSession(sessionId);
        if ("CLOSED".equals(status)) {
            return buildBill(sessionId); // already paid — idempotent
        }
        if (!"PENDING_PAYMENT".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Check out before paying");
        }
        // CAS: only the first concurrent payment closes the session.
        if (checkoutRepository.closeAsPaid(sessionId) == 1) {
            checkoutRepository.markPaymentSucceeded(sessionId);
        }
        return buildBill(sessionId);
    }

    public Bill bill(long sessionId) {
        requireSession(sessionId);
        return buildBill(sessionId);
    }

    private String requireSession(long sessionId) {
        return sessionRepository.findStatus(sessionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Session not found"));
    }

    private Bill buildBill(long sessionId) {
        TableSessionRepository.SessionView view = sessionRepository.findSession(sessionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Session not found"));
        List<OrderService.RoundResult> rounds = orderService.roundsForSession(sessionId);
        CheckoutRepository.PaymentInfo payment = checkoutRepository.findLatestPayment(sessionId).orElse(null);
        return new Bill(sessionId, view.status(), view.billAmount(), rounds, payment);
    }
}
