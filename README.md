# NotaSegura

NotaSegura is a local-first Android archive for purchases, proof-of-purchase documents, warranty dates, and payment reminders.

## Current model

A purchase is a first-class record. It can contain:

- product name;
- merchant;
- purchase value;
- purchase date;
- optional warranty end date;
- category;
- model number;
- serial number;
- notes;
- any number of image or PDF attachments.

Attachments are separate Room entities with ownership, MIME type, editable display name, document type, SHA-256 checksum, and optional OCR text. Supported document types are receipt, invoice, warranty certificate, manual, and other.

## Document ingestion

Images from the camera or document picker are copied into app-private storage. PDFs are also supported as real attachments.

Image OCR uses the bundled PP-OCRv6_small models and runs locally on the device. The app stores recognized text with the attachment and extracts possible merchant, purchase date, total, model, and serial number as reviewable suggestions. OCR never invents warranty dates, requires no runtime model download, and does not transmit receipt contents.

Search covers structured purchase fields and stored OCR text, so an old receipt can be found by merchant, model, serial number, or recognized receipt content.

The first development build downloads the pinned PP-OCRv6_small detection and recognition assets into Gradle's local cache, verifies their exact size and SHA-256 digests, and packages them into the app. Subsequent clean builds reuse the verified cache. Production installs never download OCR models at runtime.

## Payments

- exact integer-cent monetary storage;
- paid state and paid date;
- monthly recurrence with stable billing-day anchoring;
- pending-payment filtering;
- WorkManager reminders with configurable lead times;
- notification deep links to the relevant purchase/payment or corresponding list.

## Backup and restore

The app can create a portable password-protected, versioned `.notasegura` backup containing:

- purchases;
- attachment metadata;
- original managed documents;
- payments;
- a format/schema manifest;
- SHA-256 checksums for data and every document.

The ZIP payload is wrapped in a versioned encrypted envelope before export. NotaSegura derives a 256-bit key from the user-provided password with PBKDF2-HMAC-SHA256 (600,000 iterations and a unique 16-byte random salt per backup), then encrypts and authenticates the archive with AES-256-GCM using a unique 12-byte IV. The password is never stored and cannot be recovered by the app.

Restore authenticates and decrypts the envelope into private staging, validates the inner archive structure and SHA-256 checksums, prepares replacement documents, and only then replaces Room data in a transaction. A wrong password, tampered file, malformed archive, or incomplete backup is rejected before the live database is changed.

The existing PDF export remains a human-readable report and is deliberately separate from backup/recovery.

## Accessibility and senior-friendly UX

The UI is deliberately optimized for clarity rather than density:

- body text starts at 17–18sp and Material labels are never left at tiny defaults;
- status is always written in text and reinforced by color/iconography, never encoded by color alone;
- primary actions use large labeled buttons instead of gesture-only or icon-only controls;
- deleting records always requires an explicit confirmation;
- leaving an editor prompts before discarding unsaved work;
- empty states contain a direct action instead of assuming the floating button will be discovered;
- notification permission is explained before Android asks for it;
- document thumbnails use fit presentation and document viewing explains pinch-to-zoom;
- high-contrast light and dark palettes are defined explicitly.

The list layouts avoid tight horizontal price/action rows so they remain usable with large system font scaling.

Accessibility is also treated as a regression-tested product constraint. Instrumented Compose tests cover 48dp touch targets, accessible names/content descriptions, heading and reading order semantics, typography response to Android font scaling, and the purchase/payment workflows under 200% font scaling with a constrained logical display size. On API 34+, the suite also runs Android's Accessibility Test Framework against critical screens.

## Data and security model

- Room stores structured data locally.
- Receipt OCR uses bundled PP-OCRv6_small models and runs entirely on-device without runtime network access.
- Managed documents live under app-private internal storage.
- Android backup remains disabled.
- Shared PDF reports are temporary cache files exposed through FileProvider grants.
- Portable backups are explicitly created by the user through Android's document picker and encrypted with a user-controlled password.
- There is no cloud sync; live Room/database files remain protected by Android app-private storage rather than a separate app-level database-encryption layer.

## Project structure

`app/src/main/java/com/mothblank/notasegura/`

- `data/local/`: Room database and DAOs.
- `data/ocr/`: local PP-OCR receipt recognition wrapper.
- `data/repository/`: repository implementations.
- `data/storage/`: attachment staging/commit and purchase document ownership.
- `data/backup/`: versioned archive backup/restore.
- `data/worker/`: scheduled reminder checks.
- `domain/`: purchase, attachment, payment models and repository contracts.
- `ui/`: Compose screens and ViewModels.
- `util/`: OCR parsing, dates, money, file hashing/storage, export, and permissions.

## Schema policy

The current purchase/attachment/payment model is the first supported database baseline. NotaSegura has never had production users, so pre-release schemas are intentionally not migrated or preserved. Development installs using older database files can be discarded.

Once a production release has real user data, schema changes must use explicit Room migrations and preserve backup compatibility.

## Current reminder and dashboard behavior

The purchases screen includes an upcoming-deadlines dashboard for overdue/upcoming payments and warranties nearing expiry. Reminder lead times are configurable independently for payments and warranties, including the option to disable either reminder class. Notifications deep-link to the relevant purchase/payment when there is a single item, or to the appropriate list when several items need attention. A single-payment reminder also offers a direct “mark as paid” action, using the same recurrence path as the payment screen.

## Next work

- add local PDF text extraction, using OCR only when a PDF has no usable embedded text;
- add backup/restore and persistence tests for the supported baseline;
- complete release-hardening tests for process death, large font scales, notification states, file-picker/camera cancellation, and large documents.


## License

NotaSegura is free software licensed under the **GNU General Public License v3.0 only** (`GPL-3.0-only`).

You may use, study, modify, and redistribute the software under the GPLv3 terms. Distributed derivative works based on the GPL-covered program must remain under GPLv3 and provide the corresponding source as required by the license.

Third-party components retain their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

The GPL copyright license does not grant trademark rights in the **NotaSegura** or **MOTHblank** names or branding. Unofficial distributions should use distinct branding so they are not confused with official releases. See [TRADEMARKS.md](TRADEMARKS.md).
