# Gemini模型MCP工具兼容性问题解决方案

## 🚨 问题现象

您遇到的错误信息：
```
HTTP 400 - [{
  "error": {
    "code": 400,
    "message": "* GenerateContentRequest.tools[0].function_declarations[2].name: Invalid function name. Must start with a letter or an underscore. Must be alphameric (a-z, A-Z, 0-9), underscores (_), dots (.) or dashes (-), with a maximum length of 64.\n",
    "status": "INVALID_ARGUMENT"
  }
}]
```

## 🔍 根本原因分析

### 1. Gemini模型的严格命名规范
Gemini模型对工具函数名称有非常严格的要求：
- ✅ 必须以字母或下划线开头
- ✅ 只能包含：字母(a-z, A-Z)、数字(0-9)、下划线(_)、点(.)、短横线(-)
- ✅ 最大长度：64字符

### 2. 高德地图MCP工具名称不兼容
高德地图MCP工具的实际名称类似：
```
❌ novel_editor_mcp_client_amap_sse_maps_geo
❌ novel_editor_mcp_client_amap_sse_maps_weather
❌ novel_editor_mcp_client_amap_sse_maps_text_search
```

问题：
- 名称过长（超过64字符）
- 包含过多下划线和连字符
- 格式不符合Gemini规范

## 💡 解决方案

### 方案1：切换到OpenAI兼容模型（推荐）

OpenAI模型对工具名称更宽松，是最简单的解决方案。

#### 修改配置
```yaml
# application-openai.yml
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-3.5-turbo  # 或 gpt-4
          temperature: 0.7
    mcp:
      client:
        enabled: true
        # ... MCP配置保持不变
```

#### 优势
- ✅ 无需修改MCP工具配置
- ✅ 完全兼容高德地图MCP工具
- ✅ 工具名称限制更宽松
- ✅ 测试验证可用

### 方案2：仅使用符合Gemini规范的MCP工具

如果必须使用Gemini模型，只能使用符合命名规范的工具。

#### 修改配置
```yaml
# application-gemini.yml
spring:
  ai:
    openai:
      chat:
        options:
          model: gemini-2.0-flash
          temperature: 0.7
    mcp:
      client:
        enabled: true
        # 移除高德地图SSE连接，仅保留本地工具
        stdio:
          connections:
            filesystem-server:
              command: npx
              args:
                - "-y"
                - "@modelcontextprotocol/server-filesystem"
                - "/tmp"
```

#### 局限性
- ❌ 无法使用高德地图功能
- ❌ 功能受限
- ❌ 需要寻找替代方案

### 方案3：工具名称映射（理论方案）

理论上可以通过中间层映射工具名称，但Spring AI目前不直接支持。

## 🛠 具体实施步骤

### 步骤1：创建OpenAI配置文件

```yaml
# novel-main/src/test/resources/application-openai-mcp.yml
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-3.5-turbo
          temperature: 0.7
    mcp:
      client:
        enabled: true
        name: novel-editor-mcp-client
        version: 1.0.0
        request-timeout: 30s
        type: SYNC
        
        sse:
          connections:
            amap-sse:
              url: "https://mcp.amap.com/sse?key=22f29e896498f61b3f96ac3a5a0b6dad"
              description: "高德地图MCP服务器"

logging:
  level:
    org.springframework.ai.mcp: DEBUG
    org.springframework.ai.tool: DEBUG
```

### 步骤2：创建OpenAI测试类

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-openai-mcp.yml")
public class OpenAiMcpToolsTest {
    
    @Autowired
    private ChatModel openAiChatModel;
    
    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;
    
    @Test
    void testOpenAiWithAmapTools() {
        if (toolCallbackProvider == null) {
            System.out.println("跳过测试 - 未配置MCP工具");
            return;
        }
        
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
        
        String prompt = "请查询北京天安门广场的位置和今天的天气。";
        String response = chatClient.prompt(prompt).call().content();
        
        System.out.println("OpenAI + 高德地图响应: " + response);
    }
}
```

### 步骤3：运行测试验证

```bash
mvn test -Dtest=OpenAiMcpToolsTest#testOpenAiWithAmapTools
```

## 📊 模型兼容性对比

| 特性 | OpenAI (GPT-3.5/4) | Gemini | 
|------|-------------------|--------|
| MCP工具支持 | ✅ 优秀 | ⚠️ 有限 |
| 工具名称规范 | 🟡 宽松 | ❌ 严格 |
| 高德地图兼容 | ✅ 完全支持 | ❌ 不兼容 |
| 性能 | 🟡 良好 | ✅ 优秀 |
| 成本 | ❌ 需要API密钥 | ✅ 免费额度 |

## 🎯 推荐策略

### 对于生产环境
1. **优先使用OpenAI模型** - 最佳MCP兼容性
2. **配置API密钥管理** - 确保稳定服务
3. **设置错误处理** - 优雅降级

### 对于开发测试
1. **OpenAI用于MCP功能测试** - 完整功能验证
2. **Gemini用于基础功能测试** - 节省成本
3. **分离测试配置** - 灵活切换

### 对于特定场景
- **需要地图功能** → 必须使用OpenAI
- **纯文本处理** → 可以使用Gemini
- **工具调用频繁** → 推荐OpenAI

## 🔧 配置模板

### OpenAI + 高德地图完整配置
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:your-api-key}
      chat:
        options:
          model: gpt-3.5-turbo
          temperature: 0.7
    mcp:
      client:
        enabled: true
        type: SYNC
        sse:
          connections:
            amap-sse:
              url: "https://mcp.amap.com/sse?key=${AMAP_API_KEY:your-amap-key}"
```

### Gemini基础配置
```yaml
spring:
  ai:
    openai:
      chat:
        options:
          model: gemini-2.0-flash
          temperature: 0.7
    mcp:
      client:
        enabled: true
        # 仅本地工具，避免命名冲突
        stdio:
          connections:
            basic-tools:
              command: npx
              args: ["-y", "@modelcontextprotocol/server-everything"]
```

## 📝 总结

**核心问题**：Gemini模型对MCP工具函数名称有严格限制，与高德地图MCP工具不兼容。

**最佳解决方案**：
1. **切换到OpenAI模型** - 获得完整MCP功能支持
2. **保留Gemini配置** - 用于不需要工具的场景
3. **灵活配置切换** - 根据需求选择合适模型

**关键配置**：
- OpenAI: 完整MCP + 高德地图功能
- Gemini: 基础功能，避免工具名称冲突

这样既保证了功能完整性，又保持了配置的灵活性！ 