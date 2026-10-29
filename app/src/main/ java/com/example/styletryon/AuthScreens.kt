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

/** TRYVERSE wordmark: Gloock capitals with wide spacing, champagne gradient. */
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

/** Kept so older screens still work: the champagne pill button. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    ZaraButton(text, modifier, icon = icon, onClick = onClick)
}

/** "Already have an account?  Sign In" */
@Composable
fun TextLink(question: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(question, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.width(6.dp))
        Text(action, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClick() })
    }
}

/** Thin L-shaped corner mark, like a camera viewfinder. */
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

/** Label above a rounded glass field. [icon] is kept for older calls. */
@Composable
fun AuthField(
    value: String, onChange: (String) -> Unit, hint: String, icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, error: String? = null
) {
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
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            singleLine = true, shape = FieldShape, isError = error != null,
            colors = underlineFieldColors()
        )
        if (error != null) {
            Text("⚠  $error", color = Rose, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 0–3: how strong a password is (length, letters, numbers, symbols / 12+ characters). */
fun passwordStrength(p: String): Int {
    if (p.isEmpty()) return 0
    return listOf(p.length >= 8, p.any { it.isLetter() } && p.any { it.isDigit() }, p.length >= 12 || p.any { !it.isLetterOrDigit() }).count { it }
}

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

@Composable
fun SignUpScreen(db: UserDatabase, onSignedUp: (User) -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    val phoneError = if (phone.isNotBlank() && !Validate.phone(phone.trim())) "Enter a valid mobile number" else null

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
        AuthField(name, { name = it.take(40) }, "Full Name", Icons.Default.PersonOutline)
        AuthField(email, { email = it.trim().take(120) }, "Email Address", Icons.Default.MailOutline, KeyboardType.Email)
        AuthField(phone, { phone = it.take(16) }, "Mobile Number", Icons.Default.PhoneAndroid, KeyboardType.Phone, error = phoneError)
        Column {
            AuthField(password, { password = it.take(64) }, "Password", Icons.Default.Lock, KeyboardType.Password, password = true)
            StrengthBar(password)
        }
        Row(Modifier.clickable { agreed = !agreed }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("I agree to the ", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
            Text("Terms & Conditions", color = Primary, fontSize = 12.sp)
        }
        ZaraButton("Sign Up", Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowForward) {
            when {
                name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank() ->
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                !Validate.name(name) -> Toast.makeText(context, "Name must be 2–40 characters", Toast.LENGTH_SHORT).show()
                !Validate.email(email) -> Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show()
                !Validate.phone(phone.trim()) -> Toast.makeText(context, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
                !Validate.password(password) ->
                    Toast.makeText(context, "Password: 8+ characters with a letter and a number", Toast.LENGTH_LONG).show()
                !agreed -> Toast.makeText(context, "Please accept the Terms & Conditions", Toast.LENGTH_SHORT).show()
                !db.addUser(name.trim(), email, phone.trim(), password) ->
                    Toast.makeText(context, "This email is already registered. Please log in.", Toast.LENGTH_SHORT).show()
                else -> {
                    Toast.makeText(context, "Welcome to TryVerse, ${name.trim()}!", Toast.LENGTH_SHORT).show()
                    onSignedUp(User(name.trim(), email.lowercase(), phone.trim()))
                }
            }
        }
        SocialLogins()
        TextLink("Already have an account?", "Sign In", onLogin)
    }
}

/** "OR" + round glass buttons for Google / phone / email (coming soon). */
@Composable
fun SocialLogins() {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Hairline(Modifier.weight(1f))
        Text("  OR  ", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        Hairline(Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
        listOf<Pair<String, ImageVector?>>("G" to null, "" to Icons.Rounded.PhoneIphone, "" to Icons.Rounded.MailOutline).forEach { (letter, icon) ->
            Box(
                Modifier.size(44.dp).glass(CircleShape)
                    .clickable { Toast.makeText(context, "Coming soon — please sign up with email", Toast.LENGTH_SHORT).show() },
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                else Text(letter, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Login (checked against SQLite)
// ---------------------------------------------------------------------

@Composable
fun LoginScreen(db: UserDatabase, onLoggedIn: (User) -> Unit, onSignUp: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var failedAttempts by rememberSaveable { mutableIntStateOf(0) }
    var lockedUntil by rememberSaveable { mutableLongStateOf(0L) }

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
        AuthField(email, { email = it.trim().take(120) }, "Email Address", Icons.Default.MailOutline, KeyboardType.Email)
        AuthField(password, { password = it.take(64) }, "Password", Icons.Default.Lock, KeyboardType.Password, password = true)
        Spacer(Modifier.height(4.dp))
        ZaraButton("Log In", Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowForward) {
            val now = System.currentTimeMillis()
            if (now < lockedUntil) {
                Toast.makeText(context, "Too many attempts. Try again in ${(lockedUntil - now) / 1000 + 1} s", Toast.LENGTH_SHORT).show()
                return@ZaraButton
            }
            val user = if (Validate.email(email)) db.login(email, password) else null
            if (user != null) {
                failedAttempts = 0
                onLoggedIn(user)
            } else {
                failedAttempts++
                if (failedAttempts >= 5) {
                    lockedUntil = now + 30_000          // lock login for 30 seconds after 5 wrong tries
                    failedAttempts = 0
                    Toast.makeText(context, "Too many wrong attempts. Locked for 30 seconds.", Toast.LENGTH_LONG).show()
                } else {
                    // Same message whether the email or the password is wrong, so accounts can't be guessed
                    Toast.makeText(context, "Wrong email or password", Toast.LENGTH_SHORT).show()
                }
            }
        }
        SocialLogins()
        TextLink("New to TryVerse?", "Sign Up", onSignUp)
    }
}
