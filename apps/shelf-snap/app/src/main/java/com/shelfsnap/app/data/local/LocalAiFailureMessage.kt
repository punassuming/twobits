package com.shelfsnap.app.data.local

import com.twobits.localai.InsufficientMemoryException

/**
 * Shared by every local-inference call site's failure path (vision analysis, listing refine,
 * price research). A one-size-fits-all generic string ("On-device analysis failed. Try Pro or
 * BYOK instead.") was previously all any of them ever showed the user, regardless of cause — the
 * actual reason (including [InsufficientMemoryException]'s specific free/needed-memory numbers)
 * only ever reached the Debug Log, never the screen. [InsufficientMemoryException]'s message is
 * already written to be user-facing (see its throw site in `LocalInferenceMemoryGuard`), so it's
 * used verbatim here; anything else still falls back to the caller's own generic copy, since most
 * other failures (a truncated/unparseable model response, for one) don't have a message worth
 * showing raw.
 */
fun localAiFailureMessage(
    e: Throwable,
    genericMessage: String,
): String =
    if (e is InsufficientMemoryException) {
        e.message ?: genericMessage
    } else {
        genericMessage
    }
