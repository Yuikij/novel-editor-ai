# MCP客户端集成测试说明

## 概述

这个测试类 `McpClientTest` 演示了如何在Spring AI应用中集成Model Context Protocol (MCP) 客户端，特别是针对**高德地图MCP服务器**和其他远程MCP服务器的连接和使用。

## 🚀 新增功能：高德地图MCP集成

### 高德地图MCP服务器

我们已经集成了真实可用的**高德地图MCP服务器**，提供以下功能：

- **地理编码**: 地址转坐标，坐标转地址
- **POI搜索**: 搜索餐厅、酒店、景点等兴趣点
- **路径规划**: 驾车、步行、骑行、公交路线规划
- **距离计算**: 计算两点间的距离
- **天气查询**: 获取城市天气信息
- **地图展示**: 生成地图链接和导航URI

### 配置信息

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            amap-sse:
              url: "https://mcp.amap.com/sse?key=22f29e896498f61b3f96ac3a5a0b6dad"
              description: "高德地图MCP服务器，提供地图、导航、POI搜索等功能"
```

## 重要概念：MCP工具集成

### 为什么需要告知ChatClient有哪些MCP工具可用？

在Spring AI的MCP集成中，**ChatClient需要明确知道有哪些工具可以调用**。这是因为：

1. **工具发现机制**: AI模型需要知道可用的工具定义（名称、描述、参数schema）才能决定何时调用哪个工具
2. **安全控制**: 显式配置工具可以防止意外调用未授权的功能
3. **性能优化**: 只加载实际需要的工具，避免不必要的资源消耗

### 工具集成方式

```java
// 方式1：通过ToolCallbackProvider（推荐）
@Autowired(required = false)
private ToolCallbackProvider toolCallbackProvider;

ChatClient chatClientWithTools = ChatClient.builder(openAiChatModel)
    .defaultToolCallbacks(toolCallbackProvider)  // 注入MCP工具
    .build();

// 方式2：直接指定工具名称
ChatClient chatClient = ChatClient.builder(openAiChatModel)
    .defaultTools("tool1", "tool2")  // 通过名称引用
    .build();

// 方式3：传递具体的工具实例
ChatClient chatClient = ChatClient.builder(openAiChatModel)
    .defaultTools(new MyCustomTool())
    .build();
```

## 功能特性

### 1. 远程MCP服务器连接
- **高德地图SSE连接**: 连接到真实的高德地图MCP服务器
- **多服务器支持**: 配置多个MCP服务器连接（主服务器和备用服务器）
- **自动重连和错误处理**: 网络问题时的容错机制

### 2. 本地MCP服务器连接
- 支持通过STDIO连接本地MCP服务器进程
- 预配置了文件系统服务器和计算器服务器示例
- 支持环境变量和命令行参数配置

### 3. 工具集成测试
- **工具发现测试**: 验证ChatClient能否识别可用的MCP工具
- **工具调用测试**: 测试AI模型是否能正确调用MCP工具
- **对比测试**: 比较带工具和不带工具的ChatClient响应差异

### 4. 高德地图专门测试
- **基础地图功能**: 地理编码、POI搜索、路径规划
- **小说创作场景**: 为小说创作提供地理信息支持
- **错误处理**: 测试无效请求的优雅处理
- **性能测试**: 测试高德地图API的响应性能

### 5. 全面测试场景
- **基本配置测试**: 验证MCP客户端配置是否正确加载
- **基本对话功能**: 测试ChatClient的基本功能
- **远程连接测试**: 测试与远程MCP服务器的连接
- **工具发现测试**: 测试MCP工具的发现和列举
- **小说创作场景**: 针对小说创作助手的特定场景测试
- **错误处理测试**: 测试MCP客户端的错误处理能力
- **性能测试**: 测试MCP客户端的响应性能

## 配置说明

### MCP客户端配置 (application-test.yml)

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: novel-editor-mcp-client
        version: 1.0.0
        request-timeout: 30s
        type: SYNC  # 同步客户端
        
        # SSE远程连接
        sse:
          connections:
            # 高德地图MCP服务器 - 真实可用的服务
            amap-sse:
              url: "https://mcp.amap.com/sse?key=22f29e896498f61b3f96ac3a5a0b6dad"
              description: "高德地图MCP服务器，提供地图、导航、POI搜索等功能"
            
            # 备用测试服务器
            test-server-1:
              url: "http://localhost:8080/mcp"
              description: "本地测试MCP服务器"
        
        # STDIO本地连接
        stdio:
          connections:
            filesystem-server:
              command: npx
              args:
                - "-y"
                - "@modelcontextprotocol/server-filesystem"
                - "/tmp"
```

