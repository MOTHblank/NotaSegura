package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryNormalizerTest {
    @Test
    fun equivalentSpellingsShareOneKey() {
        assertEquals(
            CategoryNormalizer.key("Eletrônicos"),
            CategoryNormalizer.key("  eletronicos  ")
        )
        assertEquals(
            CategoryNormalizer.key("Casa & Cozinha"),
            CategoryNormalizer.key("casa-cozinha")
        )
    }

    @Test
    fun canonicalDisplayReusesExistingSpelling() {
        assertEquals(
            "Eletrônicos",
            CategoryNormalizer.canonicalDisplay(
                "eletronicos",
                listOf("Eletrônicos", "Casa")
            )
        )
    }

    @Test
    fun distinctDisplayCollapsesEquivalentCategories() {
        assertEquals(
            listOf("Casa", "Eletrônicos"),
            CategoryNormalizer.distinctDisplay(
                listOf("Eletronicos", "Eletrônicos", "casa", "Casa")
            )
        )
    }
}
