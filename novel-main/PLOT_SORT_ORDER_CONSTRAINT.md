# 情节排序唯一性约束功能

## 概述

本功能确保在同一章节内，情节的`sortOrder`字段不会重复，维护数据的一致性和完整性。

## 功能特性

### 1. 数据库约束
- 在`plots`表上添加了`uk_plots_chapter_sort_order`唯一约束
- 约束规则：`(chapter_id, sort_order)`必须唯一
- 添加了索引`idx_plots_chapter_sort_order`以提高查询性能

### 2. 业务逻辑验证
- 在保存或更新情节时自动验证`sortOrder`的唯一性
- 如果发生重复，自动调整后续情节的排序以避免冲突
- 支持自动分配`sortOrder`值（如果未设置）

### 3. API接口增强
- 新增验证逻辑到创建和更新情节的API
- 添加错误处理和友好的错误信息
- 提供数据修复接口用于重新整理排序

## 数据库迁移

执行以下SQL脚本来应用数据库约束：

```sql
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
```

## 核心方法

### PlotService.validateAndHandleSortOrder()

```java
/**
 * 验证并处理情节的sortOrder，确保在同一章节中不重复
 * 如果发生重复，会自动调整后续情节的sortOrder
 *
 * @param plot 待验证的情节
 * @param isUpdate 是否为更新操作（true）还是新增操作（false）
 * @throws IllegalArgumentException 如果参数无效
 */
void validateAndHandleSortOrder(Plot plot, boolean isUpdate);
```

#### 处理逻辑：
1. 参数验证：检查情节对象和章节ID是否为空
2. 自动分配：如果`sortOrder`为空，自动设置为下一个可用值
3. 合法性检查：确保`sortOrder`大于0
4. 重复检测：查找是否存在相同的`sortOrder`
5. 冲突解决：如果发现重复，自动调整后续情节的排序

### 自动调整机制

当检测到`sortOrder`冲突时，系统会：
1. 查找所有受影响的情节（同章节内排序大于等于冲突值的情节）
2. 将这些情节的`sortOrder`依次加1
3. 为新情节腾出位置

## API接口

### 创建情节
```http
POST /plots
Content-Type: application/json

{
  "projectId": 1,
  "chapterId": 1,
  "title": "情节标题",
  "description": "情节描述",
  "sortOrder": 1  // 可选，如果不提供会自动分配
}
```

### 更新情节
```http
PUT /plots/{id}
Content-Type: application/json

{
  "projectId": 1,
  "chapterId": 1,
  "title": "更新的情节标题",
  "sortOrder": 2
}
```

### 重新整理章节排序（数据修复）
```http
POST /plots/reorder-chapter/{chapterId}
```

## 错误处理

### 常见错误及处理

1. **参数错误**
   - 错误：`情节对象不能为空`
   - 处理：返回HTTP 400，提示参数错误

2. **章节ID为空**
   - 错误：`章节ID不能为空`
   - 处理：返回HTTP 400，提示参数错误

3. **非法排序值**
   - 错误：`情节排序必须大于0，当前值: 0`
   - 处理：返回HTTP 400，提示参数错误

4. **数据库约束冲突**
   - 错误：数据库级别的唯一约束违反
   - 处理：业务层会自动调整，避免约束冲突

## 测试

运行测试类`PlotSortOrderTest`来验证功能：

```bash
mvn test -Dtest=PlotSortOrderTest
```

### 测试用例包括：
- 自动设置`sortOrder`
- `sortOrder`唯一性验证
- 更新时的验证
- 重新整理排序
- 非法值处理
- 参数验证

## 日志记录

系统会记录以下关键操作：
- 自动设置`sortOrder`值
- 检测到排序重复的警告
- 排序调整的详细过程
- 错误信息和异常

## 兼容性

- 兼容现有的情节数据
- 自动处理数据迁移（从`plot_order`到`sort_order`）
- 向后兼容现有API接口

## 性能考虑

- 添加了数据库索引以优化查询性能
- 批量操作时避免频繁的单个更新
- 使用MyBatis-Plus的Lambda查询优化SQL生成

## 未来扩展

可以考虑以下增强功能：
1. 批量重排序API
2. 拖拽排序支持
3. 排序范围验证（如1-100）
4. 排序历史记录 