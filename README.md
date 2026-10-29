# TryVerse – Virtual Try-On Shopping App

TryVerse is an Android shopping app where you can **see clothes on yourself before you buy them**:
live with your phone's camera, on a photo of you, or on one of our sample models.
It also has a full shop: search and filters, wishlist, cart, test payments, order tracking,
returns / exchanges and delivery addresses on a map.

> College project (non-commercial). Payments run in **test mode only**: no real money is ever charged.

---

## Features

### Virtual try-on
- **Live camera try-on**: the garment follows your body in real time (shoulders, waist, hips, and sleeves that turn with your arms).
- **Photo try-on**: upload a photo of yourself (or pick a sample model), adjust it, and see the outfit fitted to your body.
- **Mix and match**: wear a top and a bottom together (live camera and photo try-on), then add the whole outfit to the cart.
- **Try another colour** and a side-by-side **compare** view.
- **Size suggestion**: enter your height once; a live camera body scan suggests your top size and jeans waist size.
- **Try-on history** and **saved outfits** for each user.
- **AI style tip** (optional, needs Firebase): Gemini writes a short styling suggestion.

### Shop
- 24 products in 7 categories, with **out-of-stock** products and sizes.
- **Smart search**: matches name, colour, type and description (for example "blue dress" or "black jeans").
- **Search history** per user, with a Clear option.
- **Filters**: price range, size, colour, rating, hide out-of-stock. **Sort** by popularity, price or rating.
- **Wishlist**, product page with an "on model" view and "Complete your look".

### Cart, payment and orders
- Cart with quantities, item selection and a coupon (`TRYVERSE10` = 10% off).
- Checkout: delivery address, delivery date and payment method.
- **Google Pay API (test mode)**. If Google Pay isn't available on the device, a **test checkout** opens instead. Cash on Delivery is also available.
- **Order details**: tracking timeline (Processing → Shipped → Delivered), payment ID, price summary, cancel order, buy again.
- **Return / exchange** within 7 days of delivery.

### Account
- **Sign up** with field-by-field validation, confirm password and a password strength bar; it checks that the email isn't already registered.
- **Login** checks that the account exists. 5 wrong passwords lock login for 30 seconds.
- **Forgot password** (email + mobile number) and **change password**.
- Stays logged in; every user's data (cart, wishlist, searches, addresses, looks, outfits, orders) is kept separately.
- **Delivery addresses on Google Maps**: move the pin or use your location; the street address is filled in automatically.
- Black (default) and light theme.

---

## Tech used

| Area | Technology |
|---|---|
| Language and UI | Kotlin, Jetpack Compose (Material 3) |
| Camera | CameraX |
| Body detection | Google ML Kit Pose Detection + Selfie Segmentation |
| Garment fitting | Our own fitting engine: `Canvas.drawBitmapMesh` with a 20 × 20 grid |
| Database | SQLite (users, cart, wishlist, searches, looks, addresses, outfits) |
| Passwords | PBKDF2 (SHA-256) hash with a random salt, never stored as plain text |
| Files | `orders.csv`, `session.txt`, try-on pictures in the app's private folder |
| Maps | Google Maps SDK for Android + Maps Compose, Android Geocoder |
| Payment | Google Pay API (test environment); Razorpay (optional, test key only) |
| AI (optional) | Firebase AI Logic (Gemini) with App Check |

---

## How to run

1. Clone the repo and open it in **Android Studio**.
2. In the project folder, open (or create) **`local.properties`** and add your Google Maps key:
   ```
   MAPS_API_KEY=your_google_maps_api_key
   ```
   `local.properties` is in `.gitignore`. **Never commit it.** Without a key, the map stays blank, but the rest of the app works.
3. Build and run:
   ```
   sh ./gradlew clean assembleDebug
   ```
   or press **Run** in Android Studio. Needs Android 8.0 (API 26) or newer.

**Optional:**
- **AI style tip:** add your Firebase project's `google-services.json` to the `app/` folder.
- **Razorpay test mode:** add `RAZORPAY_KEY=rzp_test_...` to `local.properties`.

### Test payment details

| Method | Use |
|---|---|
| Google Pay (test) | Any card shown in the Google Pay sheet; no money moves |
| Test checkout – UPI | `success@test` (pays) or `failure@test` (fails) |
| Test checkout – Card | `4111 1111 1111 1111`, any future expiry, any CVV |
| Coupon | `TRYVERSE10` |

On an **emulator**, Google Pay usually returns **error 412**, because the emulator's Play services is too old. The app then opens the test checkout automatically.

---

## Project structure

```
app/src/main/java/com/example/styletryon/
├── MainActivity.kt        App start, navigation, per-user data
├── AuthScreens.kt         Welcome, sign up, login, forgot password
├── UserDatabase.kt        SQLite database, password hashing, input checks, session
├── Data.kt                Products (catalogue) and cart item
├── ShopScreens.kt         Home, categories (search, filters), wishlist, product page
├── CartProfileScreens.kt  Cart, checkout, profile, change password
├── Payment.kt             Google Pay (test), test checkout, Razorpay
├── Orders.kt              Saving / reading orders (orders.csv)
├── OrderScreens.kt        Order details, tracking, cancel, return / exchange
├── AddressScreens.kt      Addresses on Google Maps
├── TryOnScreens.kt        Photo try-on, sample models, mix and match, compare
├── LiveTryOn.kt           Live camera try-on with ML Kit
├── GarmentFit.kt          The garment fitting engine and demo model
├── SizeAdvice.kt          Size suggestion from height + body scan
├── AiTryOn.kt             Photo try-on pipeline + optional Gemini style tip
├── HistoryScreen.kt       Try-on history
├── OutfitsScreen.kt       Saved outfits
└── Theme.kt               Colours, fonts, shapes, shared buttons
```

Every Kotlin file has comments explaining what each part does.

---

## Team

| Member | Name |
|---|---|
| Member 2 | Manvi Segan (N146) |
| Member 3 | Maanya Oberoi (N144) |

See the commit history for each member's work.

---

## Credits

- Fonts: **Gloock** and **Outfit** (SIL Open Font License), **TeX Gyre Chorus** (GUST Font License). Licences are in `font_licenses/`.
- Garment and model images are used only for this non-commercial college project.