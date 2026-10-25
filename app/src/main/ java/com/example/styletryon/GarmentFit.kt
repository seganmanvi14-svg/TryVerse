package com.example.styletryon

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

//  GARMENT FITTING (used by the live camera, the quick photo preview and the demo model)
//  The garment photo is fitted to the person's body: stretched to the measured
//  shoulders / waist / hips, with the sleeves turned to follow the arms.


/** A sleeve in the garment photo (left sleeve; the right one is its mirror). long = reaches the wrist. */
class SleeveFit(val rootX: Float, val rootY: Float, val endX: Float, val endY: Float, val long: Boolean)

/**
 * Where each garment photo is pinned to the body (fractions of the garment photo):
 *  topY, topL, topR = the shoulder seams (for pants: the waistband)
 *  lowY, lowL, lowR = the waist (dresses, peplum tops) or hem (tops); for pants: the leg hems
 *  lowT = where the low line goes on the body: 0.6 = waist, 1.05 = just below the hips
 *  sleeve = the left sleeve, so it can turn with the arm (null = keep sleeves as they are)
 */
class GarmentFit(
    val topY: Float, val topL: Float, val topR: Float,
    val lowY: Float, val lowL: Float, val lowR: Float,
    val lowT: Float,
    val sleeve: SleeveFit? = null
)

private val garmentFits = mapOf(
    R.drawable.dress_azure_floral to GarmentFit(0.05f, 0.22f, 0.78f, 0.27f, 0.30f, 0.70f, 0.60f, SleeveFit(0.22f, 0.06f, 0.10f, 0.26f, false)),
    R.drawable.dress_emerald_tulle to GarmentFit(0.06f, 0.18f, 0.82f, 0.29f, 0.28f, 0.74f, 0.60f, SleeveFit(0.18f, 0.06f, 0.06f, 0.12f, false)),
    R.drawable.dress_sunset_print to GarmentFit(0.02f, 0.26f, 0.74f, 0.26f, 0.26f, 0.74f, 0.60f),
    R.drawable.top_striped_knit to GarmentFit(0.07f, 0.15f, 0.85f, 0.97f, 0.18f, 0.82f, 1.05f, SleeveFit(0.15f, 0.07f, 0.10f, 0.88f, true)),
    R.drawable.top_blush_blouse to GarmentFit(0.04f, 0.14f, 0.86f, 0.90f, 0.22f, 0.78f, 1.05f, SleeveFit(0.14f, 0.04f, 0.06f, 0.90f, true)),
    R.drawable.top_red_floral to GarmentFit(0.06f, 0.12f, 0.88f, 0.45f, 0.25f, 0.75f, 0.60f),
    R.drawable.tee_midnight_fuji to GarmentFit(0.10f, 0.25f, 0.75f, 0.97f, 0.20f, 0.80f, 1.05f, SleeveFit(0.25f, 0.10f, 0.06f, 0.40f, false)),
    R.drawable.tee_dalmatian to GarmentFit(0.08f, 0.24f, 0.76f, 0.97f, 0.20f, 0.82f, 1.05f, SleeveFit(0.24f, 0.08f, 0.07f, 0.33f, false)),
    R.drawable.shirt_harbour_print to GarmentFit(0.09f, 0.24f, 0.76f, 0.97f, 0.24f, 0.78f, 1.05f),
    R.drawable.pants_rose_denim to GarmentFit(0.01f, 0.02f, 0.98f, 0.99f, 0.02f, 0.98f, 1f),
    R.drawable.pants_sand_chinos to GarmentFit(0.01f, 0.03f, 0.97f, 0.99f, 0.02f, 0.98f, 1f),
    R.drawable.pants_distressed_denim to GarmentFit(0.02f, 0.06f, 0.94f, 0.98f, 0.04f, 0.96f, 1f)
)

