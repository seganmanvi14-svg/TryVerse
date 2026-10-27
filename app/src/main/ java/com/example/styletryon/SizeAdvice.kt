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

/** Measured body widths in cm. */
data class BodySize(val shoulderCm: Float, val hipCm: Float)

/** Frames to average before we show a suggestion (about 2 seconds of camera). */
const val SIZE_SAMPLES = 30

/** Shoulder-to-ankle length as a part of the full height (adult body proportions). */
private const val SHOULDER_TO_ANKLE = 0.78f

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
fun measureBody(body: Body, heightCm: Int): BodySize? {
    if (!Validate.height(heightCm)) return null
    val ankleA = body.ankleA ?: return null
    val ankleB = body.ankleB ?: return null
    val shoulders = (body.shoulderA + body.shoulderB) / 2f
    val ankles = (ankleA + ankleB) / 2f
    val shoulderToAnkle = (ankles - shoulders).getDistance()
    if (shoulderToAnkle < 80f) return null
    val cmPerPixel = heightCm * SHOULDER_TO_ANKLE / shoulderToAnkle
    // Shoulder points are the joints; the outside of the shoulders is about 10% wider
    val shoulderCm = (body.shoulderB - body.shoulderA).getDistance() * cmPerPixel * 1.1f
    val hipCm = (hipWidthPx(body) ?: return null) * cmPerPixel
    if (shoulderCm !in 25f..60f || hipCm !in 22f..60f) return null       // pose too odd to trust
    return BodySize(shoulderCm, hipCm)
}

/** Moves the running average a little towards the new measurement (so it settles, not jumps). */
fun averageSize(old: BodySize?, new: BodySize): BodySize =
    if (old == null) new
    else BodySize(old.shoulderCm * 0.9f + new.shoulderCm * 0.1f, old.hipCm * 0.9f + new.hipCm * 0.1f)

/** Chest is roughly 2.45 x shoulder width. Matches our size guide: XS 32", S 34", M 36", L 38", XL 40". */
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

/** Hip circumference is roughly 2.75 x hip width; jeans waist is about 8" less. Rounded to 28-36. */
fun waistSizeFor(hipCm: Float): String {
    val waistInches = hipCm * 2.75f / 2.54f - 8f
    val even = ((waistInches / 2f).roundToInt() * 2).coerceIn(28, 36)
    return even.toString()
}

fun recommendedSize(product: Product, size: BodySize): String =
    if (product.type == "PANTS") waistSizeFor(size.hipCm) else topSizeFor(size.shoulderCm)

/** Size advice from the measurements saved in the user's profile, or null if they haven't measured yet. */
fun suggestedSize(product: Product, user: User?): String? =
    if (user == null || user.shoulderCm <= 0f || user.hipCm <= 0f) null
    else recommendedSize(product, BodySize(user.shoulderCm, user.hipCm))
