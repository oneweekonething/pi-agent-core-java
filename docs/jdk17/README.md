# 本目录是早期设计资料，不作为 jdk17 分支的实现规范

`jdk17` 分支（见仓库根 `AGENTS.md`）的验收标准是：

> **jdk8 runtime 语义 100% 保持，只现代化 Java 写法。**

语义基线是 `jdk8` 分支的当前源码与测试，而不是本目录的设计稿。设计稿早于实现，其中多处与本分支实际语义冲突，包括：

- 设计稿第 149 行「并发执行工具」——本分支与 jdk8 一致，同一 LLM response 内的 tool calls **顺序执行**（`AgentRuntime.executeSequential`），保证 observation 顺序确定。
- 设计稿「工具执行完后保存 AssistantMessage / `agentExecute("Continue")` 递归继续」——本分支先持久化 assistant + tool calls 再执行任何 tool，随后**直接带 tool observations 进入下一轮 LLM**，不伪造 user message。
- 设计稿 `maxToolCalls` 控制循环——本分支与 jdk8 一致，`maxTurns` 指 **LLM rounds**。
- 设计稿以 Project Reactor 为核心依赖、承诺「工具重试」——本分支保持 `CompletableFuture` runtime、单 Maven module；**工具默认不重试**（重试仅存在于 LLM 客户端装饰器 `Llm.RetryClient` 且按策略启用）。
- 设计稿把「虚拟线程」列为 JDK 17 能力——虚拟线程是 **Java 21** 正式功能；本分支由 `--release 17` 门禁强制，无法使用。

冲突之处一律以 `jdk8` 分支语义、`AGENTS.md` 与 `mvn verify` 通过的当前源码为准。
