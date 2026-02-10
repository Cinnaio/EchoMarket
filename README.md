# EchoMarket - 全球市场

**EchoMarket** 是一个专为 Minecraft 1.21+ (Paper/Folia) 服务器设计的现代化全球市场插件。它结合了传统的全球市场功能与沉浸式的 NPC 商店体验，允许玩家创建属于自己的虚拟商店，并通过 GUI 界面进行便捷的物品买卖。

## ✨ 主要特性 (Features)

*   **全球市场 & 个人商店**: 
    *   玩家可以创建多个个人商店（由 NPC 代表）。
    *   支持查看全服所有商店的列表。
    *   基于 GUI 的直观操作界面。
*   **沉浸式 NPC 交互**:
    *   深度集成 **FancyNpcs**，每个商店都对应一个实体 NPC。
    *   支持自定义 NPC 皮肤（`/market skin`）和名称。
    *   玩家通过点击 NPC 即可打开商店界面。
*   **Folia 原生支持**:
    *   完全兼容 Folia 多线程服务端架构，确保在高性能服务器上的稳定运行。
*   **灵活的经济与税收系统**:
    *   **交易税**: 卖家出售物品时自动扣除一定比例的手续费。
    *   **特殊物品税率**: 管理员可为特定稀有物品设置独立的税率（支持通过 GUI/命令 管理）。
    *   **取消订单费用**: 防止恶意刷屏，取消上架物品可设置扣除手续费。
*   **黑名单系统**:
    *   管理员可以禁止特定物品（基于 Hash 匹配）上架销售。
    *   支持 NBT 数据的精确匹配。
*   **数据存储**:
    *   支持 **SQLite** (默认，开箱即用) 和 **MySQL** (适合大型服务器)。
*   **完全可配置**:
    *   所有提示消息、GUI 布局、税率规则等均可在配置文件中自定义。

## 🛠️ 前置插件 (Dependencies)

在安装本插件前，请确保您的服务器已安装以下前置：

1.  **Java 21** (最低要求)
2.  **Vault** (经济系统支持)
3.  **FancyNpcs** (NPC 功能支持)
    *   *注: FancyNpcs 可能需要 ProtocolLib*

## 📥 安装指南 (Installation)

1.  下载 `EchoMarket-1.6.8.jar` 文件。
2.  将插件放入服务器的 `plugins` 文件夹中。
3.  确保已安装所有前置插件。
4.  启动/重启服务器。
5.  (可选) 编辑 `plugins/EchoMarket/config.yml` 配置数据库连接和税率。

## 📖 命令列表 (Commands)

### 🛒 玩家命令
| 命令 | 描述 |
| :--- | :--- |
| `/market` | 打开全球市场主菜单 |
| `/market create [名称]` | 在脚下创建一个新的商店 NPC |
| `/market sell <价格>` | 将手持物品上架到最近的商店 |
| `/market list` | 查看自己拥有的所有商店 |
| `/market remove [ID]` | 删除指定 ID 的商店（或删除准星对准的商店） |
| `/market name <名称>` | 修改当前商店的名称 |
| `/market desc <介绍>` | 修改当前商店的介绍信息 |
| `/market skin <玩家名>` | 修改当前商店 NPC 的皮肤为指定玩家的皮肤 |

### 🛡️ 管理员命令
| 命令 | 描述 | 权限 |
| :--- | :--- | :--- |
| `/market admin reload` | 重载配置文件 | `market.admin` |
| `/market admin list` | 列出服务器内所有商店信息 | `market.admin` |
| `/market admin list fee` | 列出所有特殊税率物品 | `market.admin` |
| `/market admin list ban` | 列出所有黑名单物品 | `market.admin` |
| `/market admin ban` | 封禁/解封当前手持的物品 | `market.admin` |
| `/market admin fee [费率]` | 查看或设置手持物品的特殊手续费率 | `market.admin` |

## 🔐 权限节点 (Permissions)

| 权限节点 | 默认 | 描述 |
| :--- | :--- | :--- |
| `market.list` | true | 允许玩家列出自己的商店 |
| `market.remove` | true | 允许玩家删除自己的商店 |
| `market.admin` | op | 管理员权限 (重载/封禁/费率管理等) |
| `market.shops.limit.unlimited`| op | 允许玩家创建无限数量的商店 |
| `market.shops.limit.<数量>` | - | 限制玩家可创建的最大商店数量 (例如 `market.shops.limit.5`) |

## ⚙️ 配置文件 (Configuration)

`config.yml` 核心配置项说明：

```yaml
market:
  # NPC 可视距离（格）
  npc-view-distance: 32
  # 交易距离限制（格），玩家必须在商店 NPC 附近才能交易
  distance-limit: 4
  
  # 交易服务费 (百分比)
  transaction-fee: 3.0
  
  # 下架/取消订单服务费
  fee-type: percent # percent(百分比) 或 fixed(固定金额)
  fee-value: 3
  
  # 数据库设置
  database:
    type: sqlite # 或 mysql
```

---
*Developed by Cinnaio*
