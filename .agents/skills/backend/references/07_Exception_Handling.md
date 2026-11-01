# Domain 07: Exception Handling
> **Số practices:** 9 | 🔴 3 | 🟠 5 | 🟡 1
> **Trọng số:** ×1

---

## 07.01 - Custom exception hierarchy (BusinessException, TechnicalException)
**Mức:** 🔴 BẮT BUỘC

### Metadata
- **ID:** `EXC-001`
- **Danh mục:** Exception Design
- **Độ nghiêm trọng:** HIGH
- **Thời gian sửa:** 30 phút

### Tại sao?
**Vấn đề:**
- Dùng generic exception (RuntimeException, Exception) không phân biệt được lỗi nghiệp vụ vs lỗi kỹ thuật
- Khó xử lý tập trung, mỗi handler phải instanceof check
- Không rõ ràng HTTP status code nào phù hợp
- Log pollution khi technical error bị log như business error

**Giải pháp:**
- Tạo hierarchy exception rõ ràng: BusinessException (4xx), TechnicalException (5xx)
- Mỗi loại có error code riêng (ERR_USER_NOT_FOUND vs SYS_DB_CONNECTION_FAILED)
- Dễ dàng route đến đúng handler, đúng HTTP status, đúng log level

**Lợi ích:**
- Code clean hơn, không cần nhiều try-catch
- Dễ trace lỗi (business logic bug vs infrastructure issue)
- Client nhận được error code có ý nghĩa
- Monitoring/alerting chính xác hơn

### ✅ Cách đúng

```java
// Base exception với correlation ID
public abstract class BaseException extends RuntimeException {
  private final String errorCode;
  private final String correlationId;

  protected BaseException(String errorCode, String message, String correlationId) {
    super(message);
    this.errorCode = errorCode;
    this.correlationId = correlationId;
  }

  protected BaseException(String errorCode, String message, Throwable cause, String correlationId) {
    super(message, cause);
    this.errorCode = errorCode;
    this.correlationId = correlationId;
  }

  public String getErrorCode() { return errorCode; }
  public String getCorrelationId() { return correlationId; }
}

// Business exception (4xx) - lỗi từ phía client
public class BusinessException extends BaseException {
  public BusinessException(String errorCode, String message, String correlationId) {
    super(errorCode, message, correlationId);
  }

  // Factory methods cho các lỗi thường gặp
  public static BusinessException notFound(String resource, Object id, String correlationId) {
    return new BusinessException(
      "ERR_NOT_FOUND",
      String.format("%s với ID %s không tồn tại", resource, id),
      correlationId
    );
  }

  public static BusinessException invalidInput(String field, String reason, String correlationId) {
    return new BusinessException(
      "ERR_INVALID_INPUT",
      String.format("Trường %s không hợp lệ: %s", field, reason),
      correlationId
    );
  }

  public static BusinessException forbidden(String action, String correlationId) {
    return new BusinessException(
      "ERR_FORBIDDEN",
      String.format("Không có quyền thực hiện: %s", action),
      correlationId
    );
  }
}

// Technical exception (5xx) - lỗi từ phía server
public class TechnicalException extends BaseException {
  public TechnicalException(String errorCode, String message, Throwable cause, String correlationId) {
    super(errorCode, message, cause, correlationId);
  }

  public static TechnicalException databaseError(Throwable cause, String correlationId) {
    return new TechnicalException(
      "SYS_DB_ERROR",
      "Lỗi kết nối cơ sở dữ liệu",
      cause,
      correlationId
    );
  }

  public static TechnicalException externalServiceError(String service, Throwable cause, String correlationId) {
    return new TechnicalException(
      "SYS_EXTERNAL_ERROR",
      String.format("Lỗi khi gọi dịch vụ %s", service),
      cause,
      correlationId
    );
  }
}

// Sử dụng trong service
@Service
public class UserService {

  public UserDto getUser(Long id) {
    String correlationId = MDC.get("correlationId"); // Từ filter

    return userRepository.findById(id)
      .map(userMapper::toDto)
      .orElseThrow(() -> BusinessException.notFound("User", id, correlationId));
  }

  public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    String correlationId = MDC.get("correlationId");

    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw BusinessException.invalidInput("amount", "phải lớn hơn 0", correlationId);
    }

    try {
      // Logic transfer
    } catch (SQLException ex) {
      throw TechnicalException.databaseError(ex, correlationId);
    }
  }
}
```

### ❌ Cách sai

```java
// ❌ Dùng generic exception không rõ ràng
public UserDto getUser(Long id) {
  return userRepository.findById(id)
    .orElseThrow(() -> new RuntimeException("User not found")); // Không biết 404 hay 500?
}

// ❌ Throw checked exception vô nghĩa
public void deleteUser(Long id) throws UserNotFoundException, DatabaseException {
  // Caller phải handle 2 checked exceptions
}

// ❌ Exception không chứa error code
public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException(Long id) {
    super("User " + id + " not found"); // Chỉ có message
  }
}

// ❌ Hierarchy phẳng, không phân biệt business/technical
public class InvalidInputException extends RuntimeException {}
public class DatabaseException extends RuntimeException {}
public class ExternalServiceException extends RuntimeException {}
// Handler phải instanceof check từng loại
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm throw generic exception
rg "throw new (RuntimeException|Exception|IllegalArgumentException)\(" --type java

# Tìm throw exception không có error code
rg "throw new \w+Exception\([^,)]+\)" --type java

# Tìm class không extend từ BaseException
rg "class \w+Exception extends (RuntimeException|Exception)" --type java
```

**PMD/Checkstyle rule:**
```xml
<!-- Cấm RuntimeException/Exception trực tiếp -->
<rule ref="category/java/design.xml/AvoidThrowingRawExceptionTypes"/>
```

### Checklist
- [ ] Có base exception với errorCode và correlationId
- [ ] BusinessException cho lỗi 4xx (client error)
- [ ] TechnicalException cho lỗi 5xx (server error)
- [ ] Factory methods cho các lỗi phổ biến
- [ ] Mọi custom exception extend từ base
- [ ] Không throw RuntimeException/Exception trực tiếp

---

## 07.02 - @RestControllerAdvice xử lý tập trung, không try-catch trong controller
**Mức:** 🔴 BẮT BUỘC

### Metadata
- **ID:** `EXC-002`
- **Danh mục:** Exception Handling
- **Độ nghiêm trọng:** HIGH
- **Thời gian sửa:** 20 phút

### Tại sao?
**Vấn đề:**
- Try-catch trong mỗi controller method → code lặp lại
- Inconsistent error response format giữa các endpoint
- Khó maintain khi thay đổi error format
- Quên log exception ở một số chỗ

**Giải pháp:**
- Dùng @RestControllerAdvice để handle tập trung
- @ExceptionHandler cho từng loại exception
- Controller chỉ việc throw, không cần try-catch
- Đảm bảo error response format thống nhất

**Lợi ích:**
- Controller code sạch, tập trung vào business logic
- Single source of truth cho error handling
- Dễ thay đổi error format (chỉ sửa 1 chỗ)
- Tự động log mọi exception

### ✅ Cách đúng

