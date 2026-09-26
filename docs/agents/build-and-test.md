# 构建、测试与 JDK 8 验证

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

`verify` 是兼容性门禁。根 POM 使用 Animal Sniffer 的 Java 8 签名，即使 Maven 本身运行在更新的 JDK 上，也能捕获对 Java 8 之后 API 的意外调用。

不要只用 source/target 检查来替代它：`-source 8 -target 8` 只限制语法和字节码，本身并不能阻止链接到更新版本的 JDK API。

本仓库没有 Maven wrapper。除非真的加入了 wrapper，否则不要在文档中写 `./mvnw`。
