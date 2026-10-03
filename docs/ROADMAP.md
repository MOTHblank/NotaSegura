# NotaSegura Roadmap

## Current baseline

### Purchases and attachments

The supported data model starts with:

- `Purchase` for the underlying purchase/product record;
- `Attachment` for receipt photos, PDFs, warranty documents, invoices, and future evidence;
- `PurchaseWithAttachments` for UI/domain reads;
- `Payment` for payment obligations and recurrence.

NotaSegura has never had production users, so experimental schemas that existed before this model are not compatibility targets. The current Room schema is the first supported baseline. Future migrations should begin from this baseline once real user data exists.

### Structured OCR

Image OCR now:

- persists raw recognized text on the attachment;
- extracts possible merchant, date, total, model, and serial number;
- presents each extracted value as a separate suggestion;
- requires explicit user acceptance before structured fields change;
- never derives warranty expiration from arbitrary receipt dates.

Purchase search includes OCR text.

### Senior-friendly UI baseline

The primary flows now use explicit, large, labeled controls:

- no swipe-only deletion;
- no icon-only paid/unpaid action;
- written status panels for warranties and payments;
- clear empty-state actions;
- large-text-safe vertical list cards;
- explicit unsaved-change exit confirmation;
- plain-language backup and reminder wording;
- explained notification permission;
- high-contrast light/dark themes;
- document viewer guidance and visible remove actions.

Android 8–12 notification delivery is also handled correctly, and overdue payments remain eligible for reminders.

### Portable backup / restore

Backup format v1 keeps the validated ZIP payload internally:

- `manifest.json`;
- `data.json`;
- original managed attachment files;
- SHA-256 integrity records.

Before export, that payload is wrapped in a versioned encrypted envelope using AES-256-GCM. A 256-bit key is derived from the user-controlled password using PBKDF2-HMAC-SHA256 with 600,000 iterations and a unique random salt per backup. The password is never stored or recoverable by NotaSegura.

Restore authenticates and decrypts the envelope first, then validates the archive and all attachment checksums before replacing Room data. Database replacement is transactional and old document files are only removed after a successful restore.

## Next priorities

### 1. Reminder model

Replace fixed worker windows with reminder state:

- configurable warranty thresholds such as 30/7/1 days;
- payment thresholds such as 3/1/0 days and overdue;
- delivery state to avoid repeating identical daily notifications;
- notification deep links to the exact record;
- optional "Marcar como pago" action.

### 2. Home / Today screen

Add a simple operational landing screen rather than a chart-heavy finance dashboard:

- overdue payments;
- payments due soon;
- warranties ending soon;
- scan/add/search shortcuts;
- recent purchases.

### 3. Attachment capabilities

- explicit attachment type editing;
- PDF text extraction;
- full-screen document viewing;
- attachment rename;
- optional additional OCR languages;
- duplicate-document detection using SHA-256.

### 4. Recovery and test hardening

- backup corruption/rollback tests;
- large archive tests;
- process-death tests during editing;
- orphan-file reconciliation;
- TalkBack and large-font accessibility checks;
- add Room migration tests only when the first post-release schema migration exists.
