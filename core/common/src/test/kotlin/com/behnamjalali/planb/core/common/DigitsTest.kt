package com.behnamjalali.planb.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DigitsTest {
    @Test
    fun toPersian_onlyChangesDigits() {
        assertThat(Digits.toPersian("v1.0 – 25 min")).isEqualTo("v۱.۰ – ۲۵ min")
    }

    @Test
    fun toLatin_handlesPersianAndArabic() {
        assertThat(Digits.toLatin("۱۲۳٤٥")).isEqualTo("12345")
    }

    @Test
    fun formatter_respectsPreference() {
        assertThat(NumberFormatter(persianDigits = true).format(1405)).isEqualTo("۱۴۰۵")
        assertThat(NumberFormatter(persianDigits = false).format(1405)).isEqualTo("1405")
        assertThat(NumberFormatter(persianDigits = true).percent(0.5f)).isEqualTo("۵۰٪")
        assertThat(NumberFormatter(persianDigits = false).format(2.50, 2)).isEqualTo("2.5")
        assertThat(NumberFormatter(persianDigits = true).format(2.5)).isEqualTo("۲٫۵")
        assertThat(NumberFormatter(persianDigits = true).twoDigits(7)).isEqualTo("۰۷")
    }

    @Test
    fun formatDouble_roundsDecimalValue_andHandlesNonFinite() {
        val latin = NumberFormatter(persianDigits = false)
        assertThat(latin.format(0.15)).isEqualTo("0.2")
        assertThat(latin.format(2.675, 2)).isEqualTo("2.68")
        assertThat(latin.format(Double.POSITIVE_INFINITY)).isEqualTo("∞")
        assertThat(latin.format(Double.NEGATIVE_INFINITY)).isEqualTo("-∞")
        assertThat(latin.format(Double.NaN)).isEqualTo("–")
    }

    @Test
    fun percent_absorbsFloatError_butNeverRoundsUpToFull() {
        val latin = NumberFormatter(persianDigits = false)
        assertThat(latin.percent(53 / 100f)).isEqualTo("53%")
        assertThat(latin.percent(0.29f)).isEqualTo("29%")
        assertThat(latin.percent(0.999f)).isEqualTo("99%")
        assertThat(latin.percent(Math.nextDown(1f))).isEqualTo("99%")
        assertThat(latin.percent(1f)).isEqualTo("100%")
        assertThat(latin.percent(0f)).isEqualTo("0%")
    }
}
