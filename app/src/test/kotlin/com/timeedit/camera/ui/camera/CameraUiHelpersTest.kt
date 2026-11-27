/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraUiHelpersTest {

    @Test
    fun splitInPlaceRotationTokensSplitsWordsAndCjkCleanly() {
        assertEquals(listOf("Có", "tiếng"), splitInPlaceRotationTokens("Có tiếng"))
        assertEquals(listOf("Tắt", "tiếng"), splitInPlaceRotationTokens("Tắt tiếng"))
        assertEquals(listOf("Tắt"), splitInPlaceRotationTokens("Tắt"))
        assertEquals(listOf("3s"), splitInPlaceRotationTokens("3s"))
        assertEquals(listOf("Mic", "On"), splitInPlaceRotationTokens("Mic On"))
        assertEquals(listOf("开启", "录音"), splitInPlaceRotationTokens("开启录音"))
        assertEquals(listOf("静音"), splitInPlaceRotationTokens("静音"))
        assertEquals(emptyList<String>(), splitInPlaceRotationTokens("   "))
    }
}
