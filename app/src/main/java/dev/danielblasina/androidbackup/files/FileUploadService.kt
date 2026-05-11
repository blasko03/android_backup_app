package dev.danielblasina.androidbackup.files

import android.net.Uri
import com.auth0.android.jwt.JWT
import dev.danielblasina.androidbackup.utils.JsonParse
import dev.danielblasina.androidbackup.utils.executeWithRescue
import dev.danielblasina.androidbackup.utils.executeWithRescueJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.util.encoders.Hex
import java.net.HttpURLConnection
import java.nio.file.Path
import java.util.UUID

const val MEDIA_TYPE_JSON = "application/json"

data class UploadedFile(
    val name: String,
    val chunks: List<String> = listOf(),
    val hash: String,
)

data class UploadedFileCheck(
    val name: String,
    val present: Boolean,
)

data class JwtAuth(
    val uuid: UUID,
    val password: String,
)
class FileUploadService(private val auth: FileUploadAuth) {

    companion object {
        private var token: String = ""
    }

    val client = OkHttpClient()

    fun chunkUpload(filename: String, chunk: ByteArray): Result<String> {
        val requestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, chunk.toRequestBody())
                .build()
        Uri.Builder()
        val request: Request = requestBuilder()
            .url(auth.address.resolve("chunk").toURL())
            .post(requestBody)
            .build()
        return client.newCall(request).executeWithRescue()
    }

    fun chunkPresent(filename: String): Result<String> {
        val request: Request = requestBuilder()
            .url(auth.address.resolve("chunk/").resolve(filename).toURL())
            .build()
        return client.newCall(request).executeWithRescue(successCodes = arrayOf(HttpURLConnection.HTTP_OK))
    }

    fun fileUpload(
        filename: Path,
        checksum: ByteArray,
        chunks: ArrayList<ByteArray>,
    ): Result<String> {
        val hash = Hex.toHexString(checksum)
        val chunksEncoded = chunks.map { c -> Hex.toHexString(c) }.toList();
        val json = JsonParse.objectToJsonString(UploadedFile(filename.toString(), chunksEncoded, hash))
        val request: Request = requestBuilder()
            .url(auth.address.resolve("file").toURL())
            .post(json.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
            .build()

        return client.newCall(request).executeWithRescue()
    }

    fun filesPresent(files: List<UploadedFile>): Result<List<UploadedFileCheck>> {
        val json = JsonParse.objectToJsonString(files)
        val request: Request = requestBuilder()
            .url(auth.address.resolve("has_files").toURL())
            .post(json.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
            .build()

        return client.newCall(request).executeWithRescueJson<ArrayList<UploadedFileCheck>>(successCodes = arrayOf(HttpURLConnection.HTTP_OK))
    }

    fun requestBuilder(): Request.Builder = Request
        .Builder()
        .addHeader("Authorization", "Bearer ${getToken()}")

    fun getToken(): String {
        if (token.isNotEmpty() && !JWT(token).isExpired(10)) {
            return token
        }
        val authJson = JsonParse.objectToJsonString(
            JwtAuth(
                uuid = auth.uuid,
                password = auth.password,
            ),
        )
        val authReq = Request.Builder()
            .url(auth.address.resolve("login").toURL())
            .post(authJson.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
            .build()
        token = client.newCall(authReq).executeWithRescue().getOrThrow()
        return token
    }
}
