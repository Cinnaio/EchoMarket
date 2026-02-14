package com.github.cinnaio.echomarket.market

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.storage.ShopData
import com.github.cinnaio.echomarket.util.MessageUtil
import com.github.cinnaio.echomarket.gui.GuiManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class MarketManager(private val plugin: EchoMarket) {

    fun createShop(player: Player, name: String?, desc: String?) {
        // 检查商店数量限制
        val currentShops = plugin.storage.getShops(player.uniqueId)
        val limit = getMaxShops(player)
        
        if (currentShops.size >= limit) {
            MessageUtil.send(player, "<red>你已达到商店数量限制 ($limit)。")
            return
        }

        // 检查位置是否安全 (简单检查：必须是空气)
        val targetBlock = player.getTargetBlockExact(5)
        if (targetBlock == null) {
            MessageUtil.send(player, "<market.create-failed>")
            return
        }

        val loc = targetBlock.location.add(0.5, 1.0, 0.5)

        // WorldGuard Check
        if (!player.hasPermission("market.admin") && !plugin.worldGuardHook.canBuild(player, loc)) {
            MessageUtil.send(player, "<market.create-denied-region>")
            return
        }
        
        // 调整朝向面向玩家
        loc.yaw = player.location.yaw + 180
        
        val shopName = name ?: plugin.configManager.getMessage("market.default-shop-name").replace("{player}", player.name)
        val shopDesc = desc ?: plugin.configManager.getMessage("market.default-shop-desc")
        
        if (plugin.storage.createShop(player.uniqueId, player.name, loc, shopName, shopDesc)) {
            // Find the newly created shop (likely the last one or by name/loc)
            // Ideally storage.createShop should return the ID, but it returns Boolean.
            // We can fetch shops and find the one with matching location
            val shops = plugin.storage.getShops(player.uniqueId)
            val shop = shops.find { 
                it.location.world == loc.world && 
                it.location.distanceSquared(loc) < 0.1 
            }
            
            if (shop != null) {
                plugin.npcManager.spawnNpc(loc, shopName, player.uniqueId, shop.id)
                MessageUtil.send(player, "<market.created>", mapOf("name" to shopName))
            } else {
                 MessageUtil.send(player, "<market.create-error-loading>")
            }
        } else {
            MessageUtil.send(player, "<market.create-error-db>")
        }
    }
    
    fun removeShop(player: Player, shopId: Int) {
        val shops = plugin.storage.getShops(player.uniqueId)
        val shop = shops.find { it.id == shopId }
        
        if (shop == null) {
            MessageUtil.send(player, "<market.no-shop-id>", mapOf("id" to shopId.toString()))
            return
        }
        
        // Remove from storage
        if (plugin.storage.removeShop(shopId)) {
            // Remove NPC
            plugin.npcManager.removeNpc(shopId)
            MessageUtil.send(player, "<market.shop-removed>")
        } else {
            MessageUtil.send(player, "<market.remove-failed>")
        }
    }
    
    private fun getMaxShops(player: Player): Int {
        if (player.hasPermission("market.shops.limit.unlimited") || player.isOp) return Int.MAX_VALUE
        for (i in 20 downTo 1) {
            if (player.hasPermission("market.shops.limit.$i")) return i
        }
        return 1
    }

    fun openShop(player: Player, shopId: Int) {
        // 距离检查
        val shops = plugin.storage.getAllShops() // TODO: 优化，不要每次全查
        val shop = shops.find { it.id == shopId } ?: return
        
        val distanceLimit = plugin.config.getDouble("market.distance-limit", 2.0)
        if (player.location.distance(shop.location) > distanceLimit) {
            MessageUtil.send(player, "<market.too-far>")
            return
        }
        
        plugin.guiManager.openShopGui(player, shop)
    }

    fun openMarket(player: Player) {
        plugin.guiManager.openMarketList(player)
    }

    fun resolveTargetShop(player: Player): ShopData? {
        // 1. Try looked at NPC
        val targetBlock = player.getTargetBlockExact(5)
        if (targetBlock != null) {
            val shopId = plugin.npcManager.getShopIdAt(targetBlock.location, 1.5)
            if (shopId != null) {
                // We need to fetch this shop efficiently. 
                // plugin.storage.getShops(player.uniqueId) might be cached or fast enough.
                val shop = plugin.storage.getShops(player.uniqueId).find { it.id == shopId }
                if (shop != null) return shop
            }
        }
        
        // 2. Try shops near the player (within 2 blocks)
        val playerLoc = player.location
        val nearbyShops = plugin.storage.getShops(player.uniqueId).filter { 
            it.location.world == playerLoc.world && 
            it.location.distanceSquared(playerLoc) <= 4.0 // 2 blocks squared
        }
        if (nearbyShops.isNotEmpty()) {
            // Return the closest one
            return nearbyShops.minByOrNull { it.location.distanceSquared(playerLoc) }
        }

        // 3. Try single shop
        val shops = plugin.storage.getShops(player.uniqueId)
        if (shops.size == 1) return shops[0]
        
        return null
    }

    fun updateName(player: Player, name: String, targetIndex: Int? = null) {
        val shop = if (targetIndex != null) {
            val shops = plugin.storage.getShops(player.uniqueId)
            shops.find { it.index == targetIndex }
        } else {
            resolveTargetShop(player)
        }

        if (shop == null) {
            if (targetIndex != null) {
                 MessageUtil.send(player, "<market.not-found-index>", mapOf("index" to targetIndex.toString()))
            } else {
                val shops = plugin.storage.getShops(player.uniqueId)
                if (shops.size > 1) {
                    MessageUtil.send(player, "<market.multiple-shops-target>")
                } else {
                    MessageUtil.send(player, "<market.no-shop>")
                }
            }
            return
        }
        plugin.storage.updateShopName(shop.id, name)
        plugin.npcManager.updateNpcName(shop.id, name)
        MessageUtil.send(player, "<market.update-name>", mapOf("name" to name))
    }

    fun updateDesc(player: Player, desc: String, targetIndex: Int? = null) {
        val shop = if (targetIndex != null) {
            val shops = plugin.storage.getShops(player.uniqueId)
            shops.find { it.index == targetIndex }
        } else {
            resolveTargetShop(player)
        }

        if (shop == null) {
             if (targetIndex != null) {
                 MessageUtil.send(player, "<market.not-found-index>", mapOf("index" to targetIndex.toString()))
            } else {
                val shops = plugin.storage.getShops(player.uniqueId)
                if (shops.size > 1) {
                    MessageUtil.send(player, "<market.multiple-shops-target>")
                } else {
                    MessageUtil.send(player, "<market.no-shop>")
                }
            }
            return
        }
        plugin.storage.updateShopDesc(shop.id, desc)
        MessageUtil.send(player, "<market.update-desc>")
    }

    fun removeItem(player: Player, itemHash: String) {
        val shop = plugin.storage.getShop(player.uniqueId) ?: return
        
        // 查找该 Hash 的所有物品
        val items = plugin.storage.getItems(shop.id).filter { it.itemHash == itemHash }
        if (items.isEmpty()) return
        
        // 计算服务费
        val feeType = plugin.config.getString("market.fee-type", "fixed")
        val feeValue = plugin.config.getDouble("market.fee-value", 10.0)
        
        var totalFee = 0.0
        
        // 依次下架
        items.forEach { item ->
             val fee = if (feeType == "percent") {
                 // Fee is based on total value (stock * price) * percentage
                 val totalValue = item.stock * item.price
                 totalValue * feeValue / 100.0
             } else {
                 feeValue // Fixed fee (per stack/operation)
             }
             totalFee += fee
        }
        
        if (!EchoMarket.economy.has(player, totalFee)) {
            MessageUtil.send(player, "<market.insufficient-funds>")
            return
        }
        
        EchoMarket.economy.withdrawPlayer(player, totalFee)
        
        items.forEach { item ->
            val stack = item.itemStack.clone()
            stack.amount = item.stock
            player.inventory.addItem(stack).forEach { (_, leftover) ->
                player.world.dropItem(player.location, leftover)
            }
            plugin.storage.removeItem(item.id)
        }
        
        MessageUtil.send(player, "<market.item-removed>", mapOf("fee" to totalFee.toString()))
        openShopGui(player, shop)
    }
    
    private fun openShopGui(player: Player, shop: ShopData) {
        plugin.guiManager.openShopGui(player, shop)
    }
}
