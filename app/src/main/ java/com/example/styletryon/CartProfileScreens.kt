// =====================================================================
// CartProfileScreens.kt = CART, CHECKOUT and PROFILE screens.
// Cart -> Proceed to Buy -> Checkout box (address, delivery date, payment) ->
//    Pay online: Google Pay (PaymentActivity) -> the order is saved ONLY if the payment worked
//    Cash on Delivery: the order is saved straight away
// Profile: user details, settings menu (edit profile, change password...), tiles, and My Orders.
// =====================================================================
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

// The two payment choices shown at checkout. payments[0] = online, payments[1] = cash
val payments = listOf("Pay online (Google Pay / Card)", "Cash on Delivery")

// An order that is WAITING for the online payment. It is only saved after Google Pay says 'paid'
private class PendingOrder(val items: List<CartItem>, val total: Int, val address: String, val deliverOn: Long)

// ---------------------------------------------------------------------
//  Cart
// ---------------------------------------------------------------------

/**
 * The cart lives in MainActivity and is saved in SQLite for the logged-in user,
 * so it is still there after the app is closed.
 */
// *** CART SCREEN ***
// cart = the items (kept in MainActivity and saved in SQLite for this user). email / phone go to the payment
// screen
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
    // pending = the order waiting for payment. coupon / discountPercent = the coupon code. showCheckout = show
    // the checkout box
    var pending by remember { mutableStateOf<PendingOrder?>(null) }
    var coupon by remember { mutableStateOf("") }
    var discountPercent by remember { mutableIntStateOf(0) }
    var showCheckout by remember { mutableStateOf(false) }
    // Only TICKED items are bought. total = subtotal minus the coupon discount
    val subtotal = cart.filter { it.checked }.sumOf { it.product.price * it.qty }
    val total = subtotal - subtotal * discountPercent / 100

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            TopBar("My Cart (${cart.size})", onBack)
            // Empty cart: show a message
            if (cart.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Primary, modifier = Modifier.size(60.dp))
                    Text("Your cart is empty", fontWeight = FontWeight.Bold)
                    Text("Try something on and add it here.", color = Muted)
                }
            } else {
                // Items with check boxes
                // One row per item: tick box, photo, name, colour | size, price, + / - quantity, delete button
                cart.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Tick / untick the item. cart[i] = item.copy(...) replaces the item, so the screen
                        // updates
                        Checkbox(checked = item.checked, onCheckedChange = { cart[i] = item.copy(checked = it) })
                        ProductImage(item.product, Modifier.size(64.dp, 76.dp), padding = 6)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.product.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
                            Text("${item.product.colorName} | ${item.size}", fontSize = 12.sp, color = Muted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rupees(item.product.price), fontWeight = FontWeight.Bold, color = Ink)
                                Spacer(Modifier.weight(1f))
                                // + / - buttons change how many
                                QuantityStepper(item.qty, onChange = { cart[i] = item.copy(qty = it) })
                            }
                        }
                        // Remove the item from the cart
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
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper)
                    )
                    // *** COUPON: TRYVERSE10 gives 10% off, any other code is invalid ***
                    TextButton(onClick = {
                        discountPercent = if (coupon.trim() == "TRYVERSE10") 10 else 0
                        Toast.makeText(context, if (discountPercent > 0) "Coupon applied: 10% off" else "Invalid coupon", Toast.LENGTH_SHORT).show()
                    }) { Text("Apply") }
                }
                if (discountPercent > 0) Text("You save ${rupees(subtotal - total)}", color = Color(0xFF2E8B57), fontSize = 13.sp)
            }
        }

        // Bottom bar
        // Bottom bar with the TOTAL and the Proceed to Buy button
        if (cart.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Paper).padding(16.dp)) {
                Text("Total", color = Muted, fontSize = 12.sp)
                Text(rupees(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                // At least one item must be ticked, then the checkout box opens
                PrimaryButton("Proceed to Buy", Modifier.fillMaxWidth(), Icons.Default.ArrowForward) {
                    if (cart.none { it.checked }) Toast.makeText(context, "Tick at least one item", Toast.LENGTH_SHORT).show()
                    else showCheckout = true
                }
            }
        }
    }

    // Saves the order to orders.csv and empties those items from the cart
    // *** ORDER IS PLACED HERE ***
    // Saves the order in orders.csv (Orders.kt), removes those items from the cart, and opens the order details
    fun placeOrder(p: PendingOrder, payment: String, paymentId: String) {
        val order = saveOrder(context, email, p.items, p.total, p.address, p.deliverOn, payment, paymentId)
        cart.removeAll(p.items)
        Toast.makeText(context, "Order #${order.id} placed!", Toast.LENGTH_LONG).show()
        onOrderPlaced(order)
    }

    // Result from the payment screen (Google Pay or Razorpay)
    // *** RESULT FROM GOOGLE PAY COMES BACK HERE ***
    // When PaymentActivity closes, this runs with its result
    val payLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending
        pending = null
        // PAID: save the order with the method (Google Pay / UPI (test) / Card (test)) and the payment id
        if (p != null && result.resultCode == Activity.RESULT_OK) {
            placeOrder(
                p, result.data?.getStringExtra(Payment.EXTRA_METHOD) ?: Payment.provider,
                result.data?.getStringExtra(Payment.EXTRA_PAYMENT_ID) ?: ""
            )
        } else {
            // NOT paid (cancelled or failed): show why. Nothing is saved and the cart stays the same
            val error = result.data?.getStringExtra(Payment.EXTRA_ERROR) ?: "Payment cancelled."
            Toast.makeText(context, "$error Your cart is unchanged.", Toast.LENGTH_LONG).show()
        }
    }

    // The checkout box
    if (showCheckout) {
        CheckoutDialog(
            userName = userName,
            total = total,
            addresses = addresses,
            onAddAddress = { showCheckout = false; onAddAddress() },
            onDismiss = { showCheckout = false },
            onPlace = { address, deliverOn, payment ->
                // Make the order from the ticked items, the total, the chosen address and delivery date
                val p = PendingOrder(cart.filter { it.checked }, total, address, deliverOn)
                // PAY ONLINE: remember the order as 'pending' and open the payment screen (Google Pay)
                if (payment == payments[0]) {
                    // Online: open the payment sheet; the order is saved only after the payment succeeds
                    showCheckout = false
                    pending = p
                    val description = "${p.items.sumOf { it.qty }} item(s) from TryVerse"
                    // *** OPENS THE PAYMENT SCREEN (PaymentActivity in Payment.kt) ***
                    payLauncher.launch(Payment.intent(context, total, userName, email, phone, description))
                } else {
                    showCheckout = false
                    // CASH ON DELIVERY: save the order straight away (no payment id)
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
// *** CHECKOUT BOX ***
// The user picks: the delivery address (saved addresses), the delivery date (calendar), and how to pay
@Composable
fun CheckoutDialog(
    userName: String, total: Int, onDismiss: () -> Unit, onPlace: (String, Long, String) -> Unit,
    addresses: List<SavedAddress> = defaultAddresses, onAddAddress: () -> Unit = {}
) {
    val context = LocalContext.current
    // Which address (its number in the list), which payment, and the delivery date (0 = not chosen yet)
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
                // One radio button per saved address
                addresses.forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().clickable { address = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = address == i, onClick = { address = i })
                        Column {
                            Text(a.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(a.line, fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                // 'Add new address on map' opens the Google Maps address screen
                TextButton(onClick = onAddAddress) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add new address on map")
                }
                Text("Delivery date", fontWeight = FontWeight.SemiBold)
                // *** DELIVERY DATE PICKER ***
                // Opens Android's calendar. The earliest date allowed is 2 days from today
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
                // Radio buttons: Pay online (Google Pay / Card) or Cash on Delivery
                payments.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { payment = option }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = payment == option, onClick = { payment = option })
                        Text(option, fontSize = 14.sp)
                    }
                }
                // For online payment, show that it is secured by Google Pay and is TEST mode (no real money)
                if (payment == payments[0]) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2E8B57), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Secured by ${Payment.provider} (test mode: no real money is charged)", fontSize = 11.sp, color = Muted)
                    }
                }
            }
        },
        confirmButton = {
            // Pay / Place Order button: a date and an address must be chosen first
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

// *** PROFILE SCREEN ***
// Shows the user's details, the settings menu, tiles (Orders, Saved, Outfits, Try-Ons, Address) and My Orders
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
    // *** ORDERS PER USER: reads only THIS user's orders from orders.csv ***
    var orders by remember(email) { mutableStateOf(loadOrders(context, email)) }   // this user's orders only
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }       // small info pop-ups
    var orderTab by remember { mutableStateOf("All") }
    // How many try-on looks this user has saved (from SQLite)
    val looksCount = remember(email) { if (email.isEmpty()) 0 else db.loadLooks(email).size }
    val name = user?.name ?: "Guest"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Title + settings menu
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            Box {
                // *** SETTINGS MENU (gear icon) ***: edit profile, change password, clear orders, payment
                // methods, about, log out
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit profile") }, onClick = { menuOpen = false; editing = true })
                    // Opens the Change password box
                    DropdownMenuItem(text = { Text("Change password") }, onClick = { menuOpen = false; changingPassword = true })
                    // Deletes only this user's orders from orders.csv
                    DropdownMenuItem(text = { Text("Clear order history") }, onClick = {
                        menuOpen = false
                        clearOrders(context, email)
                        orders = emptyList()
                        Toast.makeText(context, "Order history cleared", Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text("Payment methods") }, onClick = {
                        menuOpen = false
                        info = payments.joinToString("\n") + "\n\nOnline payments are handled securely by ${Payment.provider}. TryVerse never sees or stores your card or UPI details."
                    })
                    DropdownMenuItem(text = { Text("About TryVerse") }, onClick = {
                        menuOpen = false
                        info = "TryVerse 2.0\nTry clothes on live with your camera, or get an AI photo of you wearing them.\n\n" +
                                "Garment and model images: OOTDiffusion examples (CC BY-NC-SA 4.0), used for a non-commercial college project."
                    })
                    // Log out: MainActivity forgets the logged-in user (their data stays saved in SQLite for
                    // next login)
                    DropdownMenuItem(text = { Text("Log out") }, onClick = { menuOpen = false; onLogout() })
                }
            }
        }

        // Profile card
        // Profile card: a circle with the initials (e.g. Manvi Segan -> MS), name, email, phone, Edit button
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Text(
                    name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                    color = OnInk, fontSize = 24.sp, fontWeight = FontWeight.Bold
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
        // *** SIZE SUGGESTION: shows the user's sizes worked out from the live camera body scan ***
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
        // The 5 tiles with counts. Tapping a tile opens that screen
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
        // *** MY ORDERS ***
        // Tabs to filter the orders by status: All, Processing, Shipped, Delivered, Cancelled
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Processing", "Shipped", "Delivered", "Cancelled").forEach {
                FilterChip(selected = orderTab == it, onClick = { orderTab = it }, label = { Text(it) })
            }
        }
        // Only the orders with the chosen status
        val shown = orders.filter { orderTab == "All" || it.status == orderTab }
        if (shown.isEmpty()) Text("No orders here yet.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        // One card per order. Tapping it opens the Order Details screen
        shown.forEach { order ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOrder(order) },
                shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Paper)
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
    // *** EDIT PROFILE BOX ***: change name and phone (checked), saved in SQLite with db.updateUser
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

    // Change password box
    if (changingPassword && user != null) {
        ChangePasswordDialog(db, user.email, onDone = { changingPassword = false })
    }

    // Small info box (Payment methods, About TryVerse)
    if (info != null) {
        AlertDialog(
            onDismissRequest = { info = null },
            text = { Text(info!!) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("OK") } }
        )
    }
}

// Coloured order status label: green = Delivered, gold = Shipped, red = Cancelled, orange = Processing
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

// *** CHANGE PASSWORD ***
// Current password + new password + confirm. The new one is saved HASHED in SQLite
@Composable
fun ChangePasswordDialog(db: UserDatabase, email: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }          // show all errors after Save is pressed
    var wrongCurrent by remember { mutableStateOf(false) }
    // New password rules: 8+ characters with a letter and a number, and different from the current one
    val newError = when {
        !tried && newPassword.isEmpty() -> null
        !Validate.password(newPassword) -> "8+ characters with a letter and a number"
        newPassword == current -> "Choose a different password from the current one"
        else -> null
    }
    // Confirm must be the same as the new password
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
                // *** DATABASE: checks the CURRENT password first; only if it is right is the new one saved ***
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

// A password box (shows dots) with a red error under it
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

// One small tile on the profile: icon, title (e.g. Orders) and a count
@Composable
fun ProfileTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Paper).clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Accent)
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 11.sp, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, fontSize = 10.sp, color = Muted)
    }
}
