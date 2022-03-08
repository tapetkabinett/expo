package expo.modules.imagepicker.tasks

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import expo.modules.core.errors.ModuleDestroyedException
import expo.modules.imagepicker.*
import expo.modules.imagepicker.ImagePickerConstants.exifTags
import expo.modules.imagepicker.exporters.ImageExporter
import expo.modules.imagepicker.exporters.ImageExporter.Listener
import expo.modules.imagepicker.fileproviders.FileProvider
import expo.modules.kotlin.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class ImageResultTask(
  private val promise: Promise,
  private val uri: Uri,
  private val contentResolver: ContentResolver,
  private val fileProvider: FileProvider,
  private val isEdited: Boolean,
  private val withExifData: Boolean,
  private val imageExporter: ImageExporter,
  private var exifDataHandler: ExifDataHandler?,
  private val coroutineScope: CoroutineScope
) {
  /**
   * We need to make coroutine wait till the file is generated, while the underlying
   * thread is free to continue executing other coroutines.
   */
  private suspend fun getFile(): File = suspendCancellableCoroutine { cancellableContinuation ->
    try {
      val outputFile = fileProvider.generateFile()
      cancellableContinuation.resume(outputFile)
    } catch (e: Exception) {
      cancellableContinuation.resumeWithException(e)
    }
  }

  /**
   * We need to make coroutine wait till the exif data is being read, while the underlying
   * thread is free to continue executing other coroutines.
   */
  private suspend fun getExifData(): Bundle? = suspendCancellableCoroutine { cancellableContinuation ->
    try {
      val exif = if (withExifData) readExif() else null
      cancellableContinuation.resume(exif)
    } catch (e: Exception) {
      cancellableContinuation.resumeWithException(e)
    }
  }

  fun execute() {
    coroutineScope.launch {
      try {
        val outputFile = getFile()
        if (isEdited) {
          exifDataHandler?.copyExifData(uri, contentResolver)
        }
        val exif = getExifData()
        val imageExporterHandler = object : Listener {
          override fun onResult(out: ByteArrayOutputStream?, width: Int, height: Int) {
            val response = Bundle().apply {
              putString("uri", Uri.fromFile(outputFile).toString())
              putInt("width", width)
              putInt("height", height)
              putBoolean("cancelled", false)
              putString("type", "image")

              out?.let {
                putString("base64", Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP))
              }
              exif?.let {
                putBundle("exif", it)
              }
            }
            promise.resolve(response)
          }

          override fun onFailure(cause: Throwable?) {
            promise.reject(FailedToSaveResultToFileException(cause))
          }
        }
        imageExporter.export(uri, outputFile, imageExporterHandler)
      } catch (cause: ModuleDestroyedException) {
        Log.i(ImagePickerConstants.TAG, "Coroutine canceled by module destruction", cause)
        promise.reject(ModuleDestroyedException(cause))
      } catch (cause: IOException) {
        promise.reject(FailedToExtractMetadataException(cause))
      } catch (cause: Exception) {
        Log.e(ImagePickerConstants.TAG, "Unexpected exception", cause)
        promise.reject(UnexpectedException(cause))
      }
    }
  }

  @Throws(IOException::class)
  private fun readExif() = Bundle().apply {
    contentResolver.openInputStream(uri)?.use { input ->
      val exifInterface = ExifInterface(input)
      exifTags.forEach { (type, name) ->
        if (exifInterface.getAttribute(name) != null) {
          when (type) {
            "string" -> putString(name, exifInterface.getAttribute(name))
            "int" -> putInt(name, exifInterface.getAttributeInt(name, 0))
            "double" -> putDouble(name, exifInterface.getAttributeDouble(name, 0.0))
          }
        }
      }

      // Explicitly get latitude, longitude, altitude with their specific accessor functions.
      exifInterface.latLong?.let { latLong ->
        putDouble(ExifInterface.TAG_GPS_LATITUDE, latLong[0])
        putDouble(ExifInterface.TAG_GPS_LONGITUDE, latLong[1])
        putDouble(ExifInterface.TAG_GPS_ALTITUDE, exifInterface.getAltitude(0.0))
      }
    }
  }
}
