# Origami: Word Blitz

An addictive, high-retention Scrabble-inspired crossword board game built with **Kotlin & Jetpack Compose** for Android, plus a standalone **HTML5 Canvas Web Preview** (`index.html`) for GitHub Pages.

---

## Why You Couldn't See It on the Web Initially

This project's primary codebase is a **native Android application** (`app/src/main/java/com/example/...` written in Kotlin and Jetpack Compose, compiled into an Android `.apk` via Gradle). Standard web browsers and GitHub repositories do not execute native Android `.kt` source files directly without an `index.html` entry point.

To make **Origami: Word Blitz** playable both on **Android** and on the **Web (GitHub Pages)**, this repository now includes:

1. **Native Android App (`/app`)** — 100% Kotlin + Jetpack Compose with a pinch-to-zoom 15×15 `Canvas` board, spring-animated 7-tile rack, `<2ms` in-memory `WordTrieEngine`, `SoundPool` audio synthesizer, haptics, and Cloud Firestore progression.
2. **Playable Web App (`/index.html`)** — A self-contained HTML5 Canvas + Trie Engine web build at the repository root so you can play directly in any browser via GitHub Pages.

---

## How to View & Play on the Web (GitHub Pages)

1. Push the latest commit from AI Studio to your GitHub repository (`https://github.com/jcvcreatives/Origami-Word-Blitz`).
2. On GitHub, go to **Settings → Pages**.
3. Under **Build and deployment**, set **Source** to **Deploy from a branch**, select **`main`**, and folder **`/ (root)`**, then click **Save**.
4. Open **`https://jcvcreatives.github.io/Origami-Word-Blitz/`** in any browser to play immediately.

---

## How to View & Play the Android App

- **In Google AI Studio**: Open the **Preview (Streaming Android Emulator)** panel to interact with the native Android app directly in your browser.
- **On Your Android Phone**: Use the AI Studio **Export / Download APK** menu to install the compiled `.apk` on your device, or clone the repo into **Android Studio** and click **Run (`:app`)**.
