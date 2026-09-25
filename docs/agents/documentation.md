# Documentation Maintenance

**Use this document when:** changing `AGENTS.md`, design docs, README files, or commands shown to contributors.

Progressive disclosure rules:

- Keep root `AGENTS.md` limited to facts every task needs plus links to topic instructions.
- Put task-specific rules under `docs/agents/`.
- Every topic file must state when it applies.
- Commands documented as required must correspond to files/configuration that exist in the branch.
- Prefer code and executable build configuration over speculative architecture prose when they disagree.

The files under `docs/jdk8/` are design references. Some examples predate the implementation and may contain non-JDK-8 APIs; notably `CompletableFuture.orTimeout` is not JDK 8. Treat `mvn verify` and current source code as the compatibility authority.
