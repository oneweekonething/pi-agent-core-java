# Java 17 编码规范

**何时使用本文档：** 编写或评审 Java 源码时。

允许的基线：Java 17 语言特性与运行时 API（由 `--release 17` 门禁强制）。

优先（在 jdk8 分支写法的基础上，本分支采用更现代的等价形式）：

- switch 表达式（枚举分支穷尽检查由编译器保证，不再需要 `default` 兜底）。
- `instanceof` 模式匹配替代「先判断再强转」。
- lambda 替代单方法匿名内部类。
- `List.of` / `Map.of` / `Set.of` 构造不可变常量集合（注意它们拒绝 null 元素；对外部传入集合仍做防御性拷贝）。
- 小型不可变值对象保持显式构造器/getter：公共 API 的形状与 `jdk8` 分支一致（getter 风格），因此**不**改成 `record`（record 的访问器命名不同，会破坏两分支的 API 对等）。
- 运行时代码用 `CompletableFuture` 组合，而不是阻塞式 `get()`。
- 用 `java.util.concurrent` 原语做调度与取消。
- 跨模块边界的集合做防御性拷贝。
- 在 agent/工具边界给出显式的 error observation。

仍避免：

- `CompletableFuture.orTimeout` / `completeOnTimeout`：超时统一走 `Asyncs.withTimeout`（专用命名调度器、错误解包语义与 jdk8 分支一致；orTimeout 使用 JVM 全局共享调度器且会二次包装异常）。
- JDK 17 之后加入 JDK 的 API（如虚拟线程）。
- 核心运行时类引入 Lombok。
