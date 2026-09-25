# Java Pi-Agent-Core 实现计划方案

## 一、项目概览

### 1.1 项目目标
实现一个跨平台的 Java 版 Pi Agent-Core，完全兼容 TypeScript 原型设计，支持：
- 多 LLM 供应商集成（OpenAI、Anthropic、Claude 等）
- 工具动态加载与执行
- 会话树管理与持久化
- 上下文智能管理与压缩
- 企业级权限与安全模型

### 1.2 核心成果物
```
pi-agent-core-java/
├── pi-core-base              # 基础设施层
├── pi-core-runtime           # 运行时引擎
├── pi-core-tools             # 工具系统
├── pi-core-session           # 会话管理
├── pi-core-context           # 上下文管理
├── pi-core-llm               # LLM 集成层
├── pi-core-security          # 安全模块
├── pi-core-spring-boot       # Spring Boot 集成
└── pi-core-examples          # 示例与测试
```

---

## 二、技术栈选型

### 2.1 核心依赖

| 组件 | 选型 | 版本 | 原因 |
|------|------|------|------|
| JDK | OpenJDK | 17+ | 虚拟线程、record、sealed class |
| 构建工具 | Maven | 3.8+ | 依赖管理、多模块支持 |
| JSON处理 | Jackson | 2.15+ | 高性能、灵活的序列化 |
| 日志 | SLF4J + Logback | 2.0+ | 标准日志门面、性能优化 |
| 测试 | JUnit5 + Mockito | 5.9+ | 现代化测试框架 |
| 异步 | Project Reactor | 2023.12+ | 反应式编程、非阻塞I/O |
| HTTP客户端 | OkHttp/Spring WebClient | 4.11+/6.0+ | 连接复用、超时控制 |

### 2.2 可选组件
- Guava、Apache Commons、Caffeine、Netty、ANTLR4、gRpc

---

## 三、架构设计

### 3.1 分层架构

```
Application Layer (应用层)
  ↓
Agent Orchestration Layer (编排层)
  ↓
Core Runtime Layer (运行时层)
  ↓
LLM Integration Layer (LLM层)
  ↓
Infrastructure Layer (基础设施层)
```

### 3.2 核心模块关系

```
AgentExecutor (主编排器)
├── SessionManager (会话树管理)
├── ToolRegistry (工具注册表)
├── ContextManager (上下文管理)
├── LLMProvider (LLM供应商)
└── SecurityManager (安全管理)
```

---

## 四、核心类设计

### 4.1 会话管理模块 (pi-core-session)

**关键类**:
- `SessionNode`: 会话树节点
- `SessionMessage`: 密封接口 (UserMessage、AssistantMessage、ToolResultMessage)
- `SessionStorage`: 持久化接口 (JSONL/MongoDB/PostgreSQL)
- `SessionManager`: 会话树操作器

**核心功能**:
- 支持分支、回滚、历史查询
- 无损历史管理 (JSONL 格式)
- 元数据追踪 (创建时间、token 计数、标签)

### 4.2 工具系统模块 (pi-core-tools)

**关键类**:
- `ToolDefinition`: 工具定义 (参数 schema、描述)
- `ToolCall`: 工具调用请求
- `ToolResult`: 工具执行结果
- `ToolRegistry`: 工具注册表
- `ToolLoader`: 动态加载器 (JAR/类路径扫描)

**核心功能**:
- 工具的生命周期管理
- 动态加载与卸载 (热更新)
- 权限与隔离控制

### 4.3 上下文管理模块 (pi-core-context)

**关键类**:
- `ContextLayer`: 6层优先级 enum
- `ContextEntry`: 上下文条目
- `ContextManager`: 上下文栈管理器
- `ContextCompressor`: 自适应压缩

**核心功能**:
- 6层优先级栈 (SYSTEM > CONVERSATION > TURN > TOOL > USER > DYNAMIC)
- 动态注入与移除
- 智能压缩 (保留高优先级，摘要低优先级)
- Token 计数与限制

### 4.4 LLM 集成模块 (pi-core-llm)

**关键类**:
- `LLMProvider`: 供应商接口
- `OpenAIProvider`: GPT-4/3.5 实现
- `AnthropicProvider`: Claude 实现
- `ChatCompletionResponse`: LLM 响应

**核心功能**:
- 多供应商支持
- 消息格式转换
- 流式输出 (SSE)
- 向量嵌入集成
- 重试与错误处理

### 4.5 Agent 执行引擎 (pi-core-runtime)

**关键类**:
- `AgentExecutor`: 主执行器
- `AgentConfig`: 配置对象
- `AgentResponse`: 响应对象
- `ExecutionContext`: 执行上下文

