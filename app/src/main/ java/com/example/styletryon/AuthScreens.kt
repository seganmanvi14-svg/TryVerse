// =====================================================================
// AuthScreens.kt = WELCOME, SIGN UP, LOGIN and FORGOT PASSWORD screens.
// Sign Up: checks every field -> checks the DATABASE that the email is not already used -> saves the user.
// Login: checks the DATABASE that the account exists -> checks the password -> opens Home.
// The database is SQLite (UserDatabase.kt). Passwords are never saved as plain text, only as a hash.
// =====================================================================
package com.example.styletryon

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
//  WELCOME, SIGN UP AND LOGIN (Noir design)
// =====================================================================

// The TRYVERSE logo text. In the black theme the letters get a champagne (gold) gradient
@Composable
fun Logo(size: TextUnit = 30.sp, color: Color = Ink) {
    val gold = ThemeState.dark && color == Ink
    Text(
        "TRYVERSE", fontSize = size, fontFamily = Display, maxLines = 1,
        letterSpacing = (size.value * 0.17f).sp,
        color = if (gold) Color.Unspecified else color,
        style = if (gold) goldText() else TextStyle.Default
    )
}

// Old name for the main button, kept so older screens still work (it just uses ZaraButton)
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    ZaraButton(text, modifier, icon = icon, onClick = onClick)
}

// A line like "Already have an account?  Sign In" where the second part is clickable
@Composable
fun TextLink(question: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(question, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.width(6.dp))
        Text(action, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClick() })
    }
}

// Small L-shaped corner lines (like a camera viewfinder), drawn on the welcome photo
@Composable
private fun CornerMark(modifier: Modifier, top: Boolean, left: Boolean) {
    Box(
        modifier.size(18.dp).drawBehind {
            val c = Gold.copy(alpha = 0.8f)
            val y = if (top) 0f else size.height
            val x = if (left) 0f else size.width
            drawLine(c, Offset(x, y), Offset(if (left) size.width else 0f, y), strokeWidth = 2f)
            drawLine(c, Offset(x, y), Offset(x, if (top) size.height else 0f), strokeWidth = 2f)
        }
    )
}

// ---------------------------------------------------------------------
//  Welcome (front page)
// ---------------------------------------------------------------------

