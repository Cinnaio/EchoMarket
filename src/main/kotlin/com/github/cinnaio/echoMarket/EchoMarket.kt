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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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
        
        // Print Startup Info
        printStartupHeader()
        
        // Load Config
        configManager = ConfigManager(this)
        configManager.load()
        
        // Setup Economy
        if (!setupEconomy()) {
            logger.severe(String.format("[%s] - Disabled due to no Vault dependency found!", name))
            server.pluginManager.disablePlugin(this)
            return
        }
        
        // Check FancyNpcs
        if (!server.pluginManager.isPluginEnabled("FancyNpcs")) {
            logger.severe(String.format("[%s] - Disabled due to no FancyNpcs dependency found!", name))
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
        
        // PlaceholderAPI Support
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            com.github.cinnaio.echomarket.papi.EchoMarketExpansion(this).register()
        }
        
        // Reload Npc Names/Scan (Optional, if we want to ensure NPCs exist)
        // Load NPCs from storage
        npcManager.loadNpcs()
        
        printStartupFooter()
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
    
    private fun printStartupHeader() {
        val mm = MiniMessage.miniMessage()
        val sender = server.consoleSender
        
        sender.sendMessage(Component.empty())
        sender.sendMessage(mm.deserialize("<gradient:#55ffff:#00aa00><bold>EchoMarket</bold></gradient> <gray>v${description.version}"))
        sender.sendMessage(mm.deserialize("<gray>Running on <white>${server.version}"))
        sender.sendMessage(Component.empty())
        
        // Check Dependencies
        if (server.pluginManager.isPluginEnabled("Vault")) {
            // We can't access 'economy' yet as it is setup later, but we can check registration
            val rsp = server.servicesManager.getRegistration(Economy::class.java)
            val ecoName = rsp?.provider?.name ?: "Unknown"
            sender.sendMessage(mm.deserialize("<aqua>Vault <green>was found - Enabling capabilities. <green>Economy: <light_purple>$ecoName"))
        } else {
             sender.sendMessage(mm.deserialize("<red>Vault <gray>was not found - Plugin will be disabled."))
        }
        
        if (server.pluginManager.isPluginEnabled("FancyNpcs")) {
             sender.sendMessage(mm.deserialize("<aqua>FancyNpcs <green>was found - Enabling capabilities."))
        } else {
             sender.sendMessage(mm.deserialize("<red>FancyNpcs <gray>was not found - Plugin will be disabled."))
        }

        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
             sender.sendMessage(mm.deserialize("<aqua>PlaceholderAPI <green>was found - Enabling capabilities."))
        }
        
        sender.sendMessage(Component.empty())
    }
    
    private fun printStartupFooter() {
        val mm = MiniMessage.miniMessage()
        server.consoleSender.sendMessage(mm.deserialize("<green>EchoMarket loaded successfully!"))
        server.consoleSender.sendMessage(Component.empty())
    }
}
