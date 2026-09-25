# docs 目录说明

按 JDK 版本整理的 Pi-Agent-Core Java 技术方案文档。

## 目录结构

```
docs/
├── jdk8/                        # JDK 8 技术方案
│   ├── Java Pi-Agent-Core 实现计划 (JDK 8).md
│   ├── Java Pi-Agent-Core 核心类框架 (JDK 8).html
│   └── Java Pi-Agent-Core POM 配置 (JDK 8).html
├── jdk17/                       # JDK 17+ 技术方案
│   ├── Java Pi-Agent-Core 实现计划方案.md
│   └── Java Pi-Agent-Core 核心类代码框架.html
├── Pi Agent-Core 原理与流程分析.md        # 通用：TS 原型分析（版本无关）
├── Pi Agent-Core 关键概念深度分析.md      # 通用：概念分析（版本无关）
├── Pi Agent-Core 执行流程图.png           # 通用：执行流程图
└── Java Pi-Agent-Core 架构设计图.png      # 通用：架构设计图
```

## 分类依据

| 判定 | 依据 |
|------|------|
| JDK 8 | 文件名明确标注；代码仅用 Lambda/Stream/CompletableFuture；`maven.compiler 1.8` |
| JDK 17 | 技术栈表声明 JDK 17+（虚拟线程、record、sealed）；代码大量使用 `record`/`sealed` 及 Project Reactor |

## 备注

- `Pi Agent-Core 关键概念深度分析 (1).md` 与 `Pi Agent-Core 关键概念深度分析.md` 内容完全相同（字节级重复），保留待人工确认后删除。
- 两套方案的核心差异：JDK 8 版用 CompletableFuture 手写异步、无反应式依赖；JDK 17 版基于 Project Reactor + record/sealed 建模。
