# EchoMarket PlaceholderAPI 变量文档

EchoMarket 支持 [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) 变量，允许你在计分板、聊天栏、Tab 列表或任何支持 PAPI 的地方展示玩家的市场统计数据。

## 基础信息
- **Identifier (标识符)**: `echomarket`
- **用法**: `%echomarket_<变量名>%`

## 变量列表

### 商店状态
| 变量名 | 描述 | 示例返回值 |
| :--- | :--- | :--- |
| `has_shop` | 玩家当前是否拥有至少一个商店 | `true` 或 `false` |
| `shop_count` | 玩家当前拥有的商店数量 | `1` |

### 热力值 (Heat)
展示商店的热度数据，用于衡量商店的活跃度与排名权重。

| 变量名 | 描述 | 示例返回值 |
| :--- | :--- | :--- |
| `heat_total` | 玩家所有商店的热力值总和 | `150` |
| `heat_max` | 玩家拥有商店中的最高热力值 | `100` |
| `heat_<序号>` | 玩家指定**序号**商店的热力值 | `50` (对应 #1 商店) |

### 交易统计 (金额)
统计玩家作为**买家**或**卖家**参与的所有交易总额。

| 变量名 | 描述 | 示例返回值 |
| :--- | :--- | :--- |
| `volume_24h` / `volume_1d` | 过去 24 小时内的交易总额 | `1050.50` |
| `volume_7d` / `volume_1w` | 过去 7 天内的交易总额 | `5200.00` |
| `volume_30d` / `volume_1m` | 过去 30 天内的交易总额 | `15000.00` |

### 交易统计 (笔数)
统计玩家作为**买家**或**卖家**参与的所有交易次数。

| 变量名 | 描述 | 示例返回值 |
| :--- | :--- | :--- |
| `transactions_24h` / `transactions_1d` | 过去 24 小时内的交易笔数 | `5` |
| `transactions_7d` / `transactions_1w` | 过去 7 天内的交易笔数 | `23` |
| `transactions_30d` / `transactions_1m` | 过去 30 天内的交易笔数 | `102` |

## 示例用法

### 在全息图 (Hologram) 中显示
```
&b&l[玩家市场数据]
&7商店数量: &f%echomarket_shop_count%
&7今日交易额: &6%echomarket_volume_24h%
&7本周交易量: &a%echomarket_transactions_7d% 笔
```

### 在菜单 (DeluxeMenus) 中使用条件显示
```yaml
view_requirement:
  requirements:
    has_shop:
      type: string equals
      input: '%echomarket_has_shop%'
      output: 'true'
```