**核心流程**:
1. 用户输入 → 追加到会话树
2. 组装多层上下文
3. 构建 LLM 消息 + 工具列表
4. 调用 LLM (带重试)
5. 解析工具调用
6. 并发执行工具
7. 收集结果 → 保存到会话
8. 检查是否继续循环 (最多 N 轮)

### 4.6 安全管理模块 (pi-core-security)

**关键类**:
- `PermissionContext`: 权限上下文 (用户、角色、属性)
- `SecurityPolicy`: 策略接口
- `RoleBasedSecurityPolicy`: 基于角色的实现
- `IsolationExecutor`: 隔离执行器 (Docker/Process)

**核心功能**:
- 细粒度权限控制 (工具级别)
- 三种隔离方案 (无/容器/VM)
- 审计日志

---

## 五、实现路线图 (23 周)

### Phase 1: 基础设施 (4 周)

**周 1-2**: 项目搭建
- Maven 多模块结构
- GitHub Actions CI/CD
- 代码检查 (Spotless + CheckStyle)

**周 3-4**: 核心基础类
- 数据模型定义 (SessionNode、ContextEntry 等)
- 异常体系
- 工具类库

### Phase 2: 会话与上下文 (3 周)

**周 5-6**: 会话管理
- SessionManager 实现
- SessionStorage 多种后端 (File/RocksDB/MongoDB)
- 会话树遍历与分支

**周 7**: 上下文管理
- ContextManager 6层栈
- AdaptiveContextCompressor
- Token 计数与优化

### Phase 3: 工具系统 (3 周)

**周 8-9**: 工具核心
- ToolRegistry 注册表
- 生命周期管理
- 权限检查集成

**周 10**: 动态加载
- ToolLoader 实现
- JAR 加载与卸载
- 热更新支持

### Phase 4: LLM 集成 (4 周)

**周 11**: 基础架构
- LLMProvider 接口
- 消息格式转换
- 错误处理与重试机制

**周 12-13**: 多提供商
- OpenAIProvider 完整实现
- AnthropicProvider 实现
- 供应商工厂与配置

**周 14**: 增强功能
- 流式输出 (SSE)
- 向量嵌入
- 函数调用格式化

### Phase 5: Agent 执行引擎 (3 周)

**周 15**: 核心执行逻辑
- AgentExecutor 主循环
- LLM 消息构建
- 工具调用处理与结果收集

**周 16**: 高级功能
- 思维链 (Chain-of-Thought)
- 自我修正机制
- 流程控制与决策树

**周 17**: 性能优化
- 缓存层 (Caffeine)
- 批处理支持
- 并发优化 (虚拟线程)

### Phase 6: 安全与隔离 (2 周)

**周 18**: 基础安全
- RoleBasedSecurityPolicy
- 权限缓存
- 审计日志系统

**周 19**: 隔离方案
- DockerIsolationExecutor
- ProcessIsolationExecutor
- 安全测试与漏洞扫描

### Phase 7: 应用集成与发布 (2 周)

**周 20**: Spring Boot 集成
- RestController (RESTful API)
- WebSocket (实时推送)
- 配置自动化

**周 21**: 文档与示例
- JavaDoc API 文档
- Swagger/OpenAPI
- 最佳实践指南
- 3 个完整示例

**周 22-23**: 测试与发布
- 压力测试 (1000+ 并发)
- 基准测试
- Maven Central 发布

---

## 六、关键实现细节

### 6.1 会话树持久化格式

```jsonl
{"nodeId":"1","parentId":null,"type":"user","content":"hello","ts":"2024-01-01T10:00:00Z","tokens":2}
{"nodeId":"2","parentId":"1","type":"assistant","content":"Hi!","toolCalls":[],"ts":"2024-01-01T10:00:05Z","tokens":5}
{"nodeId":"3","parentId":"1","type":"user","content":"how are you?","ts":"2024-01-01T10:00:15Z","tokens":4}
```

**存储方案**:
- 本地: RocksDB (高性能)
- 云端: MongoDB (JSONB 文档) 或 PostgreSQL (JSON 列)
- 增量备份 & 快照

### 6.2 上下文优先级权重

```
SYSTEM(6)           # 系统提示词
CONVERSATION(5)     # 对话历史
TURN(4)            # 当前轮次上下文
TOOL_EXECUTION(3)  # 工具执行结果
USER_PROVIDED(2)   # 用户注入
DYNAMIC(1)         # 动态注入 (当前最低)
```

### 6.3 Agent 执行循环伪代码

