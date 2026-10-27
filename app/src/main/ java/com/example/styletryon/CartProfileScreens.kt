package com.example.styletryon

import android.app.Activity
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

val payments = listOf("Pay online (UPI / Card / Netbanking)", "Cash on Delivery")

/** An order waiting for Razorpay to say the payment worked. */
private class PendingOrder(val items: List<CartItem>, val total: Int, val address: String, val deliverOn: Long)

// ---------------------------------------------------------------------
//  Cart
// ---------------------------------------------------------------------

/**
 * The cart lives in MainActivity and is saved in SQLite for the logged-in user,
 * so it is still there after the app is closed.
 */
@Composable
fun CartScreen(
    cart: MutableList<CartItem>,
    userName: String,
    onBack: () -> Unit,
    email: String = "",
    phone: String = "",
    onOrderPlaced: (Order) -> Unit = {},
    addresses: List<SavedAddress> = defaultAddresses,   // the user's saved addresses
    onAddAddress: () -> Unit = {}                         // opens the map to add one
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingOrder?>(null) }
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

    // Saves the order to orders.csv and empties those items from the cart
    fun placeOrder(p: PendingOrder, payment: String, paymentId: String) {
        val order = saveOrder(context, email, p.items, p.total, p.address, p.deliverOn, payment, paymentId)
        cart.removeAll(p.items)
        Toast.makeText(context, "Order #${order.id} placed!", Toast.LENGTH_LONG).show()
        onOrderPlaced(order)
    }

    // Result from Razorpay (PaymentActivity)
    val payLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending
        pending = null
        if (p != null && result.resultCode == Activity.RESULT_OK) {
            placeOrder(p, "Razorpay", result.data?.getStringExtra(Payment.EXTRA_PAYMENT_ID) ?: "")
        } else {
            val error = result.data?.getStringExtra(Payment.EXTRA_ERROR) ?: "Payment cancelled."
            Toast.makeText(context, "$error Your cart is unchanged.", Toast.LENGTH_LONG).show()
        }
    }

    if (showCheckout) {
        CheckoutDialog(
            userName = userName,
            total = total,
            addresses = addresses,
            onAddAddress = { showCheckout = false; onAddAddress() },
            onDismiss = { showCheckout = false },
            onPlace = { address, deliverOn, payment ->
                val p = PendingOrder(cart.filter { it.checked }, total, address, deliverOn)
                if (payment == payments[0]) {
                    // Online: open Razorpay; the order is saved only after the payment succeeds
                    if (!Payment.isSetUp) {
                        Toast.makeText(context, "Online payment isn't set up yet. Please choose Cash on Delivery.", Toast.LENGTH_LONG).show()
                        return@CheckoutDialog
                    }
                    showCheckout = false
                    pending = p
                    val description = "${p.items.sumOf { it.qty }} item(s) from TryVerse"
                    payLauncher.launch(Payment.intent(context, total, userName, email, phone, description))
                } else {
                    showCheckout = false
                    placeOrder(p, "Cash on Delivery", "-")
                }
            }
        )
    }
}

/**
 * Pop-up with address (radio), delivery date (Date Picker) and payment (radio).
 * onPlace(address name, delivery date in milliseconds, payment method)
 */