```java
// Global exception handler
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // Handle business exception (4xx)
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
    BusinessException ex,
    HttpServletRequest request
  ) {
    log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());

    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message(ex.getMessage())
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(ex.getCorrelationId())
      .build();

    HttpStatus status = switch (ex.getErrorCode()) {
      case "ERR_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "ERR_FORBIDDEN" -> HttpStatus.FORBIDDEN;
      case "ERR_INVALID_INPUT" -> HttpStatus.BAD_REQUEST;
      default -> HttpStatus.BAD_REQUEST;
    };

    return ResponseEntity.status(status).body(response);
  }

  // Handle technical exception (5xx)
  @ExceptionHandler(TechnicalException.class)
  public ResponseEntity<ErrorResponse> handleTechnicalException(
    TechnicalException ex,
    HttpServletRequest request
  ) {
    log.error("Technical exception: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);

    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message("Lỗi hệ thống, vui lòng thử lại sau") // Generic message cho client
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(ex.getCorrelationId())
      .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  // Handle validation errors
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
    MethodArgumentNotValidException ex,
    HttpServletRequest request
  ) {
    List<String> errors = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .map(err -> err.getField() + ": " + err.getDefaultMessage())
      .toList();

    ErrorResponse response = ErrorResponse.builder()
      .code("ERR_VALIDATION")
      .message("Dữ liệu không hợp lệ")
      .details(errors)
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(MDC.get("correlationId"))
      .build();

    return ResponseEntity.badRequest().body(response);
  }

  // Catch-all handler (fallback)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
    Exception ex,
    HttpServletRequest request
  ) {
    log.error("Unhandled exception", ex);

    ErrorResponse response = ErrorResponse.builder()
      .code("SYS_UNKNOWN_ERROR")
      .message("Lỗi không xác định")
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(MDC.get("correlationId"))
      .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}

// Controller sạch sẽ, không try-catch
@RestController
@RequestMapping("/api/users")
public class UserController {

  @Autowired
  private UserService userService;

  @GetMapping("/{id}")
  public UserDto getUser(@PathVariable Long id) {
    return userService.getUser(id); // Nếu không tìm thấy, service throw BusinessException
    // ControllerAdvice tự động bắt và trả về 404
  }

  @PostMapping
  public UserDto createUser(@Valid @RequestBody CreateUserRequest request) {
    return userService.createUser(request); // Validation error tự động xử lý
  }

  @DeleteMapping("/{id}")
  public void deleteUser(@PathVariable Long id) {
    userService.deleteUser(id); // Forbidden error tự động xử lý
  }
}
```

### ❌ Cách sai

```java
// ❌ Try-catch trong controller (code lặp)
@RestController
public class UserController {

  @GetMapping("/{id}")
  public ResponseEntity<?> getUser(@PathVariable Long id) {
    try {
      UserDto user = userService.getUser(id);
      return ResponseEntity.ok(user);
    } catch (BusinessException ex) {
      return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    } catch (Exception ex) {
      log.error("Error", ex);
      return ResponseEntity.status(500).body(Map.of("error", "Internal error"));
    }
  }

  @PostMapping
  public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
    try {
      UserDto user = userService.createUser(request);
      return ResponseEntity.ok(user);
    } catch (ValidationException ex) { // Lặp lại logic
      return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    } catch (Exception ex) {
      log.error("Error", ex);
      return ResponseEntity.status(500).body(Map.of("error", "Internal error"));
    }
  }
}

// ❌ Không có @RestControllerAdvice
// Mỗi controller tự xử lý → inconsistent error format

// ❌ @ControllerAdvice nhưng không có @ExceptionHandler đầy đủ
@RestControllerAdvice
public class ErrorHandler {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> handle(BusinessException ex) {
    return ResponseEntity.badRequest().body(ex.getMessage()); // Format không đầy đủ
  }
  // Thiếu handler cho TechnicalException, ValidationException, etc.
}
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm try-catch trong controller
rg "class \w+Controller" -A 50 --type java | rg "^\s+try \{"

# Tìm controller không có @RestControllerAdvice
rg "@RestController" --type java --files-without-match "@RestControllerAdvice"

# Tìm ResponseEntity.status trong controller (sign của manual error handling)
rg "ResponseEntity\.status\(\d+\)" --type java
```

**SonarQube rule:**
```
squid:S1181 - Catch Exception or Throwable in Controllers
```

### Checklist
- [ ] Có @RestControllerAdvice với @ExceptionHandler đầy đủ
- [ ] BusinessException → 4xx
- [ ] TechnicalException → 5xx
- [ ] MethodArgumentNotValidException → 400 với field errors
- [ ] Fallback Exception handler → 500
- [ ] Controller không có try-catch (chỉ throw)
- [ ] Error response format thống nhất

---

## 07.03 - Error response format thống nhất (code, message, timestamp, path)
**Mức:** 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `EXC-003`
- **Danh mục:** API Design
- **Độ nghiêm trọng:** MEDIUM
- **Thời gian sửa:** 15 phút

### Tại sao?
**Vấn đề:**
- Frontend khó parse khi mỗi endpoint trả error format khác nhau
- Client không biết error code để hiển thị message tương ứng
- Thiếu timestamp/correlationId → khó debug
- Thiếu path → không biết endpoint nào gây lỗi

**Giải pháp:**
- Định nghĩa ErrorResponse DTO chuẩn
- Mọi @ExceptionHandler đều trả về format này
- Bao gồm: code, message, timestamp, path, correlationId, details (optional)

**Lợi ích:**
- Frontend chỉ cần 1 error parser
- Dễ debug với correlationId + timestamp
- Client có thể map error code → i18n message
- Consistent API design

### ✅ Cách đúng

```java
// Chuẩn error response
@Data
@Builder
public class ErrorResponse {
  private String code;              // ERR_NOT_FOUND, SYS_DB_ERROR
  private String message;           // Human-readable message
  private Instant timestamp;        // Thời điểm lỗi
  private String path;              // API endpoint
  private String correlationId;     // Trace ID

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> details;     // Field errors (validation)

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, Object> metadata; // Extra info (optional)
}

// Sử dụng trong handler
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
    BusinessException ex,
    HttpServletRequest request
  ) {
    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message(ex.getMessage())
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(ex.getCorrelationId())
      .build();

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
    MethodArgumentNotValidException ex,
    HttpServletRequest request
  ) {
    List<String> details = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .map(err -> err.getField() + ": " + err.getDefaultMessage())
      .toList();

    ErrorResponse response = ErrorResponse.builder()
      .code("ERR_VALIDATION")
      .message("Dữ liệu không hợp lệ")
      .details(details) // Chi tiết lỗi từng field
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(MDC.get("correlationId"))
      .build();

    return ResponseEntity.badRequest().body(response);
  }
}

// Frontend dễ parse
// TypeScript interface
interface ErrorResponse {
  code: string;
  message: string;
  timestamp: string;
  path: string;
  correlationId: string;
  details?: string[];
  metadata?: Record<string, any>;
}

// React error handling
try {
  await api.post('/users', data);
} catch (error) {
  const err = error.response.data as ErrorResponse;

  if (err.code === 'ERR_VALIDATION') {
    // Hiển thị field errors
    err.details?.forEach(detail => toast.error(detail));
  } else {
    // Hiển thị generic message
    toast.error(err.message);
  }

  // Log correlationId để support team debug
  console.error('Error ID:', err.correlationId);
}
```

### ❌ Cách sai

```java
// ❌ Inconsistent error format
@ExceptionHandler(BusinessException.class)
public ResponseEntity<?> handle(BusinessException ex) {
  return ResponseEntity.badRequest().body(ex.getMessage()); // Chỉ string
}

@ExceptionHandler(ValidationException.class)
public ResponseEntity<?> handle(ValidationException ex) {
  return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())); // Map
}

@ExceptionHandler(TechnicalException.class)
public ResponseEntity<?> handle(TechnicalException ex) {
  return ResponseEntity.status(500).body(new ErrorDto(ex)); // Custom DTO
}

// ❌ Thiếu thông tin quan trọng
public class ErrorResponse {
  private String message; // Chỉ có message, không có code/timestamp/correlationId
}

// ❌ Trả về nhiều format khác nhau
// Endpoint A: { "error": "message" }
// Endpoint B: { "code": "ERR_001", "msg": "message" }
// Endpoint C: { "status": 400, "message": "message", "timestamp": "..." }

// ❌ Frontend phải handle từng trường hợp
if (typeof error.data === 'string') {
  // Format 1
} else if (error.data.error) {
  // Format 2
} else if (error.data.message) {
  // Format 3
}
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm return ResponseEntity không dùng ErrorResponse
rg "return ResponseEntity\.(badRequest|status)\(\)\.body\(" --type java | rg -v "ErrorResponse"

# Tìm error response DTO khác
rg "class \w+(Error|Exception)(Response|Dto)" --type java

# Tìm Map.of trong exception handler
rg "@ExceptionHandler" -A 5 --type java | rg "Map\.of\("
```

