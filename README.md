# Kocur Ai (Android) 🐾⚡

<p align="center">
  <b>A powerful, aesthetic floating screen AI assistant for Android powered by OpenAI & Google Gemini.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-blue?logo=kotlin" alt="Language" />
  <img src="https://img.shields.io/badge/Design-Frutiger_Aero-cyan" alt="Design" />
  <img src="https://img.shields.io/badge/Min_SDK-26-orange" alt="Min SDK" />
</p>

---

**Kocur Ai** is an intelligent Android overlay application that brings AI directly onto your screen. Utilizing Android's `AccessibilityService`, it enables instant screen capture and analysis using multimodal LLMs without repeatedly prompting for screen recording permissions.

Whether you need instant translation, code explanation, text extraction, visual problem solving, or multi-step context analysis, Kocur Ai is always just one tap away.

*Looking for the iOS companion? Check out [Kocur-Ai-IOS](https://github.com/MizZer3/Kocur-Ai-IOS).*

---

## ✨ Features

- **📱 Always-Accessible Floating Overlay**:
  - A draggable, customizable floating action button that stays on top of any app.
  - One-tap screen capture and AI reasoning.
  - Ability to temporarily hide the button from settings when not needed.

- **📸 Multi-Screenshot Support**:
  - Long-press the floating button to reveal the `+` multi-capture button.
  - Capture multiple screens across different apps or pages and submit them together in a single request.

- **🤖 Multimodal AI Models**:
  - Full compatibility with **OpenAI** (Vision & Text-only formats, e.g. GPT-4o, GPT-4o-mini).
  - Full compatibility with **Google Gemini API** (Vision & Text-only formats, e.g. Gemini 1.5/2.0 Flash & Pro).
  - Gemini **Thinking Level** configuration (`Default`, `Low`, `Medium`, `High`, `Minimal`).
  - Customizable JSON request templates (`assets/`).

- **📁 Profile Management**:
  - **Prompt Profiles**: Save custom prompt templates (e.g. *Translate*, *Explain Code*, *Summarize*, *Math Solver*) and switch between them instantly.
  - **API Profiles**: Store and switch between multiple API endpoints, custom proxies, and API keys.

- **🎨 Frutiger Aero & Glassmorphism Aesthetic**:
  - Glossy cards, translucent glass panels, modern typography (`Comfortaa` font), and smooth gradients.
  - Fully customizable floating button: change color (hex), size, opacity, or pick any custom image/icon from your gallery!

---

## 🛠️ Architecture & Permissions

### Permissions
- `android.permission.SYSTEM_ALERT_WINDOW`: Required to render the floating button over other apps.
- `android.permission.BIND_ACCESSIBILITY_SERVICE`: Used by `ScreenCaptureService` to seamlessly grab screen frames upon request.
- `android.permission.INTERNET`: Required to communicate with OpenAI and Google Gemini endpoints.

### Key Components
- `MainActivity.kt`: Profile management, API configuration, button appearance settings, and service status.
- `ScreenCaptureService.kt`: Background accessibility service managing window overlays, touch/drag events, multi-screenshot state, and screenshot taking.
- `AiNetworkClient.kt`: Asynchronous network client powered by OkHttp and Kotlin Coroutines, dynamically formatting requests based on JSON templates and chosen API provider.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 34 (minSdk 26, Android 8.0+)
- JDK 17

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/MizZer3/Kocur-Ai.git
   cd Kocur-Ai
   ```

2. Open the project in Android Studio or build using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install the APK on your device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Setup & Usage

1. Open **Kocur Ai**.
2. Grant the **Display over other apps** permission when prompted.
3. Enable the **Kocur Ai Accessibility Service** in Android Settings -> Accessibility.
4. Go to the **API** tab:
   - Add your API Key (OpenAI or Gemini).
   - Enter the API endpoint URL (or use defaults).
   - Choose format (OpenAI Vision/Text or Gemini Vision/Text).
5. (Optional) In the **Settings** tab, customize your floating button's color, opacity, or set a custom avatar image.
6. Tap the floating button anywhere in the OS to analyze what's currently on your screen!

---

## 🇺🇦 Українською

**Kocur Ai** — це плаваючий ШІ-помічник для Android з інтерфейсом у стилі Frutiger Aero. Додаток використовує `AccessibilityService` для швидкого захоплення екрана (як одного, так і декількох знімків поспіль) та надсилання зображень разом із запитом до OpenAI або Google Gemini. Підтримує збереження профілів промптів і налаштувань API, вибір рівня Thinking для Gemini, а також повну кастомізацію зовнішнього вигляду плаваючої кнопки (колір, прозорість, власна картинка).

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.
