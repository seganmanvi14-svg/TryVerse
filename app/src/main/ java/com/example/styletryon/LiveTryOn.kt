// =====================================================================
// LiveTryOn.kt = LIVE CAMERA TRY-ON (the clothes move with you in real time).
// Every camera frame: CameraX gives the picture -> ML Kit Pose finds 33 body points (we use shoulders,
// elbows, wrists, hips, ankles) -> ML Kit Selfie Segmentation finds the person's outline ->
// drawGarmentMesh (GarmentFit.kt) bends the garment photo onto the body -> drawn over the camera.
// Also here: mix & match (top + bottom), filters, capture photo, size suggestion, save outfit.
// =====================================================================
package com.example.styletryon

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// =====================================================================
//  LIVE CAMERA TRY-ON
//  1. CameraX shows the camera.
//  2. ML Kit finds the joints (Pose Detection) and the person's outline (Selfie
//     Segmentation) in each frame, so we know this person's shape and size.
//  3. The real garment photo is fitted to that body: stretched to the measured
//     shoulders / waist / hips, with the sleeves turned to follow the arms.
//  4. Mix & match: a top and a bottom can be worn together as one outfit.
// =====================================================================

// Colour filters (a see-through colour drawn over the camera): None, Warm, Cool, Rosy, Vintage
val cameraFilters = listOf(
    "None" to Color.Transparent,
    "Warm" to Color(0x33FF9A3C),
    "Cool" to Color(0x333C8DFF),
    "Rosy" to Color(0x33FF6F91),
    "Vintage" to Color(0x40A07850)
)

// Groups in the Looks tab so a top and a bottom are easy to find
val lookGroups = listOf("All", "Tops", "Bottoms", "Dresses")

// Is this product in the chosen group? (Tops = TOP, Bottoms = PANTS, Dresses = DRESS)
fun inGroup(p: Product, group: String) = when (group) {
    "Tops" -> p.type == "TOP"
    "Bottoms" -> p.type == "PANTS"
    "Dresses" -> p.type == "DRESS"
    else -> true
}

/**
 * Mix & match: a top and a bottom are worn together, a dress replaces both,
 * and tapping something already worn takes it off. Bottoms come first in the list
 * so they are drawn under the top.
 */
// *** MIX & MATCH RULES ***
fun wear(outfit: List<Product>, p: Product): List<Product> = when {
    // Tapping something you're already wearing takes it off
    outfit.any { it.id == p.id } -> outfit.filter { it.id != p.id }
    // A dress replaces everything
    p.type == "DRESS" -> listOf(p)
    // New pants: keep the top, pants go FIRST (drawn under the top)
    p.type == "PANTS" -> listOf(p) + outfit.filter { it.type == "TOP" }
    // New top: keep the pants, the top goes LAST (drawn over the pants)
    else -> outfit.filter { it.type == "PANTS" } + p
}

/**
 * heightCm   = the user's height (0 = not entered yet), needed for the size suggestion
 * onHeight   = saves a newly entered height
 * onMeasured = saves the measured body size to the user's profile
 * onCaptured = adds a captured picture to the try-on history (with the garment it shows)
 * startOutfit / startSizes = a saved outfit to wear straight away (from My Outfits)
 * onSaveOutfit = saves the current outfit (each garment with its chosen size)
 */
