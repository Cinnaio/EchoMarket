package com.github.cinnaio.echomarket.papi

import com.github.cinnaio.echomarket.EchoMarket
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.util.concurrent.TimeUnit

class EchoMarketExpansion(private val plugin: EchoMarket) : PlaceholderExpansion() {

    override fun getIdentifier(): String {
        return "echomarket"
    }

    override fun getAuthor(): String {
        return plugin.description.authors.joinToString(", ")
    }

    override fun getVersion(): String {
        return plugin.description.version
    }

    override fun persist(): Boolean {
        return true
    }
    
    override fun canRegister(): Boolean {
        return true
    }

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null

        // Variables:
        // volume_7d, volume_24h, volume_30d
        // transactions_7d, transactions_24h, transactions_30d
        // has_shop
        // shop_count

        when (params.lowercase()) {
            "has_shop" -> {
                val count = plugin.storage.getShopCount(player.uniqueId)
                return (count > 0).toString()
            }
            "shop_count" -> {
                return plugin.storage.getShopCount(player.uniqueId).toString()
            }
        }
        
        if (params.startsWith("volume_")) {
             val durationStr = params.substringAfter("volume_")
             val since = getSinceTime(durationStr) ?: return null
             val stats = plugin.storage.getTransactionStats(player.uniqueId, since)
             return String.format("%.2f", stats.totalVolume)
        }
        
        if (params.startsWith("transactions_")) {
             val durationStr = params.substringAfter("transactions_")
             val since = getSinceTime(durationStr) ?: return null
             val stats = plugin.storage.getTransactionStats(player.uniqueId, since)
             return stats.transactionCount.toString()
        }

        return null
    }
    
    private fun getSinceTime(durationStr: String): Long? {
        val now = System.currentTimeMillis()
        return when (durationStr) {
            "24h", "1d" -> now - TimeUnit.DAYS.toMillis(1)
            "7d", "1w" -> now - TimeUnit.DAYS.toMillis(7)
            "30d", "1m" -> now - TimeUnit.DAYS.toMillis(30)
            else -> null
        }
    }
}
