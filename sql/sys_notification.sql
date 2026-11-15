-- 站内通知表
-- 在 MySQL（项目使用 edu_ai 库）中执行本文件即可建表。
-- 依赖表：sys_user(id)

CREATE TABLE IF NOT EXISTS sys_notification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    biz_type VARCHAR(40) NOT NULL,
    biz_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    read_status VARCHAR(12) NOT NULL DEFAULT 'UNREAD',
    outbound_status VARCHAR(32) NOT NULL DEFAULT 'FALLBACK_WEB_ONLY',
    sent_im_at DATETIME(3) DEFAULT NULL,
    read_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE KEY uk_notif_dedupe (
        user_id, biz_type, biz_id
    ),

    KEY idx_notif_user_read_created (
        user_id, read_status, created_at, id
    ),

    KEY idx_notif_user_created (
        user_id, created_at, id
    ),

    CONSTRAINT fk_notif_user
        FOREIGN KEY (user_id)
        REFERENCES sys_user(id)
        ON DELETE CASCADE
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4;
