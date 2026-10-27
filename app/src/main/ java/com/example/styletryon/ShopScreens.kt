package com.example.styletryon

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------
//  Shared pieces
// ---------------------------------------------------------------------

/** Garment photo on a soft background. */
@Composable
fun ProductImage(product: Product, modifier: Modifier = Modifier, padding: Int = 12) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Sand), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(product.image),
            contentDescription = product.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(padding.dp)
        )
    }
}

@Composable
fun RatingText(product: Product) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = Star, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text("${product.rating}", fontSize = 12.sp, color = Ink)
        Text(" (${product.reviews})", fontSize = 12.sp, color = Muted)
    }
}

fun rupees(amount: Int) = "₹%,d".format(amount)

@Composable
fun TopBar(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Ink) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Box(Modifier.width(48.dp)) { action() }
    }
}

/** Product card with photo, heart, name, price and rating. */
@Composable
fun ProductCard(product: Product, wishlisted: Boolean, onWish: (Product) -> Unit, onClick: (Product) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.clickable { onClick(product) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box {
            ProductImage(product, Modifier.fillMaxWidth().aspectRatio(0.85f))
            if (!product.inStock) {
                // Faded photo with an "Out of stock" label
                Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.55f)))
                Text(
                    "OUT OF STOCK", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Ink.copy(alpha = 0.8f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            IconButton(
                onClick = { onWish(product) },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color.White)
            ) {
                Icon(if (wishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Save", tint = Rose, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(product.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
            Text(rupees(product.price), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
            RatingText(product)
        }
    }
}

/** Two products per row (for use inside scrolling columns). */
@Composable
fun ProductGrid(products: List<Product>, wishlist: List<Int>, onWish: (Product) -> Unit, onProduct: (Product) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        products.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> ProductCard(p, wishlist.contains(p.id), onWish, onProduct, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun QuantityStepper(qty: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (qty > 1) onChange(qty - 1) }, modifier = Modifier.size(28.dp).clip(CircleShape).background(PrimaryLight)) {
            Icon(Icons.Default.Remove, contentDescription = "Less", modifier = Modifier.size(15.dp))
        }
        Text("$qty", modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange(qty + 1) }, modifier = Modifier.size(28.dp).clip(CircleShape).background(PrimaryLight)) {
            Icon(Icons.Default.Add, contentDescription = "More", modifier = Modifier.size(15.dp))
        }
    }
}

// ---------------------------------------------------------------------
//  Home
// ---------------------------------------------------------------------

@Composable
fun HomeScreen(
    userName: String,
    wishlist: List<Int>,
    onWish: (Product) -> Unit,
    onProduct: (Product) -> Unit,
    onCategory: (String) -> Unit,
    onShopNow: () -> Unit,
    onFilters: () -> Unit = { onCategory("All") }
) {
    val firstName = userName.substringBefore(" ")
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hi, $firstName! 👋", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Find outfits that match your vibe", color = Muted, fontSize = 13.sp)
                }
                IconButton(onClick = { Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = Ink)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                    Text(firstName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Search bar (opens Categories)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .clickable { onCategory("All") }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Muted)
                    Spacer(Modifier.width(8.dp))
                    Text("Search: blue dress, black jeans, floral...", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = onFilters,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                ) { Icon(Icons.Default.Tune, contentDescription = "Filters", tint = Ink) }
            }
        }
        // Category circles
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                categories.drop(1).forEach { cat ->
                    val sample = catalog.first { it.category == cat }
                    Column(Modifier.clickable { onCategory(cat) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(58.dp).clip(CircleShape).background(sample.color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Image(painterResource(sample.image), contentDescription = cat, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(cat, fontSize = 11.sp, color = Ink)
                    }
                }
            }
        }
        // Banner
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7E8EE))) {
                Row(Modifier.height(165.dp)) {
                    Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("New Season\nNew You", fontSize = 24.sp, color = Brand, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Trendy fits, effortless styles", color = Muted, fontSize = 12.sp)
                        }
                        Button(onClick = onShopNow, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            Text("Try Now", fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    DemoModelTryOn(catalog[1], Modifier.width(130.dp).fillMaxHeight())
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trending Now", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("See All", color = Primary, fontSize = 13.sp, modifier = Modifier.clickable { onCategory("All") })
            }
        }
        item { ProductGrid(catalog.take(6), wishlist, onWish, onProduct) }
    }
}

// ---------------------------------------------------------------------
//  Search and filters
// ---------------------------------------------------------------------

/** "dresses" -> "dress", "tops" -> "top", so plural and singular searches both match. */
fun singular(word: String): String = when {
    word.length > 4 && word.endsWith("es") && (word.endsWith("sses") || word.endsWith("shes") || word.endsWith("ches")) -> word.dropLast(2)
    word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
    else -> word
}

