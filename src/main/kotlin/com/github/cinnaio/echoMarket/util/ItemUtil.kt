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
        // 由于 Spigot API 不直接暴露原始 NBT 字符串，我们使用 ItemStack 的序列化数据作为替代
        // 这通常包含了 Material 和所有 Meta 数据 (NBT)
        // 只要序列化后的字节数组不同，Hash 就不同
        
        // 注意：ItemStack.serialize() 得到的 Map 可能不包含所有 NBT 细节，
        // 更可靠的方法是使用 BukkitObjectOutputStream 序列化整个对象
        
        val serialized = serializeItemStack(item)
        return sha256(serialized)
    }
    
    fun serializeItemStack(item: ItemStack): String {
        val io = ByteArrayOutputStream()
        val os = BukkitObjectOutputStream(io)
        os.writeObject(item)
        os.flush()
        return Base64.getEncoder().encodeToString(io.toByteArray())
    }
    
    fun deserializeItemStack(data: String): ItemStack {
        val bytes = Base64.getDecoder().decode(data)
        val `in` = ByteArrayInputStream(bytes)
        val `is` = BukkitObjectInputStream(`in`)
        return `is`.readObject() as ItemStack
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
