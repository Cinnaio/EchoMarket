@file:Suppress("DEPRECATION")

package com.github.cinnaio.echomarket.util

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

object ItemUtil {

    fun calculateHash(item: ItemStack): String {
        // Hash 必须基于：物品材质（Material） + 物品的 原始 NBT 数据
        // 为了确保不同数量的同种物品（例如 1个石头 和 64个石头）拥有相同的 Hash，
        // 我们在计算 Hash 前必须将数量归一化为 1。
        val clone = item.clone()
        clone.amount = 1
        
        val serialized = serializeItemStack(clone)
        return sha256(serialized)
    }
    
    fun serializeItemStack(item: ItemStack): String {
        // Prefer Paper's serializeAsBytes for full NBT support (including custom data)
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes())
        } catch (e: NoSuchMethodError) {
            // Fallback for older server versions (should not happen on 1.21+)
            val io = ByteArrayOutputStream()
            val os = BukkitObjectOutputStream(io)
            os.writeObject(item)
            os.flush()
            return Base64.getEncoder().encodeToString(io.toByteArray())
        }
    }
    
    @Suppress("DEPRECATION")
    fun deserializeItemStack(data: String): ItemStack {
        val bytes = Base64.getDecoder().decode(data)
        
        // Try Paper's deserializeBytes first
        try {
            return ItemStack.deserializeBytes(bytes)
        } catch (e: Exception) {
            // Fallback to legacy Java Serialization (for old database entries)
            try {
                val `in` = ByteArrayInputStream(bytes)
                val `is` = BukkitObjectInputStream(`in`)
                return `is`.readObject() as ItemStack
            } catch (e2: Exception) {
                // If both fail, throw the original error or e2
                throw e
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun getDisplayName(item: ItemStack): String {
        val meta = item.itemMeta ?: return "<translate:${item.translationKey()}>"
        val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        
        // 1. Check Custom Name (DisplayName)
        if (meta.hasDisplayName()) {
            val displayName = meta.displayName()
            if (displayName != null) {
                return mm.serialize(displayName)
            }
        }
        
        // 2. Check Item Name (1.20.5+ feature)
        // This is often used by custom items to set a non-italic default name
        try {
            if (meta.hasItemName()) {
                val itemName = meta.itemName()
                if (itemName != null) {
                    return mm.serialize(itemName)
                }
            }
        } catch (e: Throwable) {
            // Ignore if running on older versions
        }
        
        return "<translate:${item.translationKey()}>"
    }
}
