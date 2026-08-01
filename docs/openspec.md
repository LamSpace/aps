# OpenSpec — AI 原生的规范驱动开发系统

## 概述

OpenSpec 是一个 AI 原生的规范驱动开发（Spec-Driven Development）系统，由 [Fission AI](https://github.com/Fission-AI/OpenSpec) 构建，npm 包名为 `@fission-ai/openspec`（MIT 许可证）。它将每一个代码变更组织为： **提案（proposal）→ 规范（specs）→ 设计（design）→ 任务（tasks）→ 实施（apply）→ 归档（archive）** 的完整生命周期。

当前版本： **1.7.0**

### 设计哲学

```
fluid not rigid          — 流动而非僵化
iterative not waterfall  — 迭代而非瀑布
easy not complex         — 简单而非复杂
built for brownfield     — 为存量项目设计，不仅为新项目
scalable                 — 从个人项目到企业级可扩展
```

### 核心理念

OpenSpec 将 AI 编码从"一次性对话"升级为"有记录、可追溯、可复盘的结构化流程"。每个变更都有明确的为什么（proposal）、改什么（specs）、怎么改（design）、分几步（tasks），实施和归档后形成完整的历史记录。

**与替代方案的对比：**

- **vs. Spec Kit (GitHub)**：更全面但较重，有严格的阶段门控。OpenSpec 更轻量，允许自由迭代。
- **vs. Kiro (AWS)**：强大但锁定在其 IDE 中且仅限于 Claude 模型。OpenSpec 兼容已有工具。
- **vs. 无规范**：AI 编码没有规范意味着模糊的提示词和不可预测的结果。

## 安装

### 前置条件

- Node.js（运行 CLI）
- 一个 Git 仓库（OpenSpec 基于项目的 Git 仓库工作）

### 通过 npm 安装

```bash
npm install -g @fission-ai/openspec@latest
```

也支持 pnpm、yarn、bun 和 nix。

**前置条件：** Node.js 20.19.0 或更高版本。

### 更新

```bash
npm install -g @fission-ai/openspec@latest
openspec update    # 刷新每个项目中的 agent 指令
```

### 初始化项目

在项目根目录运行：

```bash
openspec init
```

这将在项目根创建 `openspec/` 目录，包含：

```
openspec/
  config.yaml       # 项目配置
  specs/            # 主规范文件（项目的核心行为定义）
  changes/          # 变更提案（进行中和已完成的变更）
    archive/        # 已归档的变更
```

同时生成 `.openspec.yaml` 项目配置文件。

---

## 核心概念

### 1. 工作流模式（Schema）

OpenSpec 默认使用 **spec-driven** 模式，定义了 4 个核心制品：

| 制品         | 文件                         | 目的                                 |
|--------------|------------------------------|--------------------------------------|
| **proposal** | `proposal.md`                | 变更的"为什么"——动机和影响           |
| **specs**    | `specs/<capability>/spec.md` | 变更的"改什么"——行为和需求的增量定义 |
| **design**   | `design.md`                  | 变更的"怎么做"——技术决策和方案       |
| **tasks**    | `tasks.md`                   | 变更的"分几步"——可追踪的实现清单     |

### 2. Capability（能力）

能力是系统的可独立描述的功能单元，每个能力在 `openspec/specs/<name>/spec.md` 中定义。变更提案通过增量 spec 文件修改这些能力：

- **新增能力（New Capability）：** 创建新的 `specs/<name>/spec.md`
- **修改能力（Modified Capability）：** 修改已有的 spec 文件

### 3. Delta Spec（增量规范）

变更中的 spec 文件是 **增量**而非完整规范——它只描述新增、修改或删除的需求。当变更归档时，增量会合并到主规范中。

增量操作类型（通过章节标题声明）：

| 操作         | 章节标题                  | 说明                                                         |
|--------------|---------------------------|--------------------------------------------------------------|
| **ADDED**    | `### Requirement: <name>` | 新增需求。新能力还需 `## Purpose` 章节（50+ 字符）           |
| **MODIFIED** | `### Requirement: <name>` | 修改已有需求。**必须**包含完整的需求块（从主规范复制后修改） |
| **REMOVED**  | `### Requirement: <name>` | 删除需求。必须包含 **Reason** 和 **Migration**               |
| **RENAMED**  | `### Requirement: <name>` | 仅重命名。使用 `FROM:` / `TO:` 格式                          |

**规范格式规则：**

- 需求标题：`### Requirement: <name>`（3 个井号）
- 场景标题：`#### Scenario: <name>`（必须是 4 个井号——用 3 个或 bullets 会 **静默失败**）
- 每个需求必须至少有一个场景
- 使用 SHALL/MUST 进行规范性需求描述（避免 should/may）
- 场景格式：WHEN/THEN 结构

### 4. skip_specs

当变更不涉及行为变更（纯重构、工具配置、文档）时，可在 `.openspec.yaml` 中设置 `skip_specs: true`，跳过 spec 制品。

---

## 完整 CLI 命令参考

### `openspec init [path]`

初始化项目的 OpenSpec 结构。

```bash
openspec init              # 在当前目录初始化
openspec init /path/to/repo # 在指定路径初始化
```

### `openspec update [path]`

更新 OpenSpec 指令文件，同步最新模板和规则。

### `openspec list [options]`

列出项目中的项目。

```bash
openspec list              # 列出活跃变更
openspec list --specs      # 列出所有规范
openspec list --json       # JSON 格式输出（供程序使用）
```

### `openspec status --change "<name>" [--json]`

显示变更的制品完成状态。

```bash
openspec status --change "add-user-auth"
openspec status --change "add-user-auth" --json
```

JSON 输出包括：

- `artifacts`：每个制品的状态（ready/blocked/done/skipped）
- `applyRequires`：实施前必须完成的制品列表
- `isComplete`：所有制品是否已完成
- `actionContext`：作用范围和编辑约束

### `openspec show [item-name]`

显示一个变更或规范的详细内容。

### `openspec view`

启动交互式仪表盘，可视化浏览规范和变更。

### `openspec instructions <artifact-id> --change "<name>" --json`

获取特定制品的创建指令。返回：

- `template`：文件结构模板
- `instruction`：编写指导
- `context`：项目约束
- `rules`：制品规则
- `dependencies`：需要先完成的制品
- `resolvedOutputPath`：输出的文件路径

### `openspec new change "<name>"`

创建新的变更目录。自动生成 `.openspec.yaml` 和目录结构。

```bash
openspec new change "add-user-auth"
```

### `openspec change`

管理变更提案的子命令组。

```bash
openspec change --help   # 查看子命令
```

### `openspec archive [change-name]`

归档已完成的变更。自动将日期前缀加入目录名（`YYYY-MM-DD-<name>`），合并增量规范到主规范。

```bash
openspec archive "add-user-auth"
```

### `openspec spec`

管理和查看规范的子命令组。

### `openspec validate [item-name]`

验证变更和规范的结构完整性。

### `openspec config [options]`

查看和修改全局 OpenSpec 配置。

### `openspec schema`

管理（实验性）工作流模式。

### `openspec store`

创建和管理 Store——在本地注册的独立 OpenSpec 仓库，用于跨项目共享规范。

### `openspec doctor`

诊断当前 OpenSpec 根的健康状态和关系。

### `openspec context`

输出当前 OpenSpec 根的工作上下文。

### `openspec workset`

组成、保持和打开个人工作视图（纯本地）。

### `openspec feedback <message>`

向 OpenSpec 提交反馈。

### `openspec completion`

管理 shell 自动补全。

### `openspec templates`

显示所有制品的已解析模板路径。

---

## 配置文件

### 项目级配置：`openspec/config.yaml`

```yaml
schema: spec-driven          # 工作流模式

# 项目上下文（可选）
# 创建制品时展示给 AI。可在此添加技术栈、约定、风格指南等
context: |
  Tech stack: Java 17, Maven, ASM
  We use conventional commits

# 每制品规则（可选）
rules:
  proposal:
    - Keep proposals under 500 words
  tasks:
    - Break tasks into chunks of max 2 hours

# 每操作指引（可选）
operations:
  apply:
    guidance:
      - Keep test summaries concise
  archive:
    guidance:
      - Summarize the archive outcome before finishing
```

### 变更级配置：`openspec/changes/<name>/.openspec.yaml`

```yaml
schema: spec-driven
created: 2026-08-01
skip_specs: true   # 跳过 spec 制品（文档/重构/工具变更）
```

---

## 典型工作流

### 完整示例：添加缺失的 Javadoc

**1. 创建变更**

```bash
openspec new change "add-missing-javadoc"
```

**2. 编写提案（proposal.md）**

```markdown
## Why

部分公开方法缺少 Javadoc 文档，降低代码可读性和 API 可用性。

## What Changes

- 为所有缺少 Javadoc 的公开/受保护方法添加文档
- 遵循代码库已有风格

## Capabilities

### New Capabilities

（无——纯文档变更）

## Impact

- 所有 src/main/java 下的文件
- 无 API 变更，无行为变更
```

**3. 跳过规范（文档变更）**
编辑 `.openspec.yaml`：

```yaml
skip_specs: true
```

**4. 编写设计（design.md）**

```markdown
## Context

APS 项目有 13 个主源文件，部分 package-private 方法缺少 Javadoc。

## Decisions

1. 逐文件审查——确保每个 Javadoc 准确描述方法行为
2. 遵循已有约定——@param、@return、@throws 标签
3. 排除测试文件
```

**5. 编写任务（tasks.md）**

```markdown
## 1. BytecodeUtils.java

- [ ] 1.1 Add Javadoc to pushInt
- [ ] 1.2 Add Javadoc to loadOpcode ...

## 2. Verification

- [ ] 2.1 mvn compile
- [ ] 2.2 mvn javadoc:javadoc
```

**6. 实施（apply）**
按照 tasks.md 逐项完成，每次完成将 `- [ ]` 改为 `- [x]`。

**7. 归档（archive）**

```bash
openspec archive "add-missing-javadoc"
```

变更目录移动到 `openspec/changes/archive/2026-08-01-add-missing-javadoc/`。

---

## 制品依赖关系

```
proposal ──┬──► specs ──┬──► tasks
           └──► design ──┘
```

- **proposal** 无依赖，最先创建
- **specs** 和 **design** 依赖 proposal
- **tasks** 依赖 specs 和 design
- **apply** 依赖 tasks

`applyRequires` 指定实施前必须完成的制品。默认 spec-driven 模式需要 `tasks`。当 `skip_specs: true` 时，specs 状态为 `skipped`，满足依赖要求。

---

## 技能集成

OpenSpec 提供了一组 Claude Code 技能（slash 命令）：

| 技能                       | 功能                                   |
|----------------------------|----------------------------------------|
| `/openspec-propose`        | 创建新变更提案，一步生成所有制品       |
| `/openspec-apply-change`   | 实施变更中的任务                       |
| `/openspec-archive-change` | 归档已完成的变更                       |
| `/openspec-explore`        | 探索模式——思考想法、调查问题、澄清需求 |
| `/openspec-sync-specs`     | 将增量规范合并到主规范                 |
| `/openspec-update-change`  | 更新已有变更的制品                     |

对应的实验性技能（`/opsx:*`）提供相同功能的简化版本。

---

## Stores（Beta）

Stores 是独立的 OpenSpec 仓库，可在本地注册以实现跨项目和跨团队共享规范：

- **跨仓库功能**——一次变更，一份计划，即使代码分散在多个仓库
- **共享需求**——平台团队维护规范，产品团队只读引用
- **计划先行**——先在 Store 中捕获计划，代码仓库随后跟上

**设置 Store：**

```bash
openspec store setup my-store              # 创建并注册本地 Store
openspec store register /path/to/existing  # 注册已有 Store
openspec store list                        # 列出已注册 Store
```

在命令中引用 Store：

```bash
openspec status --change "feature-x" --store my-store
```

---

## 注意事项

1. **Store 模式：** 对于跨项目共享规范的大型组织，可使用 `openspec store` 创建独立的规范仓库。在这种模式下，命令需添加 `--store <id>` 标志。

2. **增量规范合并：** 归档时，增量 spec 文件会与主规范合并。需确保增量准确描述新增/修改/删除的需求，避免合并冲突。

3. **`skip_specs` 使用限制：** 仅在无行为变更时使用——纯重构、工具配置、文档。`openspec validate` 会拒绝零增量且未设置 `skip_specs` 的变更。

4. **制品创建顺序：** 必须按依赖顺序创建——先 proposal，再 specs/design，最后 tasks。`openspec status --json` 可查看当前状态。

5. **模板驱动：** 制品使用模板，可通过 `openspec templates` 查看。`openspec instructions` 提供制品专属的创建指导。

6. **实施时的制品锁定：** 所有制品写入后不应随意修改。如实施过程中发现设计问题，应暂停并更新制品。

7. **归档不可逆：** 归档将变更移出活跃列表。确认所有任务完成后再归档。

8. **实验性功能：** `schema` 命令组标记为实验性，接口可能变动。

9. **模型选择：** OpenSpec 在强推理模型上效果最佳。Codex 5.5 和 Opus 4.7 推荐用于规划和实现。

10. **上下文卫生：** 开始实现前清理上下文窗口。整个会话期间保持良好的上下文卫生。

11. **常见陷阱：**
    - **不完整的 MODIFIED 需求：** MODIFIED 仅包含部分内容会在归档时丢失细节。必须从主规范复制完整需求块后再修改。
    - **Proposal 列出了 Capability 但没有对应的增量 spec 文件：** 每个能力都需要相应的 delta spec。
    - **零增量变更未设置 skip_specs：** `openspec validate` 会拒绝这些变更。
    - **新能力缺少 Purpose 章节：** 归档后主规范中会出现 `TBD ...` 占位符。

12. **遥测：** 匿名使用统计（仅命令名称和版本——不含参数、路径、内容或个人信息）。CI 中自动禁用。选择退出：`export OPENSPEC_TELEMETRY=0` 或 `export DO_NOT_TRACK=1`。

## 资源链接

- GitHub：https://github.com/Fission-AI/OpenSpec
- npm：https://www.npmjs.com/package/@fission-ai/openspec
- Discord：https://discord.gg/YctCnvvshC
- OpenSpec 文档：通过 `openspec --help` 及子命令 `--help` 获取
- 反馈：`openspec feedback <message>`
