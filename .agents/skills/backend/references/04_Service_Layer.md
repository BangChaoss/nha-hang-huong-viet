# Domain 04: Service Layer
> **Số practices:** 8 | 🔴 3 | 🟠 4 | 🟡 1
> **Trọng số:** ×1

---

## 04.01 — Business logic chỉ nằm trong Service layer

### Metadata
- **Mã số:** 04.01
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `architecture`, `separation-of-concerns`, `maintainability`

### Tại sao?
Business logic trong Controller hoặc Repository vi phạm nguyên tắc Single Responsibility Principle và khiến code khó test, khó tái sử dụng. Controller chỉ nên xử lý HTTP concerns (validate request, format response), Repository chỉ nên làm data access. Service layer là nơi duy nhất chứa business rules, calculation, orchestration logic để đảm bảo tính nhất quán và dễ bảo trì.

### ✅ Cách đúng
```java
// Controller - chỉ xử lý HTTP
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    OrderResponse response = orderService.createOrder(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}

// Service - chứa business logic
@Service
@RequiredArgsConstructor
public class OrderService {
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final InventoryService inventoryService;
  private final PaymentService paymentService;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    // Business logic: validate stock
    for (OrderItemRequest item : request.getItems()) {
      if (!inventoryService.hasStock(item.getProductId(), item.getQuantity())) {
        throw new InsufficientStockException(item.getProductId());
      }
    }

    // Business logic: calculate total
    BigDecimal total = calculateTotal(request.getItems());

    // Business logic: apply discount
    BigDecimal discount = applyDiscount(request.getCouponCode(), total);
    BigDecimal finalAmount = total.subtract(discount);

    // Orchestration
    Order order = Order.builder()
        .customerId(request.getCustomerId())
        .totalAmount(finalAmount)
        .status(OrderStatus.PENDING)
        .build();

    order = orderRepository.save(order);
    inventoryService.reserveStock(request.getItems(), order.getId());
    paymentService.processPayment(order.getId(), finalAmount);

    return OrderResponse.from(order);
  }

  private BigDecimal calculateTotal(List<OrderItemRequest> items) {
    return items.stream()
        .map(item -> {
          Product product = productRepository.findById(item.getProductId())
              .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
          return product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal applyDiscount(String couponCode, BigDecimal total) {
    if (couponCode == null || couponCode.isBlank()) {
      return BigDecimal.ZERO;
    }
    // Discount logic
    return total.multiply(BigDecimal.valueOf(0.1)); // 10% discount
  }
}

// Repository - chỉ data access
public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByCustomerId(Long customerId);
  List<Order> findByStatus(OrderStatus status);
}
```

### ❌ Cách sai
```java
// Controller chứa business logic - SAI
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;

  @PostMapping
  @Transactional
  public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    // Business logic trong controller - SAI
    BigDecimal total = BigDecimal.ZERO;
    for (OrderItemRequest item : request.getItems()) {
      Product product = productRepository.findById(item.getProductId())
          .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
      total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    Order order = new Order();
    order.setTotalAmount(total);
    order = orderRepository.save(order);

    return ResponseEntity.ok(OrderResponse.from(order));
  }
}
```

### Phát hiện
```regex
# Controller có @Transactional
@RestController[\s\S]{0,500}@Transactional

# Controller inject Repository trực tiếp (thường là code smell)
@RestController[\s\S]{0,200}private final \w+Repository
```

### Checklist
- [ ] Controller chỉ validate input và format output
- [ ] Service chứa toàn bộ business logic
- [ ] Repository chỉ có query methods, không có calculation
- [ ] Business rules có thể test độc lập với HTTP layer

---

## 04.02 — @Transactional đúng scope (service method, không phải controller)

### Metadata
- **Mã số:** 04.02
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `transaction`, `architecture`, `spring`

### Tại sao?
@Transactional trên Controller method vi phạm separation of concerns và khiến transaction kéo dài không cần thiết (bao gồm cả response serialization). Transaction nên được quản lý ở Service layer nơi business logic thực thi để đảm bảo ACID properties đúng scope. Controller method có thể gọi nhiều service methods, mỗi cái cần transaction riêng hoặc không cần transaction.

