# 世界观项目隔离解决方案

## 🎯 问题描述

**挑战**：
- 向量查询时指定了 `projectId` 进行项目隔离
- 世界观（World）本身没有 `projectId`，是全局资源
- 项目表中有 `worldId` 字段关联到世界观
- **需求**：在项目范围内的向量查询中，能够查到关联的世界观信息

## 💡 解决方案：世界观文档复制机制

我们采用**世界观文档复制**的方案，为每个使用该世界观的项目创建一份世界观文档副本，副本中包含 `projectId`。

### 🏗️ 核心机制

#### 1. 自动触发机制
```java
// 在项目向量化时，如果项目关联了世界观，自动触发世界观项目关联同步
if (project.getWorldId() != null) {
    metadata.put("worldId", project.getWorldId());
    triggerWorldProjectSync(project.getWorldId(), project.getId());
}
```

#### 2. 世界观项目关联文档创建
```java
private void createWorldProjectDocument(World world, Long projectId) {
    // 创建项目关联的元数据
    Map<String, Object> metadata = createBaseMetadata(world, "world-project", world.getVectorVersion());
    metadata.put("projectId", projectId); // 关键：设置项目ID
    metadata.put("worldId", world.getId());
    metadata.put("isWorldProjectCopy", true);
    
    // 创建文档ID，包含项目ID以区分不同项目的副本
    String documentId = "world-" + world.getId() + "-project-" + projectId + "-v" + world.getVectorVersion();
}
```

#### 3. 文档ID规则
- **原始世界观文档**：`world-{worldId}-v{version}`
- **项目关联副本**：`world-{worldId}-project-{projectId}-v{version}`
- **分片文档**：`world-{worldId}-project-{projectId}-v{version}-chunk-{index}`

## 🔄 完整生命周期管理

### 📝 创建阶段
1. **项目创建**：如果项目关联了世界观，自动创建世界观项目关联文档
2. **项目更新**：如果世界观关联发生变化，删除旧关联，创建新关联
3. **项目删除**：删除所有相关的世界观项目关联文档

### 🔄 更新阶段
```java
// 检查世界观关联是否发生变化
boolean worldChanged = !Objects.equals(oldWorldId, newWorldId);

if (worldChanged) {
    // 删除旧的世界观关联文档
    if (oldWorldId != null) {
        unifiedVectorSyncService.deleteWorldProjectDocument(oldWorldId, projectId);
    }
    // 新的世界观关联会在项目向量同步时自动创建
}
```

### 🗑️ 删除阶段
```java
// 删除世界观关联文档（如果存在）
if (project.getWorldId() != null) {
    unifiedVectorSyncService.deleteWorldProjectDocument(project.getWorldId(), projectId);
}
```

## 📊 元数据结构

### 原始世界观文档
```json
{
  "entityType": "world",
  "entityId": 123,
  "name": "幻想大陆",
  "elementTypes": ["地理", "种族", "魔法体系"],
  "elementNames": ["龙脊山脉", "精灵族", "元素魔法"],
  "elementCount": 3,
  "version": 1
}
```

### 项目关联副本文档
```json
{
  "entityType": "world-project",
  "entityId": 123,
  "projectId": 456,
  "worldId": 123,
  "name": "幻想大陆",
  "elementTypes": ["地理", "种族", "魔法体系"],
  "elementNames": ["龙脊山脉", "精灵族", "元素魔法"],
  "elementCount": 3,
  "version": 1,
  "isWorldProjectCopy": true
}
```

## 🔍 查询机制

### 向量查询
```java
// 使用项目ID过滤，可以查到：
// 1. 项目本身的文档 (projectId == 456)
// 2. 项目的章节、角色、情节文档 (projectId == 456)  
// 3. 项目关联的世界观文档 (projectId == 456 && isWorldProjectCopy == true)

SearchRequest request = SearchRequest.builder()
    .query(query)
    .topK(maxResults)
    .filterExpression("projectId == " + projectId)
    .build();
```

### 备用查询
```java
private List<Document> searchWorlds(String query, Long projectId) {
    if (projectId != null) {
        // 获取项目关联的世界观
        Project project = projectMapper.selectById(projectId);
        if (project != null && project.getWorldId() != null) {
            World world = worldMapper.selectById(project.getWorldId());
            if (world != null && contentMatches(world, query)) {
                return List.of(createDocumentFromWorld(world, projectId));
            }
        }
    }
    return Collections.emptyList();
}
```

## ⚡ 性能优化

### 1. 智能触发
- 只在项目世界观关联发生变化时才处理
- 异步处理，不阻塞主业务流程

### 2. 版本管理
- 使用版本号避免重复处理
- 支持增量更新

### 3. 批量操作
- 支持分片文档的批量创建和删除
- 减少向量数据库的操作次数

## 🛡️ 数据一致性

### 1. 事务保护
- 项目更新和世界观关联文档更新在同一个事务中
- 确保数据一致性

### 2. 异常处理
```java
try {
    unifiedVectorSyncService.deleteWorldProjectDocument(oldWorldId, projectId);
} catch (Exception e) {
    log.warn("删除旧世界观关联文档失败: worldId={}, projectId={}", oldWorldId, projectId, e);
    // 不影响主流程，记录日志即可
}
```

### 3. 幂等性
- 重复调用不会产生副作用
- 支持失败重试

## 📈 实际效果

### ✅ 解决的问题
1. **项目隔离**：每个项目只能查到自己关联的世界观
2. **数据完整性**：世界观信息完整包含在项目查询结果中
3. **性能优化**：避免了复杂的多表关联查询
4. **扩展性**：支持一个世界观被多个项目使用

### 🎯 查询示例
```
用户查询："这个世界有什么种族？"
项目ID：456

查询结果：
1. 项目456关联的世界观文档（包含精灵族、人类等种族信息）
2. 项目456中提到种族的章节内容
3. 项目456中相关的角色信息

✅ 成功实现项目隔离的世界观查询
```

## 🔧 技术要点

### 1. 文档类型区分
- `entityType: "world"` - 原始世界观文档
- `entityType: "world-project"` - 项目关联副本

### 2. 元数据标识
- `isWorldProjectCopy: true` - 标识为项目关联副本
- `worldId` - 原始世界观ID
- `projectId` - 关联项目ID

### 3. 生命周期管理
- 自动创建、更新、删除
- 与项目生命周期同步

这个解决方案完美解决了世界观在项目隔离环境中的查询问题，既保持了世界观的全局性，又实现了项目级别的数据隔离。 