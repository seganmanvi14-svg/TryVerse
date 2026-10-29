// =====================================================================
// TryOnScreens.kt = PHOTO TRY-ON (upload your photo or pick a sample model).
// Flow: 1 Upload / sample model -> 2 Adjust (zoom, move) -> 3 Processing (find the body, fit the garment)
// -> 4 Result (with MIX & MATCH, add to cart, save outfit) -> 5 Compare + Try Another Colour.
// Also here: detectBody (ML Kit on a photo), renderLook / renderOutfit (draw the clothes), save and share.
// =====================================================================
package com.example.styletryon

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.delay
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// =====================================================================
//  TRY-ON (photo)
//  1. The user uploads a photo (or picks a sample model) and adjusts it.
//  2. The garment photo is fitted to the person's body on the phone (ML Kit pose + outline).
//  3. Gemini (free, through Firebase) writes a short style analysis, if Firebase is set up.
//  The live camera try-on is in LiveTryOn.kt.
// =====================================================================

// ---------------------------------------------------------------------
//  1. Upload
// ---------------------------------------------------------------------

// *** SCREEN 1: TRY-ON START ***
// Live camera button, which outfit to try, upload a photo, or tap a sample model
@Composable
fun TryOnUploadScreen(product: Product, onProduct: (Product) -> Unit, onBack: () -> Unit, onLive: () -> Unit, onPhoto: (Bitmap) -> Unit) {
    val context = LocalContext.current
    // Opens the phone's photo picker (images stored on the device)
    // *** PHOTO PICKER: opens the phone's gallery. The chosen photo is loaded and sent on (onPhoto) ***
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bitmap = loadPhoto(context, uri)
            if (bitmap != null) onPhoto(bitmap) else Toast.makeText(context, "Could not open that photo", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Virtual Try-On", onBack)
        Row(verticalAlignment = Alignment.Bottom) {
            DisplayTitle("See it ", size = 32.sp)
            ScriptWord("on you", size = 38.sp)
        }
        Text("Live with your camera, or with a photo of you.", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(16.dp))

        // Option 1: live camera (solid block in the main colour)
        Row(
            // Live Camera Try-On button
            Modifier.fillMaxWidth().glass(CardShape).clickable { onLive() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Champagne ring camera icon (same as the bottom bar's Try-On button)
            Box(
                Modifier.size(52.dp).clip(CircleShape).border(1.5.dp, Gold, CircleShape).background(Sand),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Videocam, contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Live Camera Try-On", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("See the outfit on you in real time", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Primary)
        }
        Spacer(Modifier.height(22.dp))
        Eyebrow("AI photo try-on")
        Text("Upload a photo and our AI creates a picture of you wearing it", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        // Which outfit to try
        Eyebrow("Outfit: ${product.name}", color = Muted)
        Spacer(Modifier.height(8.dp))
        // Which outfit to try (tap to change)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(catalog) { p ->
                ProductImage(
                    p,
                    Modifier.size(56.dp, 74.dp).border(1.5.dp, if (p.id == product.id) Gold else Color.Transparent, Sharp)
                        .clickable { onProduct(p) },
                    padding = 5
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Dashed upload box (square corners)
        Column(
            Modifier.fillMaxWidth().clip(CardShape).background(GlassFill)
                .drawBehind {
                    drawRoundRect(
                        color = Gold.copy(alpha = 0.5f), cornerRadius = CornerRadius(22.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    )
                }
                .padding(vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = Primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Eyebrow("Upload your photo")
            Text("Front-facing, full or half body", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            // Opens the gallery (images only)
            ZaraButton("Choose photo") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }

        // Sample models
        Spacer(Modifier.height(20.dp))
        Eyebrow("Or try a sample model")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // *** SAMPLE MODELS: tapping one uses that photo instead of the user's own ***
            sampleModels.forEach { id ->
                Image(
                    painterResource(id), contentDescription = "Sample model", contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).aspectRatio(0.75f).goldFrame()
                        .clickable { onPhoto(BitmapFactory.decodeResource(context.resources, id)) }
                )
            }
        }

        // Tips
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().border(1.dp, Line, Sharp).padding(14.dp)) {
            Eyebrow("For the best results")
            Spacer(Modifier.height(6.dp))
            listOf("Use a clear, front-facing photo", "Show your head to hips (feet for pants)", "Good lighting", "Wear fitted clothes")
                .forEach { Text("—  $it", color = Ink, fontSize = 12.sp) }
        }
    }
}

// Reads the chosen photo. Very big photos are made smaller (max ~1600 px) so the phone doesn't run out of memory
fun loadPhoto(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1600) decoder.setTargetSampleSize(longest / 1600 + 1)
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
        }
    }
} catch (e: Exception) {
    null
}

// ---------------------------------------------------------------------
//  2. Adjust photo (drag to move, pinch or slider to zoom)
// ---------------------------------------------------------------------

// *** SCREEN 2: ADJUST PHOTO ***
// The user drags and pinches (or uses the slider) so the body fills the frame
@Composable
fun AdjustPhotoScreen(photo: Bitmap, onRetake: () -> Unit, onContinue: (Bitmap) -> Unit) {
    val image = remember(photo) { photo.asImageBitmap() }
    // zoom (1x-4x), pan (how far it was moved) and the frame size on screen
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Adjust Photo", onRetake)
        Text("Crop and adjust your photo for a better fit", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        Box(
            Modifier.weight(1f).aspectRatio(0.75f).align(Alignment.CenterHorizontally)
                .background(Sand)
                .onSizeChanged { frame = it }
                // Touch listener: drag to move, pinch to zoom
                .pointerInput(Unit) {          // touch listener: drag and pinch
                    detectTransformGestures { _, move, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                        pan += move
                    }
                }
        ) {
            // Draws the photo scaled + moved, with rule-of-thirds grid lines and corner marks
            Canvas(Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val s = maxOf(w / image.width, h / image.height) * zoom
                val left = (w - image.width * s) / 2f + pan.x
                val top = (h - image.height * s) / 2f + pan.y
                drawImage(
                    image, dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize((image.width * s).toInt(), (image.height * s).toInt()), filterQuality = FilterQuality.High
                )
                // Grid lines (rule of thirds)
                for (i in 1..2) {
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 2f)
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 2f)
                }
                // Corner marks
                val c = 28f
                val stroke = 8f
                listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(w, 0f) to Offset(-1f, 1f), Offset(0f, h) to Offset(1f, -1f), Offset(w, h) to Offset(-1f, -1f))
                    .forEach { (p, d) ->
                        drawLine(Primary, p, Offset(p.x + d.x * c, p.y), strokeWidth = stroke)
                        drawLine(Primary, p, Offset(p.x, p.y + d.y * c), strokeWidth = stroke)
                    }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ZoomOut, contentDescription = null, tint = Ink)
            // Zoom slider
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..4f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Ink)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZaraOutlinedButton("Retake", Modifier.weight(1f), onClick = onRetake)
            // Continue: cut out exactly what is in the frame and go on
            ZaraButton("Continue", Modifier.weight(2f)) {
                if (frame.width > 0) onContinue(cropPhoto(photo, frame.width.toFloat(), frame.height.toFloat(), zoom, pan))
            }
        }
    }
}