### ✅ Cách đúng
```java
// Controller - KHÔNG có @Transactional
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
  private final UserService userService;

  @PostMapping
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    UserResponse response = userService.createUser(request); // Service quản lý transaction
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/{id}")
  public ResponseEntity<UserResponse> updateUser(
      @PathVariable Long id,
      @Valid @RequestBody UpdateUserRequest request) {
    UserResponse response = userService.updateUser(id, request);
    return ResponseEntity.ok(response);
  }
}

// Service - có @Transactional đúng chỗ
@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final EmailService emailService;

  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    // Validate uniqueness
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateEmailException(request.getEmail());
    }

    // Create user
    User user = User.builder()
        .email(request.getEmail())
        .name(request.getName())
        .status(UserStatus.ACTIVE)
        .createdAt(LocalDateTime.now())
        .build();

    user = userRepository.save(user);

    // Audit log trong cùng transaction
    auditService.logUserCreation(user.getId());

    return UserResponse.from(user);
  }

  @Transactional
  public UserResponse updateUser(Long id, UpdateUserRequest request) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));

    user.setName(request.getName());
    user.setUpdatedAt(LocalDateTime.now());

    user = userRepository.save(user);
    auditService.logUserUpdate(user.getId());

    return UserResponse.from(user);
  }

  // Read-only operation - không cần @Transactional hoặc readOnly=true
  public UserResponse getUser(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
    return UserResponse.from(user);
  }
}
```

### ❌ Cách sai
```java
// Controller có @Transactional - SAI
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
  private final UserRepository userRepository;

  @PostMapping
  @Transactional // SAI - transaction kéo dài đến khi response được serialize
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    User user = new User();
    user.setEmail(request.getEmail());
    user.setName(request.getName());
    user = userRepository.save(user);
    return ResponseEntity.ok(UserResponse.from(user));
  }
}
```

### Phát hiện
```regex
# @Transactional trong Controller
@RestController[\s\S]{0,1000}@Transactional

# @Transactional trên controller method
@(Post|Get|Put|Delete|Patch)Mapping[\s\S]{0,100}@Transactional
```

### Checklist
- [ ] @Transactional chỉ có trong Service layer
- [ ] Controller không có @Transactional
- [ ] Mỗi service method có transaction scope rõ ràng
- [ ] Transaction không bao gồm HTTP response serialization

---

## 04.03 — @Transactional(readOnly=true) cho read operations

### Metadata
- **Mã số:** 04.03
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `transaction`, `performance`, `optimization`

### Tại sao?
readOnly=true cho phép Hibernate skip dirty checking, flush mode optimization và một số database có thể route query đến read replica. Điều này cải thiện performance cho read-heavy operations và giảm overhead không cần thiết. Ngoài ra còn giúp phát hiện bug sớm nếu có write operation trong read method.

### ✅ Cách đúng
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Default cho toàn class
public class ProductService {
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;

  // Kế thừa readOnly=true từ class
  public ProductResponse getProduct(Long id) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));
    return ProductResponse.from(product);
  }

  // Kế thừa readOnly=true từ class
  public Page<ProductResponse> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
    Specification<Product> spec = ProductSpecification.build(criteria);
    Page<Product> products = productRepository.findAll(spec, pageable);
    return products.map(ProductResponse::from);
  }

  // Kế thừa readOnly=true từ class
  public List<ProductResponse> getProductsByCategory(Long categoryId) {
    if (!categoryRepository.existsById(categoryId)) {
      throw new CategoryNotFoundException(categoryId);
    }
    List<Product> products = productRepository.findByCategoryId(categoryId);
    return products.stream()
        .map(ProductResponse::from)
        .toList();
  }

  // Override với readOnly=false cho write operation
  @Transactional // readOnly=false (default)
  public ProductResponse createProduct(CreateProductRequest request) {
    Category category = categoryRepository.findById(request.getCategoryId())
        .orElseThrow(() -> new CategoryNotFoundException(request.getCategoryId()));

    Product product = Product.builder()
        .name(request.getName())
        .price(request.getPrice())
        .category(category)
        .stock(request.getStock())
        .build();

    product = productRepository.save(product);
    return ProductResponse.from(product);
  }

  @Transactional
  public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));

    product.setName(request.getName());
    product.setPrice(request.getPrice());
    product.setStock(request.getStock());

    product = productRepository.save(product);
    return ProductResponse.from(product);
  }

  @Transactional
  public void deleteProduct(Long id) {
    if (!productRepository.existsById(id)) {
      throw new ProductNotFoundException(id);
    }
    productRepository.deleteById(id);
  }
}
```

### ❌ Cách sai
```java
// Không dùng readOnly cho read operations - bỏ lỡ optimization
@Service
@RequiredArgsConstructor
public class ProductService {
  private final ProductRepository productRepository;

