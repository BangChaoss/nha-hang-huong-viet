# Domain 03: REST API & Controller
> **Số practices:** 10 | 🔴 4 | 🟠 4 | 🟡 2
> **Trọng số:** ×1

---

## 03.01 — Controller chỉ xử lý HTTP, delegate logic cho Service

### Metadata
- **Mã số:** 03.01
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `controller`, `separation-of-concerns`, `thin-controller`

### Tại sao?
Controller không nên chứa business logic. Nhiệm vụ của Controller là nhận HTTP request, validate input, gọi Service layer, và trả về HTTP response. Khi Controller chứa business logic, code trở nên khó test (phải mock HTTP context), khó tái sử dụng (logic bị gắn chặt với web layer), và vi phạm Single Responsibility Principle. Service layer mới là nơi chứa business logic, có thể được gọi từ nhiều nguồn (REST API, GraphQL, scheduled jobs, message consumers).

### ✅ Cách đúng
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public ResponseEntity<UserResponse> createUser(
      @Valid @RequestBody CreateUserRequest request) {
    // Controller chỉ validate, delegate, và map response
    User user = userService.createUser(request);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(UserResponse.from(user));
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    return ResponseEntity.ok(UserResponse.from(user));
  }

  @PutMapping("/{id}/activate")
  public ResponseEntity<Void> activateUser(@PathVariable Long id) {
    // Business logic trong Service
    userService.activateUser(id);
    return ResponseEntity.noContent().build();
  }
}

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final EmailService emailService;

  @Transactional
  public User createUser(CreateUserRequest request) {
    // Business logic ở đây
    validateEmailUnique(request.email());

    User user = User.builder()
        .email(request.email())
        .name(request.name())
        .status(UserStatus.PENDING)
        .build();

    User saved = userRepository.save(user);
    emailService.sendWelcomeEmail(saved);

    return saved;
  }

  @Transactional
  public void activateUser(Long id) {
    User user = findById(id);
    if (user.getStatus() == UserStatus.ACTIVE) {
      throw new BusinessException("User already active");
    }
    user.setStatus(UserStatus.ACTIVE);
    userRepository.save(user);
    emailService.sendActivationEmail(user);
  }

  private void validateEmailUnique(String email) {
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException("Email already exists");
    }
  }
}
```

### ❌ Cách sai
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserRepository userRepository;
  private final EmailService emailService;

  @PostMapping
  public ResponseEntity<UserResponse> createUser(
      @Valid @RequestBody CreateUserRequest request) {
    // ❌ Business logic trong Controller
    if (userRepository.existsByEmail(request.email())) {
      throw new BusinessException("Email already exists");
    }

    User user = User.builder()
        .email(request.email())
        .name(request.name())
        .status(UserStatus.PENDING)
        .build();

    User saved = userRepository.save(user);
    emailService.sendWelcomeEmail(saved);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(UserResponse.from(saved));
  }
}
```

### Phát hiện
```regex
# Controller inject Repository (thường là bad practice)
@RestController[\s\S]{0,200}@Autowired[\s\S]{0,50}Repository

# Controller có @Transactional
@RestController[\s\S]{0,500}@Transactional

# Controller có logic phức tạp (nhiều if/for trong method)
@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)[\s\S]{0,100}\{[\s\S]{0,500}(if|for|while)[\s\S]{0,500}(if|for|while)
```

### Checklist
- [ ] Controller chỉ có dependency injection cho Service, không inject Repository
- [ ] Không có `@Transactional` trong Controller
- [ ] Controller method < 15 dòng (chỉ validate, delegate, map response)
- [ ] Business logic (validation, calculation, state change) nằm trong Service
- [ ] Controller có thể test dễ dàng với MockMvc mà không cần database

---

## 03.02 — Sử dụng ResponseEntity<> với HTTP status chính xác

### Metadata
- **Mã số:** 03.02
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `http-status`, `response-entity`, `rest-semantics`

### Tại sao?
HTTP status code là cách REST API giao tiếp với client về kết quả request. Sử dụng đúng status code giúp client xử lý response đúng cách (200 OK, 201 Created, 204 No Content, 400 Bad Request, 404 Not Found, 409 Conflict). ResponseEntity cho phép kiểm soát đầy đủ HTTP response (status, headers, body). Trả về đúng status code cải thiện API usability và tuân thủ REST standards.

### ✅ Cách đúng
```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;

  // 201 Created cho resource mới
  @PostMapping
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request) {
    Product product = productService.createProduct(request);
    URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(product.getId())
        .toUri();

    return ResponseEntity
        .created(location)
        .body(ProductResponse.from(product));
  }

  // 200 OK cho successful GET
  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
    Product product = productService.findById(id);
    return ResponseEntity.ok(ProductResponse.from(product));
  }

  // 204 No Content cho successful DELETE
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
    productService.deleteProduct(id);
    return ResponseEntity.noContent().build();
  }

  // 200 OK với body cho PUT
  @PutMapping("/{id}")
  public ResponseEntity<ProductResponse> updateProduct(
      @PathVariable Long id,
      @Valid @RequestBody UpdateProductRequest request) {
    Product product = productService.updateProduct(id, request);
    return ResponseEntity.ok(ProductResponse.from(product));
  }

  // 202 Accepted cho async processing
  @PostMapping("/{id}/publish")
  public ResponseEntity<AsyncTaskResponse> publishProduct(@PathVariable Long id) {
    String taskId = productService.publishProductAsync(id);
    return ResponseEntity
        .accepted()
        .body(new AsyncTaskResponse(taskId, "Processing"));
  }

  // 304 Not Modified với ETag
  @GetMapping("/{id}/image")
  public ResponseEntity<byte[]> getProductImage(
      @PathVariable Long id,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    ProductImage image = productService.getProductImage(id);
    String etag = "\"" + image.getVersion() + "\"";

    if (etag.equals(ifNoneMatch)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }

    return ResponseEntity
        .ok()
        .eTag(etag)
        .contentType(MediaType.IMAGE_PNG)
        .body(image.getData());
  }
}

@RestControllerAdvice
public class GlobalExceptionHandler {

  // 404 Not Found
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      ResourceNotFoundException ex) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
  }

  // 409 Conflict
  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ErrorResponse> handleConflict(
      DuplicateResourceException ex) {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("DUPLICATE", ex.getMessage()));
  }

  // 400 Bad Request
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleValidation(
      MethodArgumentNotValidException ex) {
    List<FieldError> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(err -> new FieldError(err.getField(), err.getDefaultMessage()))
        .toList();

    return ResponseEntity
        .badRequest()
        .body(new ValidationErrorResponse(errors));
  }
}
```

### ❌ Cách sai
```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  // ❌ Luôn trả về 200 OK, ngay cả khi tạo mới
  @PostMapping
  public ProductResponse createProduct(@RequestBody CreateProductRequest request) {
    return productService.createProduct(request);
  }

  // ❌ Trả về 200 OK khi DELETE (nên là 204)
  @DeleteMapping("/{id}")
  public String deleteProduct(@PathVariable Long id) {
    productService.deleteProduct(id);
    return "Deleted successfully";
  }

  // ❌ Trả về 200 với message thay vì 404
  @GetMapping("/{id}")
  public Map<String, Object> getProduct(@PathVariable Long id) {
    try {
      Product product = productService.findById(id);
      return Map.of("success", true, "data", product);
    } catch (ResourceNotFoundException e) {
      // ❌ Vẫn 200 OK nhưng data null
      return Map.of("success", false, "message", "Not found");
    }
  }
}
```

