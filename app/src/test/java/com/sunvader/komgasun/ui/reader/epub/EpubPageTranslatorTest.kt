package com.sunvader.komgasun.ui.reader.epub

import com.sunvader.komgasun.ui.reader.TextBlockTranslator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPageTranslatorTest {

    @Test
    fun `parseExtractedBlocksJson unwraps evaluateJavascript stringified JSON array`() {
        val rawFromWebView = "\"[{\\\"tag\\\":\\\"H1\\\",\\\"text\\\":\\\"Chapter One\\\",\\\"noIndent\\\":false},{\\\"tag\\\":\\\"P\\\",\\\"text\\\":\\\"First paragraph.\\\",\\\"noIndent\\\":true}]\""
        val blocks = EpubPageTranslationScripts.parseExtractedBlocksJson(rawFromWebView)

        assertEquals(2, blocks.size)
        assertEquals(EpubVisibleBlock(tag = "H1", text = "Chapter One", noIndent = false), blocks[0])
        assertEquals(EpubVisibleBlock(tag = "P", text = "First paragraph.", noIndent = true), blocks[1])
    }

    @Test
    fun `translateVisibleBlocks translates non-blank blocks and preserves tags and noIndent`() = runTest {
        val fakeTranslator = object : TextBlockTranslator {
            override suspend fun translateBlocks(blocks: List<String>): Result<List<String>> =
                Result.success(blocks.map { "ES: $it" })
            override fun close() = Unit
        }
        val translator = EpubPageTranslator(fakeTranslator)
        val input = listOf(
            EpubVisibleBlock(tag = "H2", text = "  The Beginning\n ", noIndent = false),
            EpubVisibleBlock(tag = "P", text = "   ", noIndent = false),
            EpubVisibleBlock(tag = "P", text = "continues from previous page.", noIndent = true)
        )

        val result = translator.translateVisibleBlocks(input).getOrThrow()

        assertEquals(2, result.size)
        assertEquals(EpubVisibleBlock(tag = "H2", text = "ES: The Beginning", noIndent = false), result[0])
        assertEquals(EpubVisibleBlock(tag = "P", text = "ES: continues from previous page.", noIndent = true), result[1])
    }

    @Test
    fun `applyTranslatedBlocksScript embeds blocks and auto-fit loop`() {
        val script = EpubPageTranslationScripts.applyTranslatedBlocksScript(
            blocks = listOf(EpubVisibleBlock(tag = "P", text = "Hola mundo", noIndent = true)),
            fontSizeSp = 20,
            isDarkTheme = true,
            useOriginalFonts = false
        )
        assertTrue(script.contains("data-epub-translation"))
        assertTrue(script.contains("Hola mundo"))
        assertTrue(script.contains("layer.scrollHeight > h"))
    }
}
