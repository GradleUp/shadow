# Minecraft Plugin Development Migration Guide

This guide helps you migrate from older Shadow plugin versions to the modern `com.gradleup.shadow` plugin with Java 21 support.

## Overview

With the release of Shadow 9.x, developers need to migrate from:
- **Old Plugin ID**: `com.github.johnrengelman.shadow`
- **New Plugin ID**: `com.gradleup.shadow`

## Migration Steps

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

### 4. Minecraft Plugin Development

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

=== "Paper/Spigot (Java 17+)"

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

### 5. Breaking Changes in 9.x

If migrating to Shadow 9.x, be aware of these breaking changes:

- **`isEnableRelocation`** → **`enableAutoRelocation`**
- **Default `duplicatesStrategy`**: Changed from `EXCLUDE` to `INCLUDE` (reverted to `EXCLUDE` in 9.0.1)
- **Transformer interface changes**: Updated to accept context objects
- **Resource handling**: `from()` behavior aligned with Gradle's `AbstractCopyTask.from`

=== "Shadow 8.x"

    ```kotlin
    tasks.shadowJar {
        isEnableRelocation = true
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
    }
    ```

=== "Shadow 9.x"

    ```kotlin
    tasks.shadowJar {
        enableAutoRelocation = true
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Explicitly set
        mergeServiceFiles()
    }
    ```

## Troubleshooting

### Build Cache Issues

If you encounter Gradle cache corruption after migrating:

```bash
# Clear Gradle cache
./gradlew clean --refresh-dependencies

# Or manually clear cache
rm -rf ~/.gradle/caches/
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

## Complete Example: UltimateTeams Plugin

Real-world example from a Folia 1.21.8 plugin:

```kotlin
plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
    id("java")
}

group = "dev.xf3d3"
version = "4.5.4-folia-1.21.8"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // other repositories...
}

dependencies {
    compileOnly("dev.folia:folia-api:1.21.8-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    // other dependencies...
}

tasks {
    shadowJar {
        archiveFileName.set("${rootProject.name}-${rootProject.version}.jar")
        archiveClassifier.set("main")
        
        // Relocate dependencies to avoid conflicts
        relocate("org.bstats", "dev.xf3d3.ultimateteams.libraries.bstats")
        relocate("co.aikar", "dev.xf3d3.ultimateteams.libraries.aikar")
        
        // IMPORTANT: Don't relocate io.papermc for Folia compatibility
        // relocate("io.papermc", "...") // ❌ This causes scheduler errors
    }
    
    build {
        dependsOn(shadowJar)
    }
}
```

This migration ensures compatibility with the latest Minecraft server software while maintaining plugin functionality.
