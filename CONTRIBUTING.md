# Contributing to Wishy Clip

Thank you for your interest in contributing to Wishy Clip! We welcome contributions from bug reports and feature requests to code contributions and custom brush/theme packs.

## Getting Started

1. Fork and clone the repository.
2. Open the project in Android Studio (Hedgehog or newer).
3. Ensure you have JDK 17 installed.
4. Run `./gradlew assembleDebug testDebugUnitTest` to verify your environment builds cleanly.

## Branch & PR Flow

- Create a feature or bugfix branch from `main` (e.g. `feature/my-new-feature` or `fix/stroke-lag`).
- Keep PRs focused and self-contained.
- Ensure all unit tests pass and code compiles without warnings.

## Code Style & Guidelines

- Write idiomatic Kotlin.
- Follow the MVI/MVVM architecture patterns established in the project.
- Read [DESIGN.md](DESIGN.md) for guidelines on adding custom themes, tokens, and icon packs.
- Read [BRUSHES.md](BRUSHES.md) for details on the brush dab engine and brush importers (.wbrush, Krita .bundle/.kpp, .abr, etc.).
