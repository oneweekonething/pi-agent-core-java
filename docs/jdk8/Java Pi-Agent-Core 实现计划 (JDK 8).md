# Java Pi-Agent-Core 实现计划方案（JDK 8 版本）

## 📋 技术栈选型

### 核心依赖
- **JDK 版本**: 8+ （Lambda、Stream、CompletableFuture）
- **构建工具**: Maven 3.5+
- **异步编程**: CompletableFuture + 线程池（传统线程模型）
  - 或使用 RxJava 2.x 作为响应式编程库
  - 使用 ForkJoinPool 进行并行处理
- **序列化**: Jackson 2.9.x - 2.13.x（JSON 处理，兼容 JDK 8）
- **数据存储**:
  - RocksDB 5.x - 6.29.x（JDK 8 兼容版本）
  - MongoDB Java Driver 3.12.x
  - PostgreSQL JDBC 42.2.x
- **Web 框架**: Spring Boot 2.7.x（最后一个支持 JDK 8 的版本）
- **日志**: SLF4J + Logback 1.2.x
- **测试**: JUnit 4.13 或 JUnit 5.8.x（兼容模式）
- **并发**: java.util.concurrent（传统线程池 + CompletableFuture）
- **缓存**: Caffeine 2.9.x（高性能本地缓存）
- **工具库**: Guava 28.x - 31.x
- **HTTP 客户端**: OkHttp3 3.14.x
- **代码简化**: Lombok 1.18.x

---

## 🏗️ 架构分层

```
Level 5 (接口层)
├─ REST API 层（Spring Boot 2.7 + 线程池）
├─ CLI 层（Picocli）
├─ WebSocket 层（实时通信，需要额外库）
└─ SDK 层（Java 客户端库）

Level 4 (业务编排层)
├─ Agent 工作流管理
├─ 工具编排和链式调用
└─ 会话生命周期管理

Level 3 (核心运行时)
├─ Agent 执行引擎（基于 CompletableFuture）
├─ 会话树管理（JSONL 格式解析）
├─ 工具系统（动态加载机制）
├─ LLM 适配层（多供应商支持）
└─ 上下文管理器（自适应压缩）

Level 2 (基础服务层)
├─ 存储引擎（RocksDB/MongoDB/PostgreSQL）
├─ 缓存层（Caffeine 缓存）
├─ 线程池管理（ForkJoinPool + ExecutorService）
└─ 安全策略执行（权限检查 + 审计）

Level 1 (数据持久化)
├─ 文件存储（Nio.2）
├─ 数据库存储（JDBC）
└─ 序列化（Jackson JSONL）
```

---

## 📅 开发计划（20 周）

### Phase 1: 基础框架搭建（第 1-3 周）
- [ ] Maven 多模块项目结构设计
- [ ] Spring Boot 2.7 配置（JDK 8 兼容）
- [ ] CompletableFuture 异步编程框架搭建
- [ ] 线程池管理策略制定
- [ ] Jackson 序列化配置
- [ ] 日志和监控框架集成
- [ ] 开发环境配置（Docker/本地开发）

**交付物**: 可运行的空白项目框架，异步基础设施完善

### Phase 2: 核心数据模型与会话管理（第 4-6 周）
- [ ] 会话节点（SessionNode）POJO 设计（使用 Lombok 简化）
- [ ] 会话树（SessionTree）JSONL 持久化实现
- [ ] 树操作 API（branch、merge、traverse）
- [ ] 元数据管理（timestamps、cost tracking）
- [ ] 会话存储 DAO（支持 RocksDB/MongoDB/PostgreSQL）
- [ ] 会话查询服务（条件查询、分页）
- [ ] CompletableFuture 异步存储操作

**交付物**: SessionManager 完整实现，通过集成测试，支持异步操作

### Phase 3: 工具系统与动态加载（第 7-9 周）
- [ ] Tool 接口定义和 POJO 设计（兼容 JDK 8 泛型）
- [ ] ToolRegistry 注册中心实现
- [ ] URLClassLoader 动态加载机制（JDK 8 标准方式）
- [ ] 工具生命周期管理
- [ ] 工具执行框架（支持超时、重试、降级）
  - 使用 ScheduledExecutorService 处理超时
  - CompletableFuture 处理异步执行和组合
- [ ] 工具输出规范化处理

**交付物**: ToolExecutor 框架可用，支持 10+ 内置工具，异步执行

### Phase 4: LLM 适配与集成（第 10-12 周）
- [ ] LLM 供应商接口定义（OpenAI、Anthropic、Google）
- [ ] HTTP 客户端库选择（OkHttp3 或 HttpClient 4.x）
- [ ] 消息格式转换（多供应商统一格式）
- [ ] 流式响应处理（使用 SSE 库或手动解析）
- [ ] CompletableFuture 异步调用
- [ ] 重试和限流策略（Guava RateLimiter）
- [ ] Token 统计和成本计算
- [ ] Mock LLM 用于测试

**交付物**: LLMAdapter 支持 3 个主要供应商，异步非阻塞