  @Transactional // SAI - nên dùng readOnly=true
  public ProductResponse getProduct(Long id) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));
    return ProductResponse.from(product);
  }

  @Transactional // SAI - nên dùng readOnly=true
  public List<ProductResponse> getAllProducts() {
    return productRepository.findAll().stream()
        .map(ProductResponse::from)
        .toList();
  }
}
```

### Phát hiện
```regex
# Service method có get/find/search nhưng không có readOnly=true
public \w+Response (get|find|search)\w+\([^)]*\)[\s\S]{0,50}@Transactional(?!\(readOnly\s*=\s*true\))

# Service class không có @Transactional(readOnly=true) mặc định
@Service[\s\S]{0,200}public class \w+Service(?![\s\S]{0,100}@Transactional\(readOnly)
```

### Checklist
- [ ] Read operations có @Transactional(readOnly=true)
- [ ] Service class có @Transactional(readOnly=true) làm default
- [ ] Write operations override với @Transactional (readOnly=false)
- [ ] Method naming convention rõ ràng (get/find vs create/update/delete)

---

## 04.04 — Tránh nested @Transactional gây unexpected behavior

### Metadata
- **Mã số:** 04.04
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `transaction`, `propagation`, `bug-prevention`

### Tại sao?
Nested transactions với propagation mặc định (REQUIRED) dùng chung một physical transaction, nếu inner method rollback thì outer cũng bị rollback ngay cả khi outer catch exception. Điều này gây unexpected behavior và khó debug. Cần hiểu rõ propagation modes (REQUIRES_NEW, NESTED) hoặc tránh nested transactions.

### ✅ Cách đúng
```java
@Service
@RequiredArgsConstructor
public class OrderService {
  private final OrderRepository orderRepository;
  private final PaymentService paymentService;
  private final EmailService emailService;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    // Create order trong transaction chính
    Order order = Order.builder()
        .customerId(request.getCustomerId())
        .totalAmount(request.getTotalAmount())
        .status(OrderStatus.PENDING)
        .build();

    order = orderRepository.save(order);

    // Payment trong transaction riêng (REQUIRES_NEW)
    // Nếu payment fail, order vẫn được tạo với status PENDING
    try {
      paymentService.processPayment(order.getId(), order.getTotalAmount());
      order.setStatus(OrderStatus.PAID);
    } catch (PaymentException e) {
      order.setStatus(OrderStatus.PAYMENT_FAILED);
      // Log và xử lý sau
    }

    order = orderRepository.save(order);

    // Email ngoài transaction (async, không cần ACID)
    emailService.sendOrderConfirmation(order.getId());

    return OrderResponse.from(order);
  }
}

@Service
@RequiredArgsConstructor
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  // REQUIRES_NEW - tạo transaction mới, độc lập với outer transaction
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Payment processPayment(Long orderId, BigDecimal amount) {
    Payment payment = Payment.builder()
        .orderId(orderId)
        .amount(amount)
        .status(PaymentStatus.PROCESSING)
        .build();

    payment = paymentRepository.save(payment);

    // Call external payment gateway
    String transactionId = paymentGateway.charge(amount);

    payment.setTransactionId(transactionId);
    payment.setStatus(PaymentStatus.SUCCESS);

    return paymentRepository.save(payment);
  }
}

@Service
@RequiredArgsConstructor
public class EmailService {
  private final ApplicationEventPublisher eventPublisher;

  // Không có @Transactional - async operation
  public void sendOrderConfirmation(Long orderId) {
    // Publish event để xử lý async
    eventPublisher.publishEvent(new OrderCreatedEvent(orderId));
  }
}
```

### ❌ Cách sai
```java
// Nested transaction với propagation mặc định - gây unexpected rollback
@Service
@RequiredArgsConstructor
public class OrderService {
  private final OrderRepository orderRepository;
  private final AuditService auditService;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    Order order = new Order();
    order.setTotalAmount(request.getTotalAmount());
    order = orderRepository.save(order);

