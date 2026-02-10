package com.github.cinnaio.echomarket.gui

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.storage.ShopData
import com.github.cinnaio.echomarket.storage.ItemData
import com.github.cinnaio.echomarket.storage.BoardData
import com.github.cinnaio.echomarket.util.MessageUtil
import com.github.cinnaio.echomarket.util.ItemUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class GuiManager(private val plugin: EchoMarket) : Listener {

    private val shopIdKey = NamespacedKey(plugin, "shop_id")
    private val itemHashKey = NamespacedKey(plugin, "item_hash")
    private val boardIdKey = NamespacedKey(plugin, "board_id")
    
    // Confirmation keys
    private val confirmHashKey = NamespacedKey(plugin, "confirm_hash")
    private val confirmAmountKey = NamespacedKey(plugin, "confirm_amount")
    private val confirmShopKey = NamespacedKey(plugin, "confirm_shop_id")

    private val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
        itemMeta = itemMeta.apply { displayName(Component.empty()) }
    }
    private val backButton = ItemStack(Material.HONEY_BLOCK).apply {
        itemMeta = itemMeta.apply { 
            displayName(MessageUtil.get("gui.buy.back"))
            lore(listOf(MessageUtil.get("gui.buy.back-lore")))
        }
    }

    fun openMarketList(player: Player) {
        val shops = plugin.storage.getAllShops()
        val inv = Bukkit.createInventory(MarketListHolder(), 54, MessageUtil.get("gui.title", mapOf("page" to "1")))
        
        fillBorders(inv)
        
        val boardIcon = ItemStack(Material.OAK_SIGN)
        val boardMeta = boardIcon.itemMeta
        boardMeta.displayName(MessageUtil.get("board.title"))
        boardIcon.itemMeta = boardMeta
        inv.setItem(49, boardIcon)

        shops.take(45).forEach { shop ->
            val icon = ItemStack(Material.PLAYER_HEAD) // 简单起见不设皮肤了，或者可以异步设
            val meta = icon.itemMeta
            meta.displayName(MessageUtil.parse("<color:#6BFF95>${shop.name}"))
            val lore = mutableListOf<Component>()
            lore.add(MessageUtil.parse("<color:#A0A0A0>店主: <color:#E6E6E6>${shop.ownerName}"))
            lore.add(MessageUtil.parse("<color:#FFD479>${shop.description}"))
            meta.lore(lore)
            meta.persistentDataContainer.set(shopIdKey, PersistentDataType.INTEGER, shop.id)
            icon.itemMeta = meta
            inv.addItem(icon)
        }

        player.openInventory(inv)
    }

    fun openShopGui(player: Player, shop: ShopData) {
        val inv = Bukkit.createInventory(ShopHolder(shop), 54, MessageUtil.parse("<color:#E6E6E6>${shop.name}"))
        fillBorders(inv)
        inv.setItem(49, backButton)

        val items = plugin.storage.getItems(shop.id)
        val groupedItems = items.groupBy { it.itemHash }

        groupedItems.forEach { (hash, itemList) ->
            if (itemList.isEmpty()) return@forEach
            val templateItem = itemList.first().itemStack.clone()
            val meta = templateItem.itemMeta
            // Use existing lore or new list, but ensure we clear old market lore if any
            val lore = mutableListOf<Component>()
            
            val priceGroups = itemList.groupBy { it.price }.toSortedMap()
            var totalStock = 0
            var minPrice = Double.MAX_VALUE
            
            // Calculate totals first
            priceGroups.forEach { (price, list) ->
                val count = list.sumOf { it.stock }
                if (count > 0) {
                    totalStock += count
                    if (price < minPrice) minPrice = price
                }
            }
            
            if (totalStock > 0) {
                // Header
                lore.add(Component.empty())
                lore.add(MessageUtil.get("gui.item-price", mapOf("price" to minPrice.toString())))
                lore.add(MessageUtil.get("gui.item-stock", mapOf("stock" to totalStock.toString())))
                lore.add(Component.empty())
                
                // Price Bars
                priceGroups.forEach { (price, list) ->
                    val count = list.sumOf { it.stock }
                    if (count > 0) {
                        // Max width 10. Scale: count / totalStock
                        val filled = ((count.toDouble() / totalStock) * 10).toInt().coerceIn(1, 10)
                        val empty = 10 - filled
                        val barStr = "█".repeat(filled) + "▒".repeat(empty)
                        lore.add(MessageUtil.get("gui.stock-entry", mapOf(
                            "price" to price.toString(),
                            "bar" to barStr,
                            "amount" to count.toString()
                        )))
                    }
                }
                
                lore.add(Component.empty())
                
                // Footer
                if (shop.ownerUuid == player.uniqueId) {
                    lore.add(MessageUtil.get("gui.cancel.lore", mapOf("fee" to plugin.config.getDouble("market.fee-value", 3.0).toString())))
                } else {
                    lore.add(MessageUtil.get("gui.item-click"))
                }
                
                meta.lore(lore)
                meta.persistentDataContainer.set(itemHashKey, PersistentDataType.STRING, hash)
                templateItem.itemMeta = meta
                templateItem.amount = 1
                inv.addItem(templateItem)
            }
        }

        player.openInventory(inv)
    }
    
    fun openBoardGui(player: Player) {
        val messages = plugin.storage.getBoardMessages()
        val inv = Bukkit.createInventory(BoardHolder(), 54, MessageUtil.get("board.title"))
        fillBorders(inv)
        inv.setItem(49, backButton)
        
        // 发布按钮
        val postBtn = ItemStack(Material.WRITABLE_BOOK)
        val postMeta = postBtn.itemMeta
        postMeta.displayName(MessageUtil.get("board.post-btn"))
        postMeta.lore(listOf(MessageUtil.get("board.post-btn-lore")))
        postBtn.itemMeta = postMeta
        inv.setItem(4, postBtn) // Top center

        messages.take(45).forEach { msg ->
            val icon = ItemStack(Material.PAPER)
            val meta = icon.itemMeta
            meta.displayName(MessageUtil.parse("<color:#FFD479>留言 #${msg.id}"))
            val lore = mutableListOf<Component>()
            lore.add(MessageUtil.parse("<color:#E6E6E6>${msg.content}"))
            lore.add(MessageUtil.parse("<color:#A0A0A0>发布者: ${msg.ownerName}"))
            val timeLeft = (msg.expireAt - System.currentTimeMillis()) / 1000
            lore.add(MessageUtil.parse("<color:#FF6B6B>剩余时间: ${formatDuration(timeLeft)}"))
            if (msg.ownerUuid == player.uniqueId) {
                lore.add(MessageUtil.parse("<color:#6BFF95>点击续期"))
                meta.persistentDataContainer.set(boardIdKey, PersistentDataType.INTEGER, msg.id)
            }
            meta.lore(lore)
            icon.itemMeta = meta
            inv.addItem(icon)
        }
        
        player.openInventory(inv)
    }

    fun openConfirmationGui(player: Player, shop: ShopData, itemHash: String, itemStack: ItemStack, stock: Int, minPrice: Double) {
        val inv = Bukkit.createInventory(ConfirmationHolder(shop, itemHash, itemStack, stock, minPrice), 54, MessageUtil.get("gui.buy.title"))
        fillBorders(inv)
        
        // 13: Display Item
        val displayItem = itemStack.clone()
        val meta = displayItem.itemMeta
        val lore = meta.lore() ?: mutableListOf()
        lore.add(Component.empty())
        lore.add(MessageUtil.get("gui.buy.min-price", mapOf("price" to minPrice.toString())))
        lore.add(MessageUtil.get("gui.buy.total-stock", mapOf("stock" to stock.toString())))
        meta.lore(lore)
        displayItem.itemMeta = meta
        inv.setItem(13, displayItem)
        
        // Controls
        // Red panes (Decrease): 19 (-64), 20 (-10), 21 (-1)
        inv.setItem(19, createControlItem(Material.RED_STAINED_GLASS_PANE, -64))
        inv.setItem(20, createControlItem(Material.RED_STAINED_GLASS_PANE, -10))
        inv.setItem(21, createControlItem(Material.RED_STAINED_GLASS_PANE, -1))
        
        // Green panes (Increase): 23 (+1), 24 (+10), 25 (+64)
        inv.setItem(23, createControlItem(Material.LIME_STAINED_GLASS_PANE, 1))
        inv.setItem(24, createControlItem(Material.LIME_STAINED_GLASS_PANE, 10))
        inv.setItem(25, createControlItem(Material.LIME_STAINED_GLASS_PANE, 64))
        
        // Set to max: 31 (Diamond Block?)
        val maxBtn = ItemStack(Material.DIAMOND_BLOCK)
        val maxMeta = maxBtn.itemMeta
        maxMeta.displayName(MessageUtil.get("gui.buy.all"))
        maxMeta.lore(listOf(MessageUtil.get("gui.buy.all-lore", mapOf("stock" to stock.toString()))))
        maxBtn.itemMeta = maxMeta
        inv.setItem(31, maxBtn)
        
        // Confirm: 40 (Emerald Block)
        updateConfirmButton(inv, 1, minPrice)
        
        // Back: 49
        inv.setItem(49, backButton)
        
        player.openInventory(inv)
    }
    
    fun openAdminBanList(player: Player, page: Int = 1) {
        val blacklist = plugin.config.getStringList("market.blacklist")
        val inv = Bukkit.createInventory(AdminBanListHolder(page), 54, MessageUtil.get("gui.admin-ban-title", mapOf("page" to page.toString())))
        fillBorders(inv)
        inv.setItem(49, backButton)
        
        val startIndex = (page - 1) * 36
        val endIndex = Math.min(startIndex + 36, blacklist.size)
        
        for (i in startIndex until endIndex) {
            val entry = blacklist[i]
            val parts = entry.split("|")
            val hash = parts[0]
            
            var item: ItemStack
            if (parts.size > 1) {
                // 有存储 Base64 数据，还原物品
                try {
                    item = ItemUtil.deserializeItemStack(parts[1])
                } catch (e: Exception) {
                    item = ItemStack(Material.BARRIER)
                }
            } else {
                // 旧数据，只有 Hash
                item = ItemStack(Material.BARRIER)
            }
            
            val meta = item.itemMeta
            // 保留物品原名，并在 Lore 中添加 Hash 信息
            val lore = meta.lore() ?: mutableListOf()
            lore.add(Component.empty())
            lore.add(MessageUtil.get("gui.admin-ban-type-hash"))
            lore.add(MessageUtil.get("gui.admin-ban-hash", mapOf("hash" to hash)))
            meta.lore(lore)
            
            // Store hash for removal
            meta.persistentDataContainer.set(itemHashKey, PersistentDataType.STRING, hash)
            item.itemMeta = meta
            inv.addItem(item)
        }
        
        // Pagination
        if (page > 1) {
            val prev = ItemStack(Material.ARROW)
            val meta = prev.itemMeta
            meta.displayName(MessageUtil.get("gui.prev-page"))
            prev.itemMeta = meta
            inv.setItem(45, prev)
        }
        if (endIndex < blacklist.size) {
            val next = ItemStack(Material.ARROW)
            val meta = next.itemMeta
            meta.displayName(MessageUtil.get("gui.next-page"))
            next.itemMeta = meta
            inv.setItem(53, next)
        }
        
        player.openInventory(inv)
    }

    fun openAdminFeeList(player: Player, page: Int = 1) {
        val specialFees = plugin.config.getConfigurationSection("market.special-fees")
        val keys = specialFees?.getKeys(false)?.toList() ?: emptyList()
        
        val inv = Bukkit.createInventory(AdminFeeListHolder(page), 54, MessageUtil.get("gui.admin-fee-title", mapOf("page" to page.toString())))
        fillBorders(inv)
        inv.setItem(49, backButton)
        
        val startIndex = (page - 1) * 36
        val endIndex = Math.min(startIndex + 36, keys.size)
        
        for (i in startIndex until endIndex) {
            val hash = keys[i]
            val rate = specialFees!!.getDouble(hash)
            
            // Try to load item from special-fees-data
            val serialized = plugin.config.getString("market.special-fees-data.$hash")
            var item: ItemStack
            if (serialized != null) {
                try {
                    item = ItemUtil.deserializeItemStack(serialized)
                } catch (e: Exception) {
                    item = ItemStack(Material.GOLD_INGOT)
                }
            } else {
                item = ItemStack(Material.GOLD_INGOT)
            }
            
            val meta = item.itemMeta
            val itemName = if (serialized != null) ItemUtil.getDisplayName(item) else MessageUtil.get("gui.admin-fee-rate", mapOf("rate" to rate.toString())) // Fallback name
            
            // If it's a real item, we append the rate to the lore or display name
            // Let's keep the item name and add rate info in lore
            val lore = meta.lore() ?: mutableListOf()
            lore.add(Component.empty())
            lore.add(MessageUtil.get("gui.admin-fee-rate-lore", mapOf("rate" to rate.toString())))
            lore.add(MessageUtil.get("gui.admin-fee-hash", mapOf("hash" to hash)))
            meta.lore(lore)
            
            item.itemMeta = meta
            inv.addItem(item)
        }
        
        // Pagination
        if (page > 1) {
            val prev = ItemStack(Material.ARROW)
            val meta = prev.itemMeta
            meta.displayName(MessageUtil.get("gui.prev-page"))
            prev.itemMeta = meta
            inv.setItem(45, prev)
        }
        if (endIndex < keys.size) {
            val next = ItemStack(Material.ARROW)
            val meta = next.itemMeta
            meta.displayName(MessageUtil.get("gui.next-page"))
            next.itemMeta = meta
            inv.setItem(53, next)
        }
        
        player.openInventory(inv)
    }

    private fun createControlItem(mat: Material, amount: Int): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta
        val absAmount = Math.abs(amount)
        if (amount > 0) {
            meta.displayName(MessageUtil.get("gui.buy.increase", mapOf("amount" to absAmount.toString())))
            meta.lore(listOf(MessageUtil.get("gui.buy.increase-lore", mapOf("amount" to absAmount.toString()))))
        } else {
            meta.displayName(MessageUtil.get("gui.buy.decrease", mapOf("amount" to absAmount.toString())))
            meta.lore(listOf(MessageUtil.get("gui.buy.decrease-lore", mapOf("amount" to absAmount.toString()))))
        }
        item.itemMeta = meta
        return item
    }
    
    private fun updateConfirmButton(inv: Inventory, amount: Int, price: Double) {
        val item = ItemStack(Material.EMERALD_BLOCK)
        val meta = item.itemMeta
        meta.displayName(MessageUtil.get("gui.buy.confirm"))
        val total = amount * price
        meta.lore(listOf(
            MessageUtil.get("gui.buy.confirm-lore-quantity", mapOf("quantity" to amount.toString())),
            MessageUtil.get("gui.buy.confirm-lore-cost", mapOf("cost" to total.toString())),
            MessageUtil.get("gui.buy.confirm-lore-action")
        ))
        
        // Store amount in persistent data for easy retrieval
        meta.persistentDataContainer.set(confirmAmountKey, PersistentDataType.INTEGER, amount)
        item.itemMeta = meta
        inv.setItem(40, item)
    }

    private fun fillBorders(inv: Inventory) {
        val size = inv.size
        for (i in 0 until 9) inv.setItem(i, filler)
        for (i in size - 9 until size) inv.setItem(i, filler)
        for (i in 0 until size step 9) inv.setItem(i, filler)
        for (i in 8 until size step 9) inv.setItem(i, filler)
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return plugin.configManager.getMessage("time.expired")
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        
        val sb = StringBuilder()
        if (days > 0) sb.append("$days${plugin.configManager.getMessage("time.days")}")
        if (hours > 0) sb.append("$hours${plugin.configManager.getMessage("time.hours")}")
        if (minutes > 0) sb.append("$minutes${plugin.configManager.getMessage("time.minutes")}")
        if (sb.isEmpty()) sb.append("$seconds${plugin.configManager.getMessage("time.seconds")}")
        
        return sb.toString()
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder ?: return
        if (holder !is MarketHolder) return
        event.isCancelled = true
        
        val player = event.whoClicked as Player
        val item = event.currentItem ?: return
        if (item.type == Material.AIR || item.type == Material.GRAY_STAINED_GLASS_PANE) return

        if (holder is MarketListHolder) {
            if (item.type == Material.OAK_SIGN) {
                openBoardGui(player)
            } else if (item.hasItemMeta() && item.itemMeta.persistentDataContainer.has(shopIdKey, PersistentDataType.INTEGER)) {
                val shopId = item.itemMeta.persistentDataContainer.get(shopIdKey, PersistentDataType.INTEGER)!!
                // Find shop data
                val shop = plugin.storage.getAllShops().find { it.id == shopId }
                if (shop != null) {
                    plugin.marketManager.openShop(player, shopId) // 使用 MarketManager 包含距离检查
                }
            }
        } else if (holder is ShopHolder) {
            if (item == backButton) {
                openMarketList(player)
                return
            }
            
            // 购买/下架逻辑
            if (item.hasItemMeta() && item.itemMeta.persistentDataContainer.has(itemHashKey, PersistentDataType.STRING)) {
                val hash = item.itemMeta.persistentDataContainer.get(itemHashKey, PersistentDataType.STRING)!!
                
                if (holder.shop.ownerUuid == player.uniqueId) {
                    if (event.isRightClick) {
                        plugin.marketManager.removeItem(player, hash)
                    } else {
                        MessageUtil.send(player, "<market.cannot-buy-own-action>")
                    }
                } else {
                    // Open Confirmation GUI instead of direct buy
                    // Need to find stock and min price
                    val items = plugin.storage.getItems(holder.shop.id).filter { it.itemHash == hash }
                    if (items.isNotEmpty()) {
                        val stock = items.sumOf { it.stock }
                        val minPrice = items.minOf { it.price }
                        val template = items.first().itemStack
                        openConfirmationGui(player, holder.shop, hash, template, stock, minPrice)
                    }
                }
            }
        } else if (holder is BoardHolder) {
             if (item == backButton) {
                openMarketList(player)
                return
            }
            
            if (item.type == Material.WRITABLE_BOOK) {
                player.closeInventory()
                MessageUtil.send(player, "<board.enter-message>")
                // 这里需要一个简单的聊天监听器或者 Conversation API
                // 为了简单，我们注册一个临时的 ChatListener
                val listener = ChatInputListener(plugin, player.uniqueId)
                plugin.server.pluginManager.registerEvents(listener, plugin)
                
                // Add timeout (60 seconds)
                player.scheduler.runDelayed(plugin, { task ->
                    if (listener.isActive) {
                        listener.unregister()
                        MessageUtil.send(player, "<board.timeout>")
                    }
                }, null, 1200L) // 60 * 20 ticks
            } else if (item.hasItemMeta() && item.itemMeta.persistentDataContainer.has(boardIdKey, PersistentDataType.INTEGER)) {
                val boardId = item.itemMeta.persistentDataContainer.get(boardIdKey, PersistentDataType.INTEGER)!!
                plugin.boardManager.renewMessage(player, boardId)
            }
        } else if (holder is ConfirmationHolder) {
            if (item == backButton) {
                plugin.marketManager.openShop(player, holder.shop.id)
                return
            }
            
            // Handle Quantity Change
            var currentAmount = event.inventory.getItem(40)?.itemMeta?.persistentDataContainer?.get(confirmAmountKey, PersistentDataType.INTEGER) ?: 1
            var newAmount = currentAmount
            
            if (item.type == Material.RED_STAINED_GLASS_PANE || item.type == Material.LIME_STAINED_GLASS_PANE) {
                // Extract amount from name or lore? Or just hardcode based on slot
                when (event.slot) {
                    19 -> newAmount -= 64
                    20 -> newAmount -= 10
                    21 -> newAmount -= 1
                    23 -> newAmount += 1
                    24 -> newAmount += 10
                    25 -> newAmount += 64
                }
            } else if (item.type == Material.DIAMOND_BLOCK) {
                newAmount = holder.maxStock
            } else if (item.type == Material.EMERALD_BLOCK) {
                // Confirm Buy
                handleBuy(player, holder.shop, holder.itemHash, currentAmount)
                return
            }
            
            // Clamp and Update
            if (newAmount < 1) newAmount = 1
            if (newAmount > holder.maxStock) newAmount = holder.maxStock
            
            if (newAmount != currentAmount) {
                updateConfirmButton(event.inventory, newAmount, holder.price)
            }
        } else if (holder is AdminBanListHolder) {
            if (item == backButton) {
                player.closeInventory()
                return
            }
            if (item.type == Material.ARROW) {
                if (event.slot == 45) {
                    openAdminBanList(player, holder.page - 1)
                } else if (event.slot == 53) {
                    openAdminBanList(player, holder.page + 1)
                }
            } else {
                 // Check if it's a valid item with hash
                 if (item.hasItemMeta() && item.itemMeta.persistentDataContainer.has(itemHashKey, PersistentDataType.STRING)) {
                     // Click to remove from blacklist
                     val hash = item.itemMeta.persistentDataContainer.get(itemHashKey, PersistentDataType.STRING)
                     if (hash != null) {
                         val blacklist = plugin.config.getStringList("market.blacklist").toMutableList()
                         val entry = blacklist.find { it.startsWith(hash) }
                         if (entry != null) {
                             blacklist.remove(entry)
                             plugin.config.set("market.blacklist", blacklist)
                             plugin.saveConfig()
                             MessageUtil.send(player, "<command.admin.ban.unbanned>")
                             openAdminBanList(player, holder.page) // Refresh
                         }
                     }
                 }
            }
        } else if (holder is AdminFeeListHolder) {
            if (item == backButton) {
                player.closeInventory()
                return
            }
            if (item.type == Material.ARROW) {
                if (event.slot == 45) {
                    openAdminFeeList(player, holder.page - 1)
                } else if (event.slot == 53) {
                    openAdminFeeList(player, holder.page + 1)
                }
            }
        }
    }
    
    private fun handleBuy(player: Player, shop: ShopData, itemHash: String, amount: Int) {
        if (shop.ownerUuid == player.uniqueId) {
            MessageUtil.send(player, "<market.cannot-buy-own>")
            return
        }
        
        // 1. 查找该 Hash 的所有库存
        val items = plugin.storage.getItems(shop.id).filter { it.itemHash == itemHash }.sortedBy { it.price }
        
        if (items.isEmpty()) {
            MessageUtil.send(player, "<market.insufficient-stock>")
            openShopGui(player, shop) // 刷新
            return
        }
        
        // 2. 找到足够的库存 (从最低价开始凑)
        var remainingNeeded = amount
        var totalPrice = 0.0
        val toBuy = mutableListOf<Pair<ItemData, Int>>() // Item, Amount from this stack
        
        for (item in items) {
            if (remainingNeeded <= 0) break
            val take = Math.min(remainingNeeded, item.stock)
            toBuy.add(item to take)
            totalPrice += take * item.price
            remainingNeeded -= take
        }
        
        if (remainingNeeded > 0) {
             MessageUtil.send(player, "<market.insufficient-stock>") // Should not happen if GUI limit is correct
             return
        }
        
        // 3. 检查钱
        if (!EchoMarket.economy.has(player, totalPrice)) {
             MessageUtil.send(player, "<market.insufficient-funds>")
             return
        }
        
        // 4. 检查背包空间 (简单检查 - 可能不准，如果买很多)
        // 更好的方法是模拟添加。
        // 这里简化：只有当背包有足够空位时才允许。
        // 或者直接给，给不下掉落。
        
        // 5. 执行交易
        // 计算交易手续费
        val transactionFeePercent = plugin.config.getDouble("market.transaction-fee", 3.0)
        // 检查是否有特殊费率
        val specialFees = plugin.config.getConfigurationSection("market.special-fees")
        val feeRate = if (specialFees != null && specialFees.contains(itemHash)) {
            specialFees.getDouble(itemHash) / 100.0
        } else {
            transactionFeePercent / 100.0
        }
        
        val feeAmount = totalPrice * feeRate
        val sellerIncome = totalPrice - feeAmount
        
        // Transaction check success
        val withdrawResult = EchoMarket.economy.withdrawPlayer(player, totalPrice)
        if (!withdrawResult.transactionSuccess()) {
             MessageUtil.send(player, "<market.buy-fail-chat>", mapOf("message" to (withdrawResult.errorMessage ?: "Unknown error")))
             return
        }
        
        EchoMarket.economy.depositPlayer(Bukkit.getOfflinePlayer(shop.ownerUuid), sellerIncome)
        
        var anyDropped = false
        
        toBuy.forEach { (item, count) ->
            // 给物品
            val stack = item.itemStack.clone()
            stack.amount = count
            val leftovers = player.inventory.addItem(stack)
            if (leftovers.isNotEmpty()) {
                anyDropped = true
                leftovers.forEach { (_, leftover) ->
                    player.world.dropItem(player.location, leftover)
                }
            }
            
            // 扣库存
            if (item.stock > count) {
                plugin.storage.updateItemStock(item.id, item.stock - count)
            } else {
                plugin.storage.removeItem(item.id)
            }
            
            // 记录日志
            plugin.storage.logTransaction(player.uniqueId, shop.ownerUuid, itemHash, count, item.price)
        }
        
        // MessageUtil.send(player, "<market.item-bought>", mapOf("price" to totalPrice.toString()))
        val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        val templateItem = items.first().itemStack
        
        // 优化物品名称显示：如果有 DisplayName 则使用，否则使用翻译键
        val itemName = ItemUtil.getDisplayName(templateItem)

        MessageUtil.send(player, "<market.buy-bought-chat>", mapOf(
            "amount" to amount.toString(), 
            "item" to itemName
        ))
        MessageUtil.send(player, "<market.buy-cost-chat>", mapOf("cost" to totalPrice.toString()))
        
        // 通知卖家
        val seller = Bukkit.getPlayer(shop.ownerUuid)
        if (seller != null && seller.isOnline) {
             MessageUtil.send(seller, "<market.seller-sold-notification>", mapOf(
                 "amount" to amount.toString(),
                 "item" to itemName,
                 "price" to sellerIncome.toString()
             ))
        }
        
        if (anyDropped) {
            MessageUtil.send(player, "<market.buy-inventory-full>")
        }
        
        // 刷新界面 -> Return to Shop
        openShopGui(player, shop)
    }
}