**OpenAPI validation:**
```yaml
# Schema definition phải thống nhất
components:
  schemas:
    ErrorResponse:
      required: [code, message, timestamp, path, correlationId]
      properties:
        code: { type: string }
        message: { type: string }
        timestamp: { type: string, format: date-time }
        path: { type: string }
        correlationId: { type: string }
        details: { type: array, items: { type: string } }
```

### Checklist
- [ ] ErrorResponse DTO có đầy đủ: code, message, timestamp, path, correlationId
- [ ] Mọi @ExceptionHandler dùng chung ErrorResponse
- [ ] Validation error có `details` array
- [ ] @JsonInclude(NON_NULL) cho optional fields
- [ ] OpenAPI spec định nghĩa ErrorResponse
- [ ] Frontend có interface TypeScript tương ứng

---

## 07.04 - Không expose stack trace ra client (production)
**Mức:** 🔴 BẮT BUỘC

### Metadata
- **ID:** `EXC-004`
- **Danh mục:** Security
- **Độ nghiêm trọng:** CRITICAL
- **Thời gian sửa:** 10 phút

### Tại sao?
**Vấn đề:**
- Stack trace lộ thông tin nhạy cảm: class name, file path, library version
- Hacker dùng để recon hệ thống (framework, dependencies)
- Violate security best practices (OWASP Top 10)
- User không hiểu stack trace, chỉ gây confusion

**Giải pháp:**
- Production: trả về generic message + correlationId
- Log đầy đủ stack trace ở server side
- Development: có thể include stack trace (với flag)
- Dùng server.error.include-stacktrace=never

**Lợi ích:**
- Bảo mật thông tin hệ thống
- Client chỉ nhận message có ý nghĩa
- Compliance với security standards
- Debug vẫn dễ dàng qua correlationId

### ✅ Cách đúng

```java
// application.yml - KHÔNG expose stack trace
server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: never          # ✅ NEVER trong production
    include-exception: false           # ✅ Không include exception class name

spring:
  profiles:
    active: ${SPRING_PROFILE:prod}

---
# Development profile - cho phép stacktrace
spring:
  config:
    activate:
      on-profile: dev

server:
  error:
    include-stacktrace: on_param      # Dev: ?trace=true để xem stacktrace
    include-exception: true

// Exception handler - không trả stack trace
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @Value("${spring.profiles.active:prod}")
  private String activeProfile;

  @ExceptionHandler(TechnicalException.class)
  public ResponseEntity<ErrorResponse> handleTechnicalException(
    TechnicalException ex,
    HttpServletRequest request
  ) {
    // ✅ Log đầy đủ stack trace ở server
    log.error("Technical error [{}]: {}", ex.getCorrelationId(), ex.getMessage(), ex);

    // ✅ Client chỉ nhận generic message
    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message("Lỗi hệ thống, vui lòng liên hệ support với mã: " + ex.getCorrelationId())
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(ex.getCorrelationId())
      .build();

    // ✅ Development mode: thêm stack trace
    if ("dev".equals(activeProfile)) {
      response.setMetadata(Map.of(
        "exception", ex.getClass().getName(),
        "stackTrace", Arrays.toString(ex.getStackTrace())
      ));
    }

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  // Fallback handler - catch-all
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
    Exception ex,
    HttpServletRequest request
  ) {
    String correlationId = MDC.get("correlationId");

    // ✅ Log chi tiết
    log.error("Unhandled exception [{}]", correlationId, ex);

    // ✅ Client nhận message chung chung
    ErrorResponse response = ErrorResponse.builder()
      .code("SYS_UNKNOWN_ERROR")
      .message("Lỗi không xác định. Mã tham chiếu: " + correlationId)
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(correlationId)
      .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}

// Custom ErrorAttributes (override Spring Boot default)
@Component
public class CustomErrorAttributes extends DefaultErrorAttributes {

  @Override
  public Map<String, Object> getErrorAttributes(
    WebRequest webRequest,
    ErrorAttributeOptions options
  ) {
    Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, options);

    // ✅ Remove sensitive fields
    errorAttributes.remove("trace");
    errorAttributes.remove("exception");
    errorAttributes.remove("errors"); // Binding errors có thể leak info

    // Add correlationId
    errorAttributes.put("correlationId", MDC.get("correlationId"));

    return errorAttributes;
  }
}
```

### ❌ Cách sai

```java
// ❌ Expose stack trace trong response
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handle(Exception ex) {
  ErrorResponse response = new ErrorResponse();
  response.setMessage(ex.getMessage());
  response.setStackTrace(ex.getStackTrace()); // ❌ Lộ stack trace!
  return ResponseEntity.status(500).body(response);
}

// ❌ application.yml - expose stack trace
server:
  error:
    include-stacktrace: always     # ❌ NGUY HIỂM!
    include-exception: true        # ❌ Lộ exception class name

// ❌ Trả về exception.toString()
@ExceptionHandler(SQLException.class)
public ResponseEntity<?> handle(SQLException ex) {
  return ResponseEntity.status(500).body(Map.of(
    "error", ex.toString() // ❌ "java.sql.SQLException: Connection refused at ..."
  ));
}

// ❌ Không tắt Spring Boot default error page
// /error endpoint trả về full stack trace nếu không custom ErrorAttributes

// ❌ Log stack trace vào response
@ExceptionHandler(Exception.class)
public ResponseEntity<?> handle(Exception ex) {
  StringWriter sw = new StringWriter();
  ex.printStackTrace(new PrintWriter(sw));
  return ResponseEntity.status(500).body(Map.of(
    "error", sw.toString() // ❌ Full stack trace trong JSON!
  ));
}
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm setStackTrace trong code
rg "\.setStackTrace\(|\.getStackTrace\(\)" --type java

# Tìm printStackTrace (red flag!)
rg "\.printStackTrace\(" --type java

# Tìm toString() của exception
rg "exception\.toString\(\)|ex\.toString\(\)" --type java

# Check application.yml
rg "include-stacktrace:\s*(always|on_trace_param)" --type yaml
```

**SonarQube rule:**
```
squid:S1148 - printStackTrace should not be called
squid:S2629 - Exception should not be exposed in error messages
```

**Security scan:**
```bash
# OWASP ZAP - check error response
# Nếu thấy "at java.base/" → stack trace exposed
```

### Checklist
- [ ] application.yml: `include-stacktrace: never`
- [ ] application.yml: `include-exception: false`
- [ ] ErrorResponse không có stackTrace field
- [ ] Log đầy đủ exception ở server (log.error với ex)
- [ ] Client chỉ nhận generic message + correlationId
- [ ] CustomErrorAttributes remove trace/exception
- [ ] Không dùng printStackTrace() anywhere

---

## 07.05 - Log đầy đủ exception gốc (log.error("msg", ex))
**Mức:** 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `EXC-005`
- **Danh mục:** Logging
- **Độ nghiêm trọng:** MEDIUM
- **Thời gian sửa:** 5 phút

### Tại sao?
**Vấn đề:**
- log.error(ex.getMessage()) → mất stack trace, không biết root cause
- Wrap exception mà không log gốc → debugging nightmare
- Log thiếu context (user, request, params) → khó reproduce
- Chỉ log message, không log exception object → log aggregator không parse được

