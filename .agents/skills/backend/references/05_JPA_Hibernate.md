# Domain 05: Spring Data JPA & Hibernate

> **Số practices:** 12 | 🔴 4 | 🟠 6 | 🟡 2
> **Trọng số:** ×2

---

## 05.01 — Tắt Open Session In View (OSIV)

### Metadata
- **Mã số:** 05.01
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `performance`, `lazy-loading`, `database-connection`, `anti-pattern`

### Tại sao?

Open Session In View (OSIV) giữ Hibernate Session mở suốt cả HTTP request lifecycle, cho phép lazy loading trong view layer. Điều này dẫn đến database connection bị giữ lâu không cần thiết, gây exhaustion connection pool dưới high load. OSIV che giấu N+1 query problems, khiến developers không phát hiện performance issues sớm. Ngoài ra, nó vi phạm separation of concerns vì business logic leak vào presentation layer. Spring Boot mặc định BẬT OSIV, phải tắt thủ công.

### ✅ Cách đúng

```java
// application.yml
spring:
  jpa:
    open-in-view: false  # TẮT OSIV
    properties:
      hibernate:
        enable_lazy_load_no_trans: false  # Không cho lazy load ngoài transaction

// Service layer xử lý toàn bộ lazy loading
@Service
@Transactional(readOnly = true)
public class OrderService {

  @Autowired
  private OrderRepository orderRepository;

  // ✅ Eager fetch items trong transaction
  public OrderDTO getOrderWithItems(Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    // Trigger lazy loading TRONG transaction
    Set<OrderItem> items = order.getItems();
    items.size(); // Force initialization

    return OrderDTO.from(order); // DTO construction trong transaction
  }

  // ✅ Sử dụng JOIN FETCH
  public OrderDTO getOrderWithItemsOptimized(Long orderId) {
    Order order = orderRepository.findByIdWithItems(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    return OrderDTO.from(order);
  }
}

// Repository với JOIN FETCH
public interface OrderRepository extends JpaRepository<Order, Long> {

  @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
  Optional<Order> findByIdWithItems(@Param("id") Long id);
}

// Controller nhận DTO, không entity
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  @Autowired
  private OrderService orderService;

  @GetMapping("/{id}")
  public ResponseEntity<OrderDTO> getOrder(@PathVariable Long id) {
    // DTO đã sẵn sàng, không cần lazy loading
    OrderDTO order = orderService.getOrderWithItems(id);
    return ResponseEntity.ok(order);
  }
}
```

### ❌ Cách sai

```java
// ❌ application.yml - Để OSIV enabled (mặc định)
spring:
  jpa:
    open-in-view: true  # ANTI-PATTERN!

// ❌ Service trả entity thay vì DTO
@Service
@Transactional(readOnly = true)
public class OrderService {

  public Order getOrder(Long orderId) {
    return orderRepository.findById(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    // Entity có lazy collections chưa initialize
  }
}

// ❌ Controller truy cập lazy properties (chỉ chạy được khi OSIV enabled)
@RestController
public class OrderController {

  @GetMapping("/orders/{id}")
  public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
    Order order = orderService.getOrder(id);

    // Lazy loading xảy ra Ở CONTROLLER (ngoài transaction)
    // -> Giữ DB connection suốt HTTP response rendering
    Set<OrderItem> items = order.getItems(); // LazyInitializationException nếu OSIV tắt!

    return ResponseEntity.ok(new OrderResponse(order, items));
  }
}
```

### Phát hiện

```bash
# Tìm config OSIV enabled
rg -i "open-in-view.*true" --type yaml

# Tìm entity được trả về từ service (anti-pattern)
rg "@Service|@Transactional" -A 10 --type java | rg "return.*Repository.*find"

# Check LazyInitializationException trong logs
rg "LazyInitializationException|could not initialize proxy"
```

### Checklist

- [ ] `spring.jpa.open-in-view=false` trong application.yml/properties
- [ ] Service layer luôn trả DTO, không trả entity trực tiếp
- [ ] Tất cả lazy loading xảy ra TRONG @Transactional methods
- [ ] Không có LazyInitializationException trong runtime logs
- [ ] Database connection pool không bị exhausted dưới load

---

## 05.02 — Phát hiện và fix N+1 query

### Metadata
- **Mã số:** 05.02
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `performance`, `n+1-query`, `database`, `optimization`

### Tại sao?

N+1 query là performance killer phổ biến nhất trong JPA applications: 1 query lấy danh sách entities + N queries lấy associations của từng entity. Ví dụ: load 100 orders → 1 query + 100 queries cho items = 101 queries thay vì 1 query với JOIN. Điều này làm tăng database load exponentially, tăng latency, giảm throughput. Dưới production load, N+1 queries có thể làm sập database. Phải phát hiện sớm trong development bằng query logging và fix bằng JOIN FETCH hoặc @EntityGraph.

### ✅ Cách đúng

```java
// application.yml - Enable query logging để phát hiện N+1
spring:
  jpa:
    show-sql: true  # Development only
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        # Phát hiện N+1 tự động
        query.fail_on_pagination_over_collection_fetch: true

// Entity với @OneToMany lazy
@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String orderNumber;

  // LAZY mặc định cho @OneToMany
  @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
  private Set<OrderItem> items = new HashSet<>();

  // getters/setters
}

// Repository với JOIN FETCH để fix N+1
public interface OrderRepository extends JpaRepository<Order, Long> {

  // ✅ JOIN FETCH - 1 query duy nhất
  @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items")
  List<Order> findAllWithItems();

  // ✅ JOIN FETCH với điều kiện
  @Query("SELECT DISTINCT o FROM Order o " +
         "LEFT JOIN FETCH o.items i " +
         "WHERE o.status = :status")
  List<Order> findByStatusWithItems(@Param("status") OrderStatus status);

  // ✅ Multiple associations - cần DISTINCT
  @Query("SELECT DISTINCT o FROM Order o " +
         "LEFT JOIN FETCH o.items " +
         "LEFT JOIN FETCH o.customer")
  List<Order> findAllWithItemsAndCustomer();
}

// Sử dụng @EntityGraph (alternative cho JOIN FETCH)
public interface ProductRepository extends JpaRepository<Product, Long> {

  @EntityGraph(attributePaths = {"category", "images"})
  @Query("SELECT p FROM Product p")
  List<Product> findAllWithCategoryAndImages();

  // Named EntityGraph
  @EntityGraph(attributePaths = {"reviews.user"})
  List<Product> findByPriceGreaterThan(BigDecimal price);
}

// Service với batch processing cho large datasets
@Service
@Transactional(readOnly = true)
public class OrderService {

  @Autowired
  private OrderRepository orderRepository;

  // ✅ Load toàn bộ với JOIN FETCH
  public List<OrderDTO> getAllOrders() {
    List<Order> orders = orderRepository.findAllWithItems();
    return orders.stream()
        .map(OrderDTO::from)
        .toList();
  }

  // ✅ Batch fetch cho pagination (tránh JOIN FETCH với pagination)
  public Page<OrderDTO> getOrdersPaginated(Pageable pageable) {
    Page<Order> orderPage = orderRepository.findAll(pageable);

    // Batch fetch items riêng
    List<Long> orderIds = orderPage.getContent().stream()
        .map(Order::getId)
        .toList();

    List<OrderItem> items = orderItemRepository.findByOrderIdIn(orderIds);

    // Map items vào orders
    Map<Long, List<OrderItem>> itemsByOrderId = items.stream()
        .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

    return orderPage.map(order -> {
      List<OrderItem> orderItems = itemsByOrderId.getOrDefault(order.getId(), List.of());
      return OrderDTO.from(order, orderItems);
    });
  }
}
```

### ❌ Cách sai

```java
// ❌ Không có JOIN FETCH - N+1 query
public interface OrderRepository extends JpaRepository<Order, Long> {
  // findAll() sẽ gây N+1 khi truy cập order.getItems()
}

@Service
public class OrderService {

  // ❌ N+1 query: 1 query lấy orders + N queries lấy items
  public List<OrderDTO> getAllOrders() {
    List<Order> orders = orderRepository.findAll(); // 1 query

    return orders.stream()
        .map(order -> {
          Set<OrderItem> items = order.getItems(); // N queries (1 cho mỗi order)
          items.size(); // Trigger lazy loading
          return OrderDTO.from(order);
        })
        .toList();
    // Tổng: 1 + N queries thay vì 1 query
  }

  // ❌ Worse: Nested N+1
  public List<OrderDTO> getOrdersWithDetails() {
    List<Order> orders = orderRepository.findAll(); // 1 query

    return orders.stream()
        .map(order -> {
          Set<OrderItem> items = order.getItems(); // N queries
          items.forEach(item -> {
            Product product = item.getProduct(); // N*M queries!
            product.getName();
          });
          return OrderDTO.from(order);
        })
        .toList();
    // Tổng: 1 + N + (N*M) queries!
  }
}
```

### Phát hiện

```bash
# Enable Hibernate statistics trong test
spring.jpa.properties.hibernate.generate_statistics=true

# Tìm repository methods không có JOIN FETCH/EntityGraph
rg "@Query.*SELECT.*FROM" --type java | rg -v "JOIN FETCH|@EntityGraph"

# Check logs cho duplicate queries (N+1 symptom)
# Log sẽ show: Hibernate: select ... (repeated N times)

# Integration test để assert query count
```

```java
// Test helper để detect N+1
@Test
void shouldNotHaveNPlusOneQuery() {
  Session session = entityManager.unwrap(Session.class);
  SessionStatistics stats = session.getStatistics();

  long queryCountBefore = stats.getPrepareStatementCount();

  // Execute business logic
  List<OrderDTO> orders = orderService.getAllOrders();

  long queryCountAfter = stats.getPrepareStatementCount();
  long totalQueries = queryCountAfter - queryCountBefore;

  // Assert: Should be 1 query (JOIN FETCH), not 1+N
  assertThat(totalQueries).isLessThanOrEqualTo(2); // Allow 1-2 queries max
}
```