fun fitOf(product: Product): GarmentFit = garmentFits[product.image] ?: when (product.type) {
    "PANTS" -> GarmentFit(0.01f, 0.05f, 0.95f, 0.99f, 0.05f, 0.95f, 1f)
    "DRESS" -> GarmentFit(0.04f, 0.25f, 0.75f, 0.28f, 0.28f, 0.72f, 0.60f)
    else -> GarmentFit(0.08f, 0.22f, 0.78f, 0.95f, 0.20f, 0.80f, 1.05f)
}

// ---------------------------------------------------------------------
//  Body
// ---------------------------------------------------------------------

/**
 * Which pixels are the person (from Selfie Segmentation), in camera-image pixels.
 * The scale / dx / dy / mirrorWidth values let us ask about a point in screen pixels.
 */
class PersonMask(
    val values: FloatArray, val width: Int, val height: Int,
    val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f, val mirrorWidth: Float = 0f
) {
    fun isPerson(p: Offset): Boolean {
        val sx = if (mirrorWidth > 0f) mirrorWidth - p.x else p.x
        val x = ((sx - dx) / scale).toInt()
        val y = ((p.y - dy) / scale).toInt()
        return x in 0 until width && y in 0 until height && values[y * width + x] > 0.5f
    }

    fun onScreen(scale: Float, dx: Float, dy: Float, mirrorWidth: Float) = PersonMask(values, width, height, scale, dx, dy, mirrorWidth)
}

/** The body joints we need to dress the person, plus the person's outline. */
data class Body(
    val shoulderA: Offset, val shoulderB: Offset,
    val hipA: Offset?, val hipB: Offset?,
    val ankleA: Offset?, val ankleB: Offset?,
    val elbowA: Offset? = null, val elbowB: Offset? = null,
    val wristA: Offset? = null, val wristB: Offset? = null,
    val mask: PersonMask? = null
) {
    /** Arm bones (shoulder-elbow, elbow-wrist) so we don't measure the arms as part of the body. */
    fun arms(): List<Pair<Offset, Offset>> {
        val list = mutableListOf<Pair<Offset, Offset>>()
        if (elbowA != null) { list.add(shoulderA to elbowA); if (wristA != null) list.add(elbowA to wristA) }
        if (elbowB != null) { list.add(shoulderB to elbowB); if (wristB != null) list.add(elbowB to wristB) }
        return list
    }
}

/** Converts an ML Kit pose into a Body. Returns null if no shoulders are visible. */
fun poseToBody(pose: Pose): Body? {
    fun point(type: Int): Offset? {
        val p = pose.getPoseLandmark(type) ?: return null
        return if (p.inFrameLikelihood > 0.5f) Offset(p.position.x, p.position.y) else null
    }
    val ls = point(PoseLandmark.LEFT_SHOULDER) ?: return null
    val rs = point(PoseLandmark.RIGHT_SHOULDER) ?: return null
    return Body(
        ls, rs,
        point(PoseLandmark.LEFT_HIP), point(PoseLandmark.RIGHT_HIP),
        point(PoseLandmark.LEFT_ANKLE), point(PoseLandmark.RIGHT_ANKLE),
        point(PoseLandmark.LEFT_ELBOW), point(PoseLandmark.RIGHT_ELBOW),
        point(PoseLandmark.LEFT_WRIST), point(PoseLandmark.RIGHT_WRIST)
    )
}

/** Moves the old body halfway towards the new one so the garment does not shake. */
fun smooth(old: Body?, new: Body?): Body? {
    if (old == null || new == null) return new
    fun avg(a: Offset?, b: Offset?): Offset? = if (a == null || b == null) b else a + (b - a) * 0.5f
    return Body(
        avg(old.shoulderA, new.shoulderA)!!, avg(old.shoulderB, new.shoulderB)!!,
        avg(old.hipA, new.hipA), avg(old.hipB, new.hipB),
        avg(old.ankleA, new.ankleA), avg(old.ankleB, new.ankleB),
        avg(old.elbowA, new.elbowA), avg(old.elbowB, new.elbowB),
        avg(old.wristA, new.wristA), avg(old.wristB, new.wristB)
    )
}
//  Fitting the garment to this person's body
private fun mix(a: Offset, b: Offset, t: Float) = a + (b - a) * t

