package com.example.ice4locations

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var placesListView: ListView
    private var userLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)
        placesListView = findViewById(R.id.placesListView)

        // Initialize the Fused Location Provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check if the location permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000)
        } else {
            getUserLocation()
        }

        searchButton.setOnClickListener {
            val searchTerm = searchEditText.text.toString().lowercase().trim()
            if (userLocation != null) {
                searchNearbyPlaces(searchTerm)
            } else {
                Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to get the user's current location
    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val locationTask: Task<Location> = fusedLocationClient.lastLocation
        locationTask.addOnSuccessListener { location ->
            if (location != null) {
                userLocation = location
                Toast.makeText(this, "Location acquired!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to fetch nearby places using Overpass API
    private fun searchNearbyPlaces(searchTerm: String) {
        val lat = userLocation!!.latitude
        val lon = userLocation!!.longitude

        // Log the user's current location
        Log.d("NearbyPlacesLocator", "User Location: Lat=$lat, Lon=$lon")

        // Define the Overpass API query URL
        val urlString = """
    https://overpass-api.de/api/interpreter?data=[out:json];
    node["amenity"="$searchTerm"](around:1000,$lat,$lon);
    out body;
    """.trimIndent()

        Log.d("NearbyPlacesLocator", "URL: $urlString") // Log the URL being used

        thread {
            try {
                val urlConnection = URL(urlString).openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("VcStudent", "VcProject/v1 (vcstudent@gmail.com)")
                urlConnection.connect()

                val responseCode = urlConnection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = urlConnection.inputStream.bufferedReader().readText()

                    val jsonResponse = JSONObject(response).getJSONArray("elements")
                    val placesList = ArrayList<Place>()

                    for (i in 0 until jsonResponse.length()) {
                        val place = jsonResponse.getJSONObject(i)
                        // Extract the name or fallback to amenity if name is missing
                        val displayName = if (place.has("tags") && place.getJSONObject("tags").has("name")) {
                            place.getJSONObject("tags").getString("name")
                        } else if (place.has("tags") && place.getJSONObject("tags").has("amenity")) {
                            place.getJSONObject("tags").getString("amenity").capitalize()
                        } else {
                            "Unnamed Place"
                        }

                        val latPlace = place.getDouble("lat")
                        val lonPlace = place.getDouble("lon")
                        val distance = calculateDistance(lat, lon, latPlace, lonPlace)
                        val address = extractAddress(place)
                        placesList.add(Place(displayName, address, distance))
                    }

                    runOnUiThread {
                        if (placesList.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No locations found", Toast.LENGTH_SHORT).show()
                        } else {
                            val adapter = PlaceAdapter(this@MainActivity, placesList)
                            placesListView.adapter = adapter
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Error fetching data: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                    Log.d("NearbyPlacesLocator", "Error: Response Code $responseCode") // Log the error response code
                }

                urlConnection.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("NearbyPlacesLocator", "Exception: ${e.message}", e) // Log the exception with full stack trace
            }
        }
    }




    // Function to calculate the distance between two points using the Haversine formula
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radius of the Earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * acos(sqrt(a.coerceAtMost(1.0)))
        return R * c
    }

    // Function to extract address from the Overpass API response
    private fun extractAddress(place: JSONObject): String {
        val tags = place.optJSONObject("tags")
        return if (tags != null) {
            val street = tags.optString("addr:street", "")
            val city = tags.optString("addr:city", "")
            val country = tags.optString("addr:country", "")
            listOf(street, city, country).filter { it.isNotBlank() }.joinToString(", ")
        } else {
            "Address not available"
        }
    }

    // Custom Adapter for ListView
    private class PlaceAdapter(context: MainActivity, private val places: List<Place>) : ArrayAdapter<Place>(context, 0, places) {
        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val view = convertView ?: android.view.LayoutInflater.from(context).inflate(R.layout.list_item_place, parent, false)

            val placeNameTextView = view.findViewById<TextView>(R.id.placeNameTextView)
            val placeAddressTextView = view.findViewById<TextView>(R.id.placeAddressTextView)
            val placeDistanceTextView = view.findViewById<TextView>(R.id.placeDistanceTextView)

            val place = places[position]
            placeNameTextView.text = place.displayName
            placeAddressTextView.text = place.address
            placeDistanceTextView.text = String.format("%.2f km", place.distanceFromUser)

            return view
        }
    }

    // Data class for Place
    private data class Place(val displayName: String, val address: String, val distanceFromUser: Double)

    // Handling permission results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1000 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getUserLocation() // Call the function to get the location
        } else {
            Toast.makeText(this, "Location permission is required to use this feature", Toast.LENGTH_SHORT).show()
        }
    }
}
