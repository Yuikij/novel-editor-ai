# 模板接口优化说明

## 优化背景

由于模板的 `content` 字段可能非常大（兆级别），在列表查询时返回该字段会导致：
1. 网络传输效率低下
2. 前端渲染性能问题
3. 内存占用过高

## 优化方案

### 1. 新增 TemplateListDTO
创建了不包含 `content` 字段的 DTO 类，用于列表查询：
```java
public class TemplateListDTO {
    private Long id;
    private String name;
    private String tags;
}
```

### 2. 新增优化后的接口

#### 2.1 分页查询模板列表（推荐）
```
GET /templates/list?page=1&size=10&name=xxx&tag=xxx
```
- 返回：`Page<TemplateListDTO>`
- 不包含 `content` 字段
- 适用于列表展示场景

#### 2.2 高级搜索模板列表（推荐）
```
POST /templates/search
{
    "page": 1,
    "size": 10,
    "name": "模板名称",
    "tags": "标签"
}
```
- 返回：`Page<TemplateListDTO>`
- 不包含 `content` 字段

#### 2.3 根据标签查询模板列表（推荐）
```
GET /templates/tag/{tag}/list
```
- 返回：`List<TemplateListDTO>`
- 不包含 `content` 字段

#### 2.4 获取模板详情（新增）
```
GET /templates/{id}/detail
```
- 返回：`Template`（完整信息，包含 `content` 字段）
- 适用于查看/编辑模板详情场景

### 3. 废弃的接口（保留兼容性）

以下接口已标记为 `@Deprecated`，建议迁移到新接口：

#### 3.1 原分页查询接口
```
GET /templates/page?page=1&size=10&name=xxx&tag=xxx
```
- 返回：`Page<Template>`（包含 `content` 字段）
- 建议迁移到：`GET /templates/list`

#### 3.2 原高级搜索接口
```
POST /templates/search-full
```
- 返回：`Page<Template>`（包含 `content` 字段）
- 建议迁移到：`POST /templates/search`

#### 3.3 原标签查询接口
```
GET /templates/tag/{tag}
```
- 返回：`List<Template>`（包含 `content` 字段）
- 建议迁移到：`GET /templates/tag/{tag}/list`

## 使用建议

### 前端开发建议
1. **列表页面**：使用 `/templates/list` 或 `/templates/search` 接口
2. **详情页面**：使用 `/templates/{id}/detail` 接口获取完整信息
3. **标签筛选**：使用 `/templates/tag/{tag}/list` 接口

### 接口迁移计划
1. **第一阶段**：新接口与旧接口并存，前端逐步迁移
2. **第二阶段**：旧接口标记为废弃，但保持可用
3. **第三阶段**：在后续版本中移除废弃接口

## 性能提升预期

1. **网络传输**：列表查询数据量减少 80-90%
2. **内存占用**：服务端内存占用减少 70-80%
3. **响应速度**：列表查询响应时间提升 50-70%
4. **前端渲染**：列表页面渲染性能提升 60-80%

## 数据库优化

在 Mapper 层新增了专门的查询方法：
- `selectPageWithoutContent()`: 分页查询不包含 content 字段
- `selectByTagWithoutContent()`: 标签查询不包含 content 字段

这些方法在 SQL 层面就排除了 `content` 字段，避免了不必要的数据传输。 