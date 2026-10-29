// =====================================================================
// GarmentFit.kt = THE FITTING ENGINE: puts a flat garment photo onto a person's body.
// Used by: the live camera, the photo / sample-model try-on, and the demo model photo.
// 1. We know where the body is (shoulders, hips, ankles, elbows, wrists + the outline).
// 2. We measure the body: shoulder line, waist, hips (and for pants: waist, hips, ankles).
// 3. A 20 x 20 grid is laid over the garment photo and every grid point is moved onto the body
//    (Canvas.drawBitmapMesh), so the garment stretches and bends to fit. Sleeves turn with the arms.
// =====================================================================
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

// =====================================================================
//  GARMENT FITTING (used by the live camera, the quick photo preview and the demo model)
//  The garment photo is fitted to the person's body: stretched to the measured
//  shoulders / waist / hips, with the sleeves turned to follow the arms.
// =====================================================================

// Where the LEFT sleeve is in the garment photo: root (at the shoulder) and end. The right sleeve is its mirror.
// long = the sleeve reaches the wrist (else it ends at the elbow)
class SleeveFit(val rootX: Float, val rootY: Float, val endX: Float, val endY: Float, val long: Boolean)

/**
 * Where each garment photo is pinned to the body (fractions of the garment photo):
 *  topY, topL, topR = the shoulder seams (for pants: the waistband)
 *  lowY, lowL, lowR = the waist (dresses, peplum tops) or hem (tops); for pants: the leg hems
 *  lowT = where the low line goes on the body: 0.6 = waist, 1.05 = just below the hips
 *  sleeve = the left sleeve, so it can turn with the arm (null = keep sleeves as they are)
 *  drape = how much narrower the hem of a skirt hangs than when laid flat (1 = no change)
 */
class GarmentFit(
    val topY: Float, val topL: Float, val topR: Float,
    val lowY: Float, val lowL: Float, val lowR: Float,
    val lowT: Float,
    val sleeve: SleeveFit? = null,
    val drape: Float = 0.9f
)

