package com.mothblank.notasegura.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.util.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val purchases: Int,
    val attachments: Int,
    val payments: Int
)

private data class BackupSnapshot(
    val purchases: List<Purchase>,
    val attachments: List<Attachment>,
    val payments: List<Payment>
)

private data class CopiedEntry(
    val sha256: String,
    val bytes: Long
)

class BackupArchiveManager(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        const val MIN_PASSWORD_LENGTH = MIN_BACKUP_PASSWORD_LENGTH

        private const val FORMAT_NAME = "NotaSeguraBackup"
        private const val FORMAT_VERSION = 1
        private const val SCHEMA_VERSION = 1
        private const val MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L
        private const val MAX_JSON_BYTES = 8 * 1024 * 1024
    }

    suspend fun createBackup(
        destination: Uri,
        password: String
    ): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(password.length >= MIN_PASSWORD_LENGTH) {
                    "A senha do backup deve ter pelo menos $MIN_PASSWORD_LENGTH caracteres."
                }
                val snapshot = database.withTransaction {
                    BackupSnapshot(
                        purchases = database.purchaseDao().getPurchasesSnapshot(),
                        attachments = database.purchaseDao().getAttachmentsSnapshot(),
                        payments = database.paymentDao().getPaymentsSnapshot()
                    )
                }
                val purchases = snapshot.purchases
                val attachments = snapshot.attachments
                val payments = snapshot.payments

                val attachmentFiles = attachments.associateWith { attachment ->
                    val file = File(attachment.filePath)
                    require(file.isFile) {
                        "Documento ausente para ${attachment.displayName ?: attachment.id}."
                    }
                    file
                }

                val dataJson = createDataJson(purchases, attachments, payments)
                val dataBytes = dataJson.toString().toByteArray(Charsets.UTF_8)

                val fileChecksums = linkedMapOf(
                    "data.json" to sha256(dataBytes)
                )
                attachments.forEach { attachment ->
                    val file = attachmentFiles.getValue(attachment)
                    fileChecksums[archiveEntryFor(attachment)] = FileStorageManager.sha256(file)
                }

                val manifest = JSONObject()
                    .put("format", FORMAT_NAME)
                    .put("version", FORMAT_VERSION)
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("createdAt", System.currentTimeMillis())
                    .put(
                        "files",
                        JSONArray().apply {
                            fileChecksums.forEach { (entry, checksum) ->
                                put(
                                    JSONObject()
                                        .put("entry", entry)
                                        .put("sha256", checksum)
                                )
                            }
                        }
                    )

                val output = context.contentResolver.openOutputStream(destination, "w")
                    ?: error("Não foi possível abrir o destino do backup.")

                output.use { raw ->
                    BackupCrypto.openEncryptedOutput(
                        raw.buffered(),
                        password.toCharArray()
                    ).use { encrypted ->
                        ZipOutputStream(encrypted).use { zip ->
                            writeBytes(
                                zip,
                                "manifest.json",
                                manifest.toString().toByteArray(Charsets.UTF_8)
                            )
                            writeBytes(zip, "data.json", dataBytes)

                            attachments.forEach { attachment ->
                                val file = attachmentFiles.getValue(attachment)
                                zip.putNextEntry(ZipEntry(archiveEntryFor(attachment)))
                                file.inputStream().buffered().use { input ->
                                    input.copyTo(zip)
                                }
                                zip.closeEntry()
                            }
                        }
                    }
                }

                BackupSummary(
                    purchases = purchases.size,
                    attachments = attachments.size,
                    payments = payments.size
                )
            }
        }

    suspend fun restoreBackup(
        source: Uri,
        password: String
    ): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(password.length >= MIN_PASSWORD_LENGTH) {
                    "A senha do backup deve ter pelo menos $MIN_PASSWORD_LENGTH caracteres."
                }
                val tempArchive = copyBackupToCache(source, password)
                val restoreDir = File(
                    context.filesDir,
                    "attachments/restore_${System.currentTimeMillis()}_${UUID.randomUUID()}"
                )
                val oldAttachments = database.purchaseDao().getAttachmentsSnapshot()

                try {
                    ZipFile(tempArchive).use { zip ->
                        val entries = zip.entries().asSequence().toList()
                        require(entries.map { it.name }.distinct().size == entries.size) {
                            "Backup contém entradas duplicadas."
                        }
                        entries.forEach { validateEntryName(it.name) }

                        val manifestEntry = zip.getEntry("manifest.json")
                            ?: error("Backup sem manifest.json.")
                        val manifest = JSONObject(readSmallEntry(zip, manifestEntry))
                        require(manifest.getString("format") == FORMAT_NAME) {
                            "Formato de backup inválido."
                        }
                        require(manifest.getInt("version") == FORMAT_VERSION) {
                            "Versão de backup não suportada."
                        }

                        val expectedChecksums = parseManifestChecksums(manifest)
                        val dataEntry = zip.getEntry("data.json")
                            ?: error("Backup sem data.json.")
                        val dataBytes = readSmallEntryBytes(zip, dataEntry)
                        require(sha256(dataBytes) == expectedChecksums["data.json"]) {
                            "Os dados do backup falharam na verificação de integridade."
                        }

                        val data = JSONObject(dataBytes.toString(Charsets.UTF_8))
                        val purchases = parsePurchases(data.getJSONArray("purchases"))
                        val payments = parsePayments(data.getJSONArray("payments"))
                        val attachmentRecords = data.getJSONArray("attachments")

                        val purchaseIds = purchases.mapTo(hashSetOf()) { it.id }
                        require(restoreDir.mkdirs() || restoreDir.isDirectory) {
                            "Não foi possível preparar a área de restauração."
                        }
                        var extractedBytes = 0L

                        val restoredAttachments = buildList {
                            for (index in 0 until attachmentRecords.length()) {
                                val item = attachmentRecords.getJSONObject(index)
                                val purchaseId = item.getString("purchaseId")
                                validateRecordId(purchaseId)
                                require(purchaseId in purchaseIds) {
                                    "Documento aponta para uma compra inexistente."
                                }

                                val archiveEntry = item.getString("archiveEntry")
                                validateEntryName(archiveEntry)
                                val expectedChecksum = expectedChecksums[archiveEntry]
                                    ?: error("Checksum ausente para $archiveEntry.")
                                val entry = zip.getEntry(archiveEntry)
                                    ?: error("Documento ausente no backup: $archiveEntry.")

                                val mimeType = item.getString("mimeType")
                                val displayName = item.optNullableString("displayName")
                                val extension = FileStorageManager.extensionFor(mimeType, displayName)
                                val id = item.getString("id")
                                validateRecordId(id)
                                val destination = File(restoreDir, "${UUID.randomUUID()}.$extension")

                                val copied = copyEntryAndDigest(
                                    zip = zip,
                                    entry = entry,
                                    destination = destination,
                                    maxBytes = MAX_ARCHIVE_BYTES - extractedBytes
                                )
                                extractedBytes += copied.bytes
                                val actualChecksum = copied.sha256
                                require(actualChecksum == expectedChecksum) {
                                    "Documento corrompido no backup: ${displayName ?: id}."
                                }

                                add(
                                    Attachment(
                                        id = id,
                                        purchaseId = purchaseId,
                                        mimeType = mimeType,
                                        type = item.getString("type"),
                                        filePath = destination.absolutePath,
                                        displayName = displayName,
                                        sha256 = actualChecksum,
                                        ocrText = item.optNullableString("ocrText"),
                                        createdAt = item.getLong("createdAt")
                                    )
                                )
                            }
                        }

                        database.withTransaction {
                            val purchaseDao = database.purchaseDao()
                            val paymentDao = database.paymentDao()

                            purchaseDao.clearAttachments()
                            purchaseDao.clearPurchases()
                            paymentDao.clearPayments()

                            if (purchases.isNotEmpty()) {
                                purchaseDao.insertPurchases(purchases)
                            }
                            if (restoredAttachments.isNotEmpty()) {
                                purchaseDao.insertAttachments(restoredAttachments)
                            }
                            if (payments.isNotEmpty()) {
                                paymentDao.insertPayments(payments)
                            }
                        }

                        oldAttachments.forEach { old ->
                            FileStorageManager.deleteManagedFile(context, old.filePath)
                        }

                        BackupSummary(
                            purchases = purchases.size,
                            attachments = restoredAttachments.size,
                            payments = payments.size
                        )
                    }
                } catch (error: Throwable) {
                    restoreDir.deleteRecursively()
                    throw error
                } finally {
                    tempArchive.delete()
                }
            }
        }

    private fun createDataJson(
        purchases: List<Purchase>,
        attachments: List<Attachment>,
        payments: List<Payment>
    ): JSONObject {
        return JSONObject()
            .put(
                "purchases",
                JSONArray().apply {
                    purchases.forEach { purchase ->
                        put(
                            JSONObject()
                                .put("id", purchase.id)
                                .put("productName", purchase.productName)
                                .putNullable("merchant", purchase.merchant)
                                .putNullable("purchaseValueCents", purchase.purchaseValueCents)
                                .put("purchaseDate", purchase.purchaseDate.toEpochDay())
                                .putNullable("warrantyEndDate", purchase.warrantyEndDate?.toEpochDay())
                                .put("category", purchase.category)
                                .putNullable("modelNumber", purchase.modelNumber)
                                .putNullable("serialNumber", purchase.serialNumber)
                                .put("notes", purchase.notes)
                                .put("createdAt", purchase.createdAt)
                                .put("updatedAt", purchase.updatedAt)
                        )
                    }
                }
            )
            .put(
                "attachments",
                JSONArray().apply {
                    attachments.forEach { attachment ->
                        put(
                            JSONObject()
                                .put("id", attachment.id)
                                .put("purchaseId", attachment.purchaseId)
                                .put("mimeType", attachment.mimeType)
                                .put("type", attachment.type)
                                .putNullable("displayName", attachment.displayName)
                                .putNullable("sha256", attachment.sha256)
                                .putNullable("ocrText", attachment.ocrText)
                                .put("createdAt", attachment.createdAt)
                                .put("archiveEntry", archiveEntryFor(attachment))
                        )
                    }
                }
            )
            .put(
                "payments",
                JSONArray().apply {
                    payments.forEach { payment ->
                        put(
                            JSONObject()
                                .put("id", payment.id)
                                .put("title", payment.title)
                                .put("amountCents", payment.amountCents)
                                .put("dueDate", payment.dueDate.toEpochDay())
                                .put("isPaid", payment.isPaid)
                                .putNullable("paidAt", payment.paidAt?.toEpochDay())
                                .putNullable("recurrenceMonths", payment.recurrenceMonths)
                                .putNullable("recurrenceAnchorDay", payment.recurrenceAnchorDay)
                                .putNullable("seriesId", payment.seriesId)
                                .putNullable("generatedFromId", payment.generatedFromId)
                        )
                    }
                }
            )
    }

    private fun parsePurchases(array: JSONArray): List<Purchase> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            validateRecordId(id)
            add(
                Purchase(
                    id = id,
                    productName = item.getString("productName"),
                    merchant = item.optNullableString("merchant"),
                    purchaseValueCents = item.optNullableLong("purchaseValueCents"),
                    purchaseDate = LocalDate.ofEpochDay(item.getLong("purchaseDate")),
                    warrantyEndDate = item.optNullableLong("warrantyEndDate")?.let { LocalDate.ofEpochDay(it) },
                    category = item.optString("category", ""),
                    modelNumber = item.optNullableString("modelNumber"),
                    serialNumber = item.optNullableString("serialNumber"),
                    notes = item.optString("notes", ""),
                    createdAt = item.getLong("createdAt"),
                    updatedAt = item.getLong("updatedAt")
                )
            )
        }
    }

    private fun parsePayments(array: JSONArray): List<Payment> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            validateRecordId(id)
            val seriesId = item.optNullableString("seriesId")?.also(::validateRecordId)
            val generatedFromId = item.optNullableString("generatedFromId")?.also(::validateRecordId)
            add(
                Payment(
                    id = id,
                    title = item.getString("title"),
                    amountCents = item.getLong("amountCents"),
                    dueDate = LocalDate.ofEpochDay(item.getLong("dueDate")),
                    isPaid = item.getBoolean("isPaid"),
                    paidAt = item.optNullableLong("paidAt")?.let { LocalDate.ofEpochDay(it) },
                    recurrenceMonths = item.optNullableInt("recurrenceMonths"),
                    recurrenceAnchorDay = item.optNullableInt("recurrenceAnchorDay"),
                    seriesId = seriesId,
                    generatedFromId = generatedFromId
                )
            )
        }
    }

    private fun copyBackupToCache(uri: Uri, password: String): File {
        val temp = File(context.cacheDir, "restore_${UUID.randomUUID()}.notasegura")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Não foi possível abrir o backup.")

            input.buffered().use { source ->
                FileOutputStream(temp).buffered().use { output ->
                    BackupCrypto.decrypt(
                        input = source,
                        output = output,
                        password = password.toCharArray(),
                        maxPlaintextBytes = MAX_ARCHIVE_BYTES
                    )
                }
            }
            return temp
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun parseManifestChecksums(manifest: JSONObject): Map<String, String> {
        val files = manifest.getJSONArray("files")
        return buildMap {
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                val entry = item.getString("entry")
                validateEntryName(entry)
                require(put(entry, item.getString("sha256")) == null) {
                    "Manifesto contém entradas duplicadas."
                }
            }
        }
    }

    private fun archiveEntryFor(attachment: Attachment): String {
        val extension = FileStorageManager.extensionFor(
            attachment.mimeType,
            attachment.displayName
        )
        return "attachments/${attachment.id}.$extension"
    }

    private fun validateRecordId(id: String) {
        require(id.matches(Regex("""[A-Za-z0-9._-]{1,128}"""))) {
            "Identificador inválido no backup."
        }
    }

    private fun validateEntryName(name: String) {
        require(
            name == "manifest.json" ||
                name == "data.json" ||
                name.matches(Regex("""attachments/[A-Za-z0-9._-]+"""))
        ) {
            "Entrada inválida no backup: $name"
        }
        require(!name.contains("..") && !name.contains('\\')) {
            "Caminho inválido no backup."
        }
    }

    private fun readSmallEntry(zip: ZipFile, entry: ZipEntry): String =
        readSmallEntryBytes(zip, entry).toString(Charsets.UTF_8)

    private fun readSmallEntryBytes(zip: ZipFile, entry: ZipEntry): ByteArray {
        require(entry.size < 0 || entry.size <= MAX_JSON_BYTES) {
            "Arquivo de metadados excessivamente grande."
        }
        zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= MAX_JSON_BYTES) {
                    "Arquivo de metadados excessivamente grande."
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun copyEntryAndDigest(
        zip: ZipFile,
        entry: ZipEntry,
        destination: File,
        maxBytes: Long
    ): CopiedEntry {
        require(maxBytes >= 0L) {
            "Backup excede o tamanho máximo permitido."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        zip.getInputStream(entry).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    require(total <= maxBytes) {
                        "Backup expandido excede o tamanho máximo permitido."
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
            }
        }
        return CopiedEntry(
            sha256 = digest.digest().toHex(),
            bytes = total
        )
    }

    private fun writeBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (isNull(name)) null else getInt(name)
}
