-- 添加向量同步状态字段到各个表
-- 执行时间：2024-01-01

-- 为projects表添加向量化状态字段
ALTER TABLE projects ADD COLUMN vector_status VARCHAR(20) DEFAULT 'NOT_INDEXED' COMMENT '向量化状态：NOT_INDEXED, PENDING, PROCESSING, INDEXED, FAILED';
ALTER TABLE projects ADD COLUMN vector_version BIGINT DEFAULT 1 COMMENT '向量化版本号';
ALTER TABLE projects ADD COLUMN vector_last_sync DATETIME NULL COMMENT '最后同步时间';
ALTER TABLE projects ADD COLUMN vector_error_message TEXT NULL COMMENT '向量化错误信息';

-- 为chapters表添加向量化状态字段
ALTER TABLE chapters ADD COLUMN vector_status VARCHAR(20) DEFAULT 'NOT_INDEXED' COMMENT '向量化状态：NOT_INDEXED, PENDING, PROCESSING, INDEXED, FAILED';
ALTER TABLE chapters ADD COLUMN vector_version BIGINT DEFAULT 1 COMMENT '向量化版本号';
ALTER TABLE chapters ADD COLUMN vector_last_sync DATETIME NULL COMMENT '最后同步时间';
ALTER TABLE chapters ADD COLUMN vector_error_message TEXT NULL COMMENT '向量化错误信息';

-- 为characters表添加向量化状态字段
ALTER TABLE characters ADD COLUMN vector_status VARCHAR(20) DEFAULT 'NOT_INDEXED' COMMENT '向量化状态：NOT_INDEXED, PENDING, PROCESSING, INDEXED, FAILED';
ALTER TABLE characters ADD COLUMN vector_version BIGINT DEFAULT 1 COMMENT '向量化版本号';
ALTER TABLE characters ADD COLUMN vector_last_sync DATETIME NULL COMMENT '最后同步时间';
ALTER TABLE characters ADD COLUMN vector_error_message TEXT NULL COMMENT '向量化错误信息';

-- 为plots表添加向量化状态字段
ALTER TABLE plots ADD COLUMN vector_status VARCHAR(20) DEFAULT 'NOT_INDEXED' COMMENT '向量化状态：NOT_INDEXED, PENDING, PROCESSING, INDEXED, FAILED';
ALTER TABLE plots ADD COLUMN vector_version BIGINT DEFAULT 1 COMMENT '向量化版本号';
ALTER TABLE plots ADD COLUMN vector_last_sync DATETIME NULL COMMENT '最后同步时间';
ALTER TABLE plots ADD COLUMN vector_error_message TEXT NULL COMMENT '向量化错误信息';

-- 为worlds表添加向量化状态字段
ALTER TABLE worlds ADD COLUMN vector_status VARCHAR(20) DEFAULT 'NOT_INDEXED' COMMENT '向量化状态：NOT_INDEXED, PENDING, PROCESSING, INDEXED, FAILED';
ALTER TABLE worlds ADD COLUMN vector_version BIGINT DEFAULT 1 COMMENT '向量化版本号';
ALTER TABLE worlds ADD COLUMN vector_last_sync DATETIME NULL COMMENT '最后同步时间';
ALTER TABLE worlds ADD COLUMN vector_error_message TEXT NULL COMMENT '向量化错误信息';

-- 创建向量同步任务表
CREATE TABLE vector_sync_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    entity_type VARCHAR(50) NOT NULL COMMENT '实体类型：project, chapter, character, plot, world',
    entity_id BIGINT NOT NULL COMMENT '实体ID',
    operation VARCHAR(20) NOT NULL COMMENT '操作类型：INDEX, UPDATE, DELETE',
    version BIGINT NOT NULL COMMENT '数据版本号',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '任务状态：PENDING, PROCESSING, COMPLETED, FAILED',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_message TEXT NULL COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    processed_at TIMESTAMP NULL COMMENT '处理完成时间',
    UNIQUE KEY uk_entity_version (entity_type, entity_id, version)
) COMMENT='向量同步任务表';

-- 创建数据变更事件表
CREATE TABLE data_change_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '事件ID',
    entity_type VARCHAR(50) NOT NULL COMMENT '实体类型',
    entity_id BIGINT NOT NULL COMMENT '实体ID',
    change_type VARCHAR(20) NOT NULL COMMENT '变更类型：CREATE, UPDATE, DELETE',
    version BIGINT NOT NULL COMMENT '数据版本号',
    event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    processed BOOLEAN DEFAULT FALSE COMMENT '是否已处理',
    processed_at TIMESTAMP NULL COMMENT '处理时间',
    INDEX idx_entity_type_id (entity_type, entity_id),
    INDEX idx_processed (processed),
    INDEX idx_event_time (event_time)
) COMMENT='数据变更事件表';

-- 添加索引提高查询性能
CREATE INDEX idx_projects_vector_status ON projects(vector_status);
CREATE INDEX idx_chapters_vector_status ON chapters(vector_status);
CREATE INDEX idx_characters_vector_status ON characters(vector_status);
CREATE INDEX idx_plots_vector_status ON plots(vector_status);
CREATE INDEX idx_worlds_vector_status ON worlds(vector_status);

CREATE INDEX idx_vector_sync_tasks_status ON vector_sync_tasks(status);
CREATE INDEX idx_vector_sync_tasks_entity ON vector_sync_tasks(entity_type, entity_id);
CREATE INDEX idx_vector_sync_tasks_created_at ON vector_sync_tasks(created_at); 