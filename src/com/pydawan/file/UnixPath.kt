package com.pydawan.file

import java.nio.file.FileSystemException
import java.nio.file.Path
import java.util.*

private const val separator = "/"
private const val up = ".."
private const val cur = "."

/**
 * A class that represents a path to a file in a Unix-like os.
 * @author David Birtles
 */
class UnixPath(path: List<String>, val absolute: Boolean) {

    private val path: List<String>

    init {
        val stack = Stack<String>()
        path.filter {
            it.isNotEmpty()
        }.forEach {
            if (it == cur) {
            } else if (it == up && !stack.isEmpty()) {
                stack.pop()
            } else {
                stack.push(it)
            }
        }

        this.path = stack.toList()
    }

    val parent: UnixPath?
        get() = if (!hasParent())
            null
        else
            UnixPath(path.subList(0, path.size - 1), absolute)

    /**
     * @return *true* if the path is *absolute* and the path is the *root* or if the path is *relative*
     * and the path is the *current directory*, *false* otherwise
     */
    fun hasParent(): Boolean = path.isNotEmpty()

    /**
     * Returns the path on the current system of the emulated file.
     * @param root the path of the emulated root
     * @return the path of the emulated file
     */
    fun toPath(root: Path): Path {
        return if (absolute)
            root.resolve(toString())
        else
            Path.of(toString())
    }

    /**
     * Returns the concatenation of the two paths. The path given
     * as parameter must not be *absolute*
     * @param path the path to resolve
     * @return the concatenated path
     * @throws FileSystemException if [path] is absolute
     */
    fun resolve(path: UnixPath): UnixPath {
        if(path.absolute)
            throw FileSystemException("Cannot resolve absolute path")

        return UnixPath(this.path + path.path, absolute)
    }

    override fun toString(): String {
        return (if (!absolute) "" else separator) + path.joinToString(separator)
    }

    companion object {
        /**
         * The root of the Unix-like filesystem
         */
        val ROOT = UnixPath(listOf(),true)

        /**
         * Returns a [UnixPath] from its [String] representation
         * @param path the [String] representation of the path
         * @return the path generated from the String
         */
        fun of(path: String): UnixPath = UnixPath(path.split(separator), path.startsWith(separator))
    }
}