// *** FIT NUMBERS FOR EACH GARMENT PHOTO ***
// All numbers are fractions of the photo (0 = left/top edge, 1 = right/bottom edge).
// Example: 0.06f, 0.29f, 0.71f = the shoulder seams are 6% down, 29% and 71% across
private val garmentFits = mapOf(
    R.drawable.dress_azure_floral to GarmentFit(0.05f, 0.22f, 0.78f, 0.27f, 0.30f, 0.70f, 0.60f, SleeveFit(0.22f, 0.06f, 0.10f, 0.26f, false)),
    R.drawable.dress_emerald_tulle to GarmentFit(0.06f, 0.18f, 0.82f, 0.29f, 0.28f, 0.74f, 0.60f, SleeveFit(0.18f, 0.06f, 0.06f, 0.12f, false)),
    R.drawable.dress_sunset_print to GarmentFit(0.02f, 0.26f, 0.74f, 0.26f, 0.26f, 0.74f, 0.60f),
    R.drawable.top_striped_knit to GarmentFit(0.07f, 0.15f, 0.85f, 0.97f, 0.18f, 0.82f, 1.05f, SleeveFit(0.15f, 0.07f, 0.10f, 0.88f, true)),
    R.drawable.top_blush_blouse to GarmentFit(0.06f, 0.29f, 0.71f, 0.76f, 0.30f, 0.70f, 1.05f, SleeveFit(0.27f, 0.07f, 0.12f, 0.82f, true)),
    R.drawable.top_red_floral to GarmentFit(0.06f, 0.12f, 0.88f, 0.45f, 0.25f, 0.75f, 0.60f),
    R.drawable.tee_midnight_fuji to GarmentFit(0.10f, 0.25f, 0.75f, 0.97f, 0.20f, 0.80f, 1.05f, SleeveFit(0.25f, 0.10f, 0.06f, 0.40f, false)),
    R.drawable.tee_dalmatian to GarmentFit(0.08f, 0.24f, 0.76f, 0.97f, 0.20f, 0.82f, 1.05f, SleeveFit(0.24f, 0.08f, 0.07f, 0.33f, false)),
    R.drawable.shirt_harbour_print to GarmentFit(0.09f, 0.24f, 0.76f, 0.97f, 0.24f, 0.78f, 1.05f),
    R.drawable.pants_rose_denim to GarmentFit(0.01f, 0.02f, 0.98f, 0.99f, 0.02f, 0.98f, 1f),
    R.drawable.pants_sand_chinos to GarmentFit(0.01f, 0.03f, 0.97f, 0.99f, 0.02f, 0.98f, 1f),
    R.drawable.pants_distressed_denim to GarmentFit(0.02f, 0.06f, 0.94f, 0.98f, 0.04f, 0.96f, 1f),
    // New arrivals
    // The 12 new garments
    R.drawable.top_white_puff_blouse to GarmentFit(0.10f, 0.22f, 0.78f, 0.97f, 0.22f, 0.78f, 0.62f, SleeveFit(0.24f, 0.10f, 0.06f, 0.62f, false)),
    R.drawable.top_black_ribbed to GarmentFit(0.04f, 0.13f, 0.87f, 0.95f, 0.22f, 0.78f, 1.05f, SleeveFit(0.13f, 0.04f, 0.06f, 0.96f, true)),
    R.drawable.shirt_denim_blue to GarmentFit(0.12f, 0.24f, 0.76f, 0.95f, 0.17f, 0.83f, 1.05f, SleeveFit(0.22f, 0.12f, 0.05f, 0.88f, true)),
    R.drawable.tee_olive_oversized to GarmentFit(0.10f, 0.26f, 0.74f, 0.97f, 0.18f, 0.82f, 1.05f, SleeveFit(0.24f, 0.10f, 0.04f, 0.50f, false)),
    R.drawable.tee_sunset_print to GarmentFit(0.10f, 0.26f, 0.74f, 0.97f, 0.16f, 0.84f, 1.05f, SleeveFit(0.24f, 0.10f, 0.03f, 0.43f, false)),
    R.drawable.kurti_pink_embroidered to GarmentFit(0.05f, 0.22f, 0.78f, 0.62f, 0.20f, 0.80f, 1.05f, SleeveFit(0.22f, 0.05f, 0.04f, 0.50f, false)),
    R.drawable.dress_black_satin to GarmentFit(0.02f, 0.12f, 0.87f, 0.30f, 0.27f, 0.71f, 0.60f),
    R.drawable.dress_yellow_floral to GarmentFit(0.02f, 0.27f, 0.72f, 0.38f, 0.35f, 0.65f, 0.70f),
    R.drawable.dress_lavender_oneshoulder to GarmentFit(0.06f, 0.0f, 0.78f, 0.42f, 0.08f, 0.70f, 0.60f),
    R.drawable.pants_black_wideleg to GarmentFit(0.01f, 0.24f, 0.72f, 0.99f, 0.02f, 0.98f, 1f),
    R.drawable.pants_lightwash_jeans to GarmentFit(0.01f, 0.17f, 0.83f, 0.99f, 0.02f, 0.98f, 1f),
    R.drawable.pants_beige_pleated to GarmentFit(0.01f, 0.04f, 0.96f, 0.99f, 0.02f, 0.98f, 1f)
)

// The fit numbers for a product. If a garment has none, use a normal value for its type
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
// *** THE PERSON'S OUTLINE (from ML Kit Selfie Segmentation) ***
// values = one number 0-1 per pixel (how sure ML Kit is that it is the person). scale / dx / dy / mirror
// let us ask about a SCREEN point even though the mask is in camera pixels
class PersonMask(
    val values: FloatArray, val width: Int, val height: Int,
    val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f, val mirrorWidth: Float = 0f
) {
    // Is this point on the person? (more than 50% sure)
    fun isPerson(p: Offset): Boolean {
        val sx = if (mirrorWidth > 0f) mirrorWidth - p.x else p.x
        val x = ((sx - dx) / scale).toInt()
        val y = ((p.y - dy) / scale).toInt()
        return x in 0 until width && y in 0 until height && values[y * width + x] > 0.5f
    }

    // Same mask, but with the numbers that convert screen pixels to mask pixels
    fun onScreen(scale: Float, dx: Float, dy: Float, mirrorWidth: Float) = PersonMask(values, width, height, scale, dx, dy, mirrorWidth)
}

