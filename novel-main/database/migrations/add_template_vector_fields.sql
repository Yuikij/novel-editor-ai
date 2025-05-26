-- 为templates表添加向量化相关字段
-- 执行时间：2024-01-01

-- 添加向量化状态字段
ALTER TABLE templates ADD COLUMN vector_status VARCHAR(20) DEFAULT 'NOT_INDEXED' COMMENT '向量化状态：NOT_INDEXED(未索引), INDEXING(索引中), INDEXED(已索引), FAILED(索引失败)';

-- 添加向量化进度字段
ALTER TABLE templates ADD COLUMN vector_progress INT DEFAULT 0 COMMENT '向量化进度百分比 (0-100)';

-- 添加向量化开始时间字段
ALTER TABLE templates ADD COLUMN vector_start_time DATETIME NULL COMMENT '向量化开始时间';

-- 添加向量化完成时间字段
ALTER TABLE templates ADD COLUMN vector_end_time DATETIME NULL COMMENT '向量化完成时间';

-- 添加向量化错误信息字段
ALTER TABLE templates ADD COLUMN vector_error_message TEXT NULL COMMENT '向量化错误信息';

-- 添加索引以提高查询性能
CREATE INDEX idx_templates_vector_status ON templates(vector_status);
CREATE INDEX idx_templates_vector_progress ON templates(vector_progress);

-- 插入示例数据（可选）
-- INSERT INTO templates (name, tags, content, vector_status, vector_progress) 
-- VALUES ('示例模板', '小说,写作', '这是一个示例模板内容...', 'NOT_INDEXED', 0); 