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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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

@Composable
fun TryOnUploadScreen(product: Product, onProduct: (Product) -> Unit, onBack: () -> Unit, onLive: () -> Unit, onPhoto: (Bitmap) -> Unit) {
    val context = LocalContext.current
    // Opens the phone's photo picker (images stored on the device)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bitmap = loadPhoto(context, uri)
            if (bitmap != null) onPhoto(bitmap) else Toast.makeText(context, "Could not open that photo", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Try-On", onBack)
        Text("See how it looks on you!", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        // Option 1: live camera
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF8A6FE0), Primary)))
                .clickable { onLive() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Live Camera Try-On", color = Color.White, fontWeight = FontWeight.Bold)
                Text("See the outfit on you in real time", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text("AI Photo Try-On", fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
        Text("Upload a photo and our AI creates a picture of you wearing it", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        // Which outfit to try
        Text("Outfit: ${product.name}", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(catalog) { p ->
                ProductImage(
                    p,
                    Modifier.size(54.dp, 64.dp).border(2.dp, if (p.id == product.id) Primary else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { onProduct(p) },
                    padding = 5
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Dashed upload box
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SoftBlue)
                .drawBehind {
                    drawRoundRect(
                        color = Primary.copy(alpha = 0.6f), cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
                    )
                }
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Primary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(10.dp))
            Text("Upload Your Photo", fontWeight = FontWeight.Bold, color = Ink)
            Text("(Front-facing, full or half body)", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = RoundedCornerShape(24.dp)
            ) { Text("Choose Photo", modifier = Modifier.padding(horizontal = 12.dp)) }
        }

        // Sample models
        Spacer(Modifier.height(16.dp))
        Text("Or try a sample model", color = Ink, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sampleModels.forEach { id ->
                Image(
                    painterResource(id), contentDescription = "Sample model", contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(14.dp))
                        .clickable { onPhoto(BitmapFactory.decodeResource(context.resources, id)) }
                )
            }
        }

        // Tips
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SoftBlue).padding(14.dp)) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Star)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Best Results:", fontWeight = FontWeight.Bold, color = Ink, fontSize = 13.sp)
                listOf("Use a clear, front-facing photo", "Show your head to hips (feet for pants)", "Good lighting", "Wear fitted clothes")
                    .forEach { Text("•  $it", color = Ink, fontSize = 12.sp) }
            }
        }
    }
}

/** Reads a photo chosen from the gallery (made smaller if it is very big). */
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

