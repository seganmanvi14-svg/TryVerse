package com.example.styletryon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

// =====================================================================
//  PHOTO TRY-ON (free: Firebase Spark plan)
//  1. The PICTURE is made on the phone with the app's body-fitting engine
//     (ML Kit finds the joints + body outline, then the garment photo is
//     fitted to this person's shoulders, waist, hips and arms).
//  2. GEMINI (free Flash model, text only) looks at the photo + garment and
//     writes a short style analysis. No image generation, so no billing.
// =====================================================================

object AiTryOn {

    /** Free Gemini model (text answers). Stable until at least May 2027. */
    private const val MODEL = "gemini-3.5-flash"

    /** Protects the free quota: at most one AI analysis every 5 seconds. */
    private const val MIN_GAP_MS = 5_000L
    private var lastRequest = 0L

    /** Gemini's style analysis for the last try-on (shown on the result screen). */
    var lastAnalysis: String = ""
        private set

    // Remember the original photo, so "Try Another Color" never draws a
    // garment on top of an old garment.
    private var lastPerson: Bitmap? = null
    private var lastResult: Bitmap? = null

    class TryOnException(message: String) : Exception(message)

    /** True when google-services.json was added (needed only for the AI analysis). */
    fun isSetUp(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Returns a photo of the person wearing [product], fitted to their body.
     * [color] = optional colour name from "Try Another Color" (e.g. "pink").
     */
    suspend fun dress(context: Context, person: Bitmap, product: Product, color: String? = null): Bitmap = coroutineScope {
        // If we were given our own last result (colour change), use the original photo instead
        val photo = if (person === lastResult && lastPerson != null) lastPerson!! else person

        // Start the Gemini analysis in the background (it doesn't block the picture)
        val analysis = async { analyse(context, photo, product, color) }

        // 1. Find the body: joints (ML Kit Pose) + outline (ML Kit Selfie Segmentation)
        val body = detectBody(photo)
            ?: throw TryOnException("Couldn't find a person. Please use a clear, front-facing photo (head to hips).")

        // 2. Fit the real garment photo to this body (shoulders, waist/hips, sleeves follow the arms)
        val tint = color?.let { name -> tryColors.firstOrNull { it.first == name }?.second }
        val look = try {
            renderLook(context, photo, body, product, tint)
        } catch (e: Exception) {
            Log.w("TryVerseAI", "Preview failed", e)
            throw TryOnException("Couldn't create the try-on picture. Please try another photo.")
        }

        // 3. Wait for the AI analysis (never fails the try-on)
        lastAnalysis = analysis.await()
        lastPerson = photo
        lastResult = look
        look
    }

    // -----------------------------------------------------------------
    //  Gemini style analysis (text only, free tier)
    // -----------------------------------------------------------------

    private suspend fun analyse(context: Context, person: Bitmap, product: Product, color: String?): String {
        if (!isSetUp(context)) return ""
        val now = System.currentTimeMillis()
        if (now - lastRequest < MIN_GAP_MS) return lastAnalysis
        lastRequest = now

        return try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(MODEL)
            val prompt = content {
                image(small(person))
                image(garmentOnWhite(context, product))
                text(
                    """
                    You are the friendly stylist in a clothes shopping app called TryVerse.
                    Image 1 is the shopper. Image 2 is the garment they are trying on:
                    "${product.name}" (${product.category}, ${color ?: product.colorName}).
                    In at most 3 short lines, tell them:
                    1. How this garment suits them (be kind and positive).
                    2. One styling tip (what to pair it with).
                    3. A size tip based on how the garment usually fits.
                    Do not comment on weight or body flaws. Plain text, no markdown.
                    """.trimIndent()
                )
            }
            withTimeout(25_000) { model.generateContent(prompt) }.text?.trim().orEmpty()
        } catch (e: Exception) {
            Log.w("TryVerseAI", "Gemini analysis failed", e)
            ""          // the try-on still works without the analysis
        }
    }

    /** The garment photo on a white background (the AI reads it better than transparency). */
    private fun garmentOnWhite(context: Context, product: Product): Bitmap {
        val cutout = BitmapFactory.decodeResource(context.resources, product.image)
        val out = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(cutout, 0f, 0f, null)
        return out
    }

    /**
     * A smaller, re-encoded copy of the photo for the AI: faster, uses less quota,
     * and has no hidden data (e.g. GPS location) from the original file.
     */
    private fun small(photo: Bitmap): Bitmap {
        val scale = 512f / maxOf(photo.width, photo.height)
        val resized = if (scale < 1f)
            Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true)
        else photo
        val bytes = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, bytes)
        val data = bytes.toByteArray()
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }
}