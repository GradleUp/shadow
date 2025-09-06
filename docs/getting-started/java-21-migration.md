# Minecraft Plugin Development Migration Guide

This guide helps you migrate from older Shadow plugin versions to the modern `com.gradleup.shadow` plugin.

## Overview

With the release of Shadow 9.x, developers need to migrate from:
- **Old Plugin ID**: `com.github.johnrengelman.shadow`
- **New Plugin ID**: `com.gradleup.shadow`

### 1. Update Plugin Declaration

=== "Before (Old Plugin)"

    ```kotlin
    plugins {
        id("com.github.johnrengelman.shadow") version "8.1.1"
    }
    ```

=== "After (New Plugin)"

    ```kotlin
    plugins {
        id("com.gradleup.shadow") version "8.3.3"
        // or version "9.1.0" for latest features
    }
    ```

### 2. Update Java Version

```kotlin
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
```

### 3. Common Issues and Solutions

#### Issue: "UnsupportedClassVersionError"
**Solution**: Migrate to Shadow 8.3.3+ or 9.x

#### Issue: "Plugin not found"
**Cause**: Using old plugin ID with newer versions  
**Solution**: Update plugin ID to `com.gradleup.shadow`

#### Issue: Gradle compatibility warnings
**Cause**: Version mismatch between Gradle and Shadow  
**Solution**: Use compatible versions from the matrix above

## Developing Minecraft Plugin

For Minecraft plugin developers using Folia, Paper, or Spigot:

=== "Folia 1.21.8+"

    ```kotlin
    plugins {
        id("java")
        id("com.gradleup.shadow") version "8.3.3"
    }
    
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    dependencies {
        compileOnly("dev.folia:folia-api:1.21.8-R0.1-SNAPSHOT")
        // other dependencies...
    }
    
    tasks {
        shadowJar {
            archiveClassifier.set("")
            relocate("com.example.lib", "your.plugin.lib.example")
        }
    }
    ```

=== "Paper/Spigot"

    ```kotlin
    plugins {
        id("java")
        id("com.gradleup.shadow") version "8.3.3"
    }
    
    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
        // other dependencies...
    }
    ```

### 5. Migrating Examples

If migrating check theses examples https://gradleup.com/shadow/changes/#migration-example

## Troubleshooting

### Build Cache Issues

If you encounter Gradle cache corruption after migrating:

```bash
# Clear Gradle cache
./gradlew clean --refresh-dependencies

```

### Dependency Conflicts

When migrating plugins with many dependencies, avoid relocating core platform classes:

```kotlin
tasks.shadowJar {
    // DON'T relocate these for Minecraft plugins
    // relocate("io.papermc", "your.plugin.papermc") // ❌ This breaks Folia schedulers
    
    // DO relocate your library dependencies
    relocate("com.google.gson", "your.plugin.lib.gson") // ✅ Safe to relocate
}
```

