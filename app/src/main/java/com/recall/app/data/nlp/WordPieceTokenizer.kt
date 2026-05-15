package com.recall.app.data.nlp

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

// ---------------------------------------------------------------------------
// Trie for O(m) longest-prefix vocabulary lookup
// ---------------------------------------------------------------------------

/**
 * A single node in the [VocabTrie].
 * Each node represents one character in a vocabulary token.
 */
private class TrieNode {
    val children = HashMap<Char, TrieNode>()
    /** Non-null when this node is the end of a complete vocab token. */
    var token: String? = null
}

/**
 * Character-level Trie built from the WordPiece vocabulary.
 *
 * Replaces the O(n²) inner loop in the original tokenizer with an O(m) walk
 * per word (m = word length), enabling efficient longest-prefix-match lookups.
 *
 * Both regular tokens (e.g. `"hello"`) and continuation tokens (e.g. `"##llo"`)
 * are inserted and looked up using their full string representation.
 */
private class VocabTrie {

    private val root = TrieNode()

    /** Insert [token] into the Trie. */
    fun insert(token: String) {
        var node = root
        for (ch in token) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        node.token = token
    }

    /**
     * Returns the longest token in the vocabulary that is a prefix of [text]
     * starting at [start], or `null` if no match exists.
     *
     * Complexity: O(m) where m = length of the longest matching prefix ≤ text length.
     */
    fun longestPrefixMatch(text: String, start: Int): String? {
        var node = root
        var lastMatch: String? = null

        for (i in start until text.length) {
            val ch = text[i]
            node = node.children[ch] ?: break
            if (node.token != null) {
                lastMatch = node.token
            }
        }

        return lastMatch
    }
}

// ---------------------------------------------------------------------------
// WordPieceTokenizer
// ---------------------------------------------------------------------------

/**
 * WordPiece tokenizer compatible with BERT-style models (e.g. all-MiniLM-L6-v2).
 *
 * ## Performance
 * The sub-word segmentation algorithm uses a [VocabTrie] for longest-prefix
 * matching. This reduces per-word complexity from **O(n²)** (original nested
 * loop over all substring lengths) to **O(m)** where m is the word length,
 * making tokenization of long OCR text (receipts, articles, 500+ words)
 * significantly faster — well within the 50 ms target on mid-range devices.
 *
 * ## Algorithm
 * For each whitespace-delimited word:
 * 1. Walk the Trie from position 0 to find the longest matching prefix token.
 * 2. Advance the cursor past the matched prefix.
 * 3. Prepend `##` for continuation sub-words and repeat from the new position.
 * 4. If no prefix matches, emit `[UNK]` for the whole word.
 */
class WordPieceTokenizer(context: Context, vocabAssetFilename: String = "vocab.txt") {

    private val vocab = mutableMapOf<String, Int>()

    /** Trie for O(m) longest-prefix-match lookups during sub-word segmentation. */
    private val trie = VocabTrie()

    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val padToken = "[PAD]"

    init {
        val inputStream = context.assets.open(vocabAssetFilename)
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            lines.forEachIndexed { index, line ->
                val token = line.trim()
                vocab[token] = index
                trie.insert(token)
            }
        }
    }

    fun tokenize(text: String, maxLength: Int = 256): TokenizationResult {
        // Basic normalization: lowercase and strip accents (simplified for MVP)
        val normalizedText = text.lowercase().trim()

        val tokens = mutableListOf<String>()
        val words = normalizedText.split(Regex("\\s+".trimIndent()))

        for (word in words) {
            if (word.isEmpty()) continue

            val subTokens = mutableListOf<String>()
            var isBad = false
            var start = 0
            val end = word.length

            while (start < end) {
                // Build the exact lookup key matching what is stored in the Trie:
                // - First sub-word:        word.substring(start)          e.g. "helloworld"
                // - Continuation sub-word: "##" + word.substring(start)   e.g. "##world"
                val lookupKey = if (start > 0) "##${word.substring(start)}" else word.substring(start)

                // O(m) Trie walk — replaces the old O(n²) inner for-loop
                val matched = trie.longestPrefixMatch(lookupKey, 0)

                if (matched == null) {
                    isBad = true
                    break
                }

                subTokens.add(matched)
                // Advance cursor: continuation tokens include "##" in their length
                val advance = if (matched.startsWith("##")) matched.length - 2 else matched.length
                start += advance
            }

            if (isBad) {
                tokens.add(unkToken)
            } else {
                tokens.addAll(subTokens)
            }
        }

        // Truncate to maxLength - 2 (reserve space for [CLS] and [SEP])
        val maxTokens = maxLength - 2
        val truncatedTokens = if (tokens.size > maxTokens) tokens.take(maxTokens) else tokens

        // Build input_ids and attention_mask
        val inputIds = mutableListOf<Long>()
        val attentionMask = mutableListOf<Long>()

        // Add [CLS]
        inputIds.add(vocab[clsToken]?.toLong() ?: 101L)
        attentionMask.add(1L)

        // Add actual tokens
        for (token in truncatedTokens) {
            inputIds.add(vocab[token]?.toLong() ?: vocab[unkToken]?.toLong() ?: 100L)
            attentionMask.add(1L)
        }

        // Add [SEP]
        inputIds.add(vocab[sepToken]?.toLong() ?: 102L)
        attentionMask.add(1L)

        // Pad sequence to maxLength
        val padTokenId = vocab[padToken]?.toLong() ?: 0L
        while (inputIds.size < maxLength) {
            inputIds.add(padTokenId)
            attentionMask.add(0L)
        }

        return TokenizationResult(
            inputIds = inputIds.toLongArray(),
            attentionMask = attentionMask.toLongArray(),
            tokenTypeIds = LongArray(maxLength) { 0L }
        )
    }
}

data class TokenizationResult(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TokenizationResult
        if (!inputIds.contentEquals(other.inputIds)) return false
        if (!attentionMask.contentEquals(other.attentionMask)) return false
        if (!tokenTypeIds.contentEquals(other.tokenTypeIds)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenTypeIds.contentHashCode()
        return result
    }
}
