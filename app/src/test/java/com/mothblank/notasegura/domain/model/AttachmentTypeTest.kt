package com.mothblank.notasegura.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentTypeTest {

    @Test
    fun normalizeKeepsSupportedTypes() {
        AttachmentType.supported.forEach { type ->
            assertEquals(type, AttachmentType.normalize(type))
        }
    }

    @Test
    fun normalizeMapsUnknownLegacyValuesToOther() {
        assertEquals(AttachmentType.OTHER, AttachmentType.normalize("UNKNOWN"))
        assertEquals(AttachmentType.OTHER, AttachmentType.normalize(""))
    }
}
