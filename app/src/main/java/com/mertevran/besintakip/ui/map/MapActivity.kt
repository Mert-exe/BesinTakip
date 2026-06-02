package com.mertevran.besintakip.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mertevran.besintakip.BuildConfig
import com.mertevran.besintakip.R
import com.mertevran.besintakip.databinding.ActivityMapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.preference.PreferenceManager

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var lastSearchLocation: GeoPoint? = null
    private val TAG = "MapActivity"
    private val SEARCH_THRESHOLD_METERS = 500.0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            enableMyLocation()
        } else {
            Toast.makeText(this, "Konum izni reddedildi. Harita özellikleri kısıtlı olacaktır.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // osmdroid configuration
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        
        val osmCache = File(cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = osmCache

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationIconTint(ContextCompat.getColor(this, R.color.primary))

        supportActionBar?.setDisplayShowTitleEnabled(false) // If you want title from code
        title = "Yakındaki Parklar"

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        checkPermissions()

        binding.fabMyLocation.setOnClickListener {
            checkPermissions()
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)

        // Add My Location Overlay
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
        myLocationOverlay.enableMyLocation()
        binding.mapView.overlays.add(myLocationOverlay)
    }

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
            }
            else -> {
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, 
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            myLocationOverlay.enableMyLocation()
            myLocationOverlay.enableFollowLocation()
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let {
                    val userPoint = GeoPoint(it.latitude, it.longitude)
                    Log.d(TAG, "Current Location: ${it.latitude}, ${it.longitude}")
                    binding.mapView.controller.animateTo(userPoint)
                    
                    val distance = lastSearchLocation?.distanceToAsDouble(userPoint) ?: Double.MAX_VALUE
                    if (distance >= SEARCH_THRESHOLD_METERS) {
                        lastSearchLocation = userPoint
                        fetchParks(it.latitude, it.longitude)
                    } else {
                        Log.d(TAG, "Skipping search, moved only ${distance.toInt()} meters")
                    }
                } ?: run {
                    Toast.makeText(this, "Konum alınamadı. Lütfen GPS'in açık olduğundan emin olun.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Location error", e)
                Toast.makeText(this, "Konum hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchParks(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }

                // Dynamic Overpass QL Query
                val query = "[out:json];node[\"leisure\"=\"park\"](around:5000,$lat,$lon);out;"
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val finalUrl = "https://overpass-api.de/api/interpreter?data=$encodedQuery"
                
                Log.d("OverpassQuery", "URL: $finalUrl")
                Log.d("OverpassQuery", "Query: $query")

                val url = URL(finalUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                val response = connection.inputStream.bufferedReader().readText()
                Log.d("OverpassQuery", "Response length: ${response.length}")
                
                val jsonObject = JSONObject(response)
                val elements = jsonObject.getJSONArray("elements")
                Log.d("OverpassQuery", "Found ${elements.length()} parks")

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    // Clear markers but keep MyLocationOverlay
                    val overlaysToKeep = binding.mapView.overlays.filter { it is MyLocationNewOverlay }
                    binding.mapView.overlays.clear()
                    binding.mapView.overlays.addAll(overlaysToKeep)
                    
                    for (i in 0 until elements.length()) {
                        val element = elements.getJSONObject(i)
                        val pLat = element.getDouble("lat")
                        val pLon = element.getDouble("lon")
                        val name = if (element.has("tags") && element.getJSONObject("tags").has("name")) {
                            element.getJSONObject("tags").getString("name")
                        } else "Park"

                        addParkMarker(pLat, pLon, name)
                    }
                    binding.mapView.invalidate()
                    
                    if (elements.length() == 0) {
                        Toast.makeText(this@MapActivity, "5km çevrenizde park bulunamadı.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Overpass API Error", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@MapActivity, "Parklar yüklenirken hata oluştu.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addParkMarker(lat: Double, lon: Double, name: String) {
        val parkMarker = Marker(binding.mapView)
        parkMarker.position = GeoPoint(lat, lon)
        parkMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        parkMarker.title = name
        // Use a default icon or customize it
        binding.mapView.overlays.add(parkMarker)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            myLocationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        myLocationOverlay.disableMyLocation()
    }
}
