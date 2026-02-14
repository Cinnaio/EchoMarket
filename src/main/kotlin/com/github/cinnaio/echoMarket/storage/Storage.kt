package com.github.cinnaio.echomarket.storage

import com.github.cinnaio.echomarket.EchoMarket
import com.github.cinnaio.echomarket.util.ItemUtil
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

interface Storage {
    fun init()
    fun close()
    
    // Shop
    fun createShop(owner: UUID, ownerName: String, location: Location, name: String, desc: String): Boolean
    fun getShop(owner: UUID): ShopData?
    fun getShop(id: Int): ShopData?
    fun getShops(owner: UUID): List<ShopData>
    fun getAllShops(): List<ShopData>
    fun updateShopName(shopId: Int, name: String)
    fun updateShopDesc(shopId: Int, desc: String)
    fun removeShop(shopId: Int): Boolean
    
    // Items
    fun addItem(shopId: Int, item: ItemStack, price: Double, stock: Int)
    fun getItems(shopId: Int): List<ItemData>
    fun removeItem(itemId: Int): Boolean
    fun updateItemStock(itemId: Int, newStock: Int)
    
    // Board
    fun addBoardMessage(owner: UUID, ownerName: String, content: String, duration: Long)
    fun getBoardMessage(id: Int): BoardData?
    fun getBoardMessages(): List<BoardData>
    fun getBoardMessages(owner: UUID): List<BoardData>
    fun renewBoardMessage(id: Int, duration: Long)
    fun deleteBoardMessage(id: Int): Boolean
    fun cleanExpiredBoardMessages()
    
    // Logs
    fun logTransaction(buyer: UUID, seller: UUID, shopId: Int, itemHash: String, amount: Int, price: Double)
    
    // Statistics
    fun getTransactionStats(player: UUID, since: Long): TransactionStats
    fun getShopCount(player: UUID): Int
    fun addShopBoost(shopId: Int, amount: Double): Boolean
    fun setShopBoost(shopId: Int, amount: Double): Boolean
    
    // Notifications
    fun addNotification(player: UUID, message: String)
    fun getNotifications(player: UUID): List<String>
    fun deleteNotifications(player: UUID)

    // Admin Log
    fun logAdminAction(adminUuid: UUID, adminName: String, action: String, target: String, details: String)
}

data class TransactionStats(
    val totalVolume: Double,
    val transactionCount: Int
)

data class ShopData(
    val id: Int,
    val ownerUuid: UUID,
    val ownerName: String,
    val location: Location,
    val name: String,
    val description: String,
    val index: Int = 1,
    val boost: Double = 0.0,
    val heat: Double = 0.0
)

data class ItemData(
    val id: Int,
    val shopId: Int,
    val itemHash: String,
    val itemStack: ItemStack,
    val price: Double,
    val stock: Int
)

data class BoardData(
    val id: Int,
    val ownerUuid: UUID,
    val ownerName: String,
    val content: String,
    val expireAt: Long
)

class StorageImpl(private val plugin: EchoMarket) : Storage {

    private lateinit var dataSource: HikariDataSource
    private val isMysql: Boolean
        get() = plugin.config.getString("database.type", "sqlite")!!.equals("mysql", ignoreCase = true)

    override fun init() {
        val config = HikariConfig()
        
        if (isMysql) {
            config.jdbcUrl = "jdbc:mysql://${plugin.config.getString("database.host")}:${plugin.config.getInt("database.port")}/${plugin.config.getString("database.database")}"
            config.username = plugin.config.getString("database.username")
            config.password = plugin.config.getString("database.password")
            config.driverClassName = "com.mysql.cj.jdbc.Driver"
        } else {
            config.jdbcUrl = "jdbc:sqlite:${plugin.dataFolder}/database.db"
            config.driverClassName = "org.sqlite.JDBC"
        }
        
        config.maximumPoolSize = 10
        dataSource = HikariDataSource(config)
        
        createTables()
        migrateSchema()
    }
    