// Makes a 600 x 800 picture of exactly what is inside the frame (same zoom and position the user chose)
fun cropPhoto(photo: Bitmap, frameW: Float, frameH: Float, zoom: Float, pan: Offset): Bitmap {
    val out = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
    val k = 600f / frameW
    val s = maxOf(frameW / photo.width, frameH / photo.height) * zoom
    val left = (frameW - photo.width * s) / 2f + pan.x
    val top = (frameH - photo.height * s) / 2f + pan.y
    val matrix = android.graphics.Matrix().apply {
        postScale(s * k, s * k)
        postTranslate(left * k, top * k)
    }
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(photo, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return out
}

// ---------------------------------------------------------------------
//  3. Trying it on (AI, or quick on-device preview)
// ---------------------------------------------------------------------

// Colours offered in 'Try Another Colour'
val tryColors = listOf(
    "pink" to Color(0xFFF2A7AE),
    "beige" to Color(0xFFE6DACB),
    "sky blue" to Color(0xFF79B4F2),
    "charcoal black" to Color(0xFF3A3D48),
    "lilac" to Color(0xFFC6B1EE)
)

/**
 * Makes the try-on picture.
 * [base] = picture to edit (the person photo, or the last AI result when only the colour changes).
 * onDone(picture, madeByAi)
 */
// *** SCREEN 3: PROCESSING ('Trying it on...') ***
@Composable
fun ProcessingScreen(base: Bitmap, person: Bitmap, product: Product, colorName: String?, tint: Color?, onDone: (Bitmap, Boolean) -> Unit, onFail: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val progress by animateFloatAsState(targetValue = (step + 1) / 4f, label = "progress")

    // Quick preview: fit the garment photo to the body on the phone (no internet)
    // Backup if the AI part fails: fit the garment on the phone only (no internet)
    fun quickPreview() {
        aiError = null
        scope.launch {
            step = 1
            val body = detectBody(person)
            if (body == null) {
                Toast.makeText(context, "Couldn't find a person. Try a clear, front-facing photo.", Toast.LENGTH_LONG).show()
                onFail()
                return@launch
            }
            step = 2; delay(300)
            val look = renderLook(context, person, body, product, tint)
            step = 3; delay(200)
            onDone(look, false)
        }
    }

    // *** STARTS THE TRY-ON (AiTryOn.dress) as soon as this screen opens ***
    // Success -> result screen. Error -> show the 'AI try-on unavailable' box
    LaunchedEffect(base, product, colorName) {
        step = 1
        try {
            val look = AiTryOn.dress(context, base, product, colorName)
            step = 3; delay(300)
            onDone(look, true)
        } catch (e: AiTryOn.TryOnException) {
            aiError = e.message
        }
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(96.dp).goldFrame().background(Sand), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(22.dp))
        DisplayTitle("Trying it on…", size = 30.sp)
        Spacer(Modifier.height(4.dp))
        Eyebrow("About 10–20 seconds", color = Muted)
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Primary, trackColor = Line
        )
        Spacer(Modifier.height(24.dp))
        Text("Our AI is dressing you in the ${product.name}", color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        // The 3 steps with ticks as they finish
        listOf("Sending your photo securely...", "Fitting the outfit to your body...", "Rendering your look...").forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step == i + 1 && step < 3) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Primary)
                else Icon(
                    if (step > i) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null, tint = if (step > i) Primary else Muted
                )
                Spacer(Modifier.width(10.dp))
                Text(label, color = Ink, fontSize = 14.sp)
            }
        }
    }

    // AI not available -> offer the quick preview
    // Error box: offer the quick preview, or go back
    if (aiError != null) {
        AlertDialog(
            onDismissRequest = { onFail() },
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
            title = { Text("AI try-on unavailable") },
            text = { Text("${aiError}\n\nWould you like a quick preview made on your phone instead?") },
            confirmButton = { ZaraButton("Quick preview") { quickPreview() } },
            dismissButton = { TextButton(onClick = { onFail() }) { Text("GO BACK", letterSpacing = 1.sp) } }
        )
    }
}

