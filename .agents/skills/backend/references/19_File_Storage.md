# Domain 19: File Storage & Upload

> **Số practices:** 10 | 🔴 3 | 🟠 4 | 🟡 3
> **Trọng số:** ×1

---

## 19.01 - File type validation (MIME type + magic bytes, không chỉ extension)

### Metadata
- **Mức độ:** 🔴 BẮT BUỘC
- **Lý do:** Security - ngăn chặn upload file độc hại, bypass extension
- **Impact:** HIGH - RCE, malware upload, XSS
- **Tags:** `security`, `validation`, `upload`

### Tại sao?

**Vấn đề:**
- Extension có thể fake dễ dàng (`virus.exe` → `virus.jpg`)
- MIME type từ client có thể giả mạo
- Magic bytes (file signature) là cách đáng tin cậy nhất

**Hậu quả khi vi phạm:**
- Upload shell script giả dạng image
- RCE nếu file được execute
- XSS qua SVG độc hại
- Malware distribution

### ✅ Cách đúng

```java
// ✅ GOOD: Validate cả MIME type + magic bytes
@Service
public class FileValidationService {

  private static final Map<String, byte[]> ALLOWED_SIGNATURES = Map.of(
    "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
    "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
    "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}
  );

  private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
    "image/jpeg", "image/png", "application/pdf"
  );

  public void validateFile(MultipartFile file) {
    // 1. Check extension
    String filename = file.getOriginalFilename();
    if (!hasAllowedExtension(filename)) {
      throw new InvalidFileException("Extension không được phép");
    }

    // 2. Check MIME type
    String contentType = file.getContentType();
    if (!ALLOWED_MIME_TYPES.contains(contentType)) {
      throw new InvalidFileException("MIME type không được phép: " + contentType);
    }

    // 3. Check magic bytes (file signature)
    try {
      byte[] fileBytes = file.getBytes();
      if (!hasValidMagicBytes(fileBytes, contentType)) {
        throw new InvalidFileException("File signature không khớp với MIME type");
      }
    } catch (IOException e) {
      throw new InvalidFileException("Không đọc được file content");
    }
  }

  private boolean hasValidMagicBytes(byte[] fileBytes, String mimeType) {
    byte[] expectedSignature = ALLOWED_SIGNATURES.get(mimeType);
    if (expectedSignature == null) return false;

    if (fileBytes.length < expectedSignature.length) return false;

    for (int i = 0; i < expectedSignature.length; i++) {
      if (fileBytes[i] != expectedSignature[i]) {
        return false;
      }
    }
    return true;
  }

  private boolean hasAllowedExtension(String filename) {
    if (filename == null) return false;
    String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    return Set.of("jpg", "jpeg", "png", "pdf").contains(ext);
  }
}

// ✅ GOOD: Sử dụng Apache Tika để detect MIME type chính xác
@Service
public class TikaFileValidator {

  private final Tika tika = new Tika();

  public void validateFile(MultipartFile file) throws IOException {
    // Detect MIME type từ file content
    String detectedMimeType = tika.detect(file.getBytes());

    // So sánh với MIME type từ client
    String declaredMimeType = file.getContentType();

    if (!detectedMimeType.equals(declaredMimeType)) {
      throw new InvalidFileException(
        "MIME type không khớp. Declared: %s, Detected: %s"
          .formatted(declaredMimeType, detectedMimeType)
      );
    }

    // Kiểm tra whitelist
    if (!Set.of("image/jpeg", "image/png", "application/pdf").contains(detectedMimeType)) {
      throw new InvalidFileException("File type không được phép: " + detectedMimeType);
    }
  }
}

// ✅ GOOD: Custom validator annotation
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileTypeValidator.class)
public @interface ValidFileType {
  String message() default "File type không hợp lệ";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
  String[] allowed() default {"image/jpeg", "image/png"};
}

public class FileTypeValidator implements ConstraintValidator<ValidFileType, MultipartFile> {

  private Set<String> allowedTypes;
  private final Tika tika = new Tika();

  @Override
  public void initialize(ValidFileType annotation) {
    this.allowedTypes = Set.of(annotation.allowed());
  }

  @Override
  public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
    if (file == null || file.isEmpty()) return true;

    try {
      String detectedType = tika.detect(file.getBytes());
      return allowedTypes.contains(detectedType);
    } catch (IOException e) {
      return false;
    }
  }
}

// Controller usage
@PostMapping("/upload")
public ResponseEntity<?> upload(
  @ValidFileType(allowed = {"image/jpeg", "image/png"})
  @RequestParam("file") MultipartFile file
) {
  // File đã được validate tự động
  return ResponseEntity.ok().build();
}
```

### ❌ Cách sai

```java
// ❌ BAD: Chỉ check extension
public void validateFile(MultipartFile file) {
  String filename = file.getOriginalFilename();
  if (!filename.endsWith(".jpg") && !filename.endsWith(".png")) {
    throw new InvalidFileException("Chỉ chấp nhận JPG/PNG");
  }
  // Attacker upload virus.exe.jpg → bypass!
}

// ❌ BAD: Chỉ tin MIME type từ client
public void validateFile(MultipartFile file) {
  String contentType = file.getContentType();
  if (!"image/jpeg".equals(contentType)) {
    throw new InvalidFileException("Chỉ chấp nhận JPEG");
  }
  // Attacker giả mạo Content-Type header → bypass!
}

// ❌ BAD: Không validate gì cả
@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
  fileService.save(file); // Lưu bất kỳ file gì!
  return ResponseEntity.ok().build();
}
```

### Phát hiện

```bash
# Tìm upload endpoint không có validation
rg -A 5 'MultipartFile' --type java | grep -v 'validate'

# Tìm code chỉ check extension
rg 'endsWith\("\.(jpg|png|pdf)' --type java

# Tìm code chỉ check contentType mà không check magic bytes
rg 'getContentType\(\)' --type java | grep -v 'magic\|signature\|Tika'
```

### Checklist

- [ ] Validate extension (whitelist)
- [ ] Validate MIME type từ client
- [ ] Validate magic bytes (file signature)
- [ ] Sử dụng Apache Tika hoặc tương đương
- [ ] Reject nếu MIME type không khớp với magic bytes
- [ ] Có unit test cho bypass attempts

---

## 19.02 - Max file size limit (spring.servlet.multipart.max-file-size)

### Metadata
- **Mức độ:** 🔴 BẮT BUỘC
- **Lý do:** DoS prevention, resource management
- **Impact:** HIGH - OOM, disk full, service down
- **Tags:** `security`, `resource-management`, `config`

### Tại sao?

**Vấn đề:**
- Upload file quá lớn → OOM
- Lấp đầy disk space
- DoS attack bằng concurrent large uploads

**Hậu quả khi vi phạm:**
- Application crash do OOM
- Disk full → service không hoạt động
- Network bandwidth bị chiếm dụng

### ✅ Cách đúng

```yaml
# ✅ GOOD: application.yml - Set giới hạn rõ ràng
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 10MB        # File đơn lẻ tối đa 10MB
      max-request-size: 50MB     # Toàn bộ request tối đa 50MB (nhiều file)
      file-size-threshold: 2MB   # > 2MB sẽ ghi ra disk thay vì memory
      location: /tmp/uploads     # Thư mục tạm
```

```java
// ✅ GOOD: Custom exception handler cho size limit
@ControllerAdvice
public class FileUploadExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<?> handleMaxSizeException(MaxUploadSizeExceededException ex) {
    return ResponseEntity
      .status(HttpStatus.PAYLOAD_TOO_LARGE)
      .body(Map.of(
        "error", "File quá lớn",
        "message", "Kích thước file tối đa: 10MB",
        "timestamp", Instant.now()
      ));
  }

  @ExceptionHandler(SizeLimitExceededException.class)
  public ResponseEntity<?> handleSizeLimitException(SizeLimitExceededException ex) {
    return ResponseEntity
      .status(HttpStatus.PAYLOAD_TOO_LARGE)
      .body(Map.of(
        "error", "Request quá lớn",
        "message", "Tổng kích thước request tối đa: 50MB"
      ));
  }
}

// ✅ GOOD: Validate size trong business logic
@Service
public class FileUploadService {

  @Value("${app.upload.max-size:10485760}") // 10MB default
  private long maxFileSize;

  public void validateFileSize(MultipartFile file) {
    if (file.getSize() > maxFileSize) {
      throw new FileSizeExceededException(
        "File %s vượt quá giới hạn %d bytes"
          .formatted(file.getOriginalFilename(), maxFileSize)
      );
    }
  }

  public void upload(MultipartFile file) {
    validateFileSize(file);
    // Process upload...
  }
}

// ✅ GOOD: Per-endpoint size limit
@PostMapping("/upload/avatar")
@RequestSizeLimit(maxSize = 2 * 1024 * 1024) // 2MB for avatar
public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
  // Custom annotation để enforce limit
  return ResponseEntity.ok().build();
}

// Custom annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestSizeLimit {
  long maxSize();
}

@Aspect
@Component
public class RequestSizeLimitAspect {

  @Before("@annotation(limit)")
  public void checkSize(JoinPoint joinPoint, RequestSizeLimit limit) {
    Object[] args = joinPoint.getArgs();
    for (Object arg : args) {
      if (arg instanceof MultipartFile file) {
        if (file.getSize() > limit.maxSize()) {
          throw new FileSizeExceededException(
            "File vượt quá %d bytes".formatted(limit.maxSize())
          );
        }
      }
    }
  }
}
```

### ❌ Cách sai

```yaml
# ❌ BAD: Không set limit (default là 1MB nhưng nên explicit)
spring:
  servlet:
    multipart:
      enabled: true
      # Không set max-file-size và max-request-size

# ❌ BAD: Limit quá lớn
spring:
  servlet:
    multipart:
      max-file-size: 1GB  # Quá lớn, dễ bị DoS
      max-request-size: 5GB
```

