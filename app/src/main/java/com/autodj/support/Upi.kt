package com.autodj.support

import android.net.Uri

/** Supporting FireTheDJ over UPI: who gets paid, the smallest amount, and the payment link. */
object Upi {
    const val ID = "mochi.vardhan@federal"
    const val NAME = "Surya Vardhan"
    const val MIN_RUPEES = 50
    val AMOUNTS = listOf(50, 100, 200, 500)

    /**
     * A UPI payment link (upi://pay) for [rupees]: any UPI app opens it with the payee, amount and
     * note filled in. `mam` asks apps to hold the minimum too, though the app checks it itself.
     */
    fun link(rupees: Int): Uri = Uri.Builder()
        .scheme("upi").authority("pay")
        .appendQueryParameter("pa", ID)
        .appendQueryParameter("pn", NAME)
        .appendQueryParameter("am", "$rupees.00")
        .appendQueryParameter("mam", "$MIN_RUPEES.00")
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", "Support FireTheDJ")
        .build()
}