### Checklist

- [ ] `spring.jpa.show-sql=true` trong development để monitor queries
- [ ] Tất cả methods lấy entities + associations dùng JOIN FETCH hoặc @EntityGraph
- [ ] Integration tests assert query count (không có N+1)
- [ ] Code review checklist: "Có lazy loading nào ngoài transaction không?"
- [ ] Performance tests dưới load thực tế (100+ records)

---

## 05.03 — FetchType.LAZY mặc định cho @OneToMany, @ManyToMany

### Metadata
- **Mã số:** 05.03
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `lazy-loading`, `performance`, `entity-design`

### Tại sao?

@OneToMany và @ManyToMany với FetchType.EAGER load toàn bộ associations mỗi khi query entity cha, kể cả khi không cần. Điều này gây memory bloat, slow queries, Cartesian product với multiple eager associations. Ví dụ: Order eager load items → mỗi lần findById(order) đều join items table dù chỉ cần order info. LAZY loading chỉ fetch khi thực sự cần, giảm database load và memory footprint. JPA spec quy định @OneToMany/@ManyToMany mặc định LAZY, nhưng phải explicit declare để tránh confusion.

### ✅ Cách đúng

```java
// Entity với LAZY loading (best practice)
@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String orderNumber;

  // ✅ EXPLICIT FetchType.LAZY cho @OneToMany
  @OneToMany(
    mappedBy = "order",
    fetch = FetchType.LAZY,  // Explicit declaration
    cascade = CascadeType.PERSIST
  )
  private Set<OrderItem> items = new HashSet<>();

  // ✅ @ManyToOne EAGER chỉ khi association nhỏ và luôn cần
  @ManyToOne(fetch = FetchType.LAZY)  // LAZY mặc định cho @ManyToOne
  @JoinColumn(name = "customer_id")
  private Customer customer;

  // getters/setters
}

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String username;

  // ✅ @ManyToMany LAZY - chỉ load khi cần
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
  )
  private Set<Role> roles = new HashSet<>();

  // getters/setters
}

// Service layer control lazy loading
@Service
@Transactional(readOnly = true)
public class OrderService {

  @Autowired
  private OrderRepository orderRepository;

  // ✅ Chỉ load order, không load items
  public OrderSummaryDTO getOrderSummary(Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    // Không truy cập order.getItems() → Không query items table
    return new OrderSummaryDTO(order.getId(), order.getOrderNumber());
  }

  // ✅ Load items chỉ khi cần với JOIN FETCH
  public OrderDetailDTO getOrderDetail(Long orderId) {
    Order order = orderRepository.findByIdWithItems(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    return OrderDetailDTO.from(order);
  }
}

// Repository với selective fetching
public interface OrderRepository extends JpaRepository<Order, Long> {

  // Không fetch items
  Optional<Order> findById(Long id);

  // Fetch items khi cần
  @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
  Optional<Order> findByIdWithItems(@Param("id") Long id);

  // Fetch multiple associations
  @Query("SELECT DISTINCT o FROM Order o " +
         "LEFT JOIN FETCH o.items " +
         "LEFT JOIN FETCH o.customer " +
         "WHERE o.id = :id")
  Optional<Order> findByIdWithItemsAndCustomer(@Param("id") Long id);
}
```

### ❌ Cách sai

```java
// ❌ EAGER loading cho @OneToMany (ANTI-PATTERN)
@Entity
public class Order {

  @OneToMany(
    mappedBy = "order",
    fetch = FetchType.EAGER  // ❌ Luôn load items, kể cả khi không cần!
  )
  private Set<OrderItem> items = new HashSet<>();

  // Mỗi findById(order) sẽ JOIN items → slow query
}

// ❌ Multiple EAGER associations (DISASTER)
@Entity
public class Product {

  @OneToMany(fetch = FetchType.EAGER)
  private Set<Review> reviews = new HashSet<>();

  @ManyToMany(fetch = FetchType.EAGER)
  private Set<Category> categories = new HashSet<>();

  @OneToMany(fetch = FetchType.EAGER)
  private Set<Image> images = new HashSet<>();

  // findById(product) → Cartesian product nightmare!
  // Query JOIN 3 tables, nếu 10 reviews × 5 categories × 8 images = 400 rows!
}

// ❌ Service không control được lazy loading
@Service
public class OrderService {

  public Order getOrder(Long orderId) {
    // Với EAGER items: luôn load items dù chỉ cần order info
    // Với LAZY items: caller phải biết lazy loading rules (bad API design)
    return orderRepository.findById(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
  }
}
```

### Phát hiện

```bash
# Tìm EAGER loading trong entities
rg "fetch.*=.*FetchType\.EAGER" --type java

# Tìm @OneToMany/@ManyToMany không explicit fetch type
rg "@(OneToMany|ManyToMany)(?!.*fetch)" --type java

# Check query logs cho unexpected JOINs
# Log pattern: SELECT ... FROM orders o LEFT JOIN order_items ... (khi chỉ cần order)
```

### Checklist

- [ ] Tất cả @OneToMany dùng `fetch = FetchType.LAZY`
- [ ] Tất cả @ManyToMany dùng `fetch = FetchType.LAZY`
- [ ] @ManyToOne dùng LAZY trừ khi association nhỏ và luôn cần
- [ ] Repository có separate methods cho fetch/no-fetch scenarios
- [ ] Service methods rõ ràng về data nào được load (DTO patterns)

---

## 05.04 — @EntityGraph hoặc JOIN FETCH thay eager loading

### Metadata
- **Mã số:** 05.04
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `performance`, `fetch-strategy`, `entity-graph`

### Tại sao?

FetchType.EAGER là static, áp dụng cho TẤT CẢ queries (không linh hoạt). @EntityGraph và JOIN FETCH cho phép dynamic fetch strategy per query: chỉ load associations khi cần, tránh over-fetching. @EntityGraph type-safe hơn JPQL strings, hỗ trợ nested paths và reusable named graphs. JOIN FETCH mạnh mẽ cho complex queries với điều kiện. Cả hai đều giải quyết N+1 query problem mà không làm bloat entity definition với EAGER.

### ✅ Cách đúng

```java
// Entity với named @EntityGraph
@Entity
@Table(name = "products")
@NamedEntityGraphs({
  @NamedEntityGraph(
    name = "Product.withCategory",
    attributeNodes = @NamedAttributeNode("category")
  ),
  @NamedEntityGraph(
    name = "Product.full",
    attributeNodes = {
      @NamedAttributeNode("category"),
      @NamedAttributeNode("images"),
      @NamedAttributeNode(value = "reviews", subgraph = "reviews.user")
    },
    subgraphs = @NamedSubgraph(
      name = "reviews.user",
      attributeNodes = @NamedAttributeNode("user")
    )
  )
})
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
  private Set<Image> images = new HashSet<>();

  @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
  private Set<Review> reviews = new HashSet<>();

  // getters/setters
}

// Repository sử dụng @EntityGraph
public interface ProductRepository extends JpaRepository<Product, Long> {

  // ✅ Ad-hoc EntityGraph với attributePaths
  @EntityGraph(attributePaths = {"category", "images"})
  List<Product> findAll();

  // ✅ Sử dụng Named EntityGraph
  @EntityGraph(value = "Product.full", type = EntityGraph.EntityGraphType.LOAD)
  Optional<Product> findById(Long id);

  // ✅ EntityGraph với query methods
  @EntityGraph(attributePaths = {"category"})
  List<Product> findByPriceGreaterThan(BigDecimal price);

  // ✅ JOIN FETCH cho complex queries
  @Query("SELECT DISTINCT p FROM Product p " +
         "LEFT JOIN FETCH p.category c " +
         "LEFT JOIN FETCH p.images " +
         "WHERE c.name = :categoryName")
  List<Product> findByCategoryNameWithImages(@Param("categoryName") String categoryName);

  // ✅ JOIN FETCH với pagination workaround
  @Query("SELECT DISTINCT p FROM Product p " +
         "LEFT JOIN FETCH p.category " +
         "WHERE p.id IN :ids")
  List<Product> findByIdsWithCategory(@Param("ids") List<Long> ids);
}

// Service sử dụng different fetch strategies
@Service
@Transactional(readOnly = true)
public class ProductService {

  @Autowired
  private ProductRepository productRepository;

  // ✅ Lightweight listing - chỉ category
  public List<ProductListDTO> getAllProducts() {
    // EntityGraph: category only
    List<Product> products = productRepository.findAll();
    return products.stream()
        .map(ProductListDTO::from)
        .toList();
  }

  // ✅ Full detail - category, images, reviews + users
  public ProductDetailDTO getProductDetail(Long productId) {
    // Named EntityGraph: Product.full
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    return ProductDetailDTO.from(product);
  }

  // ✅ Pagination với two-phase fetch (tránh JOIN FETCH + pagination warning)
  public Page<ProductDTO> getProductsPaginated(Pageable pageable) {
    // Phase 1: Paginate IDs only
    Page<Long> productIds = productRepository.findAllIds(pageable);

    // Phase 2: Fetch full data with EntityGraph
    List<Product> products = productRepository.findByIdsWithCategory(
        productIds.getContent()
    );

    return new PageImpl<>(
        products.stream().map(ProductDTO::from).toList(),
        pageable,
        productIds.getTotalElements()
    );
  }
}

// EntityGraph programmatic API (advanced)
@Repository
public class CustomProductRepositoryImpl {

  @PersistenceContext
  private EntityManager entityManager;

  public List<Product> findWithDynamicGraph(Set<String> attributes) {
    EntityGraph<Product> graph = entityManager.createEntityGraph(Product.class);
    attributes.forEach(graph::addAttributeNode);

    return entityManager.createQuery("SELECT p FROM Product p", Product.class)
        .setHint("javax.persistence.fetchgraph", graph)
        .getResultList();
  }
}
```

### ❌ Cách sai