```java
// ❌ BAD: Không validate size trong code
@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
  fileService.save(file); // Tin tưởng hoàn toàn vào config
  return ResponseEntity.ok().build();
}

// ❌ BAD: Load toàn bộ file vào memory
public void processFile(MultipartFile file) {
  byte[] bytes = file.getBytes(); // OOM nếu file lớn
  // Process...
}
```

### Phát hiện

```bash
# Tìm config thiếu max-file-size
rg 'spring.servlet.multipart' config/ | grep -v 'max-file-size'

# Tìm code load file vào memory
rg 'getBytes\(\)' --type java

# Tìm upload endpoint không có size validation
rg '@PostMapping.*upload' -A 10 --type java | grep -v 'validateSize\|maxSize'
```

### Checklist

- [ ] Set `spring.servlet.multipart.max-file-size`
- [ ] Set `spring.servlet.multipart.max-request-size`
- [ ] Set `file-size-threshold` để tránh OOM
- [ ] Custom exception handler cho `MaxUploadSizeExceededException`
- [ ] Validate size trong business logic
- [ ] Document giới hạn cho frontend/API consumers
- [ ] Monitor disk space usage

---

## 19.03 - Virus scan trước khi lưu file

### Metadata
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Lý do:** Security - ngăn chặn malware distribution
- **Impact:** MEDIUM - malware spread, data breach
- **Tags:** `security`, `malware`, `scanning`

### Tại sao?

**Vấn đề:**
- User có thể upload file chứa virus/malware
- File bị nhiễm có thể lây lan khi download
- Compliance requirement (GDPR, HIPAA, PCI-DSS)

**Hậu quả khi vi phạm:**
- Malware distribution platform
- Data breach, ransomware
- Mất uy tín, kiện tụng

### ✅ Cách đúng

```java
// ✅ GOOD: ClamAV integration (open-source antivirus)
@Service
public class VirusScanService {

  private final ClamAVClient clamAVClient;

  public VirusScanService(
    @Value("${clamav.host:localhost}") String host,
    @Value("${clamav.port:3310}") int port
  ) {
    this.clamAVClient = new ClamAVClient(host, port);
  }

  public void scanFile(MultipartFile file) throws IOException {
    byte[] bytes = file.getBytes();
    byte[] reply = clamAVClient.scan(bytes);

    if (!ClamAVClient.isCleanReply(reply)) {
      String virusName = new String(reply).trim();
      throw new VirusDetectedException(
        "Phát hiện virus trong file %s: %s"
          .formatted(file.getOriginalFilename(), virusName)
      );
    }
  }
}

// ✅ GOOD: Async virus scan với callback
@Service
public class AsyncVirusScanService {

  private final VirusScanService virusScanService;
  private final FileStorageService fileStorageService;
  private final ApplicationEventPublisher eventPublisher;

  @Async
  public CompletableFuture<ScanResult> scanFileAsync(String fileId, byte[] content) {
    try {
      // Scan
      virusScanService.scan(content);

      // Mark file as safe
      fileStorageService.markAsSafe(fileId);

      // Publish event
      eventPublisher.publishEvent(new FileScanCompletedEvent(fileId, true));

      return CompletableFuture.completedFuture(ScanResult.CLEAN);
    } catch (VirusDetectedException e) {
      // Delete infected file
      fileStorageService.delete(fileId);

      // Publish event
      eventPublisher.publishEvent(new FileScanCompletedEvent(fileId, false, e.getVirusName()));

      return CompletableFuture.completedFuture(ScanResult.INFECTED);
    }
  }
}

// ✅ GOOD: File quarantine workflow
@Service
public class FileUploadService {

  private final FileStorageService storageService;
  private final VirusScanService scanService;

  @Transactional
  public FileMetadata uploadFile(MultipartFile file) {
    // 1. Validate type, size
    validateFile(file);

    // 2. Lưu tạm vào quarantine zone
    String quarantineId = storageService.saveToQuarantine(file);

    // 3. Scan virus
    try {
      scanService.scanFile(file);
    } catch (VirusDetectedException e) {
      storageService.deleteFromQuarantine(quarantineId);
      throw e;
    }

    // 4. Move từ quarantine sang production storage
    String finalId = storageService.moveToProduction(quarantineId);

    // 5. Save metadata
    return FileMetadata.builder()
      .id(finalId)
      .filename(file.getOriginalFilename())
      .status(FileStatus.SAFE)
      .scannedAt(Instant.now())
      .build();
  }
}

// ✅ GOOD: Docker Compose setup cho ClamAV
/*
version: '3.8'
services:
  clamav:
    image: clamav/clamav:latest
    ports:
      - "3310:3310"
    volumes:
      - clamav-data:/var/lib/clamav
    environment:
      - CLAMAV_NO_FRESHCLAM=false  # Auto update virus definitions
volumes:
  clamav-data:
*/

// ✅ GOOD: VirusTotal API integration (cloud-based)
@Service
public class VirusTotalScanService {

  private final WebClient webClient;

  @Value("${virustotal.api-key}")
  private String apiKey;

  public void scanFile(MultipartFile file) throws IOException {
    // 1. Upload file
    String analysisId = uploadFile(file);

    // 2. Poll for result
    ScanResult result = pollScanResult(analysisId);

    // 3. Check malicious count
    if (result.getMaliciousCount() > 0) {
      throw new VirusDetectedException(
        "File bị đánh dấu malicious bởi %d/%d antivirus engines"
          .formatted(result.getMaliciousCount(), result.getTotalEngines())
      );
    }
  }

  private String uploadFile(MultipartFile file) throws IOException {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", file.getResource());

    var response = webClient.post()
      .uri("https://www.virustotal.com/api/v3/files")
      .header("x-apikey", apiKey)
      .bodyValue(builder.build())
      .retrieve()
      .bodyToMono(JsonNode.class)
      .block();

    return response.get("data").get("id").asText();
  }

  private ScanResult pollScanResult(String analysisId) {
    // Poll every 10s, max 5 minutes
    for (int i = 0; i < 30; i++) {
      var response = webClient.get()
        .uri("https://www.virustotal.com/api/v3/analyses/{id}", analysisId)
        .header("x-apikey", apiKey)
        .retrieve()
        .bodyToMono(JsonNode.class)
        .block();

      String status = response.get("data").get("attributes").get("status").asText();
      if ("completed".equals(status)) {
        var stats = response.get("data").get("attributes").get("stats");
        return new ScanResult(
          stats.get("malicious").asInt(),
          stats.get("malicious").asInt() + stats.get("undetected").asInt()
        );
      }

      try {
        Thread.sleep(10_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Scan interrupted");
      }
    }

    throw new RuntimeException("Scan timeout");
  }
}
```

### ❌ Cách sai

```java
// ❌ BAD: Không scan virus
@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
  String fileId = storageService.save(file); // Lưu trực tiếp
  return ResponseEntity.ok(Map.of("fileId", fileId));
}

// ❌ BAD: Scan sau khi đã public file
public String uploadFile(MultipartFile file) {
  String fileId = storageService.save(file);
  String publicUrl = storageService.getPublicUrl(fileId);

  // Scan async NHƯNG file đã public!
  virusScanService.scanAsync(fileId);

  return publicUrl; // File có thể bị download trước khi scan xong
}

// ❌ BAD: Chỉ dựa vào file extension để quyết định scan
public void uploadFile(MultipartFile file) {
  if (file.getOriginalFilename().endsWith(".exe")) {
    virusScanService.scan(file); // Chỉ scan .exe
  }
  // Virus có thể ẩn trong .jpg, .pdf, .docx, v.v.
  storageService.save(file);
}
```

### Phát hiện

```bash
# Tìm upload service không có virus scan
rg 'class.*UploadService' -A 30 --type java | grep -v 'scan\|clamav\|virustotal'

# Tìm code lưu file trực tiếp mà không scan
rg 'storageService.save' --type java | grep -v 'scan'

# Check ClamAV config
rg 'clamav' config/
```

### Checklist

- [ ] Có virus scanning service (ClamAV, VirusTotal, hoặc tương đương)
- [ ] Scan TRƯỚC KHI file được public
- [ ] Quarantine workflow (tạm lưu → scan → move/delete)
- [ ] Async scan cho file lớn
- [ ] Auto-update virus definitions
- [ ] Alert khi phát hiện virus
- [ ] Log scan results
- [ ] Handle scan timeout/failure

---

## 19.04 - Unique filename generation (UUID) tránh overwrite

### Metadata
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Lý do:** Data integrity, prevent overwrite, security
- **Impact:** MEDIUM - data loss, unauthorized access
- **Tags:** `security`, `data-integrity`, `naming`

### Tại sao?

**Vấn đề:**
- Filename trùng → overwrite file cũ
- Predictable filename → enumeration attack
- Path traversal nếu dùng original filename

**Hậu quả khi vi phạm:**
- Mất dữ liệu do overwrite
- Unauthorized access qua file enumeration
- Path traversal attack

### ✅ Cách đúng

