package com.pydawan.user

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

const val saltLength = 16
val secureRandom: SecureRandom = SecureRandom()

const val algorithm = "SHA3-256"
val charset: Charset = StandardCharsets.US_ASCII
val digest = MessageDigest.getInstance(algorithm)!!

fun generateSalt(): ByteArray {
    val bytes = ByteArray(saltLength)
    secureRandom.nextBytes(bytes)
    return bytes
}

fun hash(password: String, salt: ByteArray): ByteArray {
    val passBytes = password.toByteArray(charset)

    return hash(password, salt)
}

fun hash(password: ByteArray, salt: ByteArray): ByteArray {
    return hash(password + salt)
}

fun hash(bytes: ByteArray): ByteArray {
    return digest.digest(bytes)
}