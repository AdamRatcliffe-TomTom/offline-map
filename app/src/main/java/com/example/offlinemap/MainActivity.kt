package com.example.offlinemap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tomtom.kotlin.quantity.Distance
import com.tomtom.sdk.common.fold
import com.tomtom.sdk.common.location.GeoCoordinate
import com.tomtom.sdk.datamanagement.nds.*
import com.tomtom.sdk.location.LocationEngine
import com.tomtom.sdk.location.android.AndroidLocationEngine
import com.tomtom.sdk.maps.display.MapOptions
import com.tomtom.sdk.maps.display.TomTomMapConfig
import com.tomtom.sdk.maps.display.location.LocationMarkerOptions
import com.tomtom.sdk.maps.display.map.ResourceCachePolicy
import com.tomtom.sdk.maps.display.style.StyleDescriptor
import com.tomtom.sdk.maps.display.ui.MapFragment
import com.tomtom.sdk.maps.display.ui.OnMapReadyCallback
import com.tomtom.sdk.maps.onboardtiledataprovider.MapDisplayOnboardTileDataProvider
import com.tomtom.sdk.maps.style.StyleUriProvider
import kotlin.time.Duration.Companion.minutes

class MainActivity : AppCompatActivity() {
    private lateinit var tomTomMap: com.tomtom.sdk.maps.display.TomTomMap
    private lateinit var locationEngine: LocationEngine
    private lateinit var ndsStore: NdsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initNdsStore()
        initMap()
        setPosition(GeoCoordinate(37.536698601288116, -122.29793996398554))
        initLocationEngine()
        enableUpdates(true)
    }

    private fun initNdsStore() {
        val onboardPath = requireNotNull(this.getExternalFilesDir(null))

        ndsStore = NdsStore.create(
            context = this,
            configuration = NdsStoreConfiguration(
                ndsStorePath = onboardPath.resolve("map"),
                ndsStoreUpdateConfig = NdsStoreUpdateConfig(
                    updateStoragePath = onboardPath.resolve("updates"),
                    persistentStoragePath = onboardPath.resolve(RELATIVE_MAP_UPDATE_PERSISTENCE_PATH),
                    iqMapsRelevantRegionsEnabled = IQ_MAPS_RELEVANT_REGIONS_UPDATE,
                    iqMapsRelevantRegionsRadius = IQ_MAPS_RELEVANT_REGIONS_RADIUS,
                    iqMapsRelevantRegionsUpdateInterval = IQ_MAPS_RELEVANT_REGIONS_UPDATE_INTERVAL,
                )
            )
        ).fold(
            { it },
            {
                /* Error handling - Your code goes here */
                throw IllegalStateException(it.message)
            }
        )
    }

    private fun initMap() {
        TomTomMapConfig.tileDataProviderFactoryFunction = {
            MapDisplayOnboardTileDataProvider(ndsStore)
        }

        val mapOptions = MapOptions(
            mapStyle = StyleDescriptor(
                StyleUriProvider.ONBOARD_BROWSING_LIGHT,
                StyleUriProvider.ONBOARD_BROWSING_DARK,
                StyleUriProvider.ONBOARD_LAYER_MAPPING,
                StyleUriProvider.ONBOARD_LAYER_MAPPING
            ), mapKey = resources.getString(R.string.API_KEY),
            resourceCachePolicy = ResourceCachePolicy.NoCache
        )
        val mapFragment = MapFragment.newInstance(mapOptions)
        supportFragmentManager.beginTransaction()
            .replace(R.id.map_container, mapFragment)
            .commit()

        mapFragment.getMapAsync(mapReadyCallback)
    }

    private fun initLocationEngine() {
        locationEngine = AndroidLocationEngine(context = this)
    }

    private fun enableUserLocation() {
        if (areLocationPermissionsGranted()) {
            showUserLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun showUserLocation() {
        locationEngine.enable()
        tomTomMap.setLocationEngine(locationEngine)
        val locationMarker = LocationMarkerOptions(type = LocationMarkerOptions.Type.POINTER)
        tomTomMap.enableLocationMarker(locationMarker)
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            showUserLocation()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun areLocationPermissionsGranted() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private val mapReadyCallback = OnMapReadyCallback { map ->
        tomTomMap = map
        enableUserLocation()
    }

    private fun setPosition(currentPosition: GeoCoordinate) {
        ndsStore.setPosition(currentPosition)
    }

    private fun enableUpdates(enabled: Boolean) {
        ndsStore.setUpdatesEnabled(enabled)
    }

    companion object {
        private const val RELATIVE_MAP_UPDATE_PERSISTENCE_PATH = "mapUpdatePersistence"
        private const val IQ_MAPS_RELEVANT_REGIONS_UPDATE = true
        private val IQ_MAPS_RELEVANT_REGIONS_RADIUS = Distance.kilometers(20.0)
        private val IQ_MAPS_RELEVANT_REGIONS_UPDATE_INTERVAL = 60.minutes
    }
}