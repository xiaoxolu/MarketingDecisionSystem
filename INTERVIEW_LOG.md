# 面试记录

## 环境
- AI 工具：Codex Desktop（GPT-5）
- 项目：MarketingDecisionSystem，Java 8 + Maven + Spring Boot 2.7.18 + MySQL
- GitHub 仓库：https://github.com/xiaoxolu/MarketingDecisionSystem
- 搭建过程中的问题与解决：
  - 项目最初不是 Git 仓库：使用 `git init` 初始化本地仓库，并将默认分支改为 `main`。
  - 项目缺少 `.gitignore`：新增 `.gitignore`，排除 `target/`、IDE 配置、日志和本地环境文件，避免上传编译产物。
  - 本机没有 GitHub CLI：使用 `winget` 安装官方 GitHub CLI。
  - GitHub CLI 网页授权时，命令行与 GitHub 通信被本机网络中断：改为先在浏览器手动创建私有仓库，再通过 `git push` 触发 Git Credential Manager 浏览器认证，最终推送成功。

## 需求与实现（按时间顺序）
- [2026-07-04 16:48] 需求点：运行项目
- 我给 AI 的提示词（要点）：运行我的项目。
- AI 输出概要 / 我做了哪些采纳或修改：AI 检查项目结构，识别为 Maven/Spring Boot 项目；确认 Java、Maven、MySQL 3306 和 8080 端口状态；使用 `mvn spring-boot:run` 启动服务；验证首页 `http://localhost:8080/` 返回 200。
- 遇到的问题与怎么解决：项目依赖本机 MySQL，先确认 3306 端口可用；8080 未被占用后再启动，避免端口冲突。

- [2026-07-05 09:00] 需求点：上传项目到 GitHub 私有仓库
- 我给 AI 的提示词（要点）：将项目上传到 GitHub 的私有仓库，帮我操作。
- AI 输出概要 / 我做了哪些采纳或修改：AI 检查本地 Git 状态，发现项目还不是 Git 仓库；新增 `.gitignore`；初始化 Git 仓库；暂存源码、SQL、页面资源和测试数据；创建首次提交 `Initial commit`；将分支改为 `main`；把远端设置为 `https://github.com/xiaoxolu/MarketingDecisionSystem.git`；推送 `main` 到 GitHub。
- 遇到的问题与怎么解决：最初 `.gitignore` 放在了上级目录，导致 `target/` 曾被暂存；通过重置暂存区、把 `.gitignore` 移到仓库根目录后重新暂存，确认 `target/` 已被忽略。

- [2026-07-05 09:16] 需求点：了解如何添加 GitHub 协作者，并记录 AI 使用过程
- 我给 AI 的提示词（要点）：说明面试当天如何在 GitHub 仓库 Settings -> Collaborators 添加面试官账号；在仓库根目录创建 `INTERVIEW_LOG.md`，记录提示词要点、AI 输出概要、采纳修改和问题解决过程。
- AI 输出概要 / 我做了哪些采纳或修改：AI 根据 GitHub 仓库协作者管理流程说明操作位置，并按面试方给出的模板创建本文件；内容保留真实操作过程，方便面试时说明如何使用 AI 辅助开发与排错。
- 遇到的问题与怎么解决：GitHub 页面入口可能随版本有细微变化，最终以仓库页面的 `Settings` 和 `Collaborators`/`Manage access` 入口为准。
