package com.github.cinnaio.echomarket.command

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.util.ItemUtil
import com.github.cinnaio.echomarket.util.MessageUtil
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
                MessageUtil.send(sender, "<command.list.header>", mapOf("count" to shops.size.toString()))
                shops.forEach { shop ->
                    MessageUtil.send(sender, "<command.list.entry>", mapOf(
                        "id" to shop.id.toString(),
                        "name" to shop.name,
                        "x" to shop.location.blockX.toString(),
                        "y" to shop.location.blockY.toString(),
                        "z" to shop.location.blockZ.toString(),
                        "world" to shop.location.world.name
                    ))
                }
            }
            "remove" -> {
                // If ID provided
                if (args.size > 1) {
                    val id = args[1].toIntOrNull()
                    if (id == null) {
                        MessageUtil.send(sender, "<command.remove.invalid-id>")
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
                            MessageUtil.send(sender, "<market.not-your-shop>")
                        }
                        return true
                    }
                }
                MessageUtil.send(sender, "<command.remove.usage>")
            }
            "name" -> {
                if (args.size < 2) {
                    MessageUtil.send(sender, "<command.name.usage>")
                    return true
                }
                val name = args.drop(1).joinToString(" ")
                plugin.marketManager.updateName(sender, name)
            }
            "desc" -> {
                if (args.size < 2) {
                    MessageUtil.send(sender, "<command.desc.usage>")
                    return true
                }
                val desc = args.drop(1).joinToString(" ")
                plugin.marketManager.updateDesc(sender, desc)
            }
            "skin" -> {
                if (args.size < 2) {
                     MessageUtil.send(sender, "<command.skin.usage>")
                     return true
                }
                val skinName = args[1]
                plugin.npcManager.updateNpcSkin(sender, skinName)
            }
            "sell" -> {
                if (args.size < 2) {
                    MessageUtil.send(sender, "<command.sell.usage>")
                    return true
                }
                val price = args[1].toDoubleOrNull()
                if (price == null || price < 0) {
                    MessageUtil.send(sender, "<general.invalid-amount>")
                    return true
                }
                
                val item = sender.inventory.itemInMainHand
                if (item.type.isAir) {
                    MessageUtil.send(sender, "<command.sell.no-item>")
                    return true
                }
                
                // 检查玩家是否有商店
                val shop = plugin.marketManager.resolveTargetShop(sender)
                if (shop == null) {
                    val shops = plugin.storage.getShops(sender.uniqueId)
                    if (shops.size > 1) {
                        MessageUtil.send(sender, "<market.multiple-shops-target>")
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
                val blacklist = plugin.config.getStringList("market.blacklist")
                if (blacklist.any { it.startsWith(hash) }) {
                    MessageUtil.send(sender, "<market.blacklisted>")
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
                
                // 获取物品名称（在删除物品前获取，防止引用失效）
                val itemName = ItemUtil.getDisplayName(item)
                
                sender.inventory.setItemInMainHand(null)
                
                MessageUtil.send(sender, "<command.sell.success>", mapOf(
                    "amount" to item.amount.toString(),
                    "price" to String.format("%.2f", expectedIncome),
                    "item" to itemName
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
                        MessageUtil.send(sender, "<command.admin.list.header>", mapOf("count" to shops.size.toString()))
                        shops.forEach { shop ->
                            MessageUtil.send(sender, "<command.admin.list.entry>", mapOf(
                                "id" to shop.id.toString(),
                                "owner" to shop.ownerName,
                                "name" to shop.name
                            ))
                        }
                    }
                    "ban" -> {
                        // /market admin ban <item> (hand)
                        val item = sender.inventory.itemInMainHand
                        if (item.type.isAir) {
                            MessageUtil.send(sender, "<command.admin.ban.no-item>")
                            return true
                        }
                        val hash = ItemUtil.calculateHash(item)
                        val blacklist = plugin.config.getStringList("market.blacklist").toMutableList()
                        val existingEntry = blacklist.find { it.startsWith(hash) }
                        
                        if (existingEntry != null) {
                            blacklist.remove(existingEntry)
                            MessageUtil.send(sender, "<command.admin.ban.unbanned>")
                        } else {
                            // 为了在 GUI 中显示正确的材质，我们存储 "Hash|Base64"
                            // 归一化数量为 1 再序列化，用于展示
                            val displayItem = item.clone()
                            displayItem.amount = 1
                            val serialized = ItemUtil.serializeItemStack(displayItem)
                            blacklist.add("$hash|$serialized")
                            MessageUtil.send(sender, "<command.admin.ban.banned>")
                        }
                        plugin.config.set("market.blacklist", blacklist)
                        plugin.saveConfig()
                    }
                    "fee" -> {
                         // /market admin fee [rate]
                         // If rate is provided, set fee. If not, just show hash/info.
                         val item = sender.inventory.itemInMainHand
                         if (item.type.isAir) {
                            MessageUtil.send(sender, "<command.admin.fee.no-item>")
                            return true
                        }
                        val hash = ItemUtil.calculateHash(item)
                        
                        if (args.size >= 3) {
                            val rateStr = args[2]
                            val rate = rateStr.toDoubleOrNull()
                            if (rate == null) {
                                MessageUtil.send(sender, "<command.invalid-number>")
                                return true
                            }
                            
                            // Save rate
                            plugin.config.set("market.special-fees.$hash", rate)
                            
                            // Save item display data
                            val displayItem = item.clone()
                            displayItem.amount = 1
                            val serialized = ItemUtil.serializeItemStack(displayItem)
                            plugin.config.set("market.special-fees-data.$hash", serialized)
                            
                            plugin.saveConfig()
                            MessageUtil.send(sender, "<command.admin.fee.set>", mapOf(
                                "rate" to rate.toString(),
                                "item" to ItemUtil.getDisplayName(item)
                            ))
                        } else {
                            // View only
                            val currentRate = plugin.config.getDouble("market.special-fees.$hash", -1.0)
                            if (currentRate >= 0) {
                                MessageUtil.send(sender, "<command.admin.fee.current>", mapOf(
                                    "rate" to currentRate.toString(),
                                    "hash" to hash
                                ))
                            } else {
                                MessageUtil.send(sender, "<command.admin.fee.hash>", mapOf("hash" to hash))
                                MessageUtil.send(sender, "<command.admin.fee.hint>")
                            }
                        }
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
        MessageUtil.send(sender, "<command.admin.help.reload>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.list>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.ban>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.fee>", emptyMap(), false)
    }

    private fun sendHelp(sender: CommandSender) {
        @Suppress("DEPRECATION")
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
