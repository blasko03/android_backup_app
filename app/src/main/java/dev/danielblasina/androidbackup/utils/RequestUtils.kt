package dev.danielblasina.androidbackup.utils

import android.util.Log
import okhttp3.Call
import okio.IOException
import java.net.HttpURLConnection
import java.util.logging.Logger

val RETRYABLE_ERRORS = listOf(408, 429, 500, 502, 503, 504)

inline fun <reified T> Call.executeWithRescueJson(successCodes: Array<Int> = arrayOf()): Result<T> {
    try {
        execute().use { res ->
            val body = res.body.byteStream()
            if (successCodes.isNotEmpty() && res.code in successCodes) {
                return Result.success(JsonParse.parse<T>(body))
            }
            if (res.isSuccessful) {
                return Result.success(JsonParse.parse<T>(body))
            }
            if (res.code == HttpURLConnection.HTTP_NOT_FOUND) {
                return Result.failure(NotFoundError(res))
            }
            return Result.failure(FailedRequestError(res))
        }
    } catch (e: IOException) {
        return Result.failure(e)
    }
}

fun Call.executeWithRescue(successCodes: Array<Int> = arrayOf()): Result<String> {
    try {
        execute().use { res ->
            if (successCodes.isNotEmpty() && res.code in successCodes) {
                return Result.success(res.body.string())
            }
            if (res.isSuccessful) {
                return Result.success(res.body.string())
            }
            if (res.code == HttpURLConnection.HTTP_NOT_FOUND) {
                return Result.failure(NotFoundError(res))
            }
            Log.e(this::javaClass.name, res.toString())
            return Result.failure(FailedRequestError(res))
        }
    } catch (e: IOException) {
        return Result.failure(e)
    }
}

fun <T> withRetry(operation: () -> Result<T>, numberOfRetry: Int): Result<T> {
    val logger: Logger = Logger.getLogger("withRetry")
    var result: Result<T> = Result.failure(Exception())
    for (i in (1..numberOfRetry)) {
        result = operation()
            .onSuccess { res ->
                return Result.success(res)
            }.onFailure { e ->
                when (isRetryableError(e)) {
                    false -> {
                        return Result.failure(e)
                    }

                    true -> {
                        logger.warning { "Failed http request after $i attempts" }
                        Thread.sleep(1000)
                    }
                }
            }
    }
    logger.warning { "Failed http request will after $numberOfRetry attempts" }
    return result
}

fun isRetryableError(error: Throwable): Boolean {
    if (error is FailedRequestError && RETRYABLE_ERRORS.contains(error.response.code)) {
        return true
    }
    if (error is java.io.IOException) {
        return true
    }
    return false
}
