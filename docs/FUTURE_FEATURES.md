# Future feature ideas

## Encrypted backup and restore

Local-only storage protects privacy but creates a device-loss risk. Add an explicit, user-controlled backup/restore flow. Prefer a portable encrypted archive over implicit account coupling.

## Better receipt understanding

Expand OCR into structured suggestions for merchant, purchase total, product/model/serial identifiers, and purchase date. OCR output should always remain reviewable before it mutates saved data.

## Multiple document attachments

A purchase may have a receipt photo, invoice PDF, warranty certificate, and repair record. Model attachments as their own records rather than adding more nullable paths to `WarrantyItem`.

## Reminder customization

Allow users to choose reminder lead times and enable/disable warranty and payment reminder categories independently.

## Searchable archive

Index structured fields and OCR text so old proof-of-purchase records can be found by merchant, product, model, serial number, category, or recognized receipt text.

## Export and recovery

The existing PDF is a human-readable summary, not a backup. Keep report export and backup/restore as separate concepts with different formats and guarantees.
