package com.huongViet.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu ảnh tải lên từ máy người dùng.
 *
 * <p>Ảnh được đưa lên <strong>Cloudinary</strong> khi biến môi trường
 * {@code CLOUDINARY_URL} đã được cấu hình. Nếu chưa cấu hình, ảnh được lưu
 * trên ổ đĩa cục bộ và phục vụ lại qua {@code /uploads/**} — nhờ vậy ứng dụng
 * vẫn chạy được ở môi trường local hoặc offline.</p>
 *
 * <p>Tên file luôn được sinh lại từ UUID nên tên file do client gửi lên không
 * thể dùng để truy cập ra ngoài thư mục lưu trữ.</p>
 */
@Service
public class ImageStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageStorageService.class);

    /** Hậu tố của ảnh do Cloudinary phục vụ. */
    private static final String CLOUDINARY_HOST_MARKER = "res.cloudinary.com";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );

    private final CloudinaryStorage cloudinaryStorage;
    private final String cloudinaryFolder;
    private final Path rootDirectory;
    private final String publicPath;
    private final long maxFileSize;

    public ImageStorageService(CloudinaryStorage cloudinaryStorage,
                               @Value("${app.cloudinary.folder:huongviet/menu}") String cloudinaryFolder,
                               @Value("${app.upload.dir:./uploads}") String uploadDir,
                               @Value("${app.upload.public-path:/uploads}") String publicPath,
                               @Value("${app.upload.max-file-size-bytes:5242880}") long maxFileSize) {
        this.cloudinaryStorage = cloudinaryStorage;
        this.cloudinaryFolder = cloudinaryFolder;
        this.rootDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicPath = publicPath.endsWith("/")
                ? publicPath.substring(0, publicPath.length() - 1)
                : publicPath;
        this.maxFileSize = maxFileSize;
    }

    /**
     * Lưu một ảnh tải lên và trả về đường dẫn công khai.
     */
    public StoredImage store(MultipartFile file, String subDirectory) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không có tệp ảnh nào được chọn.");
        }
        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Ảnh vượt quá dung lượng cho phép (" + (maxFileSize / 1024 / 1024) + " MB).");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = resolveExtension(file.getOriginalFilename());

        if (!ALLOWED_CONTENT_TYPES.contains(contentType) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Chỉ chấp nhận ảnh có định dạng JPG, PNG, GIF, WEBP hoặc SVG.");
        }

        if (cloudinaryStorage.isConfigured()) {
            try {
                byte[] content = file.getBytes();
                String url = cloudinaryStorage.upload(content, file.getOriginalFilename(), cloudinaryFolder);
                return new StoredImage(url, "cloudinary");
            } catch (IllegalStateException ex) {
                logger.error("Cloudinary không tải được ảnh, chuyển sang lưu cục bộ: {}", ex.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Không lưu được ảnh lên Cloudinary: " + ex.getMessage());
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Không đọc được tệp ảnh vừa chọn.");
            }
        }

        return new StoredImage(storeLocally(readContent(file), extension, subDirectory), "local");
    }

    /**
     * Tạo ảnh minh hoạ SVG tại chỗ — dùng cho dữ liệu mẫu khi chưa có ảnh thật.
     */
    public StoredImage storePlaceholder(String subDirectory, String fileName, String title, String backgroundColor) {
        byte[] content = buildPlaceholderSvg(title, backgroundColor).getBytes(StandardCharsets.UTF_8);

        if (cloudinaryStorage.isConfigured()) {
            try {
                String url = cloudinaryStorage.upload(content, fileName, cloudinaryFolder);
                return new StoredImage(url, "cloudinary");
            } catch (IllegalStateException ex) {
                logger.error("Cloudinary không tải được ảnh minh hoạ, chuyển sang lưu cục bộ: {}", ex.getMessage());
            }
        }

        return new StoredImage(storeLocally(content, "svg", subDirectory, fileName), "local");
    }

    /**
     * Xoá ảnh đã lưu. Bỏ qua mọi đường dẫn không thuộc kho lưu trữ của hệ thống.
     *
     * @return {@code true} nếu đã xoá một tệp
     */
    public boolean delete(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return false;
        }

        if (publicUrl.contains(CLOUDINARY_HOST_MARKER)) {
            return cloudinaryStorage.destroy(publicUrl);
        }

        if (!publicUrl.startsWith(publicPath + "/")) {
            return false;
        }

        try {
            Path target = rootDirectory
                    .resolve(publicUrl.substring(publicPath.length() + 1))
                    .normalize();
            if (!target.startsWith(rootDirectory)) {
                return false;
            }
            return Files.deleteIfExists(target);
        } catch (IOException ex) {
            logger.warn("Failed to delete stored image {}: {}", publicUrl, ex.getMessage());
            return false;
        }
    }

    private String storeLocally(byte[] content, String extension, String subDirectory) {
        return storeLocally(content, extension, subDirectory, UUID.randomUUID() + "." + extension);
    }

    private String storeLocally(byte[] content, String extension, String subDirectory, String fileName) {
        try {
            Path directory = rootDirectory.resolve(subDirectory).normalize();
            Files.createDirectories(directory);

            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(rootDirectory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn tệp không hợp lệ.");
            }

            Files.write(target, content);
            logger.info("Stored image locally: {}", target);
            return publicPath + "/" + subDirectory + "/" + fileName;
        } catch (IOException ex) {
            logger.error("Failed to store image locally: ", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không lưu được ảnh lên máy chủ. Vui lòng thử lại.");
        }
    }

    private byte[] readContent(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không đọc được tệp ảnh vừa chọn.");
        }
    }

    private String buildPlaceholderSvg(String title, String backgroundColor) {
        String safeTitle = escapeXml(title == null ? "" : title);
        // Cắt ngắn tiêu đề cho vừa khung, có dự phòng cho chuỗi rỗng.
        String shortTitle = safeTitle.length() > 28 ? safeTitle.substring(0, 28) + "…" : safeTitle;

        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600" role="img">
              <rect width="800" height="600" fill="%s"/>
              <circle cx="400" cy="240" r="110" fill="rgba(255,255,255,0.14)"/>
              <text x="400" y="252" text-anchor="middle" font-family="Georgia, serif" font-size="46"
                    fill="rgba(255,255,255,0.92)">%s</text>
              <text x="400" y="430" text-anchor="middle" font-family="sans-serif" font-size="22"
                    fill="rgba(255,255,255,0.65)" letter-spacing="4">HƯƠNG VIỆT</text>
            </svg>
            """.formatted(backgroundColor, shortTitle);
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Danh sách định dạng ảnh được phép tải lên. */
    public static List<String> allowedExtensions() {
        return List.copyOf(ALLOWED_EXTENSIONS);
    }
}