**Giải pháp:**
- LUÔN log exception object: log.error("msg", ex)
- Thêm context: correlationId, userId, requestId
- Log cả input parameters (sanitized)
- Dùng SLF4J placeholder thay vì string concat

**Lợi ích:**
- Full stack trace trong log file
- Log aggregator (ELK, Splunk) parse được exception type
- Dễ trace root cause qua correlationId
- Performance tốt hơn (lazy evaluation)

### ✅ Cách đúng

```java
@Service
@Slf4j
public class UserService {

  public UserDto createUser(CreateUserRequest request) {
    String correlationId = MDC.get("correlationId");
    Long userId = SecurityUtils.getCurrentUserId();

    // ✅ Log với context
    log.info("Creating user: email={}, correlationId={}, actorId={}",
      request.getEmail(), correlationId, userId);

    try {
      // Business logic
      User user = userMapper.toEntity(request);
      user = userRepository.save(user);

      log.info("User created successfully: id={}, correlationId={}",
        user.getId(), correlationId);

      return userMapper.toDto(user);

    } catch (DataIntegrityViolationException ex) {
      // ✅ Log exception object + context
      log.error("Failed to create user: email={}, correlationId={}, reason=duplicate",
        request.getEmail(), correlationId, ex); // ex ở cuối!

      throw BusinessException.invalidInput(
        "email",
        "Email đã tồn tại",
        correlationId
      );

    } catch (Exception ex) {
      // ✅ Log đầy đủ thông tin
      log.error("Unexpected error creating user: email={}, correlationId={}, input={}",
        request.getEmail(),
        correlationId,
        sanitize(request), // Không log password!
        ex
      );

      throw TechnicalException.databaseError(ex, correlationId);
    }
  }

  // Helper: sanitize sensitive data
  private String sanitize(Object obj) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.addMixIn(CreateUserRequest.class, SensitiveDataMixin.class);
      return mapper.writeValueAsString(obj);
    } catch (Exception e) {
      return obj.getClass().getSimpleName();
    }
  }

  // Mixin để mask sensitive fields
  @JsonIgnoreProperties({"password", "ssn", "creditCard"})
  private abstract class SensitiveDataMixin {}
}

// Exception handler - log với context
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(TechnicalException.class)
  public ResponseEntity<ErrorResponse> handleTechnicalException(
    TechnicalException ex,
    HttpServletRequest request
  ) {
    // ✅ Log đầy đủ: correlation ID, path, user, exception
    log.error(
      "Technical error: code={}, path={}, correlationId={}, user={}",
      ex.getErrorCode(),
      request.getRequestURI(),
      ex.getCorrelationId(),
      SecurityUtils.getCurrentUsername(),
      ex  // ✅ Exception object cuối cùng
    );

    // Return response
    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message("Lỗi hệ thống")
      .correlationId(ex.getCorrelationId())
      .build();

    return ResponseEntity.status(500).body(response);
  }
}

// Structured logging (JSON format)
// logback-spring.xml
<configuration>
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdc>true</includeMdc>
      <includeContext>false</includeContext>
      <includeStackTrace>true</includeStackTrace> <!-- ✅ Include stack trace -->
      <fieldNames>
        <timestamp>timestamp</timestamp>
        <message>message</message>
        <stackTrace>stackTrace</stackTrace>
      </fieldNames>
    </encoder>
  </appender>
</configuration>

// Output (ELK-friendly):
{
  "timestamp": "2026-02-16T10:30:00Z",
  "level": "ERROR",
  "message": "Technical error: code=SYS_DB_ERROR, path=/api/users, correlationId=abc-123",
  "correlationId": "abc-123",
  "userId": 456,
  "exception": "jp.medicalbox.exception.TechnicalException",
  "stackTrace": [
    "jp.medicalbox.service.UserService.createUser(UserService.java:45)",
    "..."
  ]
}
```

### ❌ Cách sai

```java
// ❌ Chỉ log message, mất stack trace
try {
  userRepository.save(user);
} catch (Exception ex) {
  log.error(ex.getMessage()); // ❌ Không có stack trace!
  throw ex;
}

// ❌ String concatenation (không dùng placeholder)
log.error("Error for user " + userId + ": " + ex.getMessage(), ex);
// → userId evaluate ngay cả khi ERROR level disabled

// ✅ Dùng placeholder (lazy evaluation)
log.error("Error for user {}: {}", userId, ex.getMessage(), ex);

// ❌ Log exception.toString() thay vì exception object
log.error("Error: " + ex.toString()); // ❌ Mất stack trace

// ❌ Wrap mà không log gốc
try {
  externalService.call();
} catch (IOException ex) {
  // ❌ Throw mới mà không log gốc → mất thông tin
  throw new TechnicalException("External service failed");
}

// ✅ Log gốc trước khi wrap
try {
  externalService.call();
} catch (IOException ex) {
  log.error("External service call failed: service={}", serviceName, ex); // ✅
  throw TechnicalException.externalServiceError(serviceName, ex, correlationId);
}

// ❌ Không log context
log.error("Database error", ex); // ❌ Không biết user nào, request nào

// ✅ Log với context
log.error("Database error: userId={}, action={}, correlationId={}",
  userId, "createUser", correlationId, ex);

// ❌ Log sensitive data
log.error("Login failed: username={}, password={}", username, password, ex); // ❌ Leak password!

// ✅ Không log password
log.error("Login failed: username={}, correlationId={}", username, correlationId, ex);
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm log.error không có exception object
rg "log\.error\([^)]+\);$" --type java  # Chỉ có message, không có ex

# Tìm log.error(ex.getMessage())
rg "log\.error\([^,]*\.getMessage\(\)" --type java

# Tìm string concatenation trong log
rg 'log\.(error|warn|info)\([^)]*\s+\+\s+' --type java

# Tìm log sensitive fields
rg 'log\.(error|info|debug).*password=' --type java -i
```

**PMD rule:**
```xml
<rule ref="category/java/bestpractices.xml/GuardLogStatement"/>
<rule ref="category/java/errorprone.xml/AvoidCatchingThrowable"/>
```

### Checklist
- [ ] log.error("msg", ex) với ex ở cuối
- [ ] Dùng SLF4J placeholder {}, không concat string
- [ ] Log correlationId, userId, requestPath
- [ ] Log input parameters (đã sanitize)
- [ ] Không log password/ssn/credit card
- [ ] Structured logging (JSON) cho production
- [ ] Log aggregator có thể parse exception type

---

## 07.06 - Phân biệt client error (4xx) vs server error (5xx)
**Mức:** 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `EXC-006`
- **Danh mục:** HTTP Design
- **Độ nghiêm trọng:** MEDIUM
- **Thời gian sửa:** 10 phút

### Tại sao?
**Vấn đề:**
- Mọi error đều trả 500 → client không biết có nên retry không
- 4xx nhưng là lỗi server (NPE) → misleading
- Monitoring alert sai (business error trigger alert)
- API consumer không biết ai chịu trách nhiệm fix

**Giải pháp:**
- 4xx (400-499): client sai (bad input, unauthorized, not found) → không nên retry
- 5xx (500-599): server sai (NPE, DB down, timeout) → có thể retry
- BusinessException → 4xx, TechnicalException → 5xx
- Dùng đúng HTTP status cho từng loại error

**Lợi ích:**
- Client biết khi nào retry, khi nào không
- Monitoring chỉ alert 5xx (server issue)
- Compliance với RESTful design
- Dễ debug (phân biệt lỗi logic vs lỗi infrastructure)

### ✅ Cách đúng

