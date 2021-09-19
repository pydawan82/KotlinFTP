package com.pydawan.user

import com.pydawan.json.Serializable

val anonymous = User.new("anonymous", null)

@Serializable
class User(val name: String, private val salt: ByteArray?, private val passHash: ByteArray?) {

    companion object {

        private fun saltHash(password: String?): Pair<ByteArray?, ByteArray?> {
            val salt = if (password != null) {
                generateSalt()
            } else {
                null
            }

            val hash = if (password != null) {
                hash(password, salt!!)
            } else {
                null
            }

            return Pair(salt, hash)
        }

        fun new(name: String, password: String?): User {
            val (salt, hash) = saltHash(password)
            return User(name, salt, hash)
        }
    }

    fun hasPassword(): Boolean {
        return passHash != null
    }

    fun hasNoPassword(): Boolean {
        return passHash == null
    }

    fun checkPassword(password: String): Boolean {
        return hasNoPassword() || passHash.contentEquals(hash(password, salt!!))
    }

    override fun toString(): String {
        return "User{name: $name}"
    }
}
