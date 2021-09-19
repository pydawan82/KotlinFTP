package com.pydawan.file

import java.nio.file.Files
import java.nio.file.Path


fun getPermissions(path: Path): String {
    return try {
        val permissions = Files.getPosixFilePermissions(path)
        permissions.toString()

    } catch (e: Exception) {
        if (Files.isDirectory(path))
            "drw-rw-rw-"
        else
            "-rw-rw-rw-"
    }
}