```java
// ✅ GOOD: UUID-based filename generation
@Service
public class FileStorageService {

  @Value("${app.upload.dir:/var/app/uploads}")
  private String uploadDir;

  public FileMetadata store(MultipartFile file) throws IOException {
    // 1. Generate unique filename
    String originalFilename = file.getOriginalFilename();
    String extension = getExtension(originalFilename);
    String uniqueFilename = UUID.randomUUID() + extension;

    // 2. Create subdirectory by date (avoid too many files in one dir)
    LocalDate today = LocalDate.now();
    Path dateDir = Paths.get(uploadDir,
      String.valueOf(today.getYear()),
      String.format("%02d", today.getMonthValue()),
      String.format("%02d", today.getDayOfMonth())
    );
    Files.createDirectories(dateDir);

    // 3. Save file
    Path targetPath = dateDir.resolve(uniqueFilename);
    Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

    // 4. Return metadata
    return FileMetadata.builder()
      .id(UUID.randomUUID().toString())
      .storedFilename(uniqueFilename)
      .originalFilename(sanitizeFilename(originalFilename))
      .path(targetPath.toString())
      .size(file.getSize())
      .contentType(file.getContentType())
      .uploadedAt(Instant.now())
      .build();
  }

  private String getExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.'));
  }

  private String sanitizeFilename(String filename) {
    if (filename == null) return "unknown";

    // Remove path traversal attempts
    String sanitized = filename.replaceAll("\\.\\./", "");

    // Remove special characters
    sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");

    // Limit length
    if (sanitized.length() > 255) {
      sanitized = sanitized.substring(0, 255);
    }

    return sanitized;
  }
}

// ✅ GOOD: ULID (sortable UUID alternative)
@Service
public class ULIDFilenameGenerator {

  private final UlidCreator ulidCreator = UlidCreator.getMonotonicCreator();

  public String generateFilename(String originalFilename) {
    String extension = getExtension(originalFilename);
    String ulid = ulidCreator.create().toString().toLowerCase();
    return ulid + extension;
  }

  // ULID benefits:
  // - 128-bit compatibility với UUID
  // - Sortable by creation time
  // - Case-insensitive (base32)
  // - No special characters
}

// ✅ GOOD: Hash-based filename (content-addressable)
@Service
public class HashBasedStorage {

  public FileMetadata store(MultipartFile file) throws IOException {
    byte[] content = file.getBytes();

    // 1. Hash file content (SHA-256)
    String hash = DigestUtils.sha256Hex(content);

    // 2. Check if file already exists (deduplication)
    Optional<FileMetadata> existing = fileRepository.findByHash(hash);
    if (existing.isPresent()) {
      return existing.get(); // Reuse existing file
    }

    // 3. Store with hash as filename
    String extension = getExtension(file.getOriginalFilename());
    String filename = hash + extension;

    // 4. Create subdirectory from hash prefix (avoid too many files)
    // /uploads/ab/cd/abcdef123456...
    Path subdir = Paths.get(uploadDir, hash.substring(0, 2), hash.substring(2, 4));
    Files.createDirectories(subdir);

    Path targetPath = subdir.resolve(filename);
    Files.write(targetPath, content);

    return FileMetadata.builder()
      .id(UUID.randomUUID().toString())
      .hash(hash)
      .storedFilename(filename)
      .originalFilename(file.getOriginalFilename())
      .path(targetPath.toString())
      .build();
  }
}

// ✅ GOOD: Entity với filename mapping
@Entity
@Table(name = "files")
public class FileMetadata {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id; // Public ID cho client

  @Column(nullable = false, unique = true)
  private String storedFilename; // UUID + extension (internal)

  @Column(nullable = false)
  private String originalFilename; // User's filename (display only)

  @Column(nullable = false)
  private String path; // Full path trên disk

  @Column(length = 64)
  private String hash; // SHA-256 hash (for deduplication)

  private Long size;
  private String contentType;

  @Column(nullable = false)
  private Instant uploadedAt;

  @Column(nullable = false)
  private String uploadedBy; // User ID
}
```

### ❌ Cách sai

```java
// ❌ BAD: Dùng original filename trực tiếp
public void store(MultipartFile file) throws IOException {
  String filename = file.getOriginalFilename(); // Nguy hiểm!
  Path path = Paths.get(uploadDir, filename);
  Files.copy(file.getInputStream(), path);
  // Vấn đề:
  // 1. Overwrite nếu trùng tên
  // 2. Path traversal: ../../etc/passwd
  // 3. Predictable filename
}

// ❌ BAD: Dùng timestamp (có thể trùng)
public String generateFilename(String originalFilename) {
  String extension = getExtension(originalFilename);
  long timestamp = System.currentTimeMillis();
  return timestamp + extension;
  // 2 requests cùng millisecond → overwrite!
}

// ❌ BAD: Dùng sequential ID
private AtomicLong counter = new AtomicLong(0);

public String generateFilename(String originalFilename) {
  long id = counter.incrementAndGet();
  return id + getExtension(originalFilename);
  // Enumeration attack: file/1, file/2, file/3, ...
}

// ❌ BAD: Sanitize không đúng cách
public String sanitizeFilename(String filename) {
  return filename.replace("../", "");
  // Bypass: ....//
}
```

### Phát hiện

```bash
# Tìm code dùng original filename trực tiếp
rg 'getOriginalFilename\(\)' --type java | grep -v 'sanitize\|UUID\|hash'

# Tìm code dùng timestamp làm filename
rg 'currentTimeMillis\(\).*filename' --type java

# Tìm code không có UUID/hash generation
rg 'class.*Storage' -A 30 --type java | grep -v 'UUID\|hash\|ulid'
```

### Checklist

- [ ] Dùng UUID/ULID/hash cho stored filename
- [ ] Không dùng original filename làm stored filename
- [ ] Sanitize original filename nếu cần display
- [ ] Prevent path traversal (không dùng user input trong path)
- [ ] Subdirectory structure (tránh quá nhiều file trong 1 thư mục)
- [ ] Lưu mapping giữa public ID và stored filename trong DB
- [ ] Consider deduplication (hash-based storage)

---

## 19.05 - Lưu file ngoài webroot (không serve trực tiếp)

### Metadata
- **Mức độ:** 🔴 BẮT BUỘC
- **Lý do:** Security - ngăn chặn RCE, information disclosure
- **Impact:** HIGH - RCE, data breach, directory traversal
- **Tags:** `security`, `access-control`, `storage`

### Tại sao?

**Vấn đề:**
- File trong webroot có thể execute trực tiếp (RCE)
- Directory listing lộ cấu trúc file
- Bypass authorization checks

**Hậu quả khi vi phạm:**
- RCE nếu upload shell script
- Unauthorized access đến file nhạy cảm
- Information disclosure

### ✅ Cách đúng

```yaml
# ✅ GOOD: application.yml - Upload dir ngoài webroot
app:
  upload:
    dir: /var/app-data/uploads  # Ngoài /var/www hoặc /opt/app
    max-size: 10MB
```

```java
// ✅ GOOD: File serve qua controller với authorization
@RestController
@RequestMapping("/api/files")
public class FileDownloadController {

  private final FileStorageService storageService;
  private final FileAuthorizationService authService;

  @GetMapping("/{fileId}")
  public ResponseEntity<Resource> downloadFile(
    @PathVariable String fileId,
    Authentication authentication
  ) {
    // 1. Load file metadata
    FileMetadata metadata = storageService.getMetadata(fileId);

    // 2. Check authorization
    if (!authService.canAccess(authentication, metadata)) {
      throw new AccessDeniedException("Không có quyền truy cập file này");
    }

    // 3. Load file từ storage (ngoài webroot)
    Resource resource = storageService.loadAsResource(fileId);

    // 4. Return với proper headers
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(metadata.getContentType()))
      .header(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
      .body(resource);
  }
}

// ✅ GOOD: FileStorageService với absolute path ngoài webroot
@Service
public class FileStorageService {

  private final Path uploadLocation;

  public FileStorageService(@Value("${app.upload.dir}") String uploadDir) {
    this.uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

    // Validate upload dir không nằm trong webroot
    Path webRoot = Paths.get("src/main/resources/static").toAbsolutePath();
    if (uploadLocation.startsWith(webRoot)) {
      throw new IllegalStateException(
        "Upload dir KHÔNG ĐƯỢC nằm trong webroot: " + uploadLocation
      );
    }

    try {
      Files.createDirectories(uploadLocation);
    } catch (IOException e) {
      throw new RuntimeException("Không tạo được upload directory", e);
    }
  }

  public Resource loadAsResource(String fileId) {
    FileMetadata metadata = fileRepository.findById(fileId)
      .orElseThrow(() -> new FileNotFoundException("File không tồn tại: " + fileId));

    Path filePath = Paths.get(metadata.getPath()).normalize();

    // CRITICAL: Validate path không escape khỏi upload dir (path traversal prevention)
    if (!filePath.startsWith(uploadLocation)) {
      throw new SecurityException("Phát hiện path traversal attempt: " + filePath);
    }

    try {
      Resource resource = new UrlResource(filePath.toUri());
      if (resource.exists() && resource.isReadable()) {
        return resource;
      } else {
        throw new FileNotFoundException("File không đọc được: " + fileId);
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException("Invalid file path", e);
    }
  }
}

// ✅ GOOD: Inline display với Content-Security-Policy
@GetMapping("/{fileId}/inline")
public ResponseEntity<Resource> viewFile(@PathVariable String fileId) {
  FileMetadata metadata = storageService.getMetadata(fileId);
  Resource resource = storageService.loadAsResource(fileId);

  return ResponseEntity.ok()
    .contentType(MediaType.parseMediaType(metadata.getContentType()))
    // CSP ngăn chặn XSS nếu file là HTML
    .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline';")
    // X-Content-Type-Options ngăn MIME sniffing
    .header("X-Content-Type-Options", "nosniff")
    // Inline display
    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + metadata.getOriginalFilename() + "\"")
    .body(resource);
}

// ✅ GOOD: Security config - Disable directory listing
@Configuration
public class WebSecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      // ...
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/uploads/**").denyAll() // Block direct access
        .requestMatchers("/api/files/**").authenticated()
        .anyRequest().permitAll()
      );

    return http.build();
  }
}

// ✅ GOOD: Docker volume mount (file ngoài container filesystem)
/*
version: '3.8'
services:
  app:
    image: myapp:latest
    volumes:
      - /host/data/uploads:/var/app-data/uploads:rw  # External mount
    environment:
      - APP_UPLOAD_DIR=/var/app-data/uploads
*/
```

### ❌ Cách sai

```yaml
# ❌ BAD: Upload dir trong webroot
app:
  upload:
    dir: src/main/resources/static/uploads  # Trong webroot!
    # File có thể access trực tiếp: http://localhost:8080/uploads/file.jsp
```

