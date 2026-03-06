package no.josefus.abuhint.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

@Service
class S3FileLinkService(
    @Value("\${s3.bucket:}") private val bucket: String,
    @Value("\${s3.region:}") private val region: String,
    @Value("\${s3.key-prefix:pptx/}") private val keyPrefix: String,
    @Value("\${s3.presign-ttl-minutes:60}") private val presignTtlMinutes: Long
) {

    private val logger = LoggerFactory.getLogger(S3FileLinkService::class.java)

    fun isConfigured(): Boolean {
        return bucket.isNotBlank() && region.isNotBlank()
    }

    private val s3Client: S3Client by lazy {
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }

    private val presigner: S3Presigner by lazy {
        S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }

    fun uploadAndPresign(path: Path, fileName: String, contentType: String? = null): String? {
        if (!isConfigured()) {
            logger.warn("S3 is not configured. Missing bucket or region.")
            return null
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            logger.warn("File not found for S3 upload: {}", path)
            return null
        }
        if (Files.size(path) <= 0L) {
            logger.warn("File is empty for S3 upload: {}", path)
            return null
        }
        val normalizedPrefix = if (keyPrefix.isBlank()) "" else keyPrefix.trim().trim('/').plus("/")
        val key = normalizedPrefix + UUID.randomUUID() + "-" + fileName
        val putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .apply {
                if (!contentType.isNullOrBlank()) {
                    contentType(contentType)
                }
            }
            .build()
        return try {
            s3Client.putObject(putRequest, RequestBody.fromFile(path))
            val getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignTtlMinutes))
                .getObjectRequest(getRequest)
                .build()
            presigner.presignGetObject(presignRequest).url().toString()
        } catch (e: Exception) {
            logger.error("Failed to upload or presign S3 object: ${e.message}", e)
            null
        }
    }
}
