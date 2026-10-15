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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


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
    onShopNow: () -> Unit
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
                    Text("Search for tops, dresses, jeans...", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = { onCategory("All") },
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
//  Categories: search + chips + sort spinner + grid
// ---------------------------------------------------------------------

@Composable
fun CategoriesScreen(category: String, onCategory: (String) -> Unit, wishlist: List<Int>, onWish: (Product) -> Unit, onProduct: (Product) -> Unit) {
    var query by remember { mutableStateOf("") }
    val sortOptions = listOf("Popular", "Price: Low to High", "Price: High to Low", "Top Rated")
    var sort by remember { mutableStateOf(sortOptions[0]) }
    var sortOpen by remember { mutableStateOf(false) }

    var shown = catalog.filter {
        (category == "All" || it.category == category) &&
                (query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true))
    }
    shown = when (sort) {
        "Price: Low to High" -> shown.sortedBy { it.price }
        "Price: High to Low" -> shown.sortedByDescending { it.price }
        "Top Rated" -> shown.sortedByDescending { it.rating }
        else -> shown.sortedByDescending { it.reviews }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Categories", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search for tops, dresses, jeans...") },
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { FilterChip(selected = category == it, onClick = { onCategory(it) }, label = { Text(it) }) }
        }
        // Spinner (drop-down) for sorting
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} items", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortOpen = true }) {
                    Text("Sort: $sort", fontSize = 13.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; sortOpen = false })
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(shown.size) { i -> ProductCard(shown[i], wishlist.contains(shown[i].id), onWish, onProduct) }
        }
    }
}