```java
// ❌ Dùng FetchType.EAGER thay vì EntityGraph
@Entity
public class Product {

  @ManyToOne(fetch = FetchType.EAGER)  // ❌ Luôn load, không linh hoạt
  private Category category;

  @OneToMany(fetch = FetchType.EAGER)  // ❌ Over-fetching
  private Set<Image> images = new HashSet<>();
}

// ❌ JOIN FETCH với pagination (Hibernate warning)
@Query("SELECT p FROM Product p LEFT JOIN FETCH p.images")
Page<Product> findAllWithImages(Pageable pageable);
// WARNING: firstResult/maxResults specified with collection fetch; applying in memory!

// ❌ Không dùng DISTINCT với JOIN FETCH multiple collections
@Query("SELECT p FROM Product p " +
       "LEFT JOIN FETCH p.images " +
       "LEFT JOIN FETCH p.reviews")
List<Product> findAllWithImagesAndReviews();
// Trả về duplicate products do Cartesian product!

// ❌ Lazy loading ngoài transaction (OSIV disabled)
@Service
public class ProductService {

  public Product getProduct(Long id) {
    return productRepository.findById(id).orElseThrow();
    // Caller không biết associations nào available
  }
}

@RestController
public class ProductController {

  @GetMapping("/products/{id}")
  public ProductDTO getProduct(@PathVariable Long id) {
    Product product = productService.getProduct(id);
    product.getImages().size(); // LazyInitializationException!
    return ProductDTO.from(product);
  }
}
```

### Phát hiện

```bash
# Tìm FetchType.EAGER
rg "fetch.*=.*FetchType\.EAGER" --type java

# Tìm JOIN FETCH với pagination
rg "JOIN FETCH.*Pageable" --type java

# Tìm queries thiếu @EntityGraph
rg "List<\w+> find" --type java | rg -v "@EntityGraph|JOIN FETCH"
```

### Checklist

- [ ] Không có FetchType.EAGER trong entities (dùng @EntityGraph thay thế)
- [ ] Tất cả JOIN FETCH queries có DISTINCT khi join multiple collections
- [ ] Không dùng JOIN FETCH trực tiếp với Pageable (dùng two-phase fetch)
- [ ] Named @EntityGraph cho common fetch scenarios
- [ ] Repository methods rõ ràng về associations nào được fetch

---

## 05.05 — Projection (interface/DTO) cho SELECT tối ưu

### Metadata
- **Mã số:** 05.05
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `performance`, `projection`, `dto`, `query-optimization`

### Tại sao?

Select toàn bộ entity (SELECT e FROM Entity e) fetch TẤT CẢ columns, kể cả khi chỉ cần vài fields. Điều này lãng phí network bandwidth, memory, database I/O. Projection chỉ SELECT columns cần thiết, giảm data transfer đáng kể. Interface-based projections (Spring Data) type-safe và concise. Class-based projections (DTO) cho phép complex transformations và better performance (no proxy overhead). Scalar projections (Tuple) cho ad-hoc queries. Đặc biệt hiệu quả cho listing APIs với large datasets.

### ✅ Cách đúng

```java
// Interface-based projection (Spring Data magic)
public interface ProductSummary {
  Long getId();
  String getName();
  BigDecimal getPrice();

  // Nested projection
  CategoryInfo getCategory();

  interface CategoryInfo {
    String getName();
  }

  // Computed property với @Value
  @Value("#{target.price * 1.1}")
  BigDecimal getPriceWithTax();
}

// Class-based DTO projection
public record ProductDTO(
  Long id,
  String name,
  BigDecimal price,
  String categoryName,
  Long reviewCount
) {
  // Constructor projection trong JPQL
}

// Repository với projections
public interface ProductRepository extends JpaRepository<Product, Long> {

  // ✅ Interface projection - auto mapping
  List<ProductSummary> findAllProjectedBy();

  // ✅ Interface projection với query
  @Query("SELECT p FROM Product p WHERE p.price > :minPrice")
  List<ProductSummary> findExpensiveProducts(@Param("minPrice") BigDecimal minPrice);

  // ✅ DTO projection với constructor expression
  @Query("SELECT new jp.medicalbox.dto.ProductDTO(" +
         "p.id, p.name, p.price, c.name, " +
         "CAST(COUNT(r.id) AS long)) " +
         "FROM Product p " +
         "LEFT JOIN p.category c " +
         "LEFT JOIN p.reviews r " +
         "GROUP BY p.id, p.name, p.price, c.name")
  List<ProductDTO> findAllWithReviewCount();

  // ✅ Scalar projection với Tuple
  @Query("SELECT p.id AS id, p.name AS name, COUNT(r) AS reviewCount " +
         "FROM Product p LEFT JOIN p.reviews r " +
         "GROUP BY p.id, p.name")
  List<Tuple> findProductStatistics();

  // ✅ Native query projection
  @Query(value = "SELECT p.id, p.name, p.price, c.name AS category_name " +
                 "FROM products p " +
                 "LEFT JOIN categories c ON p.category_id = c.id " +
                 "WHERE p.price > :minPrice",
         nativeQuery = true)
  List<ProductSummary> findExpensiveProductsNative(@Param("minPrice") BigDecimal minPrice);
}

// Service sử dụng projections
@Service
@Transactional(readOnly = true)
public class ProductService {

  @Autowired
  private ProductRepository productRepository;

  // ✅ Listing với projection - chỉ cần fields
  public List<ProductSummary> getAllProductSummaries() {
    // Chỉ SELECT id, name, price, category.name
    // Không fetch images, reviews, descriptions, etc.
    return productRepository.findAllProjectedBy();
  }

  // ✅ DTO projection với aggregation
  public List<ProductDTO> getProductsWithStats() {
    return productRepository.findAllWithReviewCount();
  }

  // ✅ Tuple projection processing
  public List<ProductStatDTO> getProductStatistics() {
    List<Tuple> tuples = productRepository.findProductStatistics();

    return tuples.stream()
        .map(tuple -> new ProductStatDTO(
            tuple.get("id", Long.class),
            tuple.get("name", String.class),
            tuple.get("reviewCount", Long.class)
        ))
        .toList();
  }

  // ✅ Dynamic projection
  public <T> List<T> getProducts(Class<T> projection) {
    // Spring Data JPA magic
    return productRepository.findAllProjectedBy(projection);
  }
}

// Closed projection (only declared properties)
public interface ClosedProjection {
  String getName();
  BigDecimal getPrice();
}

// Open projection (SpEL expressions, tất cả fields được fetch)
public interface OpenProjection {
  @Value("#{target.name + ' - ' + target.category.name}")
  String getDisplayName();

  @Value("#{target.price * 0.9}")
  BigDecimal getDiscountedPrice();
}
```

### ❌ Cách sai

```java
// ❌ Fetch toàn bộ entity chỉ để lấy vài fields
@Service
public class ProductService {

  public List<ProductListDTO> getAllProducts() {
    List<Product> products = productRepository.findAll();

    // Fetch toàn bộ: id, name, description (CLOB), price, stock,
    // created_at, updated_at, category, images, reviews...
    // Chỉ cần: id, name, price

    return products.stream()
        .map(p -> new ProductListDTO(p.getId(), p.getName(), p.getPrice()))
        .toList();
  }
}

// ❌ N+1 query để lấy nested data thay vì projection
@Service
public class OrderService {

  public List<OrderSummaryDTO> getAllOrders() {
    List<Order> orders = orderRepository.findAll(); // 1 query

    return orders.stream()
        .map(order -> {
          Customer customer = order.getCustomer(); // N queries
          return new OrderSummaryDTO(
              order.getId(),
              customer.getName(), // Lazy load
              order.getTotalAmount()
          );
        })
        .toList();
  }
}

// ❌ Open projection over-fetching
public interface BadProjection {
  // SpEL expression → fetch toàn bộ entity
  @Value("#{target.price > 100 ? 'Expensive' : 'Cheap'}")
  String getPriceCategory();

  // Tất cả fields của Product bị fetch dù chỉ cần price!
}

// ❌ DTO projection thiếu GROUP BY
@Query("SELECT new com.example.ProductDTO(p.id, p.name, COUNT(r)) " +
       "FROM Product p LEFT JOIN p.reviews r")
List<ProductDTO> findProductsWithReviewCount();
// Error: COUNT(r) requires GROUP BY!
```

### Phát hiện

```bash
# Tìm findAll() không dùng projection
rg "\.findAll\(\)" --type java | rg -v "Projection|DTO"

# Tìm stream().map(entity -> DTO) pattern (hint: should use projection)
rg "stream\(\).*\.map\(.*->.*DTO" --type java

# Tìm JPQL SELECT entity không có specific fields
rg "@Query.*SELECT \w+ FROM" --type java | rg -v "new |Tuple"
```

### Checklist

- [ ] Listing APIs dùng projections thay vì full entities
- [ ] Interface projections cho simple field selection
- [ ] DTO projections cho complex transformations và aggregations
- [ ] Closed projections ưu tiên hơn Open projections (performance)
- [ ] Native queries dùng projection interfaces cho result mapping

---

## 05.06 — @Version cho optimistic locking

### Metadata
- **Mã số:** 05.06
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `concurrency`, `locking`, `data-integrity`

### Tại sao?

Concurrent updates có thể gây lost updates: User A và User B đọc cùng row, cả hai update, update của A bị ghi đè bởi B. Pessimistic locking (SELECT FOR UPDATE) giữ database locks, giảm concurrency. Optimistic locking với @Version lightweight hơn: cho phép concurrent reads, chỉ fail khi actual conflict xảy ra (version mismatch). Hibernate tự động increment version và check trong UPDATE. Phù hợp cho high-read, low-write scenarios. Ném OptimisticLockException khi conflict, application có thể retry hoặc thông báo user.

### ✅ Cách đúng