### Phát hiện
```regex
# Controller method không dùng ResponseEntity
@(GetMapping|PostMapping|PutMapping|DeleteMapping)[\s\S]{0,100}public\s+(?!ResponseEntity)[\w<>]+\s+\w+

# @PostMapping không có .created()
@PostMapping[\s\S]{0,300}ResponseEntity(?![\s\S]{0,200}\.created\()

# @DeleteMapping không có noContent()
@DeleteMapping[\s\S]{0,300}ResponseEntity(?![\s\S]{0,200}\.noContent\(\))
```

### Checklist
- [ ] Tất cả Controller method return `ResponseEntity<T>`
- [ ] POST endpoints trả về 201 Created với Location header
- [ ] DELETE endpoints trả về 204 No Content (hoặc 200 nếu có body)
- [ ] PUT/PATCH trả về 200 OK hoặc 204 No Content
- [ ] Exception handler trả về đúng status (404, 400, 409, 500)
- [ ] Async operations trả về 202 Accepted

---

## 03.03 — DTO cho request/response, không expose Entity

### Metadata
- **Mã số:** 03.03
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `dto`, `data-transfer-object`, `security`, `api-contract`

### Tại sao?
Không bao giờ expose JPA Entity trực tiếp qua REST API. Entity chứa metadata JPA (lazy loading, proxies), có thể gây N+1 query, Jackson serialization issues (infinite recursion với bidirectional relationships), và expose thông tin nhạy cảm (password, internal IDs). DTO tách biệt API contract khỏi database schema, cho phép thay đổi Entity mà không break API, và kiểm soát chính xác data nào được trả về/nhận vào.

### ✅ Cách đúng
```java
// Entity - internal model
@Entity
@Table(name = "users")
@Getter @Setter
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String email;
  private String passwordHash; // ❗ Không được expose
  private String name;

  @Enumerated(EnumType.STRING)
  private UserStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization; // ❗ Có thể gây lazy loading issue

  @OneToMany(mappedBy = "user")
  private List<Order> orders; // ❗ Có thể gây infinite recursion

  private LocalDateTime createdAt;
  private LocalDateTime lastLoginAt;
  private String internalNotes; // ❗ Admin-only field
}

// Request DTO - chỉ field cần thiết cho create
public record CreateUserRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotBlank String name,
    @NotNull Long organizationId
) {}

// Response DTO - kiểm soát field được expose
public record UserResponse(
    Long id,
    String email,
    String name,
    UserStatus status,
    OrganizationSummary organization,
    LocalDateTime createdAt
) {
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getStatus(),
        OrganizationSummary.from(user.getOrganization()),
        user.getCreatedAt()
    );
  }
}

// Nested DTO để tránh expose toàn bộ Organization
public record OrganizationSummary(
    Long id,
    String name
) {
  public static OrganizationSummary from(Organization org) {
    return new OrganizationSummary(org.getId(), org.getName());
  }
}

// Update DTO - có thể khác CreateRequest
public record UpdateUserRequest(
    @NotBlank String name,
    UserStatus status
) {}

// Admin Response - nhiều field hơn
public record AdminUserResponse(
    Long id,
    String email,
    String name,
    UserStatus status,
    OrganizationSummary organization,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt,
    String internalNotes, // Chỉ admin thấy
    int totalOrders
) {
  public static AdminUserResponse from(User user) {
    return new AdminUserResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getStatus(),
        OrganizationSummary.from(user.getOrganization()),
        user.getCreatedAt(),
        user.getLastLoginAt(),
        user.getInternalNotes(),
        user.getOrders().size()
    );
  }
}

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public ResponseEntity<UserResponse> createUser(
      @Valid @RequestBody CreateUserRequest request) {
    User user = userService.createUser(request);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(UserResponse.from(user)); // ✅ Convert Entity -> DTO
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    return ResponseEntity.ok(UserResponse.from(user));
  }
}
```

### ❌ Cách sai
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  // ❌ Trả về Entity trực tiếp
  @GetMapping("/{id}")
  public ResponseEntity<User> getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    return ResponseEntity.ok(user); // ❌ Expose passwordHash, lazy proxies, etc.
  }

  // ❌ Nhận Entity làm request body
  @PostMapping
  public ResponseEntity<User> createUser(@RequestBody User user) {
    User saved = userService.save(user);
    return ResponseEntity.ok(saved);
  }

  // ❌ Dùng @JsonIgnore trong Entity
  @Entity
  public class User {
    private Long id;
    private String email;

    @JsonIgnore // ❌ Mixing persistence concerns với serialization
    private String passwordHash;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization; // ❌ Vẫn có thể gây LazyInitializationException
  }
}
```

### Phát hiện
```regex
# Controller return Entity type
ResponseEntity<(?!.*Response|.*DTO|Void|String|List<.*Response)[\w]+>

# Controller accept Entity as @RequestBody
@RequestBody\s+(?!.*Request|.*DTO|.*Command)[\w]+\s+\w+

# Entity có @JsonIgnore (mixing concerns)
@Entity[\s\S]{0,1000}@JsonIgnore
```

### Checklist
- [ ] Controller không trả về Entity trực tiếp
- [ ] Mỗi endpoint có dedicated Request/Response DTO
- [ ] Entity không có Jackson annotations (@JsonIgnore, @JsonProperty)
- [ ] DTO có static factory method `from(Entity)` hoặc dùng MapStruct
- [ ] Nested objects cũng dùng DTO, không expose full Entity
- [ ] Password/sensitive fields không có trong Response DTO

---

## 03.04 — @Valid / @Validated cho input validation

### Metadata
- **Mã số:** 03.04
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `validation`, `bean-validation`, `security`

### Tại sao?
Input validation là first line of defense chống lại bad data và security attacks. Sử dụng Bean Validation API (@Valid, @NotNull, @Size, @Email, etc.) giúp validate declaratively, dễ đọc, dễ maintain hơn validate bằng if/else trong code. Spring tự động validate và trả về 400 Bad Request với error details khi validation fails. Validation ở Controller level đảm bảo bad data không bao giờ vào Service/Repository layer.

### ✅ Cách đúng
```java
// Request DTO với validation constraints
public record CreateProductRequest(
    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 100, message = "Name must be 3-100 characters")
    String name,

    @NotBlank(message = "SKU is required")
    @Pattern(regexp = "^[A-Z0-9-]{5,20}$", message = "Invalid SKU format")
    String sku,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    @Digits(integer = 10, fraction = 2, message = "Invalid price format")
    BigDecimal price,

    @NotNull(message = "Category ID is required")
    @Positive(message = "Category ID must be positive")
    Long categoryId,

    @Size(max = 500, message = "Description max 500 characters")
    String description,

    @Email(message = "Invalid contact email")
    String contactEmail,

    @Valid // ✅ Nested validation
    @NotNull(message = "Dimensions are required")
    ProductDimensions dimensions,

    @NotEmpty(message = "At least one tag required")
    @Size(max = 10, message = "Maximum 10 tags")
    List<@NotBlank String> tags
) {}

