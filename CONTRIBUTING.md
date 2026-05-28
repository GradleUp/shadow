# Contributing to Shadow

Thank you for considering contributing to the Shadow Gradle plugin! This document provides guidelines and information to help you get started.

## Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or higher is required to build the project. JDK 25 is recommended (and is used in GitHub Actions).
- **Gradle**: The project uses the Gradle Wrapper, so you do not need to install Gradle globally. Run command-line tasks using `./gradlew` (or `gradlew.bat` on Windows).

## Development Commands

### Code Style

Shadow uses [Spotless](https://github.com/diffplug/spotless) to maintain consistent code formatting.

- **Check and format code**: `./gradlew spotlessApply`

Please ensure your code is properly formatted before submitting a pull request.

### Testing

Shadow has multiple test suites to ensure code quality:

- **Unit tests**: `./gradlew test`
- **Documentation tests**: `./gradlew documentTest` - Tests code snippets in the `docs/` directory
- **Functional/Integration tests**: `./gradlew functionalTest`

#### Running Specific Tests

To speed up local development, you can run specific test classes or methods:
- Run a specific unit test: `./gradlew test --tests "com.example.YourTestClass"`
- Run functional tests against a specific Gradle version: `./gradlew functionalTest -PtestGradleVersion=9.1.0` (useful to verify compatibility locally with the minimum or a custom Gradle version)

Make sure all tests pass before submitting your changes.

### API Compatibility

Shadow uses Kotlin's built-in ABI validator to track public API changes.

- **Check API dumps**: `./gradlew checkKotlinAbi`
- **Update API dumps**: `./gradlew updateKotlinAbi`

API dump files are located under the `api/` directory (e.g., [shadow.api](api/shadow.api)). If you add or modify public APIs, you'll need to run `./gradlew updateKotlinAbi` to update the API dump files and include the updated dump files in your pull request.

### Linting

Shadow uses [Android Lint](https://developer.android.com/studio/write/lint) to catch potential issues.

- **Run lint checks**: `./gradlew lint`
- **Update lint baseline**: `./gradlew updateLintBaseline`

### Documentation Preview

Shadow's user guide is built using [MkDocs Material](https://squidfunk.github.io/mkdocs-material/). You can build and preview the documentation website locally:

- **Install MkDocs dependencies**: `pip install mkdocs mkdocs-material`
- **Preview documentation locally**: `mkdocs serve` (then navigate to `http://127.0.0.1:8000/`)
- **Build the static site**: `mkdocs build`

## Contribution Guidelines

### Fixing Issues

When fixing bugs or issues:

1. Ensure all existing tests pass
2. Add regression tests that verify the fix for the reported issue
3. Run the full test suite to ensure no unintended side effects
4. Update documentation if the fix changes behavior

### Adding New Features or APIs

When adding new features or public APIs:

1. Ensure proper visibility modifiers are used
2. All public APIs must be documented with KDoc comments
3. Run `./gradlew updateKotlinAbi` to update the API dump files
4. Add appropriate tests for the new functionality
5. Update the documentation in the `./docs` directory if applicable

### Before Submitting a Pull Request

1. Run `./gradlew spotlessApply` to format your code
2. Run all test suites: `./gradlew test documentTest functionalTest`
3. Run `./gradlew checkKotlinAbi` to ensure API compatibility
4. Run `./gradlew lint` to check for potential issues
5. Optionally, run `./gradlew build` to run compilation, tests, and standard verification tasks configured for the project
6. Ensure your commit messages are clear and descriptive
7. Update the `Unreleased` section in [CHANGELOG](docs/changes/README.md) if applicable