/**
 * Finds the person in the photo:
 *  - ML Kit Pose Detection  -> shoulders, hips, ankles
 *  - ML Kit Selfie Segmentation -> which pixels are the person (used to measure body width)
 */
// *** FINDS THE PERSON IN A PHOTO (ML Kit) ***
// STEP 1: Pose Detection -> joints. STEP 2: Selfie Segmentation -> outline. Returns null if no person
suspend fun detectBody(photo: Bitmap): Body? {
    val image = InputImage.fromBitmap(photo, 0)
    // suspendCoroutine = wait here until ML Kit answers (it answers later, in a listener)
    val body = suspendCoroutine<Body?> { cont ->
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build()
        )
        detector.process(image)
            .addOnSuccessListener { pose -> cont.resume(poseToBody(pose)) }
            .addOnFailureListener { cont.resume(null) }
            .addOnCompleteListener { detector.close() }
    } ?: return null
    // The outline: one number per pixel (how sure it is the person)
    val mask = suspendCoroutine<PersonMask?> { cont ->
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
        )
        segmenter.process(image)
            .addOnSuccessListener { m ->
                val values = FloatArray(m.width * m.height)
                m.buffer.rewind()
                m.buffer.asFloatBuffer().get(values)
                cont.resume(PersonMask(values, m.width, m.height))
            }
            .addOnFailureListener { cont.resume(null) }
            .addOnCompleteListener { segmenter.close() }
    }
    return body.copy(mask = mask)
}

