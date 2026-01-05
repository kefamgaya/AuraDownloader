package gain.aura.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentUris
import android.content.ContentValues
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import gain.aura.App.Companion.context
import gain.aura.R
import gain.aura.database.objects.DownloadedVideoInfo
import gain.aura.ui.page.videoplayer.VideoPlayerActivity
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly

const val AUDIO_REGEX = "(mp3|aac|opus|m4a)$"
const val VIDEO_REGEX = "(mp4|mkv|avi|webm|flv|mov|wmv|3gp|m4v)$"
const val THUMBNAIL_REGEX = "\\.(jpg|png)$"
const val SUBTITLE_REGEX = "\\.(lrc|vtt|srt|ass|json3|srv.|ttml)$"
private const val PRIVATE_DIRECTORY_SUFFIX = ".Aura"
/**
 * Checks if a file path or URI represents a video file.
 * Handles both regular file paths and content:// URIs.
 * Does NOT consider audio files as video.
 */
fun isVideoFile(path: String?): Boolean {
    if (path.isNullOrEmpty()) return false
    
    val fileName = when {
        path.startsWith("content://") -> {
            // For content URIs, try to get filename from URI or resolve it
            try {
                val uri = Uri.parse(path)
                // Try to get display name from MediaStore
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    } else null
                } ?: path.substringAfterLast("/")
            } catch (e: Exception) {
                path.substringAfterLast("/")
            }
        }
        path.startsWith("file://") -> path.removePrefix("file://").substringAfterLast("/")
        else -> path.substringAfterLast("/")
    }
    
    // Check if it's a video file AND not an audio file
    return fileName.contains(Regex(VIDEO_REGEX, RegexOption.IGNORE_CASE)) &&
           !fileName.contains(Regex(AUDIO_REGEX, RegexOption.IGNORE_CASE))
}

/**
 * Checks if a file path or URI represents an audio file.
 */
fun isAudioFile(path: String?): Boolean {
    if (path.isNullOrEmpty()) return false
    
    val fileName = when {
        path.startsWith("content://") -> {
            try {
                val uri = Uri.parse(path)
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    } else null
                } ?: path.substringAfterLast("/")
            } catch (e: Exception) {
                path.substringAfterLast("/")
            }
        }
        path.startsWith("file://") -> path.removePrefix("file://").substringAfterLast("/")
        else -> path.substringAfterLast("/")
    }
    
    return fileName.contains(Regex(AUDIO_REGEX, RegexOption.IGNORE_CASE))
}

/**
 * Normalizes a file path for consistent hashing.
 * Resolves content:// URIs to actual file paths when possible.
 * Used for saved position keys to ensure consistency.
 */
fun normalizeFilePath(path: String): String {
    return when {
        path.startsWith("content://") -> {
            // Try to resolve to actual file path
            FileUtil.resolveContentUriToFilePath(Uri.parse(path)) ?: path
        }
        path.startsWith("file://") -> path.removePrefix("file://")
        else -> path
    }
}

object FileUtil {
    fun openFileFromResult(downloadResult: Result<List<String>>) {
        val filePaths = downloadResult.getOrNull()
        if (filePaths.isNullOrEmpty()) return
        openFile(filePaths.first()) {
            ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
        }
    }

    /**
     * Resolves a content URI to an actual file path by querying MediaStore
     */
    fun resolveContentUriToFilePath(contentUri: Uri): String? {
        return try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
            context.contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // Try to get file path from DATA column (may not be available on Android 10+)
                    try {
                        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIndex >= 0) {
                            val filePath = cursor.getString(dataIndex)
                            if (!filePath.isNullOrEmpty() && File(filePath).exists()) {
                                return filePath
                            }
                        }
                    } catch (e: Exception) {
                        // DATA column not available (Android 10+ scoped storage)
                    }
                    
                    // Get display name and relative path to construct file path
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    
                    val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null
                    
