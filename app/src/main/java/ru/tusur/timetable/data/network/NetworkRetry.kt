package ru.tusur.timetable.data.network

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Повторы с экспоненциальной задержкой. API неофициальный, поэтому сетевые
 * сбои и 5xx/429 — норма, а не исключение.
 */
object NetworkRetry {

    suspend fun <T> withRetry(
        attempts: Int = 3,
        initialDelayMs: Long = 300L,
        block: suspend () -> T,
    ): T {
        var lastError: Throwable? = null
        for (attempt in 0 until attempts) {
            try {
                return block()
            } catch (throwable: Throwable) {
                lastError = throwable
                if (!isRetryable(throwable) || attempt == attempts - 1) throw throwable
                delay(initialDelayMs * (1L shl attempt))
            }
        }
        throw checkNotNull(lastError) { "Повтор не удался" }
    }

    fun isRetryable(throwable: Throwable): Boolean = when (throwable) {
        is SocketTimeoutException -> true
        is UnknownHostException -> true
        is IOException -> true
        is HttpException -> throwable.code() >= 500 || throwable.code() == 429
        else -> false
    }

    fun loggingClient(debug: Boolean): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (debug) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()
}