    private fun createTables() {
        dataSource.connection.use { conn ->
            val stmt = conn.createStatement()
            val autoInc = if (isMysql) "AUTO_INCREMENT" else "AUTOINCREMENT"
            
            // Shops
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shops (
                    id INTEGER PRIMARY KEY $autoInc,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    name VARCHAR(64),
                    description TEXT,
                    created_at LONG,
                    boost DOUBLE DEFAULT 0.0,
                    player_shop_index INTEGER DEFAULT 1
                )
            """)
            
            // Items
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS items (
                    id INTEGER PRIMARY KEY $autoInc,
                    shop_id INTEGER NOT NULL,
                    item_hash VARCHAR(64) NOT NULL,
                    item_data TEXT NOT NULL,
                    price DOUBLE NOT NULL,
                    stock INTEGER NOT NULL,
                    created_at LONG,
                    FOREIGN KEY(shop_id) REFERENCES shops(id) ON DELETE CASCADE
                )
            """)
            
            // Board
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS boards (
                    id INTEGER PRIMARY KEY $autoInc,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    content TEXT NOT NULL,
                    created_at LONG,
                    expire_at LONG
                )
            """)
            
            // Transactions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY $autoInc,
                    buyer_uuid VARCHAR(36) NOT NULL,
                    seller_uuid VARCHAR(36) NOT NULL,
                    shop_id INTEGER DEFAULT -1,
                    item_hash VARCHAR(64) NOT NULL,
                    amount INTEGER NOT NULL,
                    price DOUBLE NOT NULL,
                    timestamp LONG
                )
            """)
            
