# Java 8 编码规范

**何时使用本文档：** 编写或评审 Java 源码时。

允许的基线：Java 8 语言特性与运行时 API。

避免：

- `record`、sealed 类/接口、文本块、switch 表达式、模式匹配。
- `var`。
- `List.of`、`Map.of`、`Set.of`、`Optional.isEmpty`。
- `CompletableFuture.orTimeout` / `completeOnTimeout`。
- Java 8 之后加入 JDK 的 API。

优先：

- 小型不可变值对象，配显式构造器/getter。
- 运行时代码用 `CompletableFuture` 组合，而不是阻塞式 `get()`。
- 用 `java.util.concurrent` 原语做调度与取消。
- 跨模块边界的集合做防御性拷贝。
- 在 agent/工具边界给出显式的 error observation。

除非有具体收益、值得把注解处理纳入构建，否则不要在核心运行时类中引入 Lombok。