// ---------------------------------------------------------------------
//  4. Result
// ---------------------------------------------------------------------

/**
 * Result of the photo try-on (uploaded photo or sample model).
 * Mix & match: [person] is the original photo, so a matching top / bottom can be put on together with [product].
 * onAddOutfit / onSaveOutfit get the whole outfit (bottom first, then the top).
 */
// *** SCREEN 4: RESULT ***
@Composable
fun ResultScreen(
    look: Bitmap, madeByAi: Boolean, product: Product,
    onProduct: (Product) -> Unit, onBack: () -> Unit, onCompare: () -> Unit, onAddToCart: () -> Unit,
    person: Bitmap? = null,
    onAddOutfit: (List<Product>) -> Unit = {},
    onSaveOutfit: ((List<Product>) -> Unit)? = null
) {
    val context = LocalContext.current

    // ---- Mix & match: the item picked to wear together with [product] ----
    // *** MIX & MATCH ***
    // partner = the top / bottom chosen to wear WITH the product (null = none).
    // mixedLook = the picture with both. personBody = the body found once and reused
    var partner by remember(product.id) { mutableStateOf<Product?>(null) }
    var mixedLook by remember(product.id) { mutableStateOf<Bitmap?>(null) }
    var mixing by remember { mutableStateOf(false) }
    var personBody by remember(person) { mutableStateOf<Body?>(null) }
    // The outfit, ordered by the same rules as the live camera (bottom first, then the top)
    val outfit = partner?.let { wear(listOf(product), it) } ?: listOf(product)
    // When a partner is picked: find the body (once), then draw both garments on the ORIGINAL photo.
    // Dispatchers.Default = the drawing runs in the background so the screen doesn't freeze
    LaunchedEffect(partner) {
        val p = partner
        if (p == null || person == null) { mixedLook = null; return@LaunchedEffect }
        mixing = true
        val body = personBody ?: detectBody(person)?.also { personBody = it }
        if (body == null) {
            Toast.makeText(context, "Couldn't find the person in this photo", Toast.LENGTH_SHORT).show()
            partner = null
        } else {
            // Drawn on the phone: bottom first, then the top over it
            mixedLook = withContext(Dispatchers.Default) { renderOutfit(context, person, body, wear(listOf(product), p)) }
        }
        mixing = false
    }
    // Items that go with this one: tops for a bottom, bottoms for a top (a dress is already a full outfit)
    // What can be paired: a top gets bottoms, a bottom gets tops, a dress is already a full outfit
    val matches = when (product.type) {
        "TOP" -> catalog.filter { it.type == "PANTS" }
        "PANTS" -> catalog.filter { it.type == "TOP" }
        else -> emptyList()
    }

    // Show the mix & match picture if there is one, otherwise the normal result
    val shown = mixedLook ?: look
    val image = remember(shown) { shown.asImageBitmap() }
    // Save button: saves the picture to Pictures/TryVerse
    val save = {
        val saved = saveToGallery(context, shown) != null
        Toast.makeText(context, if (saved) "Look saved to Pictures/TryVerse" else "Could not save", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        TopBar("Virtual Try-On", onBack) {
            Text("Save", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { save() }.padding(8.dp))
        }
        // The picture (left) and a strip of all products (right). Tapping a product = a new try-on
        Row(Modifier.fillMaxWidth().height(400.dp)) {
            // The look, in a rounded frame
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp))) {
                Image(image, contentDescription = "Your look", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                // Label: Mix & match / AI fitted / Fitted to you
                GlassChip(
                    when { mixedLook != null -> "Mix & match"; madeByAi -> "AI fitted"; else -> "Fitted to you" },
                    Modifier.align(Alignment.TopStart).padding(10.dp)
                )
                // Spinner while the mix & match picture is being drawn
                if (mixing) CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = Gold, strokeWidth = 3.dp)
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
                // "Fitted" ring
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp).size(58.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(GoldDeep, GoldLight, Gold, GoldDeep))).padding(3.dp)
                        .clip(CircleShape).background(Color(0xD9070706)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                        Text("FITTED", color = Gold, fontSize = 7.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Compare
                Row(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).clip(Pill).background(Color(0x99000000))
                        .border(1.dp, Line, Pill).clickable { onCompare() }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Compare, contentDescription = null, tint = Color(0xFFF4EEE2), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Compare", fontSize = 11.sp, color = Color(0xFFF4EEE2), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            // Switch outfit (makes a new try-on)
            // Product strip on the right
            LazyColumn(Modifier.width(58.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalog) { p ->
                    ProductImage(
                        p,
                        Modifier.size(56.dp, 74.dp).border(1.5.dp, if (p.id == product.id) Gold else Color.Transparent, Sharp)
                            .clickable { if (p.id != product.id) onProduct(p) },
                        padding = 5
                    )
                }
            }
        }
        // ---- Mix & match ----
        // *** MIX & MATCH ROW ***: matching items; tap one to wear it too, tap again (or Remove) to take it off
        if (person != null) {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Mix & match", Modifier.weight(1f))
                    if (partner != null) {
                        Text(
                            "Remove ${partner!!.name.substringAfterLast(" ")}", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { partner = null }.padding(4.dp)
                        )
                    }
                }
                if (matches.isEmpty()) {
                    Text("A dress is a full outfit. Pick a top or a bottom on the right to mix & match.", color = Muted, fontSize = 12.sp)
                } else {
                    Text(
                        if (partner == null) "Complete the look: wear your ${product.name} with…"
                        else "Wearing: " + outfit.joinToString(" + ") { it.name } + "  ·  " + rupees(outfit.sumOf { it.price }),
                        color = Muted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(matches) { p ->
                            ProductImage(
                                p,
                                Modifier.size(56.dp, 74.dp).border(1.5.dp, if (partner?.id == p.id) Gold else Line, Sharp)
                                    .clickable(enabled = !mixing) { partner = if (partner?.id == p.id) null else p },
                                padding = 5
                            )
                        }
                    }
                }
            }
        }
        // "Looks good on you." glass card
        // 'Looks good on you.' card: the AI style tip (or the fit checks) and the buttons
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp).glass(CardShape).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                DisplayTitle("Looks good ", size = 24.sp)
                ScriptWord("on you.", size = 30.sp)
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(if (madeByAi) "AI Style Analysis" else "Fit Analysis", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                if (madeByAi && AiTryOn.lastAnalysis.isNotBlank()) {
                    Text(AiTryOn.lastAnalysis, color = Ink, fontSize = 12.sp)
                } else {
                    listOf("Fitted to your shoulders and hips", "Sleeves follow your arms", "Try another colour in Compare").forEach {
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, tint = Gold, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = Ink, fontSize = 12.sp)
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Shoulders ✓", "Waist ✓", "Length ✓").forEach {
                        Text(
                            it, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(Pill).border(1.dp, Gold.copy(alpha = 0.4f), Pill).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // MainActivity checks the stock and shows the message
            // With a partner: add the WHOLE outfit to the cart, or save it to My Outfits. Without: normal
            // buttons
            if (partner != null) {
                ZaraButton("Add outfit to Cart  ·  ${rupees(outfit.sumOf { it.price })}", Modifier.fillMaxWidth()) { onAddOutfit(outfit) }
                if (onSaveOutfit != null) {
                    Spacer(Modifier.height(8.dp))
                    ZaraOutlinedButton("Save outfit", Modifier.fillMaxWidth(), icon = Icons.Rounded.Checkroom) { onSaveOutfit(outfit) }
                }
            } else {
                ZaraButton("Add to Cart", Modifier.fillMaxWidth(), onClick = onAddToCart)
                Spacer(Modifier.height(8.dp))
                ZaraOutlinedButton("Compare & Try Colours", Modifier.fillMaxWidth(), onClick = onCompare)
            }
        }
    }
}

