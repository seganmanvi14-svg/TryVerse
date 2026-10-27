package com.example.styletryon

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

//  SQLite database (tryverse.db)
//  users    - accounts (Sign Up / Login), height and body measurements
//  cart     - each user's cart, so it is still there after the app is closed
//  wishlist - each user's saved items
//  searches - each user's recent searches
//  looks    - each user's try-on history (the pictures are files in filesDir/looks)
//  addresses - each user's delivery addresses (picked on the map)
//  outfits  - each user's saved mix & match outfits (lookbook)
//  Everything is stored per user (by email), so logging in brings back all of it.


/** heightCm, shoulderCm and hipCm are 0 until the user enters / measures them. */
data class User(
    val name: String,
    val email: String,
    val phone: String,
    val heightCm: Int = 0,
    val shoulderCm: Float = 0f,
    val hipCm: Float = 0f
)

/** A delivery address picked on the map. full = "Label: address" (saved with each order). */
data class SavedAddress(val id: Long, val label: String, val line: String, val lat: Double, val lng: Double) {
    val full: String get() = "$label: $line"
}

/** Every new account starts with these two (they can be edited or deleted). */
val defaultAddresses = listOf(
    SavedAddress(0L, "Home", "A-203, MPSTME Hostel, SVKM's NMIMS, Navi Mumbai - 400710", 19.1045, 73.0094),
    SavedAddress(0L, "College", "SVKM's NMIMS, Vile Parle West, Mumbai - 400056", 19.1031, 72.8376)
)

/** A saved mix & match outfit: products with the size chosen for each. */
data class SavedOutfit(val id: Long, val name: String, val items: List<Pair<Product, String>>, val time: Long) {
    val products: List<Product> get() = items.map { it.first }
    val sizes: Map<Int, String> get() = items.associate { it.first.id to it.second }
    val total: Int get() = items.sumOf { it.first.price }
}

/** One picture in the try-on history. */
data class SavedLook(val path: String, val productId: Int, val time: Long)

/**
 * Passwords are never stored. We store a random salt and a PBKDF2 hash of the password,
 * so even someone who copies the database cannot read the passwords.
 */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Compares in constant time so the check can't be timed to guess the password. */
    fun matches(password: String, salt: ByteArray, expected: ByteArray) =
        MessageDigest.isEqual(hash(password, salt), expected)
}

class UserDatabase(context: Context) : SQLiteOpenHelper(context, "tryverse.db", null, 5) {

