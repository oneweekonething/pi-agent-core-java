# Build, Tests, and JDK 8 Verification

**Use this document when:** changing build files, dependencies, language features, tests, or CI.

## Required commands

```bash
mvn -q -DskipTests compile
mvn -q test
mvn -q verify
```

Focused runtime tests:

```bash
mvn -q -pl pi-agent-runtime -am test
```

`verify` is the compatibility gate. The root POM uses Animal Sniffer with the Java 8 signature to catch accidental calls to APIs newer than Java 8 even when Maven itself runs on a newer JDK.

Do not replace this with source/target checks alone: `-source 8 -target 8` restricts syntax and bytecode but does not by itself prevent linking to newer JDK APIs.

The repository has no Maven wrapper. Do not document `./mvnw` unless the wrapper is actually added.
