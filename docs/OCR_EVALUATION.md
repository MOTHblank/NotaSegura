# Local receipt OCR evaluation

This is an opt-in, on-device evaluation, not a CI job and not a public dataset.
The repository does not contain real customer receipt images or accuracy scores.

## Prepare a private corpus

Put photographs and a JSON manifest under this Git-ignored directory:

    app/src/androidTest/assets/receipt_ocr_cases/

Do not force-add its contents. Receipt photographs may expose CNPJs, CPF numbers,
addresses, purchases, payment information and QR payloads. Use your own receipts,
obtain consent for third-party images, and redact sensitive details where possible
without erasing the target fields.

Example schema only (the referenced image is deliberately not provided):

    {
      "cases": [
        {
          "image": "receipt_001.jpg",
          "category": "faded",
          "expected": {
            "amountCents": 2890,
            "merchant": "MERCADO EXEMPLO LTDA",
            "purchaseDate": "2026-10-21"
          }
        }
      ]
    }

The amountCents field is an integer number of centavos. Use null to assert no
trustworthy amount was present. The expected merchant and purchase date are
optional; include them only when independently known. Dates use ISO YYYY-MM-DD.
Filenames may contain only ASCII letters, digits, underscores, hyphens and a
.jpg, .jpeg, .png or .webp extension. Batches are limited to 40 photographs,
each smaller than 16 MiB.

Challenge categories:
- clear: well lit, straight, legible thermal print
- faded: washed out or low-contrast ink
- qr: QR code near other information, access-key text or QR artifacts
- digits: thin, damaged or visually ambiguous price numerals
- perspective: tilted or geometrically distorted photographs
- mixed: multiple conditions at once

Ground truth must come from the original document or a separately verified record,
not the app's OCR prediction. Include receipts with missing/incorrect totals as
well as successful scans. Keep categories separate for comparisons.

## Run locally

Use Android Studio's instrumented test runner with this class:

    com.mothblank.notasegura.ReceiptOcrPhotoEvaluationTest

The class skips when the local fixture manifest is absent. It reads one private
image at a time through the same ReceiptOcrEngine.recognizeReceipt() path as
purchase entry and deletes each imported temporary file afterward. The bundled
OCR models run on the device; no cloud API is involved.

Filter local logcat by ReceiptOcrEvaluation. Only aggregate counters are printed,
with no individual predictions, names, CNPJs, raw OCR, QR payloads, or image IDs.

## Interpretation

For each annotated field the evaluation reports:
- n: annotated examples
- exact: recognized value equals ground truth
- wrong: a different value was suggested
- missing: no prediction
- autoCorrect: correct value meets the app's current autofill confidence policy
- autoWrong: incorrect value meets that same policy (important safety metric)
- reviewOnly: the value remained a manually reviewable suggestion

Useful rates include exact/n, missing/n, and autoWrong/n, computed per category.
These are observed counts, not confidence calibration. Compare the same private
corpus before and after modifying the parser or image processing. Prioritize
reducing autoWrong without sacrificing too much exact accuracy.

No reference scores or actual-photo evaluation results are claimed until private
fixtures have been supplied and the local runner has been executed.
