# Agent Runtime Loop and Cancellation

**Use this document when:** editing `pi-agent-runtime`, changing turn semantics, cancellation, or stop conditions.

The invariant is:

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

Rules:

- Never skip persisting the assistant tool-call message before its tool results.
- Tool calls in one model response execute sequentially by default. This preserves deterministic observation order.
- A denied, unknown, timed-out, or failed tool still produces a `TOOL_RESULT` node with `error=true`.
- Do not automatically retry arbitrary tools; retries can duplicate side effects. Add retry policy only when tool idempotency is explicit.
- `maxTurns` limits model turns, not individual tool calls.
- Cancellation is cooperative. Stop before starting the next model/tool operation; do not use `Thread.stop()` or similar unsafe interruption.
