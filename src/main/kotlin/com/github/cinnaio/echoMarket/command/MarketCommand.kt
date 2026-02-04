package com.github.cinnaio.echomarket.command

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.util.MessageUtil
import com.github.cinnaio.echomarket.util.ItemUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.Bukkit

class MarketCommand(private val plugin: EchoMarket) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            MessageUtil.send(sender, "<general.not-player>")
            return true
        }

        if (args.isEmpty()) {
            plugin.marketManager.openMarket(sender)
            return true
        }

        when (args[0].lowercase()) {
            "help" -> {
                sendHelp(sender)
            }
            "reload" -> {
                if (!sender.hasPermission("market.admin")) {
                    MessageUtil.send(sender, "<general.no-permission>")
                    return true
                }
                plugin.configManager.reload()
                MessageUtil.send(sender, "<general.reload-success>")
            }
            "create" -> {
                val name = if (args.size > 1) args.drop(1).joinToString(" ") else null
                plugin.marketManager.createShop(sender, name, null)
            }
            "list" -> {
                val shops = plugin.storage.getShops(sender.uniqueId)
                if (shops.isEmpty()) {
                    MessageUtil.send(sender, "<market.no-shop>")
                    return true
                }
                MessageUtil.send(sender, "<color:#FFD479>=== 你的商店列表 (${shops.size}) ===")
                shops.forEach { shop ->
                    MessageUtil.send(sender, "<color:#A0A0A0>ID: <color:#E6E6E6>${shop.id} <color:#A0A0A0>| 名称: <color:#E6E6E6>${shop.name} <color:#A0A0A0>| 位置: <color:#E6E6E6>${shop.location.blockX},${shop.location.blockY},${shop.location.blockZ} (${shop.location.world.name})")
                }
            }
            "remove" -> {
                // If ID provided
                if (args.size > 1) {
                    val id = args[1].toIntOrNull()
                    if (id == null) {
                        MessageUtil.send(sender, "<color:#FF6B6B>无效的商店 ID。")
                        return true
                    }
                    plugin.marketManager.removeShop(sender, id)
                    return true
                }
                
                // No ID, try to find looked-at NPC
                val targetBlock = sender.getTargetBlockExact(5)
                if (targetBlock != null) {
                    val shopId = plugin.npcManager.getShopIdAt(targetBlock.location, 1.5)
                    if (shopId != null) {
                        // Verify ownership
                        val shops = plugin.storage.getShops(sender.uniqueId)
                        if (shops.any { it.id == shopId }) {
                            plugin.marketManager.removeShop(sender, shopId)
                        } else {
                            MessageUtil.send(sender, "<color:#FF6B6B>这不属于你的商店。")
                        }
                        return true
                    }
                }
                MessageUtil.send(sender, "<color:#FF6B6B>请看着你的商店 NPC 输入此命令，或指定商店 ID: /market remove <ID>")
            }
            "name" -> {
                if (args.size < 2) {
                    MessageUtil.send(sender, "<color:#FF6B6B>用法: /market name <名称>")
                    return true
                }
                val name = args.drop(1).joinToString(" ")
                plugin.marketManager.updateName(sender, name)
            }
            "desc" -> {
                if (args.size < 2) {
                    MessageUtil.send(sender, "<color:#FF6B6B>用法: /market desc <介绍>")
                    return true
                }
                val desc = args.drop(1).joinToString(" ")
                plugin.marketManager.updateDesc(sender, desc)
            }
            "skin" -> {
                if (args.size < 2) {
                     MessageUtil.send(sender, "<color:#FF6B6B>用法: /market skin <玩家名>")
                     return true
                }
                val skinName = args[1]
                plugin.npcManager.updateNpcSkin(sender, skinName)
            }
            "sell" -> {
                if (args.size < 2) {
                    MessageUtil.send(sender, "<color:#FF6B6B>用法: /market sell <价格>")
                    return true
                }
                val price = args[1].toDoubleOrNull()
                if (price == null || price < 0) {
                    MessageUtil.send(sender, "<general.invalid-amount>")
                    return true
                }
                
                val item = sender.inventory.itemInMainHand
                if (item.type.isAir) {
                    MessageUtil.send(sender, "<red>请手持要出售的物品。")
                    return true
                }
                
                // 检查玩家是否有商店
                val shop = plugin.marketManager.resolveTargetShop(sender)
                if (shop == null) {
                    val shops = plugin.storage.getShops(sender.uniqueId)
                    if (shops.size > 1) {
                        MessageUtil.send(sender, "<red>你有多个商店，请看着你要上架的商店 NPC。")
                    } else {
                        MessageUtil.send(sender, "<market.no-shop>")
                    }
                    return true
                }
                
                // 距离限制
                val distanceLimit = plugin.config.getDouble("market.distance-limit", 2.0)
                if (sender.location.distance(shop.location) > distanceLimit) {
                    MessageUtil.send(sender, "<market.too-far>")
                    return true
                }
                
                // 检查黑名单
                val hash = ItemUtil.calculateHash(item)
                if (plugin.config.getStringList("market.blacklist").contains(hash)) {
                    MessageUtil.send(sender, "<red>此物品已被禁止上架。")
                    return true
                }

                // 上架逻辑
                plugin.storage.addItem(shop.id, item.clone(), price, item.amount)
                
                // Calculate expected income (Price * 1 - fee)
                val transactionFeePercent = plugin.config.getDouble("market.transaction-fee", 3.0)
                // Check special fees
                val specialFees = plugin.config.getConfigurationSection("market.special-fees")
                val feeRate = if (specialFees != null && specialFees.contains(hash)) {
                    specialFees.getDouble(hash) / 100.0
                } else {
                    transactionFeePercent / 100.0
                }
                val expectedIncome = (price * item.amount) * (1 - feeRate)
                
                sender.inventory.setItemInMainHand(null)
                MessageUtil.send(sender, "<market.seller-sold-notification>", mapOf(
                    "amount" to item.amount.toString(),
                    "price" to String.format("%.2f", expectedIncome)
                ))
            }
            "admin" -> {
                if (!sender.hasPermission("market.admin")) {
                    MessageUtil.send(sender, "<general.no-permission>")
                    return true
                }
                if (args.size < 2) {
                    sendAdminHelp(sender)
                    return true
                }
                when (args[1].lowercase()) {
                    "reload" -> {
                        plugin.configManager.reload()
                        MessageUtil.send(sender, "<general.reload-success>")
                    }
                    "list" -> {
                        if (args.size > 2) {
                            when (args[2].lowercase()) {
                                "ban" -> {
                                    plugin.guiManager.openAdminBanList(sender)
                                    return true
                                }
                                "fee" -> {
                                    plugin.guiManager.openAdminFeeList(sender)
                                    return true
                                }
                            }
                        }
                        
                        // Default list command (all shops)
                        val shops = plugin.storage.getAllShops()
                        sender.sendMessage("§e=== 商店列表 (${shops.size}) ===")
                        shops.forEach { shop ->
                            sender.sendMessage("§7ID: ${shop.id} | Owner: ${shop.ownerName} | Name: ${shop.name}")
                        }
                    }
                    "ban" -> {
                        // /market admin ban <item> (hand)
                        val item = sender.inventory.itemInMainHand
                        if (item.type.isAir) {
                            MessageUtil.send(sender, "<red>请手持要封禁的物品。")
                            return true
                        }
                        val hash = ItemUtil.calculateHash(item)
                        val blacklist = plugin.config.getStringList("market.blacklist").toMutableList()
                        if (blacklist.contains(hash)) {
                            blacklist.remove(hash)
                            MessageUtil.send(sender, "<green>物品已解封。")
                        } else {
                            blacklist.add(hash)
                            MessageUtil.send(sender, "<red>物品已封禁。")
                        }
                        plugin.config.set("market.blacklist", blacklist)
                        plugin.saveConfig()
                    }
                    "fee" -> {
                         // /market admin fee <item> (hand)
                         val item = sender.inventory.itemInMainHand
                         if (item.type.isAir) {
                            MessageUtil.send(sender, "<red>请手持物品。")
                            return true
                        }
                        val hash = ItemUtil.calculateHash(item)
                        MessageUtil.send(sender, "<green>物品 Hash: <gold>$hash")
                        MessageUtil.send(sender, "<gray>请在 config.yml 中配置此 Hash 的特殊费率 (暂未实现自动配置)。")
                    }
                    else -> sendAdminHelp(sender)
                }
            }
            else -> {
                // MessageUtil.send(sender, "<red>未知命令。")
                sendHelp(sender)
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()

        if (args.size == 1) {
            val subCommands = mutableListOf("create", "list", "remove", "name", "desc", "sell", "skin", "help", "reload")
            if (sender.hasPermission("market.admin")) {
                subCommands.add("admin")
            }
            return subCommands.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        
        if (args.size == 2) {
             when (args[0].lowercase()) {
                 "remove" -> {
                     return plugin.storage.getShops(sender.uniqueId).map { it.id.toString() }.filter { it.startsWith(args[1], ignoreCase = true) }
                 }
                 "admin" -> {
                     if (sender.hasPermission("market.admin")) {
                         return listOf("reload", "list", "ban", "fee").filter { it.startsWith(args[1], ignoreCase = true) }
                     }
                 }
                 "skin" -> {
                     return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                 }
                 "sell" -> {
                     return listOf("<价格>") // 提示用
                 }
             }
        }

        if (args.size == 3) {
            if (args[0].equals("admin", ignoreCase = true)) {
                if (args[1].equals("list", ignoreCase = true)) {
                    return listOf("ban", "fee").filter { it.startsWith(args[2], ignoreCase = true) }
                }
            }
        }
        
        return emptyList()
    }
    
    private fun sendAdminHelp(sender: CommandSender) {
        sender.sendMessage("§c/market admin reload - 重载配置")
        sender.sendMessage("§c/market admin list - 列出商店")
        sender.sendMessage("§c/market admin ban - 封禁/解封手持物品")
        sender.sendMessage("§c/market admin fee - 查看手持物品 Hash")
    }

    private fun sendHelp(sender: CommandSender) {
        val version = plugin.description.version
        MessageUtil.send(sender, "<help.title>", mapOf("version" to version), false)
        
        val commands = listOf(
            "open" to "open-detail",
            "sell" to "sell-detail",
            "list" to "list-detail",
            "remove" to "remove-detail",
            "create" to "create-detail",
            "name" to "name-detail",
            "desc" to "desc-detail",
            "skin" to "skin-detail",
            "reload" to "reload-detail"
        )
        
        commands.forEach { (cmd, detail) ->
            MessageUtil.send(sender, "<help.$cmd>", emptyMap(), false)
            MessageUtil.send(sender, "<help.$detail>", emptyMap(), false)
        }
    }
}
