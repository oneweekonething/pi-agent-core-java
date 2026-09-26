# 架构与模块边界

**何时使用本文档：** 新增模块、跨模块移动类，或调整依赖时。

## 依赖方向

```text
pi-core-common
  ├─ pi-session-manager
  └─ pi-tool-system
       └─ pi-llm-adapter
pi-session-manager + pi-tool-system + pi-llm-adapter
       └─ pi-context-manager
pi-tool-system
       └─ pi-security
session + tool + llm + context + security
       └─ pi-agent-runtime
all public runtime pieces
       └─ pi-api
```

规则：

- 更低层的模块不得依赖 `pi-agent-runtime` 或 `pi-api`。
- `pi-session-manager` 存储供应商中立的会话数据，不得依赖 LLM 或工具实现。
- `pi-llm-adapter` 拥有供应商中立的模型请求/响应类型；具体的供应商集成应实现 `Llm.Client`。
- `pi-agent-runtime` 负责模块编排，但不拥有持久化或 HTTP 传输。
- `pi-api` 是面向最终用户的组合边界。