/** Extra words for each garment type, so "bottoms", "pants" or "trousers" also find jeans. */
private fun typeWords(type: String) = when (type) {
    "PANTS" -> "pants trousers bottom bottoms jeans denim"
    "DRESS" -> "dress dresses gown frock one-piece"
    else -> "top tops upper"
}

/**
 * Smarter search: every word typed must appear somewhere in the product's name, category,
 * colour, colour family, description or type. Example: "blue dress", "black jeans", "floral".
 */
fun Product.matches(query: String): Boolean {
    val words = query.lowercase().split(" ", ",").map { singular(it.trim()) }.filter { it.isNotEmpty() }
    val text = "$name $category $colorName ${colourFamily(color)} $description ${typeWords(type)}".lowercase()
    return words.all { text.contains(it) }
}

/** Groups a product colour into a simple family for the colour filter (uses the colour's hue). */
fun colourFamily(c: Color): String {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    val h = hsv[0]
    val sat = hsv[1]
    val v = hsv[2]
    return when {
        sat < 0.18f && v > 0.8f -> "White"
        v < 0.22f || sat < 0.18f || (v < 0.35f && sat < 0.3f) -> "Black"
        h < 15f || h >= 345f -> if (sat < 0.45f) "Pink" else "Red"
        h < 70f -> "Yellow"
        h < 180f -> "Green"
        h < 255f -> "Blue"
        h < 290f -> "Purple"
        else -> "Pink"
    }
}

const val MAX_PRICE = 4000

/** Everything the Filters pop-up can choose. */
data class ShopFilters(
    val minPrice: Int = 0,
    val maxPrice: Int = MAX_PRICE,
    val sizes: Set<String> = emptySet(),
    val colours: Set<String> = emptySet(),
    val minRating: Double = 0.0,
    val inStockOnly: Boolean = false
) {
    /** How many filters are switched on (shown on the Filters button). */
    val count: Int
        get() = listOf(minPrice > 0 || maxPrice < MAX_PRICE, sizes.isNotEmpty(), colours.isNotEmpty(), minRating > 0, inStockOnly).count { it }

    fun allows(p: Product) =
        p.price in minPrice..maxPrice &&
                (sizes.isEmpty() || p.sizes.any { it in sizes && p.hasSize(it) }) &&
                (colours.isEmpty() || colourFamily(p.color) in colours) &&
                p.rating >= minRating &&
                (!inStockOnly || p.inStock)
}

/** Search text, sort and filters are kept here so they are still set after opening a product and coming back. */
object ShopState {
    var query by mutableStateOf("")
    var sort by mutableStateOf("Popular")
    var filters by mutableStateOf(ShopFilters())

    fun reset() {
        query = ""; sort = "Popular"; filters = ShopFilters()
    }
}

val sortOptions = listOf("Popular", "Price: Low to High", "Price: High to Low", "Top Rated")

// ---------------------------------------------------------------------
//  Categories: search (with history) + chips + sort spinner + filters + grid
// ---------------------------------------------------------------------

@Composable
fun CategoriesScreen(
    category: String,
    onCategory: (String) -> Unit,
    wishlist: List<Int>,
    onWish: (Product) -> Unit,
    onProduct: (Product) -> Unit,
    history: List<String> = emptyList(),            // this user's recent searches (from SQLite)
    onSearched: (String) -> Unit = {},              // save a search to the history
    onClearHistory: () -> Unit = {},
    startWithFilters: Boolean = false
) {
    val focus = LocalFocusManager.current
    val query = ShopState.query
    val filters = ShopState.filters
    var sortOpen by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(startWithFilters) }

    var shown = catalog.filter {
        (category == "All" || it.category == category) && (query.isBlank() || it.matches(query)) && filters.allows(it)
    }
    shown = when (ShopState.sort) {
        "Price: Low to High" -> shown.sortedBy { it.price }
        "Price: High to Low" -> shown.sortedByDescending { it.price }
        "Top Rated" -> shown.sortedByDescending { it.rating }
        else -> shown.sortedByDescending { it.reviews }
    }
    // Opening a product from a search counts as a finished search
    val open: (Product) -> Unit = { p -> if (query.isNotBlank()) onSearched(query); onProduct(p) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Categories", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query, onValueChange = { ShopState.query = it.take(40) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { ShopState.query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
            },
            placeholder = { Text("Search: blue dress, black jeans, floral...") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearched(query); focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
        )
        // Recent searches (saved for each user)
        if (query.isBlank() && history.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("Clear", color = Primary, fontSize = 12.sp, modifier = Modifier.clickable { onClearHistory() })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                history.forEach { h ->
                    AssistChip(
                        onClick = { ShopState.query = h; onSearched(h) },
                        label = { Text(h) },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { FilterChip(selected = category == it, onClick = { onCategory(it) }, label = { Text(it) }) }
        }
        // Item count, Filters button and the sort spinner (drop-down)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} items", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showFilters = true }) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (filters.count > 0) "Filters (${filters.count})" else "Filters", fontSize = 13.sp)
            }
            Box {
                TextButton(onClick = { sortOpen = true }) {
                    Text("Sort: ${ShopState.sort.substringBefore(":")}", fontSize = 13.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { ShopState.sort = option; sortOpen = false })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SearchOff, contentDescription = null, tint = Muted, modifier = Modifier.size(48.dp))
                Text("No items found", fontWeight = FontWeight.Bold, color = Ink)
                Text("Try another word (e.g. \"dress\", \"blue\", \"jeans\") or clear the filters.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (filters.count > 0 || category != "All") {
                    TextButton(onClick = { ShopState.filters = ShopFilters(); onCategory("All") }) { Text("Clear filters") }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(shown.size) { i -> ProductCard(shown[i], wishlist.contains(shown[i].id), onWish, open) }
        }
    }

    if (showFilters) {
        FiltersDialog(filters, onApply = { ShopState.filters = it; showFilters = false }, onDismiss = { showFilters = false })
    }
}