private fun unit(v: Offset): Offset {
    val d = v.getDistance()
    return if (d < 0.001f) Offset(1f, 0f) else v / d
}
/** Returns the two points ordered left-to-right on screen, so the garment photo is never mirrored. */
private fun leftRight(p: Offset, q: Offset) = if (p.x < q.x) Pair(p, q) else Pair(q, p)
/** Distance from point p to the line segment a-b. */
private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val t = if (len2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}
/**
 * Measures how wide the person's body is along a line through [center]: walks left and right
 * until it leaves the person or reaches an arm. If there is no outline, or the result is odd,
 * it uses [fallback] and keeps the width between [min] and [max].
 * Returns the two ends of the garment line, with a little extra room ([ease]).
 */
private fun bodyLine(body: Body, center: Offset, across: Offset, min: Float, max: Float, fallback: Float, ease: Float): Pair<Offset, Offset> {
    val mask = body.mask
    val arms = body.arms()
    val armRadius = (body.shoulderB - body.shoulderA).getDistance() * 0.12f
    fun insideBody(p: Offset) = mask!!.isPerson(p) && arms.none { (a, b) -> distanceToSegment(p, a, b) < armRadius }

    var mid = center
    var width = fallback
    if (mask != null && mask.isPerson(center)) {
        var left = 0f
        var right = 0f
        while (left < max && insideBody(center - across * (left + 1f))) left += 1f
        while (right < max && insideBody(center + across * (right + 1f))) right += 1f
        val measured = left + right
        if (measured in min..max) {
            width = measured
            mid = center + across * ((right - left) / 2f)
        } else {
            width = measured.coerceIn(min, max)
        }
    }
    val half = width * ease / 2f
    return Pair(mid - across * half, mid + across * half)
}
/**
 * The two body lines the garment is pinned to (top-left, top-right, low-left, low-right):
 *  - tops and dresses: shoulder line, and the waist (dresses, peplum) or hip line (tops)
 *  - pants: waist line (just above the hips), and the hem line at the ankles
 * Widths come from the person's measured outline, so the fit follows each body type.
 */
fun bodyLines(product: Product, body: Body, size: Float): List<Offset>? {
    val fit = fitOf(product)
    val (sA, sB) = leftRight(body.shoulderA, body.shoulderB)
    val sw = (sB - sA).getDistance()
    if (sw < 10f) return null
    val across = (sB - sA) / sw
    var down = Offset(-across.y, across.x)
    if (down.y < 0) down = -down
    val shoulders = (sA + sB) / 2f
    // Hips: detected ones, or a guess below the shoulders when they are out of view
    val (hA, hB) = if (body.hipA != null && body.hipB != null) leftRight(body.hipA, body.hipB)
    else Pair(shoulders + down * (sw * 1.35f) - across * (sw * 0.38f), shoulders + down * (sw * 1.35f) + across * (sw * 0.38f))
    val hips = (hA + hB) / 2f
    val hw = maxOf((hB - hA).getDistance(), sw * 0.5f)

    if (product.type == "PANTS") {
        val (waistL, waistR) = bodyLine(body, hips - down * (hw * 0.6f), across, hw * 1.4f, hw * 2.3f, hw * 1.9f, 1.05f * size)
        val (aA, aB) = if (body.ankleA != null && body.ankleB != null) leftRight(body.ankleA, body.ankleB)
        else Pair(hA + down * (sw * 2.6f), hB + down * (sw * 2.6f))
        val aw = (aB - aA).getDistance()
        val ankleAcross = if (aw > 1f) (aB - aA) / aw else across
        val hem = (aA + aB) / 2f + down * (hw * 0.15f)
        val hemHalf = maxOf(aw + hw * 0.9f, hw * 1.2f) * size / 2f
        return listOf(waistL, waistR, hem - ankleAcross * hemHalf, hem + ankleAcross * hemHalf)
    }
    // Shoulder line: the garment's shoulder seams sit on the shoulders
    val top = shoulders - down * (sw * 0.05f)
    val topHalf = sw * 1.12f * size / 2f
    // Low line: measured waist (dresses, peplum tops) or hips (tops)
    val lowCenter = shoulders + (hips - shoulders) * fit.lowT
    val (lowL, lowR) = if (fit.lowT < 1f) bodyLine(body, lowCenter, across, sw * 0.6f, sw * 1.15f, sw * 0.85f, 1.06f * size)   // waist
    else bodyLine(body, lowCenter, across, sw * 0.7f, sw * 1.4f, sw, 1.06f * size)                          // hips
    return listOf(top - across * topHalf, top + across * topHalf, lowL, lowR)
}
private fun rotate(v: Offset, angle: Float): Offset {
    val c = cos(angle)
    val s = sin(angle)
    return Offset(c * v.x - s * v.y, s * v.x + c * v.y)
}
private fun angleOf(v: Offset) = atan2(v.y, v.x)

