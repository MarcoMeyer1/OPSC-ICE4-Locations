package com.example.ice4locations

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

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
            val searchTerm = searchEditText.text.toString()
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

    // Function to fetch nearby places using OpenStreetMap Nominatim API
    private fun searchNearbyPlaces(searchTerm: String) {
        val encodedSearchTerm = URLEncoder.encode(searchTerm, "UTF-8") // URL encode the search term
        val lat = userLocation!!.latitude
        val lon = userLocation!!.longitude

        val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedSearchTerm&lat=$lat&lon=$lon&format=json&addressdetails=1&limit=10"

        thread {
            try {
                val urlConnection = URL(urlString).openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("User-Agent", "NearbyPlacesLocatorApp/1.0 (your-email@example.com)") // Ensure User-Agent is set
                urlConnection.connect()

                if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = urlConnection.inputStream.bufferedReader().readText()
                    val jsonResponse = JSONArray(response)
                    val placesList = ArrayList<Place>()

                    for (i in 0 until jsonResponse.length()) {
                        val place = jsonResponse.getJSONObject(i)
                        val displayName = place.getString("display_name")
                        val address = extractAddress(place)
                        placesList.add(Place(displayName, address))
                    }

                    runOnUiThread {
                        val adapter = PlaceAdapter(this@MainActivity, placesList)
                        placesListView.adapter = adapter
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Error fetching data: ${urlConnection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }

                urlConnection.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Function to extract address from the JSON response
    private fun extractAddress(place: JSONObject): String {
        val address = place.optJSONObject("address")
        return if (address != null) {
            val road = address.optString("road", "")
            val city = address.optString("city", "")
            val country = address.optString("country", "")
            listOf(road, city, country).filter { it.isNotBlank() }.joinToString(", ")
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

            val place = places[position]
            placeNameTextView.text = place.displayName
            placeAddressTextView.text = place.address

            return view
        }
    }

    // Data class for Place
    private data class Place(val displayName: String, val address: String)

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
