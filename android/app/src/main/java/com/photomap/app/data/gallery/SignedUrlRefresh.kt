package com.photomap.app.data.gallery

import coil3.network.HttpException as CoilHttpException

enum class SignedUrlVariant(val apiValue: String) {
    THUMBNAIL("thumbnail"),
    PREVIEW("preview"),
    POSTER_FRAME("posterFrame"),
}

fun isExpiredSignedUrlError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is CoilHttpException && current.response.code == HTTP_FORBIDDEN) return true
        val message = current.message.orEmpty()
        if (message.contains("HTTP 403", ignoreCase = true) ||
            message.contains("status code 403", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

private const val HTTP_FORBIDDEN = 403
