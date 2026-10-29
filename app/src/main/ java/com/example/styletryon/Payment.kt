// =====================================================================
// Payment.kt = ONLINE PAYMENT (Google Pay in TEST mode).
// Flow: Cart -> 'Pay online' -> PaymentActivity opens -> Google Pay sheet (or our test checkout)
// -> the result (paid / cancelled / failed) goes back to the cart -> the order is saved only if paid.
// No real money is ever taken: Google's TEST environment returns a fake payment token.
// =====================================================================
package com.example.styletryon

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.gms.wallet.PaymentData as GooglePaymentData

// =====================================================================
//  ONLINE PAYMENT (TEST MODE: no real money is ever taken)
//  The cart starts this small see-through screen, which opens a payment sheet
//  and passes the result (paid / cancelled / failed) back to the cart.
//
//  1. Google Pay API in its TEST environment (used by default)
//     Google's real payment sheet, with test cards. Needs no merchant account or key.
//  2. TryVerse test checkout: if Google Pay isn't available on the phone (for example
//     error 412 on an emulator without the Play Store, or old Play services), a simple
//     in-app test payment screen (test UPI / test card) is shown instead.
//  3. Razorpay Standard Checkout (used instead when a Razorpay test key is set)
//     Add RAZORPAY_KEY=rzp_test_... to local.properties (never put on GitHub).
//     The secret key is never in the app.
// =====================================================================

// 'object' = only one copy exists in the whole app. Holds the names of the values we pass between screens
object Payment {
    // EXTRA_... = the names (keys) used to pass values in an Intent (a message that opens another screen)
    const val EXTRA_AMOUNT = "amount"          // rupees
    const val EXTRA_NAME = "name"
    const val EXTRA_EMAIL = "email"
    const val EXTRA_PHONE = "phone"
    const val EXTRA_DESCRIPTION = "description"
    const val EXTRA_PAYMENT_ID = "payment_id"   // returned on success
    const val EXTRA_METHOD = "method"           // returned on success: "Google Pay" or "Razorpay"
    const val EXTRA_ERROR = "error"             // returned on failure

    // True only if a Razorpay TEST key (starting rzp_) is in local.properties. Then Razorpay is used instead of
    // Google Pay
    val razorpayReady: Boolean get() = BuildConfig.RAZORPAY_KEY.startsWith("rzp_")

    // Name shown on the checkout: "Google Pay" (or "Razorpay" if its key is set)
    val provider: String get() = if (razorpayReady) "Razorpay" else "Google Pay"

    // Makes the Intent that opens PaymentActivity, with the amount and the buyer's name, email and phone inside
    fun intent(context: Context, amount: Int, name: String, email: String, phone: String, description: String) =
        Intent(context, PaymentActivity::class.java).apply {
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_EMAIL, email)
            putExtra(EXTRA_PHONE, phone)
            putExtra(EXTRA_DESCRIPTION, description)
        }
}

// *** PAYMENT SCREEN ***
// A see-through Activity (screen) that opens the payment sheet and sends the result back to the cart.
// PaymentResultWithDataListener = Razorpay calls our onPaymentSuccess / onPaymentError
class PaymentActivity : ComponentActivity(), PaymentResultWithDataListener {

    companion object {
        // A number to recognise Google Pay's answer when it comes back in onActivityResult
        private const val GOOGLE_PAY_REQUEST = 991
    }

