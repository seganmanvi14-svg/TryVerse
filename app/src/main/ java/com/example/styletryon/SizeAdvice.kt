// =====================================================================
// SizeAdvice.kt = SIZE SUGGESTION from the user's height + the live camera.
// Idea: we know the user's height in cm, and the camera tells us the body in PIXELS.
// Shoulder-to-ankle is about 78% of a person's height -> so we can work out how many cm one pixel is.
// Then: shoulder width in cm -> top size (XS-XL), hip width in cm -> jeans waist size (28-36).
// =====================================================================
package com.example.styletryon

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

// =====================================================================
//  SIZE RECOMMENDATION (height + live camera)
//  The user enters their height once. In the live camera we know, in pixels,
//  how far the shoulders are from the ankles. In an adult that distance is about
//  78% of the height, so it tells us how many cm one pixel is. Then:
//   - shoulder width (ML Kit shoulder points)           -> top / dress size
//   - hip width (person's outline from Selfie Segmentation) -> waist size for jeans
//  It is an estimate (clothes, pose and camera angle change it), so the app
//  always shows it as a suggestion, never as a rule.
// =====================================================================

// The measured shoulder width and hip width in cm
data class BodySize(val shoulderCm: Float, val hipCm: Float)

// We average 30 camera frames (about 2 seconds) before showing a size, so one bad frame doesn't change it
const val SIZE_SAMPLES = 30

// Shoulder-to-ankle length = 78% of the full height (normal adult body proportions)
private const val SHOULDER_TO_ANKLE = 0.78f

// Distance from a point to a line (used to ignore the arms when measuring the hips)
private fun pointToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val t = if (len2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/**
 * Hip width in pixels: walks left and right from the middle of the hips until it leaves
 * the person's outline (ignoring the arms). Without an outline it guesses from the hip points.
 */
// *** HIP WIDTH IN PIXELS ***
// From the middle of the hips, walk left and right until we leave the person's outline (the arms don't count).
// No outline? Guess 1.6 x the distance between the hip points
private fun hipWidthPx(body: Body): Float? {
    val hA = body.hipA ?: return null
    val hB = body.hipB ?: return null
    val d = (hB - hA).getDistance()
    if (d < 5f) return null
    val guess = d * 1.6f                     // ML Kit hip points sit inside the body
    val mask = body.mask ?: return guess
    val across = (hB - hA) / d
    val center = (hA + hB) / 2f
    if (!mask.isPerson(center)) return guess
    val arms = body.arms()
    val armRadius = (body.shoulderB - body.shoulderA).getDistance() * 0.12f
    fun inside(p: Offset) = mask.isPerson(p) && arms.none { (a, b) -> pointToSegment(p, a, b) < armRadius }
    // Walk left and right one pixel at a time
    var left = 0f
    var right = 0f
    val max = d * 3f
    while (left < max && inside(center - across * (left + 1f))) left += 1f
    while (right < max && inside(center + across * (right + 1f))) right += 1f
    val width = left + right
    return if (width in d * 1.1f..d * 2.6f) width else guess
}

/**
 * Measures one camera frame. [body] must be in camera-image pixels.
 * Returns null unless shoulders, hips and both ankles are visible.
 */
// *** MEASURES THE BODY IN ONE CAMERA FRAME ***
// Needs the height and the whole body (shoulders, hips, both ankles) in view, otherwise null
fun measureBody(body: Body, heightCm: Int): BodySize? {
    if (!Validate.height(heightCm)) return null
    val ankleA = body.ankleA ?: return null
    val ankleB = body.ankleB ?: return null
    val shoulders = (body.shoulderA + body.shoulderB) / 2f
    val ankles = (ankleA + ankleB) / 2f
    val shoulderToAnkle = (ankles - shoulders).getDistance()
    if (shoulderToAnkle < 80f) return null
    // STEP 1: how many cm is one pixel? (height x 0.78 / shoulder-to-ankle pixels)
    val cmPerPixel = heightCm * SHOULDER_TO_ANKLE / shoulderToAnkle
    // Shoulder points are the joints; the outside of the shoulders is about 10% wider
    // STEP 2: shoulder width in cm (+10%, because the points are the joints, not the outside of the shoulders)
    val shoulderCm = (body.shoulderB - body.shoulderA).getDistance() * cmPerPixel * 1.1f
    // STEP 3: hip width in cm
    val hipCm = (hipWidthPx(body) ?: return null) * cmPerPixel
    // STEP 4: ignore impossible results (the pose was too odd to trust)
    if (shoulderCm !in 25f..60f || hipCm !in 22f..60f) return null       // pose too odd to trust
    return BodySize(shoulderCm, hipCm)
}

// Running average: 90% old + 10% new, so the number settles instead of jumping
fun averageSize(old: BodySize?, new: BodySize): BodySize =
    if (old == null) new
    else BodySize(old.shoulderCm * 0.9f + new.shoulderCm * 0.1f, old.hipCm * 0.9f + new.hipCm * 0.1f)

// *** TOP SIZE ***
// Chest ≈ 2.45 x shoulder width, in inches. Then our size guide: XS 32", S 34", M 36", L 38", XL 40"
fun topSizeFor(shoulderCm: Float): String {
    val chestInches = shoulderCm * 2.45f / 2.54f
    return when {
        chestInches < 33f -> "XS"
        chestInches < 35f -> "S"
        chestInches < 37f -> "M"
        chestInches < 39f -> "L"
        else -> "XL"
    }
}

// *** JEANS WAIST SIZE ***
// Hip all the way round ≈ 2.75 x hip width; jeans waist ≈ 8 inches less. Rounded to an even size 28-36
fun waistSizeFor(hipCm: Float): String {
    val waistInches = hipCm * 2.75f / 2.54f - 8f
    val even = ((waistInches / 2f).roundToInt() * 2).coerceIn(28, 36)
    return even.toString()
}

// Pants -> waist size, anything else -> top size
fun recommendedSize(product: Product, size: BodySize): String =
    if (product.type == "PANTS") waistSizeFor(size.hipCm) else topSizeFor(size.shoulderCm)

// *** 'YOUR SIZE' ON THE PRODUCT PAGE ***
// Uses the measurements saved in the user's profile (null if they haven't done the body scan yet)
fun suggestedSize(product: Product, user: User?): String? =
    if (user == null || user.shoulderCm <= 0f || user.hipCm <= 0f) null
    else recommendedSize(product, BodySize(user.shoulderCm, user.hipCm))
