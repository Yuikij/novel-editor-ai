# 项目流式对话API使用指南

## 概述

项目流式对话API为小说编辑器提供了基于项目的智能对话功能，支持上下文记忆和向量检索，返回流式响应。

## API接口

### 1. 流式对话接口

**接口地址：** `POST /projects/{projectId}/chat/stream`

**请求参数：**

```json
{
    "message": "帮我分析一下主角的性格特点",
    "sessionId": "optional-session-id",
    "enableVectorSearch": true,
    "maxVectorResults": 10,
    "similarityThreshold": 0.7,
    "mode": "CREATIVE",
    "temperature": 0.8,
    "maxTokens": 2000
}
```

**参数说明：**

- `message`: 用户消息内容（必填）
- `sessionId`: 会话ID，用于多会话管理（可选）
- `enableVectorSearch`: 是否启用向量检索（默认true）
- `maxVectorResults`: 向量检索最大结果数（默认10）
- `similarityThreshold`: 向量检索相似度阈值（默认0.7）
- `mode`: 对话模式（CREATIVE/ANALYSIS/PLANNING/REVIEW，默认CREATIVE）
- `temperature`: 温度参数，控制创造性（默认0.8）
- `maxTokens`: 最大输出长度（默认2000）

**响应格式：** `text/plain` 流式响应

**使用示例：**

```javascript
// 使用fetch API进行流式请求
async function streamChat(projectId, message) {
    const response = await fetch(`/projects/${projectId}/chat/stream`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            message: message,
            enableVectorSearch: true
        })
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        
        const chunk = decoder.decode(value);
        console.log(chunk); // 处理流式响应
    }
}
```

### 2. 获取对话上下文

**接口地址：** `GET /projects/{projectId}/chat/context`

**查询参数：**
- `sessionId`: 会话ID（可选）

**响应示例：**

```json
{
    "code": 200,
    "message": "获取对话上下文成功",
    "data": {
        "projectId": 1,
        "sessionId": "default",
        "messages": [
            {
                "role": "USER",
                "content": "帮我分析主角性格",
                "timestamp": "2024-01-01T10:00:00",
                "vectorResults": [...]
            },
            {
                "role": "ASSISTANT",
                "content": "根据您的项目信息...",
                "timestamp": "2024-01-01T10:00:30"
            }
        ],
        "turnCount": 1,
        "totalTokens": 500,
        "createdAt": "2024-01-01T10:00:00",
        "updatedAt": "2024-01-01T10:00:30"
    }
}
```

### 3. 清除对话上下文

**接口地址：** `DELETE /projects/{projectId}/chat/context`

**查询参数：**
- `sessionId`: 会话ID（可选）

### 4. 获取对话历史

**接口地址：** `GET /projects/{projectId}/chat/history`

**查询参数：**
- `sessionId`: 会话ID（可选）
- `limit`: 限制返回的消息数量（默认20）

## 功能特性

### 1. 上下文记忆
- 自动保存对话历史
- 支持多会话管理
- 智能上下文窗口管理

### 2. 向量检索
- 基于项目内容的智能检索
- 支持角色、情节、世界观等多种实体类型
- 可配置相似度阈值和结果数量

### 3. 流式响应
- 实时流式输出
- 降低首字延迟
- 提升用户体验

### 4. 智能提示词构建
- 自动整合项目信息
- 动态构建系统提示词
- 基于向量检索结果增强上下文

## 对话模式说明

### CREATIVE（创作模式）
- 适用于小说内容创作
- 较高的创造性和想象力
- 注重文学性和艺术性

### ANALYSIS（分析模式）
- 适用于角色分析、情节分析
- 逻辑性强，分析深入
- 提供客观的见解和建议

### PLANNING（规划模式）
- 适用于故事大纲规划
- 结构化思维
- 注重逻辑性和完整性

### REVIEW（审查模式）
- 适用于内容审查和修改建议
- 批判性思维
- 注重质量和一致性

## 最佳实践

### 1. 会话管理
```javascript
// 为不同的对话场景使用不同的sessionId
const characterAnalysisSession = "character-analysis";
const plotPlanningSession = "plot-planning";
const writingSession = "writing-" + chapterId;
```

### 2. 向量检索优化
```json
{
    "enableVectorSearch": true,
    "maxVectorResults": 5,  // 对于简单问题，减少检索结果
    "similarityThreshold": 0.8  // 提高阈值以获得更相关的结果
}
```

### 3. 温度参数调节
```json
{
    "temperature": 0.3,  // 分析类任务使用较低温度
    "temperature": 0.8,  // 创作类任务使用较高温度
    "temperature": 1.0   // 需要高度创造性时使用
}
```

## 错误处理

常见错误码：
- `400`: 请求参数错误
- `404`: 项目不存在
- `500`: 服务器内部错误

错误响应示例：
```json
{
    "code": 404,
    "message": "项目不存在: 999",
    "data": null
}
```

## 性能考虑

1. **上下文管理**：对话上下文存储在内存中，重启服务会丢失
2. **向量检索**：检索结果数量影响响应时间
3. **流式响应**：建议使用Server-Sent Events或WebSocket处理长连接
4. **并发限制**：单个项目的并发对话请求建议控制在合理范围内

## 后续优化计划

1. **持久化存储**：将对话上下文存储到数据库
2. **缓存优化**：对频繁检索的内容进行缓存
3. **负载均衡**：支持多实例部署
4. **监控告警**：添加性能监控和异常告警 