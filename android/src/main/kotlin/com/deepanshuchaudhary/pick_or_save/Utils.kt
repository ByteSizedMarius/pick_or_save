package com.deepanshuchaudhary.pick_or_save

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions.getExtensionVersion
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.deepanshuchaudhary.pick_or_save.PickOrSavePlugin.Companion.LOG_TAG
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.io.File

@Suppress("PropertyName")
class Utils {

    // Request code for ACTION_OPEN_DOCUMENT.
    val REQUEST_CODE_ACTION_OPEN_DOCUMENT = 1

    // Request code for ACTION_CREATE_DOCUMENT.
    val REQUEST_CODE_ACTION_CREATE_DOCUMENT = 2

    // Request code for ACTION_OPEN_DOCUMENT_TREE.
    val REQUEST_CODE_ACTION_OPEN_DOCUMENT_TREE = 3

    @SuppressLint("NewApi")
    fun isPhotoPickerAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getExtensionVersion(Build.VERSION_CODES.R) >= 2
        } else {
            false
        }
    }

    fun applyMimeTypesFilterToIntent(mimeTypesFilter: List<String>?, intent: Intent) {
        if ((mimeTypesFilter != null) && mimeTypesFilter.isNotEmpty()) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypesFilter.toTypedArray())
        }
    }

    fun getFileNameFromPickedDocumentUri(uri: Uri, context: Activity): String? {
        var fileName: String? = null
        val parsedScheme: String? = uri.scheme

        if (parsedScheme == "file") {
            fileName = uri.lastPathSegment
        } else {
            val contentResolver: ContentResolver = context.contentResolver
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor.use {
                if ((it != null) && it.moveToFirst()) {
                    fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        return cleanupFileName(fileName)
    }

    private fun cleanupFileName(fileName: String?): String? {
        // https://stackoverflow.com/questions/2679699/what-characters-allowed-in-file-names-on-android
        return fileName?.replace(Regex("[\\\\/:*?\"<>|\\[\\]]"), "_")
    }

    fun validateFileExtension(filePath: String, validFileExtensions: Array<String>?): Boolean {
        if (validFileExtensions == null || validFileExtensions.isEmpty()) {
            return true
        }
        val fileExtension = getFileExtension(filePath)
        for (extension in validFileExtensions) {
            if (fileExtension.equals(extension, true)) {
                return true
            }
        }
        return false
    }

    fun getFileExtension(fileName: String?): String {
        return fileName?.substringAfterLast('.', "") ?: ""
    }

    suspend fun copyFileToCacheDirOnBackground(
        context: Context,
        sourceFileUri: Uri,
        destinationFileName: String,
        resultCallback: MethodChannel.Result?,
    ): String? {
        var cachedFilePath: String? = null
        val uiScope = CoroutineScope(Dispatchers.Main)
        withContext(uiScope.coroutineContext) {
            try {
                Log.d(LOG_TAG, "Launch...")
                Log.d(LOG_TAG, "Copy on background...")
                val filePath = withContext(Dispatchers.IO) {
                    copyFileToCacheDir(context, sourceFileUri, destinationFileName)
                }
                Log.d(LOG_TAG, "...copied on background, result: $filePath")
                cachedFilePath = filePath
                Log.d(LOG_TAG, "...launch")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "copyFileToCacheDirOnBackground failed", e)
                finishWithError(
                    "file_copy_failed", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Error) {
                Log.e(LOG_TAG, "copyFileToCacheDirOnBackground failed", e)
                finishWithError(
                    "file_copy_failed", e.localizedMessage, e.toString(), resultCallback
                )
            }
        }
        return cachedFilePath
    }

    suspend fun copyMultipleFilesToCacheDirOnBackground(
        context: Context,
        sourceFileUris: List<Uri>,
        destinationFilesNames: List<String>,
        resultCallback: MethodChannel.Result?,
    ): List<String>? {
        var cachedFilesPaths: List<String>? = null
        val uiScope = CoroutineScope(Dispatchers.Main)
        withContext(uiScope.coroutineContext) {
            try {
                Log.d(LOG_TAG, "Launch...")

                Log.d(LOG_TAG, "Copy on background...")
                val filesPaths: MutableList<String> = mutableListOf()
                destinationFilesNames.indices.map { index ->
                    val destinationFileName = destinationFilesNames.elementAt(index)
                    val sourceFileUri = sourceFileUris.elementAt(index)
                    filesPaths.add(withContext(Dispatchers.IO) {
                        copyFileToCacheDir(context, sourceFileUri, destinationFileName)
                    })
                }
                Log.d(LOG_TAG, "...copied on background, result: $filesPaths")
                cachedFilesPaths = filesPaths
                Log.d(LOG_TAG, "...launch")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "copyMultipleFilesToCacheDirOnBackground failed", e)
                finishWithError(
                    "file_copy_failed", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Error) {
                Log.e(LOG_TAG, "copyMultipleFilesToCacheDirOnBackground failed", e)
                finishWithError(
                    "file_copy_failed", e.localizedMessage, e.toString(), resultCallback
                )
            }
        }
        return cachedFilesPaths
    }

    private fun copyFileToCacheDir(
        context: Context, sourceFileUri: Uri, destinationFileName: String,
    ): String {
        val cacheFileSuffix = ".${getFileExtension(destinationFileName)}"
        val cacheFilePrefix = "${destinationFileName.dropLast(cacheFileSuffix.length)}."

        // Getting destination file on cache directory.
        val destinationFile = File.createTempFile(cacheFilePrefix, cacheFileSuffix)
//        File(context.cacheDir.path, destinationFileName)

//        // Deleting existing destination file.
//        if (destinationFile.exists()) {
//            Log.d(LOG_TAG, "Deleting existing destination file '${destinationFile.path}'")
//            destinationFile.delete()
//        }

        // Copying file to cache directory.
        Log.d(LOG_TAG, "Copying '$sourceFileUri' to '${destinationFile.path}'")
        var copiedBytes: Long
        context.contentResolver.openInputStream(sourceFileUri).use { inputStream ->
            destinationFile.outputStream().use { outputStream ->
                copiedBytes = inputStream!!.copyTo(outputStream)
            }
        }

        Log.d(
            LOG_TAG,
            "Successfully copied file to '${destinationFile.absolutePath}, bytes=$copiedBytes'"
        )

        return destinationFile.absolutePath
    }


    suspend fun saveFileOnBackground(
        destinationSaveFileInfo: DestinationSaveFileInfo,
        destinationFileUri: Uri,
        resultCallback: MethodChannel.Result?,
        context: Context,
    ): String? {
        var savedFilePath: String? = null
        val uiScope = CoroutineScope(Dispatchers.Main)
        withContext(uiScope.coroutineContext) {
            val saveFile: File = destinationSaveFileInfo.file
            val isTempFile: Boolean = destinationSaveFileInfo.isTempFile
            try {
                Log.d(LOG_TAG, "Saving file on background...")
                val filePath = withContext(Dispatchers.IO) {
                    saveFile(saveFile, destinationFileUri, context)
                }
                Log.d(LOG_TAG, "...saved file on background, result: $filePath")
                savedFilePath = filePath
            } catch (e: SecurityException) {
                Log.e(LOG_TAG, "saveFileOnBackground", e)
                finishWithError(
                    "security_exception", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "saveFileOnBackground failed", e)
                finishWithError(
                    "save_file_failed", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Error) {
                Log.e(LOG_TAG, "saveFileOnBackground failed", e)
                finishWithError(
                    "save_file_failed", e.localizedMessage, e.toString(), resultCallback
                )
            } finally {
                if (isTempFile) {
                    Log.d(LOG_TAG, "Deleting temp source file: ${saveFile.path}")
                    saveFile.delete()
                }
            }
        }
        return savedFilePath
    }

    suspend fun saveMultipleFilesOnBackground(
        destinationSaveFilesInfo: List<DestinationSaveFileInfo>,
        destinationDirectoryUri: Uri,
        resultCallback: MethodChannel.Result?,
        context: Context,
    ): List<String> {
        val savedFilesPaths: MutableList<String> = mutableListOf()
        val uiScope = CoroutineScope(Dispatchers.Main)
        withContext(uiScope.coroutineContext) {
            try {
                Log.d(LOG_TAG, "Saving file on background...")
                val outputFolder: DocumentFile? = DocumentFile.fromTreeUri(
                    context, destinationDirectoryUri
                )
                destinationSaveFilesInfo.indices.map { index ->
                    yield()
                    val saveFile = destinationSaveFilesInfo.elementAt(index).file
                    val sourceFileMimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(saveFile.extension)
                    val saveFileNamePrefix: String =
                        destinationSaveFilesInfo.elementAt(index).saveFileNamePrefix
                    val documentFileNewFile = outputFolder!!.createFile(
                        sourceFileMimeType ?: "application/octet-stream", saveFileNamePrefix
                    )
                    val destinationFileUri: Uri = documentFileNewFile!!.uri
                    savedFilesPaths.add(withContext(Dispatchers.IO) {
                        saveFile(saveFile, destinationFileUri, context)
                    })
                }
                Log.d(LOG_TAG, "...saved file on background, result: $savedFilesPaths")
            } catch (e: SecurityException) {
                Log.e(LOG_TAG, "saveMultipleFilesOnBackground", e)
                finishWithError(
                    "security_exception", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "saveMultipleFilesOnBackground failed", e)
                finishWithError(
                    "save_file_failed", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Error) {
                Log.e(LOG_TAG, "saveMultipleFilesOnBackground failed", e)
                finishWithError(
                    "save_file_failed", e.localizedMessage, e.toString(), resultCallback
                )
            } finally {
                destinationSaveFilesInfo.indices.map { index ->
                    if (destinationSaveFilesInfo.elementAt(index).isTempFile) {
                        val saveFile = destinationSaveFilesInfo.elementAt(index).file
                        Log.d(LOG_TAG, "Deleting temp source file: ${saveFile.path}")
                        saveFile.delete()
                    }
                }
            }
        }
        return savedFilesPaths
    }

    private fun saveFile(
        sourceFile: File, destinationFileUri: Uri, context: Context,
    ): String {
        Log.d(LOG_TAG, "Saving file '${sourceFile.path}' to '${destinationFileUri.path}'")
        sourceFile.inputStream().use { inputStream ->
            context.contentResolver.openOutputStream(destinationFileUri).use { outputStream ->
                outputStream as java.io.FileOutputStream
                outputStream.channel.truncate(0)
                inputStream.copyTo(outputStream)
            }
        }
        Log.d(LOG_TAG, "Saved file to '${destinationFileUri.path}'")
        return destinationFileUri.path!!
    }

    fun cancelSaving(
    ) {
        fileSaveJob?.cancel()
        Log.d(LOG_TAG, "Canceled File Saving")
    }

    suspend fun deleteMultipleFilesOnBackground(
        destinationDeleteFilesInfo: List<DestinationDeleteFileInfo>,
        resultCallback: MethodChannel.Result?,
        context: Context,
    ): List<String> {
        val deletedFilesPaths: MutableList<String> = mutableListOf()
        val uiScope = CoroutineScope(Dispatchers.Main)
        withContext(uiScope.coroutineContext) {
            try {
                Log.d(LOG_TAG, "Deleting file on background...")
                destinationDeleteFilesInfo.indices.map { index ->
                    yield()
                    val deleteFileUri = destinationDeleteFilesInfo.elementAt(index).fileUri
                    if (DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(deleteFileUri))) {
                      deletedFilesPaths.add(deleteFileUri)
                    }
                }
                Log.d(LOG_TAG, "...deleted file on background, result: $deletedFilesPaths")
            } catch (e: SecurityException) {
                Log.e(LOG_TAG, "deleteMultipleFilesOnBackground", e)
                finishWithError(
                    "security_exception", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "deleteMultipleFilesOnBackground failed", e)
                finishWithError(
                    "delete_file_failed", e.localizedMessage, e.toString(), resultCallback
                )
            } catch (e: Error) {
                Log.e(LOG_TAG, "deleteMultipleFilesOnBackground failed", e)
                finishWithError(
                    "delete_file_failed", e.localizedMessage, e.toString(), resultCallback
                )
            }
        }
        return deletedFilesPaths
    }

    fun cancelDelete(
    ) {
        fileDeleteJob?.cancel()
        Log.d(LOG_TAG, "Canceled File Delete")
    }

    fun cancelDirectoryDocumentsPicker(
    ) {
        directoryDocumentsPickerJob?.cancel()
        Log.d(LOG_TAG, "Canceled directoryDocumentsPicker")
    }

    fun getURI(uri: String): Uri {
        val parsed: Uri = Uri.parse(uri)
        val parsedScheme: String? = parsed.scheme
        return if ((parsedScheme == null) || parsedScheme.isEmpty() || ("${uri[0]}" == "/")) {
            // Using "${uri[0]}" == "/" in condition above because if uri is an absolute file path without any scheme starting with "/"
            // and if its filename contains ":" then the parsed scheme will be wrong.
            Uri.fromFile(File(uri))
        } else parsed
    }

    private fun clearPendingResult(resultCallback: MethodChannel.Result?) {
        if (resultCallback == directoryDocumentsPickingResult && directoryDocumentsPickingResult != null) {
            directoryDocumentsPickingResult = null
            println("directoryDocumentsPickingResult result cleared")
        }
        if (resultCallback == filePickingResult && filePickingResult != null) {
            filePickingResult = null
            println("filePicking result cleared")
        }
        if (resultCallback == fileSavingResult && fileSavingResult != null) {
            fileSavingResult = null
            println("fileSaving result cleared")
        }
        if (resultCallback == fileDeleteResult && fileDeleteResult != null) {
            fileDeleteResult = null
            println("fileDelete result cleared")
        }
        if (resultCallback == fileMetadataResult && fileMetadataResult != null) {
            fileMetadataResult = null
            println("fileMetadata result cleared")
        }
        if (resultCallback == cacheFilePathFromUriResult && cacheFilePathFromUriResult != null) {
            cacheFilePathFromUriResult = null
            println("cacheFilePathFromUri result cleared")
        }
    }

    fun finishSuccessfully(result: Any?, resultCallback: MethodChannel.Result?) {
        resultCallback?.success(result)
        clearPendingResult(resultCallback)
    }

    fun finishWithAlreadyActiveError(result: MethodChannel.Result?) {
        result?.error("already_active", "File dialog is already active", null)
    }

    fun finishWithError(
        errorCode: String,
        errorMessage: String?,
        errorDetails: String?,
        resultCallback: MethodChannel.Result?
    ) {
        resultCallback?.error(errorCode, errorMessage, errorDetails)
        clearPendingResult(resultCallback)
    }

}