```java
// Mapping BusinessException → 4xx
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
    HttpStatus status = switch (ex.getErrorCode()) {
      // ✅ 400 Bad Request - input không hợp lệ
      case "ERR_INVALID_INPUT", "ERR_VALIDATION" -> HttpStatus.BAD_REQUEST;

      // ✅ 401 Unauthorized - chưa login
      case "ERR_UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;

      // ✅ 403 Forbidden - đã login nhưng không có quyền
      case "ERR_FORBIDDEN", "ERR_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;

      // ✅ 404 Not Found - resource không tồn tại
      case "ERR_NOT_FOUND", "ERR_USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;

      // ✅ 409 Conflict - business rule violation (duplicate, constraint)
      case "ERR_DUPLICATE", "ERR_CONFLICT" -> HttpStatus.CONFLICT;

      // ✅ 422 Unprocessable Entity - semantic error
      case "ERR_INSUFFICIENT_BALANCE", "ERR_INVALID_STATE" -> HttpStatus.UNPROCESSABLE_ENTITY;

      // ✅ 429 Too Many Requests - rate limit
      case "ERR_RATE_LIMIT" -> HttpStatus.TOO_MANY_REQUESTS;

      default -> HttpStatus.BAD_REQUEST;
    };

    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message(ex.getMessage())
      .correlationId(ex.getCorrelationId())
      .build();

    return ResponseEntity.status(status).body(response);
  }

  // Mapping TechnicalException → 5xx
  @ExceptionHandler(TechnicalException.class)
  public ResponseEntity<ErrorResponse> handleTechnicalException(TechnicalException ex) {
    HttpStatus status = switch (ex.getErrorCode()) {
      // ✅ 500 Internal Server Error - generic server error
      case "SYS_UNKNOWN_ERROR", "SYS_NPE" -> HttpStatus.INTERNAL_SERVER_ERROR;

      // ✅ 502 Bad Gateway - external service error
      case "SYS_EXTERNAL_ERROR", "SYS_API_ERROR" -> HttpStatus.BAD_GATEWAY;

      // ✅ 503 Service Unavailable - DB down, cache down
      case "SYS_DB_ERROR", "SYS_CACHE_ERROR" -> HttpStatus.SERVICE_UNAVAILABLE;

      // ✅ 504 Gateway Timeout - external service timeout
      case "SYS_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;

      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };

    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message("Lỗi hệ thống")
      .correlationId(ex.getCorrelationId())
      .build();

    return ResponseEntity.status(status).body(response);
  }

  // Validation error → 400
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    // ✅ 400 Bad Request
    return ResponseEntity.badRequest().body(/* ... */);
  }

  // Access denied → 403
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    // ✅ 403 Forbidden
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(/* ... */);
  }
}

// Service - throw đúng exception type
@Service
public class PaymentService {

  public void transfer(Long fromId, Long toId, BigDecimal amount) {
    String correlationId = MDC.get("correlationId");

    Account from = accountRepository.findById(fromId)
      .orElseThrow(() -> BusinessException.notFound("Account", fromId, correlationId)); // ✅ 404

    if (from.getBalance().compareTo(amount) < 0) {
      // ✅ 422 Unprocessable Entity - business rule
      throw new BusinessException(
        "ERR_INSUFFICIENT_BALANCE",
        "Số dư không đủ",
        correlationId
      );
    }

    try {
      // Transfer logic
    } catch (SQLException ex) {
      // ✅ 503 Service Unavailable - DB issue
      throw TechnicalException.databaseError(ex, correlationId);
    }
  }
}

// HTTP client - retry strategy based on status code
@Service
public class ExternalApiClient {

  public String callExternalApi() {
    try {
      return restTemplate.getForObject(url, String.class);
    } catch (HttpClientErrorException ex) {
      // ✅ 4xx - client error, không retry
      log.warn("Client error from external API: {}", ex.getStatusCode());
      throw BusinessException.invalidInput("request", "External API rejected", correlationId);

    } catch (HttpServerErrorException ex) {
      // ✅ 5xx - server error, có thể retry
      log.error("Server error from external API: {}", ex.getStatusCode(), ex);
      throw TechnicalException.externalServiceError("ExternalAPI", ex, correlationId);
    }
  }
}
```

### ❌ Cách sai

```java
// ❌ Mọi error đều 500
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
  return ResponseEntity.status(500).body(/* ... */); // ❌ Validation error cũng 500?
}

// ❌ Business error nhưng trả 500
public UserDto getUser(Long id) {
  return userRepository.findById(id)
    .orElseThrow(() -> new RuntimeException("Not found")); // ❌ 500 thay vì 404
}

// ❌ Server error nhưng trả 400
try {
  userRepository.save(user);
} catch (SQLException ex) {
  throw new IllegalArgumentException("Save failed"); // ❌ 400 thay vì 503
}

// ❌ Không phân biệt 401 vs 403
if (!isAuthenticated) {
  throw new SecurityException("Access denied"); // ❌ 401 hay 403?
}

// ✅ Phân biệt rõ ràng
if (!isAuthenticated) {
  throw new BusinessException("ERR_UNAUTHORIZED", "Chưa đăng nhập", correlationId); // 401
}
if (!hasPermission) {
  throw new BusinessException("ERR_FORBIDDEN", "Không có quyền", correlationId); // 403
}

// ❌ Duplicate error nhưng trả 400
if (userRepository.existsByEmail(email)) {
  throw new IllegalArgumentException("Email exists"); // ❌ Nên 409 Conflict
}

// ✅ 409 Conflict
if (userRepository.existsByEmail(email)) {
  throw new BusinessException("ERR_DUPLICATE", "Email đã tồn tại", correlationId); // 409
}
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm ResponseEntity.status(500) trong handler
rg "ResponseEntity\.status\(500|INTERNAL_SERVER_ERROR\)" --type java

# Tìm throw RuntimeException (không rõ 4xx hay 5xx)
rg "throw new RuntimeException" --type java

# Tìm IllegalArgumentException (thường bị dùng sai cho business error)
rg "throw new IllegalArgumentException" --type java
```

**HTTP test:**
```java
@Test
void testNotFound_Returns404() {
  mockMvc.perform(get("/api/users/999"))
    .andExpect(status().isNotFound())  // ✅ 404, không phải 500
    .andExpect(jsonPath("$.code").value("ERR_NOT_FOUND"));
}

@Test
void testDatabaseError_Returns503() {
  when(userRepository.save(any())).thenThrow(new SQLException());

  mockMvc.perform(post("/api/users").content("{}"))
    .andExpect(status().isServiceUnavailable())  // ✅ 503
    .andExpect(jsonPath("$.code").value("SYS_DB_ERROR"));
}
```

### Checklist
- [ ] BusinessException → 4xx (400, 401, 403, 404, 409, 422)
- [ ] TechnicalException → 5xx (500, 502, 503, 504)
- [ ] Validation error → 400
- [ ] Authentication error → 401
- [ ] Authorization error → 403
- [ ] Not found → 404
- [ ] Conflict/duplicate → 409
- [ ] Database error → 503

---

## 07.07 - Không dùng exception cho control flow
**Mức:** 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `EXC-007`
- **Danh mục:** Performance
- **Độ nghiêm trọng:** MEDIUM
- **Thời gian sửa:** 15 phút

### Tại sao?
**Vấn đề:**
- Exception rất chậm (tạo stack trace ~ 1000x chậm hơn return)
- Dùng exception cho flow thông thường → performance hit
- Code khó đọc, khó maintain
- Exception nên dành cho exceptional case, không phải expected case

**Giải pháp:**
- Dùng Optional, Result type cho expected case
- Exception chỉ cho unexpected error
- Validation trả về error list, không throw
- Check trước khi thực hiện thay vì catch sau

**Lợi ích:**
- Performance tốt hơn (đặc biệt hot path)
- Code rõ ràng hơn (return type thể hiện có thể fail)
- Dễ test (không cần expectThrows)
- Functional programming style

### ✅ Cách đúng

