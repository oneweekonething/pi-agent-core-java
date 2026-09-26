# Sessions and Context Assembly

**Use this document when:** editing the session tree, branching/rewind behavior, message conversion, or context trimming.

`SessionTree` retains all nodes and keeps one `activeTipId`. `activePath()` follows parents from the active tip to the root and is the only history sent to the model.

Rules:

- Branching creates a new user node whose parent is the selected historical node.
- Rewind changes only the active tip; it does not delete history.
- Assistant nodes store structured tool-call snapshots.
- Tool-result nodes store `toolCallId`, tool name, error status, and observation content.
- Context assembly must preserve assistant-tool-call -> tool-result ordering.
- Budget trimming removes oldest messages first and always keeps at least the newest message. Estimates include tool-call arguments and tool parameter schemas, not just message text.
- `Manager.getOrCreate` deduplicates in-flight creations per session id through a shared future, so concurrent callers on an async repository get the same tree instead of two creations. Cross-manager or cross-process duplicate creation still needs repository-level atomicity.
- Token estimation is intentionally heuristic in core. Provider-specific tokenizers belong in adapter implementations.
