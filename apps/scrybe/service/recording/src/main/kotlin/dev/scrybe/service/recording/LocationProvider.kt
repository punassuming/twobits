package dev.scrybe.service.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun captureCoarseLocationWithLabel(): Triple<Double, Double, String?>? {
            val location = withTimeoutOrNull(5_000) { getLastKnownLocation() } ?: return null
            val label =
                withTimeoutOrNull(3_000) {
                    withContext(Dispatchers.IO) { reverseGeocode(location.latitude, location.longitude) }
                }
            return Triple(location.latitude, location.longitude, label)
        }

        @SuppressLint("MissingPermission")
        private suspend fun getLastKnownLocation(): Location? {
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return null

            val client = LocationServices.getFusedLocationProviderClient(context)

            // Try balanced accuracy first; fall back to high accuracy if no fix is cached.
            return fetchLocation(client, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                ?: fetchLocation(client, Priority.PRIORITY_HIGH_ACCURACY)
        }

        @SuppressLint("MissingPermission")
        private suspend fun fetchLocation(
            client: com.google.android.gms.location.FusedLocationProviderClient,
            priority: Int,
        ): Location? =
            suspendCancellableCoroutine { cont ->
                val cancellationSource = CancellationTokenSource()
                cont.invokeOnCancellation { cancellationSource.cancel() }
                client
                    .getCurrentLocation(priority, cancellationSource.token)
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { cont.resume(null) }
                    .addOnCanceledListener { cont.resume(null) }
            }

        private suspend fun reverseGeocode(
            lat: Double,
            lng: Double,
        ): String? =
            runCatching {
                val geocoder = Geocoder(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+: getFromLocation is async — suspend until onGeocode or onError fires.
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(
                            lat,
                            lng,
                            1,
                            object : android.location.GeocodeListener {
                                override fun onGeocode(addresses: List<android.location.Address>) {
                                    cont.resume(
                                        addresses.firstOrNull()?.let { addr ->
                                            listOfNotNull(addr.locality, addr.adminArea)
                                                .joinToString(", ")
                                                .takeIf { it.isNotBlank() }
                                        },
                                    )
                                }

                                override fun onError(errorMessage: String?) {
                                    cont.resume(null)
                                }
                            },
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.let { addr ->
                        listOfNotNull(addr.locality, addr.adminArea).joinToString(", ").takeIf { it.isNotBlank() }
                    }
                }
            }.getOrNull()
    }
