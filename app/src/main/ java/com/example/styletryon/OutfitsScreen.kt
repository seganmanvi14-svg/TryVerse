package com.example.styletryon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
//  MY OUTFITS (lookbook)
//  Outfits made with mix & match in the live camera (top + bottom, or a dress)
//  are saved for the user in SQLite. From here they can be tried on live again,
//  renamed, deleted, or added to the cart in one tap.
// =====================================================================

@Composable
fun OutfitsScreen(
    outfits: List<SavedOutfit>,
    onBack: () -> Unit,
    onTryLive: (SavedOutfit) -> Unit,
    onAddAll: (SavedOutfit) -> Unit,
    onRename: (SavedOutfit, String) -> Unit,
    onDelete: (SavedOutfit) -> Unit
) {
    var renaming by remember { mutableStateOf<SavedOutfit?>(null) }
    var deleting by remember { mutableStateOf<SavedOutfit?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("My Outfits", onBack)
        if (outfits.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Checkroom, contentDescription = null, tint = Primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                DisplayTitle("No outfits saved yet", size = 24.sp)
                Text(
                    "In Live Try-On, pick a top and a bottom, then tap Save to keep the outfit here.",
                    color = Muted, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn {
                items(outfits, key = { it.id }) { outfit ->
                    OutfitCard(
                        outfit,
                        onTryLive = { onTryLive(outfit) },
                        onAddAll = { onAddAll(outfit) },
                        onRename = { renaming = outfit },
                        onDelete = { deleting = outfit }
                    )
                }
            }
        }
    }

    renaming?.let { outfit ->
        var name by remember(outfit.id) { mutableStateOf(outfit.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename outfit") },
            text = {
                OutlinedTextField(
                    name, { name = it.take(30) }, singleLine = true, label = { Text("Name (e.g. College Friday)") },
                    isError = name.isBlank(), colors = boxFieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) { onRename(outfit, name.trim()); renaming = null } }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { outfit ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${outfit.name}\"?") },
            confirmButton = { TextButton(onClick = { onDelete(outfit); deleting = null }) { Text("DELETE", color = Rose, letterSpacing = 1.sp) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("CANCEL", letterSpacing = 1.sp) } }
        )
    }
}

@Composable
fun OutfitCard(outfit: SavedOutfit, onTryLive: () -> Unit, onAddAll: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val soldOut = outfit.items.filter { (p, size) -> !p.hasSize(size) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().glass(CardShape).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DisplayTitle(outfit.name, size = 22.sp)
                    Text("Saved ${formatDate(outfit.time)}  ·  ${rupees(outfit.total)}", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onRename) { Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Ink) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted) }
            }
            // The pieces of the outfit, side by side
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                outfit.items.forEach { (p, size) ->
                    Column(Modifier.width(96.dp)) {
                        ProductImage(p, Modifier.size(96.dp, 128.dp), padding = 8)
                        Spacer(Modifier.height(4.dp))
                        Text(p.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                        Text("Size $size  ·  ${rupees(p.price)}", fontSize = 11.sp, color = if (p.hasSize(size)) Muted else Rose)
                    }
                }
            }
            if (soldOut.isNotEmpty()) {
                Text("Now out of stock: " + soldOut.joinToString { "${it.first.name} (${it.second})" }, color = Rose, fontSize = 12.sp)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZaraOutlinedButton("Try live", Modifier.weight(1f), icon = Icons.Default.Videocam, onClick = onTryLive)
                ZaraButton("Add all", Modifier.weight(1f), enabled = soldOut.size < outfit.items.size, onClick = onAddAll)
            }
        }
    }
}