            // Notifications
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id INTEGER PRIMARY KEY $autoInc,
                    player_uuid VARCHAR(36) NOT NULL,
                    message TEXT NOT NULL,
                    created_at LONG
                )
            """)

            // Admin Logs
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS admin_logs (
                    id INTEGER PRIMARY KEY $autoInc,
                    admin_uuid VARCHAR(36) NOT NULL,
                    admin_name VARCHAR(16) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    target TEXT NOT NULL,
                    details TEXT,
                    timestamp LONG
                )
            """)
        }
    }
    
    private fun migrateSchema() {
        dataSource.connection.use { conn ->
            val stmt = conn.createStatement()
            try {
                stmt.execute("ALTER TABLE shops ADD COLUMN boost DOUBLE DEFAULT 0.0")
            } catch (ignored: SQLException) {}
            
            try {
                stmt.execute("ALTER TABLE transactions ADD COLUMN shop_id INTEGER DEFAULT -1")
            } catch (ignored: SQLException) {}

            try {
                stmt.execute("ALTER TABLE shops ADD COLUMN player_shop_index INTEGER DEFAULT 1")
            } catch (ignored: SQLException) {}
        }
    }

    override fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
    }

    override fun createShop(owner: UUID, ownerName: String, location: Location, name: String, desc: String): Boolean {
        return try {
            dataSource.connection.use { conn ->
                // Calculate next index
                var nextIndex = 1
                val psCount = conn.prepareStatement("SELECT MAX(player_shop_index) as max_idx FROM shops WHERE owner_uuid = ?")
                psCount.setString(1, owner.toString())
                val rs = psCount.executeQuery()
                if (rs.next()) {
                    nextIndex = rs.getInt("max_idx") + 1
                }
                
                val ps = conn.prepareStatement("INSERT INTO shops (owner_uuid, owner_name, world, x, y, z, name, description, created_at, player_shop_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                ps.setString(1, owner.toString())
                ps.setString(2, ownerName)
                ps.setString(3, location.world.name)
                ps.setDouble(4, location.x)
                ps.setDouble(5, location.y)
                ps.setDouble(6, location.z)
                ps.setString(7, name)
                ps.setString(8, desc)
                ps.setLong(9, System.currentTimeMillis())
                ps.setInt(10, nextIndex)
                ps.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }

    override fun getShop(owner: UUID): ShopData? {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT * FROM shops WHERE owner_uuid = ?")
            ps.setString(1, owner.toString())
            val rs = ps.executeQuery()
            if (rs.next()) {
                val world = plugin.server.getWorld(rs.getString("world")) ?: return null
                val loc = Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"))
                val boost = try { rs.getDouble("boost") } catch (e: Exception) { 0.0 }
                val index = try { rs.getInt("player_shop_index") } catch (e: Exception) { 1 }
                return ShopData(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    loc,
                    rs.getString("name"),
                    rs.getString("description"),
                    index,
                    boost,
                    0.0
                )
            }
        }
        return null
    }

    override fun getShop(id: Int): ShopData? {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT * FROM shops WHERE id = ?")
            ps.setInt(1, id)
            val rs = ps.executeQuery()
            if (rs.next()) {
                val world = plugin.server.getWorld(rs.getString("world")) ?: return null
                val loc = Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"))
                val boost = try { rs.getDouble("boost") } catch (e: Exception) { 0.0 }
                val index = try { rs.getInt("player_shop_index") } catch (e: Exception) { 1 }
                return ShopData(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    loc,
                    rs.getString("name"),
                    rs.getString("description"),
                    index,
                    boost,
                    0.0
                )
            }
        }
        return null
    }

    override fun getShops(owner: UUID): List<ShopData> {
        val shops = mutableListOf<ShopData>()
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT * FROM shops WHERE owner_uuid = ?")
            ps.setString(1, owner.toString())
            val rs = ps.executeQuery()
            while (rs.next()) {
                val world = plugin.server.getWorld(rs.getString("world"))
                if (world != null) {
                    val loc = Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"))
                    val boost = try { rs.getDouble("boost") } catch (e: Exception) { 0.0 }
                    val index = try { rs.getInt("player_shop_index") } catch (e: Exception) { 1 }
                    shops.add(ShopData(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("owner_name"),
                        loc,
                        rs.getString("name"),
                        rs.getString("description"),
                        index,
                        boost,
                        0.0
                    ))
                }
            }
        }
        return shops
    }
    
    override fun removeShop(shopId: Int): Boolean {
        return try {
            dataSource.connection.use { conn ->
                val ps = conn.prepareStatement("DELETE FROM shops WHERE id = ?")
                ps.setInt(1, shopId)
                ps.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }
    override fun getAllShops(): List<ShopData> {
        val list = mutableListOf<ShopData>()
        val wTx = plugin.config.getDouble("market.heat.weights.total-transactions", 1.0)
        val wBoost = plugin.config.getDouble("market.heat.weights.boost", 10.0)
        
        dataSource.connection.use { conn ->
            val sql = """
                SELECT s.*, 
                       (SELECT COUNT(*) FROM transactions t WHERE t.seller_uuid = s.owner_uuid) as tx_count 
                FROM shops s
            """
            val ps = conn.prepareStatement(sql)
            val rs = ps.executeQuery()
            while (rs.next()) {
                val world = plugin.server.getWorld(rs.getString("world")) ?: continue
                val loc = Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"))
                val boost = try { rs.getDouble("boost") } catch (e: Exception) { 0.0 }
                val index = try { rs.getInt("player_shop_index") } catch (e: Exception) { 1 }
                val txCount = rs.getInt("tx_count")
                val heat = (txCount * wTx) + (boost * wBoost)
                
                list.add(ShopData(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    loc,
                    rs.getString("name"),
                    rs.getString("description"),
                    index,
                    boost,
                    heat
                ))
            }
        }
        return list.sortedByDescending { it.heat }
    }

    override fun updateShopName(shopId: Int, name: String) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("UPDATE shops SET name = ? WHERE id = ?")
            ps.setString(1, name)
            ps.setInt(2, shopId)
            ps.executeUpdate()
        }
    }

    override fun updateShopDesc(shopId: Int, desc: String) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("UPDATE shops SET description = ? WHERE id = ?")
            ps.setString(1, desc)
            ps.setInt(2, shopId)
            ps.executeUpdate()
        }
    }

    override fun addItem(shopId: Int, item: ItemStack, price: Double, stock: Int) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("INSERT INTO items (shop_id, item_hash, item_data, price, stock, created_at) VALUES (?, ?, ?, ?, ?, ?)")
            ps.setInt(1, shopId)
            ps.setString(2, ItemUtil.calculateHash(item))
            ps.setString(3, ItemUtil.serializeItemStack(item))
            ps.setDouble(4, price)
            ps.setInt(5, stock)
            ps.setLong(6, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    override fun getItems(shopId: Int): List<ItemData> {
        val list = mutableListOf<ItemData>()
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT * FROM items WHERE shop_id = ?")
            ps.setInt(1, shopId)
            val rs = ps.executeQuery()
            while (rs.next()) {
                list.add(ItemData(
                    rs.getInt("id"),
                    rs.getInt("shop_id"),
                    rs.getString("item_hash"),
                    ItemUtil.deserializeItemStack(rs.getString("item_data")),
                    rs.getDouble("price"),
                    rs.getInt("stock")
                ))
            }
        }
        return list
    }

    override fun removeItem(itemId: Int): Boolean {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("DELETE FROM items WHERE id = ?")
            ps.setInt(1, itemId)
            return ps.executeUpdate() > 0
        }
    }

    override fun updateItemStock(itemId: Int, newStock: Int) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("UPDATE items SET stock = ? WHERE id = ?")
            ps.setInt(1, newStock)
            ps.setInt(2, itemId)
            ps.executeUpdate()
        }
    }

    override fun addBoardMessage(owner: UUID, ownerName: String, content: String, duration: Long) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("INSERT INTO boards (owner_uuid, owner_name, content, created_at, expire_at) VALUES (?, ?, ?, ?, ?)")
            ps.setString(1, owner.toString())
            ps.setString(2, ownerName)
            ps.setString(3, content)
            val now = System.currentTimeMillis()
            ps.setLong(4, now)
            ps.setLong(5, now + duration * 1000)
            ps.executeUpdate()
        }
    }

    override fun getBoardMessage(id: Int): BoardData? {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT * FROM boards WHERE id = ?")
            ps.setInt(1, id)
            val rs = ps.executeQuery()
            if (rs.next()) {
                return BoardData(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("content"),
                    rs.getLong("expire_at")
                )
            }
        }
        return null
    }

    override fun getBoardMessages(): List<BoardData> {
        val list = mutableListOf<BoardData>()
        dataSource.connection.use { conn ->
            val now = System.currentTimeMillis()
            val ps = conn.prepareStatement("SELECT * FROM boards WHERE expire_at > ? ORDER BY created_at DESC")
            ps.setLong(1, now)
            val rs = ps.executeQuery()
            while (rs.next()) {
                list.add(BoardData(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("content"),
                    rs.getLong("expire_at")
                ))
            }
        }
        return list
    }

    override fun getBoardMessages(owner: UUID): List<BoardData> {
        val list = mutableListOf<BoardData>()
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT * FROM boards WHERE owner_uuid = ? ORDER BY created_at DESC")
            ps.setString(1, owner.toString())
            val rs = ps.executeQuery()
            while (rs.next()) {
                list.add(BoardData(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("content"),
                    rs.getLong("expire_at")
                ))
            }
        }
        return list
    }

    override fun renewBoardMessage(id: Int, duration: Long) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("UPDATE boards SET expire_at = expire_at + ? WHERE id = ?")
            ps.setLong(1, duration * 1000)
            ps.setInt(2, id)
            ps.executeUpdate()
        }
    }

    override fun deleteBoardMessage(id: Int): Boolean {
        return try {
            dataSource.connection.use { conn ->
                val ps = conn.prepareStatement("DELETE FROM boards WHERE id = ?")
                ps.setInt(1, id)
                ps.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }

    override fun cleanExpiredBoardMessages() {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("DELETE FROM boards WHERE expire_at <= ?")
            ps.setLong(1, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    override fun logTransaction(buyer: UUID, seller: UUID, shopId: Int, itemHash: String, amount: Int, price: Double) {
         dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("INSERT INTO transactions (buyer_uuid, seller_uuid, shop_id, item_hash, amount, price, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)")
            ps.setString(1, buyer.toString())
            ps.setString(2, seller.toString())
            ps.setInt(3, shopId)
            ps.setString(4, itemHash)
            ps.setInt(5, amount)
            ps.setDouble(6, price)
            ps.setLong(7, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    override fun addShopBoost(shopId: Int, amount: Double): Boolean {
        return try {
            dataSource.connection.use { conn ->
                val ps = conn.prepareStatement("UPDATE shops SET boost = boost + ? WHERE id = ?")
                ps.setDouble(1, amount)
                ps.setInt(2, shopId)
                ps.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }

    override fun setShopBoost(shopId: Int, amount: Double): Boolean {
        return try {
            dataSource.connection.use { conn ->
                val ps = conn.prepareStatement("UPDATE shops SET boost = ? WHERE id = ?")
                ps.setDouble(1, amount)
                ps.setInt(2, shopId)
                ps.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }

    override fun getTransactionStats(player: UUID, since: Long): TransactionStats {
        var totalVolume = 0.0
        var transactionCount = 0
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT SUM(amount * price) as volume, COUNT(*) as count FROM transactions WHERE (buyer_uuid = ? OR seller_uuid = ?) AND timestamp >= ?")
            ps.setString(1, player.toString())
            ps.setString(2, player.toString())
            ps.setLong(3, since)
            val rs = ps.executeQuery()
            if (rs.next()) {
                totalVolume = rs.getDouble("volume")
                transactionCount = rs.getInt("count")
            }
        }
        return TransactionStats(totalVolume, transactionCount)
    }

    override fun addNotification(player: UUID, message: String) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("INSERT INTO notifications (player_uuid, message, created_at) VALUES (?, ?, ?)")
            ps.setString(1, player.toString())
            ps.setString(2, message)
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    override fun getNotifications(player: UUID): List<String> {
        val list = mutableListOf<String>()
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT message FROM notifications WHERE player_uuid = ? ORDER BY created_at ASC")
            ps.setString(1, player.toString())
            val rs = ps.executeQuery()
            while (rs.next()) {
                list.add(rs.getString("message"))
            }
        }
        return list
    }

    override fun deleteNotifications(player: UUID) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("DELETE FROM notifications WHERE player_uuid = ?")
            ps.setString(1, player.toString())
            ps.executeUpdate()
        }
    }

    override fun getShopCount(player: UUID): Int {
        var count = 0
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT COUNT(*) as count FROM shops WHERE owner_uuid = ?")
            ps.setString(1, player.toString())
            val rs = ps.executeQuery()
            if (rs.next()) {
                count = rs.getInt("count")
            }
        }
        return count
    }

    override fun logAdminAction(adminUuid: UUID, adminName: String, action: String, target: String, details: String) {
        dataSource.connection.use { conn ->
            val ps = conn.prepareStatement("INSERT INTO admin_logs (admin_uuid, admin_name, action, target, details, timestamp) VALUES (?, ?, ?, ?, ?, ?)")
            ps.setString(1, adminUuid.toString())
            ps.setString(2, adminName)
            ps.setString(3, action)
            ps.setString(4, target)
            ps.setString(5, details)
            ps.setLong(6, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }
}

/**
 * Extension function to provide 'use' block for java.sql.Connection.
 * Solves Unresolved reference and Type mismatch errors when AutoCloseable.use is not available.
 */
inline fun <R> Connection.use(block: (Connection) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        if (exception == null) {
            try {
                close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        } else {
            try {
                close()
            } catch (closeException: Throwable) {
                // Suppress exception if possible, or just ignore to keep original exception
                // exception.addSuppressed(closeException) 
            }
        }
    }
}
