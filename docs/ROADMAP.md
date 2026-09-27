# NotaSegura Roadmap

## Current baseline

The app now has two explicit domains:

- warranty / proof-of-purchase records;
- payment reminders and recurring payment occurrences.

Important correctness work completed in the current schema:

- Room schema version 3 with explicit v1→v2 and v2→v3 migrations;
- payment values stored as integer centavos instead of floating point;
- recurring payments keep an anchor day across short months;
- paid state and paid date are distinct, so migrated paid records do not receive fabricated timestamps;
- receipt images are staged in cache and committed only with a successful record save;
- record deletion happens before managed-file deletion;
- DatePicker values are interpreted as UTC calendar dates instead of local instants;
- save screens navigate away only after persistence succeeds;
- reminder queries execute in Room rather than loading entire tables;
- PDF summaries paginate and share through FileProvider.

## Near-term priorities

### 1. Backup and restore

Implement a user-controlled backup format containing:

- Room data;
- managed receipt images;
- format/schema version;
- integrity manifest/checksums.

Prefer encrypted archives with a recoverable user workflow. A backup is incomplete until restore has been tested.

### 2. First-class purchase records

The current `WarrantyItem` remains minimal. Evolve it toward a purchase/document record with:

- product name;
- merchant;
- purchase value;
- purchase date;
- warranty duration/end date;
- serial/model number;
- notes;
- multiple attachments.

Do not infer warranty expiry from arbitrary OCR dates.

### 3. Better document ingestion

- retain image capture/gallery;
- add actual PDF persistence before reintroducing the PDF picker;
- provide OCR suggestions rather than silently overwriting typed values;
- index OCR text for later search.

### 4. Reminder controls

- configurable lead times;
- notification settings screen;
- deep links into the exact warranty/payment;
- optional notification action for marking a payment paid.

### 5. Recovery and maintenance

- orphan-file cleanup for legacy installs;
- schema migration instrumentation tests;
- backup/restore tests;
- accessibility checks with large font scale and TalkBack.
