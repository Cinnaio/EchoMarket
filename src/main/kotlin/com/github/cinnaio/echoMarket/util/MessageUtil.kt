package com.github.cinnaio.echomarket.util

import com.github.cinnaio.echomarket.EchoMarket
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender

object MessageUtil {
    private val mm = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    var prefix: String = ""

    // Define colors
    val COLOR_MAIN = "<color:#E6E6E6>"
    val COLOR_SEC = "<color:#A0A0A0>"
    val COLOR_ACCENT = "<color:#FFD479>"
    val COLOR_SUCCESS = "<color:#6BFF95>"
    val COLOR_ERROR = "<color:#FF6B6B>"

    fun send(sender: CommandSender, keyOrMessage: String, replacements: Map<String, String> = emptyMap(), usePrefix: Boolean = true) {
        var message = keyOrMessage
        if (message.startsWith("<") && message.endsWith(">") && !message.contains(" ")) {
            val key = message.substring(1, message.length - 1)
            val configMsg = EchoMarket.instance.configManager.getMessage(key)
            if (!configMsg.startsWith("<red>Missing message")) {
                message = configMsg
            }
        }
        
        var processedMessage = message
        replacements.forEach { (key, value) ->
            processedMessage = processedMessage.replace("<$key>", value)
            processedMessage = processedMessage.replace("{$key}", value)
        }
        
        // Handle Legacy & Hex
        processedMessage = convertLegacyColors(processedMessage)
        
        val finalMessage = if (usePrefix) "$prefix$processedMessage" else processedMessage
        sender.sendMessage(mm.deserialize(finalMessage))
    }
    
    fun get(key: String, replacements: Map<String, String> = emptyMap()): Component {
        val configMsg = EchoMarket.instance.configManager.getMessage(key)
        
        var processedMessage = configMsg
        replacements.forEach { (k, v) ->
            processedMessage = processedMessage.replace("<$k>", v)
            processedMessage = processedMessage.replace("{$k}", v)
        }
        
        return parse(processedMessage)
    }
    
    fun parse(message: String): Component {
        return mm.deserialize("<!italic>" + convertLegacyColors(message))
    }
    
    private fun convertLegacyColors(message: String): String {
        // Convert legacy ampersand codes to MiniMessage tags or keep them if we use a serializer that supports them?
        // MiniMessage doesn't support legacy by default.
        // But we want to support input like "&cHello" or "&#RRGGBB".
        // The easiest way is to serialize legacy first, but that returns a Component, then we need to merge with MiniMessage?
        // Actually, if we want to support MIXED, we should probably just replace common legacy codes with MiniMessage tags manually
        // OR render legacy part to string.
        
        // Simple replacement for standard colors
        var result = message
            .replace("&0", "<black>")
            .replace("&1", "<dark_blue>")
            .replace("&2", "<dark_green>")
            .replace("&3", "<dark_aqua>")
            .replace("&4", "<dark_red>")
            .replace("&5", "<dark_purple>")
            .replace("&6", "<gold>")
            .replace("&7", "<gray>")
            .replace("&8", "<dark_gray>")
            .replace("&9", "<blue>")
            .replace("&a", "<green>")
            .replace("&b", "<aqua>")
            .replace("&c", "<red>")
            .replace("&d", "<light_purple>")
            .replace("&e", "<yellow>")
            .replace("&f", "<white>")
            .replace("&l", "<bold>")
            .replace("&o", "<italic>")
            .replace("&n", "<underlined>")
            .replace("&m", "<strikethrough>")
            .replace("&k", "<obfuscated>")
            .replace("&r", "<reset>")
            
        // Hex support: &#RRGGBB -> <color:#RRGGBB>
        val hexRegex = Regex("&#([0-9a-fA-F]{6})")
        result = hexRegex.replace(result) { match ->
            "<color:#${match.groupValues[1]}>"
        }
        // {#RRGGBB} -> <color:#RRGGBB>
        val bracketHexRegex = Regex("\\{#([0-9a-fA-F]{6})\\}")
        result = bracketHexRegex.replace(result) { match ->
            "<color:#${match.groupValues[1]}>"
        }
            
        return result
    }
}