    // Runs when the payment screen opens
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the screen was only rotated, the payment sheet is already open, so don't open it twice
        if (savedInstanceState != null) return          // payment sheet is already open (e.g. screen rotated)
        // Razorpay if its key is set, otherwise Google Pay (the normal case)
        if (Payment.razorpayReady) openRazorpay() else openGooglePay()
    }

    // -----------------------------------------------------------------
    //  Google Pay (TEST environment)
    // -----------------------------------------------------------------

    // Builds the Google Pay request as JSON (text in {key: value} form):
    // what to pay with (cards), how much, in which currency, and the TEST gateway "example"
    private fun googlePayRequest(amount: Int): JSONObject {
        // Payment method: CARD (Visa, Mastercard, Amex, Discover)
        val card = JSONObject()
            .put("type", "CARD")
            .put("parameters", JSONObject()
                .put("allowedAuthMethods", JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS")))
                .put("allowedCardNetworks", JSONArray(listOf("VISA", "MASTERCARD", "AMEX", "DISCOVER"))))
            // Gateway "example" = Google's test gateway. Google sends back a fake token instead of real card
            // details
            .put("tokenizationSpecification", JSONObject()
                .put("type", "PAYMENT_GATEWAY")
                .put("parameters", JSONObject()
                    .put("gateway", "example")                       // Google's test gateway
                    .put("gatewayMerchantId", "exampleGatewayMerchantId")))
        // The full request: API version 2, the card method, shop name, and the total price in INR
        return JSONObject()
            .put("apiVersion", 2)
            .put("apiVersionMinor", 0)
            .put("allowedPaymentMethods", JSONArray().put(card))
            .put("merchantInfo", JSONObject().put("merchantName", "TryVerse (test)"))
            .put("transactionInfo", JSONObject()
                .put("totalPriceStatus", "FINAL")
                .put("totalPrice", "$amount.00")
                .put("currencyCode", "INR")
                .put("countryCode", "IN"))
    }

    // *** GOOGLE PAY OPENS HERE ***
    private fun openGooglePay() {
        try {
            // STEP 1: connect to Google Pay in its TEST environment (no real money)
            val client = Wallet.getPaymentsClient(
                this, Wallet.WalletOptions.Builder().setEnvironment(WalletConstants.ENVIRONMENT_TEST).build()
            )
            // STEP 2: turn our JSON into a Google Pay request with the cart amount
            val request = PaymentDataRequest.fromJson(googlePayRequest(intent.getIntExtra(Payment.EXTRA_AMOUNT, 0)).toString())
            // Shows the Google Pay sheet; the answer comes back in onActivityResult
            // STEP 3: show Google's payment sheet. The answer comes back in onActivityResult below
            AutoResolveHelper.resolveTask(client.loadPaymentData(request), this, GOOGLE_PAY_REQUEST)
        } catch (e: Exception) {
            // Google Pay could not even start: show why, then open our test checkout instead
            Log.e("GooglePay", "Google Pay could not start", e)
            Toast.makeText(this, "Google Pay could not start: ${e.message}", Toast.LENGTH_LONG).show()
            openTestCheckout()                                   // Google Pay can't start: use the test checkout
        }
    }

    // *** GOOGLE PAY'S ANSWER COMES BACK HERE ***
    @Deprecated("Google Pay's AutoResolveHelper still returns its result here")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Ignore answers that are not from Google Pay
        if (requestCode != GOOGLE_PAY_REQUEST) return
        when (resultCode) {
            // PAID: read the card description (e.g. Visa •••• 1111) and make a test payment id
            RESULT_OK -> {
                // e.g. "Visa •••• 1111" (test mode returns a test token, no money moves)
                val card = data?.let { GooglePaymentData.getFromIntent(it) }?.let {
                    JSONObject(it.toJson()).optJSONObject("paymentMethodData")?.optString("description")
                }.orEmpty()
                val id = "gpay_test_" + System.currentTimeMillis().toString(36) + if (card.isNotEmpty()) " ($card)" else ""
                finishPaid("Google Pay", id)
            }
            // The user closed the sheet: go back to the cart with "Payment cancelled"
            RESULT_CANCELED -> finishWith(RESULT_CANCELED, Payment.EXTRA_ERROR, "Payment cancelled.")
            // AutoResolveHelper.RESULT_ERROR: Google Pay isn't available here (e.g. error 412) -> test checkout
            // ERROR from Google (e.g. 412 = Play services too old): show the error code, then open the test
            // checkout
            else -> {
                // Show Google's error code so we know why it failed (e.g. 412 = Play services too old)
                val status = AutoResolveHelper.getStatusFromIntent(data)
                Log.e("GooglePay", "Google Pay error: code=${status?.statusCode} message=${status?.statusMessage}")
                Toast.makeText(this, "Google Pay error ${status?.statusCode}: ${status?.statusMessage ?: ""}", Toast.LENGTH_LONG).show()
                openTestCheckout()
            }
        }
    }

    // -----------------------------------------------------------------
    //  TryVerse test checkout (when Google Pay isn't available)
    // -----------------------------------------------------------------

    // *** TEST CHECKOUT (backup when Google Pay isn't available) ***
    // Draws our own test payment screen with Compose inside this Activity
    private fun openTestCheckout() {
        val amount = intent.getIntExtra(Payment.EXTRA_AMOUNT, 0)
        setContent {
            ZaraTheme {
                TestCheckout(
                    amount = amount,
                    onPaid = { method -> finishPaid(method, "test_" + System.currentTimeMillis().toString(36)) },
                    onFailed = { message -> finishWith(RESULT_CANCELED, Payment.EXTRA_ERROR, message) }
                )
            }
        }
    }

    // -----------------------------------------------------------------
    //  Razorpay (when a test key is set)
    // -----------------------------------------------------------------

    // Razorpay checkout (only when a Razorpay TEST key is set). Amount is sent in paise (rupees x 100)
    private fun openRazorpay() {
        Checkout.preload(applicationContext)
        val checkout = Checkout()
        checkout.setKeyID(BuildConfig.RAZORPAY_KEY)
        try {
            val options = JSONObject().apply {
                put("name", "TryVerse")
                put("description", intent.getStringExtra(Payment.EXTRA_DESCRIPTION) ?: "TryVerse order")
                put("currency", "INR")
                put("amount", intent.getIntExtra(Payment.EXTRA_AMOUNT, 0) * 100)       // in paise
                put("theme", JSONObject().put("color", "#6286D8"))
                put("prefill", JSONObject().apply {
                    put("name", intent.getStringExtra(Payment.EXTRA_NAME) ?: "")
                    put("email", intent.getStringExtra(Payment.EXTRA_EMAIL) ?: "")
                    put("contact", intent.getStringExtra(Payment.EXTRA_PHONE) ?: "")
                })
                put("retry", JSONObject().put("enabled", true).put("max_count", 3))
            }
            checkout.open(this, options)
        } catch (e: Exception) {
            finishWith(RESULT_CANCELED, Payment.EXTRA_ERROR, "Could not open the payment page.")
        }
    }

    // Razorpay says the payment worked
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        finishPaid("Razorpay", razorpayPaymentId ?: "")
    }

    // Razorpay says the payment failed or was cancelled
    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val message = if (code == Checkout.PAYMENT_CANCELED) "Payment cancelled." else "Payment failed. Please try again."
        finishWith(RESULT_CANCELED, Payment.EXTRA_ERROR, message)
    }

    // -----------------------------------------------------------------

    // Close this screen and tell the cart: PAID, with which method and the payment id
    private fun finishPaid(method: String, paymentId: String) {
        setResult(RESULT_OK, Intent().putExtra(Payment.EXTRA_PAYMENT_ID, paymentId).putExtra(Payment.EXTRA_METHOD, method))
        finish()
    }

    // Close this screen and send a result (e.g. cancelled + the error message) back to the cart
    private fun finishWith(result: Int, key: String, value: String) {
        setResult(result, Intent().putExtra(key, value))
        finish()
    }
}

