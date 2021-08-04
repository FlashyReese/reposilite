package com.reposilite.shared

import com.reposilite.failure.api.ErrorResponse
import com.reposilite.failure.api.errorResponse
import com.reposilite.shared.FileType.DIRECTORY
import com.reposilite.shared.FileType.FILE
import com.reposilite.web.toPath
import io.javalin.http.HttpCode
import io.javalin.http.HttpCode.NOT_FOUND
import panda.std.Result
import panda.std.Result.error
import panda.std.Result.ok
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.isDirectory
import kotlin.streams.toList
import panda.std.Result.`when` as once

enum class FileType {
    FILE,
    DIRECTORY
}

fun Path.exists(): Result<Path, ErrorResponse> =
    once(Files.exists(this), this, ErrorResponse(NOT_FOUND, "File not found: $this"))

fun Path.type(): FileType =
    if (this.isDirectory()) DIRECTORY else FILE

fun Path.delete(): Result<*, ErrorResponse> =
    catchIOException {
        exists().map { Files.delete(this) }
    }

fun Path.getLastModifiedTime(): Result<FileTime, ErrorResponse> =
    catchIOException {
        exists().map { Files.getLastModifiedTime(this) }
    }

fun Path.listFiles(): Result<List<Path>, ErrorResponse> =
    catchIOException {
        exists().map {
            Files.walk(this, 1).filter { it != this }.toList()
        }
    }

fun Path.inputStream(): Result<InputStream, ErrorResponse> =
    catchIOException {
        exists()
            .filter({ it.isDirectory().not() }, { ErrorResponse(HttpCode.NO_CONTENT, "Requested file is a directory") })
            .map { Files.newInputStream(it) }
    }

fun Path.size(): Result<Long, ErrorResponse> =
    catchIOException {
        exists().map {
            when (type()) {
                FILE -> Files.size(this)
                DIRECTORY -> Files.walk(this).mapToLong { Files.size(it) }.sum()
            }
        }
    }

fun Path.append(path: String): Result<Path, IOException> =
    path.toNormalizedPath().map { this.resolve(it).normalize() }

fun String.toNormalizedPath(): Result<Path, IOException> =
    normalizedAsUri().map { it.toPath().normalize() }

/**
 * Process uri applying following changes:
 *
 *
 *  * Remove root slash
 *  * Remove illegal path modifiers like .. and ~
 *
 *
 * @return the normalized uri
 */
fun String.normalizedAsUri(): Result<String, IOException> {
    var normalizedUri = this

    if (normalizedUri.contains("..") || normalizedUri.contains(":") || normalizedUri.contains("\\")) {
        return error(IOException("Illegal path operator in URI"))
    }

    while (normalizedUri.contains("//")) {
        normalizedUri = normalizedUri.replace("//", "/")
    }

    if (normalizedUri.startsWith("/")) {
        normalizedUri = normalizedUri.substring(1)
    }

    return ok(normalizedUri)
}

fun <VALUE> catchIOException(consumer: () -> Result<VALUE, ErrorResponse>): Result<VALUE, ErrorResponse> =
    try {
        consumer()
    } catch (ioException: IOException) {
        errorResponse(HttpCode.INTERNAL_SERVER_ERROR, ioException.localizedMessage)
    }