    try {
      // Nested transaction với REQUIRED (default) - dùng chung transaction
      auditService.logOrderCreation(order.getId()); // Nếu này throw exception
    } catch (Exception e) {
      // Catch exception nhưng outer transaction vẫn bị mark rollback-only
      // Order sẽ KHÔNG được save - unexpected behavior
      log.error("Audit failed", e);
    }

    return OrderResponse.from(order);
  }
}

@Service
@RequiredArgsConstructor
public class AuditService {
  private final AuditRepository auditRepository;

  @Transactional // SAI - dùng chung transaction với outer
  public void logOrderCreation(Long orderId) {
    AuditLog log = new AuditLog();
    log.setOrderId(orderId);
    log.setAction("ORDER_CREATED");
    auditRepository.save(log);

    // Nếu có exception ở đây, cả outer transaction bị rollback
    throw new RuntimeException("Audit failed");
  }
}
```

### Phát hiện
```regex
# Transactional method gọi transactional method trong cùng class
@Transactional[\s\S]{0,500}this\.\w+\(

# Service inject service khác và cả 2 đều có @Transactional
private final \w+Service[\s\S]{0,1000}@Transactional
```

### Checklist
- [ ] Hiểu rõ propagation modes (REQUIRED, REQUIRES_NEW, NESTED)
- [ ] Dùng REQUIRES_NEW khi cần transaction độc lập
- [ ] Tránh catch exception từ nested transaction nếu dùng REQUIRED
- [ ] Async operations (email, notification) không nằm trong transaction

---

## 04.05 — Không gọi @Transactional method trong cùng class (proxy bypass)

### Metadata
- **Mã số:** 04.05
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `transaction`, `spring-proxy`, `bug`

### Tại sao?
Spring tạo proxy cho @Transactional, khi gọi method trong cùng class qua `this.method()`, proxy bị bypass và @Transactional không hoạt động. Điều này gây ra bug nghiêm trọng: transaction không được tạo, rollback không xảy ra khi có exception. Phải refactor thành separate service hoặc inject self-reference.

### ✅ Cách đúng
```java
// Cách 1: Tách thành service riêng (KHUYẾN NGHỊ)
@Service
@RequiredArgsConstructor
public class OrderService {
  private final OrderRepository orderRepository;
  private final OrderItemService orderItemService; // Separate service

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    Order order = Order.builder()
        .customerId(request.getCustomerId())
        .totalAmount(request.getTotalAmount())
        .build();

    order = orderRepository.save(order);

    // Gọi method của service khác - proxy hoạt động bình thường
    orderItemService.createOrderItems(order.getId(), request.getItems());

    return OrderResponse.from(order);
  }
}

@Service
@RequiredArgsConstructor
public class OrderItemService {
  private final OrderItemRepository orderItemRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void createOrderItems(Long orderId, List<OrderItemRequest> items) {
    List<OrderItem> orderItems = items.stream()
        .map(item -> OrderItem.builder()
            .orderId(orderId)
            .productId(item.getProductId())
            .quantity(item.getQuantity())
            .build())
        .toList();

    orderItemRepository.saveAll(orderItems);
  }
}

// Cách 2: Self-injection (ít khuyến nghị hơn)
@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  @Lazy // Tránh circular dependency
  private final UserService self;

  public void registerUser(RegisterRequest request) {
    // Method này KHÔNG có @Transactional
    validateRequest(request);

    // Gọi qua self để proxy hoạt động
    self.createUserTransactional(request);
  }

  @Transactional
  public void createUserTransactional(RegisterRequest request) {
    User user = User.builder()
        .email(request.getEmail())
        .name(request.getName())
        .build();

    userRepository.save(user);
  }

  private void validateRequest(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateEmailException(request.getEmail());
    }
  }
}

// Cách 3: ApplicationContext.getBean (không khuyến nghị)
@Service
@RequiredArgsConstructor
public class ProductService {
  private final ProductRepository productRepository;
  private final ApplicationContext applicationContext;

  public void updateProductPrice(Long id, BigDecimal newPrice) {
    // Lấy proxy từ ApplicationContext
    ProductService self = applicationContext.getBean(ProductService.class);
    self.updatePriceTransactional(id, newPrice);
  }

