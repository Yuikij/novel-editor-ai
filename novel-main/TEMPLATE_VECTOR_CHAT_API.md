# 模板向量化和对话功能 API 文档

## 概述

本文档描述了基于模板的向量化知识库和对话功能的API接口。该功能允许将模板内容向量化存储到向量数据库中，并基于向量检索进行智能对话。

## 功能特性

- 模板内容向量化存储
- 向量化进度跟踪
- 基于向量检索的智能对话
- 流式对话支持
- 对话上下文管理

## API 接口

### 1. 模板向量化管理

#### 1.1 查询向量化进度

```http
GET /templates/vector/{id}/progress
```

**参数：**
- `id` (path): 模板ID

**响应：**
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "templateId": 123,
    "templateName": "小说写作模板",
    "vectorStatus": "INDEXED",
    "vectorProgress": 100,
    "vectorStartTime": "2024-01-01T10:00:00",
    "vectorEndTime": "2024-01-01T10:05:00",
    "vectorErrorMessage": null,
    "canChat": true
  }
}
```

**向量化状态说明：**
- `NOT_INDEXED`: 未索引
- `INDEXING`: 索引中
- `INDEXED`: 已索引
- `FAILED`: 索引失败

#### 1.2 流式获取向量化进度

```http
GET /templates/vector/{id}/progress/stream
```

**参数：**
- `id` (path): 模板ID

**响应：** Server-Sent Events 流，实时推送进度更新

#### 1.3 手动导入向量数据库

```http
POST /templates/vector/{id}/index
```

**参数：**
- `id` (path): 模板ID

**响应：**
```json
{
  "code": 200,
  "message": "向量化成功",
  "data": true
}
```

#### 1.4 异步导入向量数据库

```http
POST /templates/vector/{id}/index/async
```

**参数：**
- `id` (path): 模板ID

**响应：**
```json
{
  "code": 200,
  "message": "向量化任务已启动",
  "data": true
}
```

#### 1.5 删除向量索引

```http
DELETE /templates/vector/{id}/index
```

**参数：**
- `id` (path): 模板ID

**响应：**
```json
{
  "code": 200,
  "message": "删除成功",
  "data": true
}
```

#### 1.6 批量导入向量数据库

```http
POST /templates/vector/batch/index
```

**请求体：**
```json
{
  "ids": [123, 456, 789]
}
```

**响应：**
```json
{
  "code": 200,
  "message": "批量向量化成功",
  "data": true
}
```

### 2. 模板对话功能

#### 2.1 与模板对话

```http
POST /templates/chat
```

**请求体：**
```json
{
  "templateId": 123,
  "message": "请介绍一下这个模板的主要内容",
  "conversationId": "conv-123-456",
  "maxResults": 5,
  "similarityThreshold": 0.7
}
```

**响应：**
```json
{
  "code": 200,
  "message": "对话成功",
  "data": "这个模板主要包含了小说写作的基本结构和技巧..."
}
```

#### 2.2 流式对话

```http
POST /templates/chat/stream
```

**请求体：** 同上

**响应：** Server-Sent Events 流，实时推送对话内容

#### 2.3 检查对话可用性

```http
GET /templates/chat/{id}/can-chat
```

**参数：**
- `id` (path): 模板ID

**响应：**
```json
{
  "code": 200,
  "message": "查询成功",
  "data": true
}
```

#### 2.4 简化对话接口

```http
GET /templates/chat/{id}?message=你好&conversationId=conv-123
```

**参数：**
- `id` (path): 模板ID
- `message` (query): 消息内容
- `conversationId` (query, 可选): 对话ID
- `maxResults` (query, 可选): 最大检索结果数
- `similarityThreshold` (query, 可选): 相似度阈值

#### 2.5 简化流式对话接口

```http
GET /templates/chat/{id}/stream?message=你好&conversationId=conv-123
```

**参数：** 同上

### 3. 模板创建（支持自动向量化）

#### 3.1 创建模板并自动向量化

```http
POST /templates/with-auto-index?autoIndex=true
```

**请求体：**
```json
{
  "name": "新模板",
  "tags": "小说,写作",
  "content": "模板内容..."
}
```

#### 3.2 文件上传创建模板并自动向量化

```http
POST /templates/upload?autoIndex=true
```

**参数：**
- `name` (form): 模板名称
- `tags` (form, 可选): 模板标签
- `file` (form, 可选): 模板文件
- `content` (form, 可选): 模板内容
- `autoIndex` (form, 可选): 是否自动向量化，默认false

## 使用流程

### 1. 基本使用流程

1. **创建模板**：使用现有的模板创建接口或新的自动向量化接口
2. **向量化**：手动触发向量化或在创建时自动触发
3. **监控进度**：通过进度查询接口监控向量化状态
4. **开始对话**：向量化完成后即可开始对话

### 2. 自动向量化流程

1. **创建模板时启用自动向量化**：
   ```http
   POST /templates/upload?autoIndex=true
   ```

2. **监控向量化进度**：
   ```http
   GET /templates/vector/{id}/progress/stream
   ```

3. **开始对话**：
   ```http
   POST /templates/chat
   ```

### 3. 手动向量化流程

1. **创建模板**：
   ```http
   POST /templates
   ```

2. **手动触发向量化**：
   ```http
   POST /templates/vector/{id}/index
   ```

3. **开始对话**：
   ```http
   POST /templates/chat
   ```

## 配置参数

可以通过以下配置参数调整向量化和对话行为：

```properties
# 模板向量化配置
novel.template.chunk-size=800
novel.template.chunk-overlap=100
novel.template.max-indexable-length=100000

