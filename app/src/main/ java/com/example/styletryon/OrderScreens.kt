package com.example.styletryon

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
//  ORDER DETAILS (opened by tapping an order in My Profile)
// =====================================================================

/** [suggestSize] = the user's size from the body scan (shown when exchanging a size). */
@Composable
fun OrderDetailsScreen(
    order: Order, onBack: () -> Unit, onProduct: (Product) -> Unit,
    onBuyAgain: (Order) -> Unit = {}, suggestSize: (Product) -> String? = { null }
) {
    val context = LocalContext.current
    var current by remember(order.id) { mutableStateOf(order) }
    var confirmCancel by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf<Int?>(null) }     // item being returned / exchanged
    val status = current.status
    // Saved as "Label: address" (orders from before Maps only have the label)
    val addressLabel = current.address.substringBefore(":")
    val addressText = current.address.substringAfter(": ", defaultAddresses.firstOrNull { it.label == current.address }?.line ?: "")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Order Details", onBack)

        // Order number, date and status
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Order #${current.id}", fontSize = 20.sp, fontFamily = Display, color = Ink)
                    Text("Placed on ${current.date}  ·  ${current.itemCount} item(s)", color = Muted, fontSize = 12.sp)
                }
                StatusChip(status)
            }
            Spacer(Modifier.height(12.dp))
            OrderTimeline(current)
        }

        // Items
        Section {
            Eyebrow("Items")
            current.lines.forEachIndexed { i, line ->
                Row(
                    Modifier.fillMaxWidth().clickable { onProduct(line.product) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductImage(line.product, Modifier.size(60.dp, 80.dp), padding = 5)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.product.name.uppercase(), color = Ink, fontSize = 12.sp, letterSpacing = 0.8.sp)
                        Text("${line.product.colorName}  ·  Size ${line.size}  ·  Qty ${line.qty}", color = Muted, fontSize = 12.sp)
                        // Return / exchange (within 7 days of delivery, once per item)
                        val request = current.requestFor(i)
                        when {
                            request != null -> Text(request.progress(current).uppercase(), color = Ink, fontSize = 11.sp, letterSpacing = 1.sp)
                            current.canReturn -> Text(
                                "RETURN / EXCHANGE", color = Ink, fontSize = 11.sp, letterSpacing = 1.sp, textDecoration = TextDecoration.Underline,
                                modifier = Modifier.padding(top = 4.dp).clickable { returning = i }
                            )
                        }
                    }
                    Text(rupees(line.product.price * line.qty), fontSize = 13.sp, color = Ink)
                }
            }
        }

        // Delivery
        Section {
            Eyebrow("Delivery")
            Spacer(Modifier.height(4.dp))
            InfoRow(Icons.Default.LocationOn, addressLabel, addressText)
            InfoRow(
                Icons.Default.LocalShipping,
                if (status == "Delivered") "Delivered" else "Delivery date",
                formatDate(current.deliverOn)
            )
            if (current.canReturn) {
                InfoRow(Icons.Default.AssignmentReturn, "Easy returns", "Return or exchange any item within 7 days of delivery")
            }
            // Demo helper for the viva (debug builds only): orders normally arrive on the chosen date
            if (BuildConfig.DEBUG && (status == "Processing" || status == "Shipped")) {
                TextButton(onClick = {
                    markDelivered(context, current.id)
                    current = current.copy(state = "Delivered", deliverOn = System.currentTimeMillis())
                }) { Text("DEMO: MARK AS DELIVERED", fontSize = 11.sp, letterSpacing = 1.sp) }
            }
        }

        // Payment and price
        Section {
            Eyebrow("Payment")
            Spacer(Modifier.height(4.dp))
            InfoRow(
                if (current.payment == "Razorpay") Icons.Default.CreditCard else Icons.Default.Payments,
                current.payment,
                if (current.paymentId != "-") "Payment ID: ${current.paymentId}" else "Pay when the order arrives"
            )
            Hairline(Modifier.padding(vertical = 8.dp))
            PriceRow("Items total", rupees(current.subtotal))
            if (current.subtotal > current.total) PriceRow("Coupon discount", "– " + rupees(current.subtotal - current.total))
            PriceRow("Delivery", "FREE")
            PriceRow(if (current.payment == "Razorpay") "Total paid" else "Total to pay", rupees(current.total), bold = true)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == "Processing") {
                ZaraOutlinedButton("Cancel order", Modifier.weight(1f)) { confirmCancel = true }
            }
            ZaraButton("Buy again", Modifier.weight(1f)) { onBuyAgain(current) }
        }
        Spacer(Modifier.height(16.dp))
    }

    returning?.let { i ->
        ReturnDialog(
            order = current, index = i, suggested = suggestSize(current.lines[i].product),
            onDismiss = { returning = null },
            onSubmit = { request ->
                requestReturn(context, current.id, request)        // saved in orders.csv
                current = current.copy(requests = current.requests + request)
                returning = null
                Toast.makeText(context, "${request.type} requested. Free pickup in 1–2 days.", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this order?") },
            text = {
                Text(
                    if (current.payment == "Razorpay") "${rupees(current.total)} will be refunded to your original payment method in 5–7 days."
                    else "You won't be charged anything."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelOrder(context, current.id)           // updates orders.csv
                    current = current.copy(state = "Cancelled")
                    confirmCancel = false
                    Toast.makeText(context, "Order cancelled", Toast.LENGTH_SHORT).show()
                }) { Text("YES, CANCEL", color = Rose, letterSpacing = 1.sp) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("KEEP ORDER", letterSpacing = 1.sp) } }
        )
    }
}

