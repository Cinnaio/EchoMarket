package com.github.cinnaio.echomarket

import com.github.cinnaio.echomarket.board.BoardManager
import com.github.cinnaio.echomarket.command.MarketCommand
import com.github.cinnaio.echomarket.config.ConfigManager
import com.github.cinnaio.echomarket.gui.GuiManager
import com.github.cinnaio.echomarket.market.MarketManager
import com.github.cinnaio.echomarket.npc.NpcManager
import com.github.cinnaio.echomarket.storage.Storage
import com.github.cinnaio.echomarket.storage.StorageImpl
import com.github.cinnaio.echomarket.util.MessageUtil
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class EchoMarket : JavaPlugin() {

    lateinit var configManager: ConfigManager
    lateinit var storage: Storage
    lateinit var npcManager: NpcManager
    lateinit var guiManager: GuiManager
    lateinit var marketManager: MarketManager
    lateinit var boardManager: BoardManager

    companion object {
        lateinit var instance: EchoMarket
        lateinit var economy: Economy
        
        fun hasEconomy(): Boolean {
            return ::economy.isInitialized
        }
    }

    override fun onEnable() {
        instance = this
        
        // Load Config
        configManager = ConfigManager(this)
        configManager.load()
        
        // Setup Economy
        if (!setupEconomy()) {
            logger.severe(String.format("[%s] - Disabled due to no Vault dependency found!", name))
            server.pluginManager.disablePlugin(this)
            return
        }
        
        // Initialize Storage
        try {
            storage = StorageImpl(this)
            storage.init()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to initialize storage", e)
            server.pluginManager.disablePlugin(this)
            return
        }
        
        // Managers
        npcManager = NpcManager(this)
        guiManager = GuiManager(this)
        marketManager = MarketManager(this)
        boardManager = BoardManager(this)
        
        // Listeners
        server.pluginManager.registerEvents(guiManager, this)
        
        // Commands
        getCommand("market")?.setExecutor(MarketCommand(this))
        
        // Utils
        MessageUtil.prefix = configManager.getMessage("prefix")
        
        // Reload Npc Names/Scan (Optional, if we want to ensure NPCs exist)
        // Load NPCs from storage
        npcManager.loadNpcs()
        
        // A task to validate NPCs could be useful, but let's stick to basic requirements first.
    }

    override fun onDisable() {
        if (::storage.isInitialized) {
            storage.close()
        }
    }

    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp: RegisteredServiceProvider<Economy>? = server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            return false
        }
        economy = rsp.provider
        return true
    }
}
