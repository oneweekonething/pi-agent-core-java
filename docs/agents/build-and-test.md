# 构建、测试与 JDK 17 验证

**何时使用本文档：** 修改构建文件、依赖、语言特性、测试或 CI 时。

## 必需命令

```bash
mvn -q -DskipTests compile
mvn -q test
mvn -q verify
```

只跑运行时相关测试：

```bash
mvn -q test -Dtest=AgentRuntimeTest
```

`verify` 是兼容性门禁。根 POM 用 `maven.compiler.release=17`（即 javac `--release 17`）编译：即使 Maven 本身运行在更新的 JDK 上，也只有 JDK 17 及之前的语言特性与 API 可用，意外调用更新 API 会在编译期失败。

CI：push 到本分支（`jdk17`）或 PR 到 `main`（head 为 `jdk17`）时，`.github/workflows/jdk17.yml` 在 Temurin 17 上运行 `mvn -B -q verify`。`jdk8.yml` 只处理 head 为 `jdk8` 的 PR 与 jdk8 分支 push，两个 workflow 不会在同一 PR 上交叉运行。

不要退回只用 source/target：`-source 17 -target 17` 只限制语法和字节码，本身并不能阻止链接到更新版本的 JDK API；`--release` 同时约束平台 API。

本仓库没有 Maven wrapper。除非真的加入了 wrapper，否则不要在文档中写 `./mvnw`。
