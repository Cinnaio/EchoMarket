# EchoMarket
 
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)
![Minecraft](https://img.shields.io/badge/Paper-1.21+-green.svg)
![Folia](https://img.shields.io/badge/Folia-supported-success.svg)
 
EchoMarket 是一个面向 Minecraft 1.21+ 的“玩家商店 + 全球市场”插件。以 FancyNpcs 提供的高拟真 NPC 交互为基础，结合 Vault 经济、可视化 GUI、市集留言板与可配置的费用/热度系统，打造沉浸式、可扩展的服务器交易生态。
 
## 功能特性
 
- NPC 实体商店：玩家可在世界中创建并管理自己的 NPC 商店，支持点击交互与皮肤修改
- 全球市场 GUI：集中浏览全服商店与商品，支持热度排序与置顶机制
- 经济集成：基于 Vault 的经济结算，交易过程安全且可审计
- 交易与日志：记录交易明细与统计（总额/次数），支持 PAPI 展示
- 热力值系统：按交易次数与置顶积分计算热力值，列表智能排序
- 留言板系统：在市场中发布公告/广告，提升曝光与互动
- 区域/安全检查：支持 WorldGuard 软依赖与安全落点检测，规范商店位置
- 多商店与序号：每位玩家支持多个商店，以序号进行精准定位
- 高性能：数据库操作异步化，支持 SQLite/MySQL，兼容 Folia
 
## 兼容与依赖
 
- 服务器：Paper/Folia 1.21+
- 必需：Vault、FancyNpcs
- 软依赖：PlaceholderAPI、WorldGuard
- 语言/运行：Java 21、Kotlin
 
## 安装与启动
 
- 将插件 Jar 放入服务器 plugins 目录
- 确认已安装依赖（Vault、FancyNpcs；可选：PAPI、WorldGuard）
- 首次启动自动生成配置与数据结构
- 配置数据库类型（默认 SQLite；可切换 MySQL）
 
## 配置总览
 
- 数据库：SQLite/MySQL 切换与连接参数
- 交易费用：
  - 下架服务费：fee-type（fixed/percent）、fee-value
  - 成交服务费：transaction-fee（百分比）
- 交易距离：distance-limit（默认 4 格，靠近商店交互）
- 黑名单与特殊费率：blacklist、special-fees（按物品 Hash 配置百分比）
- 热度权重：heat.weights.total-transactions、heat.weights.boost
- 置顶价格：heat.boost.price-per-point、purchase-amount
- 留言板：展示时长、续费价格与续期时长
 
## 权限与命令（概览）
 
- 权限：
  - market.admin：管理员操作（默认 op）
  - market.shops.limit.unlimited：无限商店（默认 op）
  - market.list、market.remove：玩家管理（默认允许）
 
- 主命令：/market（别名 /m）
  - 玩家侧：create、list、name、desc、sell、skin、movehere…
  - 管理侧：admin list/ban/fee/heat/board…、reload
 
## 构建与开发
 
- Gradle 构建，ShadowJar 打包可部署插件 Jar
- 主要任务：`build`（依赖 `shadowJar`），开发期可使用 Run Paper 任务
- Java 21 Kotlin Toolchain，资源文件版本占位由 `processResources` 注入
 
## 备注
 
- 本 README 旨在提供项目概览与集成信息，不包含玩法教程
 
