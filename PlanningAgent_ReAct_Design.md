# PlanningAgent ReAct模式设计文档

## 概述

本次重构将原来的直接LLM调用改为使用ReAct模式的PlanningAgent，使计划制定阶段能够主动调用工具获取信息，然后基于获取的信息制定更准确的写作计划。

## 架构变更

### 原有架构
```
ChapterContentServiceImpl.generateChapterContentStreamFlux()
├── 直接调用 LLM (通过 PromptService.buildReasoningPrompt())
├── 解析返回的JSON为PlanRes
└── 传递给WritingAgent执行
```

### 新架构
```
ChapterContentServiceImpl.generateChapterContentStreamFlux()
├── 创建 PlanningAgent
├── PlanningAgent.run() - ReAct模式执行
│   ├── Think: 分析需要哪些信息
│   ├── Act: 调用工具获取信息 (latest_content_get, get_character_info等)
│   ├── Think: 判断信息是否充足
│   └── Act: 制定最终写作计划
├── 获取 PlanningAgent.getFinalPlan()
└── 传递给WritingAgent执行
```

## PlanningAgent设计

### 核心特性

1. **ReAct模式**: 继承自ReActAgent，实现think()和act()方法
2. **工具调用**: 能够主动调用latest_content_get、get_character_info等工具
3. **信息收集**: 逐步收集制定计划所需的信息
4. **计划生成**: 基于收集的信息生成结构化的写作计划

### 主要方法

#### think() 方法
- 分析当前已有信息
- 判断是否需要调用工具获取更多信息
- 返回是否需要执行行动

#### act() 方法
- 如果需要信息：调用工具获取信息
- 如果信息充足：制定最终写作计划

### 数据结构

#### PlanningThinkRes
```java
public static class PlanningThinkRes {
    private Boolean hasEnoughInfo;           // 是否有足够信息
    private List<ToolCallPlan> toolsToCall;  // 需要调用的工具列表
    private String reasoning;                // 推理过程
}
```

#### ToolCallPlan
```java
public static class ToolCallPlan {
    private String toolName;                 // 工具名称
    private String purpose;                  // 调用目的
    private Map<String, Object> parameters;  // 工具参数
}
```

## 工具调用流程

### 第一轮：Think -> Act (工具调用)
1. **Think阶段**: 分析当前上下文，发现需要获取最新章节内容
2. **Act阶段**: 调用`latest_content_get`工具获取章节内容

### 第二轮：Think -> Act (计划制定)
1. **Think阶段**: 分析已获取的信息，判断足够制定计划
2. **Act阶段**: 基于所有信息生成最终的PlanRes

## 提示词设计

### 思考阶段提示词
- 强调信息需求分析
- 明确可用工具及其用途
- 要求输出结构化的思考结果

### 行动阶段提示词
- 工具调用：明确指定工具参数
- 计划制定：要求输出JSON格式的计划

## 优势

1. **信息驱动**: 基于真实获取的信息制定计划，而非假设
2. **灵活性**: 可以根据需要调用不同的工具
3. **可扩展**: 容易添加新的工具和信息源
4. **透明性**: 整个思考和行动过程都有日志记录

## 测试

创建了`PlanningAgentTest`类来验证：
1. ReAct模式的基本功能
2. 工具调用流程
3. 计划制定结果

## 使用示例

```java
// 创建PlanningAgent
PlanningAgent planningAgent = new PlanningAgent(llmService, request);
planningAgent.setPlanId(planContext.getPlanId());

// 准备参数
Map<String, Object> params = new HashMap<>();
params.put("chapterId", request.getChapterId());
params.put("projectId", context.getProjectId());

// 执行计划制定
planningAgent.run(params);

// 获取结果
PlanRes finalPlan = planningAgent.getFinalPlan();
```

## 后续扩展

1. **更多工具**: 可以添加RAG查询、角色关系分析等工具
2. **智能缓存**: 避免重复调用相同的工具
3. **并行调用**: 支持同时调用多个工具
4. **计划优化**: 基于历史数据优化计划制定策略 