package com.github.cinnaio.echomarket.config

import com.github.cinnaio.echomarket.EchoMarket
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: EchoMarket) {

    private lateinit var messagesConfig: FileConfiguration
    private val messagesFile = File(plugin.dataFolder, "messages.yml")

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false)
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile)
    }

    fun getConfig(): FileConfiguration {
        return plugin.config
    }

    fun getMessage(path: String): String {
        return messagesConfig.getString(path) ?: "<red>Missing message: $path"
    }
    
    fun reload() {
        load()
    }
}