  @Transactional
  public void updatePriceTransactional(Long id, BigDecimal newPrice) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));
    product.setPrice(newPrice);
    productRepository.save(product);
  }
}
```

### ❌ Cách sai
```java
// Gọi @Transactional method trong cùng class - proxy bypass
@Service
@RequiredArgsConstructor
public class OrderService {
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;

  public OrderResponse createOrder(CreateOrderRequest request) {
    Order order = new Order();
    order.setTotalAmount(request.getTotalAmount());
    order = orderRepository.save(order);

    // SAI - gọi qua this, @Transactional của createOrderItems bị bypass
    this.createOrderItems(order.getId(), request.getItems());

    return OrderResponse.from(order);
  }

  @Transactional // Annotation này KHÔNG hoạt động khi gọi từ createOrder
  public void createOrderItems(Long orderId, List<OrderItemRequest> items) {
    List<OrderItem> orderItems = items.stream()
        .map(item -> {
          OrderItem orderItem = new OrderItem();
          orderItem.setOrderId(orderId);
          orderItem.setProductId(item.getProductId());
          return orderItem;
        })
        .toList();

    orderItemRepository.saveAll(orderItems);
    // Nếu có exception ở đây, KHÔNG rollback vì @Transactional bị bypass
  }
}
```

### Phát hiện
```regex
# this.method() trong service class
@Service[\s\S]{0,2000}this\.\w+\(

# Method không có @Transactional gọi method có @Transactional trong cùng class
public \w+ \w+\([^)]*\) \{[\s\S]{0,500}this\.\w+\([\s\S]{0,1000}@Transactional
```

### Checklist
- [ ] Không có this.method() call đến @Transactional method
- [ ] Transactional logic được tách thành separate service
- [ ] Nếu dùng self-injection, có @Lazy để tránh circular dependency
- [ ] Test cases verify transaction hoạt động đúng (rollback test)

---

## 04.06 — Service interface + implementation cho testability

### Metadata
- **Mã số:** 04.06
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** `design`, `testability`, `dependency-injection`

### Tại sao?
Interface cho Service layer giúp tách contract khỏi implementation, dễ mock trong unit test, dễ swap implementation (production vs test), và tuân thủ Dependency Inversion Principle. Tuy nhiên với Spring Boot hiện đại, concrete class injection cũng chấp nhận được nếu dùng Mockito. Interface nên dùng khi có nhiều implementation hoặc cần test isolation cao.

### ✅ Cách đúng
```java
// Interface - contract rõ ràng
public interface UserService {
  UserResponse createUser(CreateUserRequest request);
  UserResponse getUser(Long id);
  UserResponse updateUser(Long id, UpdateUserRequest request);
  void deleteUser(Long id);
  Page<UserResponse> searchUsers(UserSearchCriteria criteria, Pageable pageable);
}

// Implementation
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;

  @Override
  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateEmailException(request.getEmail());
    }

    User user = User.builder()
        .email(request.getEmail())
        .name(request.getName())
        .password(passwordEncoder.encode(request.getPassword()))
        .status(UserStatus.ACTIVE)
        .build();

    user = userRepository.save(user);
    emailService.sendWelcomeEmail(user.getEmail());

    return UserResponse.from(user);
  }

  @Override
  public UserResponse getUser(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
    return UserResponse.from(user);
  }

  @Override
  @Transactional
  public UserResponse updateUser(Long id, UpdateUserRequest request) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));

    user.setName(request.getName());
    user.setUpdatedAt(LocalDateTime.now());

    user = userRepository.save(user);
    return UserResponse.from(user);
  }

  @Override
  @Transactional
  public void deleteUser(Long id) {
    if (!userRepository.existsById(id)) {
      throw new UserNotFoundException(id);
    }
    userRepository.deleteById(id);
  }

  @Override
  public Page<UserResponse> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
    Specification<User> spec = UserSpecification.build(criteria);
    return userRepository.findAll(spec, pageable)
        .map(UserResponse::from);
  }
}

// Controller inject qua interface
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
  private final UserService userService; // Interface, không phải Impl

  @PostMapping
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.createUser(request));
  }
}

// Unit test dễ dàng mock
@ExtendWith(MockitoExtension.class)
class UserControllerTest {
  @Mock
  private UserService userService; // Mock interface

  @InjectMocks
  private UserController userController;

