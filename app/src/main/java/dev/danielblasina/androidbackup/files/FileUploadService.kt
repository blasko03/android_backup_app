package dev.danielblasina.androidbackup.files

import android.net.Uri
import dev.danielblasina.androidbackup.utils.JsonParse
import dev.danielblasina.androidbackup.utils.executeWithRescue
import dev.danielblasina.androidbackup.utils.executeWithRescueJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path

const val MEDIA_TYPE_JSON = "application/json"

data class UploadedFile(
    val name: String,
    val chunks: List<ByteArray> = listOf(),
    val hash: ByteArray,
)

data class UploadedFileCheck(
    val name: String,
    val present: Boolean,
)
class FileUploadService {

    val client = OkHttpClient()
    val uri = URI("http://192.168.1.53:8080/")

    fun chunkUpload(filename: String, chunk: ByteArray): Result<Response> {
        val requestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, chunk.toRequestBody())
                .build()
        Uri.Builder()
        val request: Request =
            Request
                .Builder()
                .url(uri.resolve("chunk").toURL())
                .post(requestBody)
                .build()
        return client.newCall(request).executeWithRescue()
    }

    fun chunkPresent(filename: String): Result<Response> {
        val request: Request =
            Request
                .Builder()
                .url(uri.resolve("chunk/").resolve(filename).toURL())
                .build()
        return client.newCall(request).executeWithRescue(successCodes = arrayOf(HttpURLConnection.HTTP_OK))
    }

    fun fileUpload(
        filename: Path,
        checksum: ByteArray,
        chunks: ArrayList<ByteArray>,
    ): Result<Response> {
        val json = JsonParse.objectToJsonString(UploadedFile(filename.toString(), chunks, checksum))
        val request: Request =
            Request
                .Builder()
                .url(uri.resolve("file").toURL())
                .post(json.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
                .build()

        return client.newCall(request).executeWithRescue()
    }

    fun filesPresent(files: List<UploadedFile>): Result<List<UploadedFileCheck>> {
        val json = JsonParse.objectToJsonString(files)
        val request: Request =
            Request
                .Builder()
                .url(uri.resolve("has_files").toURL())
                .post(json.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
                .build()

        return client.newCall(request).executeWithRescueJson<ArrayList<UploadedFileCheck>>(successCodes = arrayOf(HttpURLConnection.HTTP_OK))
    }
}
