# Pi Agent Core Java — JDK 8

Pi 风格 Agent 运行时核心的 JDK 8 兼容 Java 实现。

已实现的运行时刻意聚焦于最小闭环：

`User -> Think (LLM) -> Action (tool call) -> Tool -> Observation -> Think -> Final Answer`

## 模块

| 模块 | 职责 |
|---|---|
| `pi-core-common` | JDK 8 工具原语与异步超时竞速辅助 |
| `pi-session-manager` | 树状内存会话与活跃路径导航 |
| `pi-tool-system` | 工具定义、注册表、执行与 JDK 8 超时 |
| `pi-llm-adapter` | 供应商中立的 LLM 请求/响应抽象 |
| `pi-context-manager` | 活跃路径的上下文组装与 token 预算裁剪 |
| `pi-security` | 工具授权策略钩子 |
| `pi-agent-runtime` | Think/Action/Tool/Observation 循环 |
| `pi-api` | 轻量 SDK 门面与可运行 Demo |

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
java -cp "pi-core-common/target/classes:pi-session-manager/target/classes:pi-tool-system/target/classes:pi-llm-adapter/target/classes:pi-context-manager/target/classes:pi-security/target/classes:pi-agent-runtime/target/classes:pi-api/target/classes" com.earendil.pi.api.PiAgent
```

Demo 使用确定性的进程内 LLM 客户端与 `echo` 工具，无需 API key，也不需要网络访问。

## 设计参考资料

原始设计材料保留在 [`docs/`](docs/) 下。面向 Agent 的开发规范从 [`AGENTS.md`](AGENTS.md) 开始按渐进披露方式组织。
