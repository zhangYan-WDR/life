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
    secondary_category VARCHAR(64) DEFAULT NULL,
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
    secondary_category VARCHAR(64) DEFAULT NULL,
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

CREATE TABLE IF NOT EXISTS recipes (
    id BIGINT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    base_servings DECIMAL(10, 2) NOT NULL,
    instructions TEXT,
    note VARCHAR(255) DEFAULT NULL,
    cover_url VARCHAR(512) DEFAULT NULL,
    cover_object_key VARCHAR(255) DEFAULT NULL,
    reference_url VARCHAR(1024) DEFAULT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_recipe_family_status (family_id, status)
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id BIGINT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT DEFAULT NULL,
    name_snapshot VARCHAR(64) NOT NULL,
    category_snapshot VARCHAR(64) NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_recipe_ingredients_recipe (recipe_id, sort_order)
);

CREATE TABLE IF NOT EXISTS meal_requests (
    id BIGINT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    note VARCHAR(255) DEFAULT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at DATETIME NOT NULL,
    decided_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_meal_request_family_status (family_id, status),
    KEY idx_meal_request_requester (requester_user_id, requested_at)
);

CREATE TABLE IF NOT EXISTS meal_request_recipes (
    id BIGINT PRIMARY KEY,
    meal_request_id BIGINT NOT NULL,
    recipe_id BIGINT NOT NULL,
    recipe_name_snapshot VARCHAR(64) NOT NULL,
    base_servings DECIMAL(10, 2) NOT NULL,
    target_servings DECIMAL(10, 2) NOT NULL,
    servings_multiplier DECIMAL(10, 4) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_meal_request_recipe_request (meal_request_id)
);

CREATE TABLE IF NOT EXISTS meal_request_responses (
    id BIGINT PRIMARY KEY,
    meal_request_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(255) DEFAULT NULL,
    decided_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_meal_request_user (meal_request_id, user_id),
    KEY idx_meal_response_user_decision (user_id, decision)
);
