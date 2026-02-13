# Contributing to Shadow

Thank you for considering contributing to the Shadow Gradle plugin! This document provides guidelines and information to help you get started.

## Development Commands

### Code Style

Shadow uses [Spotless](https://github.com/diffplug/spotless) to maintain consistent code formatting.

- **Check code style**: `./gradlew spotlessCheck`
- **Format code**: `./gradlew spotlessFormat`

Please ensure your code is properly formatted before submitting a pull request.

### Testing

Shadow has multiple test suites to ensure code quality:

- **Unit tests**: `./gradlew test`
- **Documentation tests**: `./gradlew documentTest` - Tests code snippets in the `./docs` directory
- **Functional/Integration tests**: `./gradlew functionalTest`

Make sure all tests pass before submitting your changes.

### API Compatibility

Shadow uses Kotlin's [binary compatibility validator](https://github.com/Kotlin/binary-compatibility-validator) to track public API changes.

- **Check API dumps**: `./gradlew checkKotlinAbi`
- **Update API dumps**: `./gradlew updateKotlinAbi`

If you add or modify public APIs, you'll need to update the API dump files.

### Linting

Shadow uses [Android Lint](https://developer.android.com/studio/write/lint) to catch potential issues.

- **Run lint checks**: `./gradlew lint`
- **Update lint baseline**: `./gradlew updateLintBaseline`

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

1. Run `./gradlew spotlessFormat` to format your code
2. Run `./gradlew spotlessCheck` to verify formatting
3. Run all test suites: `./gradlew test documentTest functionalTest`
4. Run `./gradlew checkKotlinAbi` to ensure API compatibility
5. Run `./gradlew lint` to check for potential issues
6. Or just run `./gradlew build` for depending on the tasks above
7. Ensure your commit messages are clear and descriptive

## Questions?

If you have questions or need help, feel free to open an issue or discussion on the [GitHub repository](https://github.com/GradleUp/shadow).
