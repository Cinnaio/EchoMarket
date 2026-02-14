package com.github.cinnaio.echomarket.hook

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.regions.RegionContainer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

class WorldGuardHook {

    private var hasWorldGuard = false
    private var worldGuardPlugin: WorldGuardPlugin? = null

    init {
        val plugin = Bukkit.getPluginManager().getPlugin("WorldGuard")
        if (plugin != null && plugin is WorldGuardPlugin && plugin.isEnabled) {
            hasWorldGuard = true
            worldGuardPlugin = plugin
            Bukkit.getLogger().info("[EchoMarket] Hooked into WorldGuard.")
        }
    }

    /**
     * Checks if a player can build at the given location.
     * Returns true if WorldGuard is not present, or if the player is allowed to build.
     * Returns false if the player is denied building by the BUILD flag.
     */
    fun canBuild(player: Player, location: Location): Boolean {
        if (!hasWorldGuard) return true
        
        try {
            val localPlayer = worldGuardPlugin!!.wrapPlayer(player)
            val queryLocation = BukkitAdapter.adapt(location)
            val container = WorldGuard.getInstance().platform.regionContainer
            val query = container.createQuery()
            
            // testState returns true if permitted.
            return query.testState(queryLocation, localPlayer, Flags.BUILD)
        } catch (e: Exception) {
            e.printStackTrace()
            return true // Fail safe
        }
    }
}
