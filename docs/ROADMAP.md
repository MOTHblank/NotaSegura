# NotaSegura Roadmap

## Completed foundation

### Schema v4: purchases and attachments

The legacy warranty-centric model has been replaced by:

- `Purchase` for the underlying purchase/product record;
- `Attachment` for receipt photos, PDFs, warranty documents, invoices, and future evidence;
- `PurchaseWithAttachments` for UI/domain reads.

The v3→v4 migration preserves legacy rows and converts old `imagePath` values into attachment records.

### Structured OCR

Image OCR now:

- persists raw recognized text on the attachment;
- extracts possible merchant, date, total, model, and serial number;
- presents each extracted value as a separate suggestion;
- requires explicit user acceptance before structured fields change;
- never derives warranty expiration from arbitrary receipt dates.

Purchase search includes OCR text.

### Portable backup / restore

Backup format v1 is a ZIP-based `.notasegura` archive with:

- `manifest.json`;
- `data.json`;
- original managed attachment files;
- SHA-256 integrity records.

Restore validates the archive and all attachment checksums before replacing Room data. Database replacement is transactional and old document files are only removed after a successful restore.

The archive is currently integrity-protected but **not encrypted**.

## Next priorities

### 1. Encrypt backups

Add passphrase/recovery-key encryption using an explicit versioned cryptographic envelope. Keep checksum/integrity validation inside the encrypted payload and define password-loss behavior clearly.

### 2. Reminder model

Replace fixed worker windows with reminder state:

- configurable warranty thresholds such as 30/7/1 days;
- payment thresholds such as 3/1/0 days and overdue;
- delivery state to avoid repeating identical daily notifications;
- notification deep links to the exact record;
- optional "Marcar como pago" action.

### 3. Home / Today screen

Add a simple operational landing screen rather than a chart-heavy finance dashboard:

- overdue payments;
- payments due soon;
- warranties ending soon;
- scan/add/search shortcuts;
- recent purchases.

### 4. Attachment capabilities

- explicit attachment type editing;
- PDF text extraction;
- full-screen document viewing;
- attachment rename;
- optional additional OCR languages;
- duplicate-document detection using SHA-256.

### 5. Recovery and test hardening

- Room migration instrumentation tests through v4;
- backup corruption/rollback tests;
- large archive tests;
- process-death tests during editing;
- orphan-file reconciliation;
- TalkBack and large-font accessibility checks.