// *** LIVE TRY-ON SCREEN ***
@Composable
fun LiveTryOnScreen(
    startProduct: Product,
    onClose: () -> Unit,
    onAddToCart: (Product, String) -> Unit,
    heightCm: Int = 0,
    onHeight: (Int) -> Unit = {},
    onMeasured: (BodySize) -> Unit = {},
    onCaptured: (Bitmap, Int) -> Unit = { _, _ -> },
    startOutfit: List<Product>? = null,
    startSizes: Map<Int, String> = emptyMap(),
    onSaveOutfit: (List<Pair<Product, String>>) -> Unit = {}
) {
    val context = LocalContext.current
    // Do we have CAMERA permission? If not, ask once when the screen opens
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) Toast.makeText(context, "Camera permission is needed for live try-on", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    // What the person is wearing now (starts with the product they opened, or a saved outfit)
    var outfit by remember { mutableStateOf(startOutfit?.takeIf { it.isNotEmpty() } ?: listOf(startProduct)) }   // what the person is wearing now
    // Front / back camera, flash on/off
    var frontCamera by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    // The body found in the latest camera frame (null = no person found)
    var frame by remember { mutableStateOf<PoseFrame?>(null) }   // body points in camera-image pixels
    // How far the user dragged the garment with a finger (to fine-tune its position)
    var dragShift by remember { mutableStateOf(Offset.Zero) }     // moved with a finger (touch listener)
    // Bottom sheet tab (Looks / Filters / Size), the chosen group and filter
    var tab by remember { mutableStateOf("Looks") }
    var group by remember { mutableStateOf("All") }
    var filter by remember { mutableStateOf(cameraFilters[0]) }

    // Size suggestion: running average of the body measured in each camera frame
    // *** SIZE SUGGESTION ***
    // height from the profile, and a running average of the body measured in each frame.
    // ready = enough frames measured (30) to trust the result
    var height by remember { mutableIntStateOf(heightCm) }
    var heightText by remember { mutableStateOf("") }
    var measured by remember { mutableStateOf<BodySize?>(null) }
    var samples by remember { mutableIntStateOf(0) }
    val ready = measured != null && samples >= SIZE_SAMPLES
    // The suggested size for a product (★ in the size list)
    fun suggestionFor(p: Product): String? = if (ready) recommendedSize(p, measured!!) else null

    // Chosen size of each garment (a bigger size is drawn a little wider)
    // The size chosen for each garment. A bigger size is drawn 5% wider per size, a smaller one 5% narrower
    val chosenSizes = remember { mutableStateMapOf<Int, String>().apply { putAll(startSizes) } }
    fun sizeOf(p: Product) = chosenSizes[p.id] ?: p.defaultSize()
    fun scaleOf(p: Product) = 1f + (p.sizes.indexOf(sizeOf(p)) - 2) * 0.05f

    // Garment photos, loaded once each
    // Garment photos, loaded from res/drawable only once each (faster)
    val garments = remember { mutableMapOf<Int, Bitmap>() }
    fun garmentOf(p: Product): Bitmap = garments.getOrPut(p.image) { BitmapFactory.decodeResource(context.resources, p.image) }

    // *** CLOTHES ARE DRAWN ON THE BODY HERE *** (each garment, bottom first, then the top)
    fun drawOutfit(canvas: android.graphics.Canvas, body: Body) {
        outfit.forEach { p -> drawGarmentMesh(canvas, garmentOf(p), p, body, scaleOf(p), dragShift) }
    }

    // CameraX controller: we use IMAGE_ANALYSIS so every frame is sent to ML Kit
    val cameraController = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_ANALYSIS) }
    }
    // The view that shows the camera picture. COMPATIBLE mode lets us copy the frame for Capture
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // lets us grab the frame for Capture
        }
    }

    // Capture: camera frame + outfit + filter -> saved to the phone's Pictures folder
    // *** CAPTURE ***
    // Copy the camera frame, draw the outfit + filter on it, save it to Pictures/TryVerse and the Try-On History
    fun capture() {
        val photo = previewView.bitmap?.copy(Bitmap.Config.ARGB_8888, true)
        if (photo == null) {
            Toast.makeText(context, "Camera not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        val canvas = android.graphics.Canvas(photo)
        val b = frame?.toScreen(photo.width.toFloat(), photo.height.toFloat())
        if (b != null) drawOutfit(canvas, b)
        if (filter.second != Color.Transparent) canvas.drawColor(filter.second.toArgb())
        val saved = saveToGallery(context, photo) != null
        onCaptured(photo, outfit.lastOrNull()?.id ?: 0)          // also kept in Try-On History
        Toast.makeText(context, if (saved) "Look saved to Pictures/TryVerse and your history" else "Could not save photo", Toast.LENGTH_SHORT).show()
    }

    // Adds every garment in the outfit to the cart (skipping sizes that are out of stock)
    // Adds every garment of the outfit to the cart in its chosen size (skips sizes that are sold out)
    fun addOutfit() {
        if (outfit.isEmpty()) {
            Toast.makeText(context, "Pick something to wear first", Toast.LENGTH_SHORT).show()
            return
        }
        val (ok, soldOut) = outfit.partition { it.hasSize(sizeOf(it)) }
        ok.forEach { onAddToCart(it, sizeOf(it)) }
        val message = when {
            ok.isEmpty() -> "Sorry, this is out of stock"
            soldOut.isEmpty() -> "Added to cart: " + ok.joinToString(" + ") { "${it.name} (${sizeOf(it)})" }
            else -> "Added ${ok.size} item(s). Out of stock: " + soldOut.joinToString { it.name }
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // ---------------- Camera area ----------------
        // ---------------- CAMERA AREA ----------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (granted) {
                // *** EVERY CAMERA FRAME COMES IN HERE (with the body found by ML Kit) ***
                CameraWithPose(cameraController, previewView, frontCamera, onFrame = { f ->
                    frame = f
                    // Measure the body in this frame (only once the height is known and the whole body is visible)
                    // Measure the body in this frame (only if the height is known and the whole body is visible)
                    val m = if (f != null && height > 0) measureBody(f.body, height) else null
                    if (m != null) {
                        measured = averageSize(measured, m)
                        samples++
                    }
                })
                // Garment layer; drag with a finger to fine-tune its position
                // The GARMENT LAYER over the camera. Drag with a finger to move the clothes a little
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            dragShift += amount
                        }
                    }
                ) {
                    // Camera-image pixels -> screen pixels, then draw the outfit and the filter colour
                    val b = frame?.toScreen(size.width, size.height)      // camera pixels -> screen pixels
                    if (b != null) drawIntoCanvas { drawOutfit(it.nativeCanvas, b) }
                    drawRect(filter.second)
                }
            } else {
                // No camera permission: show the demo model instead
                // No camera permission: show the outfit on the demo model photo instead
                DemoModelTryOn(outfit.lastOrNull(), Modifier.fillMaxSize(), under = if (outfit.size > 1) outfit.first() else null)
            }

            // Top bar: close, LIVE TRY-ON, flash
            // Top bar: close, LIVE TRY-ON label, flash button (flash works only with the back camera)
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Default.Close) { onClose() }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF8A5CB0), Color(0xFF5E6FB8))))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(6.dp))
                    Text(if (granted) "LIVE TRY-ON" else "DEMO TRY-ON", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                CircleButton(if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff) {
                    torchOn = !torchOn
                    cameraController.enableTorch(torchOn)
                    if (frontCamera) Toast.makeText(context, "Flash works with the back camera", Toast.LENGTH_SHORT).show()
                }
            }

            // Hint when nobody is detected
            // Nobody found: tell the user to step back so their body is in view
            if (granted && frame == null) {
                val wholeBody = outfit.any { it.type != "TOP" }
                Text(
                    if (wholeBody) "Step back: show your head to feet" else "Step back: show your head to hips",
                    Modifier.align(Alignment.TopCenter).padding(top = 72.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = .5f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 12.sp
                )
            }

            // Right side: Switch camera, Remove outfit, Add outfit to cart
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Right-side buttons: switch camera, remove the outfit, add the outfit to the cart, save the
                // outfit
                LabeledCircleButton(Icons.Default.Cameraswitch, "Switch") { frontCamera = !frontCamera; frame = null }
                LabeledCircleButton(Icons.Default.Delete, "Remove") { outfit = emptyList() }
                LabeledCircleButton(Icons.Default.ShoppingBag, "Add") { addOutfit() }
                LabeledCircleButton(Icons.Default.BookmarkAdd, "Save") {
                    if (outfit.isEmpty()) Toast.makeText(context, "Pick something to wear first", Toast.LENGTH_SHORT).show()
                    else {
                        onSaveOutfit(outfit.map { it to sizeOf(it) })
                        Toast.makeText(context, "Outfit saved to My Outfits", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Capture button
            // The big white Capture button
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(74.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp)
                        .clip(CircleShape).background(Color.White).clickable { if (granted) capture() }
                )
                Text("Capture", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        // ---------------- Bottom sheet ----------------
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Cream)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // ---------------- BOTTOM SHEET (changes with the tab) ----------------
            when (tab) {
                // LOOKS TAB: shows the outfit + price, group chips, and the products. Tapping a product =
                // wear(...) rules above
                "Looks" -> Column {
                    // The outfit being worn (mix & match)
                    Text(
                        if (outfit.isEmpty()) "Pick a top and a bottom, or a dress"
                        else "Outfit: " + outfit.joinToString(" + ") { it.name } + "  •  " + rupees(outfit.sumOf { it.price }),
                        fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (outfit.size == 1 && outfit[0].type != "DRESS") {
                        Text(
                            if (outfit[0].type == "TOP") "Tip: pick a bottom to complete the outfit" else "Tip: pick a top to complete the outfit",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        lookGroups.forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g, fontSize = 12.sp) }) }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(catalog.filter { inGroup(it, group) }) { p ->
                            val selected = outfit.any { it.id == p.id }
                            Column(
                                Modifier.width(72.dp).clickable { outfit = wear(outfit, p); dragShift = Offset.Zero },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ProductImage(
                                    p,
                                    Modifier.size(72.dp, 80.dp).border(2.dp, if (selected) Primary else Color.Transparent, RoundedCornerShape(16.dp)),
                                    padding = 6
                                )
                                Text(p.colorName, fontSize = 11.sp, color = if (selected) Primary else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                // FILTERS TAB: one chip per colour filter
                "Filters" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cameraFilters.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.first) })
                    }
                }
                // SIZE TAB
                else -> Column {
                    // ---- Size suggestion (height + camera) ----
                    when {
                        // No height yet: ask for it (120-220 cm) and save it to the profile
                        height == 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = heightText, onValueChange = { heightText = it.filter { c -> c.isDigit() }.take(3) },
                                label = { Text("Your height (cm)") }, singleLine = true, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val cm = heightText.toIntOrNull() ?: 0
                                if (Validate.height(cm)) {
                                    height = cm
                                    onHeight(cm)
                                } else Toast.makeText(context, "Enter a height between 120 and 220 cm", Toast.LENGTH_SHORT).show()
                            }) { Text("Save") }
                        }
                        // Measuring: progress bar until 30 frames are measured
                        !ready -> Column {
                            Text("Finding your size... stand 2–3 m back so your whole body (head to feet) is in view", color = Ink, fontSize = 12.sp)
                            LinearProgressIndicator(
                                progress = { samples / SIZE_SAMPLES.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Primary, trackColor = PrimaryLight
                            )
                        }
                        // Done: show the suggested top and waist size, with buttons to use them or save them to
                        // the profile
                        else -> {
                            val m = measured!!
                            Text(
                                "Suggested for you: top " + topSizeFor(m.shoulderCm) + "  •  waist " + waistSizeFor(m.hipCm),
                                fontWeight = FontWeight.Bold, color = Primary
                            )
                            Text(
                                "Estimate from your height ($height cm): shoulders ≈ ${m.shoulderCm.toInt()} cm, hips ≈ ${m.hipCm.toInt()} cm",
                                color = Muted, fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    // One row of sizes for each garment (sold-out sizes can't be picked, ★ =
                                    // suggested size)
                                    outfit.forEach { p -> suggestionFor(p)?.takeIf { p.hasSize(it) }?.let { chosenSizes[p.id] = it } }
                                }) { Text("Use these sizes") }
                                TextButton(onClick = {
                                    onMeasured(m)
                                    Toast.makeText(context, "Saved to your profile", Toast.LENGTH_SHORT).show()
                                }) { Text("Save to profile") }
                                TextButton(onClick = { height = 0; heightText = ""; measured = null; samples = 0 }) { Text("Change height") }
                            }
                        }
                    }
                    if (outfit.isEmpty()) Text("Pick something in Looks first", color = Muted)
                    // One row of sizes for each garment in the outfit (★ = suggested size)
                    outfit.forEach { p ->
                        Text(p.name + "  •  " + rupees(p.price), fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.sizes.forEach { s ->
                                FilterChip(
                                    selected = sizeOf(p) == s, onClick = { chosenSizes[p.id] = s },
                                    enabled = p.hasSize(s), label = { Text(if (suggestionFor(p) == s) "$s ★" else s) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Tabs: Looks / Filters / Size
            // The three tabs: Looks / Filters / Size
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF1ECEF)).padding(4.dp)) {
                listOf("Looks", "Filters", "Size").forEach { t ->
                    Text(
                        t,
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (tab == t) PrimaryLight else Color.Transparent)
                            .clickable { tab = t }.padding(vertical = 10.dp),
                        color = if (tab == t) Primary else Muted,
                        fontWeight = if (tab == t) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Round see-through button with an icon (used on the camera screen)
@Composable
fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f))) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}

