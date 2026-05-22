CREATE TABLE game_rps_rooms (
    id          BIGINT PRIMARY KEY COMMENT '雪花ID',
    family_id   BIGINT NOT NULL,
    creator_id  BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / REVEALED',
    revealed_at DATETIME,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    INDEX idx_family (family_id),
    INDEX idx_creator (creator_id)
);

CREATE TABLE game_rps_participants (
    id      BIGINT PRIMARY KEY COMMENT '雪花ID',
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    UNIQUE KEY uk_room_user (room_id, user_id),
    INDEX idx_room (room_id)
);

CREATE TABLE game_rps_gestures (
    id           BIGINT PRIMARY KEY COMMENT '雪花ID',
    room_id      BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    gesture_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256(gesture:salt)',
    gesture      VARCHAR(10)  COMMENT '揭晓后填入: ROCK/SCISSORS/PAPER',
    salt         VARCHAR(32)  COMMENT '揭晓后填入',
    submitted_at DATETIME NOT NULL,
    created_at   DATETIME NOT NULL,
    updated_at   DATETIME NOT NULL,
    UNIQUE KEY uk_room_user (room_id, user_id),
    INDEX idx_room (room_id)
);
