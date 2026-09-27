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

Attachments are separate Room entities with ownership, MIME type, display name, SHA-256 checksum, and optional OCR text.

## Document ingestion

Images from the camera or document picker are copied into app-private storage. PDFs are also supported as real attachments.

Image OCR uses ML Kit and stores the recognized text with the attachment. The app extracts possible merchant, purchase date, total, model, and serial number, but presents them as reviewable suggestions. OCR never invents warranty dates.

Search covers structured purchase fields and stored OCR text, so an old receipt can be found by merchant, model, serial number, or recognized receipt content.

## Payments

- exact integer-cent monetary storage;
- paid state and paid date;
- monthly recurrence with stable billing-day anchoring;
- pending-payment filtering;
- WorkManager reminders.

## Backup and restore

The app can create a portable versioned `.notasegura` archive containing:

- purchases;
- attachment metadata;
- original managed documents;
- payments;
- a format/schema manifest;
- SHA-256 checksums for data and every document.

Restore copies the archive into staging, validates structure and checksums, prepares replacement documents, and only then replaces Room data in a transaction. A malformed or incomplete archive is rejected before the live database is changed.

**Current limitation:** backup archives provide integrity checking but are not yet encrypted. Treat exported backup files as sensitive documents.

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

## Data and security model

- Room stores structured data locally.
- Managed documents live under app-private internal storage.
- Android backup remains disabled.
- Shared PDF reports are temporary cache files exposed through FileProvider grants.
- Portable backups are explicitly created by the user through Android's document picker.
- There is no cloud sync or app-level database/file encryption yet.

## Project structure

`app/src/main/java/com/mothblank/notasegura/`

- `data/local/`: Room database and DAOs.
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

## Next work

- encrypt portable backups with a user-controlled recovery secret;
- add a dashboard for upcoming obligations and expiring warranties;
- add reminder thresholds/deep links/actions;
- add attachment types/editing and PDF text extraction;
- add backup/restore and persistence tests for the supported baseline.
