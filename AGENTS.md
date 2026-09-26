# AGENTS.md

Pi Agent Core Java is a JDK 8-compatible, Maven multi-module implementation of the minimal agent loop: Think -> Action -> Tool -> Observation.

## Always applicable

- Build system: Maven 3.5+; this repository intentionally has no Maven wrapper.
- Compile/type check: `mvn -q -DskipTests compile`
- Tests: `mvn -q test`
- Full compatibility verification: `mvn -q verify`
- Keep both Java language features **and Java runtime APIs** compatible with JDK 8. Do not use APIs introduced after Java 8 (for example `CompletableFuture.orTimeout`, `List.of`, `record`, `var`).
- Preserve the runtime ordering `LLM -> tool calls -> tool results -> LLM`; tool failures are observations returned to the model, not uncaught control-flow exceptions.
- Keep module dependencies acyclic and dependencies directed toward lower-level modules.
- Research in English, respond in Chinese.

## Read only when relevant

- [Architecture and module boundaries](docs/agents/architecture.md)
- [Build, tests, and JDK 8 verification](docs/agents/build-and-test.md)
- [Agent runtime loop and cancellation](docs/agents/runtime-loop.md)
- [Tool contracts, timeouts, and errors](docs/agents/tools.md)
- [Sessions and context assembly](docs/agents/sessions-and-context.md)
- [Java 8 coding conventions](docs/agents/code-style.md)
- [Documentation maintenance](docs/agents/documentation.md)