```java
// ✅ Dùng Optional cho expected case (không tìm thấy)
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);
}

@Service
public class UserService {

  // ✅ Return Optional, không throw exception
  public Optional<UserDto> findByEmail(String email) {
    return userRepository.findByEmail(email)
      .map(userMapper::toDto);
  }

  // Client tự quyết định xử lý
  public void example() {
    userService.findByEmail("test@example.com")
      .ifPresentOrElse(
        user -> log.info("Found: {}", user),
        () -> log.info("Not found")
      );
  }
}

// ✅ Dùng Result type cho operation có thể fail
public sealed interface Result<T> permits Success, Failure {
  record Success<T>(T value) implements Result<T> {}
  record Failure<T>(String error, String errorCode) implements Result<T> {}

  static <T> Result<T> success(T value) {
    return new Success<>(value);
  }

  static <T> Result<T> failure(String error, String errorCode) {
    return new Failure<>(error, errorCode);
  }
}

@Service
public class PaymentService {

  // ✅ Return Result thay vì throw exception
  public Result<Payment> processPayment(PaymentRequest request) {
    // Validation
    if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
      return Result.failure("Amount phải lớn hơn 0", "ERR_INVALID_AMOUNT");
    }

    Account account = accountRepository.findById(request.accountId())
      .orElse(null);

    if (account == null) {
      return Result.failure("Account không tồn tại", "ERR_ACCOUNT_NOT_FOUND");
    }

    if (account.getBalance().compareTo(request.amount()) < 0) {
      return Result.failure("Số dư không đủ", "ERR_INSUFFICIENT_BALANCE");
    }

    // Process
    Payment payment = new Payment();
    // ...

    return Result.success(payment);
  }

  // Client xử lý result
  public void example() {
    Result<Payment> result = paymentService.processPayment(request);

    switch (result) {
      case Success<Payment> success -> {
        log.info("Payment successful: {}", success.value());
      }
      case Failure<Payment> failure -> {
        log.warn("Payment failed: {} ({})", failure.error(), failure.errorCode());
      }
    }
  }
}

// ✅ Validation trả về list errors, không throw
public record ValidationResult(boolean isValid, List<String> errors) {
  public static ValidationResult valid() {
    return new ValidationResult(true, List.of());
  }

  public static ValidationResult invalid(List<String> errors) {
    return new ValidationResult(false, errors);
  }
}

public class UserValidator {

  public ValidationResult validate(CreateUserRequest request) {
    List<String> errors = new ArrayList<>();

    if (request.email() == null || !request.email().contains("@")) {
      errors.add("Email không hợp lệ");
    }

    if (request.password() == null || request.password().length() < 8) {
      errors.add("Password phải ít nhất 8 ký tự");
    }

    return errors.isEmpty()
      ? ValidationResult.valid()
      : ValidationResult.invalid(errors);
  }
}

// ✅ Check trước, không catch sau
public void deleteUser(Long id) {
  // ✅ Check exists trước
  if (!userRepository.existsById(id)) {
    throw BusinessException.notFound("User", id, correlationId);
  }

  userRepository.deleteById(id); // Không cần try-catch
}

// ❌ KHÔNG dùng exception cho control flow
// Bad example
public User findUserOrDefault(String email) {
  try {
    return userRepository.findByEmail(email)
      .orElseThrow(); // ❌ Throw exception cho flow bình thường
  } catch (NoSuchElementException ex) {
    return createDefaultUser(); // ❌ Catch để làm flow
  }
}

// ✅ Good example
public User findUserOrDefault(String email) {
  return userRepository.findByEmail(email)
    .orElseGet(this::createDefaultUser); // ✅ Dùng Optional.orElseGet
}
```

### ❌ Cách sai

```java
// ❌ Dùng exception cho control flow
public boolean isUserActive(Long id) {
  try {
    User user = userRepository.findById(id).orElseThrow();
    return user.isActive();
  } catch (NoSuchElementException ex) {
    return false; // ❌ Exception như if-else
  }
}

// ✅ Dùng Optional
public boolean isUserActive(Long id) {
  return userRepository.findById(id)
    .map(User::isActive)
    .orElse(false);
}

// ❌ Validation bằng exception
public void validateAge(int age) {
  if (age < 0) {
    throw new IllegalArgumentException("Age < 0"); // ❌ Expected case
  }
  if (age > 150) {
    throw new IllegalArgumentException("Age > 150");
  }
}

// Caller phải try-catch mọi lúc
try {
  validateAge(input);
} catch (IllegalArgumentException ex) {
  // Handle
}

// ✅ Validation trả về result
public ValidationResult validateAge(int age) {
  if (age < 0) return ValidationResult.invalid(List.of("Age < 0"));
  if (age > 150) return ValidationResult.invalid(List.of("Age > 150"));
  return ValidationResult.valid();
}

// ❌ Catch để loop (worst practice!)
int i = 0;
try {
  while (true) {
    processItem(items[i++]);
  }
} catch (ArrayIndexOutOfBoundsException ex) {
  // ❌ Dùng exception để break loop!
}

// ✅ Dùng loop bình thường
for (Item item : items) {
  processItem(item);
}

// ❌ NumberFormatException cho control flow
public int parseOrDefault(String str) {
  try {
    return Integer.parseInt(str);
  } catch (NumberFormatException ex) {
    return 0; // ❌ Expected case nhưng dùng exception
  }
}

// ✅ Dùng utility method
public int parseOrDefault(String str) {
  return NumberUtils.toInt(str, 0); // Apache Commons
}
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm catch block trống hoặc return value (sign của control flow)
rg "catch.*\{[\s\n]*return" --type java -A 2

# Tìm orElseThrow() trong hot path
rg "\.orElseThrow\(\)" --type java

# Tìm try-catch trong loop
rg "while.*\{" -A 10 --type java | rg "catch"
```

**Performance profiling:**
```java
// Benchmark: Optional vs Exception
@Benchmark
public User testOptional() {
  return userRepository.findByEmail("test@example.com")
    .orElse(null); // ~10ns
}

@Benchmark
public User testException() {
  try {
    return userRepository.findByEmail("test@example.com")
      .orElseThrow(); // ~10000ns (1000x chậm hơn!)
  } catch (NoSuchElementException ex) {
    return null;
  }
}
```

### Checklist
- [ ] Optional cho "not found" case
- [ ] Result type cho operation có thể fail
- [ ] Validation trả về errors list, không throw
- [ ] Không catch exception để làm if-else
- [ ] Không dùng exception để break loop
- [ ] NumberFormatException → dùng tryParse utility
- [ ] Check exists trước khi delete/update

---

## 07.08 - Checked exception chỉ khi caller có thể xử lý
**Mức:** 🟡 NÊN CÓ

### Metadata
- **ID:** `EXC-008`
- **Danh mục:** Exception Design
- **Độ nghiêm trọng:** LOW
- **Thời gian sửa:** 10 phút

### Tại sao?
**Vấn đề:**
- Checked exception (throws IOException) force caller phải handle
- Nhưng nếu caller không thể làm gì → just rethrow → boilerplate code
- Spring/modern Java prefer unchecked exception (RuntimeException)
- Checked exception gây API pollution (method signature dài)

**Giải pháp:**
- Checked exception chỉ khi caller có thể recovery (file not found → chọn file khác)
- Unchecked exception cho lỗi không thể recovery (DB down → retry/escalate)
- Wrap checked exception thành unchecked nếu cần
- Modern practice: prefer unchecked

**Lợi ích:**
- API sạch hơn (không có throws ...)
- Code ngắn hơn (không cần try-catch everywhere)
- Flexibility hơn (caller quyết định có catch không)
- Align với Spring Boot convention

### ✅ Cách đúng