// Round button with a small label under it
@Composable
fun LabeledCircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircleButton(icon, onClick)
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------
//  Camera + body detection
// ---------------------------------------------------------------------

/**
 * Body found in one camera image, in that image's pixels.
 * toScreen() converts it to screen pixels the same way the preview is shown
 * (scaled to fill the view, centred, and mirrored for the front camera).
 */
// *** CAMERA PIXELS -> SCREEN PIXELS ***
// The body found in one camera image. toScreen() moves every point the same way the preview is shown:
// scaled to fill the screen, centred, and mirrored for the front camera (like a mirror)
data class PoseFrame(val body: Body, val imageWidth: Float, val imageHeight: Float, val mirror: Boolean) {
    fun toScreen(viewWidth: Float, viewHeight: Float): Body {
        val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val dx = (viewWidth - imageWidth * scale) / 2f
        val dy = (viewHeight - imageHeight * scale) / 2f
        fun map(p: Offset?): Offset? {
            if (p == null) return null
            val x = p.x * scale + dx
            return Offset(if (mirror) viewWidth - x else x, p.y * scale + dy)
        }
        return Body(
            map(body.shoulderA)!!, map(body.shoulderB)!!,
            map(body.hipA), map(body.hipB),
            map(body.ankleA), map(body.ankleB),
            map(body.elbowA), map(body.elbowB),
            map(body.wristA), map(body.wristB),
            body.mask?.onScreen(scale, dx, dy, if (mirror) viewWidth else 0f)
        )
    }
}