```java
// Entity với @Version
@Entity
@Table(name = "products")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private BigDecimal price;

  private Integer stock;

  // ✅ @Version cho optimistic locking
  @Version
  private Long version;  // hoặc Integer, Timestamp

  // JPA tự động:
  // - Increment version khi UPDATE
  // - Check version trong WHERE clause: WHERE id = ? AND version = ?
  // - Throw OptimisticLockException nếu no rows updated

  // getters/setters
}

// Service với optimistic locking handling
@Service
@Transactional
public class ProductService {

  @Autowired
  private ProductRepository productRepository;

  // ✅ Update với version check
  public void updatePrice(Long productId, BigDecimal newPrice) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    // User A đọc product (version = 5)
    // User B cũng đọc product (version = 5)

    product.setPrice(newPrice);

    // User A save: UPDATE ... SET price = ?, version = 6 WHERE id = ? AND version = 5 ✅
    // User B save: UPDATE ... SET price = ?, version = 6 WHERE id = ? AND version = 5 ❌
    // → User B ném OptimisticLockException

    productRepository.save(product);
  }

  // ✅ Retry logic cho optimistic lock failures
  @Retryable(
    retryFor = OptimisticLockException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100)
  )
  public void decrementStock(Long productId, Integer quantity) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    if (product.getStock() < quantity) {
      throw new InsufficientStockException("Not enough stock");
    }

    product.setStock(product.getStock() - quantity);
    productRepository.save(product);

    // Nếu concurrent update → OptimisticLockException → Retry
  }

  // ✅ Handle OptimisticLockException manually
  public void updateProductWithRetry(Long productId, ProductUpdateDTO updateDTO) {
    int maxRetries = 3;
    int attempt = 0;

    while (attempt < maxRetries) {
      try {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        product.setName(updateDTO.name());
        product.setPrice(updateDTO.price());
        productRepository.save(product);

        return; // Success

      } catch (OptimisticLockException e) {
        attempt++;
        if (attempt >= maxRetries) {
          throw new ConcurrentUpdateException(
              "Unable to update product after " + maxRetries + " attempts", e);
        }
        // Wait before retry
        try {
          Thread.sleep(100 * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Retry interrupted", ie);
        }
      }
    }
  }
}

// REST Controller với conflict handling
@RestController
@RequestMapping("/api/products")
public class ProductController {

  @Autowired
  private ProductService productService;

  @PutMapping("/{id}")
  public ResponseEntity<?> updateProduct(
      @PathVariable Long id,
      @RequestBody ProductUpdateRequest request
  ) {
    try {
      productService.updatePrice(id, request.getPrice());
      return ResponseEntity.ok().build();

    } catch (OptimisticLockException e) {
      // HTTP 409 Conflict
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of(
              "error", "CONCURRENT_UPDATE",
              "message", "Product was modified by another user. Please refresh and try again."
          ));
    }
  }
}

// Pessimistic locking cho high-contention scenarios
public interface ProductRepository extends JpaRepository<Product, Long> {

  // Optimistic locking (default)
  Optional<Product> findById(Long id);

  // ✅ Pessimistic locking khi cần (ví dụ: payment processing)
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :id")
  Optional<Product> findByIdWithLock(@Param("id") Long id);

  // ✅ Pessimistic read (share lock)
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("SELECT p FROM Product p WHERE p.id = :id")
  Optional<Product> findByIdWithReadLock(@Param("id") Long id);
}
```

### ❌ Cách sai

```java
// ❌ Không có @Version - lost update risk
@Entity
public class Product {
  @Id
  private Long id;

  private BigDecimal price;

  private Integer stock;

  // Không có @Version → Concurrent updates ghi đè nhau
}

@Service
public class ProductService {

  // ❌ Race condition: User A và User B cùng decrement stock
  public void decrementStock(Long productId, Integer quantity) {
    Product product = productRepository.findById(productId).orElseThrow();

    // User A đọc stock = 10
    // User B đọc stock = 10
    // User A set stock = 8 (10 - 2)
    // User B set stock = 7 (10 - 3)
    // Result: stock = 7 (sai! phải là 5)

    product.setStock(product.getStock() - quantity);
    productRepository.save(product);
  }
}

// ❌ Catch OptimisticLockException nhưng không xử lý
@Service
public class OrderService {

  public void updateOrder(Long orderId, OrderUpdateDTO dto) {
    try {
      Order order = orderRepository.findById(orderId).orElseThrow();
      order.setStatus(dto.getStatus());
      orderRepository.save(order);

    } catch (OptimisticLockException e) {
      // ❌ Swallow exception - user không biết update failed!
      log.error("Optimistic lock failed", e);
    }
  }
}

// ❌ Dùng PESSIMISTIC lock cho mọi read (overkill)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p")
List<Product> findAll();
// Giữ database locks cho tất cả rows → Low concurrency!
```

### Phát hiện

```bash
# Tìm entities thiếu @Version
rg "@Entity" -A 20 --type java | rg -v "@Version"

# Tìm concurrent update patterns (stock, balance, counter)
rg "set(Stock|Balance|Counter|Quantity)" --type java

# Check exception handling
rg "catch.*OptimisticLockException" --type java
```

### Checklist

- [ ] Entities có concurrent updates có @Version field
- [ ] Service methods handle OptimisticLockException (retry hoặc return 409)
- [ ] Critical operations (payment, inventory) có retry logic
- [ ] Pessimistic locking chỉ dùng khi cần thiết (high contention)
- [ ] REST APIs trả HTTP 409 Conflict cho version mismatches

---

## 05.07 — Batch insert/update với hibernate.jdbc.batch_size

### Metadata
- **Mã số:** 05.07
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `performance`, `batch-processing`, `bulk-operations`

### Tại sao?

Insert/update từng entity một gửi N roundtrips tới database (N network calls). Batching gộp multiple statements thành 1 roundtrip, giảm network overhead đáng kể. Ví dụ: insert 1000 entities = 1000 roundtrips (slow) vs 10 batches × 100 entities (100× faster). Hibernate hỗ trợ JDBC batching với `hibernate.jdbc.batch_size`. Kết hợp với `hibernate.order_inserts`/`order_updates` để group cùng entity type. Cần IDENTITY strategy awareness (không batch được với IDENTITY, dùng SEQUENCE thay thế).

### ✅ Cách đúng

```java
// application.yml - Enable JDBC batching
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50  # Batch 50 statements per roundtrip
        order_inserts: true  # Group inserts by entity type
        order_updates: true  # Group updates by entity type
        batch_versioned_data: true  # Batch updates với @Version
    # QUAN TRỌNG: Không dùng GenerationType.IDENTITY với batching
    # IDENTITY không batch được vì cần database-generated ID ngay lập tức

// Entity với SEQUENCE strategy (batch-friendly)
@Entity
@Table(name = "products")
public class Product {

  // ✅ SEQUENCE strategy - compatible với batching
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
  @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 50)
  private Long id;

  private String name;
  private BigDecimal price;

  // getters/setters
}

// Service với batch operations
@Service
@Transactional
public class ProductService {

  @Autowired
  private ProductRepository productRepository;

  @PersistenceContext
  private EntityManager entityManager;

  // ✅ Batch insert với saveAll()
  public void importProducts(List<ProductDTO> productDTOs) {
    List<Product> products = productDTOs.stream()
        .map(dto -> {
          Product product = new Product();
          product.setName(dto.name());
          product.setPrice(dto.price());
          return product;
        })
        .toList();

    // saveAll() + batch_size = batching
    productRepository.saveAll(products);

    // Hibernate gửi:
    // INSERT INTO products (...) VALUES (...)  -- 50 times
    // INSERT INTO products (...) VALUES (...)  -- 50 times
    // ... (batches of 50)
  }

  // ✅ Manual batching với EntityManager
  public void importProductsManual(List<ProductDTO> productDTOs) {
    int batchSize = 50;

    for (int i = 0; i < productDTOs.size(); i++) {
      Product product = new Product();
      product.setName(productDTOs.get(i).name());
      product.setPrice(productDTOs.get(i).price());

      entityManager.persist(product);

      if (i > 0 && i % batchSize == 0) {
        // Flush batch và clear persistence context
        entityManager.flush();
        entityManager.clear();
      }
    }

    // Flush remaining
    entityManager.flush();
    entityManager.clear();
  }

  // ✅ Batch update
  public void updatePrices(Map<Long, BigDecimal> priceUpdates) {
    List<Long> productIds = new ArrayList<>(priceUpdates.keySet());
    List<Product> products = productRepository.findAllById(productIds);

    products.forEach(product -> {
      BigDecimal newPrice = priceUpdates.get(product.getId());
      product.setPrice(newPrice);
    });

    // saveAll() với batch_size
    productRepository.saveAll(products);
  }

  // ✅ Bulk operations cho large datasets (bypass Hibernate)
  @Modifying
  @Query("UPDATE Product p SET p.price = p.price * 1.1 WHERE p.category.id = :categoryId")
  public int increasePricesByCategory(@Param("categoryId") Long categoryId) {
    // Single UPDATE statement - fastest cho bulk updates
    return entityManager.createQuery(
        "UPDATE Product p SET p.price = p.price * 1.1 WHERE p.category.id = :categoryId")
        .setParameter("categoryId", categoryId)
        .executeUpdate();
  }
}

// Repository với batch-aware methods
public interface ProductRepository extends JpaRepository<Product, Long> {

  // saveAll() tự động batching
  @Override
  <S extends Product> List<S> saveAll(Iterable<S> entities);

  // Bulk delete (single DELETE statement)
  @Modifying
  @Query("DELETE FROM Product p WHERE p.category.id = :categoryId")
  int deleteByCategoryId(@Param("categoryId") Long categoryId);
}

// Test để verify batching
@SpringBootTest
@Transactional
class BatchingTest {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private ProductRepository productRepository;

  @Test
  void shouldBatchInserts() {
    // Enable SQL logging
    // spring.jpa.show-sql=true

    List<Product> products = IntStream.range(0, 100)
        .mapToObj(i -> {
          Product p = new Product();
          p.setName("Product " + i);
          p.setPrice(BigDecimal.valueOf(i));
          return p;
        })
        .toList();

    productRepository.saveAll(products);

    // Check logs: Should see batched inserts (50 per batch với batch_size=50)
    // Hibernate: insert into products (...) values (...)  -- repeated 50 times
    // Hibernate: insert into products (...) values (...)  -- repeated 50 times
  }
}
```