```java
// ✅ Unchecked exception (RuntimeException) - default choice
public class BusinessException extends RuntimeException {
  // Caller không bắt buộc phải catch
}

@Service
public class UserService {

  // ✅ Không throws, caller tự quyết định catch
  public UserDto createUser(CreateUserRequest request) {
    // Có thể throw BusinessException, TechnicalException
    // Caller không bắt buộc phải try-catch
  }
}

// Controller không cần try-catch
@PostMapping
public UserDto createUser(@RequestBody CreateUserRequest request) {
  return userService.createUser(request); // Clean!
  // @RestControllerAdvice tự động catch
}

// ✅ Wrap checked exception thành unchecked
@Service
public class FileService {

  public String readFile(String path) {
    try {
      return Files.readString(Path.of(path));
    } catch (IOException ex) {
      // ✅ Wrap thành unchecked
      throw new TechnicalException(
        "SYS_FILE_ERROR",
        "Không thể đọc file: " + path,
        ex,
        MDC.get("correlationId")
      );
    }
  }
}

// ✅ Checked exception CHỈ KHI caller có thể xử lý
public class FileUploadService {

  // ✅ Checked exception hợp lý: caller có thể chọn file khác
  public void uploadFile(MultipartFile file) throws FileTypeNotSupportedException {
    String extension = getExtension(file.getOriginalFilename());

    if (!ALLOWED_TYPES.contains(extension)) {
      throw new FileTypeNotSupportedException(extension); // Caller có thể retry với file khác
    }

    // Upload logic
  }
}

// Controller xử lý được
@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam MultipartFile file) {
  try {
    fileUploadService.uploadFile(file);
    return ResponseEntity.ok("Success");
  } catch (FileTypeNotSupportedException ex) {
    // ✅ Caller có thể handle: suggest allowed types
    return ResponseEntity.badRequest().body(Map.of(
      "error", "File type not supported",
      "allowedTypes", ALLOWED_TYPES
    ));
  }
}

// ✅ Functional approach (Either/Try)
public sealed interface Try<T> permits Success, Failure {
  record Success<T>(T value) implements Try<T> {}
  record Failure<T>(Throwable error) implements Try<T> {}

  static <T> Try<T> of(Supplier<T> supplier) {
    try {
      return new Success<>(supplier.get());
    } catch (Exception ex) {
      return new Failure<>(ex);
    }
  }
}

// Không cần throws, không cần try-catch
public Try<String> readFile(String path) {
  return Try.of(() -> Files.readString(Path.of(path)));
}

// Caller xử lý functional
readFile("/tmp/data.txt")
  .map(String::toUpperCase)
  .ifSuccess(content -> log.info("Content: {}", content))
  .ifFailure(error -> log.error("Failed to read", error));
```

### ❌ Cách sai

```java
// ❌ Checked exception khi caller không thể xử lý
public interface UserRepository {
  User findById(Long id) throws UserNotFoundException; // ❌ Checked exception vô nghĩa
}

// Caller bắt buộc try-catch
@Service
public class UserService {
  public UserDto getUser(Long id) throws UserNotFoundException { // ❌ Propagate
    User user = userRepository.findById(id); // ❌ Phải try-catch
    return userMapper.toDto(user);
  }
}

// Controller cũng phải try-catch hoặc throws
@GetMapping("/{id}")
public UserDto getUser(@PathVariable Long id) throws UserNotFoundException { // ❌ Ugly
  return userService.getUser(id);
}

// ✅ Nên dùng unchecked
public interface UserRepository {
  Optional<User> findById(Long id); // ✅ Không throws
}

// ❌ Multiple checked exceptions
public void processFile(String path)
  throws IOException, SQLException, JsonProcessingException { // ❌ API pollution

  String content = Files.readString(Path.of(path));
  Data data = objectMapper.readValue(content, Data.class);
  database.save(data);
}

// Caller phải catch 3 loại
try {
  processFile(path);
} catch (IOException ex) {
  // Handle
} catch (SQLException ex) {
  // Handle
} catch (JsonProcessingException ex) {
  // Handle
}

// ✅ Wrap thành 1 unchecked exception
public void processFile(String path) {
  try {
    String content = Files.readString(Path.of(path));
    Data data = objectMapper.readValue(content, Data.class);
    database.save(data);
  } catch (IOException | SQLException | JsonProcessingException ex) {
    throw new TechnicalException("SYS_FILE_PROCESS_ERROR", "Failed to process file", ex, correlationId);
  }
}

// ❌ Catch checked rồi throw unchecked không có cause
try {
  externalApi.call();
} catch (IOException ex) {
  throw new TechnicalException("API call failed"); // ❌ Mất stack trace gốc!
}

// ✅ Preserve cause
try {
  externalApi.call();
} catch (IOException ex) {
  throw new TechnicalException("SYS_API_ERROR", "API call failed", ex, correlationId);
}
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm method throws checked exception
rg "throws \w+(Exception|Error)" --type java | rg -v "RuntimeException"

# Tìm catch block empty rethrow
rg "catch.*Exception.*\{[\s\n]*throw" --type java -A 2

# Tìm method signature với nhiều throws
rg "throws (\w+Exception,\s*){2,}" --type java
```

**SonarQube rule:**
```
squid:S1130 - throws declaration should not be superfluous
squid:S112 - Generic exceptions should never be thrown
```

### Checklist
- [ ] Default: dùng unchecked exception (RuntimeException)
- [ ] Checked exception chỉ khi caller có thể recovery
- [ ] Wrap checked exception (IOException, SQLException) thành unchecked
- [ ] Không throws nhiều checked exceptions
- [ ] Preserve original exception khi wrap (cause)
- [ ] Repository/Service không throws checked exception
- [ ] Align với Spring Boot convention (unchecked)

---

## 07.09 - Correlation ID trong mỗi error response
**Mức:** 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `EXC-009`
- **Danh mục:** Observability
- **Độ nghiêm trọng:** MEDIUM
- **Thời gian sửa:** 20 phút

### Tại sao?
**Vấn đề:**
- User báo lỗi: "Tôi gặp lỗi lúc 10h sáng" → không đủ thông tin để tìm log
- Nhiều request cùng lúc → không biết log nào của request nào
- Microservices: trace request qua nhiều service
- Support team mất nhiều thời gian để match user complaint → log

**Giải pháp:**
- Mỗi request có unique correlation ID (UUID)
- Log mọi thứ với correlation ID
- Error response include correlation ID
- User/support team dùng ID này để search log

**Lợi ích:**
- Trace request từ đầu đến cuối (cross-service)
- Search log dễ dàng: grep correlationId
- User có thể provide ID khi báo lỗi
- Distributed tracing foundation

### ✅ Cách đúng