  @Test
  void createUser_shouldReturnCreated() {
    CreateUserRequest request = new CreateUserRequest("test@example.com", "Test User", "password");
    UserResponse expectedResponse = new UserResponse(1L, "test@example.com", "Test User");

    when(userService.createUser(request)).thenReturn(expectedResponse);

    ResponseEntity<UserResponse> response = userController.createUser(request);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(expectedResponse, response.getBody());
  }
}
```

### ❌ Cách sai
```java
// Không có interface - khó test, tight coupling
@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final EmailService emailService; // Cũng không có interface

  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    User user = new User();
    user.setEmail(request.getEmail());
    user = userRepository.save(user);

    // Tight coupling - khó mock EmailService
    emailService.sendWelcomeEmail(user.getEmail());

    return UserResponse.from(user);
  }
}

// Controller inject concrete class
@RestController
@RequiredArgsConstructor
public class UserController {
  private final UserService userService; // Concrete class - khó swap implementation

  @PostMapping("/users")
  public UserResponse createUser(@RequestBody CreateUserRequest request) {
    return userService.createUser(request);
  }
}
```

### Phát hiện
```regex
# Service class không implement interface
@Service[\s\S]{0,100}public class \w+Service(?! implements)

# Controller inject concrete service class (không chắc chắn 100%)
@RestController[\s\S]{0,500}private final \w+ServiceImpl
```

### Checklist
- [ ] Service có interface định nghĩa contract
- [ ] Implementation class có suffix Impl
- [ ] Controller inject qua interface, không phải concrete class
- [ ] Unit test mock được dễ dàng qua interface

---

## 04.07 — Tách service lớn thành domain services nhỏ

### Metadata
- **Mã số:** 04.07
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `design`, `maintainability`, `single-responsibility`

### Tại sao?
Service class > 500 dòng thường vi phạm Single Responsibility Principle, khó maintain, khó test và khó hiểu. Tách thành nhiều domain services nhỏ (OrderService, PaymentService, InventoryService) giúp code dễ đọc, dễ test từng phần, dễ parallel development và giảm coupling. Mỗi service chỉ chứa logic của một bounded context.

### ✅ Cách đúng
```java
// Tách thành nhiều domain services
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
  private final OrderRepository orderRepository;
  private final PaymentService paymentService;
  private final InventoryService inventoryService;
  private final ShippingService shippingService;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    // Validate inventory
    inventoryService.validateStock(request.getItems());

    // Create order
    Order order = Order.builder()
        .customerId(request.getCustomerId())
        .totalAmount(calculateTotal(request.getItems()))
        .status(OrderStatus.PENDING)
        .build();

    order = orderRepository.save(order);

    // Process payment
    paymentService.processPayment(order.getId(), order.getTotalAmount());

    // Reserve inventory
    inventoryService.reserveStock(request.getItems(), order.getId());

    // Create shipment
    shippingService.createShipment(order.getId(), request.getShippingAddress());

    order.setStatus(OrderStatus.CONFIRMED);
    return OrderResponse.from(orderRepository.save(order));
  }

  private BigDecimal calculateTotal(List<OrderItemRequest> items) {
    return items.stream()
        .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Payment processPayment(Long orderId, BigDecimal amount) {
    Payment payment = Payment.builder()
        .orderId(orderId)
        .amount(amount)
        .status(PaymentStatus.PROCESSING)
        .build();

    payment = paymentRepository.save(payment);

    try {
      String transactionId = paymentGateway.charge(amount);
      payment.setTransactionId(transactionId);
      payment.setStatus(PaymentStatus.SUCCESS);
    } catch (PaymentGatewayException e) {
      payment.setStatus(PaymentStatus.FAILED);
      payment.setErrorMessage(e.getMessage());
      throw new PaymentFailedException(orderId, e);
    }

    return paymentRepository.save(payment);
  }

  public Payment getPayment(Long paymentId) {
    return paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {
  private final InventoryRepository inventoryRepository;

  public void validateStock(List<OrderItemRequest> items) {
    for (OrderItemRequest item : items) {
      Inventory inventory = inventoryRepository.findByProductId(item.getProductId())
          .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));

      if (inventory.getAvailableQuantity() < item.getQuantity()) {
        throw new InsufficientStockException(item.getProductId(), item.getQuantity());
      }
    }
  }

  @Transactional
  public void reserveStock(List<OrderItemRequest> items, Long orderId) {
    for (OrderItemRequest item : items) {
      Inventory inventory = inventoryRepository.findByProductId(item.getProductId())
          .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));

      inventory.reserve(item.getQuantity(), orderId);
      inventoryRepository.save(inventory);
    }
  }

  @Transactional
  public void releaseStock(Long orderId) {
    List<Inventory> inventories = inventoryRepository.findByReservationOrderId(orderId);
    inventories.forEach(Inventory::releaseReservation);
    inventoryRepository.saveAll(inventories);
  }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingService {
  private final ShipmentRepository shipmentRepository;
  private final ShippingProvider shippingProvider;

  @Transactional
  public Shipment createShipment(Long orderId, Address address) {
    Shipment shipment = Shipment.builder()
        .orderId(orderId)
        .address(address)
        .status(ShipmentStatus.PENDING)
        .build();

    shipment = shipmentRepository.save(shipment);

    String trackingNumber = shippingProvider.createLabel(shipment.getId(), address);
    shipment.setTrackingNumber(trackingNumber);

    return shipmentRepository.save(shipment);
  }
}
```

### ❌ Cách sai
```java
// God Service - chứa tất cả logic trong 1 class
@Service
@RequiredArgsConstructor
public class OrderService {
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final InventoryRepository inventoryRepository;
  private final ShipmentRepository shipmentRepository;
  private final PaymentGateway paymentGateway;
  private final ShippingProvider shippingProvider;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    // 500+ dòng code xử lý order, payment, inventory, shipping
    // Khó maintain, khó test, vi phạm SRP
    // ...
  }

  @Transactional
  public void processPayment(Long orderId, BigDecimal amount) {
    // Payment logic
  }

  @Transactional
  public void reserveInventory(Long productId, int quantity) {
    // Inventory logic
  }

  @Transactional
  public void createShipment(Long orderId) {
    // Shipping logic
  }

  // 20+ methods, 1000+ dòng code
}
```

### Phát hiện
```regex
# Service class > 500 dòng (cần manual check)
# Hoặc service có quá nhiều dependencies (>5)
@Service[\s\S]{0,200}@RequiredArgsConstructor[\s\S]{0,200}(private final \w+ \w+;[\s\S]{0,50}){6,}
```

### Checklist
- [ ] Mỗi service class < 500 dòng
- [ ] Mỗi service có single responsibility (order, payment, inventory)
- [ ] Service dependencies < 5 (nếu nhiều hơn, cần tách)
- [ ] Method count < 15 per service

---

## 04.08 — Idempotent operations cho retry safety

### Metadata
- **Mã số:** 04.08
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `reliability`, `distributed-systems`, `idempotency`

### Tại sao?
Trong distributed systems, network failures, timeouts khiến client retry request. Nếu operation không idempotent (gọi nhiều lần cho kết quả khác nhau), sẽ gây duplicate data, double charge payment, inconsistent state. Idempotency key hoặc check-before-insert pattern đảm bảo retry safety và data consistency.

### ✅ Cách đúng
```java
// Pattern 1: Idempotency key
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  @Transactional
  public Payment processPayment(ProcessPaymentRequest request) {
    String idempotencyKey = request.getIdempotencyKey();

    // Check nếu đã xử lý idempotency key này
    Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existingPayment.isPresent()) {
      // Trả về kết quả cũ, không xử lý lại
      return existingPayment.get();
    }

    // Xử lý payment lần đầu
    Payment payment = Payment.builder()
        .orderId(request.getOrderId())
        .amount(request.getAmount())
        .idempotencyKey(idempotencyKey)
        .status(PaymentStatus.PROCESSING)
        .build();

    payment = paymentRepository.save(payment);

    try {
      String transactionId = paymentGateway.charge(request.getAmount());
      payment.setTransactionId(transactionId);
      payment.setStatus(PaymentStatus.SUCCESS);
    } catch (Exception e) {
      payment.setStatus(PaymentStatus.FAILED);
      throw new PaymentFailedException(request.getOrderId(), e);
    }

    return paymentRepository.save(payment);
  }
}