### ❌ Cách sai

```java
// ❌ application.yml - Không enable batching
spring:
  jpa:
    properties:
      hibernate:
        # jdbc.batch_size không config → Mặc định = 1 (no batching)

// ❌ IDENTITY strategy với batching (không hoạt động)
@Entity
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // IDENTITY cần DB-generated ID ngay lập tức
  // → Hibernate phải INSERT từng row một để lấy ID
  // → Batching bị vô hiệu hóa!
}

// ❌ Insert từng entity một trong loop
@Service
public class ProductService {

  public void importProducts(List<ProductDTO> productDTOs) {
    for (ProductDTO dto : productDTOs) {
      Product product = new Product();
      product.setName(dto.name());
      product.setPrice(dto.price());

      productRepository.save(product); // ❌ N separate INSERTs!
    }
    // 1000 DTOs = 1000 database roundtrips
  }
}

// ❌ Không flush/clear EntityManager (memory leak)
@Service
public class ProductService {

  public void importLargeDataset(List<ProductDTO> productDTOs) {
    // 1 million records
    for (ProductDTO dto : productDTOs) {
      Product product = new Product();
      product.setName(dto.name());
      entityManager.persist(product);

      // ❌ Không flush/clear → OutOfMemoryError!
      // EntityManager cache giữ tất cả entities
    }
  }
}

// ❌ Batch update với N queries thay vì bulk update
@Service
public class ProductService {

  public void increasePrices(Long categoryId, BigDecimal multiplier) {
    List<Product> products = productRepository.findByCategoryId(categoryId);

    // ❌ N updates (batched nhưng vẫn chậm)
    products.forEach(p -> p.setPrice(p.getPrice().multiply(multiplier)));
    productRepository.saveAll(products);

    // ✅ Nên dùng: 1 bulk UPDATE statement
    // UPDATE products SET price = price * ? WHERE category_id = ?
  }
}
```

### Phát hiện

```bash
# Check batch_size config
rg "batch_size" --type yaml

# Tìm GenerationType.IDENTITY (không compatible với batching)
rg "GenerationType\.IDENTITY" --type java

# Tìm save() trong loop (anti-pattern)
rg "for.*\{.*save\(" --type java

# Check flush/clear trong batch operations
rg "persist\(" -A 5 --type java | rg -v "flush|clear"
```

### Checklist

- [ ] `hibernate.jdbc.batch_size` configured (50-100 recommended)
- [ ] `hibernate.order_inserts=true` và `order_updates=true`
- [ ] Entities dùng SEQUENCE/TABLE strategy, không IDENTITY
- [ ] Batch operations dùng saveAll() thay vì save() trong loop
- [ ] EntityManager flush/clear định kỳ cho large batches (tránh OOM)
- [ ] Bulk operations (UPDATE/DELETE) dùng JPQL/native queries

---

## 05.08 — Index cho cột WHERE/JOIN/ORDER BY

### Metadata
- **Mã số:** 05.08
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `performance`, `database-index`, `query-optimization`

### Tại sao?

Queries không có index scan toàn bộ table (full table scan), độ phức tạp O(N). Index giảm xuống O(log N) hoặc O(1). Ví dụ: SELECT * FROM users WHERE email = ? trên table 1 triệu rows → scan 1 triệu rows (chậm) vs index lookup → scan 1 row (nhanh). Index cần thiết cho WHERE clauses, JOIN columns, ORDER BY, GROUP BY. Foreign keys PHẢI có index (JOIN performance). Composite index cho multi-column queries. Trade-off: index tăng INSERT/UPDATE overhead, nhưng query performance gain lớn hơn nhiều.

### ✅ Cách đúng

```java
// Entity với @Table indexes
@Entity
@Table(
  name = "users",
  indexes = {
    // ✅ Index cho unique constraint
    @Index(name = "idx_users_email", columnList = "email", unique = true),

    // ✅ Index cho common WHERE clause
    @Index(name = "idx_users_status", columnList = "status"),

    // ✅ Composite index cho multi-column queries
    @Index(name = "idx_users_created_status", columnList = "created_at, status"),

    // ✅ Index cho foreign key
    @Index(name = "idx_users_role_id", columnList = "role_id")
  }
)
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Indexed column
  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  // Foreign key - indexed
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id")
  private Role role;

  // getters/setters
}

// Flyway migration với explicit indexes
-- V1__create_users_table.sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  role_id BIGINT,
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- ✅ Index cho unique email
CREATE UNIQUE INDEX idx_users_email ON users(email);

-- ✅ Index cho WHERE status = ?
CREATE INDEX idx_users_status ON users(status);

-- ✅ Composite index cho WHERE created_at > ? AND status = ?
CREATE INDEX idx_users_created_status ON users(created_at, status);

-- ✅ Index cho foreign key (JOIN performance)
CREATE INDEX idx_users_role_id ON users(role_id);

-- ✅ Partial index (PostgreSQL) - chỉ index active users
CREATE INDEX idx_users_active ON users(email) WHERE status = 'ACTIVE';

-- Repository queries sử dụng indexes
public interface UserRepository extends JpaRepository<User, Long> {

  // ✅ Uses idx_users_email
  Optional<User> findByEmail(String email);

  // ✅ Uses idx_users_status
  List<User> findByStatus(UserStatus status);

  // ✅ Uses idx_users_created_status (composite index)
  List<User> findByCreatedAtAfterAndStatus(Instant createdAt, UserStatus status);

  // ✅ Uses idx_users_role_id (JOIN)
  @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :roleName")
  List<User> findByRoleName(@Param("roleName") String roleName);

  // ✅ ORDER BY với index
  List<User> findByStatusOrderByCreatedAtDesc(UserStatus status);
}

// Entity với compound index cho complex queries
@Entity
@Table(
  name = "orders",
  indexes = {
    @Index(name = "idx_orders_customer_status", columnList = "customer_id, status"),
    @Index(name = "idx_orders_created_at", columnList = "created_at DESC")
  }
)
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  private OrderStatus status;

  @Column(name = "created_at")
  private Instant createdAt;

  // getters/setters
}

// Query analysis để verify index usage
@Service
@Transactional(readOnly = true)
public class UserService {

  @PersistenceContext
  private EntityManager entityManager;

  // ✅ Analyze query plan (development/testing)
  public void analyzeQueryPlan() {
    // PostgreSQL EXPLAIN
    Query query = entityManager.createNativeQuery(
        "EXPLAIN ANALYZE SELECT * FROM users WHERE email = :email")
        .setParameter("email", "test@example.com");

    List<Object> results = query.getResultList();
    results.forEach(System.out::println);

    // Expected output: "Index Scan using idx_users_email on users"
    // Bad output: "Seq Scan on users" (full table scan)
  }
}
```

### ❌ Cách sai

```java
// ❌ Không có indexes
@Entity
@Table(name = "users")  // Không định nghĩa indexes!
public class User {
  @Id
  private Long id;

  private String email;  // Queried frequently, nhưng không index
  private UserStatus status;  // WHERE clause, không index

  @ManyToOne
  @JoinColumn(name = "role_id")  // Foreign key không index!
  private Role role;
}

-- ❌ Migration thiếu indexes
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL,
  role_id BIGINT
  -- Không có indexes cho email, status, role_id!
);

// ❌ Query không tận dụng index
public interface UserRepository extends JpaRepository<User, Long> {

  // ❌ LOWER(email) không dùng được index
  @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
  Optional<User> findByEmailIgnoreCase(@Param("email") String email);

  // ✅ Cách đúng: Tạo functional index
  // CREATE INDEX idx_users_email_lower ON users(LOWER(email));
}

// ❌ Composite index sai thứ tự
-- Index: (created_at, status)
CREATE INDEX idx_orders_created_status ON orders(created_at, status);

-- Query: WHERE status = ? AND created_at > ?
-- Index KHÔNG được dùng hiệu quả vì status không phải leftmost column!
-- ✅ Cần: CREATE INDEX idx_orders_status_created ON orders(status, created_at);

// ❌ Quá nhiều indexes (overhead)
@Table(
  name = "products",
  indexes = {
    @Index(columnList = "name"),
    @Index(columnList = "price"),
    @Index(columnList = "stock"),
    @Index(columnList = "category_id"),
    @Index(columnList = "name, price"),
    @Index(columnList = "name, category_id"),
    @Index(columnList = "price, stock"),
    // ... 20+ indexes
    // ❌ Mỗi INSERT/UPDATE phải update 20+ indexes!
  }
)
```

### Phát hiện

```bash
# Tìm entities không có @Index
rg "@Entity" -A 5 --type java | rg "@Table" | rg -v "indexes"

# Tìm @ManyToOne/@JoinColumn (cần index)
rg "@ManyToOne|@JoinColumn" --type java

# Analyze slow queries trong logs
rg "execution time.*[0-9]{3,}" application.log

# PostgreSQL: Find missing indexes
SELECT schemaname, tablename, attname
FROM pg_stats
WHERE schemaname = 'public'
  AND n_distinct > 100  -- High cardinality
  AND tablename NOT IN (
    SELECT tablename FROM pg_indexes WHERE indexname LIKE '%' || attname || '%'
  );
```

### Checklist

- [ ] Tất cả foreign key columns có index
- [ ] WHERE clause columns có index (high-cardinality)
- [ ] Composite indexes cho multi-column queries (đúng thứ tự)
- [ ] ORDER BY columns có index
- [ ] Unique constraints tự động tạo unique index
- [ ] Query plans analyzed (EXPLAIN ANALYZE) cho critical queries
- [ ] Không over-index (trade-off với INSERT/UPDATE performance)

---

## 05.09 — Flyway/Liquibase cho database migration

### Metadata
- **Mã số:** 05.09
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `database-migration`, `version-control`, `devops`

### Tại sao?

