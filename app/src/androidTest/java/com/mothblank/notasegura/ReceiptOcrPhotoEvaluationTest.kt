package com.mothblank.notasegura

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.data.ocr.ReceiptOcrEngine
import com.mothblank.notasegura.util.OcrConfidence
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local-only, opt-in evaluation of real receipt photos.
 *
 * No receipt images, OCR text, merchant names, CNPJs or individual predictions
 * are written to logcat. Only aggregate counts by challenge category appear.
 * Fixtures live in ignored androidTest assets and are never committed.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptOcrPhotoEvaluationTest {
    @Test
    fun evaluatePrivateReceiptPhotoCorpus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val target = instrumentation.targetContext
        val root = "receipt_ocr_cases"
        assumeTrue(
            "No local receipt fixtures supplied (see docs/OCR_EVALUATION.md)",
            "manifest.json" in assets.list(root).orEmpty()
        )

        val manifest = assets.open("$root/manifest.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val cases = manifest.getJSONArray("cases")
        require(cases.length() in 1..MAX_CASES) {
            "Use 1 to " + MAX_CASES + " real receipts per local evaluation batch."
        }
        val all = Aggregate()
        val byCategory = sortedMapOf<String, Aggregate>()
        val engine = ReceiptOcrEngine(target)

        for (index in 0 until cases.length()) {
            val record = cases.getJSONObject(index)
            val name = record.getString("image")
            require(IMAGE_NAME.matches(name)) { "Only simple JPEG/PNG/WebP filenames are accepted." }
            val category = record.getString("category").lowercase(Locale.ROOT)
            require(category in CATEGORIES) { "Unknown challenge category: " + category }
            val expected = record.getJSONObject("expected")

            val temporary = File.createTempFile(
                "receipt-ocr-eval-", "." + name.substringAfterLast('.'), target.cacheDir
            )
            try {
                assets.open("$root/$name").use { source ->
                    temporary.outputStream().use { destination ->
                        source.copyTo(destination)
                    }
                }
                require(temporary.length() in 1..MAX_IMAGE_BYTES) {
                    "Image is empty or exceeds local evaluation budget."
                }
                val prediction = runBlocking { engine.recognizeReceipt(temporary) }
                val group = byCategory.getOrPut(category) { Aggregate() }
                val expectedValue = optionalLong(expected, "amountCents")
                if (expected.has("amountCents")) {
                    all.amount.record(
                        expectedValue,
                        prediction.suggestions.purchaseValueCents,
                        prediction.suggestions.evidence["purchaseValueCents"]
                            ?.confidence == OcrConfidence.HIGH
                    )
                    group.amount.record(
                        expectedValue,
                        prediction.suggestions.purchaseValueCents,
                        prediction.suggestions.evidence["purchaseValueCents"]
                            ?.confidence == OcrConfidence.HIGH
                    )
                }
                if (expected.has("merchant")) {
                    val shouldAutofill = prediction.suggestions.evidence["merchant"]
                        ?.confidence in setOf(OcrConfidence.MEDIUM, OcrConfidence.HIGH)
                    val expectedMerchant = normalize(
                        expected.optString("merchant").takeUnless { expected.isNull("merchant") }
                    )
                    val actualMerchant = normalize(prediction.suggestions.merchant)
                    all.merchant.record(expectedMerchant, actualMerchant, shouldAutofill)
                    group.merchant.record(expectedMerchant, actualMerchant, shouldAutofill)
                }
                if (expected.has("purchaseDate")) {
                    val expectedDate = expected.optString("purchaseDate")
                        .takeUnless { expected.isNull("purchaseDate") }
                        ?.let(LocalDate::parse)
                    val actual = prediction.suggestions.purchaseDate
                    val shouldAutofill = prediction.suggestions.evidence["purchaseDate"]
                        ?.confidence in setOf(OcrConfidence.MEDIUM, OcrConfidence.HIGH)
                    all.date.record(expectedDate, actual, shouldAutofill)
                    group.date.record(expectedDate, actual, shouldAutofill)
                }
                all.cases++
                group.cases++
            } finally {
                temporary.delete()
            }
        }

        require(all.amount.evaluated > 0) { "Supply ground-truth amountCents for evaluation." }
        Log.i(TAG, "all: " + all.summary())
        byCategory.forEach { (category, aggregate) ->
            Log.i(TAG, category + ": " + aggregate.summary())
        }
        assertTrue(all.cases > 0)
    }

    private fun optionalLong(json: JSONObject, key: String): Long? =
        if (!json.has(key) || json.isNull(key)) null else json.getLong(key)

    private fun normalize(value: String?): String? = value
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        ?.replace(Regex("""\p{M}+"""), "")
        ?.uppercase(Locale.ROOT)
        ?.trim()
        ?.replace(Regex("""\s+"""), " ")

    private class FieldCounts {
        var evaluated = 0
        var correct = 0
        var incorrect = 0
        var missed = 0
        var autofillCorrect = 0
        var autofillWrong = 0
        var reviewOnly = 0

        fun <T> record(expected: T?, actual: T?, autofill: Boolean) {
            evaluated++
            when {
                actual == null -> missed++
                expected == actual -> correct++
                else -> incorrect++
            }
            when {
                actual == null -> Unit
                autofill && expected == actual -> autofillCorrect++
                autofill -> autofillWrong++
                else -> reviewOnly++
            }
        }

        override fun toString(): String =
            "n=" + evaluated + " exact=" + correct + " wrong=" + incorrect +
                " missing=" + missed + " autoCorrect=" + autofillCorrect +
                " autoWrong=" + autofillWrong + " reviewOnly=" + reviewOnly
    }

    private class Aggregate {
        var cases = 0
        val amount = FieldCounts()
        val merchant = FieldCounts()
        val date = FieldCounts()
        fun summary(): String =
            "cases=" + cases + " amount=[" + amount + "] merchant=[" +
                merchant + "] date=[" + date + "]"
    }

    private companion object {
        const val TAG = "ReceiptOcrEvaluation"
        const val MAX_CASES = 40
        const val MAX_IMAGE_BYTES = 16L * 1024 * 1024
        val CATEGORIES = setOf("clear", "faded", "qr", "digits", "perspective", "mixed")
        val IMAGE_NAME = Regex("""[A-Za-z0-9_-]{1,64}\.(?:jpe?g|png|webp)""", RegexOption.IGNORE_CASE)
    }
}
