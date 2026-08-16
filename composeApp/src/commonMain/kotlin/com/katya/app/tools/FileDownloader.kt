package com.katya.app.tools

expect class FileDownloader() {
    /**
     * Downloads a file from the given URL and saves it to the destination path.
     * If useRoot is true, it writes the file using su -c.
     * @return Result message (success or error).
     */
    suspend fun download(url: String, destinationPath: String, useRoot: Boolean): String
}