Hibernate `ddl-auto=update` hoặc `create-drop` KHÔNG phù hợp production: không version control, không rollback, có thể mất data, schema drift giữa environments. Flyway/Liquibase quản lý database schema như Git cho code: versioned migrations, repeatable, auditable. Mỗi migration là SQL script với version number, chạy 1 lần duy nhất, track trong `schema_version` table. Rollback scripts cho disaster recovery. CI/CD integration để auto-migrate. Flyway đơn giản (SQL-based), Liquibase mạnh mẽ (XML/YAML, database-agnostic).

### ✅ Cách đúng

```yaml
# application.yml - Flyway config
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ✅ CHỈ validate, không auto-generate schema
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true
    baseline-on-migrate: true  # Cho existing databases
    locations: classpath:db/migration
    schemas: public
    table: flyway_schema_history  # Track migrations
    # validate-on-migrate: true  # Validate checksums
```

```xml
<!-- pom.xml - Flyway dependency -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>

<!-- PostgreSQL driver -->
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
```

```sql
-- src/main/resources/db/migration/V1__create_users_table.sql
-- ✅ Versioned migration: V{version}__{description}.sql

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- src/main/resources/db/migration/V2__create_roles_table.sql
CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE,
  description TEXT
);

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- src/main/resources/db/migration/V3__add_user_phone.sql
-- ✅ ALTER TABLE migration
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
CREATE INDEX idx_users_phone ON users(phone);

-- src/main/resources/db/migration/V4__seed_default_roles.sql
-- ✅ Data migration
INSERT INTO roles (name, description) VALUES
  ('ADMIN', 'System administrator'),
  ('USER', 'Regular user'),
  ('MODERATOR', 'Content moderator');

-- src/main/resources/db/migration/R__update_statistics.sql
-- ✅ Repeatable migration (chạy lại khi content thay đổi)
-- R__{description}.sql
REFRESH MATERIALIZED VIEW user_statistics;
```

```java
// Entity validation với Flyway schema
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ✅ Entity fields khớp với Flyway migration
  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(length = 20)
  private String phone;  // Added in V3 migration

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
  )
  private Set<Role> roles = new HashSet<>();

  // getters/setters
}

// Flyway programmatic API (advanced)
@Configuration
public class FlywayConfig {

  @Bean
  public FlywayMigrationStrategy cleanMigrateStrategy() {
    return flyway -> {
      // ✅ Development only: clean + migrate
      if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
        flyway.clean();
      }
      flyway.migrate();
    };
  }

  // Custom migration callback
  @Component
  public class MigrationCallback implements Callback {

    @Override
    public boolean supports(Event event, Context context) {
      return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
      return true;
    }

    @Override
    public void handle(Event event, Context context) {
      log.info("Migration completed successfully");
      // Notify monitoring system, clear caches, etc.
    }
  }
}

// Integration test với Flyway
@SpringBootTest
@Transactional
class FlywayIntegrationTest {

  @Autowired
  private Flyway flyway;

  @Autowired
  private UserRepository userRepository;

  @Test
  void shouldHaveAppliedAllMigrations() {
    MigrationInfo[] migrations = flyway.info().all();

    // Assert tất cả migrations đã applied
    assertThat(migrations)
        .allMatch(m -> m.getState() == MigrationState.SUCCESS);
  }

  @Test
  void shouldHaveCorrectSchema() {
    // Verify schema khớp với entities
    User user = new User();
    user.setEmail("test@example.com");
    user.setPasswordHash("hash");
    user.setStatus(UserStatus.ACTIVE);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());

    User saved = userRepository.save(user);
    assertThat(saved.getId()).isNotNull();
  }
}
```

### ❌ Cách sai

```yaml
# ❌ Hibernate auto-generate schema (PRODUCTION DISASTER)
spring:
  jpa:
    hibernate:
      ddl-auto: update  # ❌ Không version control, không rollback!
      # ddl-auto: create-drop  # ❌ Xóa toàn bộ data mỗi lần restart!
```

```sql
-- ❌ Migration file không tuân thủ naming convention
-- bad_migration.sql (thiếu version prefix)

-- ❌ Sửa đổi migration đã applied
-- V1__create_users.sql (đã chạy production)
-- Sửa file này → checksum mismatch → Flyway failed!
-- ✅ Tạo migration mới: V5__alter_users_add_column.sql

-- ❌ Migration không idempotent
-- V3__insert_data.sql
INSERT INTO roles (name) VALUES ('ADMIN');
-- Chạy lại → duplicate key error!

-- ✅ Idempotent version:
INSERT INTO roles (name) VALUES ('ADMIN')
ON CONFLICT (name) DO NOTHING;

-- ❌ Không có rollback script
-- V4__complex_migration.sql
-- (complex schema changes)
-- Nếu có vấn đề production → Không rollback được!

-- ✅ Tạo rollback script riêng:
-- U4__rollback_complex_migration.sql
```

```java
// ❌ Entity không khớp với Flyway schema
@Entity
@Table(name = "users")
public class User {

  @Column(length = 100)  // ❌ Flyway: VARCHAR(255)
  private String email;

  @Column(name = "phone_number")  // ❌ Flyway: "phone"
  private String phone;

  // ddl-auto=validate sẽ FAIL!
}
```

### Phát hiện

```bash
# Check ddl-auto config
rg "ddl-auto.*(?!validate)" --type yaml

# Tìm migration files không tuân thủ naming
ls src/main/resources/db/migration/ | rg -v "^V\d+__|^R__"

# Verify Flyway enabled
rg "flyway.*enabled.*false" --type yaml

# Check migration status
./mvnw flyway:info  # Show migration status
./mvnw flyway:validate  # Validate checksums
```

### Checklist

- [ ] Flyway/Liquibase dependency trong pom.xml/build.gradle
- [ ] `spring.jpa.hibernate.ddl-auto=validate` (KHÔNG update/create)
- [ ] Migrations theo naming convention: V{version}__{description}.sql
- [ ] Tất cả migrations idempotent (có thể chạy lại an toàn)
- [ ] Critical migrations có rollback scripts
- [ ] CI/CD pipeline chạy migrations trước deploy
- [ ] Entity definitions khớp với Flyway schema (validate passed)

---

## 05.10 — Tránh CascadeType.ALL, chỉ định cascade cụ thể

### Metadata
- **Mã số:** 05.10
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `cascade`, `data-integrity`, `entity-relationship`

### Tại sao?

CascadeType.ALL cascade TẤT CẢ operations (PERSIST, MERGE, REMOVE, REFRESH, DETACH) tới child entities. REMOVE cascade đặc biệt nguy hiểm: xóa parent → xóa tất cả children (có thể không mong muốn). Ví dụ: xóa User → xóa tất cả Orders → mất data business critical. Nên explicit chỉ định cascade types cần thiết: PERSIST/MERGE cho composition relationships, tránh REMOVE cho aggregation relationships. Điều này tăng data safety, tránh accidental cascading deletes.

### ✅ Cách đúng

```java
// Entity với selective cascading
@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ✅ Composition: Order owns OrderItems
  // PERSIST + MERGE: Save order → auto save items
  // KHÔNG REMOVE: Xóa order không tự động xóa items (có thể cần audit)
  @OneToMany(
    mappedBy = "order",
    cascade = {CascadeType.PERSIST, CascadeType.MERGE},
    orphanRemoval = true  // Xóa items khi remove khỏi collection
  )
  private List<OrderItem> items = new ArrayList<>();

  // ✅ Aggregation: Order references Customer
  // KHÔNG cascade: Customer độc lập với Order
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  // Helper methods
  public void addItem(OrderItem item) {
    items.add(item);
    item.setOrder(this);
  }

  public void removeItem(OrderItem item) {
    items.remove(item);
    item.setOrder(null);
  }

  // getters/setters
}

@Entity
@Table(name = "order_items")
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  // ✅ Reference: OrderItem references Product
  // KHÔNG cascade: Product độc lập
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  private Integer quantity;
  private BigDecimal price;

  // getters/setters
}

// Composition relationship: Parent owns children
@Entity
@Table(name = "blog_posts")
public class BlogPost {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ✅ CASCADE ALL hợp lý cho composition
  // Delete post → delete comments (comments không tồn tại độc lập)
  @OneToMany(
    mappedBy = "post",
    cascade = CascadeType.ALL,
    orphanRemoval = true
  )
  private List<Comment> comments = new ArrayList<>();

  // getters/setters
}

// Service layer explicit operations
@Service
@Transactional
public class OrderService {

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private OrderItemRepository orderItemRepository;

  // ✅ Explicit delete với business logic
  public void deleteOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    // Business rule: Chỉ xóa CANCELLED orders
    if (order.getStatus() != OrderStatus.CANCELLED) {
      throw new IllegalStateException("Cannot delete non-cancelled order");
    }

    // Explicit delete items first (audit trail)
    order.getItems().forEach(item -> {
      auditService.logItemDeletion(item);
      orderItemRepository.delete(item);
    });

    // Then delete order
    auditService.logOrderDeletion(order);
    orderRepository.delete(order);
  }

  // ✅ Soft delete thay vì cascade REMOVE
  public void cancelOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    order.setStatus(OrderStatus.CANCELLED);
    order.setCancelledAt(Instant.now());

    // Items vẫn tồn tại (audit/reporting)
    orderRepository.save(order);
  }
}

// @ManyToMany không cascade REMOVE
@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ✅ KHÔNG cascade cho @ManyToMany
  // Xóa User KHÔNG xóa Roles (được dùng bởi users khác)
  @ManyToMany(
    fetch = FetchType.LAZY,
    cascade = {CascadeType.PERSIST, CascadeType.MERGE}
  )
  @JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
  )
  private Set<Role> roles = new HashSet<>();

  // getters/setters
}
```

### ❌ Cách sai