```java
// ❌ BAD: Serve file trực tiếp từ static resources
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**")
      .addResourceLocations("file:/var/uploads/");
    // Không có authorization check!
    // Directory listing có thể bật!
  }
}

// ❌ BAD: Không validate path (path traversal)
@GetMapping("/download")
public ResponseEntity<Resource> download(@RequestParam String filename) {
  Path path = Paths.get(uploadDir, filename); // User input trực tiếp!
  // Attacker: ?filename=../../../../etc/passwd
  Resource resource = new UrlResource(path.toUri());
  return ResponseEntity.ok().body(resource);
}

// ❌ BAD: Lưu file upload trong classpath
public void store(MultipartFile file) throws IOException {
  Path path = Paths.get("src/main/resources/uploads", file.getOriginalFilename());
  Files.copy(file.getInputStream(), path);
  // File trong classpath có thể được load bởi ClassLoader → RCE risk
}
```

### Phát hiện

```bash
# Tìm upload dir trong static resources
rg 'resources/static' config/ --type yaml

# Tìm ResourceHandler serve file upload
rg 'addResourceHandlers' --type java -A 5 | grep uploads

# Tìm code không validate path traversal
rg 'Paths.get.*filename' --type java | grep -v 'normalize\|startsWith'

# Tìm file access không có authorization
rg '@GetMapping.*download' -A 10 --type java | grep -v 'canAccess\|authorize\|checkPermission'
```

### Checklist

- [ ] Upload dir nằm ngoài webroot (/var/app-data, /opt/data, v.v.)
- [ ] Không dùng `static/uploads` hoặc `public/uploads`
- [ ] Serve file qua controller với authorization check
- [ ] Validate path traversal (normalize + startsWith check)
- [ ] Set proper Content-Security-Policy headers
- [ ] Disable directory listing
- [ ] Block direct access đến upload dir (`/uploads/**` → deny)
- [ ] Log file access for audit

---

## 19.06 - Presigned URL cho cloud storage download

### Metadata
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Lý do:** Performance, security, scalability
- **Impact:** MEDIUM - server load, bandwidth cost
- **Tags:** `cloud`, `performance`, `s3`, `security`

### Tại sao?

**Vấn đề:**
- Download qua application server tốn bandwidth
- Không tận dụng CDN của cloud provider
- Expose credentials nếu dùng public bucket

**Lợi ích:**
- Direct download từ S3/Azure Blob (faster)
- Time-limited access (security)
- Giảm load cho application server

### ✅ Cách đúng

```java
// ✅ GOOD: AWS S3 presigned URL
@Service
public class S3FileStorageService {

  private final S3Client s3Client;
  private final String bucketName;

  public S3FileStorageService(
    @Value("${aws.s3.bucket}") String bucketName,
    @Value("${aws.region}") String region
  ) {
    this.bucketName = bucketName;
    this.s3Client = S3Client.builder()
      .region(Region.of(region))
      .build();
  }

  public String uploadFile(MultipartFile file) throws IOException {
    String key = UUID.randomUUID() + getExtension(file.getOriginalFilename());

    PutObjectRequest putRequest = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .contentType(file.getContentType())
      .metadata(Map.of(
        "original-filename", file.getOriginalFilename(),
        "uploaded-by", SecurityContextHolder.getContext().getAuthentication().getName()
      ))
      .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));

    return key;
  }

  public String generatePresignedUrl(String fileKey, Duration expiration) {
    S3Presigner presigner = S3Presigner.create();

    GetObjectRequest getRequest = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(fileKey)
      .build();

    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
      .signatureDuration(expiration) // Ví dụ: 15 phút
      .getObjectRequest(getRequest)
      .build();

    PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);

    return presignedRequest.url().toString();
  }
}

// ✅ GOOD: Controller trả về presigned URL
@RestController
@RequestMapping("/api/files")
public class FileController {

  private final S3FileStorageService storageService;
  private final FileAuthorizationService authService;

  @GetMapping("/{fileId}/download-url")
  public ResponseEntity<?> getDownloadUrl(
    @PathVariable String fileId,
    Authentication authentication
  ) {
    // 1. Check authorization
    FileMetadata metadata = fileRepository.findById(fileId)
      .orElseThrow(() -> new FileNotFoundException(fileId));

    if (!authService.canAccess(authentication, metadata)) {
      throw new AccessDeniedException("Không có quyền truy cập file này");
    }

    // 2. Generate presigned URL (valid for 15 minutes)
    String presignedUrl = storageService.generatePresignedUrl(
      metadata.getS3Key(),
      Duration.ofMinutes(15)
    );

    // 3. Return URL
    return ResponseEntity.ok(Map.of(
      "downloadUrl", presignedUrl,
      "expiresIn", 900, // seconds
      "filename", metadata.getOriginalFilename()
    ));
  }
}

// ✅ GOOD: Azure Blob Storage presigned URL (SAS token)
@Service
public class AzureBlobStorageService {

  private final BlobServiceClient blobServiceClient;
  private final String containerName;

  public AzureBlobStorageService(
    @Value("${azure.storage.connection-string}") String connectionString,
    @Value("${azure.storage.container}") String containerName
  ) {
    this.blobServiceClient = new BlobServiceClientBuilder()
      .connectionString(connectionString)
      .buildClient();
    this.containerName = containerName;
  }

  public String uploadFile(MultipartFile file) throws IOException {
    String blobName = UUID.randomUUID() + getExtension(file.getOriginalFilename());

    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
    BlobClient blobClient = containerClient.getBlobClient(blobName);

    blobClient.upload(file.getInputStream(), file.getSize(), true);

    return blobName;
  }

  public String generateSasUrl(String blobName, Duration expiration) {
    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
    BlobClient blobClient = containerClient.getBlobClient(blobName);

    OffsetDateTime expiryTime = OffsetDateTime.now().plus(expiration);

    BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);

    BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);

    String sasToken = blobClient.generateSas(sasValues);

    return blobClient.getBlobUrl() + "?" + sasToken;
  }
}

// ✅ GOOD: Caching presigned URL (với expiration check)
@Service
public class PresignedUrlCacheService {

  private final LoadingCache<String, CachedPresignedUrl> urlCache;
  private final S3FileStorageService storageService;

  public PresignedUrlCacheService(S3FileStorageService storageService) {
    this.storageService = storageService;
    this.urlCache = Caffeine.newBuilder()
      .expireAfterWrite(10, TimeUnit.MINUTES) // Cache 10 phút
      .maximumSize(10_000)
      .build(this::generatePresignedUrl);
  }

  private CachedPresignedUrl generatePresignedUrl(String fileKey) {
    String url = storageService.generatePresignedUrl(fileKey, Duration.ofMinutes(15));
    Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
    return new CachedPresignedUrl(url, expiresAt);
  }

  public String getPresignedUrl(String fileKey) {
    CachedPresignedUrl cached = urlCache.get(fileKey);

    // Nếu sắp hết hạn (< 2 phút), regenerate
    if (cached.expiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(2)))) {
      urlCache.invalidate(fileKey);
      cached = urlCache.get(fileKey);
    }

    return cached.url();
  }

  record CachedPresignedUrl(String url, Instant expiresAt) {}
}

// ✅ GOOD: Frontend usage
/*
// React example
const downloadFile = async (fileId) => {
  // 1. Get presigned URL
  const response = await fetch(`/api/files/${fileId}/download-url`);
  const { downloadUrl, filename } = await response.json();

  // 2. Download directly from S3 (không qua backend)
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.download = filename;
  link.click();
};
*/
```

### ❌ Cách sai

```java
// ❌ BAD: Download file qua application server
@GetMapping("/{fileId}/download")
public ResponseEntity<byte[]> download(@PathVariable String fileId) {
  FileMetadata metadata = fileRepository.findById(fileId).orElseThrow();

  // Download từ S3 vào memory
  byte[] content = s3Client.getObject(metadata.getS3Key()).readAllBytes();

  // Gửi qua response
  return ResponseEntity.ok()
    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
    .body(content);

  // Vấn đề:
  // - Tốn bandwidth của application server
  // - Tốn memory (load toàn bộ file vào RAM)
  // - Chậm hơn direct download từ S3
}

// ❌ BAD: Public S3 bucket (không cần auth)
public String uploadFile(MultipartFile file) {
  String key = UUID.randomUUID().toString();

  PutObjectRequest request = PutObjectRequest.builder()
    .bucket(bucketName)
    .key(key)
    .acl(ObjectCannedACL.PUBLIC_READ) // Public!
    .build();

  s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

  return "https://%s.s3.amazonaws.com/%s".formatted(bucketName, key);
  // Ai cũng có thể access!
}

// ❌ BAD: Presigned URL không có expiration limit
public String generatePresignedUrl(String key) {
  return storageService.generatePresignedUrl(key, Duration.ofDays(365)); // 1 năm!
  // URL leak → access forever
}

// ❌ BAD: Không check authorization trước khi tạo presigned URL
@GetMapping("/{fileId}/download-url")
public ResponseEntity<?> getDownloadUrl(@PathVariable String fileId) {
  FileMetadata metadata = fileRepository.findById(fileId).orElseThrow();
  String url = storageService.generatePresignedUrl(metadata.getS3Key());
  return ResponseEntity.ok(Map.of("url", url));
  // Không check quyền → bất kỳ ai biết fileId đều download được!
}
```

### Phát hiện

```bash
# Tìm download qua controller mà không dùng presigned URL
rg '@GetMapping.*download' -A 15 --type java | grep 'getObject\|downloadFile' | grep -v 'presign'

# Tìm S3 public ACL
rg 'PUBLIC_READ\|PUBLIC_WRITE' --type java

# Tìm presigned URL với expiration quá dài
rg 'generatePresignedUrl.*Duration.of(Days|Hours)\([^1-9]' --type java
```

### Checklist

- [ ] Dùng presigned URL cho download từ cloud storage
- [ ] Expiration time hợp lý (5-30 phút)
- [ ] Check authorization TRƯỚC KHI tạo presigned URL
- [ ] Cache presigned URL (với expiration check)
- [ ] Private S3 bucket/Azure container (không public)
- [ ] Log presigned URL generation for audit
- [ ] Handle expiration gracefully ở frontend

