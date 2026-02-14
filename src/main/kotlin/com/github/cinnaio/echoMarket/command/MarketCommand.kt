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
        if (args.isEmpty()) {
            if (sender !is Player) {
                MessageUtil.send(sender, "<general.not-player>")
                return true
            }
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
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
                val name = if (args.size > 1) args.drop(1).joinToString(" ") else null
                plugin.marketManager.createShop(sender, name, null)
            }
            "list" -> {
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
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
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
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
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
                if (args.size < 2) {
                    MessageUtil.send(sender, "<command.name.usage>")
                    return true
                }
                
                var targetIndex: Int? = null
                var nameStartIndex = 1
                
                // Check if first arg is an index
                val potentialIndex = args[1].toIntOrNull()
                if (potentialIndex != null) {
                    val shops = plugin.storage.getShops(sender.uniqueId)
                    if (shops.any { it.index == potentialIndex }) {
                        targetIndex = potentialIndex
                        nameStartIndex = 2
                    }
                }
                
                if (args.size <= nameStartIndex) {
                    // Only index provided or no name provided
                     MessageUtil.send(sender, "<command.name.usage>")
                     return true
                }

                val name = args.drop(nameStartIndex).joinToString(" ")
                plugin.marketManager.updateName(sender, name, targetIndex)
            }
            "desc" -> {
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
                if (args.size < 2) {
                    MessageUtil.send(sender, "<command.desc.usage>")
                    return true
                }
                
                var targetIndex: Int? = null
                var nameStartIndex = 1
                
                val potentialIndex = args[1].toIntOrNull()
                if (potentialIndex != null) {
                    val shops = plugin.storage.getShops(sender.uniqueId)
                    if (shops.any { it.index == potentialIndex }) {
                        targetIndex = potentialIndex
                        nameStartIndex = 2
                    }
                }
                
                if (args.size <= nameStartIndex) {
                     MessageUtil.send(sender, "<command.desc.usage>")
                     return true
                }
                
                val desc = args.drop(nameStartIndex).joinToString(" ")
                plugin.marketManager.updateDesc(sender, desc, targetIndex)
            }
            "movehere" -> {
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
                
                val targetIndex = if (args.size > 1) args[1].toIntOrNull() else null
                plugin.marketManager.moveShop(sender, targetIndex)
            }
            "skin" -> {
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
                if (args.size < 2) {
                     MessageUtil.send(sender, "<command.skin.usage>")
                     return true
                }
                val skinName = args[1]
                val targetIndex = if (args.size > 2) args[2].toIntOrNull() else null
                plugin.npcManager.updateNpcSkin(sender, skinName, targetIndex)
            }
            "sell" -> {
                if (sender !is Player) {
                    MessageUtil.send(sender, "<general.not-player>")
                    return true
                }
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
                
                val adminUuid = if (sender is Player) sender.uniqueId else java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
                
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
                                    if (sender !is Player) {
                                        MessageUtil.send(sender, "<general.not-player>")
                                        return true
                                    }
                                    plugin.guiManager.openAdminBanList(sender)
                                    return true
                                }
                                "fee" -> {
                                    if (sender !is Player) {
                                        MessageUtil.send(sender, "<general.not-player>")
                                        return true
                                    }
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
                        if (sender !is Player) {
                            MessageUtil.send(sender, "<general.not-player>")
                            return true
                        }
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
                            plugin.storage.logAdminAction(adminUuid, sender.name, "BAN_ITEM_REMOVE", "Hash: $hash", "Unbanned")
                        } else {
                            // 为了在 GUI 中显示正确的材质，我们存储 "Hash|Base64"
                            // 归一化数量为 1 再序列化，用于展示
                            val displayItem = item.clone()
                            displayItem.amount = 1
                            val serialized = ItemUtil.serializeItemStack(displayItem)
                            blacklist.add("$hash|$serialized")
                            MessageUtil.send(sender, "<command.admin.ban.banned>")
                            plugin.storage.logAdminAction(adminUuid, sender.name, "BAN_ITEM_ADD", "Hash: $hash", "Banned")
                        }
                        plugin.config.set("market.blacklist", blacklist)
                        plugin.saveConfig()
                    }
                    "fee" -> {
                         // /market admin fee [rate]
                         // If rate is provided, set fee. If not, just show hash/info.
                         if (sender !is Player) {
                            MessageUtil.send(sender, "<general.not-player>")
                            return true
                        }
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
                            plugin.storage.logAdminAction(adminUuid, sender.name, "SET_FEE", "Hash: $hash", "Rate: $rate")
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
                    "heat" -> {
                        // /market admin heat <set|give|take> <player> <amount> [index]
                        if (args.size < 5) {
                            MessageUtil.send(sender, "<command.admin.heat.usage>")
                            return true
                        }
                        
                        val action = args[2].lowercase()
                        val targetName = args[3]
                        val amountStr = args[4]
                        val amount = amountStr.toDoubleOrNull()
                        val targetIndex = if (args.size > 5) args[5].toIntOrNull() else null
                        
                        if (amount == null) {
                            MessageUtil.send(sender, "<general.invalid-amount>")
                            return true
                        }
                        
                        val target = Bukkit.getPlayerExact(targetName)
                        val targetUuid = if (target != null) {
                            target.uniqueId
                        } else {
                             // Try offline player
                             val offline = Bukkit.getOfflinePlayer(targetName)
                             if (offline.hasPlayedBefore()) offline.uniqueId else null
                        }
                        
                        if (targetUuid == null) {
                            MessageUtil.send(sender, "<general.player-not-found>")
                            return true
                        }
                        
                        val shops = plugin.storage.getShops(targetUuid)
                        if (shops.isEmpty()) {
                            MessageUtil.send(sender, "<market.no-shop>")
                            return true
                        }
                        
                        var successCount = 0
                        shops.forEach { shop ->
                            // If index specified, check match. Else, apply to all.
                            if (targetIndex != null && shop.index != targetIndex) {
                                return@forEach
                            }
                            
                            val result = when (action) {
                                "set" -> plugin.storage.setShopBoost(shop.id, amount)
                                "give" -> plugin.storage.addShopBoost(shop.id, amount)
                                "take" -> plugin.storage.addShopBoost(shop.id, -amount)
                                else -> false
                            }
                            if (result) successCount++
                        }
                        
                        if (successCount > 0) {
                            plugin.storage.logAdminAction(adminUuid, sender.name, "MODIFY_HEAT", "Target: $targetName", "Action: $action, Amount: $amount, Count: $successCount")
                            MessageUtil.send(sender, "<command.admin.heat.success>", mapOf(
                                "action" to action,
                                "player" to targetName,
                                "amount" to amount.toString(),
                                "count" to successCount.toString()
                            ))
                        } else {
                            if (targetIndex != null) {
                                MessageUtil.send(sender, "<command.admin.heat.not-found-index>", mapOf("index" to targetIndex.toString()))
                            } else {
                                MessageUtil.send(sender, "<command.admin.heat.failed>")
                            }
                        }
                    }
                    "remove" -> {
                        // /market admin remove (looks at NPC)
                        if (args.size == 2) {
                            if (sender !is Player) {
                                MessageUtil.send(sender, "<general.not-player>")
                                return true
                            }
                            val targetBlock = sender.getTargetBlockExact(5)
                            if (targetBlock != null) {
                                val shopId = plugin.npcManager.getShopIdAt(targetBlock.location, 1.5)
                                if (shopId != null) {
                                    val shop = plugin.storage.getShop(shopId)
                                    if (shop != null) {
                                        if (plugin.storage.removeShop(shopId)) {
                                            plugin.npcManager.removeNpc(shopId)
                                            MessageUtil.send(sender, "<market.shop-removed>")
                                            notifyPlayer(shop.ownerUuid, "market.admin-shop-removed", mapOf("name" to shop.name, "admin" to sender.name))
                                            plugin.storage.logAdminAction(adminUuid, sender.name, "REMOVE_SHOP", "ShopID: $shopId", "Owner: ${shop.ownerName}, Name: ${shop.name}")
                                        } else {
                                            MessageUtil.send(sender, "<market.remove-failed>")
                                        }
                                    } else {
                                        plugin.npcManager.removeNpc(shopId)
                                        MessageUtil.send(sender, "<market.shop-removed>")
                                    }
                                    return true
                                }
                            }
                            MessageUtil.send(sender, "<command.admin.remove.usage>")
                            return true
                        }
                        
                        // /market admin remove <player> [index]
                         val targetName = args[2]
                         val targetIndex = if (args.size > 3) args[3].toIntOrNull() else null
                         
                         val target = Bukkit.getPlayerExact(targetName)
                         val targetUuid = if (target != null) {
                             target.uniqueId
                         } else {
                              val offline = Bukkit.getOfflinePlayer(targetName)
                              if (offline.hasPlayedBefore()) offline.uniqueId else null
                         }
                         
                         if (targetUuid == null) {
                             MessageUtil.send(sender, "<general.player-not-found>")
                             return true
                         }
                         
                         val shops = plugin.storage.getShops(targetUuid)
                         if (shops.isEmpty()) {
                             MessageUtil.send(sender, "<market.no-shop>")
                             return true
                         }
                         
                         if (targetIndex != null) {
                                val shop = shops.find { it.index == targetIndex }
                                if (shop != null) {
                                    if (plugin.storage.removeShop(shop.id)) {
                                        plugin.npcManager.removeNpc(shop.id)
                                        MessageUtil.send(sender, "<market.shop-removed>")
                                        notifyPlayer(shop.ownerUuid, "market.admin-shop-removed", mapOf("name" to shop.name, "admin" to sender.name))
                                        plugin.storage.logAdminAction(adminUuid, sender.name, "REMOVE_SHOP", "ShopID: ${shop.id}", "Owner: ${shop.ownerName}, Name: ${shop.name}")
                                    } else {
                                        MessageUtil.send(sender, "<market.remove-failed>")
                                    }
                                } else {
                                 MessageUtil.send(sender, "<command.admin.remove.not-found-index>", mapOf("index" to targetIndex.toString()))
                             }
                         } else {
                             if (shops.size == 1) {
                                val shop = shops[0]
                                if (plugin.storage.removeShop(shop.id)) {
                                    plugin.npcManager.removeNpc(shop.id)
                                    MessageUtil.send(sender, "<market.shop-removed>")
                                    notifyPlayer(shop.ownerUuid, "market.admin-shop-removed", mapOf("name" to shop.name, "admin" to sender.name))
                                    plugin.storage.logAdminAction(adminUuid, sender.name, "REMOVE_SHOP", "ShopID: ${shop.id}", "Owner: ${shop.ownerName}, Name: ${shop.name}")
                                } else {
                                    MessageUtil.send(sender, "<market.remove-failed>")
                                }
                            } else {
                                 MessageUtil.send(sender, "<command.admin.remove.specify-index>")
                                 shops.forEach { s ->
                                     MessageUtil.send(sender, "<command.admin.remove.list-entry>", mapOf(
                                         "id" to s.id.toString(),
                                         "index" to s.index.toString(),
                                         "name" to s.name
                                     ))
                                 }
                             }
                         }
                     }
                     "board" -> {
                         if (args.size < 3 || !args[2].equals("remove", ignoreCase = true)) {
                             MessageUtil.send(sender, "<command.admin.board.usage>")
                             return true
                         }
                         
                         if (args.size < 4) {
                             MessageUtil.send(sender, "<command.admin.board.usage>")
                             return true
                         }
                         
                         val targetStr = args[3]
                         val targetId = targetStr.toIntOrNull()
                         
                         // Try removing by ID if it's an integer
                        if (targetId != null) {
                            val msg = plugin.storage.getBoardMessage(targetId)
                            if (msg != null) {
                                if (plugin.storage.deleteBoardMessage(targetId)) {
                                    MessageUtil.send(sender, "<board.removed-id>", mapOf("id" to targetId.toString()))
                                    notifyPlayer(msg.ownerUuid, "market.admin-message-removed", mapOf("content" to msg.content, "admin" to sender.name))
                                    plugin.storage.logAdminAction(adminUuid, sender.name, "REMOVE_BOARD", "BoardID: $targetId", "Owner: ${msg.ownerName}, Content: ${msg.content}")
                                    return true
                                }
                            }
                            // If deletion failed, it might not be an ID but a player name that is a number (unlikely but possible)
                            // or simply ID not found. Continue to try as player name.
                        }
                         
                         // Remove by Player Name
                         val targetPlayer = Bukkit.getOfflinePlayer(targetStr)
                         if (!targetPlayer.hasPlayedBefore() && targetPlayer.player == null) {
                             // If targetId was not null, we already tried ID. So it's neither valid ID nor valid player.
                             if (targetId != null) {
                                  MessageUtil.send(sender, "<board.remove-failed>")
                             } else {
                                  MessageUtil.send(sender, "<general.player-not-found>")
                             }
                             return true
                         }
                         
                         val msgs = plugin.storage.getBoardMessages(targetPlayer.uniqueId)
                         if (msgs.isEmpty()) {
                              MessageUtil.send(sender, "<board.no-messages>", mapOf("player" to targetStr))
                              return true
                         }
                         
                         if (msgs.size == 1) {
                            val msg = msgs[0]
                            plugin.storage.deleteBoardMessage(msg.id)
                             MessageUtil.send(sender, "<board.removed-player>", mapOf("player" to targetStr))
                             notifyPlayer(msg.ownerUuid, "market.admin-message-removed", mapOf("content" to msg.content, "admin" to sender.name))
                             plugin.storage.logAdminAction(adminUuid, sender.name, "REMOVE_BOARD", "BoardID: ${msg.id}", "Owner: ${msg.ownerName}, Content: ${msg.content}")
                             return true
                        }
                         
                         // Check optional ID arg if multiple messages
                         if (args.size > 4) {
                             val specifiedId = args[4].toIntOrNull()
                            if (specifiedId != null) {
                                val msg = msgs.find { it.id == specifiedId }
                                if (msg != null) {
                                    plugin.storage.deleteBoardMessage(specifiedId)
                                     MessageUtil.send(sender, "<board.removed-id>", mapOf("id" to specifiedId.toString()))
                                     notifyPlayer(msg.ownerUuid, "market.admin-message-removed", mapOf("content" to msg.content, "admin" to sender.name))
                                     plugin.storage.logAdminAction(adminUuid, sender.name, "REMOVE_BOARD", "BoardID: $specifiedId", "Owner: ${msg.ownerName}, Content: ${msg.content}")
                                 } else {
                                    MessageUtil.send(sender, "<board.id-not-owned>", mapOf("id" to specifiedId.toString(), "player" to targetStr))
                                }
                                return true
                            }
                         }
                         
                         // List messages
                         MessageUtil.send(sender, "<board.multiple-messages>")
                         msgs.forEach { msg ->
                             val date = java.text.SimpleDateFormat("MM-dd HH:mm").format(java.util.Date(msg.expireAt))
                             MessageUtil.send(sender, "<board.list-entry>", mapOf(
                                 "id" to msg.id.toString(),
                                 "content" to msg.content,
                                 "expire" to date
                             ))
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
            val subCommands = mutableListOf("create", "list", "remove", "name", "desc", "sell", "skin", "movehere", "help", "reload")
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
                         return listOf("reload", "list", "ban", "fee", "heat", "remove", "board").filter { it.startsWith(args[1], ignoreCase = true) }
                     }
                 }
                 "skin" -> {
                     return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                 }
                 "sell" -> {
                     return listOf("<价格>") // 提示用
                 }
                "movehere" -> {
                    return plugin.storage.getShops(sender.uniqueId).map { it.index.toString() }.filter { it.startsWith(args[1], ignoreCase = true) }
                }
             }
        }

        if (args.size == 3) {
            if (args[0].equals("admin", ignoreCase = true)) {
                if (args[1].equals("list", ignoreCase = true)) {
                    return listOf("ban", "fee").filter { it.startsWith(args[2], ignoreCase = true) }
                }
                if (args[1].equals("heat", ignoreCase = true)) {
                    return listOf("set", "give", "take").filter { it.startsWith(args[2], ignoreCase = true) }
                }
                if (args[1].equals("remove", ignoreCase = true)) {
                    return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                }
                if (args[1].equals("board", ignoreCase = true)) {
                    return listOf("remove").filter { it.startsWith(args[2], ignoreCase = true) }
                }
            }
        }
        
        if (args.size == 4) {
                             if (args[0].equals("admin", ignoreCase = true)) {
                                  if (args[1].equals("heat", ignoreCase = true)) {
                                      return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[3], ignoreCase = true) }
                                  }
                                  if (args[1].equals("remove", ignoreCase = true)) {
                                      return listOf("<index>")
                                  }
                                  if (args[1].equals("board", ignoreCase = true) && args[2].equals("remove", ignoreCase = true)) {
                                      return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[3], ignoreCase = true) }
                                  }
                             }
                        }
                        if (args.size == 5) {
                            if (args[0].equals("admin", ignoreCase = true) && args[1].equals("board", ignoreCase = true) && args[2].equals("remove", ignoreCase = true)) {
                                return listOf("<id>")
                            }
                        }
        
        if (args.size == 5) {
            if (args[0].equals("admin", ignoreCase = true) && args[1].equals("heat", ignoreCase = true)) {
                return listOf("<amount>").filter { it.startsWith(args[4], ignoreCase = true) }
            }
        }

        if (args.size == 6) {
            if (args[0].equals("admin", ignoreCase = true) && args[1].equals("heat", ignoreCase = true)) {
                return listOf("<index>").filter { it.startsWith(args[5], ignoreCase = true) }
            }
        }
        
        return emptyList()
    }
    
    private fun sendAdminHelp(sender: CommandSender) {
        MessageUtil.send(sender, "<command.admin.help.reload>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.list>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.ban>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.fee>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.heat>", emptyMap(), false)
        MessageUtil.send(sender, "<command.admin.help.board>", emptyMap(), false)
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

    private fun notifyPlayer(uuid: java.util.UUID, messageKey: String, placeholders: Map<String, String>) {
        val player = Bukkit.getPlayer(uuid)
        if (player != null && player.isOnline) {
             MessageUtil.send(player, "<$messageKey>", placeholders)
        } else {
             val message = MessageUtil.format(messageKey, placeholders)
             plugin.storage.addNotification(uuid, message)
        }
    }
}