### 依赖要求

确保在 `pom.xml` 中包含以下依赖：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

## 运行测试

### 1. 运行高德地图MCP测试

```bash
# 运行所有测试
mvn test -Dtest=McpClientTest

# 只运行高德地图相关测试
mvn test -Dtest=McpClientTest#testAmapMcpIntegration
mvn test -Dtest=McpClientTest#testAmapSpecificFunctions
mvn test -Dtest=McpClientTest#testNovelWritingWithAmap
mvn test -Dtest=McpClientTest#testAmapErrorHandling
```

### 2. 不依赖外部MCP服务器运行
测试被设计为即使没有运行的MCP服务器也能执行，它们会优雅地处理连接失败：

```bash
mvn test -Dtest=McpClientTest#testMcpClientConfiguration
```

### 3. 使用本地MCP服务器
如果你想测试完整的MCP功能，可以启动本地MCP服务器：

```bash
# 安装MCP服务器
npm install -g @modelcontextprotocol/server-filesystem
npm install -g @modelcontextprotocol/server-everything

# 然后运行测试
mvn test -Dtest=McpClientTest
```

## 测试方法详解

### 高德地图专门测试

#### testAmapMcpIntegration()
测试高德地图MCP服务器的基本集成，包括：
- 地理编码（天安门广场坐标查询）
- POI搜索（附近餐厅）
- 路径规划（天安门到故宫）
- 天气查询（北京天气）

#### testAmapSpecificFunctions()
测试高德地图的各种具体功能：
- 地理位置信息查询（上海外滩）
- 酒店搜索（西湖附近）
- 距离计算（北京到上海）
- 天气预报（广州）
- 路线规划（深圳机场到腾讯大厦）

#### testNovelWritingWithAmap()
专门针对小说创作的地图应用场景：
- 获取故宫博物院地理信息用于场景描述
- 查找王府井大街地标作为小说背景
- 规划路线用于情节安排
- 查询天气影响故事发展

#### testAmapErrorHandling()
测试高德地图MCP的错误处理能力：
- 无效地址查询
- 不可能的路线规划
- 错误的天气查询
- 验证优雅的错误处理

### 原有测试方法

#### testMcpClientConfiguration()
验证MCP客户端的基本配置是否正确加载，检查ToolCallbackProvider是否可用。

#### testBasicChatFunctionality()
测试ChatClient的基本对话功能，确保AI模型能够正常响应。

#### testMcpRemoteConnection()
测试与远程MCP服务器的连接，验证配置的远程连接是否有效。

#### testMcpToolDiscovery()
测试MCP工具的发现功能，列出可用的工具和功能。

#### testMcpWithNovelWritingScenario()
专门针对小说创作场景的测试，包括：
- 文件操作工具测试
- 计算工具测试（章节字数计算）
- 网络工具测试（获取创作灵感）

#### testMcpToolIntegration()
专门测试MCP工具集成，验证ChatClient是否能正确调用MCP工具。

#### testChatClientWithAndWithoutTools()
对比带工具和不带工具的ChatClient响应差异，展示MCP工具的价值。

#### testMcpErrorHandling()
测试MCP客户端的错误处理能力，确保在服务器不可用时能够优雅处理。

#### testMcpPerformance()
测试MCP客户端的性能，包括响应时间测量。

## 故障排除

### 常见问题

1. **高德地图MCP连接失败**
   - 检查网络连接是否正常
   - 验证API密钥是否有效
   - 查看是否有防火墙阻止HTTPS连接

