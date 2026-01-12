package io.securenode.branding

import retrofit2.HttpException
import java.io.IOException

sealed class SecureNodeError(val code: String, message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotInitialized : SecureNodeError("not_initialized", "SecureNodeBranding not initialized")
    class Network(cause: Throwable? = null) : SecureNodeError("network", "Network error", cause)
    class Unauthorized : SecureNodeError("unauthorized", "Unauthorized (API key invalid/expired)")
    class RateLimited : SecureNodeError("rate_limited", "Rate limited")
    class Server(message: String = "Server error") : SecureNodeError("server", message)
    class BadRequest(message: String = "bad_request") : SecureNodeError("bad_request", message)
    class Unknown(cause: Throwable? = null) : SecureNodeError("unknown", "Unknown error", cause)

    companion object {
        fun fromThrowable(t: Throwable): SecureNodeError = when (t) {
            is SecureNodeError -> t
            is IOException -> Network(t)
            is HttpException -> when (t.code()) {
                400 -> BadRequest("Bad request")
                401 -> Unauthorized()
                429 -> RateLimited()
                in 500..599 -> Server("Server error (${t.code()})")
                else -> Unknown(t)
            }
            else -> Unknown(t)
        }
    }
}
