# Java 8 Coding Conventions

**Use this document when:** writing or reviewing Java source.

Allowed baseline: Java 8 language and runtime APIs.

Avoid:

- `record`, sealed classes/interfaces, text blocks, switch expressions, pattern matching.
- `var`.
- `List.of`, `Map.of`, `Set.of`, `Optional.isEmpty`.
- `CompletableFuture.orTimeout` / `completeOnTimeout`.
- APIs added to the JDK after Java 8.

Prefer:

- Small immutable value objects with explicit constructors/getters.
- `CompletableFuture` composition instead of blocking `get()` in runtime code.
- `java.util.concurrent` primitives for scheduling and cancellation.
- Defensive copies for collections crossing module boundaries.
- Explicit error observations at agent/tool boundaries.

Do not add Lombok to core runtime classes unless there is a concrete payoff that justifies making annotation processing part of the build.
