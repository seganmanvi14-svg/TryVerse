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
                // Items with check boxes
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

                // Coupon
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

        // Bottom bar
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

// ---------------------------------------------------------------------
//  Profile
// ---------------------------------------------------------------------

@Composable
fun ProfileScreen(
    db: UserDatabase,
    user: User?,
    wishlistCount: Int,
    onSaved: () -> Unit,
    onUserChanged: (User) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var orders by remember { mutableStateOf(loadOrders(context)) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }       // small info pop-ups
    var orderTab by remember { mutableStateOf("All") }
    val name = user?.name ?: "Guest"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Title + settings menu
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit profile") }, onClick = { menuOpen = false; editing = true })
                    DropdownMenuItem(text = { Text("Clear order history") }, onClick = {
                        menuOpen = false
                        clearOrders(context)
                        orders = emptyList()
                        Toast.makeText(context, "Order history cleared", Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text("About TryVerse") }, onClick = {
                        menuOpen = false
                        info = "TryVerse 2.0\nTry clothes on live with your camera, or get an AI photo of you wearing them.\n\n" +
                                "Garment and model images: OOTDiffusion examples (CC BY-NC-SA 4.0), used for a non-commercial college project."
                    })
                    DropdownMenuItem(text = { Text("Log out") }, onClick = { menuOpen = false; onLogout() })
                }
            }
        }

        // Profile card
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Text(
                    name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                Text(user?.email ?: "", color = Muted, fontSize = 13.sp)
                Text(user?.phone ?: "", color = Muted, fontSize = 13.sp)
            }
            Button(
                onClick = { editing = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight, contentColor = Primary),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) { Text("Edit", fontSize = 13.sp) }
        }

        // Tiles
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileTile(Icons.Default.Inventory2, "My Orders", "${orders.size}", Modifier.weight(1f)) { orderTab = "All" }
            ProfileTile(Icons.Default.FavoriteBorder, "Saved Items", "$wishlistCount", Modifier.weight(1f), onSaved)
            ProfileTile(Icons.Default.LocationOn, "Addresses", "${addresses.size}", Modifier.weight(1f)) {
                info = addresses.joinToString("\n\n") { "${it.first}\n${it.second}" }
            }
            ProfileTile(Icons.Default.CreditCard, "Payment", "${payments.size}", Modifier.weight(1f)) {
                info = payments.joinToString("\n")
            }
        }

        // My Orders (read from orders.csv)
        Spacer(Modifier.height(18.dp))
        Text("My Orders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Processing", "Shipped", "Delivered").forEach {
                FilterChip(selected = orderTab == it, onClick = { orderTab = it }, label = { Text(it) })
            }
        }
        val shown = orders.filter { orderTab == "All" || it.status == orderTab }
        if (shown.isEmpty()) Text("No orders here yet.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        shown.forEach { order ->
            val product = catalog.firstOrNull { it.id == order.productId } ?: catalog.first()
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(product, Modifier.size(56.dp, 66.dp), padding = 5)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Order #${order.id}", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(order.date, color = Muted, fontSize = 12.sp)
                        Text(order.items, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "● ${order.status}", color = Color(0xFF2E8B57), fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x1A2E8B57)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(rupees(order.total), fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }
    }

    // Edit profile dialog (saved in SQLite)
    if (editing && user != null) {
        var newName by remember { mutableStateOf(user.name) }
        var newPhone by remember { mutableStateOf(user.phone) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it.take(40) }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(newPhone, { newPhone = it.take(16) }, label = { Text("Phone") }, singleLine = true)
                    Text(user.email, color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!Validate.name(newName) || !Validate.phone(newPhone.trim())) {
                        Toast.makeText(context, "Enter a valid name and phone number", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    db.updateUser(user.email, newName.trim(), newPhone.trim())
                    onUserChanged(User(newName.trim(), user.email, newPhone.trim()))
                    editing = false
                    Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }

    if (info != null) {
        AlertDialog(
            onDismissRequest = { info = null },
            text = { Text(info!!) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("OK") } }
        )
    }
}

@Composable
fun ProfileTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Accent)
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 11.sp, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, fontSize = 10.sp, color = Muted)
    }
}
