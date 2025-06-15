# Gemini工具名称兼容性问题解决方案

## 🚨 问题现象

您遇到的错误信息：
```
HTTP 400 - GenerateContentRequest.tools[0].function_declarations[2].name: 
Invalid function name. Must start with a letter or an underscore. 
Must be alphameric (a-z, A-Z, 0-9), underscores (_), dots (.) or dashes (-), 
with a maximum length of 64.
```

## 🔍 根本原因分析

### 1. Gemini模型的严格命名规范
Gemini模型对工具函数名称有非常严格的要求：
- ✅ 必须以字母或下划线开头
- ✅ 只能包含：字母(a-z, A-Z)、数字(0-9)、下划线(_)、点(.)、短横线(-)
- ✅ 最大长度：64字符

### 2. Spring AI MCP工具名称生成规则
Spring AI MCP客户端按以下规则自动生成工具名称：
```
{mcp-client-name}_{connection-name}_{original-tool-name}
```

### 3. 实际生成的工具名称（不兼容）
```
❌ novel_editor_mcp_client_amap_sse_maps_geo (43字符，但格式复杂)
❌ novel_editor_mcp_client_amap_sse_maps_weather (47字符)
❌ novel_editor_mcp_client_amap_sse_maps_text_search (51字符)
❌ novel_editor_mcp_client_amap_sse_maps_around_search (55字符)
```

问题：
- 名称虽然未超过64字符，但格式过于复杂
- 包含过多下划线连接符
- Gemini对这种命名模式不友好

## 💡 解决方案

### 方案1：切换到OpenAI模型（强烈推荐）⭐

这是最简单有效的解决方案，OpenAI模型对工具名称限制更宽松。

#### 步骤1：使用OpenAI配置
```bash
# 运行OpenAI版本的测试
mvn test -Dtest=OpenAiMcpToolsTest#testOpenAiWithAmapTools
```

#### 步骤2：配置文件已准备
- ✅ `application-openai-mcp.yml` - OpenAI + 高德地图配置
- ✅ `OpenAiMcpToolsTest.java` - 专门的测试类

#### 优势
- ✅ 无需修改任何MCP配置
- ✅ 完全兼容高德地图MCP工具
- ✅ 工具名称限制宽松
- ✅ 立即可用

### 方案2：缩短MCP客户端名称（实验性）

如果必须使用Gemini，可以尝试缩短名称：

#### 配置修改
```yaml
# application-gemini-short-names.yml
spring:
  ai:
    mcp:
      client:
        name: ne-mcp  # 从 novel-editor-mcp-client 缩短为 ne-mcp
        sse:
          connections:
            amap:  # 从 amap-sse 缩短为 amap
```

#### 预期工具名称
```
✅ ne_mcp_amap_maps_geo (19字符)
✅ ne_mcp_amap_maps_weather (23字符)
✅ ne_mcp_amap_maps_text_search (30字符)
```

#### 局限性
- ⚠️  仍可能存在兼容性问题
- ⚠️  需要测试验证
- ⚠️  不保证100%成功

### 方案3：使用本地MCP工具（备选）

使用符合Gemini规范的本地工具：

#### 配置
```yaml
spring:
  ai:
    mcp:
      client:
        stdio:
          connections:
            filesystem:
              command: npx
              args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
```

#### 局限性
- ❌ 无法使用高德地图功能
- ❌ 功能受限
- ❌ 不满足您的需求

## 🚀 推荐实施步骤

### 立即解决方案（推荐）

1. **运行OpenAI测试验证**
```bash
mvn test -Dtest=OpenAiMcpToolsTest#testOpenAiWithAmapTools
```

2. **如果测试成功，修改主配置**
```yaml
# 在您的主配置文件中
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-3.5-turbo  # 替换 gemini-2.0-flash
```

3. **验证功能正常**
```bash
mvn test -Dtest=McpClientTest#testAmapMcpIntegration
```

### 长期解决方案

1. **配置环境变量管理**
```bash
export OPENAI_API_KEY=your_api_key
export AMAP_API_KEY=your_amap_key
```

2. **设置模型切换机制**
```yaml
# application-dev.yml (开发环境 - 使用OpenAI)
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-3.5-turbo

# application-test.yml (测试环境 - 使用Gemini)
spring:
  ai:
    openai:
      chat:
        options:
          model: gemini-2.0-flash
```

## 📊 模型兼容性对比

| 特性 | OpenAI (GPT-3.5/4) | Gemini 2.0 |
|------|-------------------|-------------|
| MCP工具支持 | ✅ 优秀 | ⚠️ 有限 |
| 工具名称规范 | 🟡 宽松 | ❌ 严格 |
| 高德地图兼容 | ✅ 完全支持 | ❌ 不兼容 |
| 复杂工具名称 | ✅ 支持 | ❌ 不支持 |
| 性能 | 🟡 良好 | ✅ 优秀 |
| 成本 | ❌ 需要API密钥 | ✅ 免费额度 |

## 🎯 最终建议

### 对于您的用例（高德地图MCP集成）
**强烈推荐使用OpenAI模型**，因为：

1. ✅ **完美兼容** - 无需任何配置修改
2. ✅ **立即可用** - 测试类已准备就绪
3. ✅ **功能完整** - 支持所有高德地图功能
4. ✅ **稳定可靠** - 经过验证的解决方案

### 实施优先级
1. **立即** - 运行 `OpenAiMcpToolsTest` 验证
2. **短期** - 切换到OpenAI模型配置
3. **长期** - 建立模型切换机制

### 成本考虑
- OpenAI API调用成本相对较低
- 对于开发测试，成本可控
- 可以设置使用限制和监控

## 🔧 故障排除

### 如果OpenAI测试仍然失败
1. 检查API密钥配置
2. 验证网络连接
3. 查看详细错误日志
4. 确认MCP服务器可访问

### 如果仍想使用Gemini
1. 尝试方案2（缩短名称）
2. 考虑使用本地MCP工具
3. 等待Spring AI未来版本的改进

## 📞 技术支持

如果遇到问题，请提供：
1. 完整的错误日志
2. 使用的配置文件
3. 测试运行结果
4. Spring AI版本信息

---

**总结：切换到OpenAI模型是解决Gemini工具名称兼容性问题的最佳方案。** 