```python
def agentExecute(userInput, sessionId, permissions):
    session = sessionManager.getSession(sessionId)
    
    # 1. 用户输入 → 会话树
    userNode = sessionManager.append(UserMessage(userInput))
    
    # 2. 组装上下文 (按优先级)
    context = contextManager.assemble()
    
    # 3. 构建 LLM 消息
    messages = buildMessages(session.history, context, userInput)
    messages.append(getAvailableTools())
    
    # 4. 调用 LLM
    response = llmProvider.chat(messages, config)
    
    # 5. 处理工具调用
    if response.toolCalls:
        results = []
        for toolCall in response.toolCalls:
            # 权限检查
            if not securityManager.canExecute(toolCall.toolName, permissions):
                results.append(ToolResult(DENIED))
            else:
                result = toolRegistry.execute(toolCall)
                results.append(result)
        
        # 保存工具结果到会话
        for result in results:
            sessionManager.append(ToolResultMessage(result))
        
        # 检查是否继续
        if session.toolCallCount < maxToolCalls:
            # 继续循环 (递归或迭代)
            return agentExecute("Continue", sessionId, permissions)
    
    # 6. 保存助手消息
    sessionManager.append(AssistantMessage(response.content))
    
    return AgentResponse(response.content, toolResults)
```

### 6.4 性能目标

| 操作 | 目标延迟 | 优化方案 |
|------|---------|---------|
| 会话读写 | <100ms | RocksDB 本地缓存 |
| 工具执行 | <500ms | 并发处理、超时控制 |
| 上下文组装 | <50ms | 惰性加载、索引 |
| LLM 请求 | <5s | 连接复用、流式响应 |
| 权限检查 | <10ms | 权限缓存、预计算 |

### 6.5 吞吐量目标

- **并发会话**: 1000+ 同时活跃会话
- **工具执行**: 100+ 工具/秒
- **消息处理**: 10000+ msg/秒

---

## 七、质量保障

### 7.1 测试覆盖

```
单元测试: 80% 覆盖率
├── SessionManager (100%)
├── ToolRegistry (95%)
├── ContextManager (90%)
├── AgentExecutor (85%)
└── SecurityPolicy (100%)

集成测试: 60% 覆盖率
├── 端到端 Agent 执行
├── 多工具编排
├── LLM 提供商集成
└── 权限强制执行

性能测试
├── 1000 并发会话
├── 大上下文压缩 (100KB+)
└── 工具执行吞吐量

安全测试
├── 权限绕过尝试
├── 工具隔离验证
└── 注入攻击检测
```

### 7.2 监控指标

```
关键指标 (KPI):
- P50/P95/P99 响应时间
- 工具执行成功率 (%)
- 上下文压缩率 (%)
- 内存使用率 (GB)
- LLM token 消耗 (avg/request)
- 错误率 (%)
```

### 7.3 代码质量

- SonarQube 扫描 (>80% 覆盖、<3% 重复)
- 依赖检查 (无已知漏洞)
- 性能测试 (JMH 基准)

---

## 八、项目结构

