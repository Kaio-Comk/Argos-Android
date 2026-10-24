# Contributing to Argos-Android

Thank you for your interest in improving **Argos-Android**!

## How to Contribute

1. Fork the repository on GitHub.
2. Clone your fork locally:
   ```bash
   git clone https://github.com/<your-username>/Argos-Android.git
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feat/my-improvement
   ```
4. Commit your changes with clear, descriptive commit messages.
5. Push to your fork and submit a Pull Request.

## Code Standards
- Keep the project 100% offline (no `INTERNET` permission in AndroidManifest.xml).
- Maintain MVVM architecture with Kotlin Coroutines and Jetpack Room.
- Ensure Android MediaStore compatibility across API levels 26 through 36.