/**
 * Draws the garment photo on the person (Graphics: Canvas.drawBitmapMesh).
 * A grid is laid over the garment photo and every grid point is moved onto the body:
 *  - the body of the garment is stretched between the shoulder line and the waist/hip line
 *  - the collar and the skirt / trouser legs keep their own shape
 *  - the sleeves turn to follow the person's arms
 */
fun drawGarmentMesh(canvas: android.graphics.Canvas, garment: Bitmap, product: Product, body: Body, size: Float = 1f, shift: Offset = Offset.Zero, tint: Color? = null) {
    val fit = fitOf(product)
    val lines = bodyLines(product, body, size) ?: return
    val (tl, tr, bl, br) = lines
    val gw = garment.width.toFloat()
    val gh = garment.height.toFloat()
    val acrossDir = unit(tr - tl)
    val downDir = unit((bl + br) / 2f - (tl + tr) / 2f)
    val topScale = (tr - tl).getDistance() / ((fit.topR - fit.topL) * gw)
    // The garment itself, without sleeve turning
    fun torso(fx: Float, fy: Float): Offset = when {
        fy < fit.topY ->                                      // collar and shoulders
            mix(tl, tr, (fx - fit.topL) / (fit.topR - fit.topL)) - downDir * ((fit.topY - fy) * gh * topScale)
        fy > fit.lowY -> {                                    // skirt, hem, trouser legs
            val scale = (br - bl).getDistance() / ((fit.lowR - fit.lowL) * gw)
            val drape = if (product.type == "PANTS") 1f else 0.85f   // skirts hang narrower than when laid flat
            (bl + br) / 2f + unit(br - bl) * ((fx - (fit.lowL + fit.lowR) / 2f) * gw * scale * drape) + downDir * ((fy - fit.lowY) * gh * scale)
        }
        else -> {                                             // body of the garment
            val v = (fy - fit.topY) / (fit.lowY - fit.topY)
            val l = fit.topL + (fit.lowL - fit.topL) * v
            val r = fit.topR + (fit.lowR - fit.topR) * v
            val u = (fx - l) / (r - l)
            mix(mix(tl, tr, u), mix(bl, br, u), v)
        }
    }
    // Arms on the left and right of the screen: shoulder and the point the sleeve should point to
    val sleeve = fit.sleeve
    val armsOnScreen = listOf(
        Triple(body.shoulderA, body.elbowA, body.wristA),
        Triple(body.shoulderB, body.elbowB, body.wristB)
    ).sortedBy { it.first.x }
    fun target(fx: Float, fy: Float): Offset {
        var p = torso(fx, fy)
        if (sleeve == null) return p
        for (side in 0..1) {
            val (shoulder, elbow, wrist) = armsOnScreen[side]
            if (elbow == null) continue
            val armTip = if (sleeve.long && wrist != null) wrist else elbow
            // Sleeve root and end in the photo (right sleeve = mirror of the left one)
            val rootX = if (side == 0) sleeve.rootX else 1f - sleeve.rootX
            val endX = if (side == 0) sleeve.endX else 1f - sleeve.endX
            if (fy > maxOf(sleeve.rootY, sleeve.endY) + 0.05f) continue
            // How far outside the garment body this point is (0 = body, 1 = sleeve)
            val v = ((fy - fit.topY) / (fit.lowY - fit.topY)).coerceIn(0f, 1f)
            val edge = if (side == 0) fit.topL + (fit.lowL - fit.topL) * v else fit.topR + (fit.lowR - fit.topR) * v
            val outside = if (side == 0) edge - fx else fx - edge
            val weight = ((outside + 0.02f) / 0.12f).coerceIn(0f, 1f)
            if (weight <= 0f) continue
            // Turn the sleeve around its root so it points along the arm
            val root = torso(rootX, sleeve.rootY)
            val photoDir = acrossDir * ((endX - rootX) * gw) + downDir * ((sleeve.endY - sleeve.rootY) * gh)
            var turn = angleOf(armTip - shoulder) - angleOf(photoDir)
            while (turn > PI) turn -= (2 * PI).toFloat()
            while (turn < -PI) turn += (2 * PI).toFloat()
            val offset = (acrossDir * ((fx - rootX) * gw) + downDir * ((fy - sleeve.rootY) * gh)) * topScale
            val sleevePoint = root + rotate(offset, turn)
            p = mix(p, sleevePoint, weight)
        }
        return p
    }
    val n = 20                                               // 20 x 20 grid
    val points = FloatArray((n + 1) * (n + 1) * 2)
    var k = 0
    for (row in 0..n) {
        for (col in 0..n) {
            val p = target(col.toFloat() / n, row.toFloat() / n) + shift
            points[k++] = p.x
            points[k++] = p.y
        }
    }
    val paint = android.graphics.Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        if (tint != null) colorFilter = recolorFilter(tint)
    }
    canvas.drawBitmapMesh(garment, n, n, points, 0, null, 0, paint)
}

