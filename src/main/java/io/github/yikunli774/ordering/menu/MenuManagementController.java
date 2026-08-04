package io.github.yikunli774.ordering.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Staff menu/inventory management, guarded per-action by fine-grained permissions. */
@RestController
@RequestMapping("/api/v1/management")
public class MenuManagementController {

    private final MenuService menuService;

    public MenuManagementController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/menu-items")
    @PreAuthorize("hasAuthority('menu:manage')")
    public List<AdminMenuItemResponse> listAll() {
        return menuService.staffMenu().stream()
                .map(item -> new AdminMenuItemResponse(
                        item.id(), item.code(), item.name(), item.category(),
                        item.price(), item.status(), item.available()))
                .toList();
    }

    @PostMapping("/menu-items")
    @PreAuthorize("hasAuthority('menu:manage')")
    public CreatedResponse create(@Valid @RequestBody CreateMenuItemRequest request) {
        long id = menuService.create(
                request.code(), request.name(),
                request.category() == null ? "DEFAULT" : request.category(),
                request.price(), request.initialStock());
        return new CreatedResponse(id);
    }

    @PatchMapping("/menu-items/{id}")
    @PreAuthorize("hasAuthority('menu:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable long id, @Valid @RequestBody UpdateMenuItemRequest request) {
        menuService.update(id, request.price(), request.status());
    }

    @PutMapping("/inventory/{menuItemId}")
    @PreAuthorize("hasAuthority('inventory:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setInventory(@PathVariable long menuItemId, @Valid @RequestBody SetInventoryRequest request) {
        menuService.setStock(menuItemId, request.available());
    }

    public record CreateMenuItemRequest(
            @NotBlank String code,
            @NotBlank String name,
            String category,
            @NotNull @PositiveOrZero BigDecimal price,
            @PositiveOrZero int initialStock) {
    }

    public record UpdateMenuItemRequest(
            @PositiveOrZero BigDecimal price,
            @Pattern(regexp = "AVAILABLE|SOLD_OUT|DELISTED") String status) {
    }

    public record SetInventoryRequest(@PositiveOrZero int available) {
    }

    public record CreatedResponse(long id) {
    }

    public record AdminMenuItemResponse(long id, String code, String name, String category,
                                        BigDecimal price, String status, int available) {
    }
}