public record ProductDimensions(
    @NotNull @Positive Double width,
    @NotNull @Positive Double height,
    @NotNull @Positive Double depth,
    @NotNull String unit
) {}

// Custom constraint annotation
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FutureDateValidator.class)
public @interface FutureDate {
  String message() default "Date must be in the future";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
  int daysAhead() default 1;
}

public class FutureDateValidator implements ConstraintValidator<FutureDate, LocalDate> {
  private int daysAhead;

  @Override
  public void initialize(FutureDate annotation) {
    this.daysAhead = annotation.daysAhead();
  }

  @Override
  public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
    if (value == null) return true; // @NotNull handles null
    return value.isAfter(LocalDate.now().plusDays(daysAhead - 1));
  }
}

// Controller sử dụng validation
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated // ✅ Bắt buộc cho @PathVariable/@RequestParam validation
public class ProductController {

  private final ProductService productService;

  // ✅ @Valid cho request body
  @PostMapping
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request) {
    Product product = productService.createProduct(request);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ProductResponse.from(product));
  }

  // ✅ Validation cho path variable
  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> getProduct(
      @PathVariable @Positive Long id) {
    Product product = productService.findById(id);
    return ResponseEntity.ok(ProductResponse.from(product));
  }

  // ✅ Validation cho request params
  @GetMapping
  public ResponseEntity<Page<ProductResponse>> listProducts(
      @RequestParam(required = false)
      @Size(max = 50, message = "Search query max 50 chars")
      String search,

      @RequestParam(defaultValue = "0")
      @Min(0)
      int page,

      @RequestParam(defaultValue = "20")
      @Min(1) @Max(100)
      int size) {
    Page<Product> products = productService.search(search, page, size);
    return ResponseEntity.ok(products.map(ProductResponse::from));
  }
}

// Global exception handler cho validation errors
@RestControllerAdvice
public class ValidationExceptionHandler {

  // ❗ Bắt buộc để trả về 400 với error details
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
      MethodArgumentNotValidException ex) {

    List<FieldError> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> new FieldError(
            error.getField(),
            error.getDefaultMessage(),
            error.getRejectedValue()
        ))
        .toList();

    return ResponseEntity
        .badRequest()
        .body(new ValidationErrorResponse("VALIDATION_FAILED", errors));
  }

  // Cho @PathVariable/@RequestParam validation
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex) {

    List<FieldError> errors = ex.getConstraintViolations()
        .stream()
        .map(violation -> new FieldError(
            violation.getPropertyPath().toString(),
            violation.getMessage(),
            violation.getInvalidValue()
        ))
        .toList();

    return ResponseEntity
        .badRequest()
        .body(new ValidationErrorResponse("VALIDATION_FAILED", errors));
  }
}

public record ValidationErrorResponse(
    String code,
    List<FieldError> errors
) {}

public record FieldError(
    String field,
    String message,
    Object rejectedValue
) {}
```

### ❌ Cách sai
```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  // ❌ Không có @Valid
  @PostMapping
  public ResponseEntity<ProductResponse> createProduct(
      @RequestBody CreateProductRequest request) {
    // ❌ Manual validation trong Controller
    if (request.name() == null || request.name().isBlank()) {
      throw new BadRequestException("Name is required");
    }
    if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BadRequestException("Price must be positive");
    }
    // ... nhiều if/else khác

    Product product = productService.createProduct(request);
    return ResponseEntity.ok(ProductResponse.from(product));
  }

  // ❌ Không validate path variable
  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
    // id có thể là null hoặc negative
    Product product = productService.findById(id);
    return ResponseEntity.ok(ProductResponse.from(product));
  }
}

// ❌ Request DTO không có constraints
public record CreateProductRequest(
    String name, // Không có validation
    BigDecimal price,
    Long categoryId
) {}
```

### Phát hiện
```regex
# @RequestBody không có @Valid
@RequestBody\s+(?!@Valid)[\w<>]+\s+\w+

# Request DTO không có validation annotations
public record \w+Request\([\s\S]{0,500}\)(?![\s\S]{0,100}@(NotNull|NotBlank|NotEmpty|Size|Min|Max|Email|Pattern))

# Controller không có @Validated (cần cho @PathVariable validation)
@RestController[\s\S]{0,300}public class \w+Controller(?![\s\S]{0,100}@Validated)

# Không có ValidationExceptionHandler
(?![\s\S]*@ExceptionHandler\(MethodArgumentNotValidException\.class\))
```

### Checklist
- [ ] Tất cả `@RequestBody` có `@Valid`
- [ ] Request DTO có validation constraints (@NotNull, @Size, @Email, etc.)
- [ ] Controller có `@Validated` cho @PathVariable/@RequestParam validation
- [ ] Nested objects có `@Valid`
- [ ] Custom validation dùng `@Constraint` annotation
- [ ] Global `@ExceptionHandler` cho `MethodArgumentNotValidException`
- [ ] Validation error response có field name + error message

---

## 03.05 — API versioning (URL path hoặc header)

### Metadata
- **Mã số:** 03.05
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `versioning`, `backward-compatibility`, `api-evolution`

### Tại sao?
API cần evolve mà không break existing clients. Versioning cho phép maintain multiple API versions đồng thời, deploy breaking changes safely, và deprecate old versions theo schedule. URL versioning (`/api/v1/users`) là cách phổ biến nhất vì dễ test (curl, browser), dễ cache, dễ route. Header versioning (`Accept: application/vnd.myapp.v1+json`) RESTful hơn nhưng khó debug hơn.

### ✅ Cách đúng
```java
// Chiến lược 1: URL Path Versioning (khuyến nghị)
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 {

  @GetMapping("/{id}")
  public ResponseEntity<UserResponseV1> getUser(@PathVariable Long id) {
    // V1 response format
    return ResponseEntity.ok(new UserResponseV1(id, "John Doe"));
  }
}

@RestController
@RequestMapping("/api/v2/users")
public class UserControllerV2 {

  @GetMapping("/{id}")
  public ResponseEntity<UserResponseV2> getUser(@PathVariable Long id) {
    // V2 response format - breaking change: split name
    return ResponseEntity.ok(
        new UserResponseV2(id, "John", "Doe", "john@example.com")
    );
  }
}

public record UserResponseV1(Long id, String name) {}

public record UserResponseV2(
    Long id,
    String firstName,
    String lastName,
    String email
) {}

// Chiến lược 2: Header Versioning
@RestController
@RequestMapping("/api/users")
public class UserController {

  @GetMapping(value = "/{id}", headers = "X-API-Version=1")
  public ResponseEntity<UserResponseV1> getUserV1(@PathVariable Long id) {
    return ResponseEntity.ok(new UserResponseV1(id, "John Doe"));
  }

