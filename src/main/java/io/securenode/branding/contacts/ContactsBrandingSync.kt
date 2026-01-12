package io.securenode.branding.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import io.securenode.branding.net.BrandingSyncItem
import io.securenode.branding.telemetry.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Contacts sync strategy (Option B):
 * - One SecureNode-managed contact per brandId (split into chunks if too many numbers)
 * - Adds numbers under that contact
 * - Never overwrites existing user contacts for a number (skips if number already exists elsewhere)
 * - Removes SecureNode-managed numbers/contacts that no longer exist in portal sync
 *
 * Host app must have READ_CONTACTS + WRITE_CONTACTS runtime permissions granted.
 */
internal class ContactsBrandingSync(
    private val context: Context,
    private val maxNumbersPerContact: Int,
    private val enablePhotos: Boolean
) {
    companion object {
        // Custom mimetype used to tag SecureNode-managed contacts for safe updates/cleanup.
        private const val SN_MIMETYPE = "vnd.android.cursor.item/vnd.io.securenode.branding"
        private const val SN_DATA_BRAND_ID = Data.DATA1
        private const val SN_DATA_CHUNK_INDEX = Data.DATA2
        private const val SN_DATA_LOGO_URL = Data.DATA3

        fun hasRequiredPermissions(context: Context): Boolean {
            val pm = context.packageManager
            val read = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val write = context.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
            // Some OEMs gate queries on READ, and writes on WRITE.
            return read && write && pm != null
        }
    }

    private data class ExistingSnContact(
        val rawContactId: Long,
        val markerDataId: Long,
        val brandId: String,
        val chunkIndex: Int,
        val logoUrl: String?
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val photoCacheDir: File by lazy {
        File(context.cacheDir, "sn_branding_photos").also { it.mkdirs() }
    }

    fun syncFromPortal(items: List<BrandingSyncItem>) {
        if (!hasRequiredPermissions(context)) {
            Logger.w("Contacts branding skipped: missing READ_CONTACTS/WRITE_CONTACTS")
            return
        }

        // Group by brandName (NOT brandId), because in global directory mode brand_id may be per-number.
        val brandingByBrand = items
            .filter { it.brandName.isNotBlank() && it.phoneE164.isNotBlank() }
            .groupBy { it.brandName.trim() }

        val desiredKeys = HashSet<String>() // "$groupId#$chunkIndex"
        val existing = queryExistingSnContacts()
        val existingByKey = existing.associateBy { key(it.brandId, it.chunkIndex) }

        // Upsert desired contacts.
        for ((brandNameRaw, brandItems) in brandingByBrand) {
            val brandName = brandNameRaw
            val logoUrl = brandItems.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl
            val numbers = brandItems.map { it.phoneE164 }.distinct()
            val chunks = numbers.chunked(maxNumbersPerContact.coerceAtLeast(1))
            val groupId = sha256Hex("brand:$brandName").take(16)

            for ((chunkIndex, chunkNumbers) in chunks.withIndex()) {
                val k = key(groupId, chunkIndex)
                desiredKeys.add(k)
                val displayName = if (chunkIndex == 0) brandName else "$brandName (${chunkIndex + 1})"
                val existingContact = existingByKey[k]
                if (existingContact == null) {
                    createSnContact(groupId, chunkIndex, displayName, logoUrl, chunkNumbers)
                } else {
                    updateSnContact(
                        rawContactId = existingContact.rawContactId,
                        markerDataId = existingContact.markerDataId,
                        brandId = groupId,
                        chunkIndex = chunkIndex,
                        displayName = displayName,
                        logoUrl = logoUrl,
                        desiredNumbers = chunkNumbers,
                        existingLogoUrl = existingContact.logoUrl
                    )
                }
            }
        }

        // Remove contacts no longer present in portal directory.
        for (c in existing) {
            val k = key(c.brandId, c.chunkIndex)
            if (!desiredKeys.contains(k)) {
                deleteRawContact(c.rawContactId)
            }
        }
    }

    private fun key(brandId: String, chunkIndex: Int): String = "$brandId#$chunkIndex"

    private fun queryExistingSnContacts(): List<ExistingSnContact> {
        val out = ArrayList<ExistingSnContact>()
        val cr = context.contentResolver

        val projection = arrayOf(Data._ID, Data.RAW_CONTACT_ID, SN_DATA_BRAND_ID, SN_DATA_CHUNK_INDEX, SN_DATA_LOGO_URL)
        val selection = "${Data.MIMETYPE}=?"
        val selectionArgs = arrayOf(SN_MIMETYPE)

        cr.query(Data.CONTENT_URI, projection, selection, selectionArgs, null)?.use { cur ->
            val dataIdIdx = cur.getColumnIndex(Data._ID)
            val rawIdx = cur.getColumnIndex(Data.RAW_CONTACT_ID)
            val brandIdx = cur.getColumnIndex(SN_DATA_BRAND_ID)
            val chunkIdx = cur.getColumnIndex(SN_DATA_CHUNK_INDEX)
            val logoIdx = cur.getColumnIndex(SN_DATA_LOGO_URL)
            while (cur.moveToNext()) {
                val dataId = cur.getLong(dataIdIdx)
                val rawId = cur.getLong(rawIdx)
                val brandId = cur.getString(brandIdx) ?: continue
                val chunk = (cur.getString(chunkIdx) ?: "0").toIntOrNull() ?: 0
                val logoUrl = cur.getString(logoIdx)
                if (brandId.isBlank()) continue
                out.add(ExistingSnContact(rawId, dataId, brandId, chunk, logoUrl))
            }
        }
        return out
    }

    private fun createSnContact(
        brandId: String,
        chunkIndex: Int,
        displayName: String,
        logoUrl: String?,
        numbers: List<String>
    ) {
        val ops = ArrayList<ContentProviderOperation>()

        // RawContact (local/phone storage): account fields omitted.
        ops.add(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        // Name
        ops.add(
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, displayName)
                .build()
        )

        // Marker row
        ops.add(
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, SN_MIMETYPE)
                .withValue(SN_DATA_BRAND_ID, brandId)
                .withValue(SN_DATA_CHUNK_INDEX, chunkIndex.toString())
                .withValue(SN_DATA_LOGO_URL, logoUrl)
                .build()
        )

        // URL marker (less visible) - matches iOS style (for cross-platform parity).
        ops.add(
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                .withValue(Website.URL, "securenode://managed?group=$brandId&v=1")
                .withValue(Website.TYPE, Website.TYPE_CUSTOM)
                .withValue(Website.LABEL, "SecureNode")
                .build()
        )

        // Photo (best-effort)
        if (enablePhotos && !logoUrl.isNullOrBlank()) {
            val bytes = downloadAndPreparePhoto(logoUrl)
            if (bytes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        .withValue(Photo.PHOTO, bytes)
                        .build()
                )
            }
        }

        // Phones
        for (n in numbers) {
            if (n.isBlank()) continue
            if (numberExistsElsewhere(n)) continue
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, n)
                    .withValue(Phone.TYPE, Phone.TYPE_OTHER)
                    .build()
            )
        }

        applyBatchSafely(ops, "createSnContact($brandId#$chunkIndex)")
    }

    private fun updateSnContact(
        rawContactId: Long,
        markerDataId: Long,
        brandId: String,
        chunkIndex: Int,
        displayName: String,
        logoUrl: String?,
        desiredNumbers: List<String>,
        existingLogoUrl: String?
    ) {
        // Ensure name matches and numbers match desired.
        val existingPhones = queryPhoneRows(rawContactId)
        val existingByNormalized = existingPhones.associateBy { normalizeForCompare(it.number) }
        val desiredNormalized = desiredNumbers.map { normalizeForCompare(it) }.toSet()

        val ops = ArrayList<ContentProviderOperation>()

        // Update display name (StructuredName) - best effort.
        val structuredNameDataId = queryStructuredNameDataId(rawContactId)
        if (structuredNameDataId != null) {
            ops.add(
                ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection("${Data._ID}=?", arrayOf(structuredNameDataId.toString()))
                    .withValue(StructuredName.DISPLAY_NAME, displayName)
                    .build()
            )
        }

        // Update marker logo_url if changed
        if (logoUrl != existingLogoUrl) {
            ops.add(
                ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection("${Data._ID}=?", arrayOf(markerDataId.toString()))
                    .withValue(SN_DATA_LOGO_URL, logoUrl)
                    .build()
            )
        }

        // Add missing
        for (n in desiredNumbers) {
            if (n.isBlank()) continue
            val norm = normalizeForCompare(n)
            if (norm.isBlank()) continue
            if (existingByNormalized.containsKey(norm)) continue
            if (numberExistsElsewhere(n)) continue
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, n)
                    .withValue(Phone.TYPE, Phone.TYPE_OTHER)
                    .build()
            )
        }

        // Remove extras (only from our SN-managed raw contact)
        for (row in existingPhones) {
            val norm = normalizeForCompare(row.number)
            if (!desiredNormalized.contains(norm)) {
                ops.add(
                    ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection("${Data._ID}=?", arrayOf(row.dataId.toString()))
                        .build()
                )
            }
        }

        if (ops.isNotEmpty()) {
            applyBatchSafely(ops, "updateSnContact($brandId#$chunkIndex)")
        }

        // Photo update outside batch (we need current rawContactId, and OEM providers can be picky).
        if (enablePhotos && !logoUrl.isNullOrBlank() && logoUrl != existingLogoUrl) {
            val bytes = downloadAndPreparePhoto(logoUrl)
            if (bytes != null) {
                upsertPhoto(rawContactId, bytes)
            }
        }
    }

    private data class PhoneRow(val dataId: Long, val number: String)

    private fun queryPhoneRows(rawContactId: Long): List<PhoneRow> {
        val out = ArrayList<PhoneRow>()
        val cr = context.contentResolver
        val projection = arrayOf(Data._ID, Phone.NUMBER)
        val selection = "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?"
        val args = arrayOf(rawContactId.toString(), Phone.CONTENT_ITEM_TYPE)
        cr.query(Data.CONTENT_URI, projection, selection, args, null)?.use { cur ->
            val idIdx = cur.getColumnIndex(Data._ID)
            val numIdx = cur.getColumnIndex(Phone.NUMBER)
            while (cur.moveToNext()) {
                val id = cur.getLong(idIdx)
                val num = cur.getString(numIdx) ?: ""
                out.add(PhoneRow(id, num))
            }
        }
        return out
    }

    private fun queryStructuredNameDataId(rawContactId: Long): Long? {
        val cr = context.contentResolver
        val projection = arrayOf(Data._ID)
        val selection = "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?"
        val args = arrayOf(rawContactId.toString(), StructuredName.CONTENT_ITEM_TYPE)
        cr.query(Data.CONTENT_URI, projection, selection, args, null)?.use { cur ->
            val idIdx = cur.getColumnIndex(Data._ID)
            if (cur.moveToFirst()) return cur.getLong(idIdx)
        }
        return null
    }

    private fun deleteRawContact(rawContactId: Long) {
        try {
            val uri: Uri = Uri.withAppendedPath(RawContacts.CONTENT_URI, rawContactId.toString())
            context.contentResolver.delete(uri, null, null)
        } catch (t: Throwable) {
            Logger.w("Contacts branding: failed deleting raw contact $rawContactId", t)
        }
    }

    private fun upsertPhoto(rawContactId: Long, bytes: ByteArray) {
        val cr = context.contentResolver
        val existingId = queryPhotoDataId(rawContactId)
        try {
            if (existingId != null) {
                cr.update(
                    Data.CONTENT_URI,
                    android.content.ContentValues().apply { put(Photo.PHOTO, bytes) },
                    "${Data._ID}=?",
                    arrayOf(existingId.toString())
                )
            } else {
                cr.insert(
                    Data.CONTENT_URI,
                    android.content.ContentValues().apply {
                        put(Data.RAW_CONTACT_ID, rawContactId)
                        put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        put(Photo.PHOTO, bytes)
                    }
                )
            }
        } catch (t: Throwable) {
            Logger.w("Contacts branding: failed updating photo", t)
        }
    }

    private fun queryPhotoDataId(rawContactId: Long): Long? {
        val cr = context.contentResolver
        val projection = arrayOf(Data._ID)
        val selection = "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?"
        val args = arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE)
        cr.query(Data.CONTENT_URI, projection, selection, args, null)?.use { cur ->
            val idIdx = cur.getColumnIndex(Data._ID)
            if (cur.moveToFirst()) return cur.getLong(idIdx)
        }
        return null
    }

    private fun numberExistsElsewhere(number: String): Boolean {
        val cr = context.contentResolver
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            cr.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { cur ->
                cur.moveToFirst()
            } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun applyBatchSafely(ops: ArrayList<ContentProviderOperation>, label: String) {
        try {
            // Avoid huge batches on OEM devices.
            val maxOps = 350
            val resolver: ContentResolver = context.contentResolver
            var i = 0
            while (i < ops.size) {
                val end = (i + maxOps).coerceAtMost(ops.size)
                val sub = ArrayList(ops.subList(i, end))
                resolver.applyBatch(ContactsContract.AUTHORITY, sub)
                i = end
            }
        } catch (t: Throwable) {
            Logger.w("Contacts branding batch failed: $label", t)
        }
    }

    private fun normalizeForCompare(n: String): String {
        val s = n.trim()
        if (s.isEmpty()) return ""
        val sb = StringBuilder(s.length)
        for (ch in s) {
            if (ch.isDigit()) sb.append(ch)
            else if (ch == '+' && sb.isEmpty()) sb.append(ch)
        }
        return sb.toString()
    }

    private fun downloadAndPreparePhoto(url: String): ByteArray? {
        val safeUrl = url.trim()
        if (!safeUrl.startsWith("https://") && !safeUrl.startsWith("http://")) return null

        val cacheFile = File(photoCacheDir, sha256Hex(safeUrl) + ".jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return try { cacheFile.readBytes() } catch (_: Throwable) { null }
        }

        return try {
            val req = Request.Builder()
                .url(safeUrl)
                .get()
                .header("Accept", "image/*")
                .build()
            val resp = http.newCall(req).execute()
            resp.use {
                if (!it.isSuccessful) return null
                val body = it.body ?: return null
                val bytes = body.bytes()
                if (bytes.isEmpty() || bytes.size > 1_500_000) return null

                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                val scaled = scaleToMax(bmp, 256)
                val out = java.io.ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val outBytes = out.toByteArray()
                if (outBytes.isNotEmpty()) {
                    try { cacheFile.writeBytes(outBytes) } catch (_: Throwable) {}
                }
                outBytes
            }
        } catch (t: Throwable) {
            Logger.w("Contacts branding: photo download failed", t)
            null
        }
    }

    private fun scaleToMax(src: Bitmap, max: Int): Bitmap {
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val longest = maxOf(w, h)
        if (longest <= max) return src
        val scale = max.toFloat() / longest.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return try { Bitmap.createScaledBitmap(src, nw, nh, true) } catch (_: Throwable) { src }
    }

    private fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}

