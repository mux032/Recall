package com.recall.app.data.nlp

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class WordPieceTokenizer(context: Context, vocabAssetFilename: String = "vocab.txt") {
    
    private val vocab = mutableMapOf<String, Int>()
    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val padToken = "[PAD]"

    init {
        val inputStream = context.assets.open(vocabAssetFilename)
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            lines.forEachIndexed { index, line ->
                vocab[line.trim()] = index
            }
        }
    }

    fun tokenize(text: String, maxLength: Int = 256): TokenizationResult {
        // Basic normalization: lowercase and strip accents (simplified for this MVP)
        val normalizedText = text.lowercase().trim()
        
        val tokens = mutableListOf<String>()
        val words = normalizedText.split(Regex("\\s+"))

        for (word in words) {
            if (word.isEmpty()) continue
            
            var start = 0
            val end = word.length
            var subTokens = mutableListOf<String>()
            var isBad = false

            while (start < end) {
                var subStr = ""
                var found = false
                
                // Find the longest matching sub-word
                for (len in end downTo start + 1) {
                    val candidate = word.substring(start, len)
                    val token = if (start > 0) "##$candidate" else candidate
                    
                    if (vocab.containsKey(token)) {
                        subStr = token
                        found = true
                        break
                    }
                }
                
                if (!found) {
                    isBad = true
                    break
                }
                
                subTokens.add(subStr)
                start += if (subStr.startsWith("##")) subStr.length - 2 else subStr.length
            }
            
            if (isBad) {
                tokens.add(unkToken)
            } else {
                tokens.addAll(subTokens)
            }
        }

        // Truncate to maxLength - 2 (for [CLS] and [SEP])
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

        // Pad sequence
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
