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
