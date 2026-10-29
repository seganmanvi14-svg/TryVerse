package com.example.styletryon

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

/** Garment photo in a rounded frame with a soft spotlight behind it. */
@Composable
fun ProductImage(product: Product, modifier: Modifier = Modifier, padding: Int = 12) {
    Box(
        modifier.goldFrame().background(Brush.radialGradient(listOf(Spotlight, Sand))),
        contentAlignment = Alignment.Center
    ) {
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
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Star, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text("${product.rating}", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text(" (${product.reviews})", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Normal)
    }
}

fun rupees(amount: Int) = "₹%,d".format(amount)

/** Back arrow + serif title in the middle (+ optional action on the right). */
@Composable
fun TopBar(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", tint = Ink, modifier = Modifier.size(20.dp)) }
        Text(
            title, fontSize = 20.sp, fontFamily = Display, color = Ink,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Box(Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) { action() }
    }
}

/** Discount as a whole percent (0 if there is none). */
fun Product.offPercent(): Int = if (oldPrice > price) 100 - price * 100 / oldPrice else 0

/** Product card: rounded framed photo, heart, small discount tag, bold name, price and rating. */
@Composable
fun ProductCard(product: Product, wishlisted: Boolean, onWish: (Product) -> Unit, onClick: (Product) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable { onClick(product) }) {
        Box {
            ProductImage(product, Modifier.fillMaxWidth().aspectRatio(0.78f), padding = 14)
            if (!product.inStock) {
                Box(Modifier.matchParentSize().clip(Sharp).background(Cream.copy(alpha = 0.55f)))
                GlassChip("Out of stock", Modifier.align(Alignment.Center), dotColor = Rose)
            }
            if (product.offPercent() > 0) {
                Text(
                    "-${product.offPercent()}%", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(Pill).background(Color(0x99000000))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                GlassCircle(
                    if (wishlisted) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Save",
                    tint = if (wishlisted) Primary else Color(0xFFF4EEE2), size = 32.dp
                ) { onWish(product) }
            }
        }
        Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 4.dp)) {
            Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rupees(product.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Star, contentDescription = null, tint = Star, modifier = Modifier.size(12.dp))
                Text(" ${product.rating}", fontSize = 11.sp, color = Muted)
            }
        }
    }
}

