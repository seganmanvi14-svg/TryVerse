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