```
pi-agent-core-java/
├── pom.xml (聚合 POM)
├── README.md
├── ARCHITECTURE.md
│
├── pi-core-base/                    # 基础模块
│   ├── src/main/java/com/earendil/pi/core/base/
│   │   ├── model/                   # 数据模型
│   │   │   ├── SessionNode.java
│   │   │   ├── ContextEntry.java
│   │   │   ├── ToolCall.java
│   │   │   └── ...
│   │   ├── util/                    # 工具类
│   │   ├── exception/               # 异常
│   │   └── serialization/           # 序列化
│   ├── src/test/java/
│   └── pom.xml
│
├── pi-core-session/                 # 会话管理
│   ├── src/main/java/com/earendil/pi/core/session/
│   │   ├── SessionManager.java
│   │   ├── SessionTree.java
│   │   └── storage/
│   │       ├── SessionStorage.java (接口)
│   │       ├── FileSessionStorage.java
│   │       ├── MongoSessionStorage.java
│   │       └── PostgresSessionStorage.java
│   ├── src/test/java/
│   └── pom.xml
│
├── pi-core-tools/                   # 工具系统
│   ├── src/main/java/com/earendil/pi/core/tools/
│   │   ├── ToolRegistry.java
│   │   ├── ToolExecutor.java (接口)
│   │   ├── ToolCall.java
│   │   ├── loader/
│   │   │   └── ToolLoader.java
│   │   └── annotation/
│   │       └── @Tool
│   ├── src/test/java/
│   └── pom.xml
│
├── pi-core-context/                 # 上下文管理
│   ├── src/main/java/com/earendil/pi/core/context/
│   │   ├── ContextManager.java
│   │   ├── ContextLayer.java
│   │   ├── compressor/
│   │   │   ├── ContextCompressor.java (接口)
│   │   │   └── AdaptiveContextCompressor.java
│   │   └── tokenizer/
│   │       └── TokenCounter.java
│   ├── src/test/java/
│   └── pom.xml
│
├── pi-core-llm/                     # LLM 集成
│   ├── src/main/java/com/earendil/pi/core/llm/
│   │   ├── LLMProvider.java (接口)
│   │   ├── LLMConfig.java
│   │   ├── ChatCompletionResponse.java
│   │   ├── provider/
│   │   │   ├── openai/
│   │   │   │   ├── OpenAIProvider.java
│   │   │   │   ├── OpenAIClient.java
│   │   │   │   └── OpenAIConfig.java
│   │   │   ├── anthropic/
│   │   │   │   └── AnthropicProvider.java
│   │   │   └── custom/
│   │   │       └── CustomProvider.java
│   │   └── format/
│   │       └── MessageFormatter.java
│   ├── src/test/java/
│   └── pom.xml
│
├── pi-core-runtime/                 # 运行时引擎
│   ├── src/main/java/com/earendil/pi/core/runtime/
│   │   ├── AgentExecutor.java
│   │   ├── AgentConfig.java
│   │   ├── AgentResponse.java
│   │   ├── ExecutionContext.java
│   │   └── orchestration/
│   │       ├── ToolSelector.java
│   │       └── ContextAssembler.java
│   ├── src/test/java/
│   └── pom.xml
│
├── pi-core-security/                # 安全模块
│   ├── src/main/java/com/earendil/pi/core/security/
│   │   ├── PermissionContext.java
│   │   ├── SecurityPolicy.java (接口)
│   │   ├── policy/
│   │   │   ├── RoleBasedSecurityPolicy.java
│   │   │   └── AttributeBasedSecurityPolicy.java
│   │   ├── isolation/
│   │   │   ├── IsolationExecutor.java (接口)
│   │   │   ├── DockerIsolationExecutor.java
│   │   │   └── ProcessIsolationExecutor.java
│   │   └── audit/
│   │       └── AuditLogger.java
│   ├── src/test/java/
│   └── pom.xml
│
├── pi-core-spring-boot/             # Spring Boot 集成
│   ├── src/main/java/com/earendil/pi/spring/
│   │   ├── autoconfigure/
│   │   │   └── PiAgentAutoConfiguration.java
│   │   ├── rest/
│   │   │   ├── AgentController.java
│   │   │   └── SessionController.java
│   │   ├── websocket/
│   │   │   └── AgentWebSocketHandler.java
│   │   └── config/
│   │       └── PiAgentProperties.java
│   ├── src/test/java/
│   └── pom.xml
│
└── pi-core-examples/                # 示例与演示
    ├── coding-assistant/
    │   ├── CodingAssistantExample.java
    │   ├── tools/
    │   │   ├── FileReadTool.java
    │   │   ├── FileWriteTool.java
    │   │   └── CodeAnalyzerTool.java
    │   └── pom.xml
    │
    ├── data-analyzer/
    │   ├── DataAnalyzerExample.java
    │   ├── tools/
    │   │   ├── CSVLoaderTool.java
    │   │   ├── StatisticsTool.java
    │   │   └── ChartGeneratorTool.java
    │   └── pom.xml
    │
    └── custom-domain/
        ├── CustomDomainExample.java
        └── pom.xml
```

---

## 九、技术决策与权衡

### 9.1 同步 vs 异步

**决策**: 100% 异步 (Project Reactor)

**理由**:
- ✅ 支持高并发 (虚拟线程友好)
- ✅ 非阻塞 I/O
- ✅ 易于组合与流式处理
- ❌ 学习曲线陡峭

### 9.2 持久化方案

**决策**: 三层策略
1. **默认**: RocksDB (本地高性能)
2. **云原生**: MongoDB (分布式、灵活)
3. **企业**: PostgreSQL (事务、ACID)

### 9.3 隔离安全

**决策**: 分级方案
- 🟢 可信工具: 无隔离
- 🟡 第三方工具: 进程隔离
- 🔴 不信任代码: Docker 隔离

---

## 十、风险与缓解

| 风险 | 影响 | 缓解方案 |
|------|------|---------|
| 依赖库漏洞 | 高 | 依赖扫描 + 定期更新 |
| LLM API 变化 | 中 | 适配层 + 版本隔离 |
| 性能瓶颈 | 中 | 早期基准测试 + 优化 |
| 权限绕过 | 高 | 安全审计 + 渗透测试 |
| 上下文泄露 | 高 | 加密存储 + 访问控制 |

---

## 十一、成功指标

✅ **功能完整性**: 100% 覆盖 TypeScript 版本

✅ **性能**: Agent 响应 < 10s (包括 LLM)

✅ **可靠性**: 99.5% 任务成功率

✅ **扩展性**: 支持 100+ 工具，1000+ 并发会话

✅ **生态**: GitHub 500+ Star，完善文档

✅ **质量**: 代码覆盖率 >85%，无高风险漏洞