```java
// ❌ CascadeType.ALL cho aggregation relationship
@Entity
public class Order {

  @ManyToOne(
    fetch = FetchType.LAZY,
    cascade = CascadeType.ALL  // ❌ NGUY HIỂM!
  )
  @JoinColumn(name = "customer_id")
  private Customer customer;

  // Xóa Order → xóa Customer → xóa tất cả Orders của Customer → CASCADE DISASTER!
}

// ❌ CascadeType.REMOVE không cần thiết
@Entity
public class OrderItem {

  @ManyToOne(
    cascade = CascadeType.REMOVE  // ❌ Vô nghĩa!
  )
  private Product product;

  // Xóa OrderItem → xóa Product → Product bị xóa khi còn trong orders khác!
}

// ❌ Không có orphanRemoval cho composition
@Entity
public class Order {

  @OneToMany(
    mappedBy = "order",
    cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    // ❌ Thiếu orphanRemoval = true
  )
  private List<OrderItem> items = new ArrayList<>();

  public void removeItem(OrderItem item) {
    items.remove(item);  // Item vẫn tồn tại trong DB (orphan)!
  }
}

// ❌ CascadeType.ALL trên @ManyToMany
@Entity
public class Student {

  @ManyToMany(cascade = CascadeType.ALL)  // ❌ DISASTER!
  private Set<Course> courses = new HashSet<>();

  // Xóa Student → xóa Courses → xóa tất cả Students enrolled trong courses đó!
  // Cascade loop nightmare!
}

// ❌ Service phụ thuộc vào cascade thay vì explicit logic
@Service
public class OrderService {

  public void createOrder(OrderDTO orderDTO) {
    Order order = new Order();
    // ...

    orderDTO.getItems().forEach(itemDTO -> {
      OrderItem item = new OrderItem();
      // ...
      order.addItem(item);
    });

    orderRepository.save(order);  // Cascade PERSIST items
    // ❌ Không rõ ràng items được save, khó debug
  }
}
```

### Phát hiện

```bash
# Tìm CascadeType.ALL
rg "CascadeType\.ALL" --type java

# Tìm cascade với @ManyToOne
rg "@ManyToOne.*cascade" --type java

# Tìm @ManyToMany với REMOVE
rg "@ManyToMany" -A 3 --type java | rg "REMOVE|ALL"

# Tìm composition relationships thiếu orphanRemoval
rg "@OneToMany" -A 3 --type java | rg -v "orphanRemoval"
```

### Checklist

- [ ] Không có CascadeType.ALL trên @ManyToOne (aggregation)
- [ ] Không có CascadeType.ALL trên @ManyToMany
- [ ] Composition @OneToMany có `orphanRemoval = true`
- [ ] Chỉ cascade PERSIST/MERGE, tránh REMOVE trừ khi chắc chắn
- [ ] Service layer có explicit delete logic với business rules
- [ ] Critical entities dùng soft delete thay vì hard delete

---

## 05.11 — @NaturalId cho business key thay auto-generated ID

### Metadata
- **Mã số:** 05.11
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** `natural-id`, `query-optimization`, `domain-modeling`

### Tại sao?

Auto-generated IDs (SEQUENCE, IDENTITY) là technical keys, không business meaning. Queries theo business keys (email, username, orderNumber) phổ biến hơn IDs. @NaturalId caching natural keys trong Hibernate second-level cache, cho phép lookup nhanh mà không cần query database. Ví dụ: `session.byNaturalId(User.class).using("email", email).load()` → cache hit → không query DB. Tăng performance cho common lookups. Natural IDs cũng immutable, giúp prevent accidental updates.

### ✅ Cách đúng

```java
// Entity với @NaturalId
@Entity
@Table(name = "users")
@org.hibernate.annotations.Cache(
  usage = CacheConcurrencyStrategy.READ_WRITE
)
public class User {

  // Technical ID (surrogate key)
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ✅ Natural ID (business key)
  @NaturalId(mutable = false)  // Immutable natural ID
  @Column(nullable = false, unique = true, length = 255)
  private String email;

  private String name;

  @Column(name = "password_hash")
  private String passwordHash;

  // getters/setters

  // ✅ Setter cho natural ID - validation
  public void setEmail(String email) {
    if (this.email != null) {
      throw new IllegalStateException("Email cannot be changed (natural ID is immutable)");
    }
    this.email = email;
  }
}

// Repository với natural ID queries
public interface UserRepository extends JpaRepository<User, Long> {

  // ✅ Query by natural ID
  Optional<User> findByEmail(String email);

  // Native SQL với natural ID
  @Query("SELECT u FROM User u WHERE u.email = :email")
  Optional<User> findByEmailCustom(@Param("email") String email);
}

// Service sử dụng natural ID loading
@Service
@Transactional(readOnly = true)
public class UserService {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private UserRepository userRepository;

  // ✅ Hibernate Session API - natural ID cache
  public User loadByEmail(String email) {
    Session session = entityManager.unwrap(Session.class);

    // Sử dụng natural ID cache
    return session.byNaturalId(User.class)
        .using("email", email)
        .load();  // Cache hit nếu có
  }

  // ✅ Repository method (tương đương)
  public User findByEmail(String email) {
    return userRepository.findByEmail(email)
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
  }

  // ✅ Batch natural ID loading
  public List<User> loadByEmails(List<String> emails) {
    Session session = entityManager.unwrap(Session.class);

    return session.byMultipleNaturalId(User.class)
        .enableOrderedReturn(false)
        .multiLoad(emails.stream()
            .map(email -> Map.of("email", email))
            .toArray());
  }
}

// Composite natural ID
@Entity
@Table(name = "order_items")
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ✅ Composite natural ID
  @NaturalId
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  private Order order;

  @NaturalId
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  private Product product;

  private Integer quantity;

  // getters/setters
}

// Repository cho composite natural ID
@Repository
public class OrderItemRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public Optional<OrderItem> findByOrderAndProduct(Order order, Product product) {
    Session session = entityManager.unwrap(Session.class);

    OrderItem item = session.byNaturalId(OrderItem.class)
        .using("order", order)
        .using("product", product)
        .load();

    return Optional.ofNullable(item);
  }
}

// Mutable natural ID (không khuyến khích nhưng đôi khi cần)
@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ✅ Mutable natural ID - SKU có thể thay đổi
  @NaturalId(mutable = true)
  @Column(nullable = false, unique = true, length = 50)
  private String sku;

  private String name;

  // getters/setters
}
```

### ❌ Cách sai

```java
// ❌ Chỉ dùng auto-generated ID, không natural ID
@Entity
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String email;  // ❌ Business key nhưng không @NaturalId

  // Lookup by email → Không cache được
}

// ❌ Query by ID thay vì natural ID
@Service
public class UserService {

  public User getUserByEmail(String email) {
    // ❌ 2 queries: 1 để tìm ID, 1 để load entity
    Long userId = userRepository.findIdByEmail(email);
    return userRepository.findById(userId).orElseThrow();

    // ✅ Nên dùng: findByEmail(email) trực tiếp
  }
}

// ❌ Mutable natural ID không declare mutable = true
@Entity
public class Product {

  @NaturalId  // Mặc định mutable = false
  private String sku;

  public void setSku(String sku) {
    this.sku = sku;  // ❌ Update natural ID → Cache inconsistency!
  }
}

// ❌ Composite natural ID không đầy đủ
@Entity
public class OrderItem {

  @Id
  private Long id;

  @NaturalId
  @ManyToOne
  private Order order;

  // ❌ Thiếu product trong natural ID
  // → Không unique (1 order có nhiều items)
  @ManyToOne
  private Product product;
}
```

### Phát hiện

```bash
# Tìm business keys không có @NaturalId
rg "@Column.*unique.*=.*true" --type java | rg -v "@NaturalId"

# Tìm queries by unique columns (candidates cho @NaturalId)
rg "findBy(Email|Username|Code|Sku)" --type java

# Tìm mutable natural IDs
rg "@NaturalId.*mutable.*=.*true" --type java
```

### Checklist

- [ ] Business keys (email, username, code) có @NaturalId annotation
- [ ] Natural IDs immutable (mutable = false) trừ khi cần thiết
- [ ] Hibernate second-level cache enabled cho natural ID entities
- [ ] Repository methods dùng natural ID lookups
- [ ] Composite natural IDs đầy đủ các columns cần thiết cho uniqueness

---

## 05.12 — Connection pool tuning (HikariCP)

### Metadata
- **Mã số:** 05.12
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `performance`, `connection-pool`, `hikaricp`, `tuning`

### Tại sao?

Connection pool quản lý database connections, tránh overhead của creating/closing connections cho mỗi request. HikariCP (Spring Boot default) nhanh nhất nhưng cần tuning đúng. Pool size quá nhỏ → connection starvation, requests bị block. Pool size quá lớn → database overload, memory waste. Formula: `pool_size = (core_count × 2) + effective_spindle_count`. Connection timeout, idle timeout, max lifetime cần configure để handle network issues và database restarts. Monitoring pool metrics để detect leaks và saturation.

### ✅ Cách đúng

```yaml
# application.yml - HikariCP tuning
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

    hikari:
      # ✅ Pool size (formula: cores × 2 + spindles)
      # Example: 4 cores, 1 SSD → 4×2+1 = 9
      minimum-idle: 5  # Minimum connections in pool
      maximum-pool-size: 10  # Maximum connections

      # ✅ Connection timeout
      connection-timeout: 30000  # 30s - Wait for connection from pool

      # ✅ Idle timeout
      idle-timeout: 600000  # 10min - Close idle connections

      # ✅ Max lifetime
      max-lifetime: 1800000  # 30min - Recycle connections (< DB timeout)

      # ✅ Leak detection
      leak-detection-threshold: 60000  # 60s - Warn nếu connection held > 1min

      # ✅ Connection test query (PostgreSQL)
      connection-test-query: SELECT 1

      # ✅ Pool name cho monitoring
      pool-name: HikariPool-MyApp

      # ✅ Auto-commit (default true, set false nếu dùng @Transactional)
      auto-commit: true

      # ✅ Read-only optimization
      # read-only: false

# ✅ Production tuning (high load)
---
spring:
  config:
    activate:
      on-profile: production

  datasource:
    hikari:
      minimum-idle: 10
      maximum-pool-size: 20
      connection-timeout: 20000
      leak-detection-threshold: 30000
```

