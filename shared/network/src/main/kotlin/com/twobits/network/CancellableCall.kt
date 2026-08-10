package com.twobits.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspending equivalent of [Call.execute] that actually stops the underlying HTTP request when
 * the calling coroutine is cancelled — `execute()` is a blocking call with no cancellation
 * binding of its own, so a coroutine `Job.cancel()` only stops the *caller* at its next
 * suspension point; the request itself keeps running on the OkHttp dispatcher thread until it
 * completes, wasting time, battery, and (for a paid API) money. Use this instead of `execute()`
 * anywhere a user-visible Cancel action needs to actually abort an in-flight request.
 */
suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response)
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            },
        )
        continuation.invokeOnCancellation {
            runCatching { cancel() }
        }
    }