// *** THE BODY: the joints we need + the outline *** (A and B = the two sides; hips / ankles / arms may be
// missing)
data class Body(
    val shoulderA: Offset, val shoulderB: Offset,
    val hipA: Offset?, val hipB: Offset?,
    val ankleA: Offset?, val ankleB: Offset?,
    val elbowA: Offset? = null, val elbowB: Offset? = null,
    val wristA: Offset? = null, val wristB: Offset? = null,
    val mask: PersonMask? = null
) {
    // Arm bones (shoulder-elbow, elbow-wrist, the hand), so arms are NOT counted when measuring the waist / hips
    fun arms(): List<Pair<Offset, Offset>> {
        val list = mutableListOf<Pair<Offset, Offset>>()
        if (elbowA != null) {
            list.add(shoulderA to elbowA)
            if (wristA != null) { list.add(elbowA to wristA); list.add(wristA to wristA + (wristA - elbowA) * 0.6f) }
        }
        if (elbowB != null) {
            list.add(shoulderB to elbowB)
            if (wristB != null) { list.add(elbowB to wristB); list.add(wristB to wristB + (wristB - elbowB) * 0.6f) }
        }
        return list
    }
}

// *** ML KIT POSE -> OUR BODY ***
// Takes the shoulders, hips, ankles, elbows and wrists. A point is used only if ML Kit is more than 50% sure
// it is in the picture. No shoulders = no body (null)
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

// Moves the old body HALFWAY to the new one, so the clothes don't shake in the live camera
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

// ---------------------------------------------------------------------
//  Fitting the garment to this person's body
// ---------------------------------------------------------------------

