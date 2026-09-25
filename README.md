# Pi Agent Core Java — JDK 8

A JDK 8-compatible Java implementation of the Pi-style agent runtime core.

The implemented runtime is intentionally centered on the minimal closed loop:

`User -> Think (LLM) -> Action (tool call) -> Tool -> Observation -> Think -> Final Answer`

## Modules

| Module | Responsibility |
|---|---|
| `pi-core-common` | JDK 8 utility primitives and async timeout helpers |
| `pi-session-manager` | Tree-structured in-memory sessions and active-path navigation |
| `pi-tool-system` | Tool definitions, registry, execution and JDK 8 timeouts |
| `pi-llm-adapter` | Provider-neutral LLM request/response abstractions |
| `pi-context-manager` | Active-path context assembly and token-budget trimming |
| `pi-security` | Tool authorization policy hooks |
| `pi-agent-runtime` | Think/Action/Tool/Observation loop |
| `pi-api` | Small SDK facade and runnable demo |

## Build

Requirements: JDK 8+ and Maven 3.5+.

```bash
mvn test
mvn verify
```

`mvn verify` also runs Animal Sniffer against the Java 8 API signature so code using APIs introduced after Java 8 is rejected.

## Demo

After building:

```bash
java -cp "pi-core-common/target/classes:pi-session-manager/target/classes:pi-tool-system/target/classes:pi-llm-adapter/target/classes:pi-context-manager/target/classes:pi-security/target/classes:pi-agent-runtime/target/classes:pi-api/target/classes" com.earendil.pi.api.DemoMain
```

The demo uses a deterministic in-process LLM client and an `echo` tool, so no API key or network access is required.

## Design references

The original design material remains under [`docs/`](docs/). Agent-specific development instructions use progressive disclosure from [`AGENTS.md`](AGENTS.md).