/** Pop-up to filter by price (range slider), size, colour, rating and stock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersDialog(start: ShopFilters, onApply: (ShopFilters) -> Unit, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(start) }
    val colourFamilies = remember { catalog.map { colourFamily(it.color) }.distinct() }
    val count = catalog.count { f.allows(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Price: ${rupees(f.minPrice)} – ${rupees(f.maxPrice)}", fontWeight = FontWeight.SemiBold)
                RangeSlider(
                    value = f.minPrice.toFloat()..f.maxPrice.toFloat(),
                    onValueChange = { f = f.copy(minPrice = it.start.toInt(), maxPrice = it.endInclusive.toInt()) },
                    valueRange = 0f..MAX_PRICE.toFloat(),
                    steps = 7                                   // steps of ₹500
                )
                Text("Size", fontWeight = FontWeight.SemiBold)
                listOf(topSizes, waistSizes).forEach { row ->
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { size ->
                            FilterChip(
                                selected = size in f.sizes,
                                onClick = { f = f.copy(sizes = if (size in f.sizes) f.sizes - size else f.sizes + size) },
                                label = { Text(size) }
                            )
                        }
                    }
                }
                Text("Colour", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colourFamilies.forEach { c ->
                        FilterChip(
                            selected = c in f.colours,
                            onClick = { f = f.copy(colours = if (c in f.colours) f.colours - c else f.colours + c) },
                            label = { Text(c) }
                        )
                    }
                }
                Text("Rating", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.0 to "Any", 4.0 to "4.0 ★ & up", 4.5 to "4.5 ★ & up").forEach { (value, label) ->
                        FilterChip(selected = f.minRating == value, onClick = { f = f.copy(minRating = value) }, label = { Text(label) })
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { f = f.copy(inStockOnly = !f.inStockOnly) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide out of stock items", Modifier.weight(1f))
                    Switch(checked = f.inStockOnly, onCheckedChange = { f = f.copy(inStockOnly = it) })
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(f) }) { Text("Show $count items") } },
        dismissButton = { TextButton(onClick = { f = ShopFilters() }) { Text("Reset") } }
    )
}

// ---------------------------------------------------------------------
//  Saved items (wishlist)
// ---------------------------------------------------------------------

@Composable
fun SavedScreen(wishlist: List<Int>, onWish: (Product) -> Unit, onProduct: (Product) -> Unit, onBack: () -> Unit) {
    val items = catalog.filter { wishlist.contains(it.id) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Saved Items", onBack)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Rose, modifier = Modifier.size(56.dp))
                Text("Nothing saved yet", fontWeight = FontWeight.Bold)
                Text("Tap the ♡ on any product to save it here.", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            ProductGrid(items, wishlist, onWish, onProduct)
        }
    }
}

// ---------------------------------------------------------------------
//  Product detail
// ---------------------------------------------------------------------

/** [yourSize] = size suggested from the user's body scan in Live Try-On (null if they haven't scanned). */
@Composable
fun ProductDetailScreen(
    product: Product, wishlisted: Boolean, onBack: () -> Unit, onWish: () -> Unit,
    onAdd: (Product, String) -> Unit, onTry: () -> Unit, yourSize: String? = null
) {
    val context = LocalContext.current
    var chosenSize by remember(product.id) { mutableStateOf(yourSize?.takeIf { product.hasSize(it) } ?: product.defaultSize()) }
    var qty by remember(product.id) { mutableIntStateOf(1) }
    var showOnModel by remember(product.id) { mutableStateOf(false) }
    var showSizeGuide by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    val discount = 100 - product.price * 100 / product.oldPrice

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onWish) {
                Icon(if (wishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Save", tint = Rose)
            }
            Box {
                IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                    listOf("Copy link", "Share on WhatsApp", "Share on Instagram").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            shareMenu = false
                            Toast.makeText(context, "$option: ${product.name}", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        }
        // Big image + thumbnails (product photo / on the model)
        Row(Modifier.height(330.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Sand)) {
                if (showOnModel) DemoModelTryOn(product, Modifier.fillMaxSize())
                else ProductImage(product, Modifier.fillMaxSize(), padding = 20)
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductImage(
                    product,
                    Modifier.size(56.dp, 70.dp).border(2.dp, if (!showOnModel) Primary else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { showOnModel = false },
                    padding = 6
                )
                Box(
                    Modifier.size(56.dp, 70.dp).clip(RoundedCornerShape(16.dp))
                        .border(2.dp, if (showOnModel) Primary else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { showOnModel = true }
                ) { DemoModelTryOn(product, Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(product.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            RatingText(product)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rupees(product.price), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.width(8.dp))
            Text(rupees(product.oldPrice), color = Muted, textDecoration = TextDecoration.LineThrough)
            Spacer(Modifier.width(8.dp))
            Text(
                "$discount% OFF", color = Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Rose.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(product.description, color = Muted, fontSize = 13.sp)

        Spacer(Modifier.height(14.dp))
        Text("Color: ${product.colorName}", fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).border(2.dp, Primary, CircleShape).padding(4.dp).clip(CircleShape).background(product.color))

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSizeGuide = true }) {
                Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Size Guide", fontSize = 12.sp)
            }
        }
        // Sizes that are out of stock are greyed out and crossed through
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            product.sizes.forEach { s ->
                FilterChip(
                    selected = chosenSize == s && product.hasSize(s),
                    onClick = { chosenSize = s },
                    enabled = product.hasSize(s),
                    label = { Text(s, textDecoration = if (product.hasSize(s)) null else TextDecoration.LineThrough) }
                )
            }
        }
        when {
            !product.inStock -> Text("Out of stock in all sizes. Check back soon!", color = Rose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            product.soldOut.isNotEmpty() -> Text("Sold out in: ${product.soldOut.joinToString(", ")}", color = Rose, fontSize = 12.sp)
        }
        // Size recommendation from the live camera body scan
        if (yourSize != null) {
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(PrimaryLight).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Straighten, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Your size: $yourSize (from your body scan)" + if (!product.hasSize(yourSize)) " – sold out" else "",
                    color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Text("Not sure of your size? Open Try This On → Live Camera → Size.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantity", fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.width(16.dp))
            QuantityStepper(qty, onChange = { qty = it })
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onTry, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight, contentColor = Primary)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Try This On")
            }
            Button(
                onClick = {
                    repeat(qty) { onAdd(product, chosenSize) }
                    Toast.makeText(context, "Added to cart: ${product.name} ($chosenSize) × $qty", Toast.LENGTH_SHORT).show()
                },
                enabled = product.hasSize(chosenSize),          // can't buy a size that is out of stock
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (product.inStock) "Add to Cart" else "Out of Stock")
            }
        }
    }

    if (showSizeGuide) {
        AlertDialog(
            onDismissRequest = { showSizeGuide = false },
            title = { Text("Size Guide (inches)") },
            text = {
                Column {
                    listOf("Size   Chest   Waist", "XS      32       26", "S        34       28", "M        36       30", "L         38       32", "XL       40       34")
                        .forEach { Text(it, fontFamily = FontFamily.Monospace) }
                }
            },
            confirmButton = { TextButton(onClick = { showSizeGuide = false }) { Text("Got it") } }
        )
    }
}

// ---------------------------------------------------------------------
//  Complete Your Look
// ---------------------------------------------------------------------

@Composable
fun CompleteLookScreen(product: Product, wishlist: List<Int>, onWish: (Product) -> Unit, onAdd: (Product) -> Unit, onBack: () -> Unit, onCart: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Complete Your Look", onBack)
        Text("Pair your ${product.name} with these ✨", color = Muted, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pairsWith(product)) { p ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(p, Modifier.size(76.dp, 86.dp), padding = 6)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(p.colorName, color = Muted, fontSize = 12.sp)
                            Text(rupees(p.price), fontWeight = FontWeight.Bold, color = Ink)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { onWish(p) }) {
                                Icon(if (wishlist.contains(p.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Save", tint = Rose)
                            }
                            IconButton(
                                onClick = { onAdd(p); Toast.makeText(context, "${p.name} added to cart", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(PrimaryLight)
                            ) { Icon(Icons.Default.Add, contentDescription = "Add", tint = Primary) }
                        }
                    }
                }
            }
        }
        PrimaryButton("Go to Cart", Modifier.fillMaxWidth(), Icons.Default.ArrowForward, onCart)
    }
}