/**
 * In-app TEST payment screen (no real money). UPI: success@test pays, failure@test fails.
 * Card: 4111 1111 1111 1111, any future MM/YY and any 3-digit CVV.
 */
// Test payment screen (Compose). @Composable = a function that draws part of the screen
@Composable
fun TestCheckout(amount: Int, onPaid: (String) -> Unit, onFailed: (String) -> Unit) {
    // remember + mutableStateOf = values the screen remembers. When they change, the screen redraws itself.
    // The boxes start filled with the test UPI id and test card
    var useUpi by remember { mutableStateOf(true) }
    var upi by remember { mutableStateOf("success@test") }
    var card by remember { mutableStateOf("4111 1111 1111 1111") }
    var expiry by remember { mutableStateOf("12/30") }
    var cvv by remember { mutableStateOf("123") }
    var error by remember { mutableStateOf<String?>(null) }
    var paying by remember { mutableStateOf(false) }

    // Pretend to contact the bank for a moment, then finish
    // When Pay is pressed: wait 1.5 s (pretend to contact the bank), then pass or fail
    LaunchedEffect(paying) {
        if (!paying) return@LaunchedEffect
        delay(1500)
        if (useUpi && upi.trim().lowercase() == "failure@test") onFailed("Payment failed (test UPI id failure@test).")
        else onPaid(if (useUpi) "UPI (test)" else "Card (test)")
    }

    // Checks the typed UPI id / card number, expiry (MM/YY) and CVV before "paying"
    fun pay() {
        val digits = card.filter { it.isDigit() }
        error = when {
            useUpi && !Regex("^[\\w.-]{2,}@[A-Za-z]{2,}$").matches(upi.trim()) -> "Enter a UPI id like name@bank"
            !useUpi && digits.length !in 15..16 -> "Enter a 16-digit card number"
            !useUpi && !Regex("^(0[1-9]|1[0-2])/[0-9]{2}$").matches(expiry) -> "Expiry must be MM/YY"
            !useUpi && cvv.length !in 3..4 -> "Enter the CVV"
            else -> null
        }
        if (error == null) paying = true
    }

    // Dark see-through background with a sheet at the bottom
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Paper).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("TryVerse Test Checkout", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
            Text("Test mode – no real money is charged", color = Muted, fontSize = 12.sp)
            Text("Pay ${rupees(amount)}", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Ink)
            // Two tabs: UPI or Card
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = useUpi, onClick = { useUpi = true; error = null }, label = { Text("UPI") },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp)) })
                FilterChip(selected = !useUpi, onClick = { useUpi = false; error = null }, label = { Text("Card") },
                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
            // UPI box, or card number + expiry + CVV boxes
            if (useUpi) {
                OutlinedTextField(upi, { upi = it.take(50) }, label = { Text("UPI id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Test: success@test pays, failure@test fails", color = Muted, fontSize = 11.sp)
            } else {
                OutlinedTextField(
                    card, { card = it.filter { c -> c.isDigit() || c == ' ' }.take(19) }, label = { Text("Card number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(expiry, { expiry = it.take(5) }, label = { Text("MM/YY") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        cvv, { cvv = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("CVV") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f)
                    )
                }
                Text("Test card: 4111 1111 1111 1111, any future date, any CVV", color = Muted, fontSize = 11.sp)
            }
            error?.let { Text(it, color = Rose, fontSize = 13.sp) }
            // Pay button (shows a spinner while "processing")
            Button(onClick = { if (!paying) pay() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                if (paying) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = OnInk, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Processing...")
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pay ${rupees(amount)}")
                }
            }
            Text(
                "Cancel", color = Muted, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = !paying) { onFailed("Payment cancelled.") }.padding(8.dp)
            )
        }
    }
}
