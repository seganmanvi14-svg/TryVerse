package com.example.styletryon

import android.widget.Toast
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Try" + "Verse" logo. */
@Composable
fun Logo(size: TextUnit = 30.sp) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Brand)) { append("Try") }
            withStyle(SpanStyle(color = Accent)) { append("Verse") }
        },
        fontSize = size, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(26.dp)) {
        Text(text, fontSize = 16.sp)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null)
        }
    }
}

// ---------------------------------------------------------------------
//  Welcome
// ---------------------------------------------------------------------

@Composable
fun SplashScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF4F1FF), Cream))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        Logo(54.sp)
        Text("Shop  •  Try  •  Style  •  Be You", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Round photo of our model wearing a TryVerse top
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.72f).aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topStart = 200.dp, topEnd = 200.dp, bottomStart = 40.dp, bottomEnd = 40.dp))
            ) { DemoModelTryOn(catalog[0], Modifier.fillMaxSize()) }
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(
                    "Fashion\nFits\nYour\nVibe", fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic,
                    fontSize = 30.sp, color = Ink, lineHeight = 36.sp
                )
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Ink, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Primary))
            Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryLight))
            Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryLight))
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Get Started", Modifier.fillMaxWidth(), Icons.Default.ArrowForward, onStart)
    }
}

// ---------------------------------------------------------------------
//  Sign Up (saved in SQLite)
// ---------------------------------------------------------------------

@Composable
fun AuthField(value: String, onChange: (String) -> Unit, hint: String, icon: ImageVector, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Ink) },
        trailingIcon = if (password) {
            { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Show password") } }
        } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Color(0xFFE6E6F0)
        )
    )
}

@Composable
fun SignUpScreen(db: UserDatabase, onSignedUp: (User) -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Logo(46.sp)
        Text("Create your account\nto start shopping", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        AuthField(name, { name = it.take(40) }, "Full name", Icons.Default.PersonOutline)
        AuthField(email, { email = it.trim().take(120) }, "Email", Icons.Default.MailOutline, KeyboardType.Email)
        AuthField(phone, { phone = it.take(16) }, "Phone number", Icons.Default.PhoneAndroid, KeyboardType.Phone)
        AuthField(password, { password = it.take(64) }, "Password", Icons.Default.Lock, KeyboardType.Password, password = true)
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Sign Up", Modifier.fillMaxWidth()) {
            when {
                name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank() ->
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                !Validate.name(name) -> Toast.makeText(context, "Name must be 2–40 characters", Toast.LENGTH_SHORT).show()
                !Validate.email(email) -> Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show()
                !Validate.phone(phone.trim()) -> Toast.makeText(context, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
                !Validate.password(password) ->
                    Toast.makeText(context, "Password: 8+ characters with a letter and a number", Toast.LENGTH_LONG).show()
                !db.addUser(name.trim(), email, phone.trim(), password) ->
                    Toast.makeText(context, "This email is already registered. Please log in.", Toast.LENGTH_SHORT).show()
                else -> {
                    Toast.makeText(context, "Welcome to TryVerse, ${name.trim()}!", Toast.LENGTH_SHORT).show()
                    onSignedUp(User(name.trim(), email.lowercase(), phone.trim()))
                }
            }
        }
        SocialLogins()
        Row {
            Text("Already have an account? ", color = Muted)
            Text("Login", color = Primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onLogin() })
        }
    }
}

@Composable
fun SocialLogins() {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("  or continue with  ", color = Muted, fontSize = 12.sp)
        HorizontalDivider(Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        listOf("G" to null, "" to Icons.Default.PhoneIphone, "" to Icons.Default.MailOutline).forEach { (letter, icon) ->
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White).border(1.dp, Color(0xFFE6E6F0), CircleShape)
                    .clickable { Toast.makeText(context, "Coming soon — please sign up with email", Toast.LENGTH_SHORT).show() },
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = Ink)
                else Text(letter, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF4285F4))
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Logo(46.sp)
        Text("Welcome back!\nLog in to continue", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        AuthField(email, { email = it.trim().take(120) }, "Email", Icons.Default.MailOutline, KeyboardType.Email)
        AuthField(password, { password = it.take(64) }, "Password", Icons.Default.Lock, KeyboardType.Password, password = true)
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Login", Modifier.fillMaxWidth()) {
            val now = System.currentTimeMillis()
            if (now < lockedUntil) {
                Toast.makeText(context, "Too many attempts. Try again in ${(lockedUntil - now) / 1000 + 1} s", Toast.LENGTH_SHORT).show()
                return@PrimaryButton
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
        Row {
            Text("New to TryVerse? ", color = Muted)
            Text("Sign Up", color = Primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onSignUp() })
        }
    }
}
