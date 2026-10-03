package com.mothblank.notasegura

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOcrPackagingTest {

    @Suppress("DEPRECATION")
    @Test
    fun releaseContract_hasBundledOcrModelsAndNoNetworkPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertFalse(Manifest.permission.INTERNET in permissions)
        assertFalse(Manifest.permission.ACCESS_NETWORK_STATE in permissions)

        assertAssetPresent(context, "models/det/inference.onnx")
        assertAssetPresent(context, "models/rec/inference.onnx")
        context.assets.open("models/rec/inference.yml").bufferedReader().use { reader ->
            assertTrue(reader.readText().contains("character_dict:"))
        }
    }

    private fun assertAssetPresent(context: android.content.Context, path: String) {
        context.assets.open(path).use { input ->
            assertTrue("Bundled OCR model asset is missing or empty: $path", input.read() >= 0)
        }
    }
}
