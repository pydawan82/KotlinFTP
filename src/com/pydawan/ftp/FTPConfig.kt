package com.pydawan.ftp

import com.pydawan.json.Serializable
import com.pydawan.user.User
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.memberProperties

@Serializable
class FTPConfig(
    val port: Int,
    val users: Set<User>,
    val root: Path,
) {

    companion object {

        private const val PORT = "port"
        private const val USERS = "users"
        private const val ROOT = "root"

        private const val NAME = "name"
        private const val SALT = "salt"
        private const val HASH = "hash"

        private val ascii = Charsets.US_ASCII

        private fun loadUser(json: JSONObject): User {
            val name = json.getString(NAME)
            val salt = json.optString(SALT)?.toByteArray(ascii)
            val hash = json.optString(HASH)?.toByteArray(ascii)

            return User(name, salt, hash)
        }

        private fun loadUsers(json: JSONArray): Set<User> {
            val size = json.length()
            val users = mutableSetOf<User>()

            for (index in 0 until size) {
                users.add(loadUser(json.getJSONObject(index)))
            }

            return users
        }

        fun loadFrom(json: JSONObject): FTPConfig {
            val port = json.getInt(PORT)
            val users = loadUsers(json.getJSONArray(USERS))
            val root = Path.of(json.getString(ROOT))

            return FTPConfig(port, users, root)
        }

        fun loadFrom(path: Path): FTPConfig {
            return loadFrom(JSONObject(Files.readString(path, ascii)))
        }

        fun saveTo(path: Path) {

        }
    }
}