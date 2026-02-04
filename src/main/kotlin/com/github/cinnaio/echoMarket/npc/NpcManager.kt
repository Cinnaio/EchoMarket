package com.github.cinnaio.echomarket.npc

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.util.MessageUtil
import de.oliver.fancynpcs.api.FancyNpcsPlugin
import de.oliver.fancynpcs.api.NpcData
import de.oliver.fancynpcs.api.events.NpcInteractEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.UUID

class NpcManager(private val plugin: EchoMarket) : Listener {

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun loadNpcs() {
        // Ensure all shops have NPCs
        plugin.storage.getAllShops().forEach { shop ->
            spawnNpc(shop.location, shop.name, shop.ownerUuid, shop.id)
        }
    }

    fun spawnNpc(location: Location, name: String, ownerUuid: UUID, shopId: Int) {
        val manager = FancyNpcsPlugin.get().npcManager
        
        // Check if exists using prefix
        // Since we changed naming convention, we need to find if any NPC matches "shop_<id>_*"
        val existingNpc = manager.getAllNpcs().find { it.data.name.startsWith("shop_${shopId}_") }
        
        if (existingNpc != null) {
            // Update location and name if needed
            existingNpc.data.location = location
            existingNpc.data.displayName = name
            existingNpc.updateForAll()
            return
        }
        
        // Create new NPC with unique ID
        val uniqueId = UUID.randomUUID().toString().replace("-", "").take(16)
        val npcName = "shop_${shopId}_${uniqueId}"
        
        val data = NpcData(npcName, ownerUuid, location)
        data.displayName = name
        // Set skin to owner's name by default
        data.setSkin(Bukkit.getOfflinePlayer(ownerUuid).name ?: "Alex")
        
        val npc = FancyNpcsPlugin.get().npcAdapter.apply(data)
        manager.registerNpc(npc)
        npc.create()
        npc.spawnForAll()
    }

    fun removeNpc(shopId: Int) {
        val manager = FancyNpcsPlugin.get().npcManager
        val npc = manager.getAllNpcs().find { it.data.name.startsWith("shop_${shopId}_") } ?: return
        
        npc.removeForAll()
        manager.removeNpc(npc)
    }
    
    fun getShopIdAt(location: Location, threshold: Double = 2.0): Int? {
        val manager = FancyNpcsPlugin.get().npcManager
        val npc = manager.getAllNpcs().find { 
            it.data.location.world == location.world && 
            it.data.location.distanceSquared(location) < threshold * threshold &&
            it.data.name.startsWith("shop_")
        }
        // Name format: shop_<id>_<uuid>
        val parts = npc?.data?.name?.split("_")
        if (parts != null && parts.size >= 2) {
            return parts[1].toIntOrNull()
        }
        return null
    }
    
    fun updateNpcName(location: Location, name: String) {
        val shopId = getShopIdAt(location) ?: return
        // Need to find by prefix again
        val manager = FancyNpcsPlugin.get().npcManager
        val npc = manager.getAllNpcs().find { it.data.name.startsWith("shop_${shopId}_") } ?: return
        
        npc.data.displayName = name
        npc.updateForAll()
    }
    
    fun updateNpcSkin(player: Player, skinName: String) {
        val shop = plugin.storage.getShop(player.uniqueId)
        if (shop == null) {
             MessageUtil.send(player, "<market.no-shop>")
             return
        }
        
        val manager = FancyNpcsPlugin.get().npcManager
        val npc = manager.getAllNpcs().find { it.data.name.startsWith("shop_${shop.id}_") }
        
        if (npc == null) {
            MessageUtil.send(player, "<color:#FF6B6B>未找到你的商店 NPC。")
            return
        }
        
        npc.data.setSkin(skinName)
        npc.updateForAll()
        MessageUtil.send(player, "<color:#6BFF95>NPC 皮肤已更新。")
    }
    
    @EventHandler
    fun onNpcInteract(event: NpcInteractEvent) {
        val npc = event.npc
        if (!npc.data.name.startsWith("shop_")) return
        
        // Name format: shop_<id>_<uuid>
        val parts = npc.data.name.split("_")
        if (parts.size < 2) return
        
        val shopId = parts[1].toIntOrNull() ?: return
        
        // Open shop
        plugin.marketManager.openShop(event.player, shopId)
    }
}
