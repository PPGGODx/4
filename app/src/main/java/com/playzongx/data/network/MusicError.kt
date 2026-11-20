package com.playzongx.data.network

/**
 * Errors surfaced by the network layer.
 *
 * Callers can catch this single type and inspect the subtype to decide whether
 * a retry is worthwhile ([Network]) or whether the response could not be
 * understood ([Parse]).
 */
sealed class MusicError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The request failed at the transport or HTTP status level. */
    class Network(cause: Throwable) : MusicError("Network request failed", cause)

    /** The response was not valid JSON or did not have the expected shape. */
    class Parse(cause: Throwable? = null) : MusicError("Failed to parse response", cause)

    /** The requested content is not available. */
    class Unavailable : MusicError("Content unavailable")
}
