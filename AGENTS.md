# AGENTS.md

Pi Agent Core Java 是最小 Agent 循环（Think -> Action -> Tool -> Observation）的 JDK 8 兼容 Maven 多模块实现。

## 始终适用

- 构建系统：Maven 3.5+；本仓库有意不提供 Maven wrapper。
- 编译/类型检查：`mvn -q -DskipTests compile`
- 测试：`mvn -q test`
- 完整兼容性验证：`mvn -q verify`
- Java 语言特性与 Java 运行时 API 都必须保持 JDK 8 兼容。不要使用 Java 8 之后引入的 API（例如 `CompletableFuture.orTimeout`、`List.of`、`record`、`var`）。
- 保持运行时顺序 `LLM -> tool calls -> tool results -> LLM`；工具失败是返回给模型的 observation，而不是未捕获的控制流异常。
- 保持模块依赖无环，且依赖方向指向更低层的模块。
- Research in English, respond in Chinese.（资料检索与代码调研用英文，对用户的回复用中文。）

## 仅在相关时阅读

- [架构与模块边界](docs/agents/architecture.md)
- [构建、测试与 JDK 8 验证](docs/agents/build-and-test.md)
- [Agent 运行时循环与取消](docs/agents/runtime-loop.md)
- [工具契约、超时与错误](docs/agents/tools.md)
- [会话与上下文组装](docs/agents/sessions-and-context.md)
- [Java 8 编码规范](docs/agents/code-style.md)
- [文档维护](docs/agents/documentation.md)
