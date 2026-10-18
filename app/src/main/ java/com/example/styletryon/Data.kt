package com.example.styletryon

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import androidx.compose.ui.graphics.Color
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================
//  Products
// =====================================================================

/**
 * One product in the shop.
 * image = garment photo in res/drawable-nodpi (background removed)
 * type  = where it sits on the body during try-on: "TOP", "DRESS" or "PANTS"
 */
data class Product(
    val id: Int,
    val name: String,
    val category: String,
    val price: Int,
    val oldPrice: Int,
    val rating: Double,
    val reviews: Int,
    val colorName: String,
    val color: Color,
    val sizes: List<String>,
    val image: Int,
    val type: String,
    val description: String
)

/** A product in the cart, with its chosen size and quantity. */
data class CartItem(val product: Product, val size: String, val qty: Int = 1, val checked: Boolean = true)

val topSizes = listOf("XS", "S", "M", "L", "XL")
val waistSizes = listOf("28", "30", "32", "34", "36")

val catalog = listOf(
    Product(1, "Blush Bell-Sleeve Blouse", "Tops", 1499, 2199, 4.5, 120, "Blush", Color(0xFFF0B7B3), topSizes, R.drawable.top_blush_blouse, "TOP",
        "Soft V-neck blouse with dramatic bell sleeves. Tuck it into jeans for an easy, elegant look."),
    Product(2, "Azure Floral Midi Dress", "Dresses", 2499, 3499, 4.6, 214, "Sky Blue", Color(0xFF8DB5E8), topSizes, R.drawable.dress_azure_floral, "DRESS",
        "Smocked puff-sleeve midi dress in a breezy floral print. Perfect for brunches and summer days."),
    Product(3, "Striped Boat-Neck Top", "Tops", 1299, 1799, 4.3, 98, "Navy / Ivory", Color(0xFF2E3552), topSizes, R.drawable.top_striped_knit, "TOP",
        "Classic breton stripes with mustard trims. A timeless everyday layer."),
    Product(4, "Rose Straight Jeans", "Jeans", 1899, 2599, 4.5, 188, "Rose", Color(0xFFE9B4B8), waistSizes, R.drawable.pants_rose_denim, "PANTS",
        "High-rise straight-leg jeans in a soft rose wash. Comfortable stretch denim."),
    Product(5, "Ruffled Floral Top", "Tops", 1199, 1699, 4.4, 132, "Red", Color(0xFFD23A3A), topSizes, R.drawable.top_red_floral, "TOP",
        "Off-shoulder peplum top with puff sleeves and a vivid floral print."),
    Product(6, "Emerald Tulle Dress", "Dresses", 3299, 4599, 4.8, 76, "Emerald", Color(0xFF1E8A63), topSizes, R.drawable.dress_emerald_tulle, "DRESS",
        "Layered tulle dress with a wrap bodice. Made for weddings, parties and every photo."),
    Product(7, "Midnight Fuji Tee", "T-Shirts", 899, 1299, 4.4, 301, "Navy", Color(0xFF1C2140), topSizes, R.drawable.tee_midnight_fuji, "TOP",
        "Relaxed cotton tee with a minimal mountain print."),
    Product(8, "Sand Straight Chinos", "Jeans", 1599, 2199, 4.2, 71, "Cream", Color(0xFFEDE6D6), waistSizes, R.drawable.pants_sand_chinos, "PANTS",
        "Clean straight-fit chinos in a light sand shade. Dress them up or down."),
    Product(9, "Sunset Print Maxi Dress", "Dresses", 2799, 3899, 4.2, 54, "Orange", Color(0xFFF07A2A), topSizes, R.drawable.dress_sunset_print, "DRESS",
        "Statement sleeveless maxi dress with a bold print and a full skirt."),
    Product(10, "Dalmatian Graphic Tee", "T-Shirts", 999, 1399, 4.1, 87, "Black", Color(0xFF1A1A1A), topSizes, R.drawable.tee_dalmatian, "TOP",
        "Soft black tee with a playful dalmatian graphic."),
    Product(11, "Harbour Print Shirt", "Shirts", 1699, 2399, 4.3, 64, "Sand", Color(0xFFD8CFC0), topSizes, R.drawable.shirt_harbour_print, "TOP",
        "Oversized short-sleeve shirt with an artistic harbour print."),
    Product(12, "Distressed Denim", "Jeans", 2199, 2999, 4.0, 45, "Blue", Color(0xFF5C7BA6), waistSizes, R.drawable.pants_distressed_denim, "PANTS",
        "Heavily distressed straight jeans with patchwork details.")
)

