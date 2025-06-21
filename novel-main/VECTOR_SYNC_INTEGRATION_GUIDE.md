# 向量同步集成指南

## 概述

本指南说明如何在现有业务代码中集成 `EntitySyncHelper` 来实现MySQL与向量数据库的自动同步。

## 快速开始

### 1. 注入EntitySyncHelper

在需要触发向量同步的Service中注入 `EntitySyncHelper`：

```java
@Service
public class ChapterServiceImpl {
    
    @Autowired
    private EntitySyncHelper entitySyncHelper;
    
    // 其他依赖...
}
```

### 2. 在CRUD操作中调用同步方法

#### 创建实体时
```java
@Transactional
public Chapter createChapter(Chapter chapter) {
    // 1. 保存到MySQL
    chapterMapper.insert(chapter);
    
    // 2. 触发向量同步（异步）
    entitySyncHelper.triggerCreate(
        "chapter",                // 实体类型
        chapter.getId(),          // 实体ID
        chapter.getProjectId(),   // 项目ID
        false                     // 是否紧急处理
    );
    
    return chapter;
}
```

#### 更新实体时
```java
@Transactional
public Chapter updateChapter(Chapter chapter) {
    // 1. 获取旧内容用于变更检测
    Chapter oldChapter = chapterMapper.selectById(chapter.getId());
    String oldContent = oldChapter != null ? oldChapter.getContent() : null;
    
    // 2. 递增版本号
    if (chapter.getVectorVersion() == null) {
        chapter.setVectorVersion(1L);
    } else {
        chapter.setVectorVersion(chapter.getVectorVersion() + 1);
    }
    
    // 3. 更新MySQL
    chapterMapper.updateById(chapter);
    
    // 4. 检查内容是否真的变更了
    if (entitySyncHelper.isContentChanged(oldContent, chapter.getContent())) {
        // 5. 触发向量同步
        entitySyncHelper.triggerUpdate(
            "chapter",
            chapter.getId(),
            chapter.getVectorVersion(),
            chapter.getProjectId(),
            false
        );
    }
    
    return chapter;
}
```

#### 删除实体时
```java
@Transactional
public void deleteChapter(Long chapterId) {
    // 1. 获取实体信息
    Chapter chapter = chapterMapper.selectById(chapterId);
    
    // 2. 删除MySQL数据
    chapterMapper.deleteById(chapterId);
    
    // 3. 触发向量同步（紧急删除）
    entitySyncHelper.triggerDelete(
        "chapter",
        chapterId,
        chapter.getProjectId(),
        true  // 删除操作通常设为紧急
    );
}
```

## 详细说明

### 实体类型映射

| 实体类型 | 说明 |
|---------|------|
| "project" | 项目 |
| "chapter" | 章节 |
| "character" | 角色 |
| "plot" | 情节 |
| "world" | 世界观 |

### 同步模式

#### 异步模式（推荐）
- `urgent = false`
- 不阻塞业务流程
- 由定时任务批量处理
- 适合大部分场景

```java
entitySyncHelper.triggerUpdate("chapter", chapterId, version, projectId, false);
```

#### 同步模式（紧急）
- `urgent = true`
- 立即处理，会阻塞当前线程
- 适合删除操作或重要更新

```java
entitySyncHelper.triggerDelete("chapter", chapterId, projectId, true);
```

### 版本号管理

每次更新实体时，需要递增版本号：

```java
// 创建时
entity.setVectorVersion(1L);

// 更新时
entity.setVectorVersion(entity.getVectorVersion() + 1);
```

### 内容变更检测

使用 `isContentChanged` 方法避免不必要的向量化：

```java
if (entitySyncHelper.isContentChanged(oldContent, newContent)) {
    // 只在内容真正变更时才触发同步
    entitySyncHelper.triggerUpdate(...);
}
```

## 集成检查清单

### 数据库准备
- [ ] 执行迁移脚本 `add_vector_sync_fields.sql`
- [ ] 确认所有实体表都添加了向量化状态字段
- [ ] 确认创建了 `vector_sync_tasks` 和 `data_change_events` 表

### 实体类更新
- [ ] 为所有实体类添加了向量化相关字段：
  - `vectorStatus`
  - `vectorVersion` 
  - `vectorLastSync`
  - `vectorErrorMessage`

### 业务代码集成
- [ ] 在相关Service中注入了 `EntitySyncHelper`
- [ ] 在创建操作中调用了 `triggerCreate`
- [ ] 在更新操作中调用了 `triggerUpdate`
- [ ] 在删除操作中调用了 `triggerDelete`
- [ ] 使用了版本号管理
- [ ] 使用了内容变更检测

### 配置检查
- [ ] 启用了定时任务 `@EnableScheduling`
- [ ] 启用了异步处理 `@EnableAsync`
- [ ] 配置了向量同步相关参数

## 监控和调试

### 查看同步状态
```sql
-- 查看实体的向量化状态
SELECT id, title, vector_status, vector_version, vector_last_sync, vector_error_message 
FROM chapters 
WHERE project_id = ?;

-- 查看待处理的同步任务
SELECT * FROM vector_sync_tasks WHERE status = 'PENDING';

-- 查看失败的同步任务
SELECT * FROM vector_sync_tasks WHERE status = 'FAILED';
```

### 日志配置
```yaml
logging:
  level:
    com.soukon.novelEditorAi.service.impl.UnifiedVectorSyncServiceImpl: INFO
    com.soukon.novelEditorAi.service.VectorSyncScheduleService: INFO
    com.soukon.novelEditorAi.service.EntitySyncHelper: DEBUG
```

## 最佳实践

1. **批量操作**: 对于批量创建/更新，使用异步模式避免阻塞
2. **删除操作**: 使用紧急模式确保向量文档及时删除
3. **内容检测**: 始终使用内容变更检测避免不必要的向量化
4. **版本管理**: 正确维护版本号确保数据一致性
5. **错误处理**: 向量同步失败不应影响主业务流程
6. **监控**: 定期检查失败的同步任务并处理

## 故障排除

### 常见问题

1. **同步任务一直处于PENDING状态**
   - 检查定时任务是否启用
   - 检查 `@EnableScheduling` 注解
   - 查看应用日志

2. **向量搜索返回过期数据**
   - 检查实体的 `vector_version` 是否正确更新
   - 检查一致性搜索服务是否正常工作

3. **同步任务频繁失败**
   - 检查向量数据库连接
   - 检查实体内容是否过长
   - 查看错误日志定位具体问题

### 手动触发同步
```java
// 立即同步单个实体
entitySyncHelper.syncImmediately("chapter", chapterId, "UPDATE", version);

// 恢复失败的任务
vectorSyncService.recoverFailedTasks();
``` 