  @GetMapping(value = "/{id}", headers = "X-API-Version=2")
  public ResponseEntity<UserResponseV2> getUserV2(@PathVariable Long id) {
    return ResponseEntity.ok(
        new UserResponseV2(id, "John", "Doe", "john@example.com")
    );
  }
}

// Chiến lược 3: Content Negotiation (Accept header)
@RestController
@RequestMapping("/api/users")
public class UserController {

  @GetMapping(value = "/{id}", produces = "application/vnd.myapp.v1+json")
  public ResponseEntity<UserResponseV1> getUserV1(@PathVariable Long id) {
    return ResponseEntity.ok(new UserResponseV1(id, "John Doe"));
  }

  @GetMapping(value = "/{id}", produces = "application/vnd.myapp.v2+json")
  public ResponseEntity<UserResponseV2> getUserV2(@PathVariable Long id) {
    return ResponseEntity.ok(
        new UserResponseV2(id, "John", "Doe", "john@example.com")
    );
  }
}

// Version Configuration
@Configuration
public class ApiVersionConfig {

  public static final String CURRENT_VERSION = "v2";
  public static final List<String> SUPPORTED_VERSIONS = List.of("v1", "v2");
  public static final List<String> DEPRECATED_VERSIONS = List.of("v1");

  // Redirect / -> /api/v2
  @Bean
  public WebMvcConfigurer versionRedirectConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/api/users", "/api/v2/users");
      }
    };
  }
}

// Deprecation Warning Filter
@Component
public class DeprecationWarningFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String path = request.getRequestURI();
    if (path.contains("/api/v1/")) {
      response.setHeader("X-API-Deprecated", "true");
      response.setHeader("X-API-Sunset", "2026-12-31");
      response.setHeader("X-API-Migration",
          "https://docs.example.com/api/v2-migration");
    }

    filterChain.doFilter(request, response);
  }
}

// Version-specific Service delegation
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  public UserResponseV1 getUserV1(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    return new UserResponseV1(user.getId(), user.getFullName());
  }

  public UserResponseV2 getUserV2(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    return new UserResponseV2(
        user.getId(),
        user.getFirstName(),
        user.getLastName(),
        user.getEmail()
    );
  }
}
```

### ❌ Cách sai
```java
// ❌ Không có versioning, breaking change trực tiếp
@RestController
@RequestMapping("/api/users")
public class UserController {

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    // Thay đổi response format sẽ break existing clients
    return ResponseEntity.ok(new UserResponse(id, "John", "Doe")); // Đổi từ fullName -> firstName/lastName
  }
}

// ❌ Query parameter versioning (không khuyến nghị)
@GetMapping("/{id}")
public ResponseEntity<?> getUser(
    @PathVariable Long id,
    @RequestParam(defaultValue = "1") int version) {
  if (version == 1) {
    return ResponseEntity.ok(getUserV1(id));
  } else {
    return ResponseEntity.ok(getUserV2(id));
  }
}
```

### Phát hiện
```regex
# Controller không có version trong path
@RequestMapping\("/api/(?!v\d+/)

# Không có version package structure
(?!.*\.v\d+\.)controller
```

### Checklist
- [ ] API có version trong URL path hoặc header
- [ ] Mỗi version có separate Controller class hoặc method
- [ ] Breaking changes tạo version mới, không modify version cũ
- [ ] Old version có deprecation warning headers
- [ ] Documentation cho migration guide (v1 -> v2)
- [ ] Support ít nhất 2 versions cùng lúc

---

## 03.06 — Pagination cho list endpoints (Pageable)

### Metadata
- **Mã số:** 03.06
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `pagination`, `performance`, `scalability`

### Tại sao?
Không bao giờ return toàn bộ collection mà không limit. Khi table có 10,000+ records, fetch all sẽ gây OutOfMemoryError, slow query, timeout. Pagination giới hạn số records trả về, cải thiện performance, giảm memory usage. Spring Data JPA Pageable cung cấp pagination + sorting built-in, dễ dùng, chuẩn hóa response format.

### ✅ Cách đúng
```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;

  // ✅ Sử dụng Pageable
  @GetMapping
  public ResponseEntity<PageResponse<ProductResponse>> listProducts(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ProductStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
      Pageable pageable) {

    Page<Product> page = productService.findProducts(search, status, pageable);

    PageResponse<ProductResponse> response = PageResponse.of(
        page.map(ProductResponse::from)
    );

    return ResponseEntity.ok(response);
  }

  // Custom pagination với max limit
  @GetMapping("/search")
  public ResponseEntity<PageResponse<ProductResponse>> searchProducts(
      @RequestParam String query,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

    // Validate max size
    int validatedSize = Math.min(size, 100);

    Sort sortOrder = Sort.by(parseSortParams(sort));
    Pageable pageable = PageRequest.of(page, validatedSize, sortOrder);

    Page<Product> productPage = productService.search(query, pageable);

    return ResponseEntity.ok(PageResponse.of(
        productPage.map(ProductResponse::from)
    ));
  }

  private Sort.Order[] parseSortParams(String[] sort) {
    return Arrays.stream(sort)
        .map(s -> {
          String[] parts = s.split(",");
          String property = parts[0];
          Sort.Direction direction = parts.length > 1 &&
              parts[1].equalsIgnoreCase("desc")
              ? Sort.Direction.DESC
              : Sort.Direction.ASC;
          return new Sort.Order(direction, property);
        })
        .toArray(Sort.Order[]::new);
  }
}

// Service layer
@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;

  public Page<Product> findProducts(
      String search,
      ProductStatus status,
      Pageable pageable) {

    if (search != null && status != null) {
      return productRepository.findByNameContainingAndStatus(
          search, status, pageable
      );
    } else if (search != null) {
      return productRepository.findByNameContaining(search, pageable);
    } else if (status != null) {
      return productRepository.findByStatus(status, pageable);
    } else {
      return productRepository.findAll(pageable);
    }
  }
}

// Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

  Page<Product> findByNameContaining(String name, Pageable pageable);

  Page<Product> findByStatus(ProductStatus status, Pageable pageable);

  Page<Product> findByNameContainingAndStatus(
      String name,
      ProductStatus status,
      Pageable pageable
  );

  // Custom query với pagination
  @Query("SELECT p FROM Product p WHERE " +
         "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
         "(:status IS NULL OR p.status = :status)")
  Page<Product> search(
      @Param("search") String search,
      @Param("status") ProductStatus status,
      Pageable pageable
  );
}

// Standardized Page Response DTO
public record PageResponse<T>(
    List<T> content,
    PageMetadata metadata
) {
  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        new PageMetadata(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast(),
            page.hasNext(),
            page.hasPrevious()
        )
    );
  }
}

public record PageMetadata(
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    boolean hasNext,
    boolean hasPrevious
) {}

