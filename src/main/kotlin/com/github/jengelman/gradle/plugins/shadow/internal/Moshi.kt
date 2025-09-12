package com.github.jengelman.gradle.plugins.shadow.internal

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
