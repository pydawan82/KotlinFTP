package com.pydawan.ftp

class FTPException(val code: Int, message: String?) : Exception(message) {
}