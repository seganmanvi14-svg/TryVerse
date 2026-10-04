package com.example.styletryon

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

val addresses = listOf(
    "Home" to "A-203, MPSTME Hostel, SVKM's NMIMS, Navi Mumbai - 400710",
    "College" to "SVKM's NMIMS, Vile Parle West, Mumbai - 400056"
)
val payments = listOf("UPI (GPay / PhonePe / Paytm)", "Credit / Debit Card", "Cash on Delivery")



@Composable
fun CartScreen(cart: MutableList<CartItem>, userName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var coupon by remember { mutableStateOf("") }
    var discountPercent by remember { mutableIntStateOf(0) }
    var showCheckout by remember { mutableStateOf(false) }
    val subtotal = cart.filter { it.checked }.sumOf { it.product.price * it.qty }
    val total = subtotal - subtotal * discountPercent / 100

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            TopBar("My Cart (${cart.size})", onBack)
            if (cart.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Primary, modifier = Modifier.size(60.dp))
                    Text("Your cart is empty", fontWeight = FontWeight.Bold)
                    Text("Try something on and add it here.", color = Muted)
                }
            } else {

                cart.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.checked, onCheckedChange = { cart[i] = item.copy(checked = it) })
                        ProductImage(item.product, Modifier.size(64.dp, 76.dp), padding = 6)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.product.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
                            Text("${item.product.colorName} | ${item.size}", fontSize = 12.sp, color = Muted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rupees(item.product.price), fontWeight = FontWeight.Bold, color = Ink)
                                Spacer(Modifier.weight(1f))
                                QuantityStepper(item.qty, onChange = { cart[i] = item.copy(qty = it) })
                            }
                        }
                        IconButton(onClick = { cart.removeAt(i) }) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Muted) }
                    }
                    HorizontalDivider(color = Color(0xFFECECF4))
                }


                Spacer(Modifier.height(14.dp))
                Text("Apply Coupon", fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = coupon, onValueChange = { coupon = it.uppercase() },
                        placeholder = { Text("Enter code (try TRYVERSE10)") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                    )
                    TextButton(onClick = {
                        discountPercent = if (coupon.trim() == "TRYVERSE10") 10 else 0
                        Toast.makeText(context, if (discountPercent > 0) "Coupon applied: 10% off" else "Invalid coupon", Toast.LENGTH_SHORT).show()
                    }) { Text("Apply") }
                }
                if (discountPercent > 0) Text("You save ${rupees(subtotal - total)}", color = Color(0xFF2E8B57), fontSize = 13.sp)
            }
        }

        if (cart.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                Text("Total", color = Muted, fontSize = 12.sp)
                Text(rupees(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Proceed to Buy", Modifier.fillMaxWidth(), Icons.Default.ArrowForward) {
                    if (cart.none { it.checked }) Toast.makeText(context, "Tick at least one item", Toast.LENGTH_SHORT).show()
                    else showCheckout = true
                }
            }
        }
    }

    if (showCheckout) {
        CheckoutDialog(
            userName = userName,
            total = total,
            onDismiss = { showCheckout = false },
            onPlace = { payment ->
                val chosen = cart.filter { it.checked }
                saveOrder(context, chosen, total)          // written to orders.csv
                cart.removeAll(chosen)
                showCheckout = false
                Toast.makeText(context, "Order placed! Paying by $payment", Toast.LENGTH_LONG).show()
            }
        )
    }
}


@Composable
fun CheckoutDialog(userName: String, total: Int, onDismiss: () -> Unit, onPlace: (String) -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableIntStateOf(0) }
    var payment by remember { mutableStateOf(payments[0]) }
    var date by remember { mutableStateOf("Choose date") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checkout • ${rupees(total)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Deliver to $userName", fontWeight = FontWeight.SemiBold)
                addresses.forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().clickable { address = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = address == i, onClick = { address = i })
                        Column {
                            Text(a.first, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(a.second, fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Delivery date", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = {
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_MONTH, 2)                 // earliest delivery: in 2 days
                    DatePickerDialog(context, { _, y, m, d -> date = "$d/${m + 1}/$y" },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
                    ).apply { datePicker.minDate = c.timeInMillis }.show()
                }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(date)
                }
                Spacer(Modifier.height(8.dp))
                Text("Payment method", fontWeight = FontWeight.SemiBold)
                payments.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { payment = option }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = payment == option, onClick = { payment = option })
                        Text(option, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (date == "Choose date") Toast.makeText(context, "Please choose a delivery date", Toast.LENGTH_SHORT).show()
                else onPlace(payment)
            }) { Text("Place Order") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

