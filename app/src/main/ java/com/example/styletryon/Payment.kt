package com.example.styletryon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

// =====================================================================
//  ONLINE PAYMENT (Razorpay Standard Checkout, TEST MODE)
//  The cart starts this small screen, which opens Razorpay's payment page
//  (UPI / card / netbanking / wallets). Razorpay tells this screen whether the
//  payment worked, and the screen passes the answer back to the cart.
//
//  Only the public TEST key id (rzp_test_...) is in the app. It is read from
//  local.properties (RAZORPAY_KEY=rzp_test_...), so it is never put on GitHub.
//  The secret key is never in the app. In test mode no real money is taken:
//  test card 4111 1111 1111 1111 (any future date, any CVV) or UPI id success@razorpay.
// =====================================================================

object Payment {
    const val EXTRA_AMOUNT = "amount"          // rupees
    const val EXTRA_NAME = "name"
    const val EXTRA_EMAIL = "email"
    const val EXTRA_PHONE = "phone"
    const val EXTRA_DESCRIPTION = "description"
    const val EXTRA_PAYMENT_ID = "payment_id"   // returned on success
    const val EXTRA_ERROR = "error"             // returned on failure

    /** False until a Razorpay test key is added to local.properties. */
    val isSetUp: Boolean get() = BuildConfig.RAZORPAY_KEY.startsWith("rzp_")

    fun intent(context: Context, amount: Int, name: String, email: String, phone: String, description: String) =
        Intent(context, PaymentActivity::class.java).apply {
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_EMAIL, email)
            putExtra(EXTRA_PHONE, phone)
            putExtra(EXTRA_DESCRIPTION, description)
        }
}

/** See-through screen that only shows Razorpay's checkout and returns the result. */
class PaymentActivity : Activity(), PaymentResultWithDataListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return          // checkout is already open (e.g. screen rotated)
        if (!Payment.isSetUp) {
            finishWith(RESULT_CANCELED, Payment.EXTRA_ERROR, "Online payment is not set up. Add RAZORPAY_KEY to local.properties.")
            return
        }
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

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        finishWith(RESULT_OK, Payment.EXTRA_PAYMENT_ID, razorpayPaymentId ?: "")
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val message = if (code == Checkout.PAYMENT_CANCELED) "Payment cancelled." else "Payment failed. Please try again."
        finishWith(RESULT_CANCELED, Payment.EXTRA_ERROR, message)
    }

    private fun finishWith(result: Int, key: String, value: String) {
        setResult(result, Intent().putExtra(key, value))
        finish()
    }
}