// *** WELCOME SCREEN (first screen when not logged in) ***
// Full-screen model photo + logo + 'Get Started' (sign up) and 'Log In' buttons
@Composable
fun SplashScreen(onStart: () -> Unit, onLogin: () -> Unit = onStart) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Full-screen campaign photo
        Image(
            painterResource(R.drawable.sample_model_2), contentDescription = null,
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()
        )
        // Moody shading: dark top and edges, black at the bottom
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0xA6000000), 0.28f to Color(0x33000000), 0.5f to Color(0x22000000), 0.78f to Color(0xF5000000))
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)))))

        CornerMark(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 52.dp), top = true, left = true)
        CornerMark(Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 52.dp), top = true, left = false)

        // Wordmark
        Column(Modifier.align(Alignment.TopCenter).padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(40.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(26.dp).height(1.dp).background(Gold))
                Text("  VIRTUAL FITTING ROOM  ", color = Gold, fontSize = 10.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.width(26.dp).height(1.dp).background(Gold))
            }
        }

        // SHOP / TRY / STYLE / BE YOU
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 120.dp), horizontalAlignment = Alignment.End) {
            listOf("SHOP", "TRY", "STYLE").forEach {
                Text(it, color = Color(0xFFF4EEE2), fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            }
            Text("BE YOU", color = Gold, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        }

        // Chips over the photo
        Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassChip("AI Try-On ready")
            GlassChip("AW 26 Collection")
        }

        // Glass welcome panel
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp).navigationBarsPadding()
                .clip(RoundedCornerShape(26.dp)).background(Color(0xB80E0D0B))
                .border(1.dp, Gold.copy(alpha = 0.28f), RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(22.dp).height(3.dp).clip(Pill).background(Gold))
                Box(Modifier.width(8.dp).height(3.dp).clip(Pill).background(Color(0xFF555555)))
                Box(Modifier.width(8.dp).height(3.dp).clip(Pill).background(Color(0xFF555555)))
            }
            Spacer(Modifier.height(12.dp))
            DisplayTitle("Fashion that fits", size = 28.sp, color = Color(0xFFF4EEE2))
            Row(verticalAlignment = Alignment.Bottom) {
                DisplayTitle("your ", size = 28.sp, color = Color(0xFFF4EEE2))
                ScriptWord("world.", size = 36.sp, color = Gold)
            }
            Text(
                "See every outfit on you before you buy — live camera or AI photo.",
                color = Color(0xFF9B958B), fontSize = 12.sp, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Get Started -> Sign Up screen.  Log In -> Login screen
                ZaraButton("Get Started", Modifier.weight(1.4f), icon = Icons.Rounded.ArrowForward, onClick = onStart)
                ZaraOutlinedButton("Log In", Modifier.weight(1f), onClick = onLogin)
            }
            Spacer(Modifier.height(14.dp))
            Hairline()
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12K+" to "LOOKS TRIED", "4.8★" to "RATING", "96%" to "FIT ACCURACY").forEach { (value, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, color = Gold, fontFamily = Display, fontSize = 19.sp)
                        Text(label, color = Color(0xFF9B958B), fontSize = 8.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Sign Up (saved in SQLite)
// ---------------------------------------------------------------------

// One input box with its label above it. Used for name, email, phone and passwords.
// password = true hides the text as dots and adds an eye button to show it. error = red message under the box
@Composable
fun AuthField(
    value: String, onChange: (String) -> Unit, hint: String, icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, error: String? = null
) {
    // Is the password shown? (eye button)
    var visible by remember { mutableStateOf(false) }
    Column {
        Text(hint, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = if (password) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Show password")
                    }
                }
            } else null,
            // Shows dots instead of the password letters
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            singleLine = true, shape = FieldShape, isError = error != null,
            colors = underlineFieldColors()
        )
        // *** RED ERROR MESSAGE UNDER THE BOX ***
        if (error != null) {
            Text("⚠  $error", color = Rose, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// Password strength 0-3: one point each for 8+ characters, letters AND numbers, 12+ characters or a symbol
fun passwordStrength(p: String): Int {
    if (p.isEmpty()) return 0
    return listOf(p.length >= 8, p.any { it.isLetter() } && p.any { it.isDigit() }, p.length >= 12 || p.any { !it.isLetterOrDigit() }).count { it }
}

// The bar under the password box: Weak / Good / Strong (the bar fills 1/3, 2/3 or all of the way)
@Composable
fun StrengthBar(password: String) {
    val strength = passwordStrength(password)
    if (password.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(3.dp).clip(Pill).background(Line)) {
            Box(Modifier.fillMaxWidth(maxOf(strength, 1) / 3f).fillMaxHeight().clip(Pill).background(GoldSweep))
        }
        Spacer(Modifier.width(8.dp))
        Text(listOf("Weak", "Weak", "Good", "Strong")[strength], color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// *** SIGN UP SCREEN ***
// db = our SQLite database. onSignedUp = what to do after the account is made (MainActivity opens Home)
@Composable
fun SignUpScreen(db: UserDatabase, onSignedUp: (User) -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    // What the user has typed in each box. remember + mutableStateOf = when it changes, the screen redraws
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }      // show every error after Sign Up is pressed
    var takenEmail by remember { mutableStateOf("") }        // email that is already registered in the database

    // Error for each field (null = OK). Shown once the field has text, or after Sign Up is pressed.
    // Show a field's error only after the user starts typing in it, or after they press Sign Up
    fun show(value: String) = submitted || value.isNotEmpty()
    // NAME CHECK: not empty, 2-40 letters, no numbers or symbols (null = no error)
    val nameError = when {
        !show(name) -> null
        name.isBlank() -> "Please enter your name"
        !Validate.name(name) -> "Use 2–40 letters only (no numbers or symbols)"
        else -> null
    }
    // EMAIL CHECK: not empty, looks like name@gmail.com, and not already registered
    val emailError = when {
        !show(email) -> null
        email.isBlank() -> "Please enter your email"
        !Validate.email(email) -> "Enter a valid email, e.g. name@gmail.com"
        email.lowercase() == takenEmail -> "This email is already registered. Please log in."
        else -> null
    }
    // PHONE CHECK: a valid 10-digit Indian mobile number
    val phoneError = when {
        !show(phone) -> null
        phone.isBlank() -> "Please enter your mobile number"
        !Validate.phone(phone.trim()) -> "Enter a valid 10-digit mobile number"
        else -> null
    }
    // PASSWORD CHECK: at least 8 characters, with at least one letter and one number
    val passwordError = when {
        !show(password) -> null
        password.length < 8 -> "At least 8 characters"
        password.none { it.isLetter() } -> "Add at least one letter"
        password.none { it.isDigit() } -> "Add at least one number"
        else -> null
    }
    // *** CONFIRM PASSWORD CHECK: the two passwords must be the same ***
    val confirmError = when {
        !show(confirm) -> null
        confirm != password -> "Passwords do not match"
        else -> null
    }

    // The page: logo, title, the boxes, terms checkbox, Sign Up button. verticalScroll = the page can scroll
    Column(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2A2620), Cream), radius = 1400f))
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Logo(18.sp) }
        Spacer(Modifier.height(4.dp))
        Column {
            DisplayTitle("Create", size = 34.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                DisplayTitle("Your ", size = 34.sp)
                ScriptWord("Account", size = 40.sp)
            }
            Text("Let's start your personalised shopping experience.", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
        }
        // The input boxes. take(40) etc. = stop typing after that many characters
        AuthField(name, { name = it.take(40) }, "Full Name", Icons.Default.PersonOutline, error = nameError)
        AuthField(email, { email = it.trim().take(120) }, "Email Address", Icons.Default.MailOutline, KeyboardType.Email, error = emailError)
        AuthField(phone, { phone = it.take(16) }, "Mobile Number", Icons.Default.PhoneAndroid, KeyboardType.Phone, error = phoneError)
        Column {
            AuthField(password, { password = it.take(64) }, "Password", Icons.Default.Lock, KeyboardType.Password, password = true, error = passwordError)
            StrengthBar(password)
        }
        // Confirm password box
        AuthField(confirm, { confirm = it.take(64) }, "Confirm Password", Icons.Default.Lock, KeyboardType.Password, password = true, error = confirmError)
        // Terms & Conditions checkbox (the text turns red if Sign Up is pressed without ticking it)
        Row(Modifier.clickable { agreed = !agreed }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("I agree to the ", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
            Text("Terms & Conditions", color = if (submitted && !agreed) Rose else Primary, fontSize = 12.sp)
        }
        // *** SIGN UP BUTTON ***
        ZaraButton("Sign Up", Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowForward) {
            // STEP 1: show all the red errors
            submitted = true
            // STEP 2: are ALL the fields correct?
            val allValid = Validate.name(name) && Validate.email(email) && Validate.phone(phone.trim()) &&
                Validate.password(password) && confirm == password
            when {
                // Some field is wrong -> ask the user to fix the red fields
                !allValid -> Toast.makeText(context, "Please fix the fields in red", Toast.LENGTH_SHORT).show()
                // Terms not ticked -> ask the user to accept them
                !agreed -> Toast.makeText(context, "Please accept the Terms & Conditions", Toast.LENGTH_SHORT).show()
                // The database already has an account with this email
                // *** STEP 3: CHECK THE DATABASE + SAVE THE NEW USER ***
                // isRegistered = is this email already in the users table? addUser = save the user (it also
                // refuses an email that exists).
                // If the email is taken, show 'already registered' under the email box
                db.isRegistered(email) || !db.addUser(name.trim(), email, phone.trim(), password) -> {
                    takenEmail = email.lowercase()
                    Toast.makeText(context, "This email is already registered. Please log in.", Toast.LENGTH_SHORT).show()
                }
                // STEP 4: account made -> say welcome and go to Home (logged in)
                else -> {
                    Toast.makeText(context, "Welcome to TryVerse, ${name.trim()}!", Toast.LENGTH_SHORT).show()
                    onSignedUp(db.getUser(email) ?: User(name.trim(), email.lowercase(), phone.trim()))
                }
            }
        }
        // Link to the Login screen
        TextLink("Already have an account?", "Sign In", onLogin)
    }
}

