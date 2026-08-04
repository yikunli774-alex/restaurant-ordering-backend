package io.github.yikunli774.ordering.menu;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Customer-facing menu (requires a participant token; see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/menu-items")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public List<MenuItemResponse> list() {
        return menuService.customerMenu().stream()
                .map(item -> new MenuItemResponse(
                        item.id(), item.code(), item.name(),
                        item.category(), item.price(), item.soldOut()))
                .toList();
    }

    public record MenuItemResponse(long id, String code, String name, String category,
                                   BigDecimal price, boolean soldOut) {
    }
}
