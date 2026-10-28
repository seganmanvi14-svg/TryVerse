package com.example.styletryon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// =====================================================================
//  TRY-ON HISTORY
//  Every try-on picture (AI photo try-on and live camera captures) is saved as a
//  JPEG in the app's private folder (filesDir/looks) and listed in the SQLite
//  "looks" table for the logged-in user. Other apps cannot read this folder.
// =====================================================================

/** Saves a look into the history. Pictures are made smaller (max 900 px) to save space. */
fun saveLookToHistory(context: Context, db: UserDatabase, email: String, look: Bitmap, productId: Int) {
    if (email.isEmpty()) return
    try {
        val scale = minOf(1f, 900f / maxOf(look.width, look.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(look, (look.width * scale).toInt(), (look.height * scale).toInt(), true) else look
        val folder = File(context.filesDir, "looks").apply { mkdirs() }
        val file = File(folder, "look_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        db.addLook(email, file.absolutePath, productId)
    } catch (e: Exception) {
        // History is a nice extra; never crash the try-on because of it
    }
}

@Composable
fun TryOnHistoryScreen(db: UserDatabase, email: String, onBack: () -> Unit, onProduct: (Product) -> Unit) {
    val context = LocalContext.current
    var looks by remember(email) { mutableStateOf(db.loadLooks(email)) }
    var open by remember { mutableStateOf<SavedLook?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Try-On History", onBack)
        if (looks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                DisplayTitle("No try-ons yet", size = 24.sp)
                Text("Your AI try-ons and live camera captures will appear here.", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            Text("${looks.size} LOOKS", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(looks, key = { it.path }) { look ->
                    val product = productById(look.productId)
                    Column(Modifier.clickable { open = look }) {
                        LookImage(look.path, Modifier.fillMaxWidth().aspectRatio(0.75f).goldFrame())
                        Column(Modifier.padding(horizontal = 2.dp, vertical = 8.dp)) {
                            Text(product?.name ?: "My look", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDate(look.time), fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }

    // Big view of one look: share, delete or open the product
    open?.let { look ->
        val product = productById(look.productId)
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(product?.name ?: "My look") },
            text = { LookImage(look.path, Modifier.fillMaxWidth().aspectRatio(0.75f)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        BitmapFactory.decodeFile(look.path)?.let { shareLook(context, it) }
                    }) { Text("SHARE", letterSpacing = 1.sp) }
                    if (product != null) TextButton(onClick = { open = null; onProduct(product) }) { Text("VIEW ITEM", letterSpacing = 1.sp) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    File(look.path).delete()
                    db.deleteLook(email, look.path)
                    looks = looks.filter { it.path != look.path }
                    open = null
                    Toast.makeText(context, "Look deleted", Toast.LENGTH_SHORT).show()
                }) { Text("DELETE", color = Rose, letterSpacing = 1.sp) }
            }
        )
    }
}

/** A saved look from the app's files (decoded at half size for the grid). */
@Composable
fun LookImage(path: String, modifier: Modifier = Modifier) {
    val image = remember(path) {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
    }
    if (image != null) Image(image, contentDescription = "Saved look", contentScale = ContentScale.Crop, modifier = modifier)
    else Box(modifier.background(Sand))
}
