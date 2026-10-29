// =====================================================================
// UserDatabase.kt = THE APP'S DATABASE (SQLite) + input checks + who is logged in.
// SQLite = a small database stored as one file (tryverse.db) on the phone. It has TABLES like Excel sheets:
// users, cart, wishlist, searches, looks, addresses, outfits. Every row has the user's email,
// so each user only gets their own data back after logging in.
// =====================================================================
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


// One user: name, email, phone, and height / shoulder / hip size (0 until measured with the live camera)
data class User(
    val name: String,
    val email: String,
    val phone: String,
    val heightCm: Int = 0,
    val shoulderCm: Float = 0f,
    val hipCm: Float = 0f
)

// One delivery address: label (Home...), the address text, and the map pin (latitude, longitude).
// full = "Label: address", saved with each order
data class SavedAddress(val id: Long, val label: String, val line: String, val lat: Double, val lng: Double) {
    val full: String get() = "$label: $line"
}

// Every new account starts with these two addresses (they can be edited or deleted)
val defaultAddresses = listOf(
    SavedAddress(0L, "Home", "A-203, MPSTME Hostel, SVKM's NMIMS, Navi Mumbai - 400710", 19.1045, 73.0094),
    SavedAddress(0L, "College", "SVKM's NMIMS, Vile Parle West, Mumbai - 400056", 19.1031, 72.8376)
)

// A saved mix & match outfit: the products with the size chosen for each, and the total price
data class SavedOutfit(val id: Long, val name: String, val items: List<Pair<Product, String>>, val time: Long) {
    val products: List<Product> get() = items.map { it.first }
    val sizes: Map<Int, String> get() = items.associate { it.first.id to it.second }
    val total: Int get() = items.sumOf { it.first.price }
}

// One picture in the try-on history: where the picture file is, which product, when
data class SavedLook(val path: String, val productId: Int, val time: Long)

/**
 * Passwords are never stored. We store a random salt and a PBKDF2 hash of the password,
 * so even someone who copies the database cannot read the passwords.
 */
// *** PASSWORD SECURITY (PBKDF2 hashing) ***
// The password itself is NEVER saved. We save a random 'salt' and a HASH (a scrambled version that can't be
// turned back into the password). At login we hash the typed password again and compare the two hashes.
object PasswordHasher {
    // The hash is repeated 120,000 times so guessing passwords is very slow. 256-bit result
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    // 16 random bytes. Different for every user, so two equal passwords still get different hashes
    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    // Password + salt -> PBKDF2 (with SHA-256) -> hash. clearPassword() wipes the password from memory after
    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // Is the typed password right? Hash it with the user's salt and compare (in constant time, so timing can't
    // leak anything)
    fun matches(password: String, salt: ByteArray, expected: ByteArray) =
        MessageDigest.isEqual(hash(password, salt), expected)
}

// *** THE DATABASE ***
// SQLiteOpenHelper creates / opens the file tryverse.db. 5 = database version (goes up when tables change)
class UserDatabase(context: Context) : SQLiteOpenHelper(context, "tryverse.db", null, 5) {

    companion object {
        // SQL to make the USERS table. email is the PRIMARY KEY, so the same email can never be saved twice
        private const val CREATE_USERS =
            "CREATE TABLE users (email TEXT PRIMARY KEY, name TEXT, phone TEXT, salt TEXT, hash TEXT, " +
                    "height INTEGER DEFAULT 0, shoulder REAL DEFAULT 0, hip REAL DEFAULT 0)"

        /** How many recent searches we keep for each user. */
        const val MAX_SEARCHES = 8
    }