// Pattern 2: Natural idempotency với unique constraint
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    Long customerId = request.getCustomerId();
    String cartId = request.getCartId(); // Unique per customer session

    // Check nếu order từ cart này đã tồn tại
    Optional<Order> existingOrder = orderRepository.findByCustomerIdAndCartId(customerId, cartId);
    if (existingOrder.isPresent()) {
      return OrderResponse.from(existingOrder.get());
    }

    // Create new order
    Order order = Order.builder()
        .customerId(customerId)
        .cartId(cartId) // DB có UNIQUE constraint (customer_id, cart_id)
        .totalAmount(request.getTotalAmount())
        .status(OrderStatus.PENDING)
        .build();

    try {
      order = orderRepository.save(order);
    } catch (DataIntegrityViolationException e) {
      // Race condition: 2 requests cùng lúc, 1 trong 2 bị unique constraint violation
      // Retry tìm order đã được tạo bởi request kia
      return OrderResponse.from(
          orderRepository.findByCustomerIdAndCartId(customerId, cartId)
              .orElseThrow(() -> new OrderCreationException(e))
      );
    }

    // Create order items
    List<OrderItem> items = request.getItems().stream()
        .map(itemReq -> OrderItem.builder()
            .orderId(order.getId())
            .productId(itemReq.getProductId())
            .quantity(itemReq.getQuantity())
            .build())
        .toList();

    orderItemRepository.saveAll(items);

    return OrderResponse.from(order);
  }
}