// *** CAMERA + ML KIT BODY DETECTION ***
// Shows the camera and runs ML Kit on every frame, then calls onFrame with the body (or null)
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraWithPose(
    cameraController: LifecycleCameraController,
    previewView: PreviewView,
    frontCamera: Boolean,
    onFrame: (PoseFrame?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnFrame by rememberUpdatedState(onFrame)
    val latestFront by rememberUpdatedState(frontCamera)
    // The last body found, used to smooth the movement so the clothes don't shake
    var lastBody by remember { mutableStateOf<Body?>(null) }

    // Set up once when the screen opens, cleaned up (onDispose) when it closes
    DisposableEffect(lifecycleOwner) {
        // Pose Detection = joints, Selfie Segmentation = which pixels are the person
        // ML Kit Pose Detection (STREAM_MODE = made for live video)
        val poseDetector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
        )
        // ML Kit Selfie Segmentation: which pixels are the person
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build()
        )
        // Every camera frame: find the joints, then the person's outline, then pass the body on
        // *** RUNS FOR EVERY CAMERA FRAME ***
        cameraController.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val image = imageProxy.image
            if (image == null) {
                imageProxy.close()
            } else {
                // The camera image may be sideways: work out its size when turned upright
                val rotation = imageProxy.imageInfo.rotationDegrees
                // Size of the image after it is turned upright
                val sideways = rotation == 90 || rotation == 270
                val w = (if (sideways) imageProxy.height else imageProxy.width).toFloat()
                val h = (if (sideways) imageProxy.width else imageProxy.height).toFloat()
                // Turn the camera frame into ML Kit's input format
                val input = InputImage.fromMediaImage(image, rotation)
                // STEP 1: find the body joints
                poseDetector.process(input)
                    .addOnSuccessListener { pose ->
                        // Joints -> our Body, moved halfway from the last body (smoothing)
                        lastBody = smooth(lastBody, poseToBody(pose))
                        val b = lastBody
                        if (b == null) {
                            latestOnFrame(null)
                            imageProxy.close()
                        } else {
                            // STEP 2: find the outline (mask = a number 0-1 for every pixel: how sure it is that
                            // it's the person)
                            segmenter.process(input)
                                .addOnSuccessListener { m ->
                                    val values = FloatArray(m.width * m.height)
                                    m.buffer.rewind()
                                    m.buffer.asFloatBuffer().get(values)
                                    // STEP 3: send the body + outline to the screen
                                    latestOnFrame(PoseFrame(b.copy(mask = PersonMask(values, m.width, m.height)), w, h, latestFront))
                                }
                                .addOnFailureListener { latestOnFrame(PoseFrame(b, w, h, latestFront)) }
                                // Close the frame when done, so CameraX can send the next one
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                    .addOnFailureListener { imageProxy.close() }
            }
        }
        // Connect the camera to the preview and start it (it stops by itself when the screen closes)
        previewView.controller = cameraController
        cameraController.bindToLifecycle(lifecycleOwner)
        // Screen closed: stop the analysis and the camera, and free ML Kit
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            poseDetector.close()
            segmenter.close()
        }
    }

    // When Switch is pressed: change between the front and back camera
    LaunchedEffect(frontCamera) {
        cameraController.cameraSelector =
            if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        lastBody = null
    }

    // Shows the camera preview (an Android View) inside Compose
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