---

## 19.07 - Streaming upload cho file lớn (không load vào memory)

### Metadata
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Lý do:** Performance, prevent OOM
- **Impact:** MEDIUM - OOM, slow upload, poor UX
- **Tags:** `performance`, `memory`, `streaming`

### Tại sao?

**Vấn đề:**
- Load toàn bộ file vào memory → OOM
- Blocking I/O chậm cho file lớn
- Không thể upload file > available memory

**Lợi ích:**
- Constant memory usage (O(1))
- Hỗ trợ file kích thước lớn
- Better performance

### ✅ Cách đúng

```java
// ✅ GOOD: Streaming upload lên S3
@Service
public class StreamingS3UploadService {

  private final S3Client s3Client;
  private final String bucketName;

  public String uploadStream(MultipartFile file) throws IOException {
    String key = UUID.randomUUID() + getExtension(file.getOriginalFilename());

    // Streaming upload (không load toàn bộ vào memory)
    PutObjectRequest putRequest = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .contentType(file.getContentType())
      .contentLength(file.getSize())
      .build();

    // RequestBody.fromInputStream tự động streaming
    s3Client.putObject(putRequest, RequestBody.fromInputStream(
      file.getInputStream(),
      file.getSize()
    ));

    return key;
  }
}

// ✅ GOOD: Multipart upload cho file rất lớn (>100MB)
@Service
public class MultipartS3UploadService {

  private final S3Client s3Client;
  private final String bucketName;

  public String uploadLargeFile(MultipartFile file) throws IOException {
    String key = UUID.randomUUID() + getExtension(file.getOriginalFilename());

    // 1. Initiate multipart upload
    CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
      .bucket(bucketName)
      .key(key)
      .contentType(file.getContentType())
      .build();

    CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
    String uploadId = createResponse.uploadId();

    try {
      // 2. Upload parts (5MB mỗi part)
      int partSize = 5 * 1024 * 1024; // 5MB
      List<CompletedPart> completedParts = new ArrayList<>();

      try (InputStream inputStream = file.getInputStream()) {
        byte[] buffer = new byte[partSize];
        int partNumber = 1;
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) > 0) {
          ByteArrayInputStream partStream = new ByteArrayInputStream(buffer, 0, bytesRead);

          UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
            .bucket(bucketName)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .contentLength((long) bytesRead)
            .build();

          UploadPartResponse uploadPartResponse = s3Client.uploadPart(
            uploadPartRequest,
            RequestBody.fromInputStream(partStream, bytesRead)
          );

          completedParts.add(CompletedPart.builder()
            .partNumber(partNumber)
            .eTag(uploadPartResponse.eTag())
            .build());

          partNumber++;
        }
      }

      // 3. Complete multipart upload
      CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
        .bucket(bucketName)
        .key(key)
        .uploadId(uploadId)
        .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
        .build();

      s3Client.completeMultipartUpload(completeRequest);

      return key;
    } catch (Exception e) {
      // Abort multipart upload nếu có lỗi
      AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
        .bucket(bucketName)
        .key(key)
        .uploadId(uploadId)
        .build();
      s3Client.abortMultipartUpload(abortRequest);

      throw new RuntimeException("Upload failed", e);
    }
  }
}

// ✅ GOOD: Local file storage với streaming
@Service
public class StreamingFileStorageService {

  @Value("${app.upload.dir}")
  private String uploadDir;

  public String uploadStream(MultipartFile file) throws IOException {
    String filename = UUID.randomUUID() + getExtension(file.getOriginalFilename());
    Path targetPath = Paths.get(uploadDir, filename);

    // Streaming copy (không load vào memory)
    try (InputStream inputStream = file.getInputStream()) {
      Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    return filename;
  }

  public void downloadStream(String filename, HttpServletResponse response) throws IOException {
    Path path = Paths.get(uploadDir, filename);

    if (!Files.exists(path)) {
      throw new FileNotFoundException(filename);
    }

    // Streaming download
    response.setContentType(Files.probeContentType(path));
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    response.setContentLengthLong(Files.size(path));

    try (InputStream inputStream = Files.newInputStream(path);
         OutputStream outputStream = response.getOutputStream()) {
      inputStream.transferTo(outputStream);
    }
  }
}

// ✅ GOOD: Controller với StreamingResponseBody
@RestController
@RequestMapping("/api/files")
public class StreamingDownloadController {

  private final FileStorageService storageService;

  @GetMapping("/{fileId}/stream")
  public ResponseEntity<StreamingResponseBody> streamDownload(@PathVariable String fileId) {
    FileMetadata metadata = fileRepository.findById(fileId).orElseThrow();
    Path filePath = Paths.get(metadata.getPath());

    StreamingResponseBody stream = outputStream -> {
      try (InputStream inputStream = Files.newInputStream(filePath)) {
        byte[] buffer = new byte[8192]; // 8KB buffer
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
      }
    };

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(metadata.getContentType()))
      .contentLength(metadata.getSize())
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
      .body(stream);
  }
}

// ✅ GOOD: Resumable upload (tus protocol)
@RestController
@RequestMapping("/api/files/resumable")
public class ResumableUploadController {

  private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

  @PostMapping
  public ResponseEntity<?> createUploadSession(@RequestHeader("Upload-Length") long fileSize) {
    String sessionId = UUID.randomUUID().toString();
    Path tempPath = Paths.get("/tmp", sessionId);

    UploadSession session = new UploadSession(sessionId, fileSize, tempPath, 0);
    sessions.put(sessionId, session);

    return ResponseEntity.status(HttpStatus.CREATED)
      .header("Upload-ID", sessionId)
      .build();
  }

  @PatchMapping("/{sessionId}")
  public ResponseEntity<?> uploadChunk(
    @PathVariable String sessionId,
    @RequestHeader("Upload-Offset") long offset,
    @RequestBody byte[] chunk
  ) throws IOException {
    UploadSession session = sessions.get(sessionId);
    if (session == null) {
      return ResponseEntity.notFound().build();
    }

    // Validate offset
    if (offset != session.currentOffset()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    // Append chunk
    try (FileOutputStream fos = new FileOutputStream(session.tempPath().toFile(), true)) {
      fos.write(chunk);
    }

    session.incrementOffset(chunk.length);

    // Check if complete
    if (session.currentOffset() >= session.totalSize()) {
      // Move to permanent storage
      String finalPath = storageService.finalize(session.tempPath());
      sessions.remove(sessionId);

      return ResponseEntity.ok(Map.of("fileId", finalPath));
    }

    return ResponseEntity.status(HttpStatus.NO_CONTENT)
      .header("Upload-Offset", String.valueOf(session.currentOffset()))
      .build();
  }

  record UploadSession(String id, long totalSize, Path tempPath, long currentOffset) {
    void incrementOffset(long bytes) {
      // Update offset (simplified, use AtomicLong in production)
    }
  }
}
```

### ❌ Cách sai

```java
// ❌ BAD: Load toàn bộ file vào memory
public String uploadFile(MultipartFile file) throws IOException {
  byte[] bytes = file.getBytes(); // OOM nếu file lớn!

  String key = UUID.randomUUID().toString();
  s3Client.putObject(key, RequestBody.fromBytes(bytes));

  return key;
}

// ❌ BAD: Download vào memory trước khi gửi response
@GetMapping("/{fileId}/download")
public ResponseEntity<byte[]> download(@PathVariable String fileId) {
  FileMetadata metadata = fileRepository.findById(fileId).orElseThrow();

  byte[] content = Files.readAllBytes(Paths.get(metadata.getPath())); // OOM!

  return ResponseEntity.ok()
    .body(content);
}

// ❌ BAD: Blocking I/O cho file lớn
public void processLargeFile(MultipartFile file) throws IOException {
  // Load toàn bộ vào List
  List<String> lines = new BufferedReader(new InputStreamReader(file.getInputStream()))
    .lines()
    .collect(Collectors.toList()); // OOM nếu file có nhiều triệu dòng!

  // Process...
}
```

### Phát hiện

```bash
# Tìm code load file vào memory
rg 'getBytes\(\)|readAllBytes' --type java

# Tìm upload không dùng streaming
rg 'putObject.*RequestBody.from(Bytes|String)' --type java

# Tìm download không streaming
rg 'ResponseEntity.*byte\[\]' --type java
```

### Checklist

- [ ] Dùng streaming cho upload (không load vào memory)
- [ ] Dùng streaming cho download (StreamingResponseBody)
- [ ] Multipart upload cho file > 100MB
- [ ] Proper buffer size (8KB - 64KB)
- [ ] Handle upload interruption (resumable upload)
- [ ] Monitor memory usage khi upload/download
- [ ] Cleanup temp files nếu upload failed

---

## 19.08 - Cleanup orphaned files (scheduled task)

### Metadata
- **Mức độ:** 🟡 NÊN CÓ
- **Lý do:** Resource management, cost optimization
- **Impact:** LOW - disk waste, storage cost
- **Tags:** `maintenance`, `cleanup`, `scheduling`

### Tại sao?

**Vấn đề:**
- Upload thành công nhưng transaction rollback
- User xóa record trong DB nhưng file vẫn còn
- Temp files không được cleanup

**Hậu quả:**
- Lãng phí disk space
- Tăng chi phí cloud storage
- Performance degradation (quá nhiều file)

### ✅ Cách đúng

