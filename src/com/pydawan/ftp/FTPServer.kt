package com.pydawan.ftp

import com.pydawan.user.User
import org.json.JSONObject
import java.io.Closeable
import java.io.PrintStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * A simple multithreaded FTP server
 * @author David Birtles
 */
class FTPServer(
    users: Set<User>,
    port: Int = DEFAULT_PORT,
    private val dataPorts: Set<Int> = DEFAULT_DATA_PORT,
    nThread: Int = 5,
    private val timeout: Int = 20000,
) : Closeable {

    private val serverSocket = ServerSocket(port)
    private val pool = Executors.newFixedThreadPool(nThread)

    private var count = 1

    /**
     * Listen for a client to connect and then send it to the thread pool waiting for an available thread.
     * The client will then be served.
     */
    fun listen() {
        val socket = serverSocket.accept()
        socket.soTimeout = timeout

        val order = count++ //Fetch and increment

        pool.execute {
            ClientListener(socket, dataPorts, System.out, "Michel n°$order").use {
                it.listenForever()
            }
        }
    }

    /**
     * Loop on [listen] forever
     */
    fun listenForever() {
        while (true) listen()
    }

    override fun close() {
        serverSocket.close()
    }

    companion object {
        fun fromConfig(config: FTPConfig): FTPServer {
            return FTPServer(config.users, config.port)
        }

        fun fromConfig(path: Path): FTPServer {
            return fromConfig(FTPConfig.loadFrom(JSONObject(Files.readString(path))))
        }
    }
}