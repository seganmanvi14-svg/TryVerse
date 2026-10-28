package com.example.styletryon

import androidx.compose.ui.graphics.Color

// =====================================================================
//  Products
// =====================================================================

/**
 * One product in the shop.
 * image = garment photo in res/drawable-nodpi (background removed)
 * type  = where it sits on the body during try-on: "TOP", "DRESS" or "PANTS"
 * soldOut = sizes that are out of stock (all sizes = the product is out of stock)
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
    val description: String,
    val soldOut: List<String> = emptyList()
)

/** True if at least one size can still be bought. */
val Product.inStock: Boolean get() = sizes.any { it !in soldOut }

fun Product.hasSize(size: String) = size in sizes && size !in soldOut

/** Size picked at first: M (or waist 32) if it is in stock, otherwise the first size in stock. */
fun Product.defaultSize(): String = sizes[2].takeIf { hasSize(it) } ?: sizes.firstOrNull { hasSize(it) } ?: sizes[2]

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
        "Classic breton stripes with mustard trims. A timeless everyday layer.", soldOut = listOf("XS", "XL")),
    Product(4, "Rose Straight Jeans", "Jeans", 1899, 2599, 4.5, 188, "Rose", Color(0xFFE9B4B8), waistSizes, R.drawable.pants_rose_denim, "PANTS",
        "High-rise straight-leg jeans in a soft rose wash. Comfortable stretch denim."),
    Product(5, "Ruffled Floral Top", "Tops", 1199, 1699, 4.4, 132, "Red", Color(0xFFD23A3A), topSizes, R.drawable.top_red_floral, "TOP",
        "Off-shoulder peplum top with puff sleeves and a vivid floral print."),
    Product(6, "Emerald Tulle Dress", "Dresses", 3299, 4599, 4.8, 76, "Emerald", Color(0xFF1E8A63), topSizes, R.drawable.dress_emerald_tulle, "DRESS",
        "Layered tulle dress with a wrap bodice. Made for weddings, parties and every photo.", soldOut = listOf("M")),
    Product(7, "Midnight Fuji Tee", "T-Shirts", 899, 1299, 4.4, 301, "Navy", Color(0xFF1C2140), topSizes, R.drawable.tee_midnight_fuji, "TOP",
        "Relaxed cotton tee with a minimal mountain print."),
    Product(8, "Sand Straight Chinos", "Jeans", 1599, 2199, 4.2, 71, "Cream", Color(0xFFEDE6D6), waistSizes, R.drawable.pants_sand_chinos, "PANTS",
        "Clean straight-fit chinos in a light sand shade. Dress them up or down."),
    Product(9, "Sunset Print Maxi Dress", "Dresses", 2799, 3899, 4.2, 54, "Orange", Color(0xFFF07A2A), topSizes, R.drawable.dress_sunset_print, "DRESS",
        "Statement sleeveless maxi dress with a bold print and a full skirt.", soldOut = topSizes),
    Product(10, "Dalmatian Graphic Tee", "T-Shirts", 999, 1399, 4.1, 87, "Black", Color(0xFF1A1A1A), topSizes, R.drawable.tee_dalmatian, "TOP",
        "Soft black tee with a playful dalmatian graphic."),
    Product(11, "Harbour Print Shirt", "Shirts", 1699, 2399, 4.3, 64, "Sand", Color(0xFFD8CFC0), topSizes, R.drawable.shirt_harbour_print, "TOP",
        "Oversized short-sleeve shirt with an artistic harbour print."),
    Product(12, "Distressed Denim", "Jeans", 2199, 2999, 4.0, 45, "Blue", Color(0xFF5C7BA6), waistSizes, R.drawable.pants_distressed_denim, "PANTS",
        "Heavily distressed straight jeans with patchwork details.", soldOut = listOf("28", "36")),

    // ---- New arrivals ----
    Product(13, "White Puff-Sleeve Ruched Blouse", "Tops", 1199, 1699, 4.5, 86, "White", Color(0xFFF4F2EC), topSizes, R.drawable.top_white_puff_blouse, "TOP",
        "Square-neck crop blouse with ruched front and soft puff sleeves. Pairs with high-waist jeans or trousers."),
    Product(14, "Black Ribbed Knit Long-Sleeve Top", "Tops", 999, 1499, 4.6, 204, "Black", Color(0xFF1A1A1A), topSizes, R.drawable.top_black_ribbed, "TOP",
        "Fitted rib-knit top with a square neck and long sleeves. An everyday essential."),
    Product(15, "Light Blue Denim Shirt", "Shirts", 1799, 2499, 4.4, 118, "Light Blue", Color(0xFF8FB3D9), topSizes, R.drawable.shirt_denim_blue, "TOP",
        "Oversized washed-denim shirt with a chest pocket. Wear it buttoned up or open over a tee."),
    Product(16, "Olive Green Oversized T-Shirt", "T-Shirts", 799, 1199, 4.3, 265, "Olive", Color(0xFF5C6B3A), topSizes, R.drawable.tee_olive_oversized, "TOP",
        "Heavy cotton oversized tee with dropped shoulders in a deep olive green."),
    Product(17, "Minimal Sunset Print T-Shirt", "T-Shirts", 899, 1299, 4.2, 97, "White", Color(0xFFF4F2EC), topSizes, R.drawable.tee_sunset_print, "TOP",
        "Soft white cotton tee with a small minimal sunset print on the chest."),
    Product(18, "Black Satin Cowl-Neck Slip Dress", "Dresses", 2999, 4199, 4.7, 142, "Black", Color(0xFF1A1A1A), topSizes, R.drawable.dress_black_satin, "DRESS",
        "Fluid satin midi slip dress with a cowl neck and thin straps. Made for evenings out."),
    Product(19, "Yellow Floral Vintage Sundress", "Dresses", 2299, 3199, 4.5, 133, "Yellow", Color(0xFFF3D66B), topSizes, R.drawable.dress_yellow_floral, "DRESS",
        "Sweetheart-neck sundress with a fitted waist and a full floral skirt."),
    Product(20, "Lavender One-Shoulder Ruched Mini Dress", "Dresses", 2699, 3799, 4.6, 61, "Lavender", Color(0xFFC3A8E6), topSizes, R.drawable.dress_lavender_oneshoulder, "DRESS",
        "Ruched one-shoulder party dress with a flowing chiffon drape.", soldOut = topSizes),
    Product(21, "Black High-Waist Wide-Leg Trousers", "Trousers", 1799, 2499, 4.5, 176, "Black", Color(0xFF1A1A1A), waistSizes, R.drawable.pants_black_wideleg, "PANTS",
        "Pleated high-waist trousers with wide, floor-grazing legs."),
    Product(22, "Light-Wash Wide-Leg Jeans", "Jeans", 1999, 2799, 4.4, 189, "Light Blue", Color(0xFFA9C4E2), waistSizes, R.drawable.pants_lightwash_jeans, "PANTS",
        "Relaxed high-rise jeans in a light vintage wash with wide straight legs."),
    Product(23, "Beige Pleated Tailored Trousers", "Trousers", 1899, 2599, 4.3, 88, "Beige", Color(0xFFE3D7C1), waistSizes, R.drawable.pants_beige_pleated, "PANTS",
        "Smart pleated trousers in a soft beige. Great for college and internships."),
    Product(24, "Pastel Pink Embroidered Kurti", "Kurtis", 1499, 2199, 4.6, 154, "Pink", Color(0xFFF0AFC0), topSizes, R.drawable.kurti_pink_embroidered, "TOP",
        "Cotton A-line kurti with white floral embroidery and three-quarter sleeves. Pair it with trousers or jeans.")
)

fun productById(id: Int): Product? = catalog.firstOrNull { it.id == id }

val categories = listOf("All", "Tops", "Dresses", "Jeans", "Trousers", "T-Shirts", "Shirts", "Kurtis")

/** Sample model photos on the Try-On screen. */
val sampleModels = listOf(R.drawable.sample_model_1, R.drawable.sample_model_2, R.drawable.sample_model_3, R.drawable.sample_model_4)

/** Products that go well with the one being tried on ("Complete Your Look"), only ones in stock. */
fun pairsWith(product: Product): List<Product> = when (product.type) {
    "PANTS" -> catalog.filter { it.type == "TOP" }
    "TOP" -> catalog.filter { it.type == "PANTS" }
    else -> catalog.filter { it.type == "DRESS" && it.id != product.id } + catalog.filter { it.type == "TOP" }.take(2)
}.filter { it.inStock }
