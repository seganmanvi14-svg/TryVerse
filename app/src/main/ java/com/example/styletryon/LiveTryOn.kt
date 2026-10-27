package com.example.styletryon

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
// =====================================================================

/** Colour filters for the camera ("Filters" tab). */
val cameraFilters = listOf(
    "None" to Color.Transparent,
    "Warm" to Color(0x33FF9A3C),
    "Cool" to Color(0x333C8DFF),
    "Rosy" to Color(0x33FF6F91),
    "Vintage" to Color(0x40A07850)
)

@Composable
fun LiveTryOnScreen(startProduct: Product, onClose: () -> Unit, onAddToCart: (Product, String) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) Toast.makeText(context, "Camera permission is needed for live try-on", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var product by remember { mutableStateOf(startProduct) }
    var showGarment by remember { mutableStateOf(true) }
    var frontCamera by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<PoseFrame?>(null) }   // body points in camera-image pixels
    var dragShift by remember { mutableStateOf(Offset.Zero) }     // moved with a finger (touch listener)
    var tab by remember { mutableStateOf("Looks") }
    var filter by remember { mutableStateOf(cameraFilters[0]) }
    var chosenSize by remember(product.id) { mutableStateOf(product.sizes[2]) }
    val sizeScale = 1f + (product.sizes.indexOf(chosenSize) - 2) * 0.05f
    val garment = ImageBitmap.imageResource(product.image)

    val cameraController = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_ANALYSIS) }
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // lets us grab the frame for Capture
        }
    }

    // Capture: camera frame + garment + filter -> saved to the phone's Pictures folder
    fun capture() {
        val photo = previewView.bitmap?.copy(Bitmap.Config.ARGB_8888, true)
        if (photo == null) {
            Toast.makeText(context, "Camera not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        val canvas = android.graphics.Canvas(photo)
        val b = frame?.toScreen(photo.width.toFloat(), photo.height.toFloat())
        if (showGarment && b != null) drawGarmentMesh(canvas, garment.asAndroidBitmap(), product, b, sizeScale, dragShift)
        if (filter.second != Color.Transparent) canvas.drawColor(filter.second.toArgb())
        val saved = saveToGallery(context, photo) != null
        Toast.makeText(context, if (saved) "Look saved to Pictures/TryVerse" else "Could not save photo", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // ---------------- Camera area ----------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (granted) {
                CameraWithPose(cameraController, previewView, frontCamera, onFrame = { frame = it })
                // Garment layer; drag with a finger to fine-tune its position
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            dragShift += amount
                        }
                    }
                ) {
                    val b = frame?.toScreen(size.width, size.height)      // camera pixels -> screen pixels
                    if (showGarment && b != null) {
                        drawIntoCanvas { drawGarmentMesh(it.nativeCanvas, garment.asAndroidBitmap(), product, b, sizeScale, dragShift) }
                    }
                    drawRect(filter.second)
                }
            } else {
                // No camera permission: show the demo model instead
                DemoModelTryOn(if (showGarment) product else null, Modifier.fillMaxSize())
            }

            // Top bar: close, LIVE TRY-ON, flash
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
            if (granted && frame == null) {
                Text(
                    "Step back: show your head to hips",
                    Modifier.align(Alignment.TopCenter).padding(top = 72.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = .5f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 12.sp
                )
            }

            // Right side: Switch camera, Remove garment, Add to cart
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LabeledCircleButton(Icons.Default.Cameraswitch, "Switch") { frontCamera = !frontCamera; frame = null }
                LabeledCircleButton(Icons.Default.Delete, "Remove") { showGarment = false }
                LabeledCircleButton(Icons.Default.ShoppingBag, "Add") {
                    onAddToCart(product, chosenSize)
                    Toast.makeText(context, "Added to cart: ${product.name} ($chosenSize)", Toast.LENGTH_SHORT).show()
                }
            }

            // Capture button
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
            when (tab) {
                "Looks" -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(catalog) { p ->
                        val selected = p.id == product.id && showGarment
                        Column(
                            Modifier.width(72.dp).clickable { product = p; showGarment = true; dragShift = Offset.Zero },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ProductImage(
                                p,
                                Modifier.size(72.dp, 80.dp).border(2.dp, if (selected) Primary else Color.Transparent, RoundedCornerShape(16.dp)),
                                padding = 6
                            )
                            Text(p.category, fontSize = 11.sp, color = if (selected) Primary else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                "Filters" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cameraFilters.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.first) })
                    }
                }
                else -> Column {
                    Text(product.name + "  •  ₹%,d".format(product.price), fontWeight = FontWeight.SemiBold, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        product.sizes.forEach { s ->
                            FilterChip(selected = chosenSize == s, onClick = { chosenSize = s }, label = { Text(s) })
                        }
                    }
                    Text("Tip: drag the garment with your finger to adjust it", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Tabs: Looks / Filters / Size
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

@Composable
fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f))) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}

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
    var lastBody by remember { mutableStateOf<Body?>(null) }

    DisposableEffect(lifecycleOwner) {
        // Pose Detection = joints, Selfie Segmentation = which pixels are the person
        val poseDetector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
        )
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build()
        )
        // Every camera frame: find the joints, then the person's outline, then pass the body on
        cameraController.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val image = imageProxy.image
            if (image == null) {
                imageProxy.close()
            } else {
                val rotation = imageProxy.imageInfo.rotationDegrees
                // Size of the image after it is turned upright
                val sideways = rotation == 90 || rotation == 270
                val w = (if (sideways) imageProxy.height else imageProxy.width).toFloat()
                val h = (if (sideways) imageProxy.width else imageProxy.height).toFloat()
                val input = InputImage.fromMediaImage(image, rotation)
                poseDetector.process(input)
                    .addOnSuccessListener { pose ->
                        lastBody = smooth(lastBody, poseToBody(pose))
                        val b = lastBody
                        if (b == null) {
                            latestOnFrame(null)
                            imageProxy.close()
                        } else {
                            segmenter.process(input)
                                .addOnSuccessListener { m ->
                                    val values = FloatArray(m.width * m.height)
                                    m.buffer.rewind()
                                    m.buffer.asFloatBuffer().get(values)
                                    latestOnFrame(PoseFrame(b.copy(mask = PersonMask(values, m.width, m.height)), w, h, latestFront))
                                }
                                .addOnFailureListener { latestOnFrame(PoseFrame(b, w, h, latestFront)) }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                    .addOnFailureListener { imageProxy.close() }
            }
        }
        previewView.controller = cameraController
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            poseDetector.close()
            segmenter.close()
        }
    }

    LaunchedEffect(frontCamera) {
        cameraController.cameraSelector =
            if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        lastBody = null
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

