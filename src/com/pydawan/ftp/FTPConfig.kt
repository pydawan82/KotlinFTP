package com.pydawan.ftp

import com.pydawan.json.Serializable
import com.pydawan.user.User
import java.nio.file.Path

@Serializable
class FTPConfig(
    val port: Int,
    val users: Set<User>,
    val root: Path,
)