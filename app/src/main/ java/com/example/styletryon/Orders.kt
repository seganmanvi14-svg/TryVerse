// =====================================================================
// Orders.kt = SAVING AND READING ORDERS (file handling).
// Every order is one line of text in the file orders.csv inside the app's private storage.
// Each line has the user's email, so every user only sees their own orders.
// Flow: Cart -> saveOrder() writes a line -> Profile/Orders -> loadOrders() reads the lines back.
// =====================================================================
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

// One product inside an order: which product, which size, how many
data class OrderLine(val product: Product, val size: String, val qty: Int)

// A return or exchange the user asked for one item of the order ([line] = the item's position in the order)
data class ReturnRequest(val line: Int, val type: String, val newSize: String, val reason: String, val time: Long) {
    // Text that shows how far the return / exchange has got. For the demo it moves with time: requested ->
    // picked up (after 1 hour) -> done (after 2 days)
    fun progress(order: Order): String {
        // How many hours since the request was made (3,600,000 milliseconds = 1 hour)
        val hours = (System.currentTimeMillis() - time) / 3_600_000L
        return when {
            hours < 1 -> "$type requested ($reason)"
            hours < 48 -> "Picked up • $type in progress"
            type == "Exchange" -> "Exchanged: size $newSize delivered"
            else -> "Refund of ${rupees(order.refundFor(line))} processed"
        }
    }
}

// One whole order. payment can be Google Pay, UPI (test), Card (test), Razorpay or Cash on Delivery. 'data
// class' = a class that only holds data (Kotlin makes copy(), equals() etc. for us)
data class Order(
    val id: String,
    val email: String,
    val placedAt: Long,          // time the order was placed (milliseconds)
    val lines: List<OrderLine>,
    val total: Int,              // amount paid, after any coupon
    val state: String,           // "Placed" or "Cancelled"
    val address: String,         // "Label: address" chosen at checkout
    val deliverOn: Long,         // delivery date chosen at checkout (milliseconds)
    val payment: String,         // "Google Pay", "Razorpay" or "Cash on Delivery"
    val paymentId: String,       // payment id from Google Pay / Razorpay ("-" for cash on delivery)
    val requests: List<ReturnRequest> = emptyList()
) {
    // *** ORDER TRACKING HERE ***
    // Processing -> Shipped (after 1 hour) -> Delivered (on the delivery date). Worked out from the time, not
    // saved
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

    // Small helpers: order date as text, number of items, names of the products, price before discounts
    val date: String get() = formatDate(placedAt)
    val itemCount: Int get() = lines.sumOf { it.qty }
    val summary: String get() = lines.joinToString(" + ") { it.product.name }
    val subtotal: Int get() = lines.sumOf { it.product.price * it.qty }

    // Returns / exchanges are allowed only if the order is delivered AND it is less than 7 days since delivery
    val canReturn: Boolean
        get() = status == "Delivered" && System.currentTimeMillis() - deliverOn <= 7 * 24 * 3_600_000L

    // Finds the return / exchange request for one item (null = none yet)
    fun requestFor(line: Int): ReturnRequest? = requests.firstOrNull { it.line == line }

    // Money given back for one item. If a coupon was used, the discount is shared fairly between the items
    fun refundFor(line: Int): Int {
        val l = lines.getOrNull(line) ?: return 0
        return if (subtotal == 0) 0 else l.product.price * l.qty * total / subtotal
    }
}

