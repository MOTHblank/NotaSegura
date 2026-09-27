# Future feature ideas

## Encrypted portable backups

The versioned backup format now exists and verifies integrity. The next security step is encryption with a user-controlled recovery secret. Do not make recovery depend exclusively on the device Keystore, because backups must survive device loss.

## Smarter reminders

Model reminder thresholds and delivery state rather than sending generic summaries from fixed windows. Deep-link notifications to individual purchases/payments and support direct payment completion actions.

## Today screen

Show only immediately useful information: overdue payments, near-term due dates, expiring warranties, recent documents, and common actions.

## Better document understanding

OCR is now reviewable and searchable. Extend it with PDF text extraction, more robust Brazilian fiscal-document parsing, duplicate detection, and confidence/provenance metadata for extracted values.

## Purchase lifecycle

Add repair/service history, warranty claims, return windows, extended warranties, and disposal/resale records without collapsing these into the purchase itself.

## Evidence export

Keep the current PDF report as a readable summary. Add a separate evidence-package export containing selected original documents plus a manifest, distinct from the recovery backup format.
