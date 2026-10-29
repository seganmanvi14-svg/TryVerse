// =====================================================================
// MainActivity.kt = WHERE THE APP STARTS + NAVIGATION + SHARED DATA.
// The app has ONE Activity. Every screen is a Compose function, and MainActivity decides which one to show.
// Navigation = a list (stack) of screen names; the last one is shown, Back removes it.
// It also loads / saves the logged-in user's cart, wishlist, searches, addresses and outfits (SQLite).
// =====================================================================
package com.example.styletryon

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

// Colours, type and shapes are in Theme.kt (Zara-style design system)

// *** THE APP STARTS HERE ***
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Firebase App Check: only our genuine app may use the AI.
        // Debug builds use a debug token (printed in Logcat), real builds use Google Play Integrity.
        // Only if Firebase is set up: App Check makes sure only our real app can use the AI
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            Firebase.appCheck.installAppCheckProviderFactory(
                if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
        // Black (Noir) is the default; the user can switch to light (Home sun icon / Account → Settings)
        // Use the saved theme (black by default)
        ThemeState.dark = loadThemeChoice(this) ?: true
        // Draw the app with Compose
        setContent { TryVerseApp() }
    }
}

// Screens that show the bottom bar
val tabs = listOf("home", "categories", "tryon", "cart", "profile")

