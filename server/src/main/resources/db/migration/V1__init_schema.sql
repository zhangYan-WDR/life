CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    openid VARCHAR(64) NOT NULL UNIQUE,
    nickname VARCHAR(64) NOT NULL,
    avatar VARCHAR(255) DEFAULT NULL,
    current_family_id BIGINT DEFAULT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS families (
    id BIGINT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    invite_code VARCHAR(16) NOT NULL UNIQUE,
    owner_user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS family_members (
    id BIGINT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_family_user (family_id, user_id)
);

CREATE TABLE IF NOT EXISTS ingredient_catalog_system (
    id BIGINT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    default_unit VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ingredient_catalog_family (
    id BIGINT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    default_unit VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_family_ingredient (family_id, name)
);

CREATE TABLE IF NOT EXISTS fridge_items (
    id BIGINT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT DEFAULT NULL,
    name_snapshot VARCHAR(64) NOT NULL,
    category_snapshot VARCHAR(64) NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    produced_at DATE DEFAULT NULL,
    expires_at DATE DEFAULT NULL,
    location VARCHAR(64) DEFAULT NULL,
    note VARCHAR(255) DEFAULT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fridge_change_logs (
    id BIGINT PRIMARY KEY,
    fridge_item_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    quantity_change DECIMAL(10, 2) DEFAULT NULL,
    operator_user_id BIGINT NOT NULL,
    note VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reminder_subscriptions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_type VARCHAR(32) NOT NULL,
    accepted TINYINT(1) NOT NULL DEFAULT 0,
    last_accepted_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_template (user_id, template_type)
);

CREATE TABLE IF NOT EXISTS reminder_delivery_logs (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fridge_item_id BIGINT NOT NULL,
    reminder_type VARCHAR(16) NOT NULL,
    delivered_on DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_delivery_once (user_id, fridge_item_id, reminder_type, delivered_on)
);

INSERT INTO ingredient_catalog_system (id, name, category, default_unit, enabled, sort_order)
VALUES
    (1, '鸡蛋', '蛋奶', '个', 1, 1),
    (2, '牛奶', '蛋奶', '盒', 1, 2),
    (3, '番茄', '蔬菜', '个', 1, 3),
    (4, '黄瓜', '蔬菜', '根', 1, 4),
    (5, '猪肉', '肉类', '克', 1, 5),
    (6, '鸡胸肉', '肉类', '克', 1, 6),
    (7, '米饭', '主食', '份', 1, 7),
    (8, '面条', '主食', '份', 1, 8),
    (9, '可乐', '饮品', '瓶', 1, 9),
    (10, '酸奶', '饮品', '杯', 1, 10)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    category = VALUES(category),
    default_unit = VALUES(default_unit),
    enabled = VALUES(enabled),
    sort_order = VALUES(sort_order);
