-- Menu detail fields (image lives in object storage in real life; we store only its URL),
-- and a new kitchen permission to cancel a round.

ALTER TABLE menu_item ADD COLUMN description VARCHAR(500) NULL AFTER name;
ALTER TABLE menu_item ADD COLUMN image_url VARCHAR(255) NULL AFTER description;

UPDATE menu_item SET description = '招牌川菜,鸡丁配花生宫保汁', image_url = 'https://cdn.example.com/dishes/D01.jpg' WHERE code = 'D01';
UPDATE menu_item SET description = '嫩豆腐配麻辣肉末', image_url = 'https://cdn.example.com/dishes/D02.jpg' WHERE code = 'D02';
UPDATE menu_item SET description = '东北珍珠米,一碗', image_url = 'https://cdn.example.com/dishes/D03.jpg' WHERE code = 'D03';
UPDATE menu_item SET description = '冰镇可乐 330ml', image_url = 'https://cdn.example.com/dishes/D04.jpg' WHERE code = 'D04';

-- New permission; grant to both KITCHEN and MANAGER (MANAGER's V2 grant only covered
-- permissions that existed then, so new ones must be granted explicitly).
INSERT INTO permission (code, description) VALUES ('order:cancel', 'Cancel an order round');
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'order:cancel'
WHERE r.code IN ('KITCHEN', 'MANAGER');
