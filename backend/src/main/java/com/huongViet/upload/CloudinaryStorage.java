package com.huongViet.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Lớp bọc Cloudinary — nơi ảnh món ăn được lưu trữ.
 *
 * <p>Cấu hình qua biến môi trường {@code CLOUDINARY_URL} có dạng
 * {@code cloudinary://<api_key>:<api_secret>@<cloud_name>}. Khi biến này trống,
 * {@link #isConfigured()} trả về {@code false} và ứng dụng chuyển sang lưu ảnh
 * trên ổ đĩa cục bộ.</p>
 */
@Component
public class CloudinaryStorage {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryStorage.class);

    /** Đường dẫn ảnh do Cloudinary phục vụ. */
    private static final String CLOUDINARY_URL_MARKER = "/image/upload/";

    private final Cloudinary cloudinary;
    private final String defaultFolder;

    public CloudinaryStorage(
            @Value("${app.cloudinary.url:}") String cloudinaryUrl,
            @Value("${app.cloudinary.folder:huongviet/menu}") String defaultFolder) {
        this.defaultFolder = defaultFolder;
        this.cloudinary = buildClient(cloudinaryUrl);
    }

    private static Cloudinary buildClient(String cloudinaryUrl) {
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
            logger.info("Cloudinary chưa được cấu hình (CLOUDINARY_URL trống) — sẽ lưu ảnh trên ổ đĩa cục bộ.");
            return null;
        }

        try {
            Cloudinary client = new Cloudinary(cloudinaryUrl);
            logger.info("Đã cấu hình Cloudinary cho cloud '{}'.", client.config.cloudName);
            return client;
        } catch (Exception ex) {
            logger.error("CLOUDINARY_URL không hợp lệ, sẽ lưu ảnh trên ổ đĩa cục bộ: {}", ex.getMessage());
            return null;
        }
    }

    public boolean isConfigured() {
        return cloudinary != null;
    }

    /**
     * Tải một tệp ảnh lên Cloudinary.
     *
     * @return đường dẫn công khai (https) của ảnh
     */
    public String upload(byte[] content, String filename, String folder) {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary chưa được cấu hình.");
        }
        try {
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder != null && !folder.isBlank() ? folder : defaultFolder,
                    "public_id", UUID.randomUUID().toString(),
                    "resource_type", "image",
                    "use_filename", false,
                    "overwrite", false
            );
            Map<String, Object> result = cloudinary.uploader().upload(content, options);
            String secureUrl = (String) result.get("secure_url");
            logger.info("Uploaded image to Cloudinary: {}", secureUrl);
            return secureUrl;
        } catch (IOException ex) {
            throw new IllegalStateException("Không tải được ảnh lên Cloudinary: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Cloudinary từ chối yêu cầu: " + ex.getMessage(), ex);
        }
    }

    /**
     * Xoá một ảnh trên Cloudinary bằng public_id suy ra từ đường dẫn.
     *
     * @return {@code true} nếu đã xoá
     */
    public boolean destroy(String secureUrl) {
        if (cloudinary == null) {
            return false;
        }
        String publicId = extractPublicId(secureUrl);
        if (publicId == null) {
            return false;
        }
        try {
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap());
            logger.info("Destroyed Cloudinary resource {}: {}", publicId, result.get("result"));
            return "ok".equals(result.get("result"));
        } catch (Exception ex) {
            logger.warn("Không xoá được ảnh Cloudinary {}: {}", publicId, ex.getMessage());
            return false;
        }
    }

    /**
     * Suy ra public_id từ đường dẫn ảnh Cloudinary.
     *
     * <p>Cấu trúc: {@code https://res.cloudinary.com/<cloud>/image/upload/v<version>/<public_id>.<ext>}.
     * Segment phiên bản {@code v<...>} được bỏ qua.</p>
     */
    static String extractPublicId(String secureUrl) {
        if (secureUrl == null) {
            return null;
        }
        int marker = secureUrl.indexOf(CLOUDINARY_URL_MARKER);
        if (marker < 0) {
            return null;
        }

        String remainder = secureUrl.substring(marker + CLOUDINARY_URL_MARKER.length());
        int query = remainder.indexOf('?');
        if (query >= 0) {
            remainder = remainder.substring(0, query);
        }

        String[] segments = remainder.split("/");
        int start = 0;
        if (segments.length > 1 && segments[0].matches("v\\d+")) {
            start = 1;
        }

        StringBuilder publicId = new StringBuilder();
        for (int i = start; i < segments.length; i++) {
            if (publicId.length() > 0) {
                publicId.append('/');
            }
            publicId.append(segments[i]);
        }

        String result = publicId.toString();
        if (result.isEmpty()) {
            return null;
        }
        // Bỏ đuôi tệp, nhưng giữ nguyên dấu chấm bên trong tên.
        int dot = result.lastIndexOf('.');
        return dot > 0 ? result.substring(0, dot) : result;
    }
}