// ---------------------------------------------------------------------
//  Login (checked against SQLite)
// ---------------------------------------------------------------------

// *** LOGIN SCREEN ***
@Composable
fun LoginScreen(db: UserDatabase, onLoggedIn: (User) -> Unit, onSignUp: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Wrong tries count, and the time until which login is locked.
    // rememberSaveable = these values survive even if the phone is rotated
    var failedAttempts by rememberSaveable { mutableIntStateOf(0) }
    var lockedUntil by rememberSaveable { mutableLongStateOf(0L) }
    // true = show the Forgot password box
    var forgot by remember { mutableStateOf(false) }
    var noAccount by remember { mutableStateOf("") }        // email that has no account in the database
    // Red message under the email box: invalid email, or no account with this email
    val emailError = when {
        email.isNotEmpty() && !Validate.email(email) -> "Enter a valid email, e.g. name@gmail.com"
        email.isNotEmpty() && email.lowercase() == noAccount -> "No account with this email. Please sign up first."
        else -> null
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2A2620), Cream), radius = 1400f))
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Logo(18.sp) }
        Spacer(Modifier.height(24.dp))
        Column {
            DisplayTitle("Welcome", size = 34.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                DisplayTitle("Back", size = 34.sp)
                ScriptWord(" to you", size = 40.sp)
            }
            Text("Log in to see your bag, wishlist and try-on looks.", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
        }
        // Email and password boxes
        AuthField(email, { email = it.trim().take(120) }, "Email Address", Icons.Default.MailOutline, KeyboardType.Email, error = emailError)
        AuthField(password, { password = it.take(64) }, "Password", Icons.Default.Lock, KeyboardType.Password, password = true)
        Text(
            "Forgot password?", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.End).clickable { forgot = true }.padding(4.dp)
        )
        // *** LOG IN BUTTON ***
        ZaraButton("Log In", Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowForward) {
            // STEP 1: locked after too many wrong tries? Then wait
            val now = System.currentTimeMillis()
            if (now < lockedUntil) {
                Toast.makeText(context, "Too many attempts. Try again in ${(lockedUntil - now) / 1000 + 1} s", Toast.LENGTH_SHORT).show()
                return@ZaraButton
            }
            // STEP 2: both boxes must be filled in correctly
            if (!Validate.email(email) || password.isEmpty()) {
                Toast.makeText(context, "Enter your email and password", Toast.LENGTH_SHORT).show()
                return@ZaraButton
            }
            // Check the database: has this person made an account?
            // *** STEP 3: DOES THIS ACCOUNT EXIST IN THE DATABASE? If not, ask the user to sign up first ***
            if (!db.isRegistered(email)) {
                noAccount = email.lowercase()
                Toast.makeText(context, "No account found. Please sign up first.", Toast.LENGTH_SHORT).show()
                return@ZaraButton
            }
            // *** STEP 4: PASSWORD IS CHECKED HERE (UserDatabase.login compares the hash) ***
            // Returns the user if the password is right, or null if it is wrong
            val user = db.login(email, password)
            if (user != null) {
                // Right password: reset the wrong-try count and open Home
                failedAttempts = 0
                onLoggedIn(user)
            } else {
                // Wrong password: count it. After 5 wrong tries, lock login for 30 seconds (stops guessing)
                failedAttempts++
                if (failedAttempts >= 5) {
                    lockedUntil = now + 30_000          // lock login for 30 seconds after 5 wrong tries
                    failedAttempts = 0
                    Toast.makeText(context, "Too many wrong attempts. Locked for 30 seconds.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Wrong password. ${5 - failedAttempts} tries left", Toast.LENGTH_SHORT).show()
                }
            }
        }
        TextLink("New to TryVerse?", "Sign Up", onSignUp)
    }
    // Shows the Forgot password box when 'Forgot password?' is tapped
    if (forgot) ForgotPasswordDialog(db, startEmail = email, onDone = { forgot = false })
}

