// =====================================================================
// AddressScreens.kt = DELIVERY ADDRESSES ON A MAP (Google Maps).
// Screen 1 'My Addresses': the user's saved addresses (from SQLite) with add / edit / delete.
// Screen 2 'Add / Edit Address': a Google map with a pin fixed in the middle. The user moves the map
// (or taps 'my location'), the street address for the pin is filled in automatically (Geocoder),
// and Save stores the address + the pin's latitude / longitude in SQLite.
// =====================================================================
package com.example.styletryon

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

// =====================================================================
//  MAPS: delivery addresses
//  - My Addresses: list of the user's saved addresses (SQLite) with edit / delete
//  - Address on map: the user moves the map under a fixed pin (or taps "my location");
//    the street address is filled in from the pin (Geocoder), then saved.
//  Map: Google Maps SDK for Android (Maps Compose). The API key is read from
//  local.properties (MAPS_API_KEY=...), so it is never put on GitHub.
// =====================================================================

// Picks the icon for an address label: Home = house, College = school, Work = briefcase, others = pin
private fun iconFor(label: String): ImageVector = when (label) {
    "Home" -> Icons.Default.Home
    "College" -> Icons.Default.School
    "Work" -> Icons.Default.Work
    else -> Icons.Default.Place
}

// SCREEN 1: the list of saved addresses.
// The screen only SHOWS the list; MainActivity does the saving / deleting in SQLite (onAdd, onEdit, onDelete)
@Composable
fun AddressListScreen(
    addresses: List<SavedAddress>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (SavedAddress) -> Unit,
    onDelete: (SavedAddress) -> Unit
) {
    // The address the user wants to delete (null = no delete box showing)
    var deleting by remember { mutableStateOf<SavedAddress?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("My Addresses", onBack)
        // No addresses yet: show a message
        if (addresses.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOff, contentDescription = null, tint = Primary, modifier = Modifier.size(56.dp))
                Text("No saved addresses", fontWeight = FontWeight.Bold)
                Text("Add one on the map to get your orders delivered.", color = Muted)
            }
        } else {
            // LazyColumn = a scrolling list that only draws the rows on screen. One row per address
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(addresses, key = { it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Paper).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(PrimaryLight), contentAlignment = Alignment.Center) {
                            Icon(iconFor(a.label), contentDescription = null, tint = Primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(a.line, color = Muted, fontSize = 12.sp)
                        }
                        // Edit opens the map with this address; Delete asks first
                        IconButton(onClick = { onEdit(a) }) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Ink) }
                        IconButton(onClick = { deleting = a }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Opens the map to add a new address
        PrimaryButton("Add New Address", Modifier.fillMaxWidth(), Icons.Default.AddLocationAlt, onAdd)
    }

    // "Delete address?" box. Delete really deletes it (in SQLite), Cancel closes the box
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete address?") },
            text = { Text("${a.label}\n${a.line}") },
            confirmButton = { TextButton(onClick = { onDelete(a); deleting = null }) { Text("Delete", color = Rose) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// *** SCREEN 2: GOOGLE MAP ADDRESS PICKER ***
// [start] = the address being edited, or null for a new address
@Composable
fun AddressMapScreen(start: SavedAddress?, onBack: () -> Unit, onSave: (SavedAddress) -> Unit) {
    val context = LocalContext.current
    // scope = lets us run the map animation in the background (a coroutine)
    val scope = rememberCoroutineScope()
    // Where the map starts if we don't know the user's location yet (latitude, longitude of Mumbai)
    val mumbai = LatLng(19.0760, 72.8777)
    // What the user types / picks: label (Home / College / Work / Other), flat number, address line
    var label by remember { mutableStateOf(start?.label ?: "Home") }
    var house by remember { mutableStateOf("") }                     // flat / house number (optional)
    var line by remember { mutableStateOf(start?.line ?: "") }
    // True while we are asking Google for the street address of the pin
    var lookingUp by remember { mutableStateOf(false) }

    // The map's camera. The pin is always drawn in the middle, so the pin's place = the camera target.
    // *** THE MAP'S CAMERA (where the map is looking) ***
    // Starts at the address being edited, or Mumbai, zoom 17 (street level)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(if (start != null) LatLng(start.lat, start.lng) else mumbai, 17f)
    }

    // Fill in the street address for the pin, once the map has stopped moving
    // *** PIN -> STREET ADDRESS HERE ***
    // Runs every time the map starts or stops moving. When it STOPS, we look up the address for the pin
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving) return@LaunchedEffect
        // The pin is always in the middle of the map, so the pin's place = the camera's centre
        val center = camera.position.target
        // Editing and the pin hasn't moved: keep the saved address as it is
        val unchanged = start != null && abs(center.latitude - start.lat) < 1e-5 && abs(center.longitude - start.lng) < 1e-5
        if (unchanged) return@LaunchedEffect
        // Wait a moment so we don't look up the address while the user is still dragging
        delay(400)
        lookingUp = true
        // Ask the Geocoder for the address. If it fails, keep the old text or show the coordinates
        line = addressOf(context, center) ?: line.ifBlank { "Pinned location (%.5f, %.5f)".format(center.latitude, center.longitude) }
        lookingUp = false
    }

    // Moves the map to the phone's location (animated)
    fun goToMyLocation() {
        findMyLocation(context) { p ->
            if (p == null) Toast.makeText(context, "Couldn't find your location. Turn on location or move the map.", Toast.LENGTH_LONG).show()
            else scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(p, 17f)) }
        }
    }
    // Asks Android for the LOCATION PERMISSION. If allowed, move the map there; if not, show a message
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) goToMyLocation()
        else Toast.makeText(context, "Location permission denied. Move the map to your address.", Toast.LENGTH_SHORT).show()
    }
    // Already allowed: go to the location. Not yet: ask for permission first
    fun askForLocation() {
        if (hasLocationPermission(context)) goToMyLocation()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // New address: start at the user's location (asks permission once)
    LaunchedEffect(Unit) { if (start == null) askForLocation() }          // new address: start at the user's location

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) { TopBar(if (start == null) "Add Address" else "Edit Address", onBack) }

        // ---------------- Map with a fixed pin ----------------
        // TOP PART: the map with a pin in the middle
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // *** GOOGLE MAP IS SHOWN HERE ***
            // Uses our camera state, and hides Google's own zoom / location / toolbar buttons
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false)
            )
            Icon(
                Icons.Default.LocationOn, contentDescription = "Pin", tint = Rose,
                modifier = Modifier.align(Alignment.Center).offset(y = (-22).dp).size(46.dp)
            )
            Text(
                "Move the map to place the pin on your door",
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White, fontSize = 12.sp
            )
            // "My location" button in the bottom-right corner
            SmallFloatingActionButton(
                onClick = { askForLocation() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                containerColor = Paper
            ) { Icon(Icons.Default.MyLocation, contentDescription = "My location", tint = Primary) }
        }

        // ---------------- Address details ----------------
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Paper).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Rose, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                // Shows "Finding the address..." while the Geocoder is working
                Text(if (lookingUp) "Finding the address..." else "Deliver here", fontWeight = FontWeight.SemiBold, color = Ink)
            }
            // Optional flat / house number box
            OutlinedTextField(
                value = house, onValueChange = { house = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Flat / house no., building (optional)") }
            )
            // The address line (filled from the pin, the user can still edit it). Red if empty
            OutlinedTextField(
                value = line, onValueChange = { line = it.take(200) }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                label = { Text("Address") },
                isError = line.isBlank() && !lookingUp,
                supportingText = if (line.isBlank() && !lookingUp) { { Text("Move the pin or type your address") } } else null
            )
            // Chips to choose the label: Home, College, Work, Other
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "College", "Work", "Other").forEach { l ->
                    FilterChip(
                        selected = label == l, onClick = { label = l }, label = { Text(l) },
                        leadingIcon = { Icon(iconFor(l), contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            // *** ADDRESS IS SAVED HERE ***
            // Flat no. + address line + the pin's latitude / longitude are sent to MainActivity, which saves
            // them in SQLite
            PrimaryButton("Save Address", Modifier.fillMaxWidth()) {
                if (line.isBlank()) {
                    Toast.makeText(context, "Please add the address", Toast.LENGTH_SHORT).show()
                    return@PrimaryButton
                }
                val full = if (house.isBlank()) line.trim() else house.trim() + ", " + line.trim()
                val pin = camera.position.target
                onSave(SavedAddress(start?.id ?: 0L, label, full, pin.latitude, pin.longitude))
            }
        }
    }
}

// True if the user has allowed fine (GPS) or coarse (network) location
fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// *** PHONE'S LOCATION ***
// Uses the last known location if there is one, otherwise asks once for a fresh reading.
// onFound(null) if location is off or not allowed
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
fun findMyLocation(context: Context, onFound: (LatLng?) -> Unit) {
    if (!hasLocationPermission(context)) return onFound(null)
    try {
        // LocationManager = Android's location service (GPS / network)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true)
        // The newest last-known location from any provider
        val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null) return onFound(LatLng(last.latitude, last.longitude))
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
            ?: return onFound(null)
        // No saved location: ask for ONE new reading, onFound is called when it arrives
        manager.requestSingleUpdate(provider, object : LocationListener {
            override fun onLocationChanged(location: Location) = onFound(LatLng(location.latitude, location.longitude))
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }, Looper.getMainLooper())
    } catch (e: SecurityException) {
        onFound(null)
    }
}

// *** GEOCODER: latitude / longitude -> street address text. Runs in the background (Dispatchers.IO) because it uses the internet. Null if not found ***
@Suppress("DEPRECATION")
suspend fun addressOf(context: Context, point: LatLng): String? = withContext(Dispatchers.IO) {
    try {
        if (!Geocoder.isPresent()) null
        else Geocoder(context, Locale.getDefault()).getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()?.getAddressLine(0)
    } catch (e: Exception) {
        null
    }
}