2. **MCP服务器连接失败**
   - 检查服务器是否运行在配置的端口上
   - 验证网络连接和防火墙设置
   - 查看日志中的详细错误信息

3. **STDIO连接失败**
   - 确保已安装相应的MCP服务器包
   - 检查命令路径和参数是否正确
   - 验证环境变量设置

4. **工具调用失败**
   - 检查工具参数格式是否正确
   - 验证工具权限和访问控制
   - 查看MCP服务器日志

5. **ToolCallbackProvider未找到**
   - 确保MCP客户端配置正确
   - 检查Spring AI自动配置是否生效
   - 验证MCP服务器是否正确暴露工具

### 日志配置

测试配置了详细的日志输出，可以通过以下方式查看：

```yaml
logging:
  level:
    org.springframework.ai.mcp: DEBUG
    org.springframework.ai.mcp.client: DEBUG
    org.springframework.ai.tool: DEBUG
    com.soukon.novelEditorAi: DEBUG
    io.modelcontextprotocol: DEBUG
```

## Spring AI MCP工具集成原理

### 自动配置机制

Spring AI MCP客户端通过以下方式自动集成工具：

1. **自动发现**: 扫描配置的MCP连接，建立客户端连接
2. **工具注册**: 从MCP服务器获取工具定义，创建ToolCallback
3. **提供者创建**: 自动创建ToolCallbackProvider bean
4. **ChatClient集成**: 将工具提供者注入到ChatClient中

### 工具调用流程

```
用户提问 → ChatClient → AI模型 → 识别需要工具 → 调用MCP工具 → 返回结果 → 生成最终回答
```

### 高德地图MCP工具流程

```
小说创作需求 → ChatClient → AI识别地理查询需求 → 调用高德地图API → 获取准确地理信息 → 生成适合小说的描述
```

## 扩展和定制

### 添加新的MCP服务器连接

在 `application-test.yml` 中添加新的连接配置：

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            my-custom-server:
              url: "http://my-server:9090/mcp"
              description: "我的自定义MCP服务器"
```

### 添加新的测试场景

创建新的测试方法来验证特定的MCP功能：

```java
@Test
void testCustomMcpScenario() {
    String customPrompt = "你的自定义提示";
    String response = chatClientWithTools.prompt(customPrompt).call().content();
    // 验证响应
}
```

### 高德地图API密钥管理

在生产环境中，建议将API密钥配置为环境变量：

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            amap-sse:
              url: "https://mcp.amap.com/sse?key=${AMAP_API_KEY}"
```

## 最佳实践

1. **容错设计**: 测试应该能够在MCP服务器不可用时正常运行
2. **配置灵活性**: 使用配置文件而不是硬编码连接信息
3. **工具验证**: 确保ChatClient正确配置了MCP工具
4. **日志记录**: 记录详细的测试过程和结果
5. **性能监控**: 监控MCP调用的响应时间
6. **错误处理**: 优雅地处理各种错误情况
7. **API限制**: 注意高德地图API的调用频率限制
8. **数据验证**: 验证从MCP工具获取的数据的准确性和有用性

## 小说创作应用场景

### 地理信息在小说中的应用

1. **场景描述**: 获取准确的地理信息来描述小说场景
2. **情节安排**: 根据真实的路线和距离安排角色行程
3. **背景设定**: 使用真实的地标和建筑作为故事背景
4. **氛围营造**: 结合天气信息营造故事氛围

### 示例应用

```java
// 查询故事发生地的详细信息
String prompt = "我的小说发生在北京，请帮我获取天安门广场的详细地理信息，包括周边建筑和交通情况，用于小说场景描述。";
String response = chatClientWithTools.prompt(prompt).call().content();
```

## 相关资源

- [Spring AI MCP 文档](https://docs.spring.io/spring-ai/reference/api/mcp/)
- [Model Context Protocol 规范](https://modelcontextprotocol.io/)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [高德地图开放平台](https://lbs.amap.com/)
- [高德地图MCP服务器文档](https://mcp.amap.com/) 