val categories = listOf("All", "Tops", "Dresses", "Jeans", "T-Shirts", "Shirts")

/** Sample model photos on the Try-On screen. */
val sampleModels = listOf(R.drawable.sample_model_1, R.drawable.sample_model_2, R.drawable.sample_model_3, R.drawable.sample_model_4)

/** Products that go well with the one being tried on ("Complete Your Look"). */
fun pairsWith(product: Product): List<Product> = when (product.type) {
    "PANTS" -> catalog.filter { it.type == "TOP" }
    "TOP" -> catalog.filter { it.type == "PANTS" }
    else -> catalog.filter { it.type == "DRESS" && it.id != product.id } + catalog.filter { it.type == "TOP" }.take(2)
}

// =====================================================================
//  SQLite database: user accounts (Sign Up / Login)
//  Passwords are never stored. We store a random salt and a PBKDF2 hash of the password,
//  so even someone who copies the database cannot read the passwords.
// =====================================================================

data class User(val name: String, val email: String, val phone: String)

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

class UserDatabase(context: Context) : SQLiteOpenHelper(context, "tryverse.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (email TEXT PRIMARY KEY, name TEXT, phone TEXT, salt TEXT, hash TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    /** Returns false if the email is already registered. (Queries use ? placeholders, so no SQL injection.) */
    fun addUser(name: String, email: String, phone: String, password: String): Boolean {
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply {
            put("email", email.lowercase()); put("name", name); put("phone", phone)
            put("salt", b64(salt)); put("hash", b64(PasswordHasher.hash(password, salt)))
        }
        return writableDatabase.insert("users", null, values) != -1L
    }

    fun login(email: String, password: String): User? =
        readableDatabase.rawQuery("SELECT name, phone, salt, hash FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            if (it.moveToFirst() && PasswordHasher.matches(password, unb64(it.getString(2)), unb64(it.getString(3))))
                User(it.getString(0), email.lowercase(), it.getString(1))
            else null
        }

    fun getUser(email: String): User? =
        readableDatabase.rawQuery("SELECT name, phone FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            if (it.moveToFirst()) User(it.getString(0), email.lowercase(), it.getString(1)) else null
        }

    fun updateUser(email: String, name: String, phone: String) {
        val values = ContentValues().apply { put("name", name); put("phone", phone) }
        writableDatabase.update("users", values, "email = ?", arrayOf(email.lowercase()))
    }
}

/** Input checks used by Sign Up / Login / Edit Profile. */
object Validate {
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$")
    private val phoneRegex = Regex("^[+]?[0-9 ]{8,16}$")
    fun email(e: String) = emailRegex.matches(e)
    fun phone(p: String) = phoneRegex.matches(p)
    fun name(n: String) = n.trim().length in 2..40
    /** At least 8 characters with a letter and a number. */
    fun password(p: String) = p.length in 8..64 && p.any { it.isLetter() } && p.any { it.isDigit() }
}

// =====================================================================
//  File handling: session.txt (who is logged in) and orders.csv
// =====================================================================

fun saveSession(context: Context, email: String) = File(context.filesDir, "session.txt").writeText(email)

fun loadSession(context: Context): String? {
    val file = File(context.filesDir, "session.txt")
    return if (file.exists()) file.readText().trim().ifEmpty { null } else null
}

fun clearSession(context: Context) {
    File(context.filesDir, "session.txt").delete()
}

data class Order(val id: String, val date: String, val items: String, val total: Int, val status: String, val productId: Int)

private fun ordersFile(context: Context) = File(context.filesDir, "orders.csv")

/** Adds one line to orders.csv:  id,date,items,total,status,productId */
fun saveOrder(context: Context, items: List<CartItem>, total: Int) {
    val id = "TV" + (System.currentTimeMillis() % 100000)
    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
    val names = items.joinToString(" + ") { it.product.name }.replace(",", " ")
    ordersFile(context).appendText("$id,$date,$names,$total,Processing,${items.first().product.id}\n")
}

/** Reads all orders from orders.csv (newest first). */
fun loadOrders(context: Context): List<Order> {
    val file = ordersFile(context)
    if (!file.exists()) return emptyList()
    return file.readLines().mapNotNull { line ->
        val p = line.split(",")
        if (p.size < 6) null else Order(p[0], p[1], p[2], p[3].toIntOrNull() ?: 0, p[4], p[5].toIntOrNull() ?: 1)
    }.reversed()
}

fun clearOrders(context: Context) {
    ordersFile(context).delete()
}
