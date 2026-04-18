package com.recipeplatform.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingService {

    private static final int THUMB_WIDTH  = 200;
    private static final int THUMB_HEIGHT = 200;
    private static final int MEDIUM_WIDTH  = 800;
    private static final int MEDIUM_HEIGHT = 600;
    private static final float JPEG_QUALITY = 0.85f;

    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.cdn-base-url}")
    private String cdnBaseUrl;

    public record ProcessedImage(String originalUrl, String thumbUrl, String mediumUrl) {}

    /**
     * Downloads the raw image from S3, produces two resized variants,
     * uploads them back, and returns their CDN URLs.
     */
    public ProcessedImage process(UUID imageId, String sourceKey) throws IOException {
        log.info("Processing image {} from key {}", imageId, sourceKey);

        // 1. Download original
        byte[] originalBytes = downloadFromS3(sourceKey);

        // 2. Resize to thumbnail (crop to square)
        byte[] thumbBytes = resizeImage(originalBytes, THUMB_WIDTH, THUMB_HEIGHT, true);

        // 3. Resize to medium (fit within bounds, no crop)
        byte[] mediumBytes = resizeImage(originalBytes, MEDIUM_WIDTH, MEDIUM_HEIGHT, false);

        // 4. Derive output keys
        String baseKey    = sourceKey.replace("uploads/", "processed/");
        String thumbKey   = baseKey + "_thumb.jpg";
        String mediumKey  = baseKey + "_medium.jpg";

        // 5. Upload original (copy to processed prefix for CDN)
        String originalKey = baseKey + "_original.jpg";
        uploadToS3(originalKey, originalBytes, "image/jpeg");
        uploadToS3(thumbKey,    thumbBytes,    "image/jpeg");
        uploadToS3(mediumKey,   mediumBytes,   "image/jpeg");

        return new ProcessedImage(
                cdnUrl(originalKey),
                cdnUrl(thumbKey),
                cdnUrl(mediumKey)
        );
    }

    private byte[] downloadFromS3(String key) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            return response.readAllBytes();
        }
    }

    private byte[] resizeImage(byte[] input, int width, int height, boolean crop) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        var builder = Thumbnails.of(new ByteArrayInputStream(input))
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY);

        if (crop) {
            builder.size(width, height).crop(net.coobird.thumbnailator.geometry.Positions.CENTER);
        } else {
            builder.size(width, height).keepAspectRatio(true);
        }

        builder.toOutputStream(output);
        return output.toByteArray();
    }

    private void uploadToS3(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
        log.debug("Uploaded {} bytes to s3://{}/{}", data.length, bucket, key);
    }

    private String cdnUrl(String key) {
        return cdnBaseUrl.stripTrailing() + "/" + key;
    }
}
