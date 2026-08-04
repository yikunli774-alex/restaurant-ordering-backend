-- Menu items (sellable dishes with a current price) and their inventory.

CREATE TABLE menu_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_menu_item_code UNIQUE (store_id, code),
    CONSTRAINT fk_menu_item_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT ck_menu_item_status CHECK (status IN ('AVAILABLE', 'SOLD_OUT', 'DELISTED')),
    CONSTRAINT ck_menu_item_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory (
    menu_item_id BIGINT UNSIGNED NOT NULL,
    available INT NOT NULL DEFAULT 0,
    reserved INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (menu_item_id),
    CONSTRAINT fk_inventory_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_item (id),
    CONSTRAINT ck_inventory_available CHECK (available >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed a few demo dishes with stock (store S01 was seeded in V3).
INSERT INTO menu_item (store_id, code, name, category, price)
SELECT id, 'D01', '宫保鸡丁', '热菜', 38.00 FROM store WHERE code = 'S01';
INSERT INTO menu_item (store_id, code, name, category, price)
SELECT id, 'D02', '麻婆豆腐', '热菜', 28.00 FROM store WHERE code = 'S01';
INSERT INTO menu_item (store_id, code, name, category, price)
SELECT id, 'D03', '米饭', '主食', 3.00 FROM store WHERE code = 'S01';
INSERT INTO menu_item (store_id, code, name, category, price)
SELECT id, 'D04', '可乐', '饮料', 6.00 FROM store WHERE code = 'S01';

INSERT INTO inventory (menu_item_id, available)
SELECT id, 100 FROM menu_item;
