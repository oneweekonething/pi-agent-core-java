# 工具契约、超时与错误

**何时使用本文档：** 添加工具、修改 `Tools.Registry`、工具 schema 或执行策略时。

一个工具由以下部分组成：

- `Tools.Definition`：稳定的名称、描述、参数。
- `Tools.Tool`：返回 `CompletableFuture<Tools.Execution>` 的异步实现。
- `Tools.Call`：模型选定的 call id、名称、参数。
- `Tools.Result`：归一化的 observation，包含 call id、名称、内容、错误标志与耗时。

超时由 `Asyncs.withTimeout` 用专用 `ScheduledExecutorService` 实现竞速。虽然 JDK 17 已有 `CompletableFuture.orTimeout`，但注册表保持 `Asyncs.withTimeout`：专用命名调度器可控线程生命周期，异常解包语义与 jdk8 分支一致。

错误规则：

- 工具同步抛出的异常转换为 error observation。
- 异常完成的 future 转换为 error observation。
- 未知的工具转换为 error observation。
- 超时转换为 error observation。
- 策略拒绝由运行时转换为 error observation。

取消与结果大小：

- 参数在执行前会依据定义校验（`Tools.Registry.validate`，即 `Tools.Arguments.validate`）：未知工具、缺失的必填参数或参数类型不匹配（`ParameterType`）会成为 error observation，且工具不会被调用。运行时在 `Security.Policy` 评估之前校验，策略只接触已验证的参数。
- 需要轮询取消状态的工具应重写 `execute(arguments, Cancellation)`。注册表传入一个链接到 run 级 token 的 token，并在工具超时时取消它，长时间运行的任务可以据此自行停止。
- 超过注册表 `maxResultChars`（默认 16384）的工具结果内容，在成为 observation 前会被截断并附加 `...[truncated N chars]` 标记。

每个结果都保留原始 `toolCallId`，以便供应商适配器重建结构化的工具历史。