// ---------------------------------------------------------------------
//  5. Compare looks + Try Another Color
// ---------------------------------------------------------------------

// *** SCREEN 5: COMPARE + TRY ANOTHER COLOUR ***
// Original photo and the try-on side by side; colour squares make a new try-on in that colour
@Composable
fun CompareScreen(photo: Bitmap, look: Bitmap, product: Product, colorName: String?, onColor: (String?, Color?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Compare Looks", onBack) {
            IconButton(onClick = { shareLook(context, look) }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = Ink) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(photo to "Original photo", look to "With ${product.name.substringAfterLast(" ")}").forEach { (bmp, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bmp.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).goldFrame()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label.uppercase(), color = Ink, fontSize = 11.sp, letterSpacing = 1.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Hairline()
        Eyebrow("Try another colour", Modifier.fillMaxWidth().padding(vertical = 12.dp))
        Hairline()
        Spacer(Modifier.height(16.dp))
        // First square = original colour, then the 5 try colours
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ColorDot(product.color, selected = colorName == null) { if (colorName != null) onColor(null, null) }
            tryColors.forEach { (name, c) -> ColorDot(c, selected = colorName == name) { if (colorName != name) onColor(name, c) } }
        }
        Spacer(Modifier.height(8.dp))
        Text("First square = original colour", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// One small colour square (outlined when selected)
@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).border(if (selected) 1.5.dp else 1.dp, if (selected) Ink else Line, Sharp)
            .padding(4.dp).background(color).clickable { onClick() }
    )
}

