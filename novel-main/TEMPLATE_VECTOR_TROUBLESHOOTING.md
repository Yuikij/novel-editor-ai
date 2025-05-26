# 模板向量化检索问题排查指南

## 问题现象

模板向量化完成后，在对话时检索不到相关文档，导致无法基于模板内容进行对话。

## 问题分析

### 1. 向量存储机制

在模板向量化过程中：
- 模板内容被分块（chunk）处理
- 每个分块作为一个`Document`存储到向量数据库
- 每个文档包含以下metadata：
  ```java
  metadata.put("id", "template-" + templateId);
  metadata.put("templateId", templateId); // Long类型
  metadata.put("type", "template");
  metadata.put("name", templateName);
  metadata.put("tags", tags);
  metadata.put("chunkIndex", index);
  metadata.put("totalChunks", total);
  ```

### 2. 检索过滤机制

检索时使用过滤表达式来限定搜索范围：
- 目标：只检索指定模板的文档
- 过滤字段：`templateId`
- 数据类型：Long类型

### 3. 可能的问题原因

#### A. 过滤表达式语法问题
不同向量数据库对过滤表达式的语法要求不同：
- Chroma可能需要：`templateId == 123`
- 某些数据库可能需要：`templateId == '123'`
- 还有可能需要：`templateId == "123"`

#### B. 数据类型不匹配
- 存储时：`templateId`作为Long类型存储
- 检索时：过滤表达式中的值类型必须匹配

#### C. 向量化未完成或失败
- 文档可能未成功存储到向量数据库
- 向量化过程中出现异常但未正确处理

## 解决方案

### 1. 多策略过滤检索

已实现多种过滤策略的自动尝试：

```java
String[] filterStrategies = {
    "templateId == " + templateId,           // 数字类型
    "templateId == '" + templateId + "'",    // 单引号字符串
    "templateId == \"" + templateId + "\"",  // 双引号字符串
    "type == 'template' && templateId == " + templateId,
    "type == \"template\" && templateId == " + templateId
};
```

### 2. 降级策略

如果过滤策略都失败，自动启用降级策略：
1. 无过滤器搜索所有文档
2. 在应用层手动筛选匹配的文档
3. 基于文档ID前缀匹配：`template-{templateId}-`

### 3. 向量化验证

在向量化完成后自动验证文档是否正确存储：

```java
// 验证文档是否已正确存储
String verifyFilter = "templateId == " + templateId;
SearchRequest verifyRequest = SearchRequest.builder()
        .query("test")
        .topK(1)
        .similarityThreshold(0.0f)
        .filterExpression(verifyFilter)
        .build();
List<Document> verifyResults = vectorStore.similaritySearch(verifyRequest);
```

### 4. 调试工具

#### A. API调试端点
```http
GET /templates/vector/{id}/debug
```

#### B. 调试工具类
```java
VectorStoreDebugUtil.debugTemplateDocuments(vectorStore, templateId);
```

该工具会尝试多种过滤表达式并输出详细的调试信息。

## 排查步骤

### 1. 检查向量化状态
```http
GET /templates/vector/{id}/progress
```

确认模板状态为`INDEXED`且进度为100%。

### 2. 启动调试模式
```http
GET /templates/vector/{id}/debug
```

查看日志输出，确认：
- 文档是否成功存储
- 哪种过滤表达式有效
- metadata结构是否正确

### 3. 检查日志
在对话时观察日志输出：
- 检索过程的详细信息
- 各种过滤策略的尝试结果
- 降级策略的执行情况

### 4. 手动验证
如果自动策略都失败，可以手动执行检索测试：

```java
// 尝试无过滤器搜索
SearchRequest request = SearchRequest.builder()
        .query("test")
        .topK(10)
        .similarityThreshold(0.0f)
        .build();
List<Document> results = vectorStore.similaritySearch(request);

// 检查结果中是否包含目标模板的文档
for (Document doc : results) {
    if (doc.getId().contains("template-" + templateId)) {
        // 找到了相关文档
        log.info("Found document: {}", doc.getMetadata());
    }
}
```

## 预防措施

### 1. 完善的错误处理
- 向量化过程中的异常处理
- 检索失败时的降级策略
- 详细的日志记录

### 2. 验证机制
- 向量化完成后的自动验证
- 定期检查向量存储的一致性

### 3. 监控告警
- 向量化失败率监控
- 检索成功率监控
- 文档数量异常告警

## 技术细节

### 向量数据库配置
当前使用Chroma向量数据库：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
</dependency>
```

### 文档结构
每个分块文档的结构：
- **id**: `template-{templateId}-chunk-{index}`
- **content**: 分块后的文本内容
- **metadata**: 包含templateId、type、name、tags等信息

### 检索优化
1. **多策略尝试**: 自动尝试多种过滤表达式
2. **降级机制**: 过滤失败时使用无过滤搜索+手动筛选
3. **调试支持**: 提供详细的调试工具和日志

## 常见问题解答

### Q: 为什么向量化成功但检索不到文档？
A: 最可能的原因是过滤表达式语法不匹配。我们的解决方案会自动尝试多种语法格式。

### Q: 如何确认文档是否真的存储成功？
A: 使用调试API `/templates/vector/{id}/debug` 可以详细检查存储情况。

### Q: 检索速度很慢怎么办？
A: 检查相似度阈值设置，过低的阈值会导致检索大量无关文档。

### Q: 如何优化检索精度？
A: 
1. 调整分块大小（chunk-size）
2. 优化相似度阈值
3. 改进查询文本的质量

## 总结

通过多策略过滤、降级机制、验证步骤和调试工具的综合应用，我们确保了模板向量化和检索的稳定性。即使在特定情况下某些策略失败，系统也能自动切换到可用的方案，保证功能的可用性。 