// Configuration - customize Pageable parameter names
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    PageableHandlerMethodArgumentResolver resolver =
        new PageableHandlerMethodArgumentResolver();
    resolver.setPageParameterName("page");
    resolver.setSizeParameterName("size");
    resolver.setOneIndexedParameters(false); // 0-based indexing
    resolver.setMaxPageSize(100);
    resolver.setFallbackPageable(PageRequest.of(0, 20));
    resolvers.add(resolver);
  }
}
```

### ❌ Cách sai
```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  // ❌ Không có pagination - nguy hiểm!
  @GetMapping
  public ResponseEntity<List<ProductResponse>> listProducts() {
    List<Product> products = productRepository.findAll(); // Fetch all!
    return ResponseEntity.ok(
        products.stream().map(ProductResponse::from).toList()
    );
  }

  // ❌ Manual pagination - không dùng Pageable
  @GetMapping("/search")
  public ResponseEntity<List<ProductResponse>> search(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    // ❌ Không có max limit validation
    int offset = page * size;
    List<Product> products = productRepository.findAll(); // Fetch all first!
    List<Product> paginated = products.stream()
        .skip(offset)
        .limit(size)
        .toList();

    return ResponseEntity.ok(
        paginated.stream().map(ProductResponse::from).toList()
    );
  }
}
```

### Phát hiện
```regex
# GET endpoint return List mà không có Pageable
@GetMapping[\s\S]{0,200}ResponseEntity<List<

# Repository.findAll() không có Pageable parameter
\.findAll\(\)(?!\s*;)

# Controller method không có Pageable parameter khi return collection
@GetMapping[\s\S]{0,100}public[\s\S]{0,100}ResponseEntity<(?!Page)[\s\S]{0,50}List<(?![\s\S]{0,200}Pageable)
```

### Checklist
- [ ] Tất cả list endpoints có Pageable parameter
- [ ] Repository method return `Page<T>` thay vì `List<T>`
- [ ] Response có metadata (page, size, totalElements, totalPages)
- [ ] Có max size limit (e.g., 100) để tránh abuse
- [ ] Support sorting qua `sort` parameter
- [ ] Default page size hợp lý (10-20)
- [ ] Documentation cho pagination parameters

---

## 03.07 — Đặt tên REST resource theo chuẩn (plural nouns)

### Metadata
- **Mã số:** 03.07
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** `naming-convention`, `rest-api-design`, `consistency`

### Tại sao?
REST API naming conventions cải thiện readability, predictability, và developer experience. Plural nouns (`/users`, `/products`) rõ ràng hơn singular (`/user`), nhất quán với "collection of resources" concept. Kebab-case cho multi-word resources (`/product-categories`), lowercase, không dùng verbs trong URL (verbs nằm trong HTTP methods GET/POST/PUT/DELETE).

### ✅ Cách đúng
```java
// ✅ Plural nouns, kebab-case
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  @GetMapping // GET /api/v1/users
  public ResponseEntity<PageResponse<UserResponse>> listUsers(Pageable pageable) {
    // ...
  }

  @PostMapping // POST /api/v1/users
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    // ...
  }

  @GetMapping("/{id}") // GET /api/v1/users/123
  public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    // ...
  }

  @PutMapping("/{id}") // PUT /api/v1/users/123
  public ResponseEntity<UserResponse> updateUser(
      @PathVariable Long id,
      @Valid @RequestBody UpdateUserRequest request) {
    // ...
  }

  @DeleteMapping("/{id}") // DELETE /api/v1/users/123
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    // ...
  }
}

// ✅ Nested resources
@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
public class UserOrderController {

  @GetMapping // GET /api/v1/users/123/orders
  public ResponseEntity<PageResponse<OrderResponse>> getUserOrders(
      @PathVariable Long userId,
      Pageable pageable) {
    // ...
  }

  @PostMapping // POST /api/v1/users/123/orders
  public ResponseEntity<OrderResponse> createOrder(
      @PathVariable Long userId,
      @Valid @RequestBody CreateOrderRequest request) {
    // ...
  }
}

// ✅ Sub-resources và actions
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

  // Sub-resource
  @GetMapping("/{orderId}/items") // GET /api/v1/orders/123/items
  public ResponseEntity<List<OrderItemResponse>> getOrderItems(@PathVariable Long orderId) {
    // ...
  }

  // Controller actions (ngoại lệ cho non-CRUD operations)
  @PostMapping("/{orderId}/cancel") // POST /api/v1/orders/123/cancel
  public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId) {
    // Action verb OK ở cuối path
  }

  @PostMapping("/{orderId}/refund") // POST /api/v1/orders/123/refund
  public ResponseEntity<RefundResponse> refundOrder(
      @PathVariable Long orderId,
      @Valid @RequestBody RefundRequest request) {
    // ...
  }

  @PostMapping("/{orderId}/ship") // POST /api/v1/orders/123/ship
  public ResponseEntity<OrderResponse> shipOrder(
      @PathVariable Long orderId,
      @Valid @RequestBody ShipmentRequest request) {
    // ...
  }
}

// ✅ Multi-word resources - kebab-case
@RestController
@RequestMapping("/api/v1/product-categories") // ✅ kebab-case
public class ProductCategoryController {
  // ...
}

@RestController
@RequestMapping("/api/v1/shipping-addresses")
public class ShippingAddressController {
  // ...
}

// ✅ Query parameters cho filtering
@GetMapping("/api/v1/products")
public ResponseEntity<PageResponse<ProductResponse>> searchProducts(
    @RequestParam(required = false) String name,
    @RequestParam(required = false) String category,
    @RequestParam(required = false) ProductStatus status,
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAfter,
    Pageable pageable) {
  // GET /api/v1/products?name=laptop&category=electronics&status=ACTIVE
}
```

### ❌ Cách sai
```java
// ❌ Singular nouns
@RestController
@RequestMapping("/api/v1/user") // Should be /users
public class UserController {}

// ❌ Verbs trong URL path
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  @GetMapping("/getAll") // ❌ GET /api/v1/users/getAll
  public List<UserResponse> getAll() {}

  @PostMapping("/createUser") // ❌ POST /api/v1/users/createUser
  public UserResponse create(@RequestBody CreateUserRequest request) {}

  @DeleteMapping("/deleteUser/{id}") // ❌ DELETE /api/v1/users/deleteUser/123
  public void delete(@PathVariable Long id) {}
}

// ❌ CamelCase hoặc snake_case
@RestController
@RequestMapping("/api/v1/productCategories") // ❌ Should be product-categories
public class ProductCategoryController {}

@RestController
@RequestMapping("/api/v1/shipping_addresses") // ❌ Should be shipping-addresses
public class ShippingAddressController {}

// ❌ RPC-style endpoints
@PostMapping("/api/v1/sendEmail") // ❌ Should be POST /api/v1/emails
public void sendEmail(@RequestBody EmailRequest request) {}

@GetMapping("/api/v1/calculatePrice") // ❌ Should be GET /api/v1/prices?productId=X
public BigDecimal calculatePrice(@RequestParam Long productId) {}
```

### Phát hiện
```regex
# Singular nouns trong path (có thể false positive)
@RequestMapping\(".*/(user|product|order|item)"\)

