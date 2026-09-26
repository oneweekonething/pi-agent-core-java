# Pi Agent Core Java — JDK 8

Pi 风格 Agent 运行时核心的 JDK 8 兼容 Java 实现。

已实现的运行时刻意聚焦于最小闭环：

`User -> Think (LLM) -> Action (tool call) -> Tool -> Observation -> Think -> Final Answer`

## 包结构

单 Maven 模块、单发布物 `pi-agent-core.jar`，按领域划分源码包（模块边界已合并，package 边界保留）：

| Package | 职责 |
|---|---|
| `com.earendil.pi` | `PiAgent` SDK 门面与可运行 Demo |
| `com.earendil.pi.agent` | Think/Action/Tool/Observation 循环（`AgentRuntime`） |
| `com.earendil.pi.llm` | 供应商中立的 LLM 请求/响应抽象与重试装饰器 |
| `com.earendil.pi.tool` | 工具定义、注册表、参数校验、执行与 JDK 8 超时 |
| `com.earendil.pi.session` | 树状内存会话与活跃路径导航 |
| `com.earendil.pi.context` | 活跃路径的上下文组装与 token 预算裁剪 |
| `com.earendil.pi.security` | 工具授权策略钩子 |
| `com.earendil.pi.internal` | 运行时内部基础类（`Asyncs`、`Cancellation`），不构成对外 API |

依赖方向（package 级）：`internal` → `session`/`tool` → `llm` → `context`、`security` → `agent` → `PiAgent`，由 AGENTS.md 约定维护。

## 构建

环境要求：JDK 8+ 与 Maven 3.5+。

```bash
mvn test
mvn verify
```

`mvn verify` 还会以 Java 8 API 签名运行 Animal Sniffer，拒绝使用 Java 8 之后引入 API 的代码。

## Demo

构建完成后：

```bash
java -cp target/classes com.earendil.pi.PiAgent
```

Demo 使用确定性的进程内 LLM 客户端与 `echo` 工具，无需 API key，也不需要网络访问。

## 设计参考资料

原始设计材料保留在 [`docs/`](docs/) 下。面向 Agent 的开发规范从 [`AGENTS.md`](AGENTS.md) 开始按渐进披露方式组织。
