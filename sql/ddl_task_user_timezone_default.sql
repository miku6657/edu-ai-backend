-- 兜底：为 ddl_task.user_timezone 增加默认时区
-- 目的：任何 DDL 创建入口（手动创建、AI 提取、草案 CREATE_NEW）在未显式设置时区时，
--       插入语句即使省略 user_timezone 也不会再报 "doesn't have a default value"。
-- 注意：本语句会把该列类型显式改为 VARCHAR(100)。执行前建议先
--       SHOW COLUMNS FROM ddl_task LIKE 'user_timezone';
--       确认原列长度，若原长度不是 100 请保持一致。
-- 已有数据不受影响。

ALTER TABLE ddl_task
MODIFY COLUMN user_timezone VARCHAR(100)
NOT NULL DEFAULT 'Asia/Shanghai'
COMMENT '用户时区';
