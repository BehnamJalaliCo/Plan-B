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
}
