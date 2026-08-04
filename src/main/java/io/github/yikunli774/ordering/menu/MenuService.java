package io.github.yikunli774.ordering.menu;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MenuService {

    private final MenuRepository repository;

    public MenuService(MenuRepository repository) {
        this.repository = repository;
    }

    public List<MenuRepository.MenuItemView> customerMenu() {
        return repository.findForCustomer();
    }

    public List<MenuRepository.MenuItemAdminView> staffMenu() {
        return repository.findForStaff();
    }

    @Transactional
    public long create(String code, String name, String category, BigDecimal price, int initialStock) {
        long storeId = repository.defaultStoreId();
        long id;
        try {
            id = repository.createMenuItem(storeId, code, name, category, price);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.RESOURCE_ALREADY_EXISTS,
                    "A menu item with code '" + code + "' already exists");
        }
        repository.createInventory(id, initialStock);
        return id;
    }

    /** Partial update: null price/status keep their current value. */
    public void update(long id, BigDecimal price, String status) {
        MenuRepository.MenuItemAdminView current = repository.findAdminById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Menu item not found"));
        repository.updateMenuItem(id,
                price != null ? price : current.price(),
                status != null ? status : current.status());
    }

    public void setStock(long menuItemId, int available) {
        if (repository.setInventoryAvailable(menuItemId, available) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                    "Menu item / inventory not found");
        }
    }
}
