# AGENTS.md

Pi Agent Core Java 是最小 Agent 循环（Think -> Action -> Tool -> Observation）的 JDK 17 单模块实现（与 `jdk8` 分支功能与处理逻辑对等）。

## 始终适用

- 构建系统：Maven 3.5+；单模块工程（`pi-agent-core.jar`），本仓库有意不提供 Maven wrapper。
- 编译/类型检查：`mvn -q -DskipTests compile`
- 测试：`mvn -q test`
- 完整兼容性验证：`mvn -q verify`
- Java 语言特性与运行时 API 以 JDK 17 为基线，由 `--release 17` 门禁强制（拒绝 JDK 17 之后的 API）。switch 表达式、`instanceof` 模式匹配、文本块、`List.of`/`Map.of` 等 Java 9-17 特性可用；不使用 JDK 17 之后才引入的 API。
- 本分支与 `jdk8` 分支保持功能与处理逻辑一致：改动运行时行为时以 jdk8 分支为准绳，测试集与 jdk8 保持一致（测试通过即是对等性的证据）。
- 保持运行时顺序 `LLM -> tool calls -> tool results -> LLM`；工具失败是返回给模型的 observation，而不是未捕获的控制流异常。
- 保持 package 依赖无环且方向指向更低层的包：`internal` → `session`/`tool` → `llm` → `context`、`security` → `agent` → `PiAgent`（根包）。根包的 `CancellationToken` 是公共 API，可供各层使用；除此之外低层包不得引用 `PiAgent` 或 `agent`。架构边界由 package、`ArchitectureTest` 与本文档约定维护，不再使用多 Maven module 强制。
- Research in English, respond in Chinese.（资料检索与代码调研用英文，对用户的回复用中文。）

## 仅在相关时阅读

- [架构与模块边界](docs/agents/architecture.md)
- [构建、测试与 JDK 17 验证](docs/agents/build-and-test.md)
- [Agent 运行时循环与取消](docs/agents/runtime-loop.md)
- [工具契约、超时与错误](docs/agents/tools.md)
- [会话与上下文组装](docs/agents/sessions-and-context.md)
- [Java 17 编码规范](docs/agents/code-style.md)
- [文档维护](docs/agents/documentation.md)
