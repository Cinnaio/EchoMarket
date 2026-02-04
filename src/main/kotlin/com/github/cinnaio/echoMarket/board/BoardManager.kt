package com.github.cinnaio.echomarket.board

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.util.MessageUtil
import org.bukkit.entity.Player

class BoardManager(private val plugin: EchoMarket) {

    fun openBoard(player: Player) {
        plugin.guiManager.openBoardGui(player)
    }

    fun postMessage(player: Player, content: String) {
        // 检查是否已有留言（如果限制每人一条）
        // 需求未明确限制数量，但为了防刷屏，通常限制一条或者收费。
        // 需求：首次免费，付费续期。这意味着可能没有发布费，或者首次发布免费。
        // 假设：发布新留言是免费的。
        
        val duration = plugin.config.getLong("board.duration-seconds", 86400)
        plugin.storage.addBoardMessage(player.uniqueId, player.name, content, duration)
        MessageUtil.send(player, "<board.message-posted>")
        openBoard(player)
    }
    
    fun renewMessage(player: Player, messageId: Int) {
        // 检查余额 (这里需要 Vault 依赖，或者简单扣除物品？需求未提及经济插件)
        // 需求：使用 Kotlin Java 21... 兼容 Paper... 
        // 需求中没有提到 Vault 或经济前置。
        // 这是一个问题。通常“交易”隐含了经济系统。
        // 5.4 成交优先级规则：实际支付金额...
        // 5.5 下架服务费...
        // 如果没有 Vault，难道是以物易物？或者自带经济系统？
        // 通常默认是有 Vault 的。
        // 如果没有 Vault，我只能假设有 Vault，或者让用户自己去解决依赖。
        // 为了完整性，我应该检查 Vault 是否存在，或者简单实现一个 Dummy Economy (或者报错)。
        // 鉴于这是一个“插件生成”，通常默认包含 Vault 依赖。
        // 我需要在 plugin.yml 加 Vault 依赖，build.gradle.kts 加 Vault API。
        
        // 既然需求没提，我先假设有 Vault。
        // 并在 build.gradle.kts 添加 VaultAPI。
        
        // 续期逻辑
        val cost = plugin.config.getDouble("board.renew-price", 100.0)
        if (!EchoMarket.economy.has(player, cost)) {
            MessageUtil.send(player, "<market.insufficient-funds>")
            return
        }
        
        EchoMarket.economy.withdrawPlayer(player, cost)
        val duration = plugin.config.getLong("board.renew-duration-seconds", 86400)
        plugin.storage.renewBoardMessage(messageId, duration)
        MessageUtil.send(player, "<board.message-renewed>")
        openBoard(player) // 刷新
    }
}
