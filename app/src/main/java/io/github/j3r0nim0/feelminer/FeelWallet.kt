package io.github.j3r0nim0.feelminer

import org.bouncycastle.crypto.digests.KeccakDigest

/**
 * Feelcoin address validation, following the official feelcoin-miner README:
 * CryptoNote Base58 encoding and checksum, Feelcoin mainnet prefixes
 * 84 standard, 85 integrated, 86 subaddress.
 *
 * This is NOT the upstream XMRig tag table: at v0.1.0 that table does not
 * list a FEELCOIN entry, so this app validates by the README prefixes
 * instead of by the miner's coin() mapping. The miner itself is still
 * configured explicitly with coin=feelcoin, algo=rx/0, so pool stratum
 * does not depend on the tag table.
 *
 * Accepts only a single token. Rejects seed words (multiple words),
 * private keys, passwords, and Monero addresses (prefix 18 / 42 etc.
 * are not 84/85/86).
 */
object FeelWallet {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BLOCK_SIZES = intArrayOf(0, 2, 3, 5, 6, 7, 9, 10, 11)

    fun isValid(address: String): Boolean {
        val a = address.trim()
        if (a.isEmpty()) return false
        if (a.contains(' ') || a.contains('\n') || a.contains('\t')) return false
        // Standard FEEL address is 95 chars, integrated is 106 chars.
        if (a.length != 95 && a.length != 106) return false
        val decoded = base58Decode(a) ?: return false
        // Decoded form: varint prefix + 32 spend + 32 view (+ 8 payment id) + 4 checksum
        if (decoded.size != 69 && decoded.size != 77) return false
        val body = decoded.copyOf(decoded.size - 4)
        val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
        val digest = KeccakDigest(256)
        digest.update(body, 0, body.size)
        val out = ByteArray(32)
        digest.doFinal(out, 0)
        if (!checksum.contentEquals(out.copyOf(4))) return false
        val prefix = readVarint(body) ?: return false
        return prefix == 84L || prefix == 85L || prefix == 86L
    }

    fun masked(address: String): String {
        val a = address.trim()
        return if (a.length > 10) a.substring(0, 6) + "…" + a.substring(a.length - 4) else a
    }

    private fun readVarint(data: ByteArray): Long? {
        var result = 0L
        var shift = 0
        for (b in data) {
            val v = b.toInt() and 0xFF
            result = result or ((v and 0x7F).toLong() shl shift)
            if (v and 0x80 == 0) return result
            shift += 7
            if (shift > 63) return null
        }
        return null
    }

    private fun base58Decode(input: String): ByteArray? {
        val reverse = IntArray(256) { -1 }
        for (i in ALPHABET.indices) reverse[ALPHABET[i].code] = i
        val fullBlocks = input.length / 11
        val lastSize = input.length % 11
        val lastIndex = BLOCK_SIZES.indexOf(lastSize)
        if (lastIndex < 0) return null
        val dataSize = fullBlocks * 8 + lastIndex
        val out = ArrayList<Byte>()
        var pos = 0
        for (i in 0..fullBlocks) {
            val blockLen = if (i < fullBlocks) 11 else lastSize
            if (blockLen == 0) break
            var num = 0L
            var order = 1L
            // Big-integer in base 58 is too large for Long for 11 chars;
            // use java.math.BigInteger for correctness, not speed.
            var big = java.math.BigInteger.ZERO
            val base = java.math.BigInteger.valueOf(58)
            for (j in 0 until blockLen) {
                val d = reverse[input[pos + j].code]
                if (d < 0) return null
                big = big.multiply(base).add(java.math.BigInteger.valueOf(d.toLong()))
            }
            val bytes = big.toByteArray()
            // BigInteger may add a leading zero byte for sign.
            val raw = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
            val want = if (i < fullBlocks) 8 else lastIndex
            if (raw.size > want) return null
            for (k in 0 until want - raw.size) out.add(0)
            for (b in raw) out.add(b)
            pos += blockLen
            @Suppress("UNUSED_VARIABLE") val ignored = num
            @Suppress("UNUSED_VARIABLE") val ignored2 = order
        }
        if (out.size != dataSize) return null
        return out.toByteArray()
    }
}