```java
// ✅ GOOD: Scheduled task cleanup orphaned files
@Component
@Slf4j
public class OrphanedFileCleanupJob {

  private final FileRepository fileRepository;
  private final FileStorageService storageService;

  @Value("${app.cleanup.orphan-age-days:7}")
  private int orphanAgeDays;

  // Chạy hàng ngày lúc 2 AM
  @Scheduled(cron = "0 0 2 * * *")
  @Transactional
  public void cleanupOrphanedFiles() {
    log.info("Starting orphaned file cleanup job...");

    Instant cutoffTime = Instant.now().minus(Duration.ofDays(orphanAgeDays));

    // 1. Tìm files trong storage
    List<String> storedFiles = storageService.listAllFiles();
    log.info("Found {} files in storage", storedFiles.size());

    // 2. Tìm files trong DB
    Set<String> referencedFiles = fileRepository.findAll()
      .stream()
      .map(FileMetadata::getStoredFilename)
      .collect(Collectors.toSet());
    log.info("Found {} files referenced in DB", referencedFiles.size());

    // 3. Tìm orphaned files
    List<String> orphanedFiles = storedFiles.stream()
      .filter(file -> !referencedFiles.contains(file))
      .toList();

    log.info("Found {} orphaned files", orphanedFiles.size());

    // 4. Delete orphaned files (older than cutoff)
    int deletedCount = 0;
    for (String orphanedFile : orphanedFiles) {
      try {
        Instant lastModified = storageService.getLastModifiedTime(orphanedFile);
        if (lastModified.isBefore(cutoffTime)) {
          storageService.delete(orphanedFile);
          deletedCount++;
          log.debug("Deleted orphaned file: {}", orphanedFile);
        }
      } catch (Exception e) {
        log.error("Failed to delete orphaned file: {}", orphanedFile, e);
      }
    }

    log.info("Cleanup completed. Deleted {} orphaned files", deletedCount);
  }
}

// ✅ GOOD: Soft delete với expiration
@Entity
@Table(name = "files")
public class FileMetadata {

  @Id
  private String id;

  private String storedFilename;

  @Column(nullable = false)
  private Instant uploadedAt;

  private Instant deletedAt; // NULL = active

  private Instant expiresAt; // Auto-delete after this time

  @Column(nullable = false)
  private FileStatus status; // PENDING, ACTIVE, DELETED
}

@Component
@Slf4j
public class SoftDeletedFileCleanupJob {

  private final FileRepository fileRepository;
  private final FileStorageService storageService;

  @Value("${app.cleanup.soft-delete-retention-days:30}")
  private int retentionDays;

  @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
  @Transactional
  public void cleanupSoftDeletedFiles() {
    Instant cutoffTime = Instant.now().minus(Duration.ofDays(retentionDays));

    // Tìm files đã soft delete > 30 ngày
    List<FileMetadata> filesToDelete = fileRepository
      .findByDeletedAtBeforeAndStatus(cutoffTime, FileStatus.DELETED);

    log.info("Found {} soft-deleted files to permanently delete", filesToDelete.size());

    for (FileMetadata file : filesToDelete) {
      try {
        // Delete from storage
        storageService.delete(file.getStoredFilename());

        // Delete from DB
        fileRepository.delete(file);

        log.debug("Permanently deleted file: {}", file.getId());
      } catch (Exception e) {
        log.error("Failed to delete file: {}", file.getId(), e);
      }
    }
  }
}

// ✅ GOOD: Cleanup expired files
@Component
@Slf4j
public class ExpiredFileCleanupJob {

  private final FileRepository fileRepository;
  private final FileStorageService storageService;

  @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
  @Transactional
  public void cleanupExpiredFiles() {
    Instant now = Instant.now();

    // Tìm files đã hết hạn
    List<FileMetadata> expiredFiles = fileRepository
      .findByExpiresAtBeforeAndStatus(now, FileStatus.ACTIVE);

    log.info("Found {} expired files", expiredFiles.size());

    for (FileMetadata file : expiredFiles) {
      try {
        // Delete from storage
        storageService.delete(file.getStoredFilename());

        // Update status hoặc delete record
        file.setStatus(FileStatus.EXPIRED);
        file.setDeletedAt(now);
        fileRepository.save(file);

        log.debug("Cleaned up expired file: {}", file.getId());
      } catch (Exception e) {
        log.error("Failed to cleanup expired file: {}", file.getId(), e);
      }
    }
  }
}

// ✅ GOOD: Cleanup temp upload files
@Component
@Slf4j
public class TempFileCleanupJob {

  @Value("${app.upload.temp-dir:/tmp/uploads}")
  private String tempDir;

  @Scheduled(cron = "0 0 * * * *") // Every hour
  public void cleanupTempFiles() throws IOException {
    Path tempPath = Paths.get(tempDir);

    if (!Files.exists(tempPath)) return;

    Instant cutoffTime = Instant.now().minus(Duration.ofHours(1));

    try (var stream = Files.walk(tempPath)) {
      List<Path> oldFiles = stream
        .filter(Files::isRegularFile)
        .filter(path -> {
          try {
            FileTime lastModified = Files.getLastModifiedTime(path);
            return lastModified.toInstant().isBefore(cutoffTime);
          } catch (IOException e) {
            return false;
          }
        })
        .toList();

      log.info("Found {} temp files older than 1 hour", oldFiles.size());

      for (Path file : oldFiles) {
        try {
          Files.delete(file);
          log.debug("Deleted temp file: {}", file);
        } catch (IOException e) {
          log.error("Failed to delete temp file: {}", file, e);
        }
      }
    }
  }
}

// ✅ GOOD: Monitor storage usage
@Component
@Slf4j
public class StorageUsageMonitor {

  private final FileRepository fileRepository;
  private final MeterRegistry meterRegistry;

  @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
  public void updateStorageMetrics() {
    // Total file count
    long totalFiles = fileRepository.count();
    meterRegistry.gauge("storage.files.total", totalFiles);

    // Total size
    long totalSize = fileRepository.sumFileSize();
    meterRegistry.gauge("storage.size.bytes", totalSize);

    // Count by status
    Map<FileStatus, Long> countByStatus = fileRepository.countByStatus();
    countByStatus.forEach((status, count) ->
      meterRegistry.gauge("storage.files.by_status", Tags.of("status", status.name()), count)
    );

    log.debug("Storage metrics updated: {} files, {} bytes", totalFiles, totalSize);
  }
}
```

### ❌ Cách sai

```java
// ❌ BAD: Không có cleanup job
// Files bị orphaned tích tụ mãi mãi

// ❌ BAD: Delete file trước khi delete DB record
@Transactional
public void deleteFile(String fileId) {
  FileMetadata metadata = fileRepository.findById(fileId).orElseThrow();

  // Delete file trước
  storageService.delete(metadata.getStoredFilename());

  // Delete DB record sau (nếu exception → orphaned record)
  fileRepository.delete(metadata);
}

// ❌ BAD: Hard delete ngay lập tức (không soft delete)
public void deleteFile(String fileId) {
  FileMetadata metadata = fileRepository.findById(fileId).orElseThrow();
  storageService.delete(metadata.getStoredFilename()); // Không thể recover!
  fileRepository.delete(metadata);
}

// ❌ BAD: Cleanup job không có timeout/batch limit
@Scheduled(cron = "0 0 2 * * *")
public void cleanup() {
  List<String> allFiles = storageService.listAllFiles(); // Millions of files!

  for (String file : allFiles) {
    // Process từng file → timeout, OOM
  }
}
```

### Phát hiện

```bash
# Tìm project không có @Scheduled cleanup job
rg '@Scheduled' --type java | grep -i cleanup

# Tìm delete file mà không soft delete
rg 'storageService.delete' --type java | grep -v 'deletedAt\|soft'

# Tìm code delete file trước DB record
rg 'delete.*file.*repository.delete' -A 5 --type java
```

### Checklist

- [ ] Scheduled job cleanup orphaned files
- [ ] Soft delete với retention period
- [ ] Cleanup expired files (nếu có TTL)
- [ ] Cleanup temp upload files
- [ ] Monitor storage usage metrics
- [ ] Batch processing (không load tất cả vào memory)
- [ ] Error handling và retry logic
- [ ] Alert khi storage usage cao

---

## 19.09 - Image resize/compress trước khi lưu

### Metadata
- **Mức độ:** 🟡 NÊN CÓ
- **Lý do:** Performance, cost optimization, UX
- **Impact:** LOW - storage cost, bandwidth, loading time
- **Tags:** `optimization`, `image`, `performance`

### Tại sao?

**Vấn đề:**
- User upload ảnh 10MB từ smartphone
- Display ảnh chỉ cần 200KB (thumbnail)
- Lãng phí bandwidth và storage

**Lợi ích:**
- Giảm storage cost
- Faster page load
- Better UX (especially mobile)

### ✅ Cách đúng

