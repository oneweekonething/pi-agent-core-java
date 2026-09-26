# Agent 运行时循环与取消

**何时使用本文档：** 修改 `pi-agent-runtime`、调整轮次语义、取消机制或停止条件时。

不变式是：

```text
user message
  -> build context
  -> LLM response
  -> persist assistant text + structured tool calls
  -> execute tool calls sequentially
  -> persist each tool result as an observation
  -> build the next LLM request
  -> final answer / max turns / cancellation
```

规则：

- 持久化 assistant 的 tool-call 消息必须先于它的工具结果，不得跳过。
- 同一个模型响应里的 tool calls 默认顺序执行。这保留了 observation 顺序的确定性。
- 被拒绝、未知、超时或失败的工具仍会产生 `error=true` 的 `TOOL_RESULT` 节点。
- 不要自动重试任意工具；重试可能复制副作用。只有工具幂等性明确时才添加重试策略。
- `maxTurns` 限制的是模型轮次，不是单个工具调用。
- 同一个 session id 同时只允许一个 run 处于活跃状态。对同一 session 的第二次并发 `run` 会以 `IllegalStateException` 快速失败，而不是把交错追加写进会话树。
- 取消是协作式的。在开始下一个模型/工具操作前停止；不要使用 `Thread.stop()` 或类似的不安全中断。
- 因取消而被跳过的 tool call 仍会得到 `error=true` 的 `TOOL_RESULT` 节点（内容为 `tool call cancelled before execution`），因此已持久化的 assistant tool-call 消息不会缺少与之配对的工具结果。
- 每次模型调用都会收到一个链接到 run token 的取消 token，并与 `Config.llmTimeoutMillis` 竞速；超时会取消该 token，使遵循它的客户端（如 `Llm.RetryClient`）停止调度新的尝试，模型超时则使 run 失败。
