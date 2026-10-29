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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File
import java.util.Locale
import kotlin.math.abs

// =====================================================================
//  MAPS: delivery addresses
//  - My Addresses: list of the user's saved addresses (SQLite) with edit / delete
//  - Address on map: the user moves the map under a fixed pin (or taps "my location");
//    the street address is filled in from the pin (Geocoder), then saved.
//  Map: OpenStreetMap through the osmdroid library (free, no API key needed).
// =====================================================================

private fun iconFor(label: String): ImageVector = when (label) {
    "Home" -> Icons.Default.Home
    "College" -> Icons.Default.School
    "Work" -> Icons.Default.Work
    else -> Icons.Default.Place
}

@Composable
fun AddressListScreen(
    addresses: List<SavedAddress>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (SavedAddress) -> Unit,
    onDelete: (SavedAddress) -> Unit
) {
    var deleting by remember { mutableStateOf<SavedAddress?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("My Addresses", onBack)
        if (addresses.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOff, contentDescription = null, tint = Primary, modifier = Modifier.size(56.dp))
                Text("No saved addresses", fontWeight = FontWeight.Bold)
                Text("Add one on the map to get your orders delivered.", color = Muted)
            }
        } else {
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
                        IconButton(onClick = { onEdit(a) }) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Ink) }
                        IconButton(onClick = { deleting = a }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add New Address", Modifier.fillMaxWidth(), Icons.Default.AddLocationAlt, onAdd)
    }

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

/** Opens the map at [start] (editing) or at the user's location (new address). */
@Composable
fun AddressMapScreen(start: SavedAddress?, onBack: () -> Unit, onSave: (SavedAddress) -> Unit) {
    val context = LocalContext.current
    val mumbai = GeoPoint(19.0760, 72.8777)
    var center by remember { mutableStateOf(if (start != null) GeoPoint(start.lat, start.lng) else mumbai) }
    var label by remember { mutableStateOf(start?.label ?: "Home") }
    var house by remember { mutableStateOf("") }                     // flat / house number (optional)
    var line by remember { mutableStateOf(start?.line ?: "") }
    var lookingUp by remember { mutableStateOf(false) }

    // osmdroid set-up: name the app for the map server and keep map tiles in the app's private cache
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)                                  // pinch to zoom
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            controller.setCenter(center)
        }
    }

    // Whenever the map moves, the pin (always in the middle) is at the map's centre
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                center = GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
                return false
            }
            override fun onZoom(event: ZoomEvent?): Boolean = false
        }
        mapView.addMapListener(listener)
        mapView.onResume()
        onDispose {
            mapView.removeMapListener(listener)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Fill in the street address for the pin, once the map has stopped moving
    LaunchedEffect(center) {
        val unchanged = start != null && abs(center.latitude - start.lat) < 1e-5 && abs(center.longitude - start.lng) < 1e-5
        if (unchanged) return@LaunchedEffect
        delay(700)
        lookingUp = true
        line = addressOf(context, center) ?: line.ifBlank { "Pinned location (%.5f, %.5f)".format(center.latitude, center.longitude) }
        lookingUp = false
    }

    fun goToMyLocation() {
        findMyLocation(context) { p ->
            if (p == null) Toast.makeText(context, "Couldn't find your location. Turn on location or move the map.", Toast.LENGTH_LONG).show()
            else mapView.controller.animateTo(p)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) goToMyLocation()
        else Toast.makeText(context, "Location permission denied. Move the map to your address.", Toast.LENGTH_SHORT).show()
    }
    fun askForLocation() {
        if (hasLocationPermission(context)) goToMyLocation()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) { if (start == null) askForLocation() }          // new address: start at the user's location

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) { TopBar(if (start == null) "Add Address" else "Edit Address", onBack) }

        // ---------------- Map with a fixed pin ----------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
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
            SmallFloatingActionButton(
                onClick = { askForLocation() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                containerColor = Paper
            ) { Icon(Icons.Default.MyLocation, contentDescription = "My location", tint = Primary) }
            Text(
                "© OpenStreetMap contributors", fontSize = 10.sp, color = Ink,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp)
            )
        }

        // ---------------- Address details ----------------
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Paper).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Rose, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (lookingUp) "Finding the address..." else "Deliver here", fontWeight = FontWeight.SemiBold, color = Ink)
            }
            OutlinedTextField(
                value = house, onValueChange = { house = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Flat / house no., building (optional)") }
            )
            OutlinedTextField(
                value = line, onValueChange = { line = it.take(200) }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                label = { Text("Address") },
                isError = line.isBlank() && !lookingUp,
                supportingText = if (line.isBlank() && !lookingUp) { { Text("Move the pin or type your address") } } else null
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "College", "Work", "Other").forEach { l ->
                    FilterChip(
                        selected = label == l, onClick = { label = l }, label = { Text(l) },
                        leadingIcon = { Icon(iconFor(l), contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            PrimaryButton("Save Address", Modifier.fillMaxWidth()) {
                if (line.isBlank()) {
                    Toast.makeText(context, "Please add the address", Toast.LENGTH_SHORT).show()
                    return@PrimaryButton
                }
                val full = if (house.isBlank()) line.trim() else house.trim() + ", " + line.trim()
                onSave(SavedAddress(start?.id ?: 0L, label, full, center.latitude, center.longitude))
            }
        }
    }
}

fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** The phone's location: the last known one, or else one fresh reading. onFound(null) if it isn't available. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
fun findMyLocation(context: Context, onFound: (GeoPoint?) -> Unit) {
    if (!hasLocationPermission(context)) return onFound(null)
    try {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true)
        val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null) return onFound(GeoPoint(last.latitude, last.longitude))
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
            ?: return onFound(null)
        manager.requestSingleUpdate(provider, object : LocationListener {
            override fun onLocationChanged(location: Location) = onFound(GeoPoint(location.latitude, location.longitude))
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }, Looper.getMainLooper())
    } catch (e: SecurityException) {
        onFound(null)
    }
}

/** Street address for a point on the map (Android Geocoder, needs internet). Null if not found. */
@Suppress("DEPRECATION")
suspend fun addressOf(context: Context, point: GeoPoint): String? = withContext(Dispatchers.IO) {
    try {
        if (!Geocoder.isPresent()) null
        else Geocoder(context, Locale.getDefault()).getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()?.getAddressLine(0)
    } catch (e: Exception) {
        null
    }
}