### Phase 5: 上下文管理与压缩（第 13-15 周）
- [ ] 上下文栈（6 层优先级）实现
- [ ] 自适应上下文压缩算法（支持摘要策略）
- [ ] 消息池化和重用机制（对象池模式）
- [ ] 上下文预算管理
- [ ] 压缩效果监控和指标（计数器模式）
- [ ] 长对话支持测试
- [ ] JSONL 流式读写优化

**交付物**: ContextManager 完成，支持 100K+ tokens 会话

### Phase 6: Agent 执行引擎（第 16-18 周）
- [ ] 执行循环主体实现（基于 CompletableFuture 链）
- [ ] 工具调用分发机制
- [ ] 消息状态机管理（Enum + State Pattern）
- [ ] 流式输出处理（WebSocket 或 Server-Sent Events）
- [ ] 异常处理和回滚
- [ ] 性能监控和日志（Metrics 库）
- [ ] 线程池配置优化

**交付物**: AgentExecutor 完全可用，支持并发运行（线程池限制）

### Phase 7: 安全、API 与测试（第 19-20 周）
- [ ] 权限控制模型实现（RBAC）
- [ ] 隔离执行器（Docker/Process Sandbox）
- [ ] 审计日志完整记录
- [ ] REST API 设计与实现（Spring Boot 2.7）
- [ ] CLI 工具开发（Picocli）
- [ ] 完整单元测试（75%+ 覆盖率）
- [ ] 性能基准测试
- [ ] JDK 8 兼容性验证
- [ ] 生产环境部署文档

**交付物**: 完整可部署的 Agent Core 系统，带文档，JDK 8 认证

---

## 📊 性能目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 吞吐量 | 100-200 req/s | 传统线程池限制 |
| 会话 I/O 延迟 | <150ms | RocksDB 缓存命中 |
| 工具执行延迟 | <500ms | 平均响应时间 |
| 并发连接数 | 200-300 | 线程池上限 |
| 内存占用 | <512MB | 单实例 50 活跃会话 |
| GC 停顿 | <100ms | G1GC 或 CMS |

---

## 🔧 关键技术实现细节

### JDK 8 异步编程方案

**方案 A: CompletableFuture（推荐）**
```java
// 原生 JDK 8 支持，无额外依赖
CompletableFuture.supplyAsync(() -> callLLM())
  .thenCompose(response -> executeTool(response))
  .exceptionally(ex -> handleError(ex))
  .thenAccept(result -> updateSession(result));
```

优点：
- 无外部依赖
- 性能稳定
- 天然支持异步链式调用
- 易于测试（可以立即返回 CompletableFuture）

缺点：
- 需要显式异常处理
- 代码略显复杂
- 堆栈跟踪可读性差

**方案 B: RxJava 2.x（可选）**
```java
Observable.fromCallable(() -> callLLM())
  .flatMap(response -> executeTool(response))
  .subscribe(result -> updateSession(result));
```

优点：
- 强大的操作符库
- 更灵活的异步组合
- 更好的错误处理

缺点：
- 增加依赖
- 学习曲线陡
- 额外的运行时开销

→ **选择**: CompletableFuture（无依赖负担）

### 线程模型设计

**线程池配置** (JDK 8 标准方案):
```java
// 核心执行器
ExecutorService executor = new ThreadPoolExecutor(
  20,                          // corePoolSize
  100,                         // maxPoolSize
  60, TimeUnit.SECONDS,        // keepAliveTime
  new LinkedBlockingQueue<>(500)  // 队列
);

// 用于 CompletableFuture
ForkJoinPool.commonPool()  // 默认使用
// 或自定义：new ForkJoinPool(16)
```

**性能考虑**:
- 每个线程占用 ~1MB 内存
- 推荐 20-100 线程范围
- 监控线程池负载，动态调整
- 使用 BlockingQueue 防止任务堆积

### 存储方案对比

| 方案 | RocksDB | MongoDB | PostgreSQL |
|------|---------|---------|------------|
| 本地开发 | ✅ 最佳 | 需要容器 | 需要容器 |
| JDK 8 兼容 | ✅ 5.x+ | ✅ 3.12+ | ✅ 42.2+ |
| 事务支持 | ❌ | ⚠️ 单文档 | ✅ ACID |
| 查询灵活性 | ⚠️ KV | ✅ JSON | ✅ SQL |
| 集群部署 | ❌ | ✅ | ✅ |
| 性能 | ✅ 最快 | ✅ | ⚠️ |

→ **推荐**: RocksDB (本地开发) + PostgreSQL (生产部署)

### 缓存策略

```
┌─────────────────────────────────────┐
│  L1: 内存缓存（Caffeine）           │  ← 70-80% 命中率
│  默认 1000 entries，10min 过期      │
├─────────────────────────────────────┤
│  L2: RocksDB 本地存储               │  ← 15-20% 命中率
│  毫秒级访问，TB 级容量              │
├─────────────────────────────────────┤
│  L3: 数据库（PostgreSQL/MongoDB）   │  ← <5% 命中率
│  持久化备份，慢速访问               │
└─────────────────────────────────────┘
```

