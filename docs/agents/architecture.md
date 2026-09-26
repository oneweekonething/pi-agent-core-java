# 架构与包边界

**何时使用本文档：** 新增 package、跨包移动类，或调整依赖方向时。

工程是单 Maven 模块（`pi-agent-core.jar`）；架构边界由源码 package 加本约定维护，不再用多 Maven module 强制。

## 依赖方向

```text
com.earendil.pi.internal
  ├─ com.earendil.pi.session
  └─ com.earendil.pi.tool
       └─ com.earendil.pi.llm
session + tool + llm
       └─ com.earendil.pi.context
tool
  └─ com.earendil.pi.security
session + tool + llm + context + security
       └─ com.earendil.pi.agent
all public runtime pieces
       └─ com.earendil.pi（PiAgent）
```

规则：

- 根包 `com.earendil.pi` 中的 `CancellationToken` 是公共 API（出现在 `AgentRuntime.run`、`Llm.Client.complete`、`Tools.Tool.execute` 的签名中），任何层都可以使用它。
- 除此之外，更低层的包不得依赖 `com.earendil.pi.agent` 或根包中的 `PiAgent`。包依赖方向由 `ArchitectureTest` 在测试期强制：根包对低层的可见 API 仅限 `CancellationToken`（类级检查），且禁止对 `com.earendil.pi.*` 使用通配 import。
- `com.earendil.pi.session` 存储供应商中立的会话数据，不得依赖 LLM 或工具实现。
- `com.earendil.pi.llm` 拥有供应商中立的模型请求/响应类型；具体的供应商集成应实现 `Llm.Client`。
- `com.earendil.pi.agent` 负责编排，但不拥有持久化或 HTTP 传输。
- 根包 `com.earendil.pi` 中的 `PiAgent` 是面向最终用户的组合边界。
- `com.earendil.pi.internal` 是运行时内部实现，不构成对外 API，随时可变更。
- 未来出现独立部署/独立版本的需求（如 `pi-openai`、`pi-mcp`）时，再拆出 Maven module。

