# NotaSegura

NotaSegura is an Android app for keeping proof-of-purchase documents, warranty dates, and payment reminders in one place.

The app is local-first. Receipt images are copied into app-private storage, Room stores the structured records, and Android WorkManager handles reminder checks. NotaSegura does not currently provide cloud synchronization or application-level encrypted storage, so it should not be described as an encrypted vault.

## Current features

- Warranty tracking with purchase and expiration dates.
- Receipt/photo attachment stored in app-private internal storage.
- OCR assistance for likely purchase dates without inventing warranty dates.
- Payment reminders with exact cent-based monetary storage.
- Monthly recurring payments with stable billing-day anchoring.
- Paid-date tracking for newly marked payments.
- Search and filtering for warranties and payments.
- Reminder notifications for warranties expiring within 30 days and unpaid payments due within 3 days.
- Multi-page PDF summary export through Android's share sheet.
- Large touch targets and high-contrast typography intended to remain usable for older users.

## Data and security model

- Structured data is stored locally in Room.
- Attached receipt images are stored under the app's private files directory.
- Android backup is disabled in the manifest.
- PDF exports are temporary cache files shared only through a FileProvider grant.
- There is no cloud backup or app-level database/file encryption yet.

This means normal Android application sandboxing protects the data from ordinary other apps, but losing the device or uninstalling the app can still destroy local records unless the user exports them.

## Project structure

`app/src/main/java/com/mothblank/notasegura/`

- `data/local/`: Room database and DAOs.
- `data/repository/`: repository implementations.
- `data/storage/`: receipt/document persistence that coordinates Room and managed files.
- `data/worker/`: scheduled reminder checks.
- `domain/`: models and repository contracts.
- `ui/`: Compose screens and ViewModels.
- `util/`: date, money, export, notification-permission, and file helpers.

## Build

1. Clone the repository.
2. Open it in Android Studio Ladybug or newer.
3. Sync Gradle.
4. Run the `app` configuration on Android 8.0 (API 26) or newer.

## Next product work

The strongest next additions are:

- encrypted, user-controlled backup/restore with restore verification;
- richer purchase metadata such as merchant, serial/model number, notes, and purchase value;
- OCR suggestions for merchant/total/serial fields;
- full PDF document attachments rather than image-only evidence;
- explicit notification settings and reminder lead-time controls.
