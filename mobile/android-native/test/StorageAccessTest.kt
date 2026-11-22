package com.fraudshield.app

import com.fraudshield.app.StorageAccess.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.8: storage permission per Android version. */
class StorageAccessTest {
    @Test fun android11to14UseAllFilesAccessOnly() {
        for (sdk in listOf(30, 31, 32, 33, 34)) {
            assertTrue(StorageAccess.hasAccess(sdk, isExternalStorageManager = true, readGranted = false, writeGranted = false))
            assertFalse(StorageAccess.hasAccess(sdk, isExternalStorageManager = false, readGranted = true, writeGranted = true))
            assertEquals(Request.ALL_FILES_SETTINGS, StorageAccess.request(sdk))
        }
    }

    @Test fun android8to10AreNoLongerAlwaysGranted() {
        for (sdk in 26..29) {
            assertFalse("sdk $sdk nothing granted", StorageAccess.hasAccess(sdk, false, readGranted = false, writeGranted = false))
            assertFalse("sdk $sdk read only", StorageAccess.hasAccess(sdk, false, readGranted = true, writeGranted = false))
            assertTrue("sdk $sdk both", StorageAccess.hasAccess(sdk, false, readGranted = true, writeGranted = true))
            assertEquals(Request.RUNTIME_PROMPT, StorageAccess.request(sdk))
        }
    }

    @Test fun userTappingDenyInTheDialogIsNotSentToSettings() {
        assertFalse(StorageAccess.openAppSettingsInstead(granted = false, elapsedMs = 1_800))
    }

    @Test fun instantRefusalMeansDontAskAgainSoAppInfoOpens() {
        assertTrue(StorageAccess.openAppSettingsInstead(granted = false, elapsedMs = 40))
    }

    @Test fun grantedNeverOpensSettings() {
        assertFalse(StorageAccess.openAppSettingsInstead(granted = true, elapsedMs = 10))
    }
}
