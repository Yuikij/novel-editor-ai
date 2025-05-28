# RAG增强写作系统使用指南

## 快速开始

### 1. 系统配置

在 `application.yml` 中配置相关参数：

```yaml
novel:
  chapter:
    default-max-tokens: 2000      # 默认最大token数
    default-temperature: 0.7      # 默认温度参数
  rag:
    max-results: 5               # RAG搜索最大结果数
    enabled: true                # 是否启用RAG功能
  template:
    chat:
      max-results: 5
      similarity-threshold: 0.0
      max-tokens: 2000
      temperature: 0.7
```

### 2. 基本使用流程

#### 步骤1：准备项目数据
确保你的项目包含以下基础数据：
- 项目基本信息（标题、简介、风格等）
- 角色信息（姓名、性格、背景等）
- 情节设定（章节情节、发展脉络等）
- 世界观设定（如果有的话）

#### 步骤2：发起章节内容生成请求
```java
ChapterContentRequest request = new ChapterContentRequest();
request.setChapterId(123L);
request.setMaxTokens(2000);
request.setTemperature(0.7f);

// 调用服务
Result<String> result = chapterContentService.generateChapterContentExecute(request);
```

#### 步骤3：监控生成进度
```java
String planId = result.getData();
PlanContext context = chapterContentService.getPlanContextMap().get(planId);

// 检查状态
PlanState state = context.getPlanState();
String message = context.getMessage();
int progress = context.getProgress();
```

## 核心功能详解

### 🔧 工具调用系统

#### 角色查询工具 (character_query)
```json
{
  "tool": "character_query",
  "parameters": {
    "queryType": "by_project",
    "projectId": 123,
    "characterName": "主角名称"  // 可选
  }
}
```

**支持的查询类型：**
- `by_name`: 按角色名称查询
- `by_id`: 按角色ID查询  
- `by_project`: 查询项目下所有角色

#### 情节查询工具 (plot_query)
```json
{
  "tool": "plot_query",
  "parameters": {
    "queryType": "by_chapter",
    "chapterId": 456,
    "projectId": 123  // 可选
  }
}
```

**支持的查询类型：**
- `by_title`: 按情节标题查询
- `by_id`: 按情节ID查询
- `by_chapter`: 查询章节相关情节
- `by_project`: 查询项目下所有情节

#### RAG搜索工具 (rag_search)
```json
{
  "tool": "rag_search",
  "parameters": {
    "projectId": 123,
    "query": "主角的性格特征",
    "maxResults": 5,
    "searchType": "character"
  }
}
```

**支持的搜索类型：**
- `general`: 通用搜索
- `character`: 角色相关搜索
- `world`: 世界观相关搜索
- `chapter`: 章节相关搜索

### 🤖 智能写作流程

#### 1. 分析阶段 (Analysis)
系统会分析当前的写作任务：
- 理解章节上下文
- 识别需要的信息类型
- 制定信息获取策略

#### 2. 查询阶段 (Query)
根据分析结果，自动调用相关工具：
- 查询涉及的角色信息
- 获取相关情节设定
- 搜索背景资料

#### 3. 规划阶段 (Planning)
基于获取的信息制定写作计划：
- 确定写作重点
- 安排叙事结构
- 设定字数目标

#### 4. 创作阶段 (Creation)
执行具体的内容创作：
- 流式生成内容
- 实时质量监控
- 保持一致性

## 高级功能

### 📊 监控和调试

#### 查看工具状态
```java
Map<String, String> toolStates = writingToolManager.getToolStates();
for (Map.Entry<String, String> entry : toolStates.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

#### 获取工具描述
```java
Map<String, String> descriptions = writingToolManager.getToolDescriptions();
```

#### 检查工具可用性
```java
boolean available = writingToolManager.isToolAvailable("character_query");
```

### 🔄 流式处理

#### 监听生成过程
```java
PlanContext context = chapterContentService.getPlanContextMap().get(planId);
Flux<String> contentStream = context.getPlanStream();

if (contentStream != null) {
    contentStream.subscribe(
        content -> {
            // 处理生成的内容片段
            System.out.print(content);
        },
        error -> {
            // 处理错误
            System.err.println("生成失败: " + error.getMessage());
        },
        () -> {
            // 生成完成
            System.out.println("\n生成完成！");
        }
    );
}
```

### 🎯 自定义配置

#### 调整生成参数
```java
ChapterContentRequest request = new ChapterContentRequest();
request.setChapterId(123L);
request.setMaxTokens(3000);        // 增加token数量
request.setTemperature(0.8f);      // 提高创造性
```

#### 启用/禁用RAG功能
```yaml
novel:
  rag:
    enabled: false  # 禁用RAG功能
