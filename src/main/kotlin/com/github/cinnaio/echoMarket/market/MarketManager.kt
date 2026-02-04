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
        if (targetBlock == null || !targetBlock.type.isSolid) {
            MessageUtil.send(player, "<market.create-failed>")
            return
        }
        
        val loc = targetBlock.location.add(0.5, 1.0, 0.5)
        // 调整朝向面向玩家
        loc.yaw = player.location.yaw + 180
        
        val shopName = name ?: "${player.name}的商店"
        val shopDesc = desc ?: "欢迎光临！"
        
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
                 MessageUtil.send(player, "<red>创建成功但在加载时出错。")
            }
        } else {
            MessageUtil.send(player, "<red>数据库错误，创建失败。")
        }
    }
    
    fun removeShop(player: Player, shopId: Int) {
        val shops = plugin.storage.getShops(player.uniqueId)
        val shop = shops.find { it.id == shopId }
        
        if (shop == null) {
            MessageUtil.send(player, "<red>你没有 ID 为 $shopId 的商店。")
            return
        }
        
        // Remove from storage
        if (plugin.storage.removeShop(shopId)) {
            // Remove NPC
            plugin.npcManager.removeNpc(shopId)
            MessageUtil.send(player, "<green>商店已删除。")
        } else {
            MessageUtil.send(player, "<red>删除失败。")
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

    fun updateName(player: Player, name: String) {
        val shop = resolveTargetShop(player)
        if (shop == null) {
            val shops = plugin.storage.getShops(player.uniqueId)
            if (shops.size > 1) {
                MessageUtil.send(player, "<red>你有多个商店，请看着你要修改的商店 NPC。")
            } else {
                MessageUtil.send(player, "<market.no-shop>")
            }
            return
        }
        plugin.storage.updateShopName(shop.id, name)
        plugin.npcManager.updateNpcName(shop.location, name)
        MessageUtil.send(player, "<market.update-name>", mapOf("name" to name))
    }

    fun updateDesc(player: Player, desc: String) {
        val shop = resolveTargetShop(player)
        if (shop == null) {
            val shops = plugin.storage.getShops(player.uniqueId)
            if (shops.size > 1) {
                MessageUtil.send(player, "<red>你有多个商店，请看着你要修改的商店 NPC。")
            } else {
                MessageUtil.send(player, "<market.no-shop>")
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
