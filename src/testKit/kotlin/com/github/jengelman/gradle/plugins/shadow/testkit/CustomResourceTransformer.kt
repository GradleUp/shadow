package com.github.jengelman.gradle.plugins.shadow.testkit

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer

class CustomResourceTransformer : ResourceTransformer by ResourceTransformer.Companion
