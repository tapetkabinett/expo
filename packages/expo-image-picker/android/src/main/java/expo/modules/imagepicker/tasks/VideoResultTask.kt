package expo.modules.imagepicker.tasks

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import expo.modules.core.errors.ModuleDestroyedException
import expo.modules.imagepicker.FailedToExtractMetadataException
import expo.modules.imagepicker.FailedToSaveResultToFileException
import expo.modules.imagepicker.ImagePickerConstants
import expo.modules.imagepicker.UnexpectedException
import expo.modules.imagepicker.fileproviders.FileProvider
import expo.modules.kotlin.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoResultTask(
  private val promise: Promise,
  private val uri: Uri,
  private val contentResolver: ContentResolver,
  private val fileProvider: FileProvider,
  private val mediaMetadataRetriever: MediaMetadataRetriever,
  private val coroutineScope: CoroutineScope
) {
  private fun extractMediaMetadata(key: Int): Int =
    mediaMetadataRetriever.extractMetadata(key)!!.toInt()

  /**
   * We need to make coroutine wait till the video is saved, while the underlying
   * thread is free to continue executing other coroutines.
   */
  private suspend fun getFile(): File = suspendCancellableCoroutine { cancellableContinuation ->
    try {
      val outputFile = fileProvider.generateFile()
      saveVideo(outputFile)
      cancellableContinuation.resume(outputFile)
    } catch (e: Exception) {
      cancellableContinuation.resumeWithException(e)
    }
  }

  fun execute() {
    coroutineScope.launch {
      try {
        val outputFile = getFile()
        val response = Bundle().apply {
          putString("uri", Uri.fromFile(outputFile).toString())
          putBoolean("cancelled", false)
          putString("type", "video")
          putInt("width", extractMediaMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
          putInt("height", extractMediaMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
          putInt("rotation", extractMediaMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
          putInt("duration", extractMediaMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
        }
        promise.resolve(response)
      } catch (cause: ModuleDestroyedException) {
        Log.d(ImagePickerConstants.TAG, "Coroutine canceled by module destruction", cause)
        promise.reject(expo.modules.imagepicker.ModuleDestroyedException(cause))
      } catch (cause: NullPointerException) {
        promise.reject(FailedToExtractMetadataException(cause))
      } catch (cause: IllegalArgumentException) {
        promise.reject(FailedToExtractMetadataException(cause))
      } catch (cause: SecurityException) {
        promise.reject(FailedToExtractMetadataException(cause))
      } catch (cause: IOException) {
        promise.reject(FailedToSaveResultToFileException(cause))
      } catch (cause: Exception) {
        Log.e(ImagePickerConstants.TAG, "Unexpected exception", cause)
        promise.reject(UnexpectedException(cause))
      }
    }
  }

  @Throws(IOException::class)
  private fun saveVideo(outputFile: File) {
    contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(outputFile).use { out ->
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } > 0) {
          out.write(buffer, 0, bytesRead)
        }
      }
    }
  }
}