# 模板对话配置
novel.template.chat.max-results=5
novel.template.chat.similarity-threshold=0.7
novel.template.chat.max-tokens=2000
novel.template.chat.temperature=0.7
```

## 错误处理

### 常见错误码

- `400`: 请求参数错误
- `404`: 模板不存在
- `500`: 服务器内部错误

### 常见错误场景

1. **模板未向量化**：
   ```json
   {
     "code": 400,
     "message": "模板尚未完成向量化，无法进行对话"
   }
   ```

2. **模板内容为空**：
   ```json
   {
     "code": 400,
     "message": "模板内容为空，无法进行向量化"
   }
   ```

3. **向量化进行中**：
   ```json
   {
     "code": 400,
     "message": "模板正在索引中，请稍后再试"
   }
   ```

## 最佳实践

1. **向量化时机**：建议在模板创建时启用自动向量化，避免后续手动操作
2. **进度监控**：对于大型模板，使用流式进度接口实时监控向量化状态
3. **对话上下文**：使用conversationId维持对话上下文，提供更好的对话体验
4. **参数调优**：根据实际需求调整maxResults和similarityThreshold参数
5. **错误处理**：在前端实现适当的错误处理和用户提示

## 示例代码

### JavaScript 示例

```javascript
// 创建模板并自动向量化
async function createTemplateWithAutoIndex(templateData) {
  const response = await fetch('/templates/with-auto-index?autoIndex=true', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(templateData)
  });
  return response.json();
}

// 监控向量化进度
function monitorVectorProgress(templateId) {
  const eventSource = new EventSource(`/templates/vector/${templateId}/progress/stream`);
  
  eventSource.onmessage = function(event) {
    const progress = JSON.parse(event.data);
    console.log('向量化进度:', progress.vectorProgress + '%');
    
    if (progress.vectorStatus === 'INDEXED') {
      eventSource.close();
      console.log('向量化完成，可以开始对话');
    }
  };
}

// 流式对话
async function chatWithTemplate(templateId, message) {
  const response = await fetch('/templates/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      templateId: templateId,
      message: message
    })
  });
  
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    
    const chunk = decoder.decode(value);
    console.log('收到回复:', chunk);
  }
}
```

### Python 示例

```python
import requests
import json

# 创建模板并自动向量化
def create_template_with_auto_index(template_data):
    response = requests.post(
        '/templates/with-auto-index?autoIndex=true',
        json=template_data
    )
    return response.json()

# 与模板对话
def chat_with_template(template_id, message):
    response = requests.post('/templates/chat', json={
        'templateId': template_id,
        'message': message
    })
    return response.json()

# 检查对话可用性
def can_chat_with_template(template_id):
    response = requests.get(f'/templates/chat/{template_id}/can-chat')
    return response.json()['data']
``` 