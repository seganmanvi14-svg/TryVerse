package com.example.styletryon

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

// ---------- TryVerse colours (from the design) ----------
val Ink = Color(0xFF232A4D)          // main text
val Brand = Color(0xFF2F3A8F)        // "Try" in the logo, headings
val Accent = Color(0xFFA67CF0)       // "Verse" in the logo
val Primary = Color(0xFF6286D8)      // main buttons
val PrimaryDark = Color(0xFF4659A8)  // "Add to Cart" button
val PrimaryLight = Color(0xFFE3E9FB) // light buttons, selected chips
val SoftBlue = Color(0xFFEFF4FD)     // cards / upload box
val Cream = Color(0xFFF8F8FD)        // screen background
val Sand = Color(0xFFF1EEF3)         // behind product photos
val Muted = Color(0xFF8A8CA3)        // secondary text
val Rose = Color(0xFFE0707F)         // hearts, discounts
val Star = Color(0xFFF2B233)         // rating star

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Firebase App Check: only our genuine app may use the AI.
        // Debug builds use a debug token (printed in Logcat), real builds use Google Play Integrity.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            Firebase.appCheck.installAppCheckProviderFactory(
                if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
        setContent { TryVerseApp() }
    }
}

/** Screens that show the bottom navigation bar. */
val tabs = listOf("home", "categories", "tryon", "cart", "profile")