```java
// ✅ GOOD: Image resize với Thumbnailator
@Service
public class ImageProcessingService {

  @Value("${app.image.max-width:1920}")
  private int maxWidth;

  @Value("${app.image.max-height:1080}")
  private int maxHeight;

  @Value("${app.image.quality:0.85}")
  private float quality;

  public byte[] resizeImage(MultipartFile file) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Thumbnails.of(file.getInputStream())
      .size(maxWidth, maxHeight)
      .outputFormat("jpg")
      .outputQuality(quality)
      .toOutputStream(outputStream);

    return outputStream.toByteArray();
  }

  public byte[] createThumbnail(MultipartFile file, int width, int height) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Thumbnails.of(file.getInputStream())
      .size(width, height)
      .crop(Positions.CENTER) // Crop to exact size
      .outputFormat("jpg")
      .outputQuality(0.8f)
      .toOutputStream(outputStream);

    return outputStream.toByteArray();
  }
}

// ✅ GOOD: Multi-size image storage (responsive images)
@Service
public class ImageUploadService {

  private final ImageProcessingService imageProcessing;
  private final FileStorageService storageService;

  @Transactional
  public ImageMetadata uploadImage(MultipartFile file) throws IOException {
    // Validate image type
    if (!isImage(file)) {
      throw new InvalidFileException("File không phải ảnh");
    }

    // Generate base ID
    String baseId = UUID.randomUUID().toString();

    // 1. Original (resize nếu quá lớn)
    byte[] original = imageProcessing.resizeImage(file);
    String originalKey = storageService.save(baseId + "_original.jpg", original);

    // 2. Large (1200x800)
    byte[] large = imageProcessing.createThumbnail(file, 1200, 800);
    String largeKey = storageService.save(baseId + "_large.jpg", large);

    // 3. Medium (800x600)
    byte[] medium = imageProcessing.createThumbnail(file, 800, 600);
    String mediumKey = storageService.save(baseId + "_medium.jpg", medium);

    // 4. Small (400x300)
    byte[] small = imageProcessing.createThumbnail(file, 400, 300);
    String smallKey = storageService.save(baseId + "_small.jpg", small);

    // 5. Thumbnail (150x150)
    byte[] thumbnail = imageProcessing.createThumbnail(file, 150, 150);
    String thumbnailKey = storageService.save(baseId + "_thumb.jpg", thumbnail);

    // Save metadata
    return ImageMetadata.builder()
      .id(baseId)
      .originalKey(originalKey)
      .largeKey(largeKey)
      .mediumKey(mediumKey)
      .smallKey(smallKey)
      .thumbnailKey(thumbnailKey)
      .originalFilename(file.getOriginalFilename())
      .build();
  }

  private boolean isImage(MultipartFile file) {
    String contentType = file.getContentType();
    return contentType != null && contentType.startsWith("image/");
  }
}

// ✅ GOOD: Async image processing
@Service
public class AsyncImageProcessingService {

  private final ImageProcessingService imageProcessing;
  private final FileStorageService storageService;
  private final ApplicationEventPublisher eventPublisher;

  @Async
  public CompletableFuture<ImageMetadata> processImageAsync(String uploadId, MultipartFile file) {
    try {
      // 1. Lưu original tạm
      String tempKey = storageService.saveTemp(uploadId, file.getBytes());

      // 2. Process các size variants
      ImageMetadata metadata = createImageVariants(uploadId, file);

      // 3. Delete temp file
      storageService.delete(tempKey);

      // 4. Publish event
      eventPublisher.publishEvent(new ImageProcessedEvent(uploadId, metadata));

      return CompletableFuture.completedFuture(metadata);
    } catch (Exception e) {
      eventPublisher.publishEvent(new ImageProcessingFailedEvent(uploadId, e.getMessage()));
      throw new CompletionException(e);
    }
  }

  private ImageMetadata createImageVariants(String baseId, MultipartFile file) throws IOException {
    // Similar to uploadImage() above
    // ...
    return null;
  }
}

// ✅ GOOD: WebP format support (better compression)
@Service
public class WebPImageService {

  public byte[] convertToWebP(MultipartFile file) throws IOException {
    // Sử dụng libwebp hoặc imageio-webp
    BufferedImage image = ImageIO.read(file.getInputStream());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();

    ImageWriteParam writeParam = writer.getDefaultWriteParam();
    writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    writeParam.setCompressionQuality(0.85f);

    ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream);
    writer.setOutput(ios);
    writer.write(null, new IIOImage(image, null, null), writeParam);

    writer.dispose();
    ios.close();

    return outputStream.toByteArray();
  }
}

// ✅ GOOD: Controller trả về responsive image URLs
@RestController
@RequestMapping("/api/images")
public class ImageController {

  @GetMapping("/{imageId}")
  public ResponseEntity<?> getImage(@PathVariable String imageId) {
    ImageMetadata metadata = imageRepository.findById(imageId).orElseThrow();

    return ResponseEntity.ok(Map.of(
      "id", metadata.getId(),
      "urls", Map.of(
        "original", generateUrl(metadata.getOriginalKey()),
        "large", generateUrl(metadata.getLargeKey()),
        "medium", generateUrl(metadata.getMediumKey()),
        "small", generateUrl(metadata.getSmallKey()),
        "thumbnail", generateUrl(metadata.getThumbnailKey())
      ),
      "srcset", generateSrcSet(metadata)
    ));
  }

  private String generateSrcSet(ImageMetadata metadata) {
    return String.join(", ",
      generateUrl(metadata.getSmallKey()) + " 400w",
      generateUrl(metadata.getMediumKey()) + " 800w",
      generateUrl(metadata.getLargeKey()) + " 1200w",
      generateUrl(metadata.getOriginalKey()) + " 1920w"
    );
  }
}

// ✅ GOOD: Frontend usage
/*
<img
  src="/api/images/123/medium"
  srcset="/api/images/123/srcset"
  sizes="(max-width: 600px) 400px, (max-width: 1200px) 800px, 1200px"
  alt="Responsive image"
/>
*/
```

### ❌ Cách sai

```java
// ❌ BAD: Lưu original image không resize
@PostMapping("/upload/image")
public ResponseEntity<?> uploadImage(@RequestParam MultipartFile file) {
  String key = storageService.save(file); // Lưu 10MB original!
  return ResponseEntity.ok(Map.of("imageUrl", key));
}

// ❌ BAD: Resize on-the-fly khi request
@GetMapping("/images/{id}/thumbnail")
public ResponseEntity<byte[]> getThumbnail(@PathVariable String id) {
  byte[] original = storageService.load(id); // Load 10MB
  byte[] thumbnail = imageProcessing.resize(original, 150, 150); // Resize mỗi request!
  return ResponseEntity.ok(thumbnail);
  // Waste CPU, slow response time
}

// ❌ BAD: Không maintain aspect ratio
public byte[] resize(byte[] image, int width, int height) {
  // Force exact size → image bị méo
  return Thumbnails.of(new ByteArrayInputStream(image))
    .forceSize(width, height) // ❌ forceSize
    .asBytes();
}

// ❌ BAD: Quality quá cao hoặc quá thấp
Thumbnails.of(file)
  .size(800, 600)
  .outputQuality(1.0f) // 100% quality → file size lớn không cần thiết
  .toOutputStream(out);

Thumbnails.of(file)
  .size(800, 600)
  .outputQuality(0.3f) // 30% quality → ảnh mờ, blocky
  .toOutputStream(out);
```

### Phát hiện

```bash
# Tìm upload image mà không resize
rg '@PostMapping.*image.*upload' -A 15 --type java | grep -v 'resize\|thumbnail\|compress'

# Tìm code resize on-the-fly
rg '@GetMapping.*thumbnail' -A 10 --type java | grep 'resize'

# Tìm Thumbnailator usage với bad quality
rg 'outputQuality\((0\.[0-3]|1\.0)' --type java
```

### Checklist

- [ ] Resize image trước khi lưu (max width/height)
- [ ] Generate multiple sizes (original, large, medium, small, thumbnail)
- [ ] Proper quality setting (0.8 - 0.9)
- [ ] Maintain aspect ratio
- [ ] Support WebP format
- [ ] Async processing cho image variants
- [ ] Return responsive image URLs (srcset)
- [ ] Consider lazy loading

---

## 19.10 - Storage abstraction layer (local ↔ S3 ↔ Azure Blob)

### Metadata
- **Mức độ:** 🟡 NÊN CÓ
- **Lý do:** Flexibility, testability, vendor lock-in prevention
- **Impact:** LOW - vendor lock-in, testing difficulty
- **Tags:** `architecture`, `abstraction`, `cloud-agnostic`

### Tại sao?

**Vấn đề:**
- Vendor lock-in (khó migrate từ S3 sang Azure)
- Khó test (phụ thuộc vào cloud service)
- Duplicate code cho mỗi storage backend

**Lợi ích:**
- Easy migration giữa cloud providers
- Testable (mock storage)
- Consistent API

### ✅ Cách đúng