// Small maths helpers: a point between a and b (t = 0..1), a direction of length 1, left-to-right order,
// distance to a line
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
// *** MEASURES HOW WIDE THE BODY IS at one height ***
// From [center], walk left and right one pixel at a time until we leave the person or reach an arm.
// If there is no outline (demo model) or the result is strange, use [fallback] and keep it between [min] and
// [max].
// Returns the two ends of the line, a little wider than the body ([ease]) so clothes are not skin-tight
private fun bodyLine(body: Body, center: Offset, across: Offset, min: Float, max: Float, fallback: Float, ease: Float): Pair<Offset, Offset> {
    val mask = body.mask
    val arms = body.arms()
    val armRadius = (body.shoulderB - body.shoulderA).getDistance() * 0.12f
    fun insideBody(p: Offset) = mask!!.isPerson(p) && arms.none { (a, b) -> distanceToSegment(p, a, b) < armRadius }

    var mid = center
    var width = fallback
    // Walk left and right
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
 * The body lines the garment is pinned to. Returns 6 points:
 *  - tops and dresses: shoulder line (left, right), waist or hip line (left, right), and the
 *    hip line a skirt should hug (left, right)
 *  - pants: waist line, hem line at the ankles, and the hip line (widest part of the legs)
 * Widths come from the person's measured outline, so the fit follows each body type.
 * The shoulder, hip and ankle points are the body JOINTS (as ML Kit finds them), so the
 * outline of the body is a little wider than these points.
 */
// *** WHERE THE GARMENT IS PINNED ON THE BODY *** (6 points = 3 lines)
fun bodyLines(product: Product, body: Body, size: Float): List<Offset>? {
    val fit = fitOf(product)
    val (sA, sB) = leftRight(body.shoulderA, body.shoulderB)
    val sw = (sB - sA).getDistance()
    if (sw < 10f) return null
    // across = direction from one shoulder to the other, down = at a right angle to it (works even if the person
    // leans)
    val across = (sB - sA) / sw
    var down = Offset(-across.y, across.x)
    if (down.y < 0) down = -down
    val shoulders = (sA + sB) / 2f

    // Hips: detected ones, or a guess below the shoulders when they are out of view
    // Hips found? Use them. Not in view (e.g. a selfie)? Guess them below the shoulders
    val (hA, hB) = if (body.hipA != null && body.hipB != null) leftRight(body.hipA, body.hipB)
    else Pair(shoulders + down * (sw * 1.35f) - across * (sw * 0.38f), shoulders + down * (sw * 1.35f) + across * (sw * 0.38f))
    val hips = (hA + hB) / 2f
    val hw = maxOf((hB - hA).getDistance(), sw * 0.5f)

    // *** PANTS: waist line, hip line (widest part), and hem line at the ankles ***
    if (product.type == "PANTS") {
        val (waistL, waistR) = bodyLine(body, hips - down * (hw * 0.6f), across, hw * 1.4f, hw * 2.3f, hw * 1.9f, 1.1f * size)
        // Hip line: the widest part, so the trousers cover the hips and thighs
        val (hipL, hipR) = bodyLine(body, hips + down * (hw * 0.15f), across, hw * 1.5f, hw * 2.8f, hw * 2.1f, 1.2f * size)
        // Ankles found, or guessed far below the hips
        val (aA, aB) = if (body.ankleA != null && body.ankleB != null) leftRight(body.ankleA, body.ankleB)
        else Pair(hA + down * (sw * 2.6f), hB + down * (sw * 2.6f))
        val aw = (aB - aA).getDistance()
        val ankleAcross = if (aw > 1f) (aB - aA) / aw else across
        val hem = (aA + aB) / 2f + down * (hw * 0.15f)
        // The hem is a bit wider than the space between the ankles, so both legs are covered
        val hemHalf = maxOf(aw + hw * 1.3f, hw * 1.4f) * size / 2f
        return listOf(waistL, waistR, hem - ankleAcross * hemHalf, hem + ankleAcross * hemHalf, hipL, hipR)
    }

    // Shoulder line: the garment's shoulder seams sit just outside the shoulder joints
    // *** TOPS AND DRESSES ***
    // Shoulder line: just above and a little outside the shoulder joints
    val top = shoulders - down * (sw * 0.05f)
    val topHalf = sw * 1.16f * size / 2f
    // Low line: measured waist (dresses, peplum tops) or hips (tops).
    // The waist is never wider than the hips allow (arms held close to the body can make the outline look wider).
    // Waist (dresses, peplum tops) or hip line (tops). The waist is limited by the hip size, because
    // arms close to the body would otherwise make the waist look too wide
    val lowCenter = shoulders + (hips - shoulders) * fit.lowT
    val (lowL, lowR) = if (fit.lowT < 1f) bodyLine(body, lowCenter, across, minOf(sw * 0.6f, hw * 1.2f), minOf(sw * 1.15f, hw * 1.4f), minOf(sw * 0.85f, hw * 1.35f), 1.06f * size)   // waist
                       else bodyLine(body, lowCenter, across, sw * 0.7f, sw * 1.4f, sw, 1.06f * size)                                                                       // hips
    // Hip line for skirts: a skirt sits close to the hips before it flares out
    // Hip line for skirts: a skirt sits close to the hips before it flares out
    val (hipL, hipR) = bodyLine(body, hips + down * (hw * 0.3f), across, minOf(sw * 0.7f, hw * 1.5f), minOf(sw * 1.5f, hw * 1.95f), minOf(sw, hw * 1.85f), 1.08f * size)
    return listOf(top - across * topHalf, top + across * topHalf, lowL, lowR, hipL, hipR)
}

// How wide the garment photo is at each height (read from its see-through pixels once, then remembered)
private val rowWidths = HashMap<Int, FloatArray>()

private fun garmentWidthAt(garment: Bitmap, key: Int, fy: Float): Float {
    val widths = rowWidths.getOrPut(key) {
        val rows = 50
        val line = IntArray(garment.width)
        FloatArray(rows + 1) { i ->
            try {
                val y = (i * (garment.height - 1)) / rows
                garment.getPixels(line, 0, garment.width, 0, y, garment.width, 1)
                val first = line.indexOfFirst { (it ushr 24) > 128 }
                val last = line.indexOfLast { (it ushr 24) > 128 }
                if (first < 0) 0f else (last - first + 1).toFloat() / garment.width
            } catch (e: Exception) {
                0f                                          // pixels can't be read (hardware bitmap): no narrowing
            }
        }
    }
    return widths[(fy.coerceIn(0f, 1f) * (widths.size - 1)).toInt()]
}

// Turns a direction by an angle (used to turn the sleeves), and the angle of a direction
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
// *** CLOTHES ARE DRAWN ON THE BODY HERE ***
// canvas = where to draw, garment = the garment photo, size = bigger for bigger sizes,
// shift = moved by the user's finger, tint = new colour (Try Another Colour)
fun drawGarmentMesh(canvas: android.graphics.Canvas, garment: Bitmap, product: Product, body: Body, size: Float = 1f, shift: Offset = Offset.Zero, tint: Color? = null) {
    val fit = fitOf(product)
    // STEP 1: the body lines (tl, tr = top left/right; bl, br = low left/right; hipL, hipR = hips)
    val lines = bodyLines(product, body, size) ?: return
    val (tl, tr, bl, br) = lines
    val hipL = lines[4]
    val hipR = lines[5]
    val gw = garment.width.toFloat()
    val gh = garment.height.toFloat()
    val acrossDir = unit(tr - tl)
    val downDir = unit((bl + br) / 2f - (tl + tr) / 2f)
    // STEP 2: how much the photo must be scaled at the top and at the bottom line to fit this body
    val topScale = (tr - tl).getDistance() / ((fit.topR - fit.topL) * gw)
    val lowScale = (br - bl).getDistance() / ((fit.lowR - fit.lowL) * gw)

    // Skirts: narrow the skirt so it sits close to the hips (a dress laid flat is wider than a body)
    // STEP 3 (skirts only): a dress laid flat is wider than a body, so make the skirt hug the hips
    var hipNarrow = 1f
    var hipRow = fit.lowY + 0.1f
    if (product.type != "PANTS" && fit.lowY < 0.8f) {
        hipRow = (fit.lowY + ((hipL + hipR) / 2f - (bl + br) / 2f).getDistance() / (gh * lowScale)).coerceIn(fit.lowY + 0.02f, 0.98f)
        val flatWidth = garmentWidthAt(garment, product.image, hipRow) * gw * lowScale
        if (flatWidth > 0f) hipNarrow = minOf(1f, (hipR - hipL).getDistance() / flatWidth)
    }
    // Pants: the hip line sits this far down the garment photo
    // In a pants photo, the hips are 17% of the way down
    val pantsHipRow = 0.17f

    // The garment itself, without sleeve turning
    // *** STEP 4: WHERE ONE POINT OF THE GARMENT PHOTO GOES ON THE BODY ***
    // (fx, fy) = a point in the photo (0-1). Returns the point on the screen
    fun torso(fx: Float, fy: Float): Offset = when {
        // Above the shoulders (collar): keeps its shape, just scaled
        fy < fit.topY ->                                      // collar and shoulders
            mix(tl, tr, (fx - fit.topL) / (fit.topR - fit.topL)) - downDir * ((fit.topY - fy) * gh * topScale)
        // Below the low line (skirt / trouser legs): skirts hang straight down and get a little narrower to the
        // hem;
        // trouser legs follow the body
        fy > fit.lowY -> {                                    // skirt, hem, trouser legs
            // A skirt hugs the hips, then hangs a little narrower towards the hem than when laid flat
            val t = ((fy - fit.lowY) / (1f - fit.lowY)).coerceIn(0f, 1f)
            val narrow = if (product.type == "PANTS") 1f
            else (1f + (hipNarrow - 1f) * ((fy - fit.lowY) / (hipRow - fit.lowY)).coerceIn(0f, 1f)) * (1f - (1f - fit.drape) * t)
            // Trouser legs follow the body; skirts hang straight down (gravity), even if the person leans
            val hang = if (product.type == "PANTS") downDir else Offset(0f, 1f)
            (bl + br) / 2f + unit(br - bl) * ((fx - (fit.lowL + fit.lowR) / 2f) * gw * lowScale * narrow) + hang * ((fy - fit.lowY) * gh * lowScale)
        }
        // Between the lines (the body of the garment): stretched between the shoulder line and the low line.
        // Pants go waist -> hips -> ankles, so they are widest at the hips like a real body
        else -> {                                             // body of the garment
            val v = (fy - fit.topY) / (fit.lowY - fit.topY)
            val l = fit.topL + (fit.lowL - fit.topL) * v
            val r = fit.topR + (fit.lowR - fit.topR) * v
            val u = (fx - l) / (r - l)
            if (product.type == "PANTS") {
                // Waist -> hips -> ankles, so the trousers are widest at the hips like a real body
                val vh = ((pantsHipRow - fit.topY) / (fit.lowY - fit.topY)).coerceIn(0.05f, 0.95f)
                if (v < vh) mix(mix(tl, tr, u), mix(hipL, hipR, u), v / vh)
                else mix(mix(hipL, hipR, u), mix(bl, br, u), (v - vh) / (1f - vh))
            } else mix(mix(tl, tr, u), mix(bl, br, u), v)
        }
    }

    // Arms on the left and right of the screen: shoulder and the point the sleeve should point to
    // *** STEP 5: SLEEVES FOLLOW THE ARMS ***
    val sleeve = fit.sleeve
    val armsOnScreen = listOf(
        Triple(body.shoulderA, body.elbowA, body.wristA),
        Triple(body.shoulderB, body.elbowB, body.wristB)
    ).sortedBy { it.first.x }

    // Where a photo point goes, including the sleeve turning
    fun target(fx: Float, fy: Float): Offset {
        var p = torso(fx, fy)
        if (sleeve == null) return p
        for (side in 0..1) {
            val (_, elbow, wrist) = armsOnScreen[side]
            if (elbow == null) continue
            // Long sleeves point to the wrist, short ones to the elbow
            val armTip = if (sleeve.long && wrist != null) wrist else elbow
            // Sleeve root and end in the photo (right sleeve = mirror of the left one)
            val rootX = if (side == 0) sleeve.rootX else 1f - sleeve.rootX
            val endX = if (side == 0) sleeve.endX else 1f - sleeve.endX
            if (fy > maxOf(sleeve.rootY, sleeve.endY) + 0.05f) continue
            // How far outside the garment body this point is (0 = body, 1 = sleeve)
            val v = ((fy - fit.topY) / (fit.lowY - fit.topY)).coerceIn(0f, 1f)
            val edge = if (side == 0) fit.topL + (fit.lowL - fit.topL) * v else fit.topR + (fit.lowR - fit.topR) * v
            val outside = if (side == 0) edge - fx else fx - edge
            // How much this point is part of the sleeve (0 = body of the garment, 1 = sleeve), so the join is
            // smooth
            val weight = ((outside + 0.02f) / 0.12f).coerceIn(0f, 1f)
            if (weight <= 0f) continue
            // Turn the sleeve around its root so it points along the arm
            val root = torso(rootX, sleeve.rootY)
            val photoDir = acrossDir * ((endX - rootX) * gw) + downDir * ((sleeve.endY - sleeve.rootY) * gh)
            // Aim from the sleeve's root (just outside the shoulder) at the elbow / wrist, so the sleeve lies on the arm
            var turn = angleOf(armTip - root) - angleOf(photoDir)
            while (turn > PI) turn -= (2 * PI).toFloat()
            while (turn < -PI) turn += (2 * PI).toFloat()
            val offset = (acrossDir * ((fx - rootX) * gw) + downDir * ((fy - sleeve.rootY) * gh)) * topScale
            val sleevePoint = root + rotate(offset, turn)
            p = mix(p, sleevePoint, weight)
        }
        return p
    }

    // *** STEP 6: THE 20 x 20 GRID (drawBitmapMesh) ***
    // Work out where all 21 x 21 grid points go, then Android draws the photo stretched between them
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
    // Smooth drawing, and the colour filter if a new colour was chosen
    val paint = android.graphics.Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        if (tint != null) colorFilter = recolorFilter(tint)
    }
    // *** THE GARMENT IS DRAWN, BENT ONTO THE BODY ***
    canvas.drawBitmapMesh(garment, n, n, points, 0, null, 0, paint)
}