    // Runs once, the first time the app opens: makes all the tables
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_USERS)
        createShoppingTables(db)
    }

    // Makes the other tables: cart, wishlist, searches, looks, addresses, outfits (each row has the user's
    // email)
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

    // Runs when the app is updated to a newer database version. Adds the new tables / columns without deleting
    // accounts
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

    // Base64 = turns bytes (salt / hash) into normal text so they can be saved in a TEXT column, and back
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)


    //  Accounts (queries use ? placeholders, so no SQL injection)


    // *** SIGN UP: SAVE A NEW USER ***
    // Makes a salt, hashes the password, and inserts the row. Returns false if the email already exists
    fun addUser(name: String, email: String, phone: String, password: String): Boolean {
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply {
            put("email", email.lowercase()); put("name", name); put("phone", phone)
            put("salt", b64(salt)); put("hash", b64(PasswordHasher.hash(password, salt)))
        }
        // insert returns -1 if it failed (e.g. the email is already there, because email is the PRIMARY KEY)
        val added = writableDatabase.insert("users", null, values) != -1L
        if (added) addDefaultAddresses(writableDatabase, email.lowercase())
        return added
    }

    // *** IS THERE AN ACCOUNT WITH THIS EMAIL? *** (used by Sign Up and Login)
    // ? = placeholder: the email is passed separately, so nobody can break the SQL (no SQL injection)
    fun isRegistered(email: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM users WHERE email = ?", arrayOf(email.lowercase())).use { it.moveToFirst() }

    // *** LOGIN: PASSWORD IS CHECKED HERE ***
    // Reads this user's salt + hash and checks the typed password. Right = the User, wrong = null
    fun login(email: String, password: String): User? {
        val ok = readableDatabase.rawQuery("SELECT salt, hash FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            it.moveToFirst() && PasswordHasher.matches(password, unb64(it.getString(0)), unb64(it.getString(1)))
        }
        return if (ok) getUser(email) else null
    }

    // Reads one user's details (name, phone, height, shoulder, hip)
    fun getUser(email: String): User? =
        readableDatabase.rawQuery(
            "SELECT name, phone, height, shoulder, hip FROM users WHERE email = ?", arrayOf(email.lowercase())
        ).use {
            if (it.moveToFirst()) User(it.getString(0), email.lowercase(), it.getString(1), it.getInt(2), it.getFloat(3), it.getFloat(4))
            else null
        }

    // Edit profile: saves the new name and phone
    fun updateUser(email: String, name: String, phone: String) {
        val values = ContentValues().apply { put("name", name); put("phone", phone) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }

    // Saves a new password: a NEW random salt and its hash
    private fun setPassword(email: String, password: String) {
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply { put("salt", b64(salt)); put("hash", b64(PasswordHasher.hash(password, salt))) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }

    // *** CHANGE PASSWORD (Profile) *** only works if the current password is right
    fun changePassword(email: String, current: String, newPassword: String): Boolean {
        if (login(email, current) == null) return false
        setPassword(email, newPassword)
        return true
    }

    /**
     * Forgot password: the email and the mobile number given at sign up must both match.
     * (A real app would also send an OTP to that phone.)
     */
    // *** FORGOT PASSWORD ***
    // The email AND the mobile number given at sign up must match (last 10 digits). Then the new password is
    // saved
    fun resetPassword(email: String, phone: String, newPassword: String): Boolean {
        val saved = readableDatabase.rawQuery("SELECT phone FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return false
        fun digits(p: String) = p.filter { c -> c.isDigit() }.takeLast(10)
        if (digits(phone).length < 10 || digits(saved) != digits(phone)) return false
        setPassword(email, newPassword)
        return true
    }

    // Saves the user's height (entered in the live try-on, used for the size suggestion)
    fun setHeight(email: String, heightCm: Int) {
        val values = ContentValues().apply { put("height", heightCm) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }

    // Saves the shoulder and hip width measured by the live camera body scan
    fun setMeasurements(email: String, shoulderCm: Float, hipCm: Float) {
        val values = ContentValues().apply { put("shoulder", shoulderCm); put("hip", hipCm) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }


    //  Cart and wishlist (the whole list is saved each time it changes)


    // *** CART PER USER: reads this user's cart rows back into CartItems ***
    fun loadCart(email: String): List<CartItem> =
        readableDatabase.rawQuery("SELECT product_id, size, qty, checked FROM cart WHERE email = ? ORDER BY rowid", arrayOf(email)).use {
            val items = mutableListOf<CartItem>()
            while (it.moveToNext()) {
                val product = productById(it.getInt(0)) ?: continue      // skip products that no longer exist
                items.add(CartItem(product, it.getString(1), it.getInt(2), it.getInt(3) == 1))
            }
            items
        }

    // Saves the whole cart: delete this user's old rows, insert the new ones.
    // A TRANSACTION = all of it is saved, or none of it (never half a cart)
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

    // Reads this user's saved (♡) product ids
    fun loadWishlist(email: String): List<Int> =
        readableDatabase.rawQuery("SELECT product_id FROM wishlist WHERE email = ? ORDER BY rowid", arrayOf(email)).use {
            val ids = mutableListOf<Int>()
            while (it.moveToNext()) ids.add(it.getInt(0))
            ids
        }

    // Saves the whole wishlist the same way (delete old, insert new, in one transaction)
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

    // *** SEARCH HISTORY ***
    // Saves a search (lower case, max 40 letters). CONFLICT_REPLACE = the same search again just updates its
    // time
    fun addSearch(email: String, query: String) {
        val text = query.trim().take(40)
        if (text.isEmpty()) return
        val db = writableDatabase
        db.insertWithOnConflict("searches", null, ContentValues().apply {
            put("email", email); put("query", text.lowercase()); put("time", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        // Keep only the newest few
        // Keep only the newest 8 searches for this user, delete older ones
        db.execSQL(
            "DELETE FROM searches WHERE email = ? AND query NOT IN " +
                    "(SELECT query FROM searches WHERE email = ? ORDER BY time DESC LIMIT $MAX_SEARCHES)",
            arrayOf(email, email)
        )
    }

    // This user's recent searches, newest first
    fun loadSearches(email: String): List<String> =
        readableDatabase.rawQuery("SELECT query FROM searches WHERE email = ? ORDER BY time DESC", arrayOf(email)).use {
            val list = mutableListOf<String>()
            while (it.moveToNext()) list.add(it.getString(0))
            list
        }

    // The Clear button: delete this user's search history
    fun clearSearches(email: String) {
        writableDatabase.delete("searches", "email = ?", arrayOf(email))
    }

    //  Delivery addresses (Maps)

    // Gives a new account the two default addresses
    private fun addDefaultAddresses(db: SQLiteDatabase, email: String) {
        defaultAddresses.forEach { a ->
            db.insert("addresses", null, ContentValues().apply {
                put("email", email); put("label", a.label); put("line", a.line); put("lat", a.lat); put("lng", a.lng)
            })
        }
    }

    // *** ADDRESSES PER USER (shown in My Addresses and at checkout) ***
    fun loadAddresses(email: String): List<SavedAddress> =
        readableDatabase.rawQuery("SELECT id, label, line, lat, lng FROM addresses WHERE email = ? ORDER BY id", arrayOf(email)).use {
            val list = mutableListOf<SavedAddress>()
            while (it.moveToNext()) list.add(SavedAddress(it.getLong(0), it.getString(1), it.getString(2), it.getDouble(3), it.getDouble(4)))
            list
        }

    // Adds a new address (id 0) or updates an existing one (from the map screen)
    fun saveAddress(email: String, a: SavedAddress) {
        val values = ContentValues().apply {
            put("email", email); put("label", a.label); put("line", a.line); put("lat", a.lat); put("lng", a.lng)
        }
        if (a.id == 0L) writableDatabase.insert("addresses", null, values)
        else writableDatabase.update("addresses", values, "id = ? AND email = ?", arrayOf(a.id.toString(), email))
    }

    // Deletes one address (only if it belongs to this user)
    fun deleteAddress(email: String, id: Long) {
        writableDatabase.delete("addresses", "id = ? AND email = ?", arrayOf(id.toString(), email))
    }

    // -----------------------------------------------------------------
    //  Saved outfits (items are stored as "productId:size;productId:size")
    // -----------------------------------------------------------------

    // *** SAVED OUTFITS: saves a mix & match outfit as text like 4:M;21:30 (productId:size) ***
    fun saveOutfit(email: String, name: String, items: List<Pair<Product, String>>) {
        writableDatabase.insert("outfits", null, ContentValues().apply {
            put("email", email); put("name", name); put("time", System.currentTimeMillis())
            put("items", items.joinToString(";") { "${it.first.id}:${it.second}" })
        })
    }

    // Reads this user's outfits back (newest first) and turns the text back into products + sizes
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

    // Rename / delete an outfit
    fun renameOutfit(email: String, id: Long, name: String) {
        writableDatabase.update("outfits", ContentValues().apply { put("name", name) }, "id = ? AND email = ?", arrayOf(id.toString(), email))
    }

    fun deleteOutfit(email: String, id: Long) {
        writableDatabase.delete("outfits", "id = ? AND email = ?", arrayOf(id.toString(), email))
    }

    // -----------------------------------------------------------------
    //  Try-on history
    // -----------------------------------------------------------------

    // *** TRY-ON HISTORY: saves where a try-on picture is stored, for which product, and when ***
    fun addLook(email: String, path: String, productId: Int) {
        writableDatabase.insert("looks", null, ContentValues().apply {
            put("email", email); put("path", path); put("product_id", productId); put("time", System.currentTimeMillis())
        })
    }

    // This user's try-on pictures, newest first (skips pictures whose file was deleted)
    fun loadLooks(email: String): List<SavedLook> =
        readableDatabase.rawQuery("SELECT path, product_id, time FROM looks WHERE email = ? ORDER BY time DESC", arrayOf(email)).use {
            val list = mutableListOf<SavedLook>()
            while (it.moveToNext()) {
                if (File(it.getString(0)).exists()) list.add(SavedLook(it.getString(0), it.getInt(1), it.getLong(2)))
            }
            list
        }

    // Removes one picture from the history
    fun deleteLook(email: String, path: String) {
        writableDatabase.delete("looks", "email = ? AND path = ?", arrayOf(email, path))
    }
}

// *** INPUT CHECKS (used by Sign Up, Login, Edit Profile, Change Password) ***
// Regex = a pattern the text must match
object Validate {
    // Email pattern: something@something.something
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$")
    // Indian mobile number: 10 digits starting with 6-9, optionally with +91 in front
    private val phoneRegex = Regex("^(\\+91)?[6-9][0-9]{9}$")

    // True if the text matches the pattern (spaces and dashes in phone numbers are ignored)
    fun email(e: String) = emailRegex.matches(e)
    fun phone(p: String) = phoneRegex.matches(p.replace(" ", "").replace("-", ""))

    // Name: 2-40 characters, starts with a letter, only letters, spaces, dots, apostrophes and hyphens
    fun name(n: String): Boolean {
        val t = n.trim()
        return t.length in 2..40 && t.first().isLetter() && t.all { it.isLetter() || it in " .'-" }
    }

    // Password: 8-64 characters with at least one letter and one number
    fun password(p: String) = p.length in 8..64 && p.any { it.isLetter() } && p.any { it.isDigit() }

    // Password strength 0-4: 8+ characters, letters and numbers, upper and lower case, a symbol
    fun strength(p: String): Int {
        var score = 0
        if (p.length >= 8) score++
        if (p.any { it.isLetter() } && p.any { it.isDigit() }) score++
        if (p.any { it.isUpperCase() } && p.any { it.isLowerCase() }) score++
        if (p.any { !it.isLetterOrDigit() }) score++
        return score
    }

    // Height must be between 120 and 220 cm
    fun height(cm: Int) = cm in 120..220
}

//  File handling: session.txt (who is logged in)

// *** WHO IS LOGGED IN (file handling) ***
// session.txt holds the logged-in user's email, so the app opens straight to Home next time
fun saveSession(context: Context, email: String) = File(context.filesDir, "session.txt").writeText(email)

// Reads the logged-in email (null = nobody is logged in)
fun loadSession(context: Context): String? {
    val file = File(context.filesDir, "session.txt")
    return if (file.exists()) file.readText().trim().ifEmpty { null } else null
}

// Log out = delete session.txt
fun clearSession(context: Context) {
    File(context.filesDir, "session.txt").delete()
}