```java
// ✅ GOOD: Storage abstraction interface
public interface FileStorageService {

  /**
   * Upload file và trả về storage key
   */
  String upload(String filename, InputStream inputStream, long size, String contentType) throws IOException;

  /**
   * Upload file từ MultipartFile
   */
  default String upload(MultipartFile file) throws IOException {
    return upload(
      file.getOriginalFilename(),
      file.getInputStream(),
      file.getSize(),
      file.getContentType()
    );
  }

  /**
   * Download file
   */
  InputStream download(String key) throws IOException;

  /**
   * Delete file
   */
  void delete(String key) throws IOException;

  /**
   * Check if file exists
   */
  boolean exists(String key);

  /**
   * Get file metadata
   */
  StorageMetadata getMetadata(String key) throws IOException;

  /**
   * Generate presigned URL (nếu hỗ trợ)
   */
  Optional<String> generatePresignedUrl(String key, Duration expiration);

  /**
   * List files với prefix
   */
  List<String> list(String prefix);
}

record StorageMetadata(
  String key,
  long size,
  String contentType,
  Instant lastModified
) {}

// ✅ GOOD: Local filesystem implementation
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

  private final Path uploadLocation;

  public LocalFileStorageService(@Value("${app.upload.dir:/var/app/uploads}") String uploadDir) {
    this.uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

    try {
      Files.createDirectories(uploadLocation);
    } catch (IOException e) {
      throw new RuntimeException("Could not create upload directory", e);
    }
  }

  @Override
  public String upload(String filename, InputStream inputStream, long size, String contentType) throws IOException {
    String key = UUID.randomUUID() + "_" + filename;
    Path targetPath = uploadLocation.resolve(key);

    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

    return key;
  }

  @Override
  public InputStream download(String key) throws IOException {
    Path path = uploadLocation.resolve(key).normalize();

    if (!path.startsWith(uploadLocation)) {
      throw new SecurityException("Path traversal attempt: " + key);
    }

    if (!Files.exists(path)) {
      throw new FileNotFoundException("File not found: " + key);
    }

    return Files.newInputStream(path);
  }

  @Override
  public void delete(String key) throws IOException {
    Path path = uploadLocation.resolve(key).normalize();

    if (!path.startsWith(uploadLocation)) {
      throw new SecurityException("Path traversal attempt: " + key);
    }

    Files.deleteIfExists(path);
  }

  @Override
  public boolean exists(String key) {
    Path path = uploadLocation.resolve(key).normalize();
    return path.startsWith(uploadLocation) && Files.exists(path);
  }

  @Override
  public StorageMetadata getMetadata(String key) throws IOException {
    Path path = uploadLocation.resolve(key).normalize();

    if (!Files.exists(path)) {
      throw new FileNotFoundException("File not found: " + key);
    }

    return new StorageMetadata(
      key,
      Files.size(path),
      Files.probeContentType(path),
      Files.getLastModifiedTime(path).toInstant()
    );
  }

  @Override
  public Optional<String> generatePresignedUrl(String key, Duration expiration) {
    // Local storage không hỗ trợ presigned URL
    return Optional.empty();
  }

  @Override
  public List<String> list(String prefix) {
    try (var stream = Files.walk(uploadLocation)) {
      return stream
        .filter(Files::isRegularFile)
        .map(uploadLocation::relativize)
        .map(Path::toString)
        .filter(name -> prefix == null || name.startsWith(prefix))
        .toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to list files", e);
    }
  }
}

// ✅ GOOD: S3 implementation
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final String bucketName;

  public S3FileStorageService(
    @Value("${aws.s3.bucket}") String bucketName,
    @Value("${aws.region}") String region
  ) {
    this.bucketName = bucketName;
    this.s3Client = S3Client.builder()
      .region(Region.of(region))
      .build();
    this.s3Presigner = S3Presigner.builder()
      .region(Region.of(region))
      .build();
  }

  @Override
  public String upload(String filename, InputStream inputStream, long size, String contentType) throws IOException {
    String key = UUID.randomUUID() + "_" + filename;

    PutObjectRequest putRequest = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .contentType(contentType)
      .contentLength(size)
      .build();

    s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, size));

    return key;
  }

  @Override
  public InputStream download(String key) throws IOException {
    GetObjectRequest getRequest = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .build();

    return s3Client.getObject(getRequest);
  }

  @Override
  public void delete(String key) {
    DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .build();

    s3Client.deleteObject(deleteRequest);
  }

  @Override
  public boolean exists(String key) {
    try {
      HeadObjectRequest headRequest = HeadObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

      s3Client.headObject(headRequest);
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  @Override
  public StorageMetadata getMetadata(String key) throws IOException {
    HeadObjectRequest headRequest = HeadObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .build();

    HeadObjectResponse response = s3Client.headObject(headRequest);

    return new StorageMetadata(
      key,
      response.contentLength(),
      response.contentType(),
      response.lastModified()
    );
  }

  @Override
  public Optional<String> generatePresignedUrl(String key, Duration expiration) {
    GetObjectRequest getRequest = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .build();

    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
      .signatureDuration(expiration)
      .getObjectRequest(getRequest)
      .build();

    PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);

    return Optional.of(presigned.url().toString());
  }

  @Override
  public List<String> list(String prefix) {
    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
      .bucket(bucketName)
      .prefix(prefix)
      .build();

    ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

    return response.contents().stream()
      .map(S3Object::key)
      .toList();
  }
}

// ✅ GOOD: Azure Blob Storage implementation
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "azure")
public class AzureBlobStorageService implements FileStorageService {

  private final BlobServiceClient blobServiceClient;
  private final String containerName;

  public AzureBlobStorageService(
    @Value("${azure.storage.connection-string}") String connectionString,
    @Value("${azure.storage.container}") String containerName
  ) {
    this.blobServiceClient = new BlobServiceClientBuilder()
      .connectionString(connectionString)
      .buildClient();
    this.containerName = containerName;
  }

  @Override
  public String upload(String filename, InputStream inputStream, long size, String contentType) {
    String blobName = UUID.randomUUID() + "_" + filename;

    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
    BlobClient blobClient = containerClient.getBlobClient(blobName);

    blobClient.upload(inputStream, size, true);

    return blobName;
  }

  @Override
  public InputStream download(String key) {
    BlobClient blobClient = getBlobClient(key);
    return blobClient.openInputStream();
  }

  @Override
  public void delete(String key) {
    BlobClient blobClient = getBlobClient(key);
    blobClient.delete();
  }

  @Override
  public boolean exists(String key) {
    BlobClient blobClient = getBlobClient(key);
    return blobClient.exists();
  }

  @Override
  public StorageMetadata getMetadata(String key) {
    BlobClient blobClient = getBlobClient(key);
    BlobProperties properties = blobClient.getProperties();

    return new StorageMetadata(
      key,
      properties.getBlobSize(),
      properties.getContentType(),
      properties.getLastModified().toInstant()
    );
  }

  @Override
  public Optional<String> generatePresignedUrl(String key, Duration expiration) {
    BlobClient blobClient = getBlobClient(key);

    OffsetDateTime expiryTime = OffsetDateTime.now().plus(expiration);
    BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
    BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);

    String sasToken = blobClient.generateSas(sasValues);
    String url = blobClient.getBlobUrl() + "?" + sasToken;

    return Optional.of(url);
  }

  @Override
  public List<String> list(String prefix) {
    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

    return containerClient.listBlobsByHierarchy(prefix).stream()
      .map(item -> item.getName())
      .toList();
  }

  private BlobClient getBlobClient(String blobName) {
    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
    return containerClient.getBlobClient(blobName);
  }
}

// ✅ GOOD: Configuration
@Configuration
public class StorageConfiguration {

  @Bean
  @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
  public FileStorageService localFileStorageService() {
    return new LocalFileStorageService();
  }

  @Bean
  @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
  public FileStorageService s3FileStorageService() {
    return new S3FileStorageService();
  }

  @Bean
  @ConditionalOnProperty(name = "app.storage.type", havingValue = "azure")
  public FileStorageService azureBlobStorageService() {
    return new AzureBlobStorageService();
  }
}

// application.yml
/*
app:
  storage:
    type: ${STORAGE_TYPE:local}  # local, s3, azure
  upload:
    dir: /var/app/uploads  # For local storage

aws:
  s3:
    bucket: my-bucket
  region: us-east-1

azure:
  storage:
    connection-string: ${AZURE_STORAGE_CONNECTION_STRING}
    container: uploads
*/
```

### ❌ Cách sai

```java
// ❌ BAD: Hardcode S3 client ở nhiều nơi
@Service
public class FileUploadService {

  @Autowired
  private S3Client s3Client; // Tight coupling với S3!

  public String uploadFile(MultipartFile file) {
    // S3-specific code
    PutObjectRequest request = PutObjectRequest.builder()
      .bucket("my-bucket")
      .key(UUID.randomUUID().toString())
      .build();

    s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

    // Không thể dễ dàng switch sang Azure hoặc local storage
  }
}

// ❌ BAD: Không có abstraction, mỗi service tự implement
@Service
public class ProfileService {
  @Autowired private S3Client s3Client;

  public void uploadAvatar(MultipartFile file) {
    // Duplicate S3 logic
  }
}

@Service
public class DocumentService {
  @Autowired private S3Client s3Client;

  public void uploadDocument(MultipartFile file) {
    // Duplicate S3 logic (again!)
  }
}

// ❌ BAD: Không dùng @ConditionalOnProperty
@Configuration
public class StorageConfig {

  @Bean
  public FileStorageService fileStorageService() {
    // Hardcoded implementation
    return new S3FileStorageService();
    // Phải sửa code để switch implementation!
  }
}
```

### Phát hiện

```bash
# Tìm direct usage của S3Client/BlobClient
rg '@Autowired.*S3Client|@Autowired.*BlobClient' --type java

# Tìm service không dùng abstraction interface
rg 'class.*Service.*{' -A 10 --type java | grep 'S3Client\|BlobClient' | grep -v 'implements FileStorageService'

# Tìm hardcoded bucket/container names
rg '"[a-z-]+-bucket"|"[a-z-]+-container"' --type java | grep -v '@Value'
```

### Checklist

- [ ] Storage abstraction interface (FileStorageService)
- [ ] Multiple implementations (Local, S3, Azure, GCS)
- [ ] Configuration-driven selection (@ConditionalOnProperty)
- [ ] Consistent API across implementations
- [ ] Easy to test (mock interface)
- [ ] No direct usage của cloud SDK ở business logic
- [ ] Support presigned URLs (nếu cloud storage hỗ trợ)
- [ ] Migration path documented

---

## Tổng kết Domain 19: File Storage & Upload

### Priority Matrix

| Mức độ | Practices | Trọng số |
|--------|-----------|----------|
| 🔴 BẮT BUỘC | 19.01, 19.02, 19.05 | ×3 |
| 🟠 KHUYẾN NGHỊ | 19.03, 19.04, 19.06, 19.07 | ×2 |
| 🟡 NÊN CÓ | 19.08, 19.09, 19.10 | ×1 |

### Quick Checklist

**Security (CRITICAL):**
- [ ] File type validation (MIME + magic bytes)
- [ ] Max file size limit
- [ ] Virus scan
- [ ] Lưu file ngoài webroot
- [ ] Authorization check trước khi serve file

**Performance:**
- [ ] Streaming upload/download
- [ ] Presigned URL cho cloud storage
- [ ] Image resize/compress
- [ ] Multipart upload cho file lớn

**Maintenance:**
- [ ] Cleanup orphaned files
- [ ] Soft delete với retention
- [ ] Storage metrics monitoring

**Architecture:**
- [ ] Storage abstraction layer
- [ ] Unique filename generation
- [ ] Cloud-agnostic design

### Anti-patterns phổ biến

1. ❌ Chỉ validate extension, không validate magic bytes
2. ❌ Load file vào memory (getBytes()) thay vì streaming
3. ❌ Lưu file trong webroot (RCE risk)
4. ❌ Download qua application server thay vì presigned URL
5. ❌ Không cleanup orphaned files
6. ❌ Hardcode cloud SDK thay vì abstraction layer

### Tools & Libraries

| Tool | Purpose |
|------|---------|
| Apache Tika | MIME type detection |
| ClamAV | Virus scanning |
| Thumbnailator | Image resize |
| AWS S3 SDK | S3 integration |
| Azure Blob SDK | Azure Blob integration |
| Spring Multipart | File upload handling |
