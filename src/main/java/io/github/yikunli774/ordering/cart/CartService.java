package io.github.yikunli774.ordering.cart;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import io.github.yikunli774.ordering.menu.MenuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final MenuRepository menuRepository;

    public CartService(CartRepository cartRepository, MenuRepository menuRepository) {
        this.cartRepository = cartRepository;
        this.menuRepository = menuRepository;
    }

    public record CartLine(long menuItemId, String code, String name,
                           BigDecimal price, int quantity, BigDecimal lineTotal) {
    }

    public record CartView(List<CartLine> items, BigDecimal total) {
    }

    public CartView changeItem(long sessionId, long menuItemId, int quantityDelta) {
        // Only adding needs an availability check; removing is always allowed.
        if (quantityDelta > 0) {
            MenuRepository.PricedItem item = menuRepository.findPricedById(menuItemId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Menu item not found"));
            if (!"AVAILABLE".equals(item.status())) {
                throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ITEM_UNAVAILABLE,
                        "'" + item.name() + "' is not available");
            }
        }
        cartRepository.mutate(sessionId, menuItemId, quantityDelta);
        return view(sessionId);
    }

    public CartView view(long sessionId) {
        Map<Long, Integer> quantities = cartRepository.getCart(sessionId);
        if (quantities.isEmpty()) {
            return new CartView(List.of(), BigDecimal.ZERO);
        }
        Map<Long, MenuRepository.PricedItem> items = menuRepository.findPricedByIds(quantities.keySet())
                .stream().collect(java.util.stream.Collectors.toMap(MenuRepository.PricedItem::id, i -> i));

        List<CartLine> lines = quantities.entrySet().stream()
                .filter(e -> items.containsKey(e.getKey()))
                .map(e -> {
                    MenuRepository.PricedItem item = items.get(e.getKey());
                    int quantity = e.getValue();
                    BigDecimal lineTotal = item.price().multiply(BigDecimal.valueOf(quantity));
                    return new CartLine(item.id(), item.code(), item.name(), item.price(), quantity, lineTotal);
                })
                .sorted(Comparator.comparing(CartLine::code))
                .toList();

        BigDecimal total = lines.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(lines, total);
    }

    public void clear(long sessionId) {
        cartRepository.clear(sessionId);
    }
}