    companion object {
        private const val CREATE_USERS =
            "CREATE TABLE users (email TEXT PRIMARY KEY, name TEXT, phone TEXT, salt TEXT, hash TEXT, " +
                    "height INTEGER DEFAULT 0, shoulder REAL DEFAULT 0, hip REAL DEFAULT 0)"

        /** How many recent searches we keep for each user. */
        const val MAX_SEARCHES = 8
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_USERS)
        createShoppingTables(db)
    }

    /** Tables added in version 3. */
    private fun createShoppingTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cart (email TEXT, product_id INTEGER, size TEXT, qty INTEGER, checked INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS wishlist (email TEXT, product_id INTEGER, PRIMARY KEY (email, product_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS searches (email TEXT, query TEXT, time INTEGER, PRIMARY KEY (email, query))")
        db.execSQL("CREATE TABLE IF NOT EXISTS looks (email TEXT, path TEXT, product_id INTEGER, time INTEGER)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS addresses (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT, label TEXT, line TEXT, lat REAL, lng REAL)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS outfits (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT, name TEXT, items TEXT, time INTEGER)")
    }

    /** Upgrades without losing accounts (only version 1, which had no password hashing, is rebuilt). */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS users")
            db.execSQL(CREATE_USERS)
        } else if (oldVersion < 3) {
            db.execSQL("ALTER TABLE users ADD COLUMN height INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE users ADD COLUMN shoulder REAL DEFAULT 0")
            db.execSQL("ALTER TABLE users ADD COLUMN hip REAL DEFAULT 0")
        }
        createShoppingTables(db)
        // Version 4 added addresses: give existing accounts the two default addresses
        if (oldVersion in 2..3) {
            db.rawQuery("SELECT email FROM users", null).use { while (it.moveToNext()) addDefaultAddresses(db, it.getString(0)) }
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)


    //  Accounts (queries use ? placeholders, so no SQL injection)


    /** Returns false if the email is already registered. */
    fun addUser(name: String, email: String, phone: String, password: String): Boolean {
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply {
            put("email", email.lowercase()); put("name", name); put("phone", phone)
            put("salt", b64(salt)); put("hash", b64(PasswordHasher.hash(password, salt)))
        }
        val added = writableDatabase.insert("users", null, values) != -1L
        if (added) addDefaultAddresses(writableDatabase, email.lowercase())
        return added
    }

    fun isRegistered(email: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM users WHERE email = ?", arrayOf(email.lowercase())).use { it.moveToFirst() }

    fun login(email: String, password: String): User? {
        val ok = readableDatabase.rawQuery("SELECT salt, hash FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            it.moveToFirst() && PasswordHasher.matches(password, unb64(it.getString(0)), unb64(it.getString(1)))
        }
        return if (ok) getUser(email) else null
    }

    fun getUser(email: String): User? =
        readableDatabase.rawQuery(
            "SELECT name, phone, height, shoulder, hip FROM users WHERE email = ?", arrayOf(email.lowercase())
        ).use {
            if (it.moveToFirst()) User(it.getString(0), email.lowercase(), it.getString(1), it.getInt(2), it.getFloat(3), it.getFloat(4))
            else null
        }

    fun updateUser(email: String, name: String, phone: String) {
        val values = ContentValues().apply { put("name", name); put("phone", phone) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }

    /** Saves a new password (a new random salt and its PBKDF2 hash). */
    private fun setPassword(email: String, password: String) {
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply { put("salt", b64(salt)); put("hash", b64(PasswordHasher.hash(password, salt))) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }

    /** Change password (Profile): only works if the current password is right. */
    fun changePassword(email: String, current: String, newPassword: String): Boolean {
        if (login(email, current) == null) return false
        setPassword(email, newPassword)
        return true
    }

    /**
     * Forgot password: the email and the mobile number given at sign up must both match.
     * (A real app would also send an OTP to that phone.)
     */
    fun resetPassword(email: String, phone: String, newPassword: String): Boolean {
        val saved = readableDatabase.rawQuery("SELECT phone FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return false
        fun digits(p: String) = p.filter { c -> c.isDigit() }.takeLast(10)
        if (digits(phone).length < 10 || digits(saved) != digits(phone)) return false
        setPassword(email, newPassword)
        return true
    }

    fun setHeight(email: String, heightCm: Int) {
        val values = ContentValues().apply { put("height", heightCm) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }

    /** Body measurements from the live camera (used for size advice). */
    fun setMeasurements(email: String, shoulderCm: Float, hipCm: Float) {
        val values = ContentValues().apply { put("shoulder", shoulderCm); put("hip", hipCm) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }


    //  Cart and wishlist (the whole list is saved each time it changes)


    fun loadCart(email: String): List<CartItem> =
        readableDatabase.rawQuery("SELECT product_id, size, qty, checked FROM cart WHERE email = ? ORDER BY rowid", arrayOf(email)).use {
            val items = mutableListOf<CartItem>()
            while (it.moveToNext()) {
                val product = productById(it.getInt(0)) ?: continue      // skip products that no longer exist
                items.add(CartItem(product, it.getString(1), it.getInt(2), it.getInt(3) == 1))
            }
            items
        }

    fun saveCart(email: String, items: List<CartItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cart", "email = ?", arrayOf(email))
            items.forEach { item ->
                db.insert("cart", null, ContentValues().apply {
                    put("email", email); put("product_id", item.product.id); put("size", item.size)
                    put("qty", item.qty); put("checked", if (item.checked) 1 else 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadWishlist(email: String): List<Int> =
        readableDatabase.rawQuery("SELECT product_id FROM wishlist WHERE email = ? ORDER BY rowid", arrayOf(email)).use {
            val ids = mutableListOf<Int>()
            while (it.moveToNext()) ids.add(it.getInt(0))
            ids
        }

    fun saveWishlist(email: String, ids: List<Int>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("wishlist", "email = ?", arrayOf(email))
            ids.distinct().forEach { id ->
                db.insert("wishlist", null, ContentValues().apply { put("email", email); put("product_id", id) })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // -----------------------------------------------------------------
    //  Search history
    // -----------------------------------------------------------------

    fun addSearch(email: String, query: String) {
        val text = query.trim().take(40)
        if (text.isEmpty()) return
        val db = writableDatabase
        db.insertWithOnConflict("searches", null, ContentValues().apply {
            put("email", email); put("query", text.lowercase()); put("time", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        // Keep only the newest few
        db.execSQL(
            "DELETE FROM searches WHERE email = ? AND query NOT IN " +
                    "(SELECT query FROM searches WHERE email = ? ORDER BY time DESC LIMIT $MAX_SEARCHES)",
            arrayOf(email, email)
        )
    }

    fun loadSearches(email: String): List<String> =
        readableDatabase.rawQuery("SELECT query FROM searches WHERE email = ? ORDER BY time DESC", arrayOf(email)).use {
            val list = mutableListOf<String>()
            while (it.moveToNext()) list.add(it.getString(0))
            list
        }

    fun clearSearches(email: String) {
        writableDatabase.delete("searches", "email = ?", arrayOf(email))
    }

    //  Delivery addresses (Maps)

    private fun addDefaultAddresses(db: SQLiteDatabase, email: String) {
        defaultAddresses.forEach { a ->
            db.insert("addresses", null, ContentValues().apply {
                put("email", email); put("label", a.label); put("line", a.line); put("lat", a.lat); put("lng", a.lng)
            })
        }
    }

    fun loadAddresses(email: String): List<SavedAddress> =
        readableDatabase.rawQuery("SELECT id, label, line, lat, lng FROM addresses WHERE email = ? ORDER BY id", arrayOf(email)).use {
            val list = mutableListOf<SavedAddress>()
            while (it.moveToNext()) list.add(SavedAddress(it.getLong(0), it.getString(1), it.getString(2), it.getDouble(3), it.getDouble(4)))
            list
        }

    /** Adds a new address (id 0) or updates an existing one. */
    fun saveAddress(email: String, a: SavedAddress) {
        val values = ContentValues().apply {
            put("email", email); put("label", a.label); put("line", a.line); put("lat", a.lat); put("lng", a.lng)
        }
        if (a.id == 0L) writableDatabase.insert("addresses", null, values)
        else writableDatabase.update("addresses", values, "id = ? AND email = ?", arrayOf(a.id.toString(), email))
    }

    fun deleteAddress(email: String, id: Long) {
        writableDatabase.delete("addresses", "id = ? AND email = ?", arrayOf(id.toString(), email))
    }

    // -----------------------------------------------------------------
    //  Saved outfits (items are stored as "productId:size;productId:size")
    // -----------------------------------------------------------------

    fun saveOutfit(email: String, name: String, items: List<Pair<Product, String>>) {
        writableDatabase.insert("outfits", null, ContentValues().apply {
            put("email", email); put("name", name); put("time", System.currentTimeMillis())
            put("items", items.joinToString(";") { "${it.first.id}:${it.second}" })
        })
    }

    fun loadOutfits(email: String): List<SavedOutfit> =
        readableDatabase.rawQuery("SELECT id, name, items, time FROM outfits WHERE email = ? ORDER BY time DESC", arrayOf(email)).use {
            val list = mutableListOf<SavedOutfit>()
            while (it.moveToNext()) {
                val items = it.getString(2).split(";").mapNotNull { piece ->
                    val f = piece.split(":")
                    val product = f.getOrNull(0)?.toIntOrNull()?.let { id -> productById(id) } ?: return@mapNotNull null
                    product to (f.getOrNull(1) ?: product.defaultSize())
                }
                if (items.isNotEmpty()) list.add(SavedOutfit(it.getLong(0), it.getString(1), items, it.getLong(3)))
            }
            list
        }

    fun renameOutfit(email: String, id: Long, name: String) {
        writableDatabase.update("outfits", ContentValues().apply { put("name", name) }, "id = ? AND email = ?", arrayOf(id.toString(), email))
    }

    fun deleteOutfit(email: String, id: Long) {
        writableDatabase.delete("outfits", "id = ? AND email = ?", arrayOf(id.toString(), email))
    }

    // -----------------------------------------------------------------
    //  Try-on history
    // -----------------------------------------------------------------

    fun addLook(email: String, path: String, productId: Int) {
        writableDatabase.insert("looks", null, ContentValues().apply {
            put("email", email); put("path", path); put("product_id", productId); put("time", System.currentTimeMillis())
        })
    }

    fun loadLooks(email: String): List<SavedLook> =
        readableDatabase.rawQuery("SELECT path, product_id, time FROM looks WHERE email = ? ORDER BY time DESC", arrayOf(email)).use {
            val list = mutableListOf<SavedLook>()
            while (it.moveToNext()) {
                if (File(it.getString(0)).exists()) list.add(SavedLook(it.getString(0), it.getInt(1), it.getLong(2)))
            }
            list
        }

    fun deleteLook(email: String, path: String) {
        writableDatabase.delete("looks", "email = ? AND path = ?", arrayOf(email, path))
    }
}

/** Input checks used by Sign Up / Login / Edit Profile. */
object Validate {
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$")
    /** Indian mobile number: 10 digits starting with 6-9, optionally with +91 in front. */
    private val phoneRegex = Regex("^(\\+91)?[6-9][0-9]{9}$")

    fun email(e: String) = emailRegex.matches(e)
    fun phone(p: String) = phoneRegex.matches(p.replace(" ", "").replace("-", ""))

    /** 2-40 characters: letters, spaces, dots, apostrophes and hyphens, starting with a letter. */
    fun name(n: String): Boolean {
        val t = n.trim()
        return t.length in 2..40 && t.first().isLetter() && t.all { it.isLetter() || it in " .'-" }
    }

    /** At least 8 characters with a letter and a number. */
    fun password(p: String) = p.length in 8..64 && p.any { it.isLetter() } && p.any { it.isDigit() }

    /** 0-4: one point each for length 8+, letters and numbers, upper and lower case, a symbol. */
    fun strength(p: String): Int {
        var score = 0
        if (p.length >= 8) score++
        if (p.any { it.isLetter() } && p.any { it.isDigit() }) score++
        if (p.any { it.isUpperCase() } && p.any { it.isLowerCase() }) score++
        if (p.any { !it.isLetterOrDigit() }) score++
        return score
    }

    fun height(cm: Int) = cm in 120..220
}

//  File handling: session.txt (who is logged in)

fun saveSession(context: Context, email: String) = File(context.filesDir, "session.txt").writeText(email)

fun loadSession(context: Context): String? {
    val file = File(context.filesDir, "session.txt")
    return if (file.exists()) file.readText().trim().ifEmpty { null } else null
}

fun clearSession(context: Context) {
    File(context.filesDir, "session.txt").delete()
}
