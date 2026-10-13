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
