package com.behnamjalali.planb.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchNormalizerTest {
    private val zwnj = 0x200C.toChar()

    @Test
    fun arabicYehAndKaf_becomePersian() {
        assertThat(SearchNormalizer.normalize("كتاب علي")).isEqualTo(SearchNormalizer.normalize("کتاب علی"))
    }

    @Test
    fun halfSpace_joinsTheWord() {
        val withZwnj = "می${zwnj}خواهم"
        assertThat(SearchNormalizer.normalize(withZwnj)).isEqualTo("میخواهم")
        assertThat(SearchNormalizer.tokens(withZwnj)).containsExactly("میخواهم")
        // Written with or without the half-space, the plural is the same token.
        assertThat(SearchNormalizer.tokens("کتاب${zwnj}ها")).isEqualTo(SearchNormalizer.tokens("کتابها"))
    }

    @Test
    fun indexTokens_keepHalfSpaceParts() {
        assertThat(SearchNormalizer.indexTokens("می${zwnj}خواهم بروم"))
            .containsExactly("میخواهم", "بروم", "می", "خواهم")
        assertThat(SearchNormalizer.indexTokens("بدون نیم فاصله")).isEqualTo(SearchNormalizer.tokens("بدون نیم فاصله"))
    }

    @Test
    fun zeroWidthSpace_separatesWords() {
        assertThat(SearchNormalizer.tokens("a\u200Bb")).containsExactly("a", "b").inOrder()
    }

    @Test
    fun hamzaSeats_fold() {
        assertThat(SearchNormalizer.normalize("مسئله")).isEqualTo(SearchNormalizer.normalize("مسیله"))
        assertThat(SearchNormalizer.normalize("مؤسسه")).isEqualTo("موسسه")
        assertThat(SearchNormalizer.normalize("أإآ")).isEqualTo("ااا")
        assertThat(SearchNormalizer.normalize("مدرسة")).isEqualTo("مدرسه")
    }

    @Test
    fun digits_persianArabicLatin_areEquivalent() {
        assertThat(SearchNormalizer.normalize("جلسه ۱۴۰۵")).isEqualTo("جلسه 1405")
        assertThat(SearchNormalizer.normalize("جلسه ١٤٠٥")).isEqualTo("جلسه 1405")
    }

    @Test
    fun diacriticsAndTatweel_removed() {
        assertThat(SearchNormalizer.normalize("کِتابـــ")).isEqualTo("کتاب")
        assertThat(SearchNormalizer.normalize("Café")).isEqualTo("cafe")
    }

    @Test
    fun alefVariants_andHehYeh() {
        assertThat(SearchNormalizer.normalize("آب")).isEqualTo("اب")
        assertThat(SearchNormalizer.normalize("خانهٔ")).isEqualTo("خانه")
        assertThat(SearchNormalizer.normalize("خانۀ")).isEqualTo("خانه")
    }

    @Test
    fun caseAndWhitespace_folded() {
        assertThat(SearchNormalizer.normalize("  Weekly   REVIEW\tNotes ")).isEqualTo("weekly review notes")
    }

    @Test
    fun mixedText_tokens() {
        assertThat(SearchNormalizer.tokens("پروژه Plan-B (نسخه ۲)"))
            .containsExactly("پروژه", "planb", "نسخه", "2").inOrder()
    }

    @Test
    fun emptyInput() {
        assertThat(SearchNormalizer.normalize("")).isEmpty()
        assertThat(SearchNormalizer.tokens("  ")).isEmpty()
    }
}
