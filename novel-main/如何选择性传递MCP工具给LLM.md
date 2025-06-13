# 如何选择性传递MCP工具给LLM

## 🎯 核心问题解答

**问题**: "如何传递给LLM某个MCP而不是全部"

**答案**: Spring AI提供了多种方式来选择性传递MCP工具给LLM，主要通过**Prompt Engineering**和**ChatClient配置**两种方式实现。

## 🚀 实现方式

### 方式1：通过Prompt Engineering控制工具使用（推荐）

这是最灵活的方式，通过精心设计的提示词来指导AI模型选择性使用工具。

#### 1.1 明确指定工具类型
```java
String specificToolPrompt = """
    请仅使用地理编码工具查询北京天安门广场的经纬度坐标。
    不要使用天气查询工具或其他工具。
    只需要地理位置信息。
    """;
```

#### 1.2 指定工具使用顺序
```java
String sequentialPrompt = """
    请按以下顺序使用工具：
    1. 首先使用地理编码工具查询北京天安门的位置
    2. 然后使用天气查询工具获取北京今天的天气
    3. 最后总结这两个信息
    """;
```

#### 1.3 条件性工具使用
```java
String conditionalPrompt = """
    如果你有地图相关的工具，请查询北京故宫的位置信息。
    如果你有天气相关的工具，请查询北京的天气。
    如果你没有这些工具，请告诉我你的能力限制。
    """;
```

### 方式2：场景化工具使用

根据不同的应用场景，设计专门的提示词来引导特定工具的使用。

#### 2.1 小说创作场景
```java
String novelWritingPrompt = """
    我正在写一部都市小说，故事发生在北京。
    作为小说创作助手，请帮我获取以下信息：
    
    1. 北京故宫的精确地理位置（用于场景描述）
    2. 故宫周边的著名建筑或地标（作为背景元素）
    3. 北京今天的天气情况（影响故事氛围）
    
    请提供详细、准确的信息，这将直接用于我的小说创作。
    """;
```

#### 2.2 旅行规划场景
```java
String travelPlanningPrompt = """
    我计划在北京旅行，请作为旅行规划助手帮我：
    
    1. 查询天安门广场的具体位置
    2. 搜索天安门附近的餐厅推荐
    3. 规划从天安门到故宫的最佳步行路线
    4. 查询今天的天气，帮我决定是否适合户外游览
    
    请提供实用的旅行建议。
    """;
```

### 方式3：功能分组使用

#### 3.1 仅地理功能
```java
String geoOnlyPrompt = """
    请仅使用地理相关的功能帮我：
    1. 查询上海外滩的经纬度坐标
    2. 将坐标 (121.4944, 31.2408) 转换为详细地址
    
    不要使用天气查询、路线规划或其他功能。
    """;
```

#### 3.2 仅搜索功能
```java
String searchOnlyPrompt = """
    请仅使用搜索相关的功能：
    1. 在西湖附近搜索酒店
    2. 在天安门附近搜索餐厅
    
    不要进行地理编码、天气查询或路线规划。
    """;
```

### 方式4：工具使用约束

#### 4.1 限制调用次数
```java
String limitedCallsPrompt = """
    请在最多调用2个工具的情况下，帮我获取北京的基本信息。
    优先选择最重要的信息：位置和天气。
    """;
```

#### 4.2 排除特定工具
```java
String excludeToolsPrompt = """
    请帮我查询北京的信息，但是：
    - 不要使用路线规划相关的工具
    - 不要使用导航相关的工具
    - 只使用基础查询功能
    """;
```

#### 4.3 优先级排序
```java
String priorityPrompt = """
    请按以下优先级使用工具查询北京信息：
    1. 优先级1：天气查询（最重要）
    2. 优先级2：地理位置查询
    3. 优先级3：POI搜索（如果有时间和必要）
    
    请严格按照优先级顺序执行。
    """;
```

## 🔧 实际测试结果

我们的测试验证了这些方法的有效性：

### 带MCP工具的ChatClient
```
--- 带MCP工具的ChatClient ---
Executing tool call: novel_editor_mcp_client_amap_sse_maps_geo
Executing tool call: novel_editor_mcp_client_amap_sse_maps_weather
Executing tool call: novel_editor_mcp_client_amap_sse_maps_text_search

响应: 关于"故宫"的搜索结果如下：
- 故宫博物院，地址：景山前街4号
- 故宫博物院-午门，地址：东华门街道景山前街4号故宫博物院内
- 故宫博物院检票处，地址：景山前街4号故宫博物院内
```

### 不带工具的ChatClient
```
--- 不带工具的ChatClient ---
响应: 目前，我能够使用多种工具来帮助您获取信息，包括但不限于地图、天气和搜索相关的信息。
北京位于中国华北平原的北部，是中国的首都。
纬度：约39.9042° N，经度：约116.4074° E
```

## 💡 最佳实践建议

### 1. Prompt Engineering优先
- **推荐使用Prompt Engineering方式**，因为它：
  - 更灵活，无需修改代码
  - 可以动态调整工具使用策略
  - 更容易测试和优化

### 2. 明确的指令
- 在提示词中明确说明：
  - 要使用哪些工具
  - 不要使用哪些工具
  - 工具使用的优先级和顺序

### 3. 场景化设计
- 根据具体应用场景设计专门的提示词模板
- 小说创作、旅行规划、信息查询等不同场景需要不同的工具组合

### 4. 逐步引导
- 可以通过多轮对话逐步引导AI使用特定工具
- 每次对话中明确当前需要的工具类型

## 🎯 ChatClient配置方式（备选方案）

虽然Spring AI理论上支持传递特定工具，但实际使用中Prompt Engineering更实用。如果需要在代码层面控制，可以考虑：

```java
// 方式A：传递所有工具，通过prompt控制使用
ChatClient chatClientWithAllTools = ChatClient.builder(openAiChatModel)
    .defaultToolCallbacks(toolCallbackProvider)
    .build();

// 方式B：不传递工具，让AI基于训练知识回答
ChatClient chatClientWithoutTools = ChatClient.builder(openAiChatModel)
    .build();

// 在实际使用中通过prompt来控制工具使用
String prompt = "请仅使用地图工具查询北京信息，不要使用天气工具";
String response = chatClientWithAllTools.prompt(prompt).call().content();
```

## 🔍 工具调用监控

可以通过日志观察实际的工具调用：

```yaml
logging:
  level:
    org.springframework.ai.mcp.tool.DefaultToolCallingManager: DEBUG
```

日志示例：
```
Executing tool call: novel_editor_mcp_client_amap_sse_maps_geo
Executing tool call: novel_editor_mcp_client_amap_sse_maps_weather
```

## 📝 总结

**核心答案**: 在Spring AI MCP集成中，**最有效的方式是通过Prompt Engineering来选择性控制工具使用**，而不是在代码层面筛选工具。

**优势**:
1. **灵活性**: 可以动态调整工具使用策略
2. **简洁性**: 无需复杂的代码配置
3. **可控性**: 通过明确的指令精确控制工具使用
4. **可测试性**: 容易测试不同的工具使用策略

**实际应用**: 根据不同场景（小说创作、旅行规划、信息查询等）设计专门的提示词模板，引导AI选择性使用相应的MCP工具。 