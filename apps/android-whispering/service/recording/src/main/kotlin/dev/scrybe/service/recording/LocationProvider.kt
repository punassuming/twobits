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
        suspend fun captureCoarseLocationWithLabel(): Triple<Double, Double, String?>? =
            withTimeoutOrNull(3_000) {
                val location = getLastKnownLocation() ?: return@withTimeoutOrNull null
                val label = withContext(Dispatchers.IO) { reverseGeocode(location.latitude, location.longitude) }
                Triple(location.latitude, location.longitude, label)
            }

        @SuppressLint("MissingPermission")
        private suspend fun getLastKnownLocation(): Location? {
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return null

            return suspendCancellableCoroutine { cont ->
                val cancellationSource = CancellationTokenSource()
                cont.invokeOnCancellation { cancellationSource.cancel() }
                LocationServices
                    .getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { cont.resume(null) }
                    .addOnCanceledListener { cont.resume(null) }
            }
        }

        @Suppress("DEPRECATION")
        private fun reverseGeocode(
            lat: Double,
            lng: Double,
        ): String? =
            runCatching {
                val geocoder = Geocoder(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var result: String? = null
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        result =
                            addresses.firstOrNull()?.let { addr ->
                                listOfNotNull(addr.locality, addr.adminArea).joinToString(", ").takeIf { it.isNotBlank() }
                            }
                    }
                    result
                } else {
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.let { addr ->
                        listOfNotNull(addr.locality, addr.adminArea).joinToString(", ").takeIf { it.isNotBlank() }
                    }
                }
            }.getOrNull()
    }