// Turns a time in milliseconds into text like 28 Oct 2026
fun formatDate(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

// *** THE ORDERS FILE: orders.csv in the app's private folder (other apps can't read it) ***
private fun ordersFile(context: Context) = File(context.filesDir, "orders.csv")

// Removes commas, semicolons, colons and new lines from text, because those characters separate the parts of a
// CSV line
private fun clean(text: String) = text.replace(", ", " · ").replace(",", " ").replace(";", " ").replace(":", " ").replace("\n", " ")

// Turns one Order into one line of text (CSV = comma-separated values)
private fun toLine(o: Order): String {
    // Products are written as productId:size:qty, separated by ;
    val items = o.lines.joinToString(";") { "${it.product.id}:${clean(it.size)}:${it.qty}" }
    // Returns / exchanges are written as line:type:newSize:reason:time, or - if there are none
    val requests = if (o.requests.isEmpty()) "-"
    else o.requests.joinToString(";") { "${it.line}:${it.type}:${clean(it.newSize)}:${clean(it.reason)}:${it.time}" }
    return listOf(o.id, clean(o.email), o.placedAt, items, o.total, o.state, clean(o.address), o.deliverOn, clean(o.payment), clean(o.paymentId), requests)
        .joinToString(",")
}

// Turns one line of the file back into an Order (null if the line is broken or from an old version)
private fun fromLine(line: String): Order? {
    // Split the line at the commas into its parts
    val p = line.split(",")
    if (p.size != 10 && p.size != 11) return null      // skips lines in the old (version 2) format
    // Rebuild each product of the order from productId:size:qty
    val lines = p[3].split(";").mapNotNull { item ->
        val f = item.split(":")
        val product = f.getOrNull(0)?.toIntOrNull()?.let { productById(it) } ?: return@mapNotNull null
        OrderLine(product, f.getOrElse(1) { "M" }, f.getOrNull(2)?.toIntOrNull() ?: 1)
    }
    if (lines.isEmpty()) return null
    // Rebuild the returns / exchanges (part 10 of the line)
    val requests = p.getOrElse(10) { "-" }.split(";").mapNotNull { r ->
        val f = r.split(":")
        if (f.size != 5) null
        else ReturnRequest(f[0].toIntOrNull() ?: return@mapNotNull null, f[1], f[2], f[3], f[4].toLongOrNull() ?: 0L)
    }
    // Make the Order object from the parts
    return Order(
        p[0], p[1], p[2].toLongOrNull() ?: 0L, lines, p[4].toIntOrNull() ?: 0, p[5], p[6],
        p[7].toLongOrNull() ?: 0L, p[8], p[9], requests
    )
}

// *** ORDER IS SAVED HERE ***
// Called after checkout (after the payment worked, or for Cash on Delivery). Adds one line to orders.csv
fun saveOrder(
    context: Context, email: String, items: List<CartItem>, total: Int,
    address: String, deliverOn: Long, payment: String, paymentId: String = "-"
): Order {
    // Current time in milliseconds
    val now = System.currentTimeMillis()
    val order = Order(
        // Order number like TV311627 (made from the time)
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
    // appendText = add the new line at the END of the file (old orders stay)
    ordersFile(context).appendText(toLine(order) + "\n")
    return order
}

// Reads every line of orders.csv and turns them into Orders (all users)
private fun readAll(context: Context): List<Order> {
    val file = ordersFile(context)
    if (!file.exists()) return emptyList()
    return file.readLines().mapNotNull { fromLine(it) }
}

// *** ORDERS PER USER: only the orders with this user's email, newest first ***
fun loadOrders(context: Context, email: String): List<Order> =
    readAll(context).filter { it.email == email.lowercase() }.reversed()

// Changes one order: read all, change the matching one, write the whole file again
private fun updateOrder(context: Context, id: String, change: (Order) -> Order) {
    val all = readAll(context).map { if (it.id == id) change(it) else it }
    ordersFile(context).writeText(all.joinToString("") { toLine(it) + "\n" })
}

// Cancel = mark the order's state as Cancelled
fun cancelOrder(context: Context, id: String) = updateOrder(context, id) { it.copy(state = "Cancelled") }

// Return / exchange = add the request to that order
fun requestReturn(context: Context, id: String, request: ReturnRequest) =
    updateOrder(context, id) { it.copy(requests = it.requests + request) }

// Demo button: pretends the order has arrived now, so we can show returns in the viva
fun markDelivered(context: Context, id: String) =
    updateOrder(context, id) { it.copy(state = "Delivered", deliverOn = System.currentTimeMillis()) }

// Deletes only this user's orders, other users' orders stay
fun clearOrders(context: Context, email: String) {
    val others = readAll(context).filter { it.email != email.lowercase() }
    ordersFile(context).writeText(others.joinToString("") { toLine(it) + "\n" })
}
