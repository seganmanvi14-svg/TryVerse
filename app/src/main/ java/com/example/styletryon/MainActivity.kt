package com.example.styletryon

import android.graphics.Bitmap
import android.os.Bundle
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
    var selected by remember { mutableStateOf(catalog.first()) }       // product page
    var tryProduct by remember { mutableStateOf(catalog.first()) }     // garment being tried on
    var photo by remember { mutableStateOf<Bitmap?>(null) }            // photo picked by the user
    var cropped by remember { mutableStateOf<Bitmap?>(null) }          // photo after "Adjust Photo"
    var look by remember { mutableStateOf<Bitmap?>(null) }             // the try-on result
    var madeByAi by remember { mutableStateOf(false) }                 // AI picture or quick preview
    var editBase by remember { mutableStateOf<Bitmap?>(null) }         // AI result to recolour
    var colorName by remember { mutableStateOf<String?>(null) }        // "Try Another Color"
    var tint by remember { mutableStateOf<Color?>(null) }
    val cart = remember { mutableStateListOf<CartItem>() }
    val wishlist = remember { mutableStateListOf<Int>() }

    val toggleWish: (Product) -> Unit = { p -> if (wishlist.contains(p.id)) wishlist.remove(p.id) else wishlist.add(p.id) }
    val addToCart: (Product, String) -> Unit = { p, s -> cart.add(CartItem(p, s)) }
    val openProduct: (Product) -> Unit = { selected = it; go("detail") }
    val startTryOn: (Product) -> Unit = { tryProduct = it; colorName = null; tint = null; go("tryon") }
    val retry: (Product) -> Unit = { tryProduct = it; colorName = null; tint = null; editBase = null; go("processing") }

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
                                    BadgedBox(badge = { if (t == "cart" && cart.isNotEmpty()) Badge { Text("${cart.size}") } }) {
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
                    "signup" -> SignUpScreen(
                        db = db,
                        onSignedUp = { user = it; saveSession(context, it.email); openTab("home") },
                        onLogin = { go("login") }
                    )
                    "login" -> LoginScreen(
                        db = db,
                        onLoggedIn = { user = it; saveSession(context, it.email); openTab("home") },
                        onSignUp = { openTab("signup") }
                    )
                    "home" -> HomeScreen(
                        userName = user?.name ?: "there",
                        wishlist = wishlist, onWish = toggleWish, onProduct = openProduct,
                        onCategory = { category = it; openTab("categories") },
                        onShopNow = { startTryOn(catalog[1]) }
                    )
                    "categories" -> CategoriesScreen(category, { category = it }, wishlist, toggleWish, openProduct)
                    "saved" -> SavedScreen(wishlist, toggleWish, openProduct, onBack = { back() })
                    "detail" -> ProductDetailScreen(
                        product = selected,
                        wishlisted = wishlist.contains(selected.id),
                        onBack = { back() },
                        onWish = { toggleWish(selected) },
                        onAdd = addToCart,
                        onTry = { startTryOn(selected) }
                    )
                    "tryon" -> TryOnUploadScreen(
                        product = tryProduct,
                        onProduct = { tryProduct = it },
                        onBack = { if (stack.size > 1) back() else openTab("home") },
                        onLive = { go("live") },
                        onPhoto = { photo = it; go("adjust") }
                    )
                    "live" -> LiveTryOnScreen(
                        startProduct = tryProduct,
                        onClose = { back() },
                        onAddToCart = addToCart
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
                        onDone = { picture, ai -> look = picture; madeByAi = ai; showResult() },
                        onFail = { back() }
                    )
                    "result" -> ResultScreen(
                        look = look!!, madeByAi = madeByAi, product = tryProduct,
                        onProduct = retry,
                        onBack = { back() },
                        onCompare = { go("compare") },
                        onAddToCart = { addToCart(tryProduct, tryProduct.sizes[2]); go("complete") }
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
                        onAdd = { addToCart(it, it.sizes[2]) },
                        onBack = { back() },
                        onCart = { openTab("cart") }
                    )
                    "cart" -> CartScreen(cart, userName = user?.name ?: "", onBack = { openTab("home") })
                    else -> ProfileScreen(
                        db = db,
                        user = user,
                        wishlistCount = wishlist.size,
                        onSaved = { go("saved") },
                        onUserChanged = { user = it },
                        onLogout = { clearSession(context); user = null; openTab("login") }
                    )
                }
            }
        }
    }
}
