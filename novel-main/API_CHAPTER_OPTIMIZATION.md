# 章节接口优化说明

## 优化背景

由于章节的 `content` 和 `historyContent` 字段可能非常大（兆级别），在列表查询时返回这些字段会导致：
1. 网络传输效率低下
2. 前端渲染性能问题
3. 内存占用过高
4. 响应时间过长

## 优化方案

### 1. 新增 ChapterListDTO
创建了不包含 `content` 和 `historyContent` 字段的 DTO 类，用于列表查询：
```java
public class ChapterListDTO {
    private Long id;
    private Long projectId;
    private Long templateId;
    private String title;
    private Integer sortOrder;
    private String status;
    private String summary;
    private String notes;
    private Long wordCountGoal;
    private Long wordCount;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 不包含 content 和 historyContent 字段
}
```

### 2. 新增优化后的接口

#### 2.1 查询所有章节列表（推荐）
```
GET /chapters/list
```
- 返回：`List<ChapterListDTO>`
- 不包含 `content` 和 `historyContent` 字段
- 适用于章节列表展示场景

#### 2.2 根据项目ID查询章节列表（推荐）
```
GET /chapters/project/{projectId}/list
```
- 返回：`List<ChapterListDTO>`
- 不包含 `content` 和 `historyContent` 字段
- 适用于项目章节列表展示

#### 2.3 分页查询章节列表（推荐）
```
GET /chapters/list/page?page=1&pageSize=10&projectId=xxx&title=xxx&status=xxx
```
- 返回：`Page<ChapterListDTO>`
- 不包含 `content` 和 `historyContent` 字段
- 支持按项目ID、标题、状态筛选

#### 2.4 获取章节详情（新增）
```
GET /chapters/{id}/detail
```
- 返回：`Chapter`（完整信息，包含 `content` 和 `historyContent` 字段）
- 适用于查看/编辑章节详情场景

### 3. 废弃的接口（保留兼容性）

以下接口已标记为 `@Deprecated`，建议迁移到新接口：

#### 3.1 原查询所有章节接口
```
GET /chapters
```
- 返回：`List<Chapter>`（包含 `content` 和 `historyContent` 字段）
- 建议迁移到：`GET /chapters/list`

#### 3.2 原根据项目ID查询接口
```
GET /chapters/project/{projectId}
```
- 返回：`List<Chapter>`（包含 `content` 和 `historyContent` 字段）
- 建议迁移到：`GET /chapters/project/{projectId}/list`

#### 3.3 原分页查询接口
```
GET /chapters/page?page=1&pageSize=10&projectId=xxx&title=xxx&status=xxx
```
- 返回：`Page<Chapter>`（包含 `content` 和 `historyContent` 字段）
- 建议迁移到：`GET /chapters/list/page`

## 使用建议

### 前端开发建议
1. **章节列表页面**：使用 `/chapters/list` 或 `/chapters/list/page` 接口
2. **项目章节列表**：使用 `/chapters/project/{projectId}/list` 接口
3. **章节详情页面**：使用 `/chapters/{id}/detail` 接口获取完整信息
4. **章节编辑页面**：使用 `/chapters/{id}/detail` 接口获取完整内容

### 接口迁移计划
1. **第一阶段**：新接口与旧接口并存，前端逐步迁移
2. **第二阶段**：旧接口标记为废弃，但保持可用
3. **第三阶段**：在后续版本中移除废弃接口

## 性能提升预期

1. **网络传输**：列表查询数据量减少 85-95%
2. **内存占用**：服务端内存占用减少 80-90%
3. **响应速度**：列表查询响应时间提升 60-80%
4. **前端渲染**：列表页面渲染性能提升 70-90%

## 数据库优化

在 Mapper 层新增了专门的查询方法：
- `selectPageWithoutContent()`: 分页查询不包含 content 和 historyContent 字段
- `selectListByProjectIdWithoutContent()`: 根据项目ID查询不包含大字段
- `selectAllWithoutContent()`: 查询所有章节不包含大字段

这些方法在 SQL 层面就排除了 `content` 和 `historyContent` 字段，避免了不必要的数据传输。

## 特殊字段说明

### content 字段
- 包含章节的完整文本内容
- 可能达到数万字符
- 仅在详情和编辑场景需要

### historyContent 字段
- 包含章节的历史版本内容（JSONObject）
- 存储最近10个版本的完整内容
- 数据量可能非常大
- 仅在版本管理和恢复场景需要

## 相关接口保持不变

以下接口因为业务需要，仍然返回完整的章节信息：
- `POST /chapters` - 创建章节
- `PUT /chapters/{id}` - 更新章节
- `GET /chapters/{id}` - 获取章节（兼容性保留）
- 章节历史相关接口（需要完整的历史数据） 