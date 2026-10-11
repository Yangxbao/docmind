-- =============================================================
-- DocMind 智档 · 数据库初始化脚本
-- 使用方法（任选其一）：
--   1. 命令行: mysql -uroot -p < sql/init.sql
--   2. docker-compose 启动时会自动执行本目录下的 SQL
-- =============================================================

CREATE DATABASE IF NOT EXISTS docmind DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE docmind;

-- -------------------------------------------------------------
-- 用户表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL                COMMENT '用户名（唯一）',
    password    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 加密存储）',
    nickname    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '昵称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- -------------------------------------------------------------
-- 文档主表：一条记录 = 一个上传的文件 + 它提取出来的全文
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS document (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL                COMMENT '所属用户id',
    file_name   VARCHAR(255) NOT NULL                COMMENT '原始文件名',
    file_type   VARCHAR(20)  NOT NULL                COMMENT '文件类型: md/txt/pdf/image',
    file_path   VARCHAR(255) NOT NULL                COMMENT '磁盘相对路径',
    file_size   BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小(字节)',
    file_md5    VARCHAR(32)  NOT NULL DEFAULT ''     COMMENT '文件MD5（用于内容去重）',
    content     LONGTEXT                             COMMENT '提取出的全文文本（关键词检索用）',
    summary     VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '摘要（首个段落截取）',
    status      TINYINT      NOT NULL DEFAULT 0      COMMENT '处理状态: 0处理中 1完成 2失败',
    fail_reason VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '失败原因',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_user_md5 (user_id, file_md5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档表';

-- -------------------------------------------------------------
-- 分块表：一块 = 一段正文 + 它的向量（语义检索的最小单位）
-- 向量以 float[] 序列化成字节存入 BLOB，个人规模(几万块内)足够快
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_chunk (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id   BIGINT   NOT NULL                COMMENT '所属文档id',
    user_id       BIGINT   NOT NULL                COMMENT '所属用户id（冗余，检索时按用户一次捞全）',
    seq           INT      NOT NULL DEFAULT 0      COMMENT '块序号（同一文档内从0递增）',
    content       TEXT                             COMMENT '该块文本',
    embedding     BLOB                             COMMENT '向量（float[] 序列化字节）',
    embedding_dim INT      NOT NULL DEFAULT 0      COMMENT '向量维度（内置512 / bge-m3为1024）',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_doc (document_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档分块与向量表';

-- -------------------------------------------------------------
-- 标签表（自动生成 + 手动维护，同一用户内标签名唯一）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL                COMMENT '所属用户id',
    name        VARCHAR(50) NOT NULL                COMMENT '标签名',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tag (user_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标签表';

-- -------------------------------------------------------------
-- 文档-标签 关联表（多对多）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS document_tag (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id BIGINT NOT NULL COMMENT '文档id',
    tag_id      BIGINT NOT NULL COMMENT '标签id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_tag (document_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档标签关联表';

-- -------------------------------------------------------------
-- 搜索历史表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS search_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NOT NULL                COMMENT '用户id',
    keyword      VARCHAR(200) NOT NULL                COMMENT '搜索词',
    result_count INT          NOT NULL DEFAULT 0      COMMENT '本次结果条数',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '搜索历史表';
