package com.example.styletryon

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================
//  File handling: orders.csv
//  One order per line (one file for the app, each line belongs to one user):
//  id,email,placedAt,items,total,state,address,deliverOn,payment,paymentId,requests
//  items    = productId:size:qty;productId:size:qty   (no commas, so the CSV stays valid)
//  requests = returns / exchanges: line:type:newSize:reason:time;...   ("-" = none)
// =====================================================================

/** One product line inside an order. */
data class OrderLine(val product: Product, val size: String, val qty: Int)

/** A return or exchange asked for one item ([line] = its position in the order). */
data class ReturnRequest(val line: Int, val type: String, val newSize: String, val reason: String, val time: Long) {
    /** Progress for the demo: requested -> picked up (after 1 hour) -> done (after 2 days). */
    fun progress(order: Order): String {
        val hours = (System.currentTimeMillis() - time) / 3_600_000L
        return when {
            hours < 1 -> "$type requested ($reason)"
            hours < 48 -> "Picked up • $type in progress"
            type == "Exchange" -> "Exchanged: size $newSize delivered"
            else -> "Refund of ${rupees(order.refundFor(line))} processed"
        }
    }
}

data class Order(
    val id: String,
    val email: String,
    val placedAt: Long,          // time the order was placed (milliseconds)
    val lines: List<OrderLine>,
    val total: Int,              // amount paid, after any coupon
    val state: String,           // "Placed" or "Cancelled"
    val address: String,         // "Label: address" chosen at checkout
    val deliverOn: Long,         // delivery date chosen at checkout (milliseconds)
    val payment: String,         // "Razorpay" or "Cash on Delivery"
    val paymentId: String,       // Razorpay payment id ("-" for cash on delivery)
    val requests: List<ReturnRequest> = emptyList()
) {
    /** Order status for the demo: Processing -> Shipped (after 1 hour) -> Delivered (on the delivery date). */
    val status: String
        get() {
            val now = System.currentTimeMillis()
            return when {
                state == "Cancelled" -> "Cancelled"
                state == "Delivered" -> "Delivered"
                now >= deliverOn -> "Delivered"
                now - placedAt > 60 * 60 * 1000L -> "Shipped"
                else -> "Processing"
            }
        }

    val date: String get() = formatDate(placedAt)
    val itemCount: Int get() = lines.sumOf { it.qty }
    val summary: String get() = lines.joinToString(" + ") { it.product.name }
    val subtotal: Int get() = lines.sumOf { it.product.price * it.qty }

    /** Returns and exchanges are allowed for 7 days after delivery. */
    val canReturn: Boolean
        get() = status == "Delivered" && System.currentTimeMillis() - deliverOn <= 7 * 24 * 3_600_000L

    fun requestFor(line: Int): ReturnRequest? = requests.firstOrNull { it.line == line }

    /** Refund for one item: its share of what was paid, so a coupon discount is shared fairly. */
    fun refundFor(line: Int): Int {
        val l = lines.getOrNull(line) ?: return 0
        return if (subtotal == 0) 0 else l.product.price * l.qty * total / subtotal
    }
}

fun formatDate(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun ordersFile(context: Context) = File(context.filesDir, "orders.csv")

/** Removes characters that would break the CSV line. */
private fun clean(text: String) = text.replace(", ", " · ").replace(",", " ").replace(";", " ").replace(":", " ").replace("\n", " ")

private fun toLine(o: Order): String {
    val items = o.lines.joinToString(";") { "${it.product.id}:${clean(it.size)}:${it.qty}" }
    val requests = if (o.requests.isEmpty()) "-"
    else o.requests.joinToString(";") { "${it.line}:${it.type}:${clean(it.newSize)}:${clean(it.reason)}:${it.time}" }
    return listOf(o.id, clean(o.email), o.placedAt, items, o.total, o.state, clean(o.address), o.deliverOn, clean(o.payment), clean(o.paymentId), requests)
        .joinToString(",")
}

private fun fromLine(line: String): Order? {
    val p = line.split(",")
    if (p.size != 10 && p.size != 11) return null      // skips lines in the old (version 2) format
    val lines = p[3].split(";").mapNotNull { item ->
        val f = item.split(":")
        val product = f.getOrNull(0)?.toIntOrNull()?.let { productById(it) } ?: return@mapNotNull null
        OrderLine(product, f.getOrElse(1) { "M" }, f.getOrNull(2)?.toIntOrNull() ?: 1)
    }
    if (lines.isEmpty()) return null
    val requests = p.getOrElse(10) { "-" }.split(";").mapNotNull { r ->
        val f = r.split(":")
        if (f.size != 5) null
        else ReturnRequest(f[0].toIntOrNull() ?: return@mapNotNull null, f[1], f[2], f[3], f[4].toLongOrNull() ?: 0L)
    }
    return Order(
        p[0], p[1], p[2].toLongOrNull() ?: 0L, lines, p[4].toIntOrNull() ?: 0, p[5], p[6],
        p[7].toLongOrNull() ?: 0L, p[8], p[9], requests
    )
}

/** Adds one order to orders.csv and returns it. */
fun saveOrder(
    context: Context, email: String, items: List<CartItem>, total: Int,
    address: String, deliverOn: Long, payment: String, paymentId: String = "-"
): Order {
    val now = System.currentTimeMillis()
    val order = Order(
        id = "TV" + (now % 1_000_000),
        email = email.lowercase(),
        placedAt = now,
        lines = items.map { OrderLine(it.product, it.size, it.qty) },
        total = total,
        state = "Placed",
        address = address,
        deliverOn = deliverOn,
        payment = payment,
        paymentId = paymentId.ifBlank { "-" }
    )
    ordersFile(context).appendText(toLine(order) + "\n")
    return order
}

private fun readAll(context: Context): List<Order> {
    val file = ordersFile(context)
    if (!file.exists()) return emptyList()
    return file.readLines().mapNotNull { fromLine(it) }
}

/** This user's orders, newest first. */
fun loadOrders(context: Context, email: String): List<Order> =
    readAll(context).filter { it.email == email.lowercase() }.reversed()

/** Rewrites orders.csv with one order changed. */
private fun updateOrder(context: Context, id: String, change: (Order) -> Order) {
    val all = readAll(context).map { if (it.id == id) change(it) else it }
    ordersFile(context).writeText(all.joinToString("") { toLine(it) + "\n" })
}

fun cancelOrder(context: Context, id: String) = updateOrder(context, id) { it.copy(state = "Cancelled") }

/** Saves a return / exchange request for one item of the order. */
fun requestReturn(context: Context, id: String, request: ReturnRequest) =
    updateOrder(context, id) { it.copy(requests = it.requests + request) }

/** Demo only (debug builds): pretend the order has arrived, so returns can be shown. */
fun markDelivered(context: Context, id: String) =
    updateOrder(context, id) { it.copy(state = "Delivered", deliverOn = System.currentTimeMillis()) }

/** Deletes only this user's orders. */
fun clearOrders(context: Context, email: String) {
    val others = readAll(context).filter { it.email != email.lowercase() }
    ordersFile(context).writeText(others.joinToString("") { toLine(it) + "\n" })
}