// ---------------------------------------------------------------------
//  Rendering, saving and sharing the look
// ---------------------------------------------------------------------

// *** DRAWS ONE GARMENT ON THE PHOTO ***: copy the photo, then drawGarmentMesh (GarmentFit.kt) on it
fun renderLook(context: Context, photo: Bitmap, body: Body, product: Product, tint: Color?): Bitmap {
    val out = photo.copy(Bitmap.Config.ARGB_8888, true)
    val garment = BitmapFactory.decodeResource(context.resources, product.image)
    drawGarmentMesh(android.graphics.Canvas(out), garment, product, body, tint = tint)
    return out
}

// *** MIX & MATCH DRAWING ***: copy the photo, then draw each garment in order (bottom first, then the top)
fun renderOutfit(context: Context, photo: Bitmap, body: Body, outfit: List<Product>): Bitmap {
    val out = photo.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    outfit.forEach { p -> drawGarmentMesh(canvas, BitmapFactory.decodeResource(context.resources, p.image), p, body) }
    return out
}

// *** SAVES A PICTURE TO THE PHONE (Pictures/TryVerse) ***
// Android 10+: through MediaStore (no storage permission needed). Older: the app's own pictures folder
fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
    val name = "TryVerse_${System.currentTimeMillis()}.jpg"
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TryVerse")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            uri
        } else {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Uri.fromFile(file)
        }
    } catch (e: Exception) {
        null
    }
}

// Saves the picture, then opens the phone's share menu (WhatsApp, Instagram...)
fun shareLook(context: Context, bitmap: Bitmap) {
    val uri = saveToGallery(context, bitmap)
    if (uri == null || Build.VERSION.SDK_INT < 29) {
        Toast.makeText(context, if (uri != null) "Saved to your phone" else "Could not share", Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "My new look on TryVerse ✨")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your look"))
}

