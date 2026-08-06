package io.github.yikunli774.ordering.kitchen;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import io.github.yikunli774.ordering.order.InventoryRepository;
import io.github.yikunli774.ordering.order.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The kitchen's view of order rounds and the state-machine transitions on them. */
@Service
public class KitchenService {

    private final KitchenRepository kitchenRepository;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    public KitchenService(KitchenRepository kitchenRepository,
                          OrderRepository orderRepository, InventoryRepository inventoryRepository) {
        this.kitchenRepository = kitchenRepository;
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public record KitchenItem(String name, int quantity) {
    }

    public record KitchenRound(long roundId, int roundNo, String status, String tableCode, List<KitchenItem> items) {
    }

    public List<KitchenRound> queue() {
        return kitchenRepository.queue().stream()
                .map(q -> new KitchenRound(q.id(), q.roundNo(), q.status(), q.tableCode(),
                        orderRepository.findItems(q.id()).stream()
                                .map(it -> new KitchenItem(it.name(), it.quantity()))
                                .toList()))
                .toList();
    }

    public void prepare(long roundId) {
        advance(roundId, "CONFIRMED", "PREPARING");
    }

    public void ready(long roundId) {
        advance(roundId, "PREPARING", "READY");
    }

    public void complete(long roundId) {
        advance(roundId, "READY", "COMPLETED");
    }

    /** Cancel a not-yet-completed round and return its reserved stock to available. */
    @Transactional
    public void cancel(long roundId) {
        String current = requireRound(roundId);
        if ("CANCELLED".equals(current)) {
            return; // idempotent
        }
        if ("COMPLETED".equals(current)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.INVALID_STATE_TRANSITION,
                    "A completed round cannot be cancelled");
        }
        if (kitchenRepository.transition(roundId, current, "CANCELLED") == 1) {
            for (OrderRepository.RoundItem item : orderRepository.findItems(roundId)) {
                inventoryRepository.release(item.menuItemId(), item.quantity(),
                        "cancel:" + roundId + ":" + item.menuItemId());
            }
        }
    }

    private void advance(long roundId, String from, String to) {
        String current = requireRound(roundId);
        // CAS: succeeds only if the round is still in `from`; concurrent duplicate clicks lose.
        if (kitchenRepository.transition(roundId, from, to) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.INVALID_STATE_TRANSITION,
                    "Round is not in " + from + " state (currently " + current + ")");
        }
    }

    private String requireRound(long roundId) {
        return kitchenRepository.findStatus(roundId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Order round not found"));
    }
}