/** Recolours a garment: keeps its light and shade, changes its colour. */
fun recolorFilter(c: Color): ColorMatrixColorFilter {
    val k = 1.15f
    val lift = 35f
    return ColorMatrixColorFilter(
        floatArrayOf(
            0.3f * c.red * k, 0.59f * c.red * k, 0.11f * c.red * k, 0f, lift * c.red,
            0.3f * c.green * k, 0.59f * c.green * k, 0.11f * c.green * k, 0f, lift * c.green,
            0.3f * c.blue * k, 0.59f * c.blue * k, 0.11f * c.blue * k, 0f, lift * c.blue,
            0f, 0f, 0f, 1f, 0f
        )
    )
}
//  Demo model (Home banner, welcome screen, product page) — no ML needed
@Composable
fun DemoModelTryOn(product: Product?, modifier: Modifier = Modifier) {
    val model = ImageBitmap.imageResource(R.drawable.demo_model)
    val garment = product?.let { ImageBitmap.imageResource(it.image) }
    Canvas(modifier.background(Color(0xFFD9D9DE))) {
        // Fill the box with the model photo (crop the sides if needed)
        val scale = maxOf(size.width / model.width, size.height / model.height)
        val w = model.width * scale
        val h = model.height * scale
        val x0 = (size.width - w) / 2f
        val y0 = (size.height - h) / 2f
        drawImage(model, dstOffset = IntOffset(x0.toInt(), y0.toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.High)

        if (product != null && garment != null) {
            // Body points of the model photo (fractions of the photo size)
            fun at(fx: Float, fy: Float) = Offset(x0 + fx * w, y0 + fy * h)
            val body = Body(
                shoulderA = at(0.385f, 0.195f), shoulderB = at(0.615f, 0.195f),
                hipA = at(0.455f, 0.47f), hipB = at(0.595f, 0.47f),
                ankleA = at(0.44f, 0.86f), ankleB = at(0.54f, 0.875f),
                elbowA = at(0.35f, 0.35f), elbowB = at(0.635f, 0.35f),
                wristA = at(0.335f, 0.51f), wristB = at(0.665f, 0.51f)
            )
            drawIntoCanvas { drawGarmentMesh(it.nativeCanvas, garment.asAndroidBitmap(), product, body) }
        }
    }
}