**Caffeine 配置示例**:
```java
Cache<String, SessionTree> cache = Caffeine.newBuilder()
  .maximumSize(1000)
  .expireAfterWrite(10, TimeUnit.MINUTES)
  .build();
```

### 依赖树核心库

```
pi-agent-core (root)
├─ pi-core-common           // 公共工具、异常、常量
├─ pi-session-manager       // 会话存储和树操作
├─ pi-tool-system           // 工具注册和执行
├─ pi-llm-adapter           // LLM 多供应商适配
├─ pi-context-manager       // 上下文和压缩
├─ pi-agent-runtime         // Agent 执行引擎（CompletableFuture 异步）
├─ pi-security              // 权限和隔离
└─ pi-api                   // REST/CLI/SDK 接口

外部依赖版本 (JDK 8 兼容):
├─ spring-boot: 2.7.x
├─ spring-framework: 5.3.x
├─ jackson: 2.9.x - 2.13.x
├─ rocksdb: 5.18.x - 6.29.x
├─ caffeine: 2.9.x (缓存)
├─ guava: 28.x - 31.x (工具库)
├─ okhttp3: 3.14.x (HTTP 客户端)
└─ lombok: 1.18.x (代码简化)
```

### 部署考虑

1. **本地开发**: 单 JVM 进程 + RocksDB + SQLite/H2 数据库
2. **容器部署**: Docker + PostgreSQL + Caffeine 缓存
3. **分布式**: Kubernetes + 共享存储（PersistentVolume）
4. **监控**: Prometheus + Grafana（线程池指标、GC 监控）
5. **备注**: JDK 8 容器需要显式配置 JVM 参数

---

## 🎯 关键优化策略

### 异步优先

- 所有 I/O 操作使用 CompletableFuture
- 避免阻塞调用（除非有明确的阻塞语义）
- 正确配置线程池大小

### 内存效率

- 使用对象池减少 GC 压力
- JSONL 流式读写（避免一次性加载）
- 消息池化和重用

### 缓存优化

- Caffeine L1 缓存热数据
- RocksDB L2 本地高速存储
- 分层缓存策略明确

### 监控与诊断

- 线程池监控（活跃线程、队列长度）
- 缓存命中率统计
- GC 日志分析
- 响应时间分布

---

## ✅ 质量保障

| 方面 | 要求 |
|------|------|
| 单元测试覆盖率 | 75%+ |
| 集成测试 | 关键工作流全覆盖 |
| 压力测试 | 200+ 并发稳定运行 |
| JDK 8 兼容性 | 每个 Phase 验证 |
| 线程安全测试 | 并发工具类完整测试 |
| 内存泄漏检测 | JProfiler/YourKit 验证 |

---

## 🚀 快速启动指南

### 1. 项目初始化
```bash
mvn archetype:generate -DgroupId=com.earendil -DartifactId=pi-agent-core
cd pi-agent-core
# 创建多模块结构
mkdir {pi-core-common,pi-session-manager,pi-tool-system,...}
```

### 2. Spring Boot 2.7 + JDK 8 配置
```xml
<properties>
  <java.version>1.8</java.version>
  <maven.compiler.source>1.8</maven.compiler.source>
  <maven.compiler.target>1.8</maven.compiler.target>
  <spring-boot.version>2.7.14</spring-boot.version>
  <jackson.version>2.13.4</jackson.version>
  <rocksdb.version>6.29.5</rocksdb.version>
</properties>
```

### 3. 核心依赖 pom.xml
```xml
<dependencies>
  <!-- Spring Boot 2.7 -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>2.7.14</version>
  </dependency>
  
  <!-- Jackson 序列化 -->
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.13.4</version>
  </dependency>
  
  <!-- RocksDB -->
  <dependency>
    <groupId>org.rocksdb</groupId>
    <artifactId>rocksdbjni</artifactId>
    <version>6.29.5</version>
  </dependency>
  
  <!-- Caffeine 缓存 -->
  <dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>2.9.3</version>
  </dependency>
  
  <!-- Lombok -->
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>
  </dependency>
  
  <!-- OkHttp3 -->
  <dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>3.14.9</version>
  </dependency>
</dependencies>
```

---

## 📈 预期里程碑

| 周期 | 主要成果 | 验收标准 |
|------|--------|---------|
| Week 3 | 框架搭建完成 | Maven 多模块可编译运行 |
| Week 6 | SessionManager 就绪 | 会话增删改查通过集成测试 |
| Week 9 | 工具系统可用 | 10+ 工具注册和执行 |
| Week 12 | LLM 多供应商支持 | 3 个供应商通过集成测试 |
| Week 15 | 上下文压缩算法完成 | 100K+ tokens 无损处理 |
| Week 18 | Agent 执行引擎就绪 | 循环执行通过压力测试 |
| Week 20 | 生产就绪 | 完整文档 + JDK 8 认证 |