/** Two products per row (for use inside scrolling columns). */
@Composable
fun ProductGrid(products: List<Product>, wishlist: List<Int>, onWish: (Product) -> Unit, onProduct: (Product) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        products.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> ProductCard(p, wishlist.contains(p.id), onWish, onProduct, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** – 1 +  in a glass pill. */
@Composable
fun QuantityStepper(qty: Int, onChange: (Int) -> Unit) {
    Row(Modifier.glass(Pill), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (qty > 1) onChange(qty - 1) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Less", tint = Ink, modifier = Modifier.size(16.dp))
        }
        Text("$qty", modifier = Modifier.width(24.dp), textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        IconButton(onClick = { onChange(qty + 1) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "More", tint = Ink, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------
//  Home (Noir: greeting, glass search, category rings, "New You" card, trending)
// ---------------------------------------------------------------------

/** Round category bubble with a champagne gradient ring (like a story ring). */
@Composable
fun CategoryRing(product: Product, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(Brush.sweepGradient(listOf(GoldDeep, GoldLight, Gold, GoldDeep))).padding(2.dp)
                .clip(CircleShape).background(Cream).padding(2.dp)
                .clip(CircleShape).background(Brush.radialGradient(listOf(Spotlight, Sand))),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(product.image), contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

/** Rounded campaign card: model photo, "NEW SEASON 01 / 04", big serif title, glass button. */
@Composable
fun CampaignBanner(product: Product, eyebrow: String, title: String, button: String, height: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(CardShape).border(1.dp, Gold.copy(alpha = 0.25f), CardShape).clickable { onClick() }) {
        DemoModelTryOn(product, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xD9000000), Color(0x33000000)))))
        Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(eyebrow, color = Gold)
                Text("   01 / 04", color = Color(0x99F4EEE2), fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(6.dp))
            DisplayTitle(title, size = 46.sp, color = Color(0xFFF4EEE2))
            Text("Effortless style\nfor every chapter.", color = Color(0xFFB9B2A6), fontSize = 12.sp, fontWeight = FontWeight.Normal)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(Pill).background(Color(0x33FFFFFF)).border(1.dp, Gold.copy(alpha = 0.5f), Pill).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(button, color = Color(0xFFF4EEE2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Color(0xFFF4EEE2), modifier = Modifier.size(14.dp))
            }
        }
        // Slide dashes
        Row(Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(16.dp).height(2.dp).background(Gold))
            repeat(3) { Box(Modifier.width(6.dp).height(2.dp).background(Color(0x88FFFFFF))) }
        }
    }
}

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
    val side = Modifier.padding(horizontal = 18.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Greeting
        item {
            Row(side.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).border(1.5.dp, Gold, CircleShape).background(Sand),
                    contentAlignment = Alignment.Center
                ) { Text(firstName.take(1).uppercase(), fontFamily = Display, fontSize = 20.sp, color = Primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hi, $firstName", fontFamily = Display, fontSize = 22.sp, color = Ink)
                    Text("Good to see you again.", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
                IconButton(onClick = { toggleBlackTheme(context) }) {
                    Icon(
                        if (ThemeState.dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (ThemeState.dark) "Light theme" else "Black theme", tint = Ink
                    )
                }
                IconButton(onClick = { Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Rounded.NotificationsNone, contentDescription = "Notifications", tint = Ink)
                }
            }
        }
        // Glass search (opens Categories) + filters
        item {
            Row(
                side.fillMaxWidth().height(48.dp).glass(Pill).clickable { onCategory("All") }.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search for dresses, jeans, tops...", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
                IconButton(onClick = onFilters) { Icon(Icons.Rounded.Tune, contentDescription = "Filters", tint = Primary) }
            }
        }
        // Category rings
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                categories.drop(1).forEach { cat -> CategoryRing(catalog.first { it.category == cat }, cat) { onCategory(cat) } }
            }
        }
        // "New You" campaign card
        item { Box(side) { CampaignBanner(catalog[1], "New season", "New\nYou", "Shop Now", 250, onShopNow) } }
        // Trending
        item {
            Row(side, verticalAlignment = Alignment.Bottom) {
                DisplayTitle("Trending Now", size = 22.sp)
                Spacer(Modifier.weight(1f))
                Text("See All", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onCategory("All") })
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(catalog.sortedByDescending { it.reviews }.take(6)) { p ->
                    ProductCard(p, wishlist.contains(p.id), onWish, onProduct, Modifier.width(140.dp))
                }
            }
        }
        // Just for you
        item { Box(side) { DisplayTitle("Just For You", size = 22.sp) } }
        item { Box(side) { ProductGrid(catalog.take(6), wishlist, onWish, onProduct) } }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        DisplayTitle(if (category == "All") "Shop All" else category, size = 32.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        // Rounded glass search field
        OutlinedTextField(
            value = query, onValueChange = { ShopState.query = it.take(40) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = Pill,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { ShopState.query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
            },
            placeholder = { Text("Search: blue dress, black jeans, floral...", fontSize = 13.sp, fontWeight = FontWeight.Normal) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearched(query); focus.clearFocus() }),
            colors = underlineFieldColors()
        )
        // Recent searches (saved for each user)
        if (query.isBlank() && history.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Recent searches", color = Muted)
                Spacer(Modifier.weight(1f))
                Text("Clear", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClearHistory() })
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
        // Category menu as text tabs
        TextTabs(categories, selected = category, onSelect = { onCategory(it) }, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        Hairline()
        // Item count, Filters button and the sort spinner (drop-down)
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} items", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showFilters = true }) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (filters.count > 0) "Filters (${filters.count})" else "Filters", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box {
                TextButton(onClick = { sortOpen = true }) {
                    Text("Sort: ${ShopState.sort.substringBefore(":")}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                DisplayTitle("No items found", size = 24.sp)
                Spacer(Modifier.height(6.dp))
                Text("Try another word (e.g. \"dress\", \"blue\", \"jeans\") or clear the filters.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (filters.count > 0 || category != "All") {
                    Spacer(Modifier.height(14.dp))
                    ZaraOutlinedButton("Clear filters") { ShopState.filters = ShopFilters(); onCategory("All") }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 4.dp, end = 4.dp)
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
                Eyebrow("Price: ${rupees(f.minPrice)} – ${rupees(f.maxPrice)}")
                RangeSlider(
                    value = f.minPrice.toFloat()..f.maxPrice.toFloat(),
                    onValueChange = { f = f.copy(minPrice = it.start.toInt(), maxPrice = it.endInclusive.toInt()) },
                    valueRange = 0f..MAX_PRICE.toFloat(),
                    steps = 7                                   // steps of ₹500
                )
                Eyebrow("Size", Modifier.padding(top = 8.dp, bottom = 4.dp))
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
                Eyebrow("Colour", Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colourFamilies.forEach { c ->
                        FilterChip(
                            selected = c in f.colours,
                            onClick = { f = f.copy(colours = if (c in f.colours) f.colours - c else f.colours + c) },
                            label = { Text(c) }
                        )
                    }
                }
                Eyebrow("Rating", Modifier.padding(top = 12.dp, bottom = 4.dp))
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
        confirmButton = { ZaraButton("Show $count items") { onApply(f) } },
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
                Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = Primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                DisplayTitle("Nothing saved yet", size = 24.sp)
                Text("Tap the heart on any product to save it here.", color = Muted, textAlign = TextAlign.Center)
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Big photo with the thumbnails on the left and glass buttons on top
        Box(Modifier.fillMaxWidth().height(440.dp)) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Spotlight, Sand)))) {
                if (showOnModel) DemoModelTryOn(product, Modifier.fillMaxSize())
                else ProductImage(product, Modifier.fillMaxSize().padding(start = 70.dp, end = 10.dp, top = 70.dp, bottom = 40.dp), padding = 10)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x80000000), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Cream)))
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(Icons.Rounded.ArrowBackIosNew, "Back", size = 38.dp) { onBack() }
                Spacer(Modifier.weight(1f))
                Box {
                    GlassCircle(Icons.Rounded.IosShare, "Share", size = 38.dp) { shareMenu = true }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        listOf("Copy link", "Share on WhatsApp", "Share on Instagram").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                shareMenu = false
                                Toast.makeText(context, "$option: ${product.name}", Toast.LENGTH_SHORT).show()
                            })
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassCircle(
                    if (wishlisted) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Save",
                    tint = if (wishlisted) Primary else Color(0xFFF4EEE2), size = 38.dp
                ) { onWish() }
            }
            // Thumbnails: product photo / on the model
            Column(Modifier.align(Alignment.CenterStart).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductImage(
                    product,
                    Modifier.size(50.dp, 68.dp).border(1.5.dp, if (!showOnModel) Gold else Color.Transparent, Sharp).clickable { showOnModel = false },
                    padding = 5
                )
                Box(
                    Modifier.size(50.dp, 68.dp).clip(Sharp).border(1.5.dp, if (showOnModel) Gold else Line, Sharp).clickable { showOnModel = true }
                ) { DemoModelTryOn(product, Modifier.fillMaxSize()) }
            }
            GlassChip("View on you", Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp), onClick = onTry)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            DisplayTitle(product.name, size = 28.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rupees(product.price), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.width(10.dp))
                Text(rupees(product.oldPrice), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Normal, textDecoration = TextDecoration.LineThrough)
                Spacer(Modifier.width(10.dp))
                Text("$discount% OFF", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RatingText(product)
            }
            Spacer(Modifier.height(10.dp))
            Text(product.description, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Normal)

            Spacer(Modifier.height(16.dp))
            Text("Color", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, Gold, CircleShape).padding(4.dp).clip(CircleShape).background(product.color))
                Spacer(Modifier.width(10.dp))
                Text(product.colorName, color = Muted, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Size", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("Size Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.clickable { showSizeGuide = true }.padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(6.dp))
            // Sizes that are out of stock are faded and crossed through
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                product.sizes.forEach { s ->
                    SizeBox(s, selected = chosenSize == s && product.hasSize(s), enabled = product.hasSize(s)) { chosenSize = s }
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                !product.inStock -> Text("Out of stock in all sizes. Check back soon!", color = Rose, fontSize = 13.sp)
                product.soldOut.isNotEmpty() -> Text("Sold out in: ${product.soldOut.joinToString(", ")}", color = Rose, fontSize = 12.sp)
            }
            // Size recommendation from the live camera body scan
            if (yourSize != null) {
                Row(
                    Modifier.padding(top = 8.dp).glass(Pill).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Straighten, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Your size: $yourSize (from your body scan)" + if (!product.hasSize(yourSize)) " – sold out" else "",
                        color = Ink, fontSize = 12.sp
                    )
                }
            } else {
                Text("Not sure of your size? Try-On → Live camera → Size.", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.width(16.dp))
                QuantityStepper(qty, onChange = { qty = it })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalShipping, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Free delivery  ·  Easy 7-day returns", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
            }
            Spacer(Modifier.height(16.dp))
            ZaraButton(
                if (product.inStock) "Add to Cart" else "Out of Stock", Modifier.fillMaxWidth(),
                enabled = product.hasSize(chosenSize)          // can't buy a size that is out of stock
            ) {
                repeat(qty) { onAdd(product, chosenSize) }
                Toast.makeText(context, "Added to cart: ${product.name} ($chosenSize) × $qty", Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.height(10.dp))
            ZaraOutlinedButton("Try-On", Modifier.fillMaxWidth(), icon = Icons.Rounded.PhotoCamera, onClick = onTry)
            Spacer(Modifier.height(24.dp))
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
        Row(verticalAlignment = Alignment.Bottom) {
            DisplayTitle("Wear it ", size = 28.sp)
            ScriptWord("with", size = 34.sp)
        }
        Text("Pieces that go with your ${product.name}.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        Hairline()
        LazyColumn(Modifier.weight(1f)) {
            items(pairsWith(product)) { p ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(p, Modifier.size(84.dp, 110.dp), padding = 8)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(p.colorName, color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(rupees(p.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { onWish(p) }) {
                                Icon(if (wishlist.contains(p.id)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Save", tint = Primary)
                            }
                            IconButton(
                                onClick = { onAdd(p); Toast.makeText(context, "${p.name} added to cart", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(GoldSweep)
                            ) { Icon(Icons.Rounded.Add, contentDescription = "Add", tint = OnInk, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Hairline()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ZaraButton("Go to Cart", Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowForward, onClick = onCart)
    }
}