// Helper class for chat input
class ChatInputListener(private val plugin: EchoMarket, private val playerUuid: UUID) : Listener {
    
    var isActive = true
        private set

    fun unregister() {
        if (!isActive) return
        isActive = false
        org.bukkit.event.HandlerList.unregisterAll(this)
    }

    @Suppress("DEPRECATION")
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun onChat(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        if (!isActive) return
        if (event.player.uniqueId == playerUuid) {
            event.isCancelled = true
            val content = event.message
            unregister() // Unregister immediately
            
            // Switch to main thread to modify world/gui
            // Use Folia-compatible scheduler
            event.player.scheduler.run(plugin, { _ ->
                plugin.boardManager.postMessage(event.player, content)
            }, null)
        }
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        if (event.player.uniqueId == playerUuid) {
            unregister()
        }
    }
}

interface MarketHolder : InventoryHolder {
    override fun getInventory(): Inventory = throw UnsupportedOperationException()
}
class MarketListHolder : MarketHolder
class ShopHolder(val shop: ShopData) : MarketHolder
class BoardHolder : MarketHolder
class AdminBanListHolder(val page: Int) : MarketHolder
class AdminFeeListHolder(val page: Int) : MarketHolder
class ConfirmationHolder(
    val shop: ShopData, 
    val itemHash: String, 
    val itemStack: ItemStack,
    val maxStock: Int,
    val price: Double
) : MarketHolder