# Verbs trong URL path
@RequestMapping\(".*//(get|create|update|delete|fetch|find|search)[A-Z]

# CamelCase trong path
@RequestMapping\(".*[a-z][A-Z]

# snake_case trong path
@RequestMapping\(".*_
```

### Checklist
- [ ] Resource names dùng plural nouns (`/users`, `/products`)
- [ ] Multi-word resources dùng kebab-case (`/product-categories`)
- [ ] Không có verbs trong URL path (trừ actions như `/cancel`, `/approve`)
- [ ] HTTP methods thể hiện action (GET/POST/PUT/DELETE, không phải `/getUser`)
- [ ] Nested resources rõ ràng (`/users/{userId}/orders`)
- [ ] Lowercase cho tất cả path segments
- [ ] Filtering qua query params, không phải path (`?status=active`)

---

## 03.08 — @RestControllerAdvice cho global exception handling

### Metadata
- **Mã số:** 03.08
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `exception-handling`, `error-response`, `aop`

### Tại sao?
Không xử lý exception trong từng Controller method (code duplication, inconsistent error format). @RestControllerAdvice cho phép centralized exception handling, standardized error response format, logging tập trung, và dễ maintain. Client nhận được consistent error structure (error code, message, timestamp, path) cho mọi exception. Tránh expose stack trace hoặc sensitive information cho end users.

### ✅ Cách đúng
```java
// Standardized Error Response
public record ErrorResponse(
    String code,
    String message,
    LocalDateTime timestamp,
    String path
) {
  public static ErrorResponse of(String code, String message, String path) {
    return new ErrorResponse(code, message, LocalDateTime.now(), path);
  }
}

public record ValidationErrorResponse(
    String code,
    String message,
    List<FieldError> errors,
    LocalDateTime timestamp,
    String path
) {}

public record FieldError(
    String field,
    String message,
    Object rejectedValue
) {}

// Custom Business Exceptions
public class ResourceNotFoundException extends RuntimeException {
  private final String resourceType;
  private final Object resourceId;

  public ResourceNotFoundException(String resourceType, Object resourceId) {
    super(String.format("%s not found with id: %s", resourceType, resourceId));
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  public String getResourceType() { return resourceType; }
  public Object getResourceId() { return resourceId; }
}

public class DuplicateResourceException extends RuntimeException {
  private final String field;
  private final Object value;

  public DuplicateResourceException(String field, Object value) {
    super(String.format("Resource already exists with %s: %s", field, value));
    this.field = field;
    this.value = value;
  }
}

public class BusinessException extends RuntimeException {
  private final String errorCode;

  public BusinessException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() { return errorCode; }
}

// Global Exception Handler
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // 404 Not Found
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleResourceNotFound(
      ResourceNotFoundException ex,
      HttpServletRequest request) {

    log.warn("Resource not found: {} with id {}",
        ex.getResourceType(), ex.getResourceId());

    ErrorResponse error = ErrorResponse.of(
        "RESOURCE_NOT_FOUND",
        ex.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(error);
  }

  // 409 Conflict
  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateResource(
      DuplicateResourceException ex,
      HttpServletRequest request) {

    log.warn("Duplicate resource: {}", ex.getMessage());

    ErrorResponse error = ErrorResponse.of(
        "DUPLICATE_RESOURCE",
        ex.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(error);
  }

  // 400 Bad Request - Validation Errors
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
      MethodArgumentNotValidException ex,
      HttpServletRequest request) {

    List<FieldError> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> new FieldError(
            error.getField(),
            error.getDefaultMessage(),
            error.getRejectedValue()
        ))
        .toList();

    log.warn("Validation failed: {} errors", errors.size());

    ValidationErrorResponse response = new ValidationErrorResponse(
        "VALIDATION_FAILED",
        "Request validation failed",
        errors,
        LocalDateTime.now(),
        request.getRequestURI()
    );

    return ResponseEntity
        .badRequest()
        .body(response);
  }

  // 400 Bad Request - @PathVariable/@RequestParam validation
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex,
      HttpServletRequest request) {

    List<FieldError> errors = ex.getConstraintViolations()
        .stream()
        .map(violation -> new FieldError(
            violation.getPropertyPath().toString(),
            violation.getMessage(),
            violation.getInvalidValue()
        ))
        .toList();

    ValidationErrorResponse response = new ValidationErrorResponse(
        "VALIDATION_FAILED",
        "Request validation failed",
        errors,
        LocalDateTime.now(),
        request.getRequestURI()
    );

    return ResponseEntity
        .badRequest()
        .body(response);
  }

  // 400 Bad Request - Custom Business Exception
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException ex,
      HttpServletRequest request) {

    log.warn("Business exception: {}", ex.getMessage());

    ErrorResponse error = ErrorResponse.of(
        ex.getErrorCode(),
        ex.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity
        .badRequest()
        .body(error);
  }

  // 401 Unauthorized
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorResponse> handleUnauthorized(
      UnauthorizedException ex,
      HttpServletRequest request) {

    log.warn("Unauthorized access: {}", ex.getMessage());

    ErrorResponse error = ErrorResponse.of(
        "UNAUTHORIZED",
        ex.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(error);
  }

  // 403 Forbidden
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex,
      HttpServletRequest request) {

    log.warn("Access denied: {}", request.getRequestURI());

    ErrorResponse error = ErrorResponse.of(
        "FORBIDDEN",
        "You don't have permission to access this resource",
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(error);
  }

  // 500 Internal Server Error
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
      Exception ex,
      HttpServletRequest request) {

    // ❗ Log full stack trace cho debugging
    log.error("Unexpected error occurred", ex);

    // ❗ Không expose stack trace cho client
    ErrorResponse error = ErrorResponse.of(
        "INTERNAL_SERVER_ERROR",
        "An unexpected error occurred. Please contact support.",
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(error);
  }

  // 405 Method Not Allowed
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException ex,
      HttpServletRequest request) {

    ErrorResponse error = ErrorResponse.of(
        "METHOD_NOT_ALLOWED",
        String.format("Method %s is not supported for this endpoint", ex.getMethod()),
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.METHOD_NOT_ALLOWED)
        .header("Allow", String.join(", ", ex.getSupportedHttpMethods()))
        .body(error);
  }

  // 415 Unsupported Media Type
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex,
      HttpServletRequest request) {

    ErrorResponse error = ErrorResponse.of(
        "UNSUPPORTED_MEDIA_TYPE",
        String.format("Content type %s is not supported", ex.getContentType()),
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(error);
  }
}
```

### ❌ Cách sai
```java
// ❌ Exception handling trong từng Controller
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  @GetMapping("/{id}")
  public ResponseEntity<?> getUser(@PathVariable Long id) {
    try {
      User user = userService.findById(id);
      return ResponseEntity.ok(UserResponse.from(user));
    } catch (ResourceNotFoundException ex) {
      // ❌ Duplicate error handling logic
      return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", ex.getMessage()));
    } catch (Exception ex) {
      // ❌ Expose stack trace
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", ex.toString()));
    }
  }

  @PostMapping
  public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
    try {
      // ❌ Duplicate validation logic
      if (request.email() == null) {
        return ResponseEntity
            .badRequest()
            .body(Map.of("error", "Email is required"));
      }
      User user = userService.createUser(request);
      return ResponseEntity.ok(UserResponse.from(user));
    } catch (DuplicateResourceException ex) {
      // ❌ Inconsistent error format
      return ResponseEntity
          .status(HttpStatus.CONFLICT)
          .body("User already exists");
    }
  }
}
```

### Phát hiện
```regex
# Controller có try-catch blocks
@(GetMapping|PostMapping|PutMapping|DeleteMapping)[\s\S]{0,500}try\s*\{

# ResponseEntity.status trong Controller (có thể OK cho success case)
ResponseEntity\.status\(HttpStatus\.(NOT_FOUND|CONFLICT|BAD_REQUEST|INTERNAL_SERVER_ERROR)

# Không có @RestControllerAdvice
(?![\s\S]*@RestControllerAdvice)

# Exception không được handle
throw new \w+Exception\((?![\s\S]{0,2000}@ExceptionHandler\(\w+Exception\.class\))
```

### Checklist
- [ ] Có `@RestControllerAdvice` class
- [ ] Tất cả custom exceptions có `@ExceptionHandler`
- [ ] Error response có structure nhất quán (code, message, timestamp, path)
- [ ] Validation errors trả về field-level details
- [ ] Generic Exception handler catch-all với 500 status
- [ ] Log exceptions với appropriate level (WARN cho 4xx, ERROR cho 5xx)
- [ ] Không expose stack trace hoặc sensitive info cho client
- [ ] HTTP status code chính xác (404, 400, 409, 500, etc.)

---

## 03.09 — Content negotiation (JSON/XML)

### Metadata
- **Mã số:** 03.09
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** `content-negotiation`, `media-type`, `api-flexibility`

### Tại sao?
Content negotiation cho phép client request response format mong muốn qua Accept header. REST API có thể support multiple formats (JSON, XML, CSV) mà không cần duplicate endpoints. Client gửi `Accept: application/json` hoặc `Accept: application/xml`, server trả về format tương ứng. Cải thiện API flexibility, backward compatibility khi thêm format mới.

### ✅ Cách đúng
```java
// Add XML dependency
// <dependency>
//   <groupId>com.fasterxml.jackson.dataformat</groupId>
//   <artifactId>jackson-dataformat-xml</artifactId>
// </dependency>

// DTO support both JSON and XML
@JacksonXmlRootElement(localName = "user")
public record UserResponse(
    @JacksonXmlProperty(isAttribute = true)
    Long id,

    @JacksonXmlProperty
    String email,

    @JacksonXmlProperty
    String name,

    @JacksonXmlProperty
    UserStatus status,

    @JacksonXmlProperty(localName = "created_at")
    LocalDateTime createdAt
) {
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getStatus(),
        user.getCreatedAt()
    );
  }
}

// Controller với produces cho multiple formats
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  // ✅ Support JSON và XML
  @GetMapping(
      value = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
  )
  public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    return ResponseEntity.ok(UserResponse.from(user));
    // Accept: application/json -> JSON response
    // Accept: application/xml -> XML response
  }

  // ✅ Support multiple input formats
  @PostMapping(
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
  )
  public ResponseEntity<UserResponse> createUser(
      @Valid @RequestBody CreateUserRequest request) {
    User user = userService.createUser(request);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(UserResponse.from(user));
  }

  // ✅ Custom format - CSV export
  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<String> exportUsers() {
    List<User> users = userService.findAll();
    String csv = convertToCsv(users);

    return ResponseEntity
        .ok()
        .header("Content-Disposition", "attachment; filename=users.csv")
        .body(csv);
  }

  private String convertToCsv(List<User> users) {
    StringBuilder csv = new StringBuilder("id,email,name,status\n");
    users.forEach(user ->
        csv.append(String.format("%d,%s,%s,%s\n",
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getStatus()
        ))
    );
    return csv.toString();
  }
}

