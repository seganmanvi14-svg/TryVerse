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
