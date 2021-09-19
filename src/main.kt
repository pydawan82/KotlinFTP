import com.pydawan.ftp.FTPServer
import java.nio.file.Path

fun main() {
    val config = Path.of("config.json")
    FTPServer.fromConfig(config).use {
        it.listenForever()
    }
}