// Pattern 3: Update operations - idempotent by nature
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
  private final UserRepository userRepository;

  @Transactional
  public UserResponse updateUserProfile(Long userId, UpdateProfileRequest request) {
    // Update luôn idempotent - gọi nhiều lần cùng data cho kết quả giống nhau
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    user.setName(request.getName());
    user.setPhone(request.getPhone());
    user.setAddress(request.getAddress());
    user.setUpdatedAt(LocalDateTime.now());

    user = userRepository.save(user);
    return UserResponse.from(user);
  }

  @Transactional
  public void activateUser(Long userId) {
    // Set state - idempotent
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    if (user.getStatus() == UserStatus.ACTIVE) {
      // Đã active rồi, không làm gì
      return;
    }

    user.setStatus(UserStatus.ACTIVE);
    user.setActivatedAt(LocalDateTime.now());
    userRepository.save(user);
  }
}

// DTO với idempotency key
public record ProcessPaymentRequest(
    Long orderId,
    BigDecimal amount,
    String idempotencyKey // UUID từ client
) {
  public ProcessPaymentRequest {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency key is required");
    }
  }
}
```

### ❌ Cách sai
```java
// Không idempotent - retry sẽ tạo duplicate
@Service
@RequiredArgsConstructor
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  @Transactional
  public Payment processPayment(Long orderId, BigDecimal amount) {
    // Không check duplicate, mỗi lần gọi tạo payment mới
    Payment payment = new Payment();
    payment.setOrderId(orderId);
    payment.setAmount(amount);

    payment = paymentRepository.save(payment);

    // Nếu charge thành công nhưng response bị mất (network timeout)
    // Client retry -> charge lần 2 -> double charge
    String transactionId = paymentGateway.charge(amount);
    payment.setTransactionId(transactionId);

    return paymentRepository.save(payment);
  }
}

// Increment operation - không idempotent
@Service
@RequiredArgsConstructor
public class PointService {
  private final UserRepository userRepository;

  @Transactional
  public void addPoints(Long userId, int points) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    // SAI - retry sẽ cộng điểm nhiều lần
    user.setPoints(user.getPoints() + points);

    userRepository.save(user);
  }
}
```

### Phát hiện
```regex
# Method có tên create/process/add mà không check duplicate
public \w+ (create|process|add)\w+\([^)]*\) \{[\s\S]{0,500}(?!find|exists)

# Increment operation
user\.set\w+\(user\.get\w+\(\) \+

# Payment/Order creation không có idempotency check
public Payment process[\s\S]{0,500}new Payment\([\s\S]{0,500}save\(
```

### Checklist
- [ ] Create operations có idempotency key hoặc unique constraint
- [ ] Payment operations check duplicate trước khi charge
- [ ] Increment operations dùng database atomic operation (UPDATE SET points = points + ?)
- [ ] Update operations naturally idempotent (set giá trị cố định)
- [ ] API documentation ghi rõ retry policy và idempotency guarantee