@Composable
fun TryVerseApp() {
    val context = LocalContext.current
    val db = remember { UserDatabase(context) }

    // Simple navigation: a list of screen names, the last one is shown
    val stack = remember { mutableStateListOf("splash") }
    val screen = stack.last()
    fun go(to: String) { stack.add(to) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun openTab(to: String) { stack.clear(); stack.add(to) }
    /** Replaces the try-on steps after "adjust" with the result screen. */
    fun showResult() {
        while (stack.size > 1 && stack.last() != "adjust") stack.removeAt(stack.lastIndex)
        stack.add("result")
    }
    BackHandler(enabled = stack.size > 1) { back() }

    // Shared app state
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
    val cart = remember { mutableStateListOf<CartItem>() }
    val wishlist = remember { mutableStateListOf<Int>() }

    // ---------------------------------------------------------------
    //  Everything a user does is kept for that user (SQLite):
    //  on login / app start their cart, wishlist and searches are loaded,
    //  and every change to the cart or wishlist is saved straight away.
    //  So closing the app, or logging out and in again, loses nothing.
    // ---------------------------------------------------------------
    val email = user?.email
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
        snapshotFlow { cart.toList() to wishlist.toList() }.collect { (c, w) ->
            db.saveCart(email, c)
            db.saveWishlist(email, w)
        }
    }
    // The Home filter button opens the filters only once
    LaunchedEffect(screen) { if (screen != "categories") filtersFirst = false }

    /** Size to add by default: the user's suggested size if it is in stock, otherwise M / 32 (or the first in stock). */
    fun bestSize(p: Product) = suggestedSize(p, user)?.takeIf { p.hasSize(it) } ?: p.defaultSize()

    val toggleWish: (Product) -> Unit = { p -> if (wishlist.contains(p.id)) wishlist.remove(p.id) else wishlist.add(p.id) }
    val addToCart: (Product, String) -> Unit = { p, s ->
        if (!p.hasSize(s)) {
            Toast.makeText(context, "Sorry, ${p.name} ($s) is out of stock", Toast.LENGTH_SHORT).show()
        } else {
            // Same product and size again -> one more of it, instead of a second line
            val i = cart.indexOfFirst { it.product.id == p.id && it.size == s }
            if (i >= 0) cart[i] = cart[i].copy(qty = cart[i].qty + 1) else cart.add(CartItem(p, s))
        }
    }
    val openProduct: (Product) -> Unit = { selected = it; go("detail") }
    val startTryOn: (Product) -> Unit = { tryProduct = it; colorName = null; tint = null; go("tryon") }
    val retry: (Product) -> Unit = { tryProduct = it; colorName = null; tint = null; editBase = null; go("processing") }
    val loggedIn: (User) -> Unit = { user = it; saveSession(context, it.email); openTab("home") }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Primary, onPrimary = Color.White, secondary = Accent,
            background = Cream, surface = Color.White, onBackground = Ink, onSurface = Ink,
            secondaryContainer = PrimaryLight
        )
    ) {
        Scaffold(
            containerColor = Cream,
            bottomBar = {
                if (screen in tabs) {
                    NavigationBar(containerColor = Color.White) {
                        val labels = listOf("Home", "Categories", "Try-On", "Cart", "Profile")
                        val icons = listOf(Icons.Default.Home, Icons.Default.GridView, Icons.Default.Checkroom, Icons.Default.ShoppingCart, Icons.Default.Person)
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = screen == t,
                                onClick = { openTab(t) },
                                icon = {
                                    BadgedBox(badge = { if (t == "cart" && cart.isNotEmpty()) Badge { Text("${cart.sumOf { it.qty }}") } }) {
                                        Icon(icons[i], contentDescription = labels[i])
                                    }
                                },
                                label = { Text(labels[i]) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Primary, selectedTextColor = Primary, indicatorColor = PrimaryLight
                                )
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (screen) {
                    "splash" -> SplashScreen(onStart = { openTab(if (user != null) "home" else "signup") })
                    "signup" -> SignUpScreen(db = db, onSignedUp = loggedIn, onLogin = { go("login") })
                    "login" -> LoginScreen(db = db, onLoggedIn = loggedIn, onSignUp = { openTab("signup") })
                    "home" -> HomeScreen(
                        userName = user?.name ?: "there",
                        wishlist = wishlist, onWish = toggleWish, onProduct = openProduct,
                        onCategory = { category = it; openTab("categories") },
                        onShopNow = { startTryOn(catalog[1]) },
                        onFilters = { category = "All"; filtersFirst = true; openTab("categories") }
                    )
                    "categories" -> CategoriesScreen(
                        category, { category = it }, wishlist, toggleWish, openProduct,
                        history = searches,
                        onSearched = { q -> user?.let { db.addSearch(it.email, q); searches = db.loadSearches(it.email) } },
                        onClearHistory = { user?.let { db.clearSearches(it.email) }; searches = emptyList() },
                        startWithFilters = filtersFirst
                    )
                    "saved" -> SavedScreen(wishlist, toggleWish, openProduct, onBack = { back() })
                    "detail" -> ProductDetailScreen(
                        product = selected,
                        wishlisted = wishlist.contains(selected.id),
                        onBack = { back() },
                        onWish = { toggleWish(selected) },
                        onAdd = addToCart,
                        onTry = { startTryOn(selected) },
                        yourSize = suggestedSize(selected, user)
                    )
                    "tryon" -> TryOnUploadScreen(
                        product = tryProduct,
                        onProduct = { tryProduct = it },
                        onBack = { if (stack.size > 1) back() else openTab("home") },
                        onLive = { liveOutfit = null; go("live") },
                        onPhoto = { photo = it; go("adjust") }
                    )
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
                    "adjust" -> AdjustPhotoScreen(
                        photo = photo!!,
                        onRetake = { back() },
                        onContinue = { cropped = it; colorName = null; tint = null; editBase = null; go("processing") }
                    )
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
                    "result" -> ResultScreen(
                        look = look!!, madeByAi = madeByAi, product = tryProduct,
                        onProduct = retry,
                        onBack = { back() },
                        onCompare = { go("compare") },
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
                    "cart" -> CartScreen(
                        cart, userName = user?.name ?: "", onBack = { openTab("home") },
                        email = user?.email ?: "", phone = user?.phone ?: "",
                        onOrderPlaced = { openOrder = it; go("order") },
                        addresses = addresses,
                        onAddAddress = { editingAddress = null; go("map") }
                    )
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
