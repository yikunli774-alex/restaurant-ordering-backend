package io.github.yikunli774.ordering.cart;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import io.github.yikunli774.ordering.table.ParticipantPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** The shared cart for a table session. Any participant of that session may edit it. */
@RestController
@RequestMapping("/api/v1/table-sessions/{sessionId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PutMapping("/items/{menuItemId}")
    public CartResponse changeItem(@PathVariable long sessionId,
                                   @PathVariable long menuItemId,
                                   @Valid @RequestBody CartItemRequest request,
                                   @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        return toResponse(cartService.changeItem(sessionId, menuItemId, request.quantityDelta()));
    }

    @GetMapping
    public CartResponse view(@PathVariable long sessionId,
                             @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        return toResponse(cartService.view(sessionId));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable long sessionId,
                      @AuthenticationPrincipal ParticipantPrincipal participant) {
        requireOwnSession(participant, sessionId);
        cartService.clear(sessionId);
    }

    private static void requireOwnSession(ParticipantPrincipal participant, long sessionId) {
        if (participant.sessionId() != sessionId) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.PARTICIPANT_FORBIDDEN,
                    "This session does not belong to you");
        }
    }

    private static CartResponse toResponse(CartService.CartView view) {
        List<CartLineResponse> lines = view.items().stream()
                .map(l -> new CartLineResponse(l.menuItemId(), l.code(), l.name(),
                        l.price(), l.quantity(), l.lineTotal()))
                .toList();
        return new CartResponse(lines, view.total());
    }

    public record CartItemRequest(int quantityDelta) {
    }

    public record CartLineResponse(long menuItemId, String code, String name,
                                   BigDecimal price, int quantity, BigDecimal lineTotal) {
    }

    public record CartResponse(List<CartLineResponse> items, BigDecimal total) {
    }
}
