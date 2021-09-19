package com.pydawan.ftp

import com.pydawan.file.UnixPath
import com.pydawan.file.getPermissions
import com.pydawan.user.User
import com.pydawan.user.anonymous

import java.io.*
import java.net.*
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.NoSuchElementException

private const val DELIMITER = "\n"

/**
 * A class that handles a client's FTP requests.
 * @author David Birtles
 */
class ClientListener(
    private val socket: Socket,
    private val dataPorts: Set<Int>,
    private val log: PrintStream,
    private val name: String,
) : Closeable {

    private val out = PrintWriter(socket.getOutputStream(), true, Charsets.US_ASCII)
    private val scanner = Scanner(socket.getInputStream(), Charsets.US_ASCII)

    init {
        scanner.useDelimiter(DELIMITER)
    }

    private val rootPath = Path.of("E:/")
    private val users = listOf(anonymous)

    private var user: User? = null
    private var authenticated = false
    private var wd: UnixPath = UnixPath.ROOT
    private var dataSocket: Socket? = null
    private var transferType = TransferType.BINARY

    private var rnFrom: Path? = null

    private var lastCommand: ((String) -> Pair<Int, String>?)? = null

    private val dateFormat = SimpleDateFormat("hh:mm:ss dd/MM/yy")

    init {
        welcome()
    }

    private fun unixPathOf(path: Iterable<String>): UnixPath {
        return unixPathOf(path.joinToString(" "))
    }

    /**
     * Returns the [UnixPath]
     */
    private fun unixPathOf(path: String): UnixPath {
        val localPath = UnixPath.of(path)

        return if (localPath.absolute) {
            localPath
        } else {
            wd.resolve(localPath)
        }
    }

    private fun pathOf(path: String): Path {
        return unixPathOf(path).toPath(rootPath)
    }


    /**
     * Prints a message to the log output stream.
     * @param message the message to print
     */
    private fun printLog(message: String) {
        val date = dateFormat.format(Date())
        synchronized(log) {
            log.println("[$date]$message")
        }
    }

    /**
     * Prints a formatted outgoing message to the log
     */
    private fun printLogOut(message: String) {
        printLog("-->$name: $message")
    }

    /**
     * Prints a formatted incoming message to the log
     */
    private fun printLogIn(message: String) {

        printLog("<--$name: $message")
    }

    /**
     * Prints a message to both command socket and log
     * @param message the message to print/send
     */
    private fun send(message: String) {
        out.println(message)
        printLogOut(message)
    }

    /**
     * Calls the [send] method with a formatted response
     * @param code the response code
     * @param message the response message
     */
    private fun send(code: Int, message: String) {
        send("$code $message")
    }

    /**
     * Sends the welcome message
     */
    private fun welcome() {
        send(COMMAND_OK, "Welcome!")
    }

    /**
     * Removes the trailing carriage return of a command
     */
    private fun removeCR(input: String): String {
        return input.removeSuffix("\r")
    }

    /**
     * Loops on [listenRequest] for each incoming line from command socket.
     */
    fun listenForever() {
        try {
            scanner.forEachRemaining {
                listenRequest(removeCR(it))
            }

            throw scanner.ioException()
        } catch (quit: QuitEvent) {
            send(DISCONNECTION, "See ya.")
        } catch (e: SocketTimeoutException) {
            send(TIMEOUT, "Connection timed out.")
        } catch (e: IOException) {
            send(DISCONNECTION, "Fatal connection error")
        }
    }

    /**
     * Parses and responds to a request
     * @param rawRequest the raw [String] request
     */
    private fun listenRequest(rawRequest: String) {
        printLogIn(rawRequest)

        val (cmdName, args) = parse(rawRequest)
        val command = dispatch(cmdName)

        try {
            val response = command(args)
            if (response != null)
                send(response.first, response.second)

        } catch (e: FTPException) {
            send(e.code, e.message ?: "")
        }

        lastCommand = command
    }

    /**
     * Parses a request and returns both the command and the arguments.
     * @param rawRequest the raw input [String]
     * @return The command and the arguments
     */
    private fun parse(rawRequest: String): Pair<String, String> {
        val parameters = rawRequest.split(" ", limit = 2)
        val cmd = parameters[0]
        val arg = parameters.getOrElse(1) { "" }

        return Pair(cmd, arg)
    }

    /**
     * Splits a [String] into different arguments using space as delimiter
     * @param arg the whole argument
     * @return the list of splitted arguments
     */
    private fun split(arg: String): List<String> {
        return if (arg.isBlank())
            listOf()
        else
            arg.split(" ")
    }

    /**
     * Returns the method corresponding to the given command name.
     * This method is not case-sensitive.
     * @param cmd the name of the command
     * @return the method associated to the command
     */
    private fun dispatch(cmd: String): (String) -> Pair<Int, String>? {
        return when (cmd.uppercase()) {
            "SYST" -> this::syst
            "FEAT" -> this::feat
            "MDTM" -> this::mdtm
            "USER" -> this::user
            "PASS" -> this::pass
            "TYPE" -> this::type
            "PWD" -> this::pwd
            "CDUP" -> this::cdup
            "CWD" -> this::cwd
            "LIST" -> this::list
            "RNFR" -> this::rnfr
            "RNTO" -> this::rnto
            "RETR" -> this::retr
            "STOR" -> this::stor
            "DELE" -> this::dele
            "SIZE" -> this::size
            "EPSV" -> this::epsv
            "PASV" -> this::pasv
            "EPRT" -> this::eprt
            "PORT" -> this::port
            "QUIT" -> this::quit

            else -> this::error
        }
    }

    /**
     * Checks if the number of arguments is equal to the given value
     * @param args a list of arguments
     * @param argc the required number of arguments
     * @return *[argc] == [args].size*
     */
    private fun checkArgc(args: List<String>, argc: Int): Boolean {
        return argc == args.size
    }

    /**
     * Asserts that the list of arguments is at least the size of
     * one of the given [argc] values
     * @param args a list of arguments
     * @param argc a list of argument count
     * @throws FTPException if the size of [args] is not contained in argc
     */
    private fun assertArgc(args: List<String>, vararg argc: Int) {

        val ok = argc.map {
            checkArgc(args, it)
        }.contains(true)

        if (!ok) {
            throw FTPException(ERROR_ARGS, "This command requires ${argc.joinToString(", ")} argument(s)")
        }
    }

    /**
     * Returns the name of the operating system.
     * @return the name of the os
     */
    private fun getSystemDetails(): String {
        return System.getProperty("os.name")
    }

    /**
     * Method corresponding to the SYST command.
     * This command takes no arguments and responds with
     * details about the system.
     * @return the FTP response
     */
    private fun syst(arg: String): Pair<Int, String> {
        val args = split(arg)
        assertArgc(args, 0)

        val os = getSystemDetails()

        return Pair(SYSTEM, os)
    }

    private fun printFeatures() {
        out.println(
            """
            EPSV
            EPRT
            SIZE
            MDTM
        """.trimIndent()
        )
    }

    private fun feat(arg: String): Pair<Int, String> {
        val args = split(arg)
        assertArgc(args, 0)

        printFeatures()

        return Pair(HELP, "END")
    }

    private fun mdtm(arg: String): Pair<Int, String> {
        val path = pathOf(arg)

        return try {
            val lastModified = Files.getLastModifiedTime(path)
            Pair(LAST_MODIFIED, lastModified.toString())
        } catch (e: IOException) {
            Pair(FILE_NOT_OK, "File not found")
        } catch (e: SecurityException) {
            Pair(FILE_NOT_OK, "You are not authorized to get this file")
        }
    }

    private fun user(arg: String): Pair<Int, String> {
        val name = arg

        return try {

            val newUser = users.first {
                it.name == name
            }

            user = newUser

            if (newUser.hasPassword())
                Pair(USER_OK, "Waiting for password")
            else
                Pair(COMMAND_OK, "Welcome in")

        } catch (e: NoSuchElementException) {
            Pair(NOT_CONNECTED, "User is not recognized")
        }


    }

    private fun pass(arg: String): Pair<Int, String> {
        val password = arg
        val user = user

        return if (user == null)
            Pair(NOT_CONNECTED, "Use USER before")
        else if (user.checkPassword(password))
            Pair(USER_LOGGED_IN, "Logged in as ${user.name}")
        else
            Pair(NOT_CONNECTED, "Wrong password")
    }

    private fun type(arg: String): Pair<Int, String> {
        val type = arg

        return try {
            val transferType = TransferType.values().first {
                it.code == type
            }

            this.transferType = transferType

            Pair(COMMAND_OK, "Changed transfer type to $transferType")
        } catch (e: NoSuchElementException) {
            Pair(ERROR_ARGS, "Unrecognized type")
        }

    }

    private fun pwd(arg: String): Pair<Int, String> {
        val args = split(arg)
        assertArgc(args, 0)

        return Pair(COMMAND_OK, "$wd")
    }

    private fun cdup(arg: String): Pair<Int, String> {
        val args = split(arg)
        assertArgc(args, 0)

        val parent = wd.parent
        println(wd)
        println(wd.hasParent())

        return if (parent != null) {
            wd = parent
            Pair(COMMAND_OK, "Changed to parent directory")
        } else {
            Pair(FILE_NOT_OK, "Current file is root")
        }
    }

    private fun cwd(arg: String): Pair<Int, String> {
        wd = unixPathOf(arg)

        return Pair(COMMAND_OK, "Changed to $wd")
    }

    private fun printFile(out: PrintStream, path: Path) {
        val size = Files.size(path)
        val modify = Files.getLastModifiedTime(path)
        val perm = getPermissions(path)
        val name = path.fileName

        out.println("Type=file;Size=$size;Modify=$modify;Perm=$perm; $name")
    }

    private fun printDirectory(out: PrintStream, path: Path) {
        val size = Files.size(path)
        val perm = getPermissions(path)
        val name = path.fileName

        out.println("Type=dir;Size=$size;Perm=$perm; $name")
    }

    private fun printPath(out: PrintStream, path: Path) {
        try {
            if (Files.isDirectory(path))
                printDirectory(out, path)
            else
                printFile(out, path)
        } catch (e: AccessDeniedException) {
            //Just ignore the file
        }
    }

    private fun printList(dataSocket: Socket, path: Path) {
        dataSocket.use { socket ->
            PrintStream(socket.getOutputStream()).use { out ->
                Files.list(path).forEach { path ->
                    printPath(out, path)
                }
            }
        }
    }

    private fun list(arg: String): Pair<Int, String> {
        val dir = (if (arg.isEmpty()) wd else unixPathOf(arg)).toPath(rootPath)
        val dataSocket = this.dataSocket

        return if (!Files.exists(dir)) {
            Pair(ERROR_ARGS, "File does not exists")
        } else if (!Files.isDirectory(dir)) {
            Pair(ERROR_ARGS, "File is not a directory")
        } else if (dataSocket == null || dataSocket.isClosed) {
            Pair(CONNECTION_CLOSED, "Data connection is closed")
        } else {
            send(STARTING_TRANSFER, "Starting transfer")
            printList(dataSocket, dir)
            Pair(CLOSING_DATA_CONNECTION, "Transfer complete")
        }
    }

    private fun rnfr(arg: String): Pair<Int, String> {
        val path = pathOf(arg)

        return if (Files.exists(path)) {
            rnFrom = path
            Pair(FILE_OK_CONTINUE, "Waiting for RNTO")
        } else {
            Pair(FILE_NOT_OK, "File not found")
        }
    }

    private fun move(from: Path, to: Path) {
        Files.move(from, to)
    }

    private fun rnto(arg: String): Pair<Int, String> {
        val rnTo = pathOf(arg)
        val rnFrom = rnFrom

        return if (rnFrom != null) {
            try {
                move(rnFrom, rnTo)
                Pair(COMMAND_OK, "File moved")
            } catch (e: IOException) {
                Pair(FILE_NOT_OK, "Failed to move file")
            }
        } else {
            Pair(BAD_SEQUENCE_COMMANDS, "Expected RNFR before")
        }

    }

    private fun sendFileAscii(path: Path, out: OutputStream) {
        PrintStream(out).use { printStream ->
            Files.lines(path, Charsets.US_ASCII).forEachOrdered { line ->
                printStream.println(line)
            }
        }
    }

    private fun sendFileBinary(path: Path, out: OutputStream) {
        out.use { writer ->
            Files.newInputStream(path).use { reader ->
                reader.transferTo(writer)
            }
        }
    }

    private fun sendFile(path: Path, out: OutputStream, transferType: TransferType) {
        when (transferType) {
            TransferType.BINARY -> sendFileBinary(path, out)
            TransferType.ASCII -> sendFileAscii(path, out)
        }
    }

    private fun retr(arg: String): Pair<Int, String> {
        val path = pathOf(arg)
        val dataSocket = dataSocket

        return if (dataSocket != null) {
            sendFile(path, dataSocket.getOutputStream(), transferType)
            dataSocket.close()

            Pair(CLOSING_DATA_CONNECTION, "Transfer complete, closing data connection")
        } else {
            Pair(NOT_CONNECTED, "The data connection is not established")
        }
    }

    private fun storeBinary(path: Path, input: InputStream) {
        input.use {
            Files.newOutputStream(path).use { out ->
                input.transferTo(out)
            }
        }
    }

    private fun storeAscii(path: Path, input: InputStream) {
        PrintStream(Files.newOutputStream(path)).use { out ->
            Scanner(input).use { scanner ->
                scanner.forEachRemaining(out::println)
            }
        }
    }

    private fun store(path: Path, input: InputStream, transferType: TransferType) {
        when (transferType) {
            TransferType.BINARY -> storeBinary(path, input)
            TransferType.ASCII -> storeAscii(path, input)
        }
    }

    private fun stor(arg: String): Pair<Int, String> {
        val path = pathOf(arg)
        val dataSocket = dataSocket

        return if (dataSocket != null) {
            try {
                store(path, dataSocket.getInputStream(), transferType)
                Pair(CLOSING_DATA_CONNECTION, "Transfer complete")
            } catch (e: IOException) {
                Pair(426, "An IO error occurred")
            }
        } else {
            Pair(NOT_CONNECTED, "Data connection not established")
        }
    }

    private fun delete(path: Path) {
        Files.delete(path)
    }

    private fun dele(arg: String): Pair<Int, String> {
        val path = pathOf(arg)

        return try {
            delete(path)
            Pair(COMMAND_OK, "File deleted")
        } catch (e: IOException) {
            Pair(FILE_NOT_OK, "Cannot delete file")
        } catch (e: SecurityException) {
            Pair(FILE_NOT_OK, "You are not allowed to perform this action")
        }
    }

    private fun size(path: Path): Long {
        return Files.size(path)
    }

    private fun size(arg: String): Pair<Int, String> {
        val path = pathOf(arg)

        return try {
            val size = size(path)
            Pair(COMMAND_OK, size.toString())
        } catch (e: IOException) {
            Pair(FILE_NOT_OK, "An IO error occurred")
        } catch (e: SecurityException) {
            Pair(FILE_NOT_OK, "You are not allowed to perform this action")
        }
    }

    private fun epsvMsg(port: Int) {
        send(ENTERING_EXTENDED_PASSIVE, "Entering extended passive mode (|||$port|)")
    }

    private fun tryOpenData(port: Int, onOpen: (Int) -> Unit): Socket? {
        try {
            ServerSocket(port).use {
                onOpen(port)
                return it.accept()
            }
        } catch (e: BindException) {
            return null
        }
    }

    private fun openData(onOpen: (Int) -> Unit): Socket? {
        return try {
            dataPorts.firstNotNullOf {
                tryOpenData(it, onOpen)
            }
        } catch (e: NoSuchElementException) {
            null
        }
    }

    private fun epsv(arg: String): Pair<Int, String>? {
        val args = split(arg)
        assertArgc(args, 0)
        closeData()

        val dataSocket = openData(this::epsvMsg)

        return if (dataSocket != null) {
            this.dataSocket = dataSocket
            null
        } else {
            Pair(NOT_CONNECTED, "Unable to open a data port for connection")
        }

    }

    private fun getAddress() = Inet4Address.getLocalHost()

    private fun pasvMsg(port: Int) {
        assert(port < UShort.MAX_VALUE.toInt())

        val mod = UByte.MAX_VALUE.toInt()
        val address = getAddress().address.joinToString()

        send(ENTERING_PASSIVE, "Entering passive mode (${address},${port % mod},${port % mod})")
    }

    private fun pasv(arg: String): Pair<Int, String>? {
        val args = split(arg)
        assertArgc(args, 0)
        closeData()

        val dataSocket = openData(this::pasvMsg)

        return if (dataSocket != null) {
            this.dataSocket = dataSocket
            null
        } else {
            Pair(NOT_CONNECTED, "Unable to open a data port for connection")
        }
    }

    private fun eprt(arg: String): Pair<Int, String> {
        val args = split(arg)
        assertArgc(args, 1)

        val addressStr = args[0]
        val delimiter = addressStr[0]
        val params = addressStr.split(Regex.fromLiteral("$delimiter"))

        return try {
            val address = params[2]
            val port = params[3].toInt()

            dataSocket = Socket(address, port)
            Pair(COMMAND_OK, "Connected, waiting transfer")
        } catch (e: IndexOutOfBoundsException) {
            Pair(ERROR_ARGS, "Ill formed args")
        } catch (e: IOException) {
            Pair(NOT_CONNECTED, "Unable to connect to remote host")
        } catch (e: SecurityException) {
            Pair(NOT_CONNECTED, "Unable to connect to remote host")
        }
    }

    private fun port(arg: String): Pair<Int, String> {
        val args = split(arg)
        assertArgc(args, 1)

        val delimiter = ','
        val addressStr = args[0]

        return try {
            val bytes = addressStr.split(delimiter).map(String::toUByte)

            if (bytes.size != 6)
                return Pair(ERROR_ARGS, "Ill formed address")

            val address = Inet4Address.getByAddress(bytes.subList(0, 4).map(UByte::toByte).toByteArray())
            val port = bytes.subList(4, 6).map(UByte::toInt).reduce { i1, i2 ->
                i1 * UByte.MAX_VALUE.toInt() + i2
            }

            dataSocket = Socket(address, port)

            Pair(COMMAND_OK, "Connected to $address:$port")
        } catch (e: NumberFormatException) {
            Pair(ERROR_ARGS, "Ill formed address")
        } catch (e: IOException) {
            Pair(ERROR_ARGS, "Failed to connect")
        }
    }

    private fun quit(arg: String): Nothing {
        val args = split(arg)
        assertArgc(args, 0)
        throw QuitEvent()
    }

    private fun error(arg: String): Pair<Int, String> {
        return Pair(COMMAND_NOT_IMPLEMENTED, "Command not implemented.")
    }

    private fun closeData() {
        val dataSocket = dataSocket

        if (dataSocket != null && !dataSocket.isClosed)
            dataSocket.close()
    }

    override fun close() {
        out.close()
        scanner.close()
        socket.close()
    }

}