```

## 最佳实践

### ✅ 推荐做法

#### 1. 数据准备
- **完善角色信息**：确保角色的性格、背景、目标等信息完整
- **详细情节设定**：为每个章节设置清晰的情节目标
- **一致的世界观**：保持世界观设定的内在逻辑

#### 2. 参数调优
- **合理设置token数**：根据章节长度需求调整maxTokens
- **适当的温度值**：0.7-0.8适合创意写作，0.3-0.5适合事实性内容
- **RAG结果数量**：通常5-10个结果足够，过多会影响性能

#### 3. 监控和优化
- **实时监控状态**：关注生成进度和工具调用情况
- **分析工具使用**：查看哪些工具被频繁使用
- **内容质量评估**：定期检查生成内容的质量

### ❌ 避免事项

#### 1. 数据问题
- **信息不完整**：角色或情节信息缺失会影响生成质量
- **数据矛盾**：确保角色设定和情节发展的一致性
- **过时信息**：及时更新项目相关信息

#### 2. 参数设置
- **token数过大**：可能导致内存问题或响应缓慢
- **温度值极端**：过高(>0.9)可能产生不连贯内容，过低(<0.3)可能过于机械
- **频繁调用**：避免短时间内重复生成相同内容

#### 3. 系统使用
- **忽略错误**：及时处理工具调用失败或生成错误
- **资源泄漏**：确保计划完成后清理相关资源
- **并发冲突**：避免同时为同一章节生成多个版本

## 故障排除

### 常见问题

#### 1. 工具调用失败
**症状**：工具返回错误或空结果
**解决方案**：
- 检查数据库连接
- 验证查询参数
- 查看工具状态日志

#### 2. RAG搜索无结果
**症状**：搜索返回空结果
**解决方案**：
- 检查向量化状态
- 调整搜索阈值
- 验证过滤条件

#### 3. 生成内容质量差
**症状**：内容不连贯或偏离主题
**解决方案**：
- 完善基础数据
- 调整温度参数
- 检查提示词设置

#### 4. 性能问题
**症状**：生成速度慢或内存占用高
**解决方案**：
- 减少token数量
- 限制RAG结果数
- 优化数据库查询

### 日志分析

#### 关键日志标识
- `[RAG增强写作]`: 主要流程日志
- `角色查询工具`: 角色查询相关日志
- `情节查询工具`: 情节查询相关日志
- `RAG搜索工具`: RAG搜索相关日志

#### 日志级别设置
```yaml
logging:
  level:
    com.soukon.novelEditorAi.agent: DEBUG
    com.soukon.novelEditorAi.service.impl.ChapterContentServiceImpl: INFO
```

## 扩展开发

### 添加新工具

#### 1. 实现工具接口
```java
public class CustomTool implements WritingToolCallback {
    @Override
    public WritingToolResult apply(String input, ToolContext toolContext) {
        // 实现工具逻辑
        return WritingToolResult.success("结果");
    }
    
    // 实现其他必要方法...
}
```

#### 2. 注册到工具管理器
```java
@Component
public class WritingToolManager {
    private void initializeTools() {
        // 添加自定义工具
        CustomTool customTool = new CustomTool();
        toolInstances.put(customTool.getName(), customTool);
    }
}
```

### 自定义提示词

#### 修改系统提示词
```java
private String buildEnhancedSystemPrompt() {
    return """
            你是一位专业的小说创作AI助手...
            
            ## 自定义规则
            1. 特殊要求1
            2. 特殊要求2
            """;
}
```

## 总结

RAG增强写作系统通过智能工具调用和信息检索，显著提升了AI写作的质量和智能化程度。正确使用本系统可以帮助你：

- 🎯 **提高写作质量**：基于准确信息进行创作
- 🔄 **保持内容一致性**：避免角色和情节矛盾
- ⚡ **提升创作效率**：自动化信息获取和分析
- 🎨 **增强文学性**：专业的文学创作指导

遵循本指南的最佳实践，你将能够充分发挥系统的潜力，创作出高质量的小说内容。 