/**
 * Forgot password: the user proves it is their account with the email + mobile number
 * used at sign up, then sets a new password (checked like on Sign Up).
 */
@Composable
fun ForgotPasswordDialog(db: UserDatabase, startEmail: String, onDone: () -> Unit) {
    val context = LocalContext.current
    // The box starts with the email already typed on the login screen
    var email by remember { mutableStateOf(startEmail) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    // Wrong tries (after 5 the box closes) and whether email + phone didn't match any account
    var wrongTries by remember { mutableIntStateOf(0) }
    var noMatch by remember { mutableStateOf(false) }

    fun show(value: String) = tried || value.isNotEmpty()
    val emailError = if (show(email) && !Validate.email(email)) "Enter a valid email" else null
    val phoneError = when {
        show(phone) && !Validate.phone(phone) -> "Enter your 10-digit mobile number"
        noMatch -> "Email and mobile number don't match any account"
        else -> null
    }
    val passwordError = if (show(password) && !Validate.password(password)) "8+ characters with a letter and a number" else null
    val confirmError = if (show(confirm) && confirm != password) "Passwords do not match" else null

    // *** FORGOT PASSWORD BOX ***: email, mobile number, new password, confirm new password
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Paper,
        title = { Text("Reset Password", color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the email and mobile number you signed up with.", color = Muted, fontSize = 12.sp)
                AuthField(email, { email = it.trim().take(120); noMatch = false }, "Email Address", Icons.Default.MailOutline, KeyboardType.Email, error = emailError)
                AuthField(phone, { phone = it.take(16); noMatch = false }, "Mobile Number", Icons.Default.PhoneAndroid, KeyboardType.Phone, error = phoneError)
                Column {
                    AuthField(password, { password = it.take(64) }, "New Password", Icons.Default.Lock, KeyboardType.Password, password = true, error = passwordError)
                    StrengthBar(password)
                }
                AuthField(confirm, { confirm = it.take(64) }, "Confirm New Password", Icons.Default.Lock, KeyboardType.Password, password = true, error = confirmError)
            }
        },
        confirmButton = {
            // RESET button
            TextButton(onClick = {
                tried = true
                // Too many wrong tries: close the box
                if (wrongTries >= 5) {
                    Toast.makeText(context, "Too many attempts. Please try again later.", Toast.LENGTH_LONG).show()
                    onDone()
                    return@TextButton
                }
                // Every box must be correct and both new passwords the same
                if (!Validate.email(email) || !Validate.phone(phone) || !Validate.password(password) || confirm != password) return@TextButton
                // *** DATABASE CHECK: email AND mobile number must match the same account, then the new password
                // is saved ***
                if (db.resetPassword(email, phone, password)) {
                    Toast.makeText(context, "Password reset. Please log in with your new password.", Toast.LENGTH_LONG).show()
                    onDone()
                } else {
                    wrongTries++
                    noMatch = true
                }
            }) { Text("RESET", color = Primary, letterSpacing = 1.sp) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("CANCEL", color = Muted, letterSpacing = 1.sp) } }
    )
}
