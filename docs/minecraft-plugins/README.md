# Minecraft Plugin Development

## Configurations for Developing Minecraft Plugins

For Minecraft plugin developers using Folia, Paper, or Spigot:

=== "Folia 1.21.8+ (Requires Java 21)"

    ```kotlin
    plugins {
      id("java")
      id("com.gradleup.shadow")
    }

    java {
      sourceCompatibility = JavaVersion.VERSION_21
      targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
      compileOnly("dev.folia:folia-api:1.21.8-R0.1-SNAPSHOT")
      // other dependencies...
    }

    tasks.shadowJar {
      archiveClassifier = ""
      relocate("com.example.lib", "your.plugin.lib.example")
    }
    ```

=== "Paper/Spigot (Java 17+)"

    ```kotlin
    plugins {
      id("java")
      id("com.gradleup.shadow")
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

## Troubleshooting

### Build Cache Issues

If you encounter Gradle cache corruption after migrating:

```sh
# Clear Gradle cache and rerun the shadowJar task.
./gradlew clean shadowJar
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

This configuration ensures compatibility with the latest Minecraft server software while maintaining plugin functionality.
