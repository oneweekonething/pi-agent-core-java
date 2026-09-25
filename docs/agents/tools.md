# Tool Contracts, Timeouts, and Errors

**Use this document when:** adding tools, changing `ToolRegistry`, tool schemas, or execution policy.

A tool consists of:

- `ToolDefinition`: stable name, description, parameters.
- `Tool`: async implementation returning `CompletableFuture<ToolExecution>`.
- `ToolCall`: model-selected call id, name, arguments.
- `ToolResult`: normalized observation containing call id, name, content, error flag, and duration.

JDK 8 has no `CompletableFuture.orTimeout`. The registry implements timeout racing with `ScheduledExecutorService` through `Futures.withTimeout`.

Error rules:

- Synchronous exceptions thrown by a tool are converted to error observations.
- Exceptional futures are converted to error observations.
- Unknown tools are converted to error observations.
- Timeouts are converted to error observations.
- Policy denial is converted by the runtime to an error observation.

Keep the original `toolCallId` in every result so provider adapters can reconstruct structured tool history.