// *** TRY ANOTHER COLOUR ***
// A colour matrix: turns each pixel to grey (keeps light and shade) and then tints it with the new colour
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

// ---------------------------------------------------------------------
//  Demo model (Home banner, welcome screen, product page) — no ML needed
// ---------------------------------------------------------------------

/**
 * Body joints of the demo model photo (demo_model.webp), as fractions of the photo:
 * shoulders A/B, hips A/B, ankles A/B, elbows A/B, wrists A/B, each as x, y.
 * She wears a fitted top and leggings and stands straight, so any garment covers them neatly.
 */
// Where the demo model's joints are in her photo (fractions). No ML Kit needed: we measured them once
private val demoPoints = floatArrayOf(
    0.401f, 0.305f, 0.571f, 0.305f,      // shoulders
    0.432f, 0.49f, 0.538f, 0.49f,      // hips
    0.418f, 0.907f, 0.541f, 0.907f,      // ankles
    0.376f, 0.454f, 0.591f, 0.454f,      // elbows
    0.365f, 0.573f, 0.599f, 0.573f       // wrists
)

// *** DEMO MODEL ***
// Shows the demo model photo wearing [product] (and [under] = pants under a top, for mix & match).
// Used on the Home banner, the product page ('on model') and the live screen without camera permission
@Composable
fun DemoModelTryOn(product: Product?, modifier: Modifier = Modifier, under: Product? = null) {
    val model = ImageBitmap.imageResource(R.drawable.demo_model)
    val garment = product?.let { ImageBitmap.imageResource(it.image) }
    val underGarment = under?.let { ImageBitmap.imageResource(it.image) }
    Canvas(modifier.background(Color(0xFFD9D9DE))) {
        // Fill the box with the model photo (crop the sides if needed)
        // Fill the box with the photo (crop the sides if needed) and draw it
        val scale = maxOf(size.width / model.width, size.height / model.height)
        val w = model.width * scale
        val h = model.height * scale
        val x0 = (size.width - w) / 2f
        val y0 = (size.height - h) / 2f
        drawImage(model, dstOffset = IntOffset(x0.toInt(), y0.toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.High)

        if (product != null && garment != null) {
            // Body points of the model photo
            val p = demoPoints
            fun at(i: Int) = Offset(x0 + p[i] * w, y0 + p[i + 1] * h)
            // Her joints, converted from fractions to screen pixels
            val body = Body(
                shoulderA = at(0), shoulderB = at(2), hipA = at(4), hipB = at(6), ankleA = at(8), ankleB = at(10),
                elbowA = at(12), elbowB = at(14), wristA = at(16), wristB = at(18)
            )
            // Draw the pants first (if any), then the main garment
            drawIntoCanvas {
                if (under != null && underGarment != null) drawGarmentMesh(it.nativeCanvas, underGarment.asAndroidBitmap(), under, body)
                drawGarmentMesh(it.nativeCanvas, garment.asAndroidBitmap(), product, body)
            }
        }
    }
}
