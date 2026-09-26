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
- Only one run may be active per session id. A second concurrent `run` on the same session fails fast with `IllegalStateException` instead of interleaving appends into the session tree.
- Cancellation is cooperative. Stop before starting the next model/tool operation; do not use `Thread.stop()` or similar unsafe interruption.
- Tool calls skipped because of cancellation still get `TOOL_RESULT` nodes with `error=true` (`tool call cancelled before execution`), so a persisted assistant tool-call message is never left without matching tool results.
- Each model call receives a cancellation token linked to the run token and is raced against `Config.llmTimeoutMillis`; the timeout cancels the token so clients that honor it (like `Llm.RetryClient`) stop scheduling new attempts, and a model timeout fails the run.