// Configuration cho XML support
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    configurer
        .favorParameter(false) // Disable ?format=xml query param
        .ignoreAcceptHeader(false) // Enable Accept header
        .defaultContentType(MediaType.APPLICATION_JSON) // Default to JSON
        .mediaType("json", MediaType.APPLICATION_JSON)
        .mediaType("xml", MediaType.APPLICATION_XML);
  }

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> {
      // JSON config
      builder.indentOutput(true);
      builder.serializationInclusion(JsonInclude.Include.NON_NULL);

      // XML config
      builder.createXmlMapper(true);
    };
  }
}

// Custom Message Converter cho special format
@Configuration
public class CustomMessageConverterConfig implements WebMvcConfigurer {

  @Override
  public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
    // Add custom CSV converter
    converters.add(new CsvHttpMessageConverter());
  }
}

public class CsvHttpMessageConverter extends AbstractHttpMessageConverter<List<?>> {

  public CsvHttpMessageConverter() {
    super(new MediaType("text", "csv"));
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return List.class.isAssignableFrom(clazz);
  }

  @Override
  protected List<?> readInternal(
      Class<? extends List<?>> clazz,
      HttpInputMessage inputMessage) {
    throw new UnsupportedOperationException("CSV read not supported");
  }

  @Override
  protected void writeInternal(
      List<?> data,
      HttpOutputMessage outputMessage) throws IOException {

    if (data.isEmpty()) {
      return;
    }

    // Convert list to CSV
    StringBuilder csv = new StringBuilder();
    // ... CSV generation logic

    outputMessage.getBody().write(csv.toString().getBytes());
  }
}

// Example responses
/*
Request: GET /api/v1/users/1
Accept: application/json

Response:
{
  "id": 1,
  "email": "john@example.com",
  "name": "John Doe",
  "status": "ACTIVE",
  "createdAt": "2026-01-15T10:30:00"
}

Request: GET /api/v1/users/1
Accept: application/xml

Response:
<user id="1">
  <email>john@example.com</email>
  <name>John Doe</name>
  <status>ACTIVE</status>
  <created_at>2026-01-15T10:30:00</created_at>
</user>
*/
```

### ❌ Cách sai
```java
// ❌ Hardcode JSON, không support negotiation
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    // ❌ Chỉ trả về JSON, ignore Accept header
    User user = userService.findById(id);
    return ResponseEntity.ok(UserResponse.from(user));
  }

  // ❌ Separate endpoints cho format khác
  @GetMapping("/{id}/xml")
  public ResponseEntity<String> getUserXml(@PathVariable Long id) {
    // ❌ Duplicate logic, không chuẩn REST
    User user = userService.findById(id);
    String xml = convertToXml(user);
    return ResponseEntity.ok(xml);
  }
}
```

### Phát hiện
```regex
# Controller không có produces attribute
@(GetMapping|PostMapping|PutMapping)\((?![\s\S]{0,100}produces)

# Chỉ support JSON
produces\s*=\s*\{?\s*MediaType\.APPLICATION_JSON_VALUE\s*\}?(?![\s\S]{0,50}MediaType\.APPLICATION_XML)
```

### Checklist
- [ ] Controller methods có `produces` attribute với multiple media types
- [ ] DTO support JSON và XML serialization
- [ ] Configuration cho content negotiation (Accept header)
- [ ] Default format là JSON
- [ ] Custom formats (CSV, PDF) có dedicated endpoints hoặc converters
- [ ] Test với different Accept headers
- [ ] Documentation cho supported formats

---

## 03.10 — CORS configuration tập trung

### Metadata
- **Mã số:** 03.10
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `cors`, `security`, `cross-origin`, `configuration`

### Tại sao?
CORS (Cross-Origin Resource Sharing) bắt buộc khi frontend và backend ở different origins (domain/port khác nhau). Không configure CORS đúng dẫn đến browser block requests. CORS config phải tập trung (global configuration), không scatter trong từng Controller (@CrossOrigin). Phải cẩn thận với `allowedOrigins: "*"` (security risk), specify exact origins, enable credentials nếu cần cookies/authentication.

### ✅ Cách đúng
```java
// Centralized CORS Configuration
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Value("${app.cors.allowed-origins}")
  private String[] allowedOrigins;

  @Value("${app.cors.allowed-methods}")
  private String[] allowedMethods;

  @Value("${app.cors.allowed-headers}")
  private String[] allowedHeaders;

  @Value("${app.cors.allow-credentials}")
  private boolean allowCredentials;

  @Value("${app.cors.max-age}")
  private long maxAge;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods(allowedMethods)
        .allowedHeaders(allowedHeaders)
        .allowCredentials(allowCredentials)
        .maxAge(maxAge);
  }
}

