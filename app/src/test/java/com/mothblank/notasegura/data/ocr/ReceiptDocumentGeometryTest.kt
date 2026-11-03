package com.mothblank.notasegura.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptDocumentGeometryTest {
    @Test
    fun visibleModeratelySkewedPortraitReceiptIsAcceptedInAnyCornerOrder() {
        val corners = listOf(
            PaperCorner(670.0, 1060.0),
            PaperCorner(235.0, 90.0),
            PaperCorner(205.0, 1080.0),
            PaperCorner(640.0, 120.0)
        )
        val accepted = ReceiptDocumentGeometry.assess(corners, 900, 1200)
        assertNotNull(accepted)
        assertEquals(PaperCorner(235.0, 90.0), accepted!!.corners.first())
        assertTrue(accepted.width > 400.0)
        assertTrue(accepted.height > 900.0)
    }

    @Test
    fun rejectsPaperTouchingFrameBecauseCornersAreIncomplete() {
        val corners = listOf(
            PaperCorner(2.0, 20.0),
            PaperCorner(897.0, 20.0),
            PaperCorner(897.0, 1180.0),
            PaperCorner(2.0, 1180.0)
        )
        assertNull(ReceiptDocumentGeometry.assess(corners, 900, 1200))
    }

    @Test
    fun rejectsSmallRectangularObjectsAndTextBoxes() {
        val corners = listOf(
            PaperCorner(300.0, 300.0),
            PaperCorner(500.0, 300.0),
            PaperCorner(500.0, 500.0),
            PaperCorner(300.0, 500.0)
        )
        assertNull(ReceiptDocumentGeometry.assess(corners, 900, 1200))
    }

    @Test
    fun rejectsSeverelyForeshortenedDocuments() {
        val corners = listOf(
            PaperCorner(200.0, 100.0),
            PaperCorner(680.0, 100.0),
            PaperCorner(460.0, 1080.0),
            PaperCorner(420.0, 1080.0)
        )
        assertNull(ReceiptDocumentGeometry.assess(corners, 900, 1200))
    }

    @Test
    fun rejectsConcaveContours() {
        val corners = listOf(
            PaperCorner(200.0, 100.0),
            PaperCorner(680.0, 100.0),
            PaperCorner(350.0, 420.0),
            PaperCorner(200.0, 1090.0)
        )
        assertNull(ReceiptDocumentGeometry.assess(corners, 900, 1200))
    }

    @Test
    fun rejectsNonFinitePointsAndDuplicateCorners() {
        assertNull(
            ReceiptDocumentGeometry.assess(
                listOf(
                    PaperCorner(Double.NaN, 100.0),
                    PaperCorner(680.0, 100.0),
                    PaperCorner(680.0, 1000.0),
                    PaperCorner(200.0, 1000.0)
                ), 900, 1200
            )
        )
        assertNull(
            ReceiptDocumentGeometry.assess(
                listOf(
                    PaperCorner(200.0, 100.0),
                    PaperCorner(680.0, 100.0),
                    PaperCorner(680.0, 100.0),
                    PaperCorner(200.0, 1000.0)
                ), 900, 1200
            )
        )
    }
}
