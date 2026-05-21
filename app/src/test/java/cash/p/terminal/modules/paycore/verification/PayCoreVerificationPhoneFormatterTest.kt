package cash.p.terminal.modules.paycore.verification

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class PayCoreVerificationPhoneFormatterTest {

    @Test
    fun normalizeRussianPhoneDigits_startsWith8_stripsNationalPrefix() {
        assertEquals("9045556478", normalizeRussianPhoneDigits("89045556478"))
    }

    @Test
    fun normalizeRussianPhoneDigits_countryCodePasted_stripsLeading7() {
        assertEquals("9045556478", normalizeRussianPhoneDigits("+79045556478"))
    }

    @Test
    fun normalizeRussianPhoneDigits_startsWith9_keepsLocalDigits() {
        assertEquals("9045556478", normalizeRussianPhoneDigits("9045556478"))
    }

    @Test
    fun formatRussianPhoneMask_fullNumber_formatsNumber() {
        assertEquals("(904)555-64-78", formatRussianPhoneMask("9045556478"))
    }

    @Test
    fun russianPhoneVisualTransformation_fullNumber_mapsOffsets() {
        val transformed = RussianPhoneVisualTransformation.filter(AnnotatedString("9045556478"))

        assertEquals("(904)555-64-78", transformed.text.text)
        assertEquals(1, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(5, transformed.offsetMapping.originalToTransformed(3))
        assertEquals(9, transformed.offsetMapping.originalToTransformed(6))
        assertEquals(12, transformed.offsetMapping.originalToTransformed(8))
        assertEquals(14, transformed.offsetMapping.originalToTransformed(10))
        assertEquals(0, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(0, transformed.offsetMapping.transformedToOriginal(1))
        assertEquals(3, transformed.offsetMapping.transformedToOriginal(5))
        assertEquals(6, transformed.offsetMapping.transformedToOriginal(9))
        assertEquals(8, transformed.offsetMapping.transformedToOriginal(12))
        assertEquals(10, transformed.offsetMapping.transformedToOriginal(14))
    }
}
