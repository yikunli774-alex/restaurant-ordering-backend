package io.github.yikunli774.ordering.order;

import io.github.yikunli774.ordering.cart.CartRepository;
import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import io.github.yikunli774.ordering.menu.MenuRepository;
import io.github.yikunli774.ordering.table.TableSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Submits a cart as one order round (加菜): reserves stock, snapshots prices, is idempotent. */
@Service
public class OrderService {

    private static final String SCOPE = "ORDER_SUBMIT";

    private final CartRepository cartRepository;
    private final MenuRepository menuRepository;
    private final TableSessionRepository sessionRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;

    public OrderService(CartRepository cartRepository, MenuRepository menuRepository,
                        TableSessionRepository sessionRepository, InventoryRepository inventoryRepository,
                        OrderRepository orderRepository) {
        this.cartRepository = cartRepository;
        this.menuRepository = menuRepository;
        this.sessionRepository = sessionRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
    }

    public record RoundLine(long menuItemId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record RoundResult(long roundId, int roundNo, String status, BigDecimal amount, List<RoundLine> items) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoundResult submitRound(long sessionId, String idempotencyKey) {
        // 1. A retried request with the same key returns the original round — never a second one.
        var replay = orderRepository.findIdempotentResult(SCOPE, idempotencyKey);
        if (replay.isPresent()) {
            return loadResult(replay.get());
        }

        // 2. New rounds are only allowed while the session is OPEN (not during checkout).
        String status = sessionRepository.findStatus(sessionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Session not found"));
        if (!"OPEN".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.SESSION_IN_CHECKOUT,
                    "Session is not open for new orders");
        }

        // 3. Take the cart atomically so a concurrent submission of the same cart gets nothing.
        Map<Long, Integer> cart = cartRepository.getAndClear(sessionId);
        if (cart.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.EMPTY_CART, "Cart is empty");
        }

        try {
            Map<Long, MenuRepository.PricedItem> priced = menuRepository.findPricedByIds(cart.keySet())
                    .stream().collect(Collectors.toMap(MenuRepository.PricedItem::id, item -> item));

            List<RoundLine> lines = new ArrayList<>();
            BigDecimal amount = BigDecimal.ZERO;
            for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
                long menuItemId = entry.getKey();
                int quantity = entry.getValue();
                MenuRepository.PricedItem item = priced.get(menuItemId);
                if (item == null || !"AVAILABLE".equals(item.status())) {
                    throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ITEM_UNAVAILABLE,
                            "An item in the cart is no longer available");
                }
                boolean reserved = inventoryRepository.reserve(
                        menuItemId, quantity, idempotencyKey + ":" + menuItemId);
                if (!reserved) {
                    throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.INSUFFICIENT_STOCK,
                            "'" + item.name() + "' is out of stock");
                }
                BigDecimal lineTotal = item.price().multiply(BigDecimal.valueOf(quantity));
                amount = amount.add(lineTotal);
                lines.add(new RoundLine(menuItemId, item.name(), item.price(), quantity, lineTotal));
            }

            int roundNo = orderRepository.nextRoundNo(sessionId);
            long roundId = orderRepository.insertRound(sessionId, roundNo, amount);
            for (RoundLine line : lines) {
                orderRepository.insertItem(roundId, line.menuItemId(), line.name(),
                        line.unitPrice(), line.quantity(), line.lineTotal());
            }
            orderRepository.insertIdempotency(SCOPE, idempotencyKey, roundId);

            return new RoundResult(roundId, roundNo, "CONFIRMED", amount, lines);
        } catch (RuntimeException failure) {
            // Give the cart back so the customer can adjust and retry (stock changes roll back with the tx).
            cartRepository.restore(sessionId, cart);
            throw failure;
        }
    }

    public List<RoundResult> roundsForSession(long sessionId) {
        return orderRepository.findRoundsForSession(sessionId).stream()
                .map(round -> loadResult(round.id()))
                .toList();
    }

    private RoundResult loadResult(long roundId) {
        OrderRepository.RoundSummary round = orderRepository.findRound(roundId)
                .orElseThrow(() -> new IllegalStateException("Round vanished: " + roundId));
        List<RoundLine> items = orderRepository.findItems(roundId).stream()
                .map(item -> new RoundLine(item.menuItemId(), item.name(),
                        item.unitPrice(), item.quantity(), item.lineTotal()))
                .toList();
        return new RoundResult(round.id(), round.roundNo(), round.status(), round.amount(), items);
    }
}
