# EchoMarket

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21-green.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

**EchoMarket** 是一个基于 **FancyNpcs** 和 **Vault** 的现代化玩家商店与全球市场插件。
专为 1.21+ 服务器设计，支持 Folia，提供沉浸式的 NPC 商店交互体验。

## ✨ 功能特性

- **🏪 NPC 实体商店**：基于 FancyNpcs，玩家可以创建实体 NPC 作为商店，支持点击交互。
- **🌍 全球市场菜单**：提供直观的 GUI 菜单浏览全服商店和商品。
- **💰 经济系统集成**：完美支持 Vault 经济系统，交易安全可靠。
- **📊 交易统计**：内置交易日志与统计系统，支持查询历史交易额与单数。
- **📈 热力值系统**：商店根据热力值智能排序，支持交易量权重与付费置顶（Boost）。
- **🔢 多商店管理**：玩家商店自动分配序号（#1, #2...），支持精准定位与管理。
- **📝 留言板系统**：提供玩家留言板功能，增强服务器社交属性。
- **⚡ 高性能**：支持 Folia 多线程架构，数据库操作异步处理（支持 SQLite/MySQL）。
- **🔌 PlaceholderAPI 支持**：提供丰富的 PAPI 变量展示玩家市场数据。
- **🛠️ 管理员工具**：支持违禁品封禁列表、自定义手续费率等管理功能。

## 📦 依赖项

在使用本插件前，请确保服务器已安装以下前置插件：

| 插件名称 | 必须 | 说明 |
| :--- | :---: | :--- |
| **Vault** | ✅ | 经济系统基础 API |
| **FancyNpcs** | ✅ | 用于生成和管理商店 NPC |
| **PlaceholderAPI** | ❌ | (可选) 用于变量展示 |

## 📥 安装说明

1. 下载 `EchoMarket-x.x.x.jar`。
2. 将插件放入服务器的 `plugins` 文件夹。
3. 确保已安装所有[必要前置](#-依赖项)。
4. 启动服务器，插件将自动生成配置文件。
5. (可选) 在 `config.yml` 中配置数据库连接（默认为 SQLite）。

## 📜 命令列表

主命令: `/market` 或 `/m`

| 子命令 | 权限 | 描述 |
| :--- | :--- | :--- |
| `/m` | 无 | 打开全球市场菜单 |
| `/m help` | 无 | 查看帮助信息 |
| `/m create [名称]` | 无 | 在当前位置创建一个商店 NPC |
| `/m list` | `market.list` | 查看自己拥有的商店列表 |
| `/m remove [ID]` | `market.remove` | 删除指定 ID 的商店（或查看的商店） |
| `/m name <名称>` | 无 | 修改当前看向商店的名称 |
| `/m desc <描述>` | 无 | 修改当前看向商店的描述 |
| `/m sell <价格>` | 无 | 将手中物品上架到当前看向的商店 |
| `/m admin` | `market.admin` | 打开管理员管理面板 (封禁/费率) |
| `/m admin heat` | `market.admin` | 管理商店热力值 (set/give/take) |
| `/m reload` | `market.admin` | 重载配置文件 |

## 🔧 配置文件

插件首次运行后会在 `plugins/EchoMarket/config.yml` 生成配置文件。
支持自定义：
- 数据库类型 (SQLite/MySQL)
- 交易手续费率
- 商店创建限制
- 语言消息 (Language)

## 🧩 PlaceholderAPI 变量

EchoMarket 提供了丰富的 PAPI 变量支持，详情请查阅 [PLACEHOLDERS.md](PLACEHOLDERS.md)。

示例：
- `%echomarket_volume_7d%`: 7天内交易总额
- `%echomarket_has_shop%`: 是否拥有商店

## 🤝 贡献与支持

如果你发现了 Bug 或有功能建议，欢迎提交 Issue 或 Pull Request。

---
*Created by Cinnaio*