// application.yml
/*
app:
  cors:
    allowed-origins:
      - https://app.example.com
      - https://admin.example.com
    allowed-methods:
      - GET
      - POST
      - PUT
      - DELETE
      - PATCH
      - OPTIONS
    allowed-headers:
      - Authorization
      - Content-Type
      - X-Requested-With
      - Accept
      - Origin
    allow-credentials: true
    max-age: 3600

# application-dev.yml (cho development)
app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173
    allow-credentials: true

# application-prod.yml (cho production)
app:
  cors:
    allowed-origins:
      - https://app.example.com
    allow-credentials: true
*/

// Alternative: CorsFilter approach
@Configuration
public class CorsFilterConfig {

  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilter() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOriginPatterns(List.of(
        "https://*.example.com",
        "http://localhost:[*]" // Development
    ));
    config.setAllowedHeaders(List.of(
        "Authorization",
        "Content-Type",
        "Accept",
        "X-Requested-With",
        "X-CSRF-Token"
    ));
    config.setAllowedMethods(List.of(
        "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
    ));
    config.setExposedHeaders(List.of(
        "X-Total-Count",
        "X-Page-Number",
        "X-Page-Size"
    ));
    config.setMaxAge(3600L);

    source.registerCorsConfiguration("/api/**", config);

    FilterRegistrationBean<CorsFilter> bean =
        new FilterRegistrationBean<>(new CorsFilter(source));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);

    return bean;
  }
}

// Environment-specific CORS configuration
@Configuration
@Profile("production")
public class ProductionCorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins("https://app.example.com")
        .allowedMethods("GET", "POST", "PUT", "DELETE")
        .allowedHeaders("Authorization", "Content-Type")
        .allowCredentials(true)
        .maxAge(3600);
  }
}

@Configuration
@Profile("development")
public class DevelopmentCorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("*") // ⚠️ Only for development!
        .allowedMethods("*")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}

// Security config integration
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()
            .requestMatchers("/api/**").authenticated()
        );

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.example.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);

    return source;
  }
}

// Preflight request handling
// Browser tự động gửi OPTIONS request trước actual request
// Spring MVC tự động handle, không cần manual implementation

// Testing CORS
/*
# Test CORS preflight
curl -X OPTIONS \
  -H "Origin: https://app.example.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type,Authorization" \
  http://localhost:8080/api/v1/users

# Expected response headers:
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
*/
```

### ❌ Cách sai
```java
// ❌ @CrossOrigin trong từng Controller (không tập trung)
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*") // ❌ Security risk, không maintain được
public class UserController {
  // ...
}

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin(origins = "http://localhost:3000") // ❌ Hardcode, không consistent
public class ProductController {
  // ...
}

// ❌ Wildcard cho production
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins("*") // ❌ Cho phép mọi origin
        .allowedMethods("*") // ❌ Cho phép mọi method
        .allowCredentials(true); // ❌ Conflict: credentials = true với origins = *
  }
}

// ❌ Không có CORS config
// Browser sẽ block cross-origin requests
```

### Phát hiện
```regex
# @CrossOrigin trong Controller (nên dùng global config)
@CrossOrigin

# allowedOrigins = "*" (security risk)
allowedOrigins\(\s*"\*"\s*\)

# allowCredentials(true) với wildcard origins
allowedOrigins\(\s*"\*"\s*\)[\s\S]{0,200}allowCredentials\(true\)

# Không có CORS configuration
(?![\s\S]*addCorsMappings|[\s\S]*CorsFilter)
```

### Checklist
- [ ] CORS config tập trung (WebMvcConfigurer hoặc CorsFilter)
- [ ] Không dùng `@CrossOrigin` trong Controller
- [ ] `allowedOrigins` specify exact domains, không dùng `"*"` trong production
- [ ] `allowedMethods` chỉ enable methods cần thiết
- [ ] `allowCredentials` = true nếu cần cookies/auth headers
- [ ] Different config cho dev/staging/prod environments
- [ ] `maxAge` set hợp lý (3600s) để reduce preflight requests
- [ ] Test CORS với actual frontend hoặc curl OPTIONS request
- [ ] `exposedHeaders` cho custom response headers cần thiết

---

## Tổng kết Domain 03

| Practice | Mức độ | Điểm trừ | Tác động |
|----------|--------|----------|----------|
| 03.01 Controller delegate cho Service | 🔴 | -10 | Code khó test, vi phạm SRP |
| 03.02 ResponseEntity + HTTP status | 🟠 | -5 | API không chuẩn REST |
| 03.03 DTO cho request/response | 🔴 | -10 | Security risk, tight coupling |
| 03.04 @Valid input validation | 🔴 | -10 | Bad data vào system |
| 03.05 API versioning | 🟠 | -5 | Breaking changes phá client |
| 03.06 Pagination (Pageable) | 🟠 | -5 | Performance issue, OOM |
| 03.07 REST naming conventions | 🟡 | -2 | API khó dùng, inconsistent |
| 03.08 @RestControllerAdvice | 🔴 | -10 | Error handling không nhất quán |
| 03.09 Content negotiation | 🟡 | -2 | API kém flexible |
| 03.10 CORS configuration | 🟠 | -5 | CORS errors, security risk |

**Tổng điểm tối đa:** 64 điểm
**Số practices bắt buộc:** 4 (03.01, 03.03, 03.04, 03.08)

### Quick Wins
1. Thêm `@Valid` cho tất cả `@RequestBody` (03.04)
2. Tạo `@RestControllerAdvice` class (03.08)
3. Return `ResponseEntity<>` thay vì direct object (03.02)
4. Tách DTO riêng, không expose Entity (03.03)
5. Setup CORS global config (03.10)
