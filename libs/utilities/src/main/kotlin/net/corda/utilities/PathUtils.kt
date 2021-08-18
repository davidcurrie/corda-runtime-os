package net.corda.utilities

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.CopyOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileTime
import java.util.stream.Stream
import kotlin.streams.toList

/**
 * Allows you to write code like: Paths.get("someDir") / "subdir" / "filename" but using the Paths API to avoid platform
 * separator problems.
 * @see Path.resolve
 */
operator fun Path.div(other: String): Path = resolve(other)

/**
 * Allows you to write code like: "someDir" / "subdir" / "filename" but using the Paths API to avoid platform
 * separator problems.
 * @see Path.resolve
 */
operator fun String.div(other: String): Path = Paths.get(this) / other

/** @see Files.createFile */
fun Path.createFile(vararg attrs: FileAttribute<*>): Path = Files.createFile(this, *attrs)

/** @see Files.createDirectory */
fun Path.createDirectory(vararg attrs: FileAttribute<*>): Path = Files.createDirectory(this, *attrs)

/** @see Files.createDirectories */
fun Path.createDirectories(vararg attrs: FileAttribute<*>): Path = Files.createDirectories(this, *attrs)

/** @see Files.exists */
fun Path.exists(vararg options: LinkOption): Boolean = Files.exists(this, *options)

/** Copy the file into the target directory using [Files.copy]. */
fun Path.copyToDirectory(targetDir: Path, vararg options: CopyOption): Path {
    require(targetDir.isDirectory()) { "$targetDir is not a directory" }
    /*
     * We must use fileName.toString() here because resolve(Path)
     * will throw ProviderMismatchException if the Path parameter
     * and targetDir have different Path providers, e.g. a file
     * on the filesystem vs an entry in a ZIP file.
     *
     * Path.toString() is assumed safe because fileName should
     * not include any path separator characters.
     */
    val targetFile = targetDir.resolve(fileName.toString())
    Files.copy(this, targetFile, *options)
    return targetFile
}

/** @see Files.copy */
fun Path.copyTo(target: Path, vararg options: CopyOption): Path = Files.copy(this, target, *options)

/** @see Files.move */
fun Path.moveTo(target: Path, vararg options: CopyOption): Path = Files.move(this, target, *options)

/** @see Files.isRegularFile */
fun Path.isRegularFile(vararg options: LinkOption): Boolean = Files.isRegularFile(this, *options)

/** @see Files.getLastModifiedTime */
fun Path.lastModifiedTime(vararg options: LinkOption): FileTime = Files.getLastModifiedTime(this, *options)

/** @see Files.isDirectory */
fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)

/** @see Files.isSameFile */
fun Path.isSameAs(other: Path): Boolean = Files.isSameFile(this, other)

/**
 * Same as [Files.list] except it also closes the [Stream].
 * @return the output of [block]
 */
inline fun <R> Path.list(block: (Stream<Path>) -> R): R = Files.list(this).use(block)

/** Same as [list] but materialises all the entiries into a list. */
fun Path.list(): List<Path> = list { it.toList() }

/** @see Files.delete */
fun Path.delete(): Unit = Files.delete(this)

/** @see Files.deleteIfExists */
fun Path.deleteIfExists(): Boolean = Files.deleteIfExists(this)

/** Deletes this path (if it exists) and if it's a directory, all its child paths recursively. */
fun Path.deleteRecursively() {
    if (!exists()) return
    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            file.delete()
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exception: IOException?): FileVisitResult {
            dir.delete()
            return FileVisitResult.CONTINUE
        }
    })
}

/** @see Files.newOutputStream */
fun Path.outputStream(vararg options: OpenOption): OutputStream = Files.newOutputStream(this, *options)

/** @see Files.newInputStream */
fun Path.inputStream(vararg options: OpenOption): InputStream = Files.newInputStream(this, *options)

/** @see Files.newBufferedReader */
fun Path.reader(charset: Charset = UTF_8): BufferedReader = Files.newBufferedReader(this, charset)

/** @see Files.newBufferedWriter */
fun Path.writer(charset: Charset = UTF_8, vararg options: OpenOption): BufferedWriter {
    return Files.newBufferedWriter(this, charset, *options)
}

/** @see Files.readAllBytes */
fun Path.readAll(): ByteArray = Files.readAllBytes(this)

/** @see Files.write */
fun Path.write(bytes: ByteArray, vararg options: OpenOption): Path = Files.write(this, bytes, *options)

/** Write the given string to this file. */
fun Path.writeText(text: String, charset: Charset = UTF_8, vararg options: OpenOption) {
    writer(charset, *options).use { it.write(text) }
}

/**
 * Same as [inputStream] except it also closes the [InputStream].
 * @return the output of [block]
 */
inline fun <R> Path.read(vararg options: OpenOption, block: (InputStream) -> R): R = inputStream(*options).use(block)

/**
 * Same as [outputStream] except it also closes the [OutputStream].
 * @param createDirs if true then the parent directory of this file is created. Defaults to false.
 * @return the output of [block]
 */
inline fun Path.write(createDirs: Boolean = false, vararg options: OpenOption = emptyArray(), block: (OutputStream) -> Unit) {
    if (createDirs) {
        normalize().parent?.createDirectories()
    }
    outputStream(*options).use(block)
}

/**
 * Same as [Files.lines] except it also closes the [Stream]
 * @return the output of [block]
 */
inline fun <R> Path.readLines(charset: Charset = UTF_8, block: (Stream<String>) -> R): R {
    return Files.lines(this, charset).use(block)
}

fun Path.writeLines(lines: Iterable<CharSequence>, charset: Charset = UTF_8, vararg options: OpenOption): Path {
    return Files.write(this, lines, charset, *options)
}

/* Check if the Path is symbolic link */
fun Path.safeSymbolicRead(): Path {
    if (Files.isSymbolicLink(this)) {
        return (Files.readSymbolicLink(this))
    } else {
        return (this)
    }
}

fun URI.toPath(): Path = Paths.get(this)

fun URL.toPath(): Path = toURI().toPath()