// *** THE WHOLE APP ***
@Composable
fun TryVerseApp() {
    val context = LocalContext.current
    // Open the SQLite database once (remember = keep it, don't make a new one every redraw)
    val db = remember { UserDatabase(context) }

    // Simple navigation: a list of screen names, the last one is shown
    // Already logged in: open Home straight away; otherwise start on the welcome screen
    // *** NAVIGATION STACK ***
    // Already logged in (session.txt + the user exists in the database) -> start on Home, else the welcome
    // screen
    val stack = remember { mutableStateListOf(if (loadSession(context)?.let { db.getUser(it) } != null) "home" else "splash") }
    val screen = stack.last()
    // go = open a screen on top. back = close the top screen. openTab = clear everything and open a tab
    fun go(to: String) { stack.add(to) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun openTab(to: String) { stack.clear(); stack.add(to) }
    // After processing, go to the result and drop the processing screen (so Back goes to Adjust, not processing)
    fun showResult() {
        while (stack.size > 1 && stack.last() != "adjust") stack.removeAt(stack.lastIndex)
        stack.add("result")
    }
    // The phone's Back button goes to the previous screen
    BackHandler(enabled = stack.size > 1) { back() }

    // Shared app state
    // *** SHARED APP STATE *** (values many screens need). user = who is logged in (null = nobody)
    var user by remember { mutableStateOf(loadSession(context)?.let { db.getUser(it) }) }
    var category by remember { mutableStateOf("All") }
    var filtersFirst by remember { mutableStateOf(false) }             // Home's filter button opens the filters
    var selected by remember { mutableStateOf(catalog.first()) }       // product page
    var tryProduct by remember { mutableStateOf(catalog.first()) }     // garment being tried on
    var photo by remember { mutableStateOf<Bitmap?>(null) }            // photo picked by the user
    var cropped by remember { mutableStateOf<Bitmap?>(null) }          // photo after "Adjust Photo"
    var look by remember { mutableStateOf<Bitmap?>(null) }             // the try-on result
    var madeByAi by remember { mutableStateOf(false) }                 // AI picture or quick preview
    var editBase by remember { mutableStateOf<Bitmap?>(null) }         // AI result to recolour
    var colorName by remember { mutableStateOf<String?>(null) }        // "Try Another Color"
    var tint by remember { mutableStateOf<Color?>(null) }
    var openOrder by remember { mutableStateOf<Order?>(null) }         // order shown in Order Details
    var searches by remember { mutableStateOf(listOf<String>()) }      // recent searches
    var addresses by remember { mutableStateOf(listOf<SavedAddress>()) }   // delivery addresses (Maps)
    var editingAddress by remember { mutableStateOf<SavedAddress?>(null) } // address open on the map
    var outfits by remember { mutableStateOf(listOf<SavedOutfit>()) }      // saved mix & match outfits
    var liveOutfit by remember { mutableStateOf<SavedOutfit?>(null) }      // outfit opened in the live camera
    // The cart and the wishlist (when they change, the screens update by themselves)
    val cart = remember { mutableStateListOf<CartItem>() }
    val wishlist = remember { mutableStateListOf<Int>() }

    // ---------------------------------------------------------------
    //  Everything a user does is kept for that user (SQLite):
    //  on login / app start their cart, wishlist and searches are loaded,
    //  and every change to the cart or wishlist is saved straight away.
    //  So closing the app, or logging out and in again, loses nothing.
    // ---------------------------------------------------------------
    // *** DATA PER USER ***
    val email = user?.email
    // Runs when the user logs in / out: empty everything, then load THIS user's data from SQLite
    LaunchedEffect(email) {
        cart.clear()
        wishlist.clear()
        searches = emptyList()
        addresses = emptyList()
        outfits = emptyList()
        if (email == null) return@LaunchedEffect
        cart.addAll(db.loadCart(email))
        wishlist.addAll(db.loadWishlist(email))
        searches = db.loadSearches(email)
        addresses = db.loadAddresses(email)
        outfits = db.loadOutfits(email)
        // *** AUTO-SAVE ***: every time the cart or the wishlist changes, save it in SQLite straight away
        snapshotFlow { cart.toList() to wishlist.toList() }.collect { (c, w) ->
            db.saveCart(email, c)
            db.saveWishlist(email, w)
        }
    }
    // The Home filter button opens the filters only once
    LaunchedEffect(screen) { if (screen != "categories") filtersFirst = false }

    // Size to add by default: the user's body-scan size if it is in stock, else M / 32
    fun bestSize(p: Product) = suggestedSize(p, user)?.takeIf { p.hasSize(it) } ?: p.defaultSize()

    // ♡ button: save or unsave a product
    val toggleWish: (Product) -> Unit = { p -> if (wishlist.contains(p.id)) wishlist.remove(p.id) else wishlist.add(p.id) }
    // *** ADD TO CART ***: refuses sold-out sizes; the same product + size again just adds 1 to the quantity
    val addToCart: (Product, String) -> Unit = { p, s ->
        if (!p.hasSize(s)) {
            Toast.makeText(context, "Sorry, ${p.name} ($s) is out of stock", Toast.LENGTH_SHORT).show()
        } else {
            // Same product and size again -> one more of it, instead of a second line
            val i = cart.indexOfFirst { it.product.id == p.id && it.size == s }
            if (i >= 0) cart[i] = cart[i].copy(qty = cart[i].qty + 1) else cart.add(CartItem(p, s))
        }
    }
    // Shortcuts used by many screens: open a product page, start a try-on, redo a try-on, log in
    val openProduct: (Product) -> Unit = { selected = it; go("detail") }
    val startTryOn: (Product) -> Unit = { tryProduct = it; colorName = null; tint = null; go("tryon") }
    val retry: (Product) -> Unit = { tryProduct = it; colorName = null; tint = null; editBase = null; go("processing") }
    // Logged in / signed up: remember the user, save the session (stay logged in), go Home
    val loggedIn: (User) -> Unit = { user = it; saveSession(context, it.email); openTab("home") }

    // Status and navigation bars follow the theme (black bars with white icons in the black theme)
    // The phone's top and bottom bars use the theme colours (light icons in the black theme)
    val view = LocalView.current
    val dark = ThemeState.dark
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = Cream.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Cream.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    // Our colours, fonts and shapes for everything inside
    ZaraTheme {
        Scaffold(
            containerColor = Cream,
            // Bottom bar only on the 5 tab screens
            bottomBar = { if (screen in tabs) NoirBottomBar(screen, cart.sumOf { it.qty }) { openTab(it) } }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                // *** WHICH SCREEN TO SHOW *** (by the name on top of the stack)
                when (screen) {
                    // Welcome screen -> Sign Up or Log In
                    "splash" -> SplashScreen(
                        onStart = { openTab(if (user != null) "home" else "signup") },
                        onLogin = { openTab(if (user != null) "home" else "login") }
                    )
                    // Sign up and Login (both check the SQLite database)
                    "signup" -> SignUpScreen(db = db, onSignedUp = loggedIn, onLogin = { go("login") })
                    "login" -> LoginScreen(db = db, onLoggedIn = loggedIn, onSignUp = { openTab("signup") })
                    // Home
                    "home" -> HomeScreen(
                        userName = user?.name ?: "there",
                        wishlist = wishlist, onWish = toggleWish, onProduct = openProduct,
                        onCategory = { category = it; openTab("categories") },
                        onShopNow = { startTryOn(catalog[1]) },
                        onFilters = { category = "All"; filtersFirst = true; openTab("categories") }
                    )
                    // Categories with search; searches are saved per user in SQLite
                    "categories" -> CategoriesScreen(
                        category, { category = it }, wishlist, toggleWish, openProduct,
                        history = searches,
                        onSearched = { q -> user?.let { db.addSearch(it.email, q); searches = db.loadSearches(it.email) } },
                        onClearHistory = { user?.let { db.clearSearches(it.email) }; searches = emptyList() },
                        startWithFilters = filtersFirst
                    )
                    "saved" -> SavedScreen(wishlist, toggleWish, openProduct, onBack = { back() })
                    // Product page (with 'your size' from the body scan)
                    "detail" -> ProductDetailScreen(
                        product = selected,
                        wishlisted = wishlist.contains(selected.id),
                        onBack = { back() },
                        onWish = { toggleWish(selected) },
                        onAdd = addToCart,
                        onTry = { startTryOn(selected) },
                        yourSize = suggestedSize(selected, user)
                    )
                    // Try-on start: live camera, upload a photo, or a sample model
                    "tryon" -> TryOnUploadScreen(
                        product = tryProduct,
                        onProduct = { tryProduct = it },
                        onBack = { if (stack.size > 1) back() else openTab("home") },
                        onLive = { liveOutfit = null; go("live") },
                        onPhoto = { photo = it; go("adjust") }
                    )
                    // Live camera try-on. Height, body measurements, captures and outfits are saved for the user
                    "live" -> LiveTryOnScreen(
                        startProduct = tryProduct,
                        onClose = { back() },
                        onAddToCart = addToCart,
                        heightCm = user?.heightCm ?: 0,
                        onHeight = { cm -> user?.let { db.setHeight(it.email, cm); user = it.copy(heightCm = cm) } },
                        onMeasured = { m ->
                            user?.let {
                                db.setMeasurements(it.email, m.shoulderCm, m.hipCm)
                                user = it.copy(shoulderCm = m.shoulderCm, hipCm = m.hipCm)
                            }
                        },
                        onCaptured = { picture, productId -> user?.let { saveLookToHistory(context, db, it.email, picture, productId) } },
                        startOutfit = liveOutfit?.products,
                        startSizes = liveOutfit?.sizes ?: emptyMap(),
                        onSaveOutfit = { items ->
                            user?.let {
                                db.saveOutfit(it.email, "Outfit ${outfits.size + 1}", items)
                                outfits = db.loadOutfits(it.email)
                            }
                        }
                    )
                    // Adjust the photo, then processing
                    "adjust" -> AdjustPhotoScreen(
                        photo = photo!!,
                        onRetake = { back() },
                        onContinue = { cropped = it; colorName = null; tint = null; editBase = null; go("processing") }
                    )
                    // Makes the try-on picture; the result is also saved in the Try-On History
                    "processing" -> ProcessingScreen(
                        base = editBase ?: cropped!!,
                        person = cropped!!,
                        product = tryProduct,
                        colorName = colorName,
                        tint = tint,
                        onDone = { picture, ai ->
                            look = picture
                            madeByAi = ai
                            user?.let { saveLookToHistory(context, db, it.email, picture, tryProduct.id) }   // Try-On History
                            showResult()
                        },
                        onFail = { back() }
                    )
                    // *** RESULT + MIX & MATCH ***
                    // person = the clean photo, so a second garment can be drawn on it
                    "result" -> ResultScreen(
                        look = look!!, madeByAi = madeByAi, product = tryProduct,
                        person = cropped,
                        // Mix & match: add the whole outfit (each item in the user's best size)
                        // Add the whole mix & match outfit to the cart (each in the user's best size), then open
                        // the cart
                        onAddOutfit = { items ->
                            val (ok, soldOut) = items.partition { it.inStock }
                            ok.forEach { addToCart(it, bestSize(it)) }
                            val msg = if (soldOut.isEmpty()) "Outfit added to cart" else "Added. Out of stock: " + soldOut.joinToString { it.name }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok.isNotEmpty()) openTab("cart")
                        },
                        // Save the outfit to My Outfits in SQLite (only when logged in)
                        onSaveOutfit = user?.let { u ->
                            { items: List<Product> ->
                                db.saveOutfit(u.email, "Outfit ${outfits.size + 1}", items.map { it to bestSize(it) })
                                outfits = db.loadOutfits(u.email)
                                Toast.makeText(context, "Saved to My Outfits", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onProduct = retry,
                        onBack = { back() },
                        onCompare = { go("compare") },
                        // Add the single item, then show 'Complete Your Look'
                        onAddToCart = {
                            if (tryProduct.inStock) {
                                addToCart(tryProduct, bestSize(tryProduct))
                                Toast.makeText(context, "${tryProduct.name} (${bestSize(tryProduct)}) added to cart", Toast.LENGTH_SHORT).show()
                                go("complete")
                            } else {
                                Toast.makeText(context, "Sorry, ${tryProduct.name} is out of stock", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    // Compare + Try Another Colour (makes a new try-on in that colour)
                    "compare" -> CompareScreen(
                        photo = cropped!!, look = look!!, product = tryProduct, colorName = colorName,
                        onColor = { name, c ->
                            colorName = name
                            tint = c
                            editBase = if (name != null && madeByAi) look else null   // AI: recolour the last result
                            go("processing")
                        },
                        onBack = { back() }
                    )
                    "complete" -> CompleteLookScreen(
                        product = tryProduct, wishlist = wishlist, onWish = toggleWish,
                        onAdd = { addToCart(it, bestSize(it)) },
                        onBack = { back() },
                        onCart = { openTab("cart") }
                    )
                    // Cart -> checkout -> payment -> Order Details
                    "cart" -> CartScreen(
                        cart, userName = user?.name ?: "", onBack = { openTab("home") },
                        email = user?.email ?: "", phone = user?.phone ?: "",
                        onOrderPlaced = { openOrder = it; go("order") },
                        addresses = addresses,
                        onAddAddress = { editingAddress = null; go("map") }
                    )
                    // Order details; Buy again puts the same items back in the cart
                    "order" -> OrderDetailsScreen(
                        order = openOrder!!,
                        onBack = { back() },
                        onProduct = openProduct,
                        suggestSize = { suggestedSize(it, user) },
                        onBuyAgain = { order ->
                            order.lines.forEach { line -> repeat(line.qty) { addToCart(line.product, line.size) } }
                            openTab("cart")
                        }
                    )
                    // Try-On History and My Outfits (rename / delete are saved in SQLite)
                    "history" -> TryOnHistoryScreen(db, user?.email ?: "", onBack = { back() }, onProduct = openProduct)
                    "outfits" -> OutfitsScreen(
                        outfits = outfits,
                        onBack = { back() },
                        onTryLive = { o -> liveOutfit = o; tryProduct = o.products.first(); go("live") },
                        onAddAll = { o ->
                            val inStock = o.items.filter { (p, size) -> p.hasSize(size) }
                            inStock.forEach { (p, size) -> addToCart(p, size) }
                            Toast.makeText(context, "${inStock.size} item(s) added to cart", Toast.LENGTH_SHORT).show()
                        },
                        onRename = { o, name -> user?.let { db.renameOutfit(it.email, o.id, name); outfits = db.loadOutfits(it.email) } },
                        onDelete = { o -> user?.let { db.deleteOutfit(it.email, o.id); outfits = db.loadOutfits(it.email) } }
                    )
                    // My Addresses and the Google Maps picker; addresses are saved per user in SQLite
                    "addresses" -> AddressListScreen(
                        addresses = addresses,
                        onBack = { back() },
                        onAdd = { editingAddress = null; go("map") },
                        onEdit = { editingAddress = it; go("map") },
                        onDelete = { a -> user?.let { db.deleteAddress(it.email, a.id); addresses = db.loadAddresses(it.email) } }
                    )
                    "map" -> AddressMapScreen(
                        start = editingAddress,
                        onBack = { back() },
                        onSave = { a ->
                            user?.let { db.saveAddress(it.email, a); addresses = db.loadAddresses(it.email) }
                            Toast.makeText(context, "Address saved", Toast.LENGTH_SHORT).show()
                            back()
                        }
                    )
                    // Profile (the default)
                    else -> ProfileScreen(
                        db = db,
                        user = user,
                        wishlistCount = wishlist.size,
                        onSaved = { go("saved") },
                        addressCount = addresses.size,
                        onAddresses = { go("addresses") },
                        outfitCount = outfits.size,
                        onOutfits = { go("outfits") },
                        onUserChanged = { user = it },
                        // *** LOG OUT ***: delete session.txt and forget the user. Their data stays in SQLite
                        // for next time
                        onLogout = {
                            clearSession(context)
                            ShopState.reset()
                            user = null              // the cart and wishlist stay saved in SQLite for next login
                            openTab("login")
                        },
                        onOrder = { openOrder = it; go("order") },
                        onHistory = { go("history") }
                    )
                }
            }
        }
    }
}

/**
 * Noir bottom bar: Home, Categories, a raised round Try-On button with a champagne ring, Cart (with count), Profile.
 */
// *** BOTTOM BAR ***: Home, Categories, the raised round Try-On button, Cart (with the item count), Profile
@Composable
fun NoirBottomBar(screen: String, cartCount: Int, onTab: (String) -> Unit) {
    val labels = listOf("Home", "Categories", "Try-On", "Cart", "Profile")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.GridView, Icons.Rounded.PhotoCamera, Icons.Rounded.ShoppingCart, Icons.Rounded.Person)
    Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.padding(top = 22.dp).fillMaxWidth().background(Paper)) {
            Hairline()
            Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, t ->
                    val on = screen == t
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable { onTab(t) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        if (t == "tryon") {
                            Spacer(Modifier.height(24.dp))      // the raised button sits above this label
                        } else {
                            Box {
                                Icon(icons[i], contentDescription = labels[i], tint = if (on) Primary else Muted, modifier = Modifier.size(24.dp))
                                if (t == "cart" && cartCount > 0) {
                                    Text(
                                        "$cartCount", color = OnInk, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp)
                                            .clip(CircleShape).background(Primary).padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(labels[i], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (on) Primary else Muted)
                    }
                }
            }
        }
        // Raised Try-On button
        Box(
            Modifier.align(Alignment.TopCenter).size(62.dp)
                .shadow(16.dp, CircleShape, ambientColor = Gold, spotColor = Gold)
                .clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF1F1C17), Color(0xFF0B0A09))))
                .border(1.5.dp, Gold, CircleShape).clickable { onTab("tryon") },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PhotoCamera, contentDescription = "Try-On", tint = Gold, modifier = Modifier.size(26.dp)) }
    }
}