/**
 * Return or exchange one item: pick a reason, then either a new size (sizes in stock only,
 * ★ = the size suggested by the body scan) or a refund.
 */
@Composable
fun ReturnDialog(order: Order, index: Int, suggested: String?, onDismiss: () -> Unit, onSubmit: (ReturnRequest) -> Unit) {
    val line = order.lines[index]
    val reasons = listOf("Too small", "Too big", "Didn't like the colour", "Damaged or defective")
    val options = line.product.sizes.filter { it != line.size }                 // other sizes
    val canExchange = options.any { line.product.hasSize(it) }
    var reason by remember { mutableStateOf(reasons[0]) }
    var exchange by remember { mutableStateOf(canExchange) }
    var newSize by remember {
        mutableStateOf(suggested?.takeIf { it in options && line.product.hasSize(it) } ?: options.firstOrNull { line.product.hasSize(it) } ?: "")
    }
    val doExchange = exchange && canExchange

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return / Exchange") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(line.product, Modifier.size(48.dp, 56.dp), padding = 4)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(line.product.name.uppercase(), color = Ink, fontSize = 12.sp, letterSpacing = 0.8.sp)
                        Text("Size ${line.size}  ·  Qty ${line.qty}", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Eyebrow("Reason")
                reasons.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r, fontSize = 14.sp)
                    }
                }
                Eyebrow("What would you like?", Modifier.padding(top = 8.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = doExchange, onClick = { exchange = true }, enabled = canExchange, label = { Text("Exchange size") })
                    FilterChip(selected = !doExchange, onClick = { exchange = false }, label = { Text("Return for refund") })
                }
                if (doExchange) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { s ->
                            SizeBox(if (s == suggested) "$s ★" else s, selected = newSize == s, enabled = line.product.hasSize(s)) { newSize = s }
                        }
                    }
                    if (suggested != null) Text("★ = your size from the body scan", color = Muted, fontSize = 11.sp)
                    Text("Free pickup. Your new size is delivered when we collect this one.", color = Muted, fontSize = 12.sp)
                } else {
                    Text("Refund: ${rupees(order.refundFor(index))}", fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        if (order.payment == "Razorpay") "Back to your original payment method 5–7 days after pickup."
                        else "To your bank account after pickup (we'll ask for the details).",
                        color = Muted, fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            ZaraButton(if (doExchange) "Request exchange" else "Request return") {
                if (doExchange && newSize.isEmpty()) return@ZaraButton
                onSubmit(
                    ReturnRequest(index, if (doExchange) "Exchange" else "Return", if (doExchange) newSize else "-", reason, System.currentTimeMillis())
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** One part of the screen in a rounded glass card. */
@Composable
fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).glass(CardShape).padding(16.dp), content = content)
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (detail.isNotEmpty()) Text(detail, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun PriceRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = if (bold) Ink else Muted, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Ordered -> Shipped -> Delivered, with ticks for the steps already done. */
@Composable
fun OrderTimeline(order: Order) {
    if (order.status == "Cancelled") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cancel, contentDescription = null, tint = Rose)
            Spacer(Modifier.width(8.dp))
            Text("This order was cancelled", color = Rose)
        }
        return
    }
    val done = when (order.status) {
        "Delivered" -> 3
        "Shipped" -> 2
        else -> 1
    }
    val steps = listOf(
        "Order placed" to order.date,
        "Shipped" to if (done >= 2) "On the way" else "Usually within a day",
        "Delivered" to (if (done >= 3) "Delivered on " else "Expected on ") + formatDate(order.deliverOn)
    )
    steps.forEachIndexed { i, (title, detail) ->
        Row {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (i < done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null, tint = if (i < done) Primary else Disabled, modifier = Modifier.size(20.dp)
                )
                if (i < steps.lastIndex) {
                    Box(Modifier.width(2.dp).height(24.dp).background(if (i + 1 < done) Primary else Line))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title.uppercase(), color = if (i < done) Ink else Muted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
                Text(detail, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