                    if (!displayName.isNullOrEmpty()) {
                        // Try to construct path from relative path and display name
                        val downloadPaths = mutableListOf<String>()
                        
                        // Add path from relative path if available
                        if (!relativePath.isNullOrEmpty()) {
                            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            downloadPaths.add(File(baseDir, relativePath).resolve(displayName).absolutePath)
                        }
                        
                        // Add common download paths
                        downloadPaths.addAll(listOf(
                            "/storage/emulated/0/Download/Aura/$displayName",
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                .resolve("Aura").resolve(displayName).absolutePath
                        ))
                        
                        // Try to find the file
                        downloadPaths.firstOrNull { File(it).exists() }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving content URI to file path", e)
            null
        }
    }

    /**
     * Searches for a media file in Downloads/Aura directory by filename
     */
    fun findVideoFileInDownloadsAura(fileName: String): String? {
        return try {
            val downloadDir = File("/storage/emulated/0/Download/Aura")
            if (downloadDir.exists() && downloadDir.isDirectory) {
                // First try exact match
                val exactMatch = downloadDir.listFiles()?.firstOrNull { file ->
                    file.isFile && file.name.equals(fileName, ignoreCase = true)
                }
                if (exactMatch != null) {
                    return exactMatch.absolutePath
                }
                
                // Then try matching by base name with any media extension
                val videoExtensions = listOf("mp4", "mkv", "avi", "webm", "flv", "mov", "wmv", "3gp", "m4v")
                val audioExtensions = listOf("mp3", "aac", "opus", "m4a", "ogg", "flac", "wav")
                val allExtensions = videoExtensions + audioExtensions
                
                val baseName = fileName.substringBeforeLast(".")
                downloadDir.listFiles()?.firstOrNull { file ->
                    file.isFile && 
                    file.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
                    allExtensions.contains(file.extension.lowercase())
                }?.absolutePath
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for file in Downloads/Aura: $fileName", e)
            null
        }
    }
    
    /**
     * Searches for ANY file in Downloads/Aura directory by filename (more lenient)
     */
    fun findAnyFileInDownloadsAura(fileName: String): String? {
        return try {
            val downloadDir = File("/storage/emulated/0/Download/Aura")
            if (downloadDir.exists() && downloadDir.isDirectory) {
                // Exact match first
                downloadDir.listFiles()?.firstOrNull { file ->
                    file.isFile && file.name.equals(fileName, ignoreCase = true)
                }?.absolutePath ?: run {
                    // Partial match - file name contains our search term
                    val baseName = fileName.substringBeforeLast(".")
                    downloadDir.listFiles()?.firstOrNull { file ->
                        file.isFile && file.name.contains(baseName, ignoreCase = true)
                    }?.absolutePath
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for any file in Downloads/Aura: $fileName", e)
            null
        }
    }

    /**
     * Searches for a file in MediaStore by filename.
     * Essential for finding files on Android 11+ where direct path access is restricted.
     * Tries exact match first, then partial match (base name without extension).
     */
    fun findFileUriInMediaStore(fileName: String): Uri? {
        return try {
            val collection = if (android.os.Build.VERSION.SDK_INT >= 29) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
            
            // First try exact match
            val exactSelection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
            val exactSelectionArgs = arrayOf(fileName)
            
            context.contentResolver.query(
                collection,
                projection,
                exactSelection,
                exactSelectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    if (idIndex >= 0) {
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
            }
            
            // If exact match fails, try partial match (base name)
            val baseName = fileName.substringBeforeLast(".")
            if (baseName.isNotEmpty() && baseName != fileName) {
                val partialSelection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                val partialSelectionArgs = arrayOf("$baseName.%")
                
                context.contentResolver.query(
                    collection,
                    projection,
                    partialSelection,
                    partialSelectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                        if (idIndex >= 0) {
                            val id = cursor.getLong(idIndex)
                            return ContentUris.withAppendedId(collection, id)
                        }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding file in MediaStore", e)
            null
        }
    }

    fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) {
        Log.d(TAG, "openFile called with path: $path")
        
        // Always resolve to file path - no URIs!
        val actualPath = when {
            path.startsWith("content://") -> {
                Log.d(TAG, "Resolving content URI: $path")
                // Resolve content URI to file path
                resolveContentUriToFilePath(Uri.parse(path)) ?: run {
                    // If resolution fails, try to get filename and search in Downloads/Aura
                    try {
                        val contentUri = Uri.parse(path)
                        val displayName = context.contentResolver.query(
                            contentUri,
                            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                            null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) cursor.getString(nameIndex) else null
                            } else null
                        }
                        
                        Log.d(TAG, "Extracted display name from content URI: $displayName")
                        
                        // Search for file in Downloads/Aura by name
                        if (!displayName.isNullOrEmpty()) {
                            findVideoFileInDownloadsAura(displayName)
                        } else null
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting filename from content URI", e)
                        null
                    }
                } ?: path
            }
            path.startsWith("file://") -> {
                path.removePrefix("file://")
            }
            else -> {
                // Already a file path - verify it exists
                val file = File(path)
                if (file.exists()) {
                    path
                } else {
                    Log.d(TAG, "File not found at path: $path, searching in Downloads/Aura")
                    // File doesn't exist at this path, try to find it in Downloads/Aura
                    val fileName = file.name
                    if (!fileName.isEmpty()) {
                        findVideoFileInDownloadsAura(fileName) ?: path
                    } else {
                        path
                    }
                }
            }
        }
        
        Log.d(TAG, "Resolved path: $actualPath")
        
        // Final verification: make sure we have a valid file path
        val finalPath = if (!actualPath.startsWith("/storage/") && !actualPath.startsWith("/")) {
            // Not a valid path, try Downloads/Aura
            val fileName = actualPath.substringAfterLast("/")
            if (!fileName.isEmpty()) {
                findVideoFileInDownloadsAura(fileName) ?: actualPath
            } else {
                actualPath
            }
        } else {
            actualPath
        }
        
        Log.d(TAG, "Final path: $finalPath")
        
        // Verify file exists
        val file = File(finalPath)
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: $finalPath")
            // Last attempt: use lenient search in Downloads/Aura
            val fileName = file.name
            if (!fileName.isEmpty()) {
                // Try file system search first (fastest)
                val foundPath = findAnyFileInDownloadsAura(fileName)
                if (foundPath != null) {
                    Log.d(TAG, "Found file by lenient search: $foundPath")
                    val fileUri = "file://$foundPath"
                    try {
                        createIntentForOpeningFile(fileUri)?.run { 
                            context.startActivity(this)
                            return
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening file by lenient search", e)
                    }
                } else {
                    // Try MediaStore search (robust against Scoped Storage)
                    Log.d(TAG, "File system search failed, trying MediaStore for: $fileName")
                    val foundUri = findFileUriInMediaStore(fileName)
                    if (foundUri != null) {
                        Log.d(TAG, "Found file in MediaStore: $foundUri")
                        try {
                            createIntentForOpeningFile(foundUri.toString())?.run {
                                context.startActivity(this)
                                return
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening file by MediaStore URI", e)
                        }
                    }
                }
            }
            onFailureCallback(Exception("File not found: $finalPath"))
            return
        }
        
        // Open with file path - convert to file:// URI for VideoPlayerActivity
        val fileUri = "file://$finalPath"
        Log.d(TAG, "Opening file with URI: $fileUri")
        val result = fileUri.runCatching {
            createIntentForOpeningFile(this)?.run { 
                context.startActivity(this) 
            } ?: throw Exception("Cannot create intent to open file")
        }
        
        result.onFailure { error ->
            Log.e(TAG, "Error opening file", error)
            onFailureCallback(error)
        }
    }

    private fun createIntentForFile(path: String?): Intent? {
        if (path == null) return null

        val uri =
            path
                .runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(path)).run {
                        if (this?.exists() == true) {
                            this.uri
                        } else if (File(this@runCatching).exists()) {
                            FileProvider.getUriForFile(
                                context,
                                context.getFileProvider(),
                                File(this@runCatching),
                            )
                        } else null
                    }
                }
                .getOrNull() ?: return null

        return Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            data = uri
        }
    }

    fun createIntentForOpeningFile(path: String?): Intent? {
        if (path == null) return null
        
        // Handle content:// URIs directly
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            // Check if it's a video based on extension from display name or mime type
            val isVideo = isVideoFile(path)
            
            if (isVideo) {
                val title = try {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) else "Video"
                    } ?: "Video"
                } catch (e: Exception) { "Video" }
                
                return VideoPlayerActivity.createIntent(context, path, title)
            } else {
                return Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        
        // Normalize path - remove file:// prefix if present to get actual file path
        val normalizedPath = path.removePrefix("file://")
        
        // Verify file exists
        val file = try {
            File(normalizedPath)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid file path: $normalizedPath", e)
            return null
        }
        
        // Check if it's a video file by extension
        val isVideo = normalizedPath.contains(Regex(VIDEO_REGEX, RegexOption.IGNORE_CASE))
        
        // Try to access the file - on Android 10+, File.exists() might return false
        // even if the file is accessible via FileProvider, so we'll try both approaches
        val fileExists = try {
            file.exists()
        } catch (e: Exception) {
            Log.d(TAG, "Error checking file existence: ${e.message}")
            false
        }
        
        // If file doesn't exist via direct path (common on Android 10+ with scoped storage),
        // try to find it via MediaStore
        if (!fileExists) {
            Log.d(TAG, "File not accessible via direct path: $normalizedPath, trying MediaStore")
            val fileName = file.name
            if (!fileName.isEmpty()) {
                // Try to find file in MediaStore by name
                val foundUri = findFileUriInMediaStore(fileName)
                if (foundUri != null) {
                    Log.d(TAG, "Found file in MediaStore: $foundUri")
                    if (isVideo) {
                        val title = try {
                            context.contentResolver.query(foundUri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) {
                                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                                    name.substringBeforeLast(".").ifEmpty { "Video" }
                                } else "Video"
                            } ?: "Video"
                        } catch (e: Exception) { "Video" }
                        return VideoPlayerActivity.createIntent(context, foundUri.toString(), title)
                    } else {
                        return Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(foundUri, context.contentResolver.getType(foundUri) ?: "*/*")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                }
            }
            // Even if File.exists() returns false, try FileProvider - it might work
            // This handles cases where the file exists but permissions prevent direct access
            Log.d(TAG, "File.exists() returned false, but trying FileProvider anyway for: $normalizedPath")
        }
        
        if (isVideo) {
            val videoTitle = try {
                file.nameWithoutExtension.ifEmpty { "Video" }
            } catch (e: Exception) {
                "Video"
            }
            
            // Pass the file path directly to VideoPlayerActivity (not file:// URI)
            // VideoPlayerScreen will handle it properly with FileProvider
            return VideoPlayerActivity.createIntent(context, normalizedPath, videoTitle)
        }
        
        // For non-video files, use FileProvider
        return try {
            Intent(Intent.ACTION_VIEW).apply {
                val uri = FileProvider.getUriForFile(
                    context,
                    context.getFileProvider(),
                    file
                )
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating intent for non-video file", e)
            null
        }
    }

    fun createIntentForSharingFile(path: String?): Intent? =
        createIntentForFile(path)?.apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, data)
            val mimeType = data?.let { context.contentResolver.getType(it) } ?: "media/*"
            setDataAndType(this.data, mimeType)
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(data))
        }

    fun Context.getFileProvider() = "$packageName.provider"

    fun String.getFileSize(): Long =
        this.run {
            val length = File(this).length()
            if (length == 0L) DocumentFile.fromSingleUri(context, Uri.parse(this))?.length() ?: 0L
            else length
        }

    fun String.getFileName(): String =
        this.run {
            File(this).nameWithoutExtension.ifEmpty {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.name ?: "video"
            }
        }

    fun deleteFile(path: String) =
        path.runCatching {
            if (!File(path).delete()) DocumentFile.fromSingleUri(context, Uri.parse(this))?.delete()
        }

    @CheckResult
    fun scanFileToMediaLibraryPostDownload(title: String, downloadDir: String): List<String> =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile && it.absolutePath.contains(title) }
            .map { it.absolutePath }
            .toMutableList()
            .apply {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
                removeAll {
                    it.contains(Regex(THUMBNAIL_REGEX)) || it.contains(Regex(SUBTITLE_REGEX))
                }
            }

    fun scanDownloadDirectoryToMediaLibrary(downloadDir: String) =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .run {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
            }

    /**
     * Scans the Downloads/Aura directory for video files and imports them into the database
     */
    suspend fun scanAndImportVideosFromDownloadsAura(): Int = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File("/storage/emulated/0/Download/Aura")
            if (!downloadDir.exists() || !downloadDir.isDirectory) {
                Log.w(TAG, "Downloads/Aura directory does not exist")
                return@withContext 0
            }

            val videoFiles = downloadDir
                .walkTopDown()
                .filter { file ->
                    file.isFile && file.extension.lowercase().matches(Regex(VIDEO_REGEX, RegexOption.IGNORE_CASE))
                }
                .toList()

            if (videoFiles.isEmpty()) {
                Log.d(TAG, "No video files found in Downloads/Aura")
                return@withContext 0
            }

            var importedCount = 0
            videoFiles.forEach { file ->
                try {
                    val filePath = file.absolutePath
                    val fileName = file.nameWithoutExtension
                    
                    // Check if already exists in database
                    val existingInfo = DatabaseUtil.getInfoByPath(filePath)
                    if (existingInfo == null) {
                        // Create DownloadedVideoInfo for this file
                        val videoInfo = DownloadedVideoInfo(
                            id = 0,
                            videoTitle = fileName,
                            videoAuthor = "Unknown",
                            videoUrl = "",
                            thumbnailUrl = "",
                            videoPath = filePath,
                            extractor = "Local File"
                        )
                        
                        // Insert into database
                        DatabaseUtil.insertInfo(videoInfo)
                        importedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error importing video file: ${file.absolutePath}", e)
                }
            }

            Log.d(TAG, "Imported $importedCount video files from Downloads/Aura")
            importedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning Downloads/Aura directory", e)
            0
        }
    }

    @CheckResult
    fun moveFilesToSdcard(tempPath: File, sdcardUri: String): Result<List<String>> {
        val uriList = mutableListOf<String>()
        val destDir =
            Uri.parse(sdcardUri).run {
                DocumentsContract.buildDocumentUriUsingTree(
                    this,
                    DocumentsContract.getTreeDocumentId(this),
                )
            }
        val res =
            tempPath.runCatching {
                walkTopDown().forEach {
                    if (it.isDirectory) return@forEach
                    val mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

                    val destUri =
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            destDir,
                            mimeType,
                            it.name,
                        ) ?: return@forEach

                    val inputStream = it.inputStream()
                    val outputStream =
                        context.contentResolver.openOutputStream(destUri) ?: return@forEach
                    inputStream.copyTo(outputStream)
                    inputStream.closeQuietly()
                    outputStream.closeQuietly()
                    uriList.add(destUri.toString())
                }
                uriList
            }
        tempPath.deleteRecursively()
        return res
    }

    fun clearTempFiles(downloadDir: File): Int {
        var count = 0
        downloadDir.walkTopDown().forEach {
            if (it.isFile && !it.isHidden) {
                if (it.delete()) count++
            }
        }
        return count
    }

    fun Context.getConfigDirectory(): File = cacheDir

    fun Context.getConfigFile(suffix: String = "") = File(getConfigDirectory(), "config$suffix.txt")

    fun Context.getCookiesFile() = File(getConfigDirectory(), "cookies.txt")

    fun getExternalTempDir() =
        File(getExternalDownloadDirectory(), "tmp").apply {
            mkdirs()
            createEmptyFile(".nomedia")
        }

    fun Context.getSdcardTempDir(child: String?): File =
        getExternalTempDir().run { child?.let { resolve(it) } ?: this }

    fun Context.getArchiveFile(): File = filesDir.createEmptyFile("archive.txt").getOrThrow()

    fun Context.getInternalTempDir() = File(filesDir, "tmp")

    internal fun getExternalDownloadDirectory() =
        (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
            .resolve("Aura")
            .also { it.mkdirs() }

    internal fun getExternalPrivateDownloadDirectory() =
        (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
            .resolve(PRIVATE_DIRECTORY_SUFFIX)

    fun File.createEmptyFile(fileName: String): Result<File> =
        this.runCatching {
                mkdirs()
                resolve(fileName).apply { this@apply.createNewFile() }
            }
            .onFailure { it.printStackTrace() }

    fun writeContentToFile(content: String, file: File): File = file.apply { writeText(content) }

    fun getRealPath(treeUri: Uri): String {
        val path: String = treeUri.path.toString()
        Log.d(TAG, path)
        if (!path.contains("primary:")) {
            ToastUtil.makeToast("This directory is not supported")
            return getExternalDownloadDirectory().absolutePath
        }
        // Scoped storage: do not attempt to resolve tree URIs into raw filesystem paths.
        // Return app-owned download directory instead.
        return getExternalDownloadDirectory().absolutePath
    }

    private const val TAG = "FileUtil"

    /**
     * Exports a file into user-visible Downloads via MediaStore (scoped storage compliant).
     * Returns the actual file path string on success, or null on failure.
     */
    fun exportToMediaStoreDownloads(file: File, subDir: String = "Aura"): String? {
        return runCatching {
                val ext = file.extension.lowercase()
                val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

                Log.d(TAG, "Exporting file to MediaStore: ${file.absolutePath}, mimeType: $mimeType")

                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            val relativePath = if (subDir.isNotEmpty()) {
                                Environment.DIRECTORY_DOWNLOADS + "/$subDir"
                            } else {
                                Environment.DIRECTORY_DOWNLOADS
                            }
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = context.contentResolver.insert(collection, values) ?: run {
                    Log.e(TAG, "Failed to insert into MediaStore")
                    return null
                }
                
                Log.d(TAG, "MediaStore URI created: $uri")

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: run {
                    Log.e(TAG, "Failed to open output stream for MediaStore")
                    return null
                }

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }.also {
                        context.contentResolver.update(uri, it, null, null)
                    }
                } else {
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }

                // First, try to resolve the actual path from MediaStore
                val resolvedPath = resolveContentUriToFilePath(uri)
                if (resolvedPath != null && File(resolvedPath).exists()) {
                    Log.d(TAG, "Resolved MediaStore path: $resolvedPath")
                    return resolvedPath
                }
                
                // Fallback: construct the expected path
                val downloadPath = if (subDir.isNotEmpty()) {
                    "/storage/emulated/0/Download/$subDir/${file.name}"
                } else {
                    "/storage/emulated/0/Download/${file.name}"
                }
                
                // Verify the file exists at this path
                val finalFile = File(downloadPath)
                if (finalFile.exists()) {
                    Log.d(TAG, "Constructed path exists: $downloadPath")
                    downloadPath
                } else {
                    // File doesn't exist at expected path, search for it
                    Log.w(TAG, "File not found at expected path: $downloadPath, searching...")
                    findAnyFileInDownloadsAura(file.name) ?: run {
                        Log.e(TAG, "Could not find exported file anywhere!")
                        downloadPath // Return the expected path anyway
                    }
                }
            }
            .onFailure { e ->
                Log.e(TAG, "Error exporting file to MediaStore", e)
            }
            .getOrNull()
    }
}
