# NotaSegura 🛡️🧾

NotaSegura is an Android application designed to help users, especially the elderly, manage their physical and digital receipts, warranty expirations, and payment reminders in a simple, secure, and organized way.

Built with modern Android standards (Jetpack Compose, Room, WorkManager).

## 🌟 Key Features

- **Warranty Tracking:** Store and monitor warranty expiration dates for your purchases.
- **Receipt Management:** Save photos of physical receipts securely within the app's internal storage.
- **Payment Reminders:** Keep track of upcoming bills and payments with a dedicated timeline.
- **Smart Notifications:** Receive timely alerts for expiring warranties and upcoming payment due dates.
- **PDF Export:** Generate comprehensive reports of all your warranties and payments for insurance or personal accounting.
- **Accessible Design:** High-contrast UI with large touch targets and readable typography, optimized for ease of use.

## 🛠️ Built With

- **[Kotlin](https://kotlinlang.org/):** Modern programming language for Android.
- **[Jetpack Compose](https://developer.android.com/jetpack/compose):** Modern toolkit for building native UI.
- **[Room Database](https://developer.android.com/training/data-storage/room):** Robust local data persistence.
- **[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager):** Reliable background task scheduling for notifications.
- **[Coil](https://coil-kt.github.io/coil/):** Fast and lightweight image loading for receipts.
- **[Coroutines & Flow](https://kotlinlang.org/docs/reference/coroutines/coroutines-guide.html):** Asynchronous programming model.

## 📂 Project Structure

- `app/src/main/java/com/mothblank/notasegura/`
    - `data/`: Local database (Room), repositories, and background workers.
    - `domain/`: Data models and business logic.
    - `ui/`: Jetpack Compose screens, components, and themes.
    - `util/`: Helper classes for file storage and PDF export.

## 🚀 Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/MOTHblank/NotaSegura.git
   ```
2. **Open in Android Studio:**
   Load the project using Android Studio Ladybug (or newer).
3. **Build and Run:**
   Sync Gradle and run the application on an emulator or physical device.

## 🗺️ Roadmap & Future Features

Check out our detailed plans for the future:
- [Project Roadmap](docs/ROADMAP.md)
- [Future Features Ideas](docs/FUTURE_FEATURES.md)

---
*Created with ❤️ for financial organization and peace of mind.*