```java
// 1. Filter tạo correlation ID cho mỗi request
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  private static final String CORRELATION_ID_MDC_KEY = "correlationId";

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    try {
      // ✅ Lấy từ header (nếu có) hoặc tạo mới
      String correlationId = request.getHeader(CORRELATION_ID_HEADER);
      if (correlationId == null || correlationId.isBlank()) {
        correlationId = UUID.randomUUID().toString();
      }

      // ✅ Lưu vào MDC (Mapped Diagnostic Context)
      MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

      // ✅ Trả về response header
      response.setHeader(CORRELATION_ID_HEADER, correlationId);

      filterChain.doFilter(request, response);

    } finally {
      // ✅ Clear MDC sau request
      MDC.clear();
    }
  }
}

// 2. Exception include correlation ID
public abstract class BaseException extends RuntimeException {
  private final String errorCode;
  private final String correlationId;

  protected BaseException(String errorCode, String message, String correlationId) {
    super(message);
    this.errorCode = errorCode;
    this.correlationId = correlationId != null ? correlationId : MDC.get("correlationId");
  }

  public String getCorrelationId() {
    return correlationId;
  }
}

// 3. Error response include correlation ID
@Data
@Builder
public class ErrorResponse {
  private String code;
  private String message;
  private Instant timestamp;
  private String path;

  @NotNull
  private String correlationId; // ✅ MANDATORY field

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> details;
}

// 4. Exception handler populate correlation ID
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
    BusinessException ex,
    HttpServletRequest request
  ) {
    String correlationId = ex.getCorrelationId();

    // ✅ Log với correlation ID (MDC tự động thêm vào log)
    log.warn("Business error [{}]: {} - {}",
      correlationId, ex.getErrorCode(), ex.getMessage());

    ErrorResponse response = ErrorResponse.builder()
      .code(ex.getErrorCode())
      .message(ex.getMessage())
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(correlationId) // ✅ Include in response
      .build();

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
    Exception ex,
    HttpServletRequest request
  ) {
    String correlationId = MDC.get("correlationId");

    log.error("Unhandled exception [{}]", correlationId, ex);

    ErrorResponse response = ErrorResponse.builder()
      .code("SYS_UNKNOWN_ERROR")
      .message("Lỗi hệ thống. Vui lòng liên hệ support với mã: " + correlationId)
      .timestamp(Instant.now())
      .path(request.getRequestURI())
      .correlationId(correlationId)
      .build();

    return ResponseEntity.status(500).body(response);
  }
}

// 5. Logback config - auto include correlation ID
// logback-spring.xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>
        %d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{correlationId}] %logger{36} - %msg%n
      </pattern>
    </encoder>
  </appender>

  <!-- JSON format cho production -->
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>correlationId</includeMdcKeyName>
    </encoder>
  </appender>
</configuration>

// Log output:
// 2026-02-16 10:30:00 [http-nio-8080-exec-1] ERROR [abc-123-def-456] c.e.s.UserService - User not found

// 6. RestTemplate propagate correlation ID
@Configuration
public class RestTemplateConfig {

  @Bean
  public RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate();

    // ✅ Interceptor để forward correlation ID
    restTemplate.getInterceptors().add((request, body, execution) -> {
      String correlationId = MDC.get("correlationId");
      if (correlationId != null) {
        request.getHeaders().add("X-Correlation-ID", correlationId);
      }
      return execution.execute(request, body);
    });

    return restTemplate;
  }
}

// 7. Async method preserve correlation ID
@Service
public class NotificationService {

  @Async
  public void sendEmail(String to, String subject) {
    // ✅ Async method mất MDC context → phải truyền manual
    String correlationId = MDC.get("correlationId");

    CompletableFuture.runAsync(() -> {
      // ✅ Set lại MDC trong async thread
      MDC.put("correlationId", correlationId);

      try {
        emailService.send(to, subject);
        log.info("Email sent to {}", to); // Log có correlationId
      } finally {
        MDC.clear();
      }
    });
  }
}

// 8. Frontend display correlation ID
// React error handler
try {
  await api.post('/users', data);
} catch (error) {
  const err = error.response.data as ErrorResponse;

  toast.error(
    `${err.message}\n\nMã lỗi: ${err.correlationId}\n` +
    `Vui lòng cung cấp mã này khi liên hệ support.`
  );

  // Copy to clipboard
  navigator.clipboard.writeText(err.correlationId);
}
```

### ❌ Cách sai

```java
// ❌ Không có correlation ID
@RestControllerAdvice
public class ErrorHandler {
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handle(Exception ex) {
    ErrorResponse response = new ErrorResponse();
    response.setMessage(ex.getMessage());
    // ❌ Không có correlationId → không trace được
    return ResponseEntity.status(500).body(response);
  }
}

// ❌ Correlation ID không consistent
// Request 1: UUID
// Request 2: timestamp
// Request 3: random number
// → Không thể search log

// ❌ Không log correlation ID
log.error("User creation failed"); // ❌ Không biết request nào

// ✅ Log với correlation ID
log.error("User creation failed: correlationId={}", MDC.get("correlationId"));

// ❌ Async method mất correlation ID
@Async
public void processAsync() {
  log.info("Processing..."); // ❌ correlationId = null (MDC lost)
}

// ✅ Truyền correlation ID vào async
@Async
public void processAsync(String correlationId) {
  MDC.put("correlationId", correlationId);
  try {
    log.info("Processing..."); // ✅ correlationId preserved
  } finally {
    MDC.clear();
  }
}

// ❌ RestTemplate không forward correlation ID
// Service A gọi Service B → Service B không có correlation ID
// → Không trace được cross-service

// ❌ Error message không mention correlation ID
return ResponseEntity.status(500).body(
  ErrorResponse.builder()
    .message("Lỗi hệ thống") // ❌ User không biết phải làm gì
    .correlationId(correlationId)
    .build()
);

// ✅ Hướng dẫn user dùng correlation ID
return ResponseEntity.status(500).body(
  ErrorResponse.builder()
    .message("Lỗi hệ thống. Vui lòng liên hệ support với mã: " + correlationId)
    .correlationId(correlationId)
    .build()
);
```

### Phát hiện

**Grep pattern:**
```bash
# Tìm ErrorResponse không có correlationId
rg "class.*ErrorResponse" -A 10 --type java | rg -v "correlationId"

# Tìm log.error không có correlationId
rg "log\.error\(" --type java | rg -v "correlationId"

# Tìm @Async method không preserve MDC
rg "@Async" -A 10 --type java | rg -v "MDC"
```

**Runtime check:**
```java
@Test
void errorResponse_shouldHaveCorrelationId() {
  mockMvc.perform(get("/api/users/999"))
    .andExpect(status().isNotFound())
    .andExpect(jsonPath("$.correlationId").exists())
    .andExpect(jsonPath("$.correlationId").isNotEmpty());
}

@Test
void log_shouldIncludeCorrelationId() {
  // Check log output contains correlation ID
  assertTrue(logOutput.contains("[abc-123-def]"));
}
```

### Checklist
- [ ] Filter tạo correlation ID cho mỗi request
- [ ] Lưu correlation ID vào MDC
- [ ] ErrorResponse có correlationId field (mandatory)
- [ ] Logback pattern include %X{correlationId}
- [ ] RestTemplate interceptor forward correlation ID
- [ ] @Async method preserve correlation ID
- [ ] Error message hướng dẫn user dùng correlation ID
- [ ] Frontend hiển thị correlation ID khi lỗi

---

## Tổng kết

### Mức độ ưu tiên
1. **🔴 BẮT BUỘC (3):**
   - 07.01: Custom exception hierarchy
   - 07.02: @RestControllerAdvice xử lý tập trung
   - 07.04: Không expose stack trace

2. **🟠 KHUYẾN NGHỊ (5):**
   - 07.03: Error response format thống nhất
   - 07.05: Log đầy đủ exception gốc
   - 07.06: Phân biệt 4xx vs 5xx
   - 07.07: Không dùng exception cho control flow
   - 07.09: Correlation ID

3. **🟡 NÊN CÓ (1):**
   - 07.08: Checked exception chỉ khi caller có thể xử lý

### Quick checklist
```bash
# Exception hierarchy
grep -r "extends RuntimeException" --include="*.java"  # Nên extend từ BaseException

# RestControllerAdvice
grep -r "@RestControllerAdvice" --include="*.java"  # Phải có

# Stack trace
grep -r "include-stacktrace" application.yml  # Phải = never

# Error format
grep -r "class.*ErrorResponse" --include="*.java"  # Check có đầy đủ fields

# Correlation ID
grep -r "correlationId" --include="*.java"  # Phải có trong ErrorResponse
```

### Anti-patterns cần tránh
- ❌ throw new RuntimeException("message")
- ❌ Try-catch trong controller
- ❌ include-stacktrace: always
- ❌ Mọi error đều 500
- ❌ Catch exception để làm if-else
- ❌ Method throws 3+ checked exceptions
- ❌ Error response không có correlationId
