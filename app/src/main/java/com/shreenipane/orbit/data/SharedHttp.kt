package com.shreenipane.orbit.data

import okhttp3.OkHttpClient

/**
 * One OkHttp client for the whole app. Per-purpose clients are derived with
 * newBuilder(), which shares this one's connection pool and dispatcher threads.
 */
object SharedHttp {
    val base: OkHttpClient by lazy { OkHttpClient() }
}
