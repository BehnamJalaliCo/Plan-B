package com.behnamjalali.planb.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.behnamjalali.planb.core.model.AccentColor

internal object AccentPalette {
    private val light = mapOf(
        AccentColor.LAVENDER to AccentTones(Color(0xFFEAE6FD), Color(0xFF2E2470), Color(0xFF6B5BD2)),
        AccentColor.MINT to AccentTones(Color(0xFFD7F3EA), Color(0xFF0E4236), Color(0xFF23866F)),
        AccentColor.PEACH to AccentTones(Color(0xFFFFE6D8), Color(0xFF55230C), Color(0xFFC76638)),
        AccentColor.POWDER_BLUE to AccentTones(Color(0xFFDCEAF8), Color(0xFF123D63), Color(0xFF3B74AE)),
        AccentColor.ROSE to AccentTones(Color(0xFFFADDE6), Color(0xFF5A1530), Color(0xFFB8476F)),
        AccentColor.SAND to AccentTones(Color(0xFFF6EBCB), Color(0xFF4A3708), Color(0xFFA27A16)),
        AccentColor.SAGE to AccentTones(Color(0xFFE3EEDB), Color(0xFF233A17), Color(0xFF5B7F45)),
        AccentColor.SLATE to AccentTones(Color(0xFFE4E6EE), Color(0xFF232838), Color(0xFF5B6380)),
    )
    private val dark = mapOf(
        AccentColor.LAVENDER to AccentTones(Color(0xFF332B66), Color(0xFFE6E0FF), Color(0xFFB9ADFF)),
        AccentColor.MINT to AccentTones(Color(0xFF173F36), Color(0xFFCDF3E6), Color(0xFF86D9C0)),
        AccentColor.PEACH to AccentTones(Color(0xFF4F2A17), Color(0xFFFFE0D0), Color(0xFFFFB38E)),
        AccentColor.POWDER_BLUE to AccentTones(Color(0xFF1B3550), Color(0xFFD6E8FA), Color(0xFF9CC4EE)),
        AccentColor.ROSE to AccentTones(Color(0xFF4D2033), Color(0xFFFBD9E5), Color(0xFFF2A3BE)),
        AccentColor.SAND to AccentTones(Color(0xFF433817), Color(0xFFF6EBCB), Color(0xFFE2C46E)),
        AccentColor.SAGE to AccentTones(Color(0xFF26361F), Color(0xFFDDEBD3), Color(0xFFA6C990)),
        AccentColor.SLATE to AccentTones(Color(0xFF2A3044), Color(0xFFE2E5F0), Color(0xFFAEB6D2)),
    )

    fun tones(accent: AccentColor, dark: Boolean): AccentTones =
        (if (dark) this.dark else light).getValue(accent)
}
