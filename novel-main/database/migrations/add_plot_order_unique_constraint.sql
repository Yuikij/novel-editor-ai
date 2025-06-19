-- 为plots表添加sort_order字段的唯一约束
-- 确保同一章节内的情节顺序不重复

-- 首先检查是否存在sort_order字段，如果不存在则添加
ALTER TABLE plots 
ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0 
COMMENT '情节排序，在同一章节内不能重复';

-- 如果存在plot_order字段但没有sort_order字段，则迁移数据
UPDATE plots SET sort_order = plot_order WHERE sort_order = 0 AND plot_order IS NOT NULL;

-- 删除旧的plot_order字段（如果存在）
ALTER TABLE plots DROP COLUMN IF EXISTS plot_order;

-- 添加联合唯一约束，确保同一章节内的sort_order不重复
ALTER TABLE plots 
ADD CONSTRAINT uk_plots_chapter_sort_order 
UNIQUE (chapter_id, sort_order);

-- 添加索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_plots_chapter_sort_order 
ON plots (chapter_id, sort_order); 