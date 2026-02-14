package com.github.cinnaio.echomarket.listener

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.util.MessageUtil
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerListener(private val plugin: EchoMarket) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val notifications = plugin.storage.getNotifications(player.uniqueId)
        
        if (notifications.isNotEmpty()) {
            notifications.forEach { message ->
                // Send notification with prefix
                MessageUtil.send(player, message, emptyMap(), true)
            }
            plugin.storage.deleteNotifications(player.uniqueId)
        }
    }
}