@Composable
fun CheckoutDialog(
    userName: String, total: Int, onDismiss: () -> Unit, onPlace: (String, Long, String) -> Unit,
    addresses: List<SavedAddress> = defaultAddresses, onAddAddress: () -> Unit = {}
) {
    val context = LocalContext.current
    var address by remember { mutableIntStateOf(0) }
    var payment by remember { mutableStateOf(payments[0]) }
    var deliverOn by remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checkout • ${rupees(total)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Deliver to $userName", fontWeight = FontWeight.SemiBold)
                if (addresses.isEmpty()) Text("No saved address yet", color = Rose, fontSize = 13.sp)
                addresses.forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().clickable { address = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = address == i, onClick = { address = i })
                        Column {
                            Text(a.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(a.line, fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                TextButton(onClick = onAddAddress) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add new address on map")
                }
                Text("Delivery date", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = {
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_MONTH, 2)                 // earliest delivery: in 2 days
                    DatePickerDialog(context, { _, y, m, d ->
                        deliverOn = Calendar.getInstance().apply { set(y, m, d, 9, 0, 0) }.timeInMillis
                    },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
                    ).apply { datePicker.minDate = c.timeInMillis }.show()
                }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (deliverOn == 0L) "Choose date" else formatDate(deliverOn))
                }
                Spacer(Modifier.height(8.dp))
                Text("Payment method", fontWeight = FontWeight.SemiBold)
                payments.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { payment = option }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = payment == option, onClick = { payment = option })
                        Text(option, fontSize = 14.sp)
                    }
                }
                if (payment == payments[0]) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2E8B57), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Secured by Razorpay (test mode: no real money is charged)", fontSize = 11.sp, color = Muted)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (deliverOn == 0L) Toast.makeText(context, "Please choose a delivery date", Toast.LENGTH_SHORT).show()
                else if (address !in addresses.indices) Toast.makeText(context, "Please add a delivery address", Toast.LENGTH_SHORT).show()
                else onPlace(addresses[address].full, deliverOn, payment)
            }) { Text(if (payment == payments[0]) "Pay ${rupees(total)}" else "Place Order") }
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
    onLogout: () -> Unit,
    addressCount: Int = 0,
    onAddresses: () -> Unit = {},          // opens My Addresses
    outfitCount: Int = 0,
    onOutfits: () -> Unit = {},            // opens My Outfits
    onOrder: (Order) -> Unit = {},         // opens Order Details
    onHistory: () -> Unit = {}             // opens Try-On History
) {
    val context = LocalContext.current
    val email = user?.email ?: ""
    var orders by remember(email) { mutableStateOf(loadOrders(context, email)) }   // this user's orders only
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }       // small info pop-ups
    var orderTab by remember { mutableStateOf("All") }
    val looksCount = remember(email) { if (email.isEmpty()) 0 else db.loadLooks(email).size }
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
                    DropdownMenuItem(text = { Text("Change password") }, onClick = { menuOpen = false; changingPassword = true })
                    DropdownMenuItem(text = { Text("Clear order history") }, onClick = {
                        menuOpen = false
                        clearOrders(context, email)
                        orders = emptyList()
                        Toast.makeText(context, "Order history cleared", Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text("Payment methods") }, onClick = {
                        menuOpen = false
                        info = payments.joinToString("\n") + "\n\nOnline payments are handled securely by Razorpay. TryVerse never sees or stores your card or UPI details."
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

        // My size (from Live Try-On: height + body scan)
        if (user != null && user.heightCm > 0) {
            Text(
                "Height ${user.heightCm} cm  •  " +
                        if (user.shoulderCm > 0f) "Your sizes: top ${topSizeFor(user.shoulderCm)}, waist ${waistSizeFor(user.hipCm)}"
                        else "Scan in Live Try-On → Size to get your size",
                color = Primary, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp)).background(PrimaryLight)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // Tiles
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileTile(Icons.Default.Inventory2, "Orders", "${orders.size}", Modifier.weight(1f)) { orderTab = "All" }
            ProfileTile(Icons.Default.FavoriteBorder, "Saved", "$wishlistCount", Modifier.weight(1f), onSaved)
            ProfileTile(Icons.Default.Checkroom, "Outfits", "$outfitCount", Modifier.weight(1f), onOutfits)
            ProfileTile(Icons.Default.PhotoLibrary, "Try-Ons", "$looksCount", Modifier.weight(1f), onHistory)
            ProfileTile(Icons.Default.LocationOn, "Address", "$addressCount", Modifier.weight(1f), onAddresses)
        }

        // My Orders (read from orders.csv)
        Spacer(Modifier.height(18.dp))
        Text("My Orders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Processing", "Shipped", "Delivered", "Cancelled").forEach {
                FilterChip(selected = orderTab == it, onClick = { orderTab = it }, label = { Text(it) })
            }
        }
        val shown = orders.filter { orderTab == "All" || it.status == orderTab }
        if (shown.isEmpty()) Text("No orders here yet.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        shown.forEach { order ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOrder(order) },
                shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(order.lines.first().product, Modifier.size(56.dp, 66.dp), padding = 5)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Order #${order.id}", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("${order.date}  •  ${order.itemCount} item(s)", color = Muted, fontSize = 12.sp)
                        Text(order.summary, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusChip(order.status)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rupees(order.total), fontWeight = FontWeight.Bold, color = Ink)
                        Icon(Icons.Default.ChevronRight, contentDescription = "Details", tint = Muted)
                    }
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
                    onUserChanged(user.copy(name = newName.trim(), phone = newPhone.trim()))
                    editing = false
                    Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }

    if (changingPassword && user != null) {
        ChangePasswordDialog(db, user.email, onDone = { changingPassword = false })
    }

    if (info != null) {
        AlertDialog(
            onDismissRequest = { info = null },
            text = { Text(info!!) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("OK") } }
        )
    }
}

/** Coloured label for an order status. */
@Composable
fun StatusChip(status: String) {
    val colour = when (status) {
        "Delivered" -> Color(0xFF2E8B57)
        "Shipped" -> Primary
        "Cancelled" -> Rose
        else -> Color(0xFFE0922F)          // Processing
    }
    Text(
        "● $status", color = colour, fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colour.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Change password: current password, new password and confirm; saved hashed in SQLite. */
@Composable
fun ChangePasswordDialog(db: UserDatabase, email: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }          // show all errors after Save is pressed
    var wrongCurrent by remember { mutableStateOf(false) }
    val newError = when {
        !tried && newPassword.isEmpty() -> null
        !Validate.password(newPassword) -> "8+ characters with a letter and a number"
        newPassword == current -> "Choose a different password from the current one"
        else -> null
    }
    val confirmError = if ((tried || confirm.isNotEmpty()) && confirm != newPassword) "Passwords do not match" else null

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PasswordBox(current, { current = it.take(64); wrongCurrent = false }, "Current password", if (wrongCurrent) "Current password is wrong" else null)
                PasswordBox(newPassword, { newPassword = it.take(64) }, "New password", newError)
                PasswordBox(confirm, { confirm = it.take(64) }, "Confirm new password", confirmError)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tried = true
                if (!Validate.password(newPassword) || newPassword == current || confirm != newPassword) return@TextButton
                if (db.changePassword(email, current, newPassword)) {
                    Toast.makeText(context, "Password changed", Toast.LENGTH_SHORT).show()
                    onDone()
                } else {
                    wrongCurrent = true
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

/** Password text box with an error message under it. */
@Composable
fun PasswordBox(value: String, onChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null
    )
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
