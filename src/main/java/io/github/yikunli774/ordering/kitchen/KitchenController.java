package io.github.yikunli774.ordering.kitchen;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Kitchen board (staff only). Each action is guarded by its own fine-grained permission. */
@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('order:read')")
    public List<RoundResponse> queue() {
        return kitchenService.queue().stream()
                .map(r -> new RoundResponse(r.roundId(), r.roundNo(), r.status(), r.tableCode(),
                        r.items().stream().map(i -> new ItemResponse(i.name(), i.quantity())).toList()))
                .toList();
    }

    @PostMapping("/orders/{roundId}/prepare")
    @PreAuthorize("hasAuthority('order:prepare')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void prepare(@PathVariable long roundId) {
        kitchenService.prepare(roundId);
    }

    @PostMapping("/orders/{roundId}/ready")
    @PreAuthorize("hasAuthority('order:ready')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ready(@PathVariable long roundId) {
        kitchenService.ready(roundId);
    }

    @PostMapping("/orders/{roundId}/complete")
    @PreAuthorize("hasAuthority('order:complete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable long roundId) {
        kitchenService.complete(roundId);
    }

    @PostMapping("/orders/{roundId}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long roundId) {
        kitchenService.cancel(roundId);
    }

    public record ItemResponse(String name, int quantity) {
    }

    public record RoundResponse(long roundId, int roundNo, String status, String tableCode,
                                List<ItemResponse> items) {
    }
}