@Composable
fun AdjustPhotoScreen(photo: Bitmap, onRetake: () -> Unit, onContinue: (Bitmap) -> Unit) {
    val image = remember(photo) { photo.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Adjust Photo", onRetake)
        Text("Crop and adjust your photo for a better fit", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        Box(
            Modifier.weight(1f).aspectRatio(0.75f).align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(18.dp)).background(Color.White)
                .onSizeChanged { frame = it }
                .pointerInput(Unit) {          // touch listener: drag and pinch
                    detectTransformGestures { _, move, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                        pan += move
                    }
                }
        ) {
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
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..4f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Ink)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRetake, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight, contentColor = Primary)
            ) { Text("Retake") }
            Button(
                onClick = { if (frame.width > 0) onContinue(cropPhoto(photo, frame.width.toFloat(), frame.height.toFloat(), zoom, pan)) },
                modifier = Modifier.weight(2f).height(52.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continue")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

/** Makes a 600 x 800 picture of exactly what is inside the frame. */
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

/** Colours for "Try Another Color" (name is sent to the AI). */
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
@Composable
fun ProcessingScreen(base: Bitmap, person: Bitmap, product: Product, colorName: String?, tint: Color?, onDone: (Bitmap, Boolean) -> Unit, onFail: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val progress by animateFloatAsState(targetValue = (step + 1) / 4f, label = "progress")

    // Quick preview: fit the garment photo to the body on the phone (no internet)
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
        Box(Modifier.size(110.dp).clip(CircleShape).background(PrimaryLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Primary, modifier = Modifier.size(50.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Trying It On...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("This takes about 10–20 seconds", color = Muted)
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Primary, trackColor = PrimaryLight
        )
        Spacer(Modifier.height(24.dp))
        Text("Our AI is dressing you in the ${product.name}", color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
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
    if (aiError != null) {
        AlertDialog(
            onDismissRequest = { onFail() },
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
            title = { Text("AI try-on unavailable") },
            text = { Text("${aiError}\n\nWould you like a quick preview made on your phone instead?") },
            confirmButton = { Button(onClick = { quickPreview() }) { Text("Quick Preview") } },
            dismissButton = { TextButton(onClick = { onFail() }) { Text("Go Back") } }
        )
    }
}

/**
 * Finds the person in the photo:
 *  - ML Kit Pose Detection  -> shoulders, hips, ankles
 *  - ML Kit Selfie Segmentation -> which pixels are the person (used to measure body width)
 */
suspend fun detectBody(photo: Bitmap): Body? {
    val image = InputImage.fromBitmap(photo, 0)
    val body = suspendCoroutine<Body?> { cont ->
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build()
        )
        detector.process(image)
            .addOnSuccessListener { pose -> cont.resume(poseToBody(pose)) }
            .addOnFailureListener { cont.resume(null) }
            .addOnCompleteListener { detector.close() }
    } ?: return null
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

@Composable
fun ResultScreen(
    look: Bitmap, madeByAi: Boolean, product: Product,
    onProduct: (Product) -> Unit, onBack: () -> Unit, onCompare: () -> Unit, onAddToCart: () -> Unit
) {
    val context = LocalContext.current
    val image = remember(look) { look.asImageBitmap() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Your Try-On Result", onBack) {
            IconButton(onClick = { shareLook(context, look) }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = Ink) }
        }
        Box(Modifier.weight(1f).aspectRatio(0.75f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(20.dp))) {
            Image(image, contentDescription = "Your look", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                if (madeByAi && AiTryOn.lastAnalysis.isNotBlank()) "✨ AI styled" else "Fitted to you",
                Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White, fontSize = 11.sp
            )
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .clickable { onCompare() }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Compare, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Compare", fontSize = 12.sp, color = Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Switch outfit (makes a new try-on)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(catalog) { p ->
                ProductImage(
                    p,
                    Modifier.size(62.dp, 72.dp).border(2.dp, if (p.id == product.id) Primary else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { if (p.id != product.id) onProduct(p) },
                    padding = 5
                )
            }
        }
        // Gemini's style tip (only when Firebase is set up)
        if (madeByAi && AiTryOn.lastAnalysis.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PrimaryLight).padding(12.dp)) {
                Text("✨ AI Style Analysis", fontWeight = FontWeight.Bold, color = Primary, fontSize = 13.sp)
                Text(AiTryOn.lastAnalysis, color = Ink, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val saved = saveToGallery(context, look) != null
                    Toast.makeText(context, if (saved) "Look saved to Pictures/TryVerse" else "Could not save", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight, contentColor = Primary)
            ) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save Look")
            }
            Button(
                onClick = {
                    Toast.makeText(context, "${product.name} added to cart", Toast.LENGTH_SHORT).show()
                    onAddToCart()
                },
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add to Cart")
            }
        }
    }
}

// ---------------------------------------------------------------------
//  5. Compare looks + Try Another Color
// ---------------------------------------------------------------------

@Composable
fun CompareScreen(photo: Bitmap, look: Bitmap, product: Product, colorName: String?, onColor: (String?, Color?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Compare Looks", onBack) {
            IconButton(onClick = { shareLook(context, look) }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = Ink) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(photo to "Original Photo", look to "With ${product.name.substringAfterLast(" ")}").forEach { (bmp, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bmp.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = Ink, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Try Another Color", color = Primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PrimaryLight).padding(12.dp)
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ColorDot(product.color, selected = colorName == null) { if (colorName != null) onColor(null, null) }
            tryColors.forEach { (name, c) -> ColorDot(c, selected = colorName == name) { if (colorName != name) onColor(name, c) } }
        }
        Spacer(Modifier.height(8.dp))
        Text("First circle = original colour", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(2.dp, if (selected) Primary else Color.Transparent, CircleShape)
            .padding(4.dp).clip(CircleShape).background(color).clickable { onClick() }
    )
}

// ---------------------------------------------------------------------
//  Rendering, saving and sharing the look
// ---------------------------------------------------------------------

/** Person photo + garment warped onto the body (optionally recoloured). */
fun renderLook(context: Context, photo: Bitmap, body: Body, product: Product, tint: Color?): Bitmap {
    val out = photo.copy(Bitmap.Config.ARGB_8888, true)
    val garment = BitmapFactory.decodeResource(context.resources, product.image)
    drawGarmentMesh(android.graphics.Canvas(out), garment, product, body, tint = tint)
    return out
}

/** Saves a picture into Pictures/TryVerse on the phone. Returns its Uri, or null if it failed. */
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

/** Saves the look and opens the phone's share menu. */
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

