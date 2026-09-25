# Architecture and Module Boundaries

**Use this document when:** adding a module, moving a class across modules, or changing dependencies.

## Dependency direction

```text
pi-core-common
  ├─ pi-session-manager
  └─ pi-tool-system
       └─ pi-llm-adapter
pi-session-manager + pi-tool-system + pi-llm-adapter
       └─ pi-context-manager
pi-tool-system
       └─ pi-security
session + tool + llm + context + security
       └─ pi-agent-runtime
all public runtime pieces
       └─ pi-api
```

Rules:

- Lower-level modules must not depend on `pi-agent-runtime` or `pi-api`.
- `pi-session-manager` stores provider-neutral session data and must not depend on LLM or tool implementations.
- `pi-llm-adapter` owns provider-neutral model request/response types; concrete provider integrations should implement `LlmClient`.
- `pi-agent-runtime` orchestrates modules but does not own persistence or HTTP transport.
- `pi-api` is the composition boundary for end users.