```java
// HikariCP programmatic config (advanced)
@Configuration
public class DataSourceConfig {

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  public HikariConfig hikariConfig() {
    HikariConfig config = new HikariConfig();

    // ✅ JDBC URL và credentials
    config.setJdbcUrl(env.getProperty("DB_URL"));
    config.setUsername(env.getProperty("DB_USERNAME"));
    config.setPassword(env.getProperty("DB_PASSWORD"));

    // ✅ Pool tuning
    config.setMinimumIdle(5);
    config.setMaximumPoolSize(10);
    config.setConnectionTimeout(30000);
    config.setIdleTimeout(600000);
    config.setMaxLifetime(1800000);

    // ✅ Performance tuning
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    config.addDataSourceProperty("useServerPrepStmts", "true");

    // ✅ Connection init SQL
    config.setConnectionInitSql("SET TIME ZONE 'UTC'");

    return config;
  }

  @Bean
  public DataSource dataSource(HikariConfig hikariConfig) {
    return new HikariDataSource(hikariConfig);
  }
}

// Connection leak detection
@Component
public class ConnectionLeakMonitor {

  @Autowired
  private HikariDataSource dataSource;

  @Scheduled(fixedRate = 60000)  // Every 1 minute
  public void monitorConnectionPool() {
    HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();

    int activeConnections = poolMXBean.getActiveConnections();
    int idleConnections = poolMXBean.getIdleConnections();
    int totalConnections = poolMXBean.getTotalConnections();
    int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();

    log.info("HikariCP stats - Active: {}, Idle: {}, Total: {}, Awaiting: {}",
        activeConnections, idleConnections, totalConnections, threadsAwaitingConnection);

    // ✅ Alert nếu pool saturation
    if (threadsAwaitingConnection > 0) {
      log.warn("Connection pool saturation detected! {} threads waiting",
          threadsAwaitingConnection);
      // Send alert to monitoring system
    }

    // ✅ Alert nếu connection leak suspected
    if (activeConnections > totalConnections * 0.9) {
      log.warn("Possible connection leak! {}/{} connections active",
          activeConnections, totalConnections);
    }
  }
}

// Micrometer metrics cho HikariCP
@Configuration
public class MetricsConfig {

  @Bean
  public MeterBinder hikariMetrics(HikariDataSource dataSource) {
    return new HikariDataSourceMetricsTracker(dataSource, "hikari");
  }
}

// Repository với proper connection handling
@Repository
@Transactional(readOnly = true)
public class UserRepository {

  @PersistenceContext
  private EntityManager entityManager;

  // ✅ @Transactional ensures connection returned to pool
  public User findById(Long id) {
    return entityManager.find(User.class, id);
    // Connection auto-returned khi method ends
  }

  // ❌ Manual connection management (tránh)
  public void dangerousMethod() {
    Connection conn = null;
    try {
      conn = dataSource.getConnection();
      // ... SQL operations
    } catch (SQLException e) {
      log.error("SQL error", e);
    } finally {
      if (conn != null) {
        try {
          conn.close();  // PHẢI close manually
        } catch (SQLException e) {
          log.error("Failed to close connection", e);
        }
      }
    }
  }
}

// Integration test với HikariCP
@SpringBootTest
class HikariCPIntegrationTest {

  @Autowired
  private HikariDataSource dataSource;

  @Test
  void shouldHaveCorrectPoolSize() {
    HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();

    assertThat(poolMXBean.getTotalConnections()).isLessThanOrEqualTo(10);
    assertThat(poolMXBean.getIdleConnections()).isGreaterThanOrEqualTo(5);
  }

  @Test
  void shouldNotLeakConnections() throws InterruptedException {
    HikariPoolMXBean poolBefore = dataSource.getHikariPoolMXBean();
    int activeConnectionsBefore = poolBefore.getActiveConnections();

    // Execute 100 transactions
    for (int i = 0; i < 100; i++) {
      userRepository.findById(1L);
    }

    Thread.sleep(1000);  // Wait for connections to return

    HikariPoolMXBean poolAfter = dataSource.getHikariPoolMXBean();
    int activeConnectionsAfter = poolAfter.getActiveConnections();

    // Active connections should return to baseline
    assertThat(activeConnectionsAfter).isLessThanOrEqualTo(activeConnectionsBefore + 1);
  }
}
```

### ❌ Cách sai

```yaml
# ❌ Pool size quá lớn (waste resources)
spring:
  datasource:
    hikari:
      maximum-pool-size: 100  # ❌ Quá lớn cho database có 10 max connections!

# ❌ Pool size quá nhỏ (connection starvation)
spring:
  datasource:
    hikari:
      maximum-pool-size: 2  # ❌ Bottleneck cho concurrent requests

# ❌ Timeout quá ngắn
spring:
  datasource:
    hikari:
      connection-timeout: 1000  # 1s - ❌ Quá ngắn, requests fail dễ dàng

# ❌ Không config leak detection
spring:
  datasource:
    hikari:
      # leak-detection-threshold không set → Không phát hiện leaks

# ❌ Max lifetime > database timeout
spring:
  datasource:
    hikari:
      max-lifetime: 3600000  # 60min
      # PostgreSQL default timeout: 10min → Connections die unexpectedly!
```

```java
// ❌ Connection leak - không close
@Service
public class UserService {

  @Autowired
  private DataSource dataSource;

  public void leakyMethod() {
    Connection conn = dataSource.getConnection();
    // ... operations
    // ❌ KHÔNG close connection → Leak!
  }
}

// ❌ Không dùng @Transactional (manual connection management)
@Service
public class OrderService {

  // ❌ Không @Transactional → Developer phải manage connection manually
  public void createOrder(OrderDTO dto) {
    Connection conn = null;
    try {
      conn = dataSource.getConnection();
      // ... complex SQL
    } finally {
      // Dễ quên close()
    }
  }
}

// ❌ Blocking operations trong transaction (hold connection lâu)
@Service
@Transactional
public class ReportService {

  public void generateReport() {
    List<Order> orders = orderRepository.findAll();

    // ❌ HTTP call trong transaction (hold DB connection!)
    for (Order order : orders) {
      restTemplate.getForObject("http://api.example.com/customer/" + order.getCustomerId());
    }

    // ✅ Nên: Fetch data first, close transaction, then HTTP calls
  }
}
```

### Phát hiện

```bash
# Check HikariCP config
rg "maximum-pool-size|minimum-idle" --type yaml

# Tìm manual connection management
rg "dataSource\.getConnection\(\)" --type java

# Tìm methods thiếu @Transactional
rg "Connection conn" --type java | rg -v "@Transactional"

# Check logs cho connection warnings
rg "HikariPool.*Connection.*not available|Connection leak detection"
```

### Checklist

- [ ] `maximum-pool-size` tuned theo formula: `cores × 2 + spindles`
- [ ] `connection-timeout` đủ lớn (20-30s) cho high load
- [ ] `max-lifetime` < database connection timeout
- [ ] `leak-detection-threshold` enabled (30-60s)
- [ ] Monitoring HikariCP metrics (active/idle/total connections)
- [ ] Tất cả database operations trong @Transactional methods
- [ ] Không có manual connection management (dùng JPA/JDBC Template)

---

## Tổng kết Domain 05

### Thống kê
- **Tổng practices:** 12
- **🔴 BẮT BUỘC:** 4 (05.01, 05.02, 05.03, 05.08, 05.09)
- **🟠 KHUYẾN NGHỊ:** 6 (05.04, 05.05, 05.06, 05.07, 05.10, 05.12)
- **🟡 NÊN CÓ:** 1 (05.11)

### Critical Checklist (Must-Have)
```
[ ] 05.01 — OSIV disabled (spring.jpa.open-in-view=false)
[ ] 05.02 — Không có N+1 queries (JOIN FETCH/@EntityGraph)
[ ] 05.03 — FetchType.LAZY mặc định cho @OneToMany/@ManyToMany
[ ] 05.08 — Tất cả WHERE/JOIN columns có indexes
[ ] 05.09 — Flyway migrations (ddl-auto=validate)
```

### Performance Impact Matrix
| Practice | Impact | Effort | Priority |
|----------|--------|--------|----------|
| 05.01 OSIV | 🔴 HIGH | LOW | P0 |
| 05.02 N+1 | 🔴 CRITICAL | MEDIUM | P0 |
| 05.03 LAZY | 🔴 HIGH | LOW | P0 |
| 05.04 EntityGraph | 🟠 MEDIUM | MEDIUM | P1 |
| 05.05 Projection | 🟠 MEDIUM | LOW | P1 |
| 05.06 @Version | 🟠 MEDIUM | LOW | P2 |
| 05.07 Batching | 🟠 HIGH | MEDIUM | P1 |
| 05.08 Index | 🔴 CRITICAL | LOW | P0 |
| 05.09 Flyway | 🔴 HIGH | MEDIUM | P0 |
| 05.10 Cascade | 🟠 MEDIUM | LOW | P2 |
| 05.11 NaturalId | 🟡 LOW | LOW | P3 |
| 05.12 HikariCP | 🟠 HIGH | LOW | P1 |

### Quick Wins (High Impact, Low Effort)
1. **Tắt OSIV** → 5 phút → Massive connection pool improvement
2. **Add indexes** → 10 phút → Query performance boost 10-100×
3. **FetchType.LAZY** → 5 phút → Giảm over-fetching
4. **HikariCP tuning** → 10 phút → Better connection management

### Common Pitfalls
❌ OSIV enabled (mặc định Spring Boot)
❌ N+1 queries ẩn trong service layer
❌ EAGER loading mặc định
❌ Missing indexes trên foreign keys
❌ `ddl-auto=update` trong production
❌ CascadeType.ALL mọi nơi

---

**🎯 Domain 05 focus:** Performance và data integrity là ưu tiên hàng đầu cho JPA/Hibernate applications!
