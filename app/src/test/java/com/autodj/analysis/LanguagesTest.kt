package com.autodj.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguagesTest {
    @Test
    fun chipsAndPlaylistNamesNameTheirLanguage() {
        assertEquals(Languages.TELUGU, Languages.named("Telugu › Party today"))
        assertEquals(Languages.TELUGU, Languages.named("Tollywood Dance Essentials"))
        assertEquals(Languages.TAMIL, Languages.named("Kollywood Dance Essentials"))
        assertEquals(Languages.HINDI, Languages.named("Bollywood Party"))
        assertEquals(Languages.SPANISH, Languages.named("Latin › Reggaeton"))
        assertEquals(Languages.KOREAN, Languages.named("K-Pop hits"))
        // A mood says nothing about language.
        assertNull(Languages.named("Party › Party today"))
        assertNull(Languages.named(null))
    }

    @Test
    fun aScriptSaysItsLanguage() {
        assertEquals(Languages.TELUGU, Languages.fromScript("[00:01.00] నువ్వు నేను ప్రేమ ఇది నిజం కదా మనసు"))
        assertEquals(Languages.HINDI, Languages.fromScript("[00:01.00] तेरा मेरा प्यार यह दिल कहता है"))
        assertNull(Languages.fromScript("[00:01.00] all the lights are on tonight"))
    }

    @Test
    fun lyricsInEnglishLettersAreGuessedFromEverydayWords() {
        val english = "I know you love me baby and it is all we want tonight in the dark".split(" ")
        assertEquals(Languages.ENGLISH, Languages.fromWords(english + english + english))
        val spanish = "yo te quiero mi amor con todo el corazon y para siempre en la noche".split(" ")
        assertEquals(Languages.SPANISH, Languages.fromWords(spanish + spanish + spanish))
        val hindi = "tera mera pyaar hai dil mein tum ho na kya yeh mujhe chahiye".split(" ")
        assertEquals(Languages.HINDI, Languages.fromWords(hindi + hindi + hindi))
        // Too few words to tell.
        assertNull(Languages.fromWords(listOf("love", "the")))
    }

    @Test
    fun aSetTakesItsMostCommonLanguageWhenItIsAtLeastHalf() {
        assertEquals(Languages.TELUGU, Languages.ofSet(listOf("Telugu", "Telugu", "Telugu", "English", null, null)))
        assertNull(Languages.ofSet(listOf("Telugu", "English", "Spanish", "Hindi")))
        // Too few known to say.
        assertNull(Languages.ofSet(listOf("Telugu", null, null)))
    }
}
