# Domain 11: Async & Messaging
> **Số practices:** 10 | 🔴 2 | 🟠 6 | 🟡 2
> **Trọng số:** ×1

## 11.01 - @Async với custom TaskExecutor (không dùng default) 🔴

### Metadata
- **ID:** BP-11.01
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -15 points/vi phạm
- **Loại:** Configuration
- **Tag:** `async`, `executor`, `thread-pool`

### Tại sao?

Default Spring async executor có cấu hình không phù hợp production:
- Unlimited thread pool → memory leak, OutOfMemoryError
- Không có queue capacity limit → unbounded queue
- Không có rejection policy → crash khi overload
- Không có monitoring metrics

Custom TaskExecutor cho phép:
- Kiểm soát số lượng thread (core + max pool size)
- Giới hạn queue capacity
- Định nghĩa rejection policy
- Đặt tên thread cho debugging
- Tích hợp monitoring

### ✅ Cách đúng

```java
// ✅ Custom AsyncConfig với named executors
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-task-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }

  @Bean(name = "emailExecutor")
  public Executor emailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("email-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
  }
}

// Service sử dụng executor cụ thể
@Service
public class NotificationService {

  @Async("emailExecutor")
  public CompletableFuture<Void> sendEmailAsync(String to, String subject, String body) {
    // Send email logic
    return CompletableFuture.completedFuture(null);
  }

  @Async("taskExecutor")
  public CompletableFuture<String> processDataAsync(String data) {
    // Process data logic
    return CompletableFuture.completedFuture("Processed: " + data);
  }
}
```

### ❌ Cách sai

```java
// ❌ Dùng default executor (unlimited threads)
@Configuration
@EnableAsync
public class BadAsyncConfig {
  // No custom executor
}

@Service
public class BadNotificationService {

  @Async // ❌ Không chỉ định executor
  public void sendEmailAsync(String to) {
    // Sử dụng default executor - NGUY HIỂM!
  }
}

// ❌ Executor không giới hạn
@Bean
public Executor badExecutor() {
  ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(Integer.MAX_VALUE); // ❌ Unlimited
  executor.setQueueCapacity(Integer.MAX_VALUE); // ❌ Unbounded
  executor.initialize();
  return executor;
}
```

### Phát hiện

```regex
# Tìm @EnableAsync không có custom executor
(?s)@EnableAsync[^}]*?class\s+\w+\s*\{(?!.*@Bean.*Executor)

# Tìm @Async không có executor name
@Async\s*(?!\()

# Tìm executor với giá trị quá lớn
setCorePoolSize\((50|100|200|Integer\.MAX_VALUE)\)
setQueueCapacity\((1000|5000|Integer\.MAX_VALUE)\)
```

### Checklist

- [ ] Mỗi @EnableAsync có ít nhất 1 custom executor bean
- [ ] Mỗi @Async method chỉ định executor name
- [ ] Core pool size < 50 (thường 5-20)
- [ ] Max pool size hợp lý (thường gấp 2x core)
- [ ] Queue capacity giới hạn (thường 50-500)
- [ ] Có RejectedExecutionHandler
- [ ] Thread name prefix có ý nghĩa
- [ ] Có waitForTasksToCompleteOnShutdown

---

## 11.02 - Thread pool sizing phù hợp workload 🟠

### Metadata
- **ID:** BP-11.02
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -8 points/vi phạm
- **Loại:** Performance
- **Tag:** `thread-pool`, `tuning`, `performance`

### Tại sao?

Thread pool size ảnh hưởng trực tiếp đến:
- **Throughput:** Quá ít thread → underutilization
- **Latency:** Quá nhiều thread → context switching overhead
- **Memory:** Mỗi thread tốn ~1MB stack memory
- **CPU:** Thread > CPU cores → thrashing

Formula tối ưu:
- **CPU-bound:** `core_count + 1`
- **I/O-bound:** `core_count * (1 + wait_time/compute_time)`
- **Mixed:** Phân tích workload và test thực tế

### ✅ Cách đúng

```java
// ✅ CPU-bound executor (computational tasks)
@Configuration
public class ExecutorConfig {

  @Bean(name = "cpuBoundExecutor")
  public Executor cpuBoundExecutor() {
    int cores = Runtime.getRuntime().availableProcessors();
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(cores + 1); // CPU-bound formula
    executor.setMaxPoolSize(cores + 1);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("cpu-");
    executor.initialize();
    return executor;
  }

  // ✅ I/O-bound executor (database, API calls)
  @Bean(name = "ioBoundExecutor")
  public Executor ioBoundExecutor() {
    int cores = Runtime.getRuntime().availableProcessors();
    // Giả sử wait_time/compute_time = 10 (90% I/O waiting)
    int poolSize = cores * (1 + 10); // = cores * 11
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(poolSize);
    executor.setMaxPoolSize(poolSize * 2);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("io-");
    executor.initialize();
    return executor;
  }

  // ✅ Configurable executor từ properties
  @Bean(name = "configExecutor")
  public Executor configExecutor(
    @Value("${app.executor.core-pool-size:10}") int corePoolSize,
    @Value("${app.executor.max-pool-size:20}") int maxPoolSize,
    @Value("${app.executor.queue-capacity:100}") int queueCapacity
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("config-");
    executor.initialize();
    return executor;
  }
}

// application.yml
/*
app:
  executor:
    core-pool-size: 10
    max-pool-size: 20
    queue-capacity: 100
*/
```

### ❌ Cách sai

```java
// ❌ Magic numbers không giải thích
@Bean
public Executor badExecutor1() {
  ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(100); // ❌ Tại sao 100?
  executor.setMaxPoolSize(200);  // ❌ Quá lớn cho I/O-bound?
  executor.initialize();
  return executor;
}

// ❌ Hardcoded values không liên quan workload
@Bean
public Executor badExecutor2() {
  ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(5);  // ❌ Tại sao không phải 4 hoặc 6?
  executor.setMaxPoolSize(10);  // ❌ Gấp đôi core - lý do?
  executor.initialize();
  return executor;
}

// ❌ Không xem xét CPU cores
@Bean
public Executor badExecutor3() {
  ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(20); // ❌ Cố định trên mọi server
  executor.setMaxPoolSize(40);
  executor.initialize();
  return executor;
}
```

### Phát hiện

```regex
# Tìm hardcoded pool size > 50
setCorePoolSize\(([5-9]\d|\d{3,})\)

# Tìm pool size không dựa trên availableProcessors
(?<!Runtime\.getRuntime\(\)\.availableProcessors)setCorePoolSize

# Tìm maxPoolSize = corePoolSize * constant
setCorePoolSize\((\d+)\).*setMaxPoolSize\(\1\s*\*\s*2\)
```

### Checklist

- [ ] CPU-bound tasks: pool size ≈ CPU cores + 1
- [ ] I/O-bound tasks: pool size tính theo wait/compute ratio
- [ ] Sử dụng Runtime.getRuntime().availableProcessors()
- [ ] Có comment giải thích công thức sizing
- [ ] Có configuration properties cho tuning
- [ ] Monitoring metrics (active threads, queue size)
- [ ] Load testing để verify sizing
- [ ] Documented capacity limits

---

## 11.03 - @EnableAsync trên configuration class riêng 🟡

### Metadata
- **ID:** BP-11.03
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -3 points/vi phạm
- **Loại:** Organization
- **Tag:** `configuration`, `separation`, `maintainability`

### Tại sao?

@EnableAsync có side effects toàn bộ application:
- Kích hoạt proxy creation cho @Async methods
- Ảnh hưởng đến bean initialization order
- Có thể conflict với @Transactional

Tách riêng giúp:
- Dễ bật/tắt async functionality
- Clear dependency injection order
- Dễ testing (mock async behavior)
- Tránh circular dependencies

### ✅ Cách đúng

```java
// ✅ Dedicated AsyncConfig class
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-");
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new CustomAsyncExceptionHandler();
  }

  @Bean(name = "emailExecutor")
  public Executor emailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setThreadNamePrefix("email-");
    executor.initialize();
    return executor;
  }
}

// Custom exception handler
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);

  @Override
  public void handleUncaughtException(Throwable ex, Method method, Object... params) {
    log.error("Async exception in method: {} with params: {}", method.getName(), params, ex);
    // Send alert, metrics, etc.
  }
}

// ✅ Profile-specific async config
@Configuration
@EnableAsync
@Profile("!test") // Disable async in tests
public class AsyncConfigProd {
  // Production async config
}

@Configuration
@Profile("test")
public class AsyncConfigTest {
  // Synchronous executor for testing
  @Bean
  public Executor taskExecutor() {
    return new SyncTaskExecutor(); // Runs synchronously
  }
}
```

### ❌ Cách sai

```java
// ❌ @EnableAsync trên main application class
@SpringBootApplication
@EnableAsync // ❌ Không nên đặt đây
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}

// ❌ @EnableAsync trên service class
@Service
@EnableAsync // ❌ SAI - chỉ dùng trên @Configuration
public class BadService {

  @Async
  public void doSomething() {
    // ...
  }
}

// ❌ Mixed với các config khác
@Configuration
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
public class MixedConfig {
  // ❌ Quá nhiều concerns trong 1 class
}
```

### Phát hiện

```regex
# Tìm @EnableAsync trên @SpringBootApplication
@SpringBootApplication[^}]*?@EnableAsync

# Tìm @EnableAsync trên service/component
@(Service|Component|Repository)[^}]*?@EnableAsync

# Tìm config class có quá nhiều @Enable annotations
@Configuration.*@Enable.*@Enable.*@Enable
```

### Checklist

- [ ] @EnableAsync trên dedicated @Configuration class
- [ ] Class tên là AsyncConfig hoặc tương tự
- [ ] Implement AsyncConfigurer interface
- [ ] Override getAsyncExecutor()
- [ ] Override getAsyncUncaughtExceptionHandler()
- [ ] Không mix với @EnableCaching, @EnableScheduling
- [ ] Có profile-specific config cho testing
- [ ] Package: config.async

---

## 11.04 - Error handling cho async methods (AsyncUncaughtExceptionHandler) 🟠

### Metadata
- **ID:** BP-11.04
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -10 points/vi phạm
- **Loại:** Error Handling
- **Tag:** `async`, `exception`, `monitoring`

### Tại sao?

Exception trong @Async methods **không tự động propagate**:
- Void methods: exception bị "nuốt" mất
- CompletableFuture: exception trong CompletionException
- Không có global exception handler như @ControllerAdvice

Hậu quả:
- Silent failures → data loss
- Không có logs → không debug được
- Không có alerts → không biết production lỗi

AsyncUncaughtExceptionHandler giải quyết:
- Catch tất cả unhandled exceptions
- Centralized logging
- Metrics & alerting
- Graceful degradation

### ✅ Cách đúng

```java
// ✅ Custom AsyncUncaughtExceptionHandler
@Component
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);
  private final MeterRegistry meterRegistry;
  private final AlertService alertService;

  public CustomAsyncExceptionHandler(MeterRegistry meterRegistry, AlertService alertService) {
    this.meterRegistry = meterRegistry;
    this.alertService = alertService;
  }

  @Override
  public void handleUncaughtException(Throwable ex, Method method, Object... params) {
    String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

    log.error("Async exception in {}: {}", methodName, ex.getMessage(), ex);

    // Metrics
    meterRegistry.counter("async.exceptions",
      "method", methodName,
      "exception", ex.getClass().getSimpleName()
    ).increment();

    // Alert for critical errors
    if (ex instanceof DatabaseException || ex instanceof PaymentException) {
      alertService.sendAlert("CRITICAL async failure in " + methodName, ex);
    }

    // Additional context
    log.debug("Method parameters: {}", Arrays.toString(params));
  }
}

// ✅ Register handler
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  private final CustomAsyncExceptionHandler exceptionHandler;

  public AsyncConfig(CustomAsyncExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return exceptionHandler;
  }
}

// ✅ Service with proper error handling
@Service
public class OrderService {

  @Async("taskExecutor")
  public CompletableFuture<Order> processOrderAsync(Long orderId) {
    try {
      Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));

      // Process order
      order.setStatus(OrderStatus.PROCESSING);
      orderRepository.save(order);

      return CompletableFuture.completedFuture(order);
    } catch (Exception ex) {
      log.error("Failed to process order {}", orderId, ex);
      // Return exceptionally completed future
      return CompletableFuture.failedFuture(ex);
    }
  }

  @Async("taskExecutor")
  public void sendNotificationAsync(Long userId, String message) {
    try {
      notificationService.send(userId, message);
    } catch (Exception ex) {
      // Log locally + global handler will catch
      log.error("Failed to send notification to user {}", userId, ex);
      throw ex; // Re-throw để AsyncUncaughtExceptionHandler bắt
    }
  }
}
```

### ❌ Cách sai

```java
// ❌ Không có exception handler
@Configuration
@EnableAsync
public class BadAsyncConfig {
  // ❌ Không implement AsyncConfigurer
  // ❌ Không có getAsyncUncaughtExceptionHandler
}

// ❌ Silent failures
@Service
public class BadOrderService {

  @Async
  public void processOrderAsync(Long orderId) {
    try {
      // Process order
    } catch (Exception ex) {
      // ❌ Swallow exception
      log.debug("Error: {}", ex.getMessage()); // Only debug level!
    }
  }

  @Async
  public void sendEmailAsync(String to, String body) {
    // ❌ Không có try-catch
    // Exception sẽ bị nuốt mất
    emailService.send(to, body);
  }
}

// ❌ Generic exception handler
@Override
public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
  return (ex, method, params) -> {
    System.out.println("Error: " + ex); // ❌ Sysout thay vì logger
    // ❌ Không có metrics
    // ❌ Không có alerting
  };
}
```

### Phát hiện

```regex
# Tìm @EnableAsync không implement AsyncConfigurer
@EnableAsync.*class\s+\w+(?!\s+implements\s+AsyncConfigurer)

# Tìm @Async void methods không có try-catch
@Async.*\n.*public\s+void\s+\w+[^{]*\{(?!.*try)

# Tìm catch blocks swallow exceptions
catch\s*\([^)]+\)\s*\{\s*\}

# Tìm log.debug trong catch blocks
catch.*\{.*log\.debug
```

### Checklist

- [ ] AsyncConfig implements AsyncConfigurer
- [ ] Override getAsyncUncaughtExceptionHandler()
- [ ] Custom handler với meaningful logging
- [ ] Log level ERROR cho exceptions
- [ ] Include method name + parameters
- [ ] Record metrics (counter/gauge)
- [ ] Alert cho critical errors
- [ ] @Async methods có try-catch
- [ ] CompletableFuture.failedFuture() cho errors
- [ ] Test exception handling

---

## 11.05 - Message queue cho cross-service communication 🟠

### Metadata
- **ID:** BP-11.05
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -10 points/vi phạm
- **Loại:** Architecture
- **Tag:** `messaging`, `microservices`, `decoupling`

### Tại sao?

**Synchronous REST calls** giữa services có vấn đề:
- Tight coupling → service A chết khi B down
- Cascading failures → domino effect
- Latency amplification → timeout chains
- No buffering → lost requests khi overload

**Message Queue** (RabbitMQ, Kafka, SQS) giải quyết:
- **Decoupling:** Services không biết nhau
- **Reliability:** Messages persisted, không mất
- **Buffering:** Queue chống load spikes
- **Scalability:** Add consumers dễ dàng
- **Async:** Non-blocking communication

### ✅ Cách đúng

```java
// ✅ RabbitMQ configuration
@Configuration
public class RabbitMQConfig {

  public static final String ORDER_EXCHANGE = "order.exchange";
  public static final String ORDER_CREATED_QUEUE = "order.created.queue";
  public static final String ORDER_CREATED_ROUTING_KEY = "order.created";

  @Bean
  public TopicExchange orderExchange() {
    return new TopicExchange(ORDER_EXCHANGE, true, false);
  }

  @Bean
  public Queue orderCreatedQueue() {
    return QueueBuilder.durable(ORDER_CREATED_QUEUE)
      .withArgument("x-dead-letter-exchange", "dlx.exchange")
      .withArgument("x-message-ttl", 3600000) // 1 hour TTL
      .build();
  }

  @Bean
  public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange orderExchange) {
    return BindingBuilder
      .bind(orderCreatedQueue)
      .to(orderExchange)
      .with(ORDER_CREATED_ROUTING_KEY);
  }

  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                       Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    template.setMandatory(true); // Return unroutable messages
    template.setReturnsCallback(returned -> {
      log.error("Message returned: {}", returned.getMessage());
    });
    return template;
  }
}

// ✅ Publisher
@Service
public class OrderEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
  private final RabbitTemplate rabbitTemplate;

  public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publishOrderCreated(OrderCreatedEvent event) {
    try {
      rabbitTemplate.convertAndSend(
        RabbitMQConfig.ORDER_EXCHANGE,
        RabbitMQConfig.ORDER_CREATED_ROUTING_KEY,
        event
      );
      log.info("Published OrderCreatedEvent: {}", event.getOrderId());
    } catch (Exception ex) {
      log.error("Failed to publish OrderCreatedEvent: {}", event.getOrderId(), ex);
      // Fallback: save to outbox table
      outboxRepository.save(new OutboxMessage(event));
    }
  }
}

// ✅ Consumer
@Component
public class OrderEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
  private final InventoryService inventoryService;

  public OrderEventConsumer(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE)
  public void handleOrderCreated(OrderCreatedEvent event,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  Channel channel) {
    try {
      log.info("Received OrderCreatedEvent: {}", event.getOrderId());

      // Idempotency check
      if (processedEventRepository.existsById(event.getEventId())) {
        log.warn("Duplicate event {}, skipping", event.getEventId());
        channel.basicAck(deliveryTag, false);
        return;
      }

      // Process
      inventoryService.reserveItems(event.getItems());

      // Mark as processed
      processedEventRepository.save(new ProcessedEvent(event.getEventId()));

      // Manual ACK
      channel.basicAck(deliveryTag, false);

    } catch (Exception ex) {
      log.error("Failed to process OrderCreatedEvent: {}", event.getOrderId(), ex);
      try {
        // Reject and requeue (hoặc gửi đến DLQ)
        channel.basicNack(deliveryTag, false, false);
      } catch (IOException ioEx) {
        log.error("Failed to NACK message", ioEx);
      }
    }
  }
}

// Event DTO
public record OrderCreatedEvent(
  String eventId,
  Long orderId,
  Long customerId,
  List<OrderItem> items,
  Instant createdAt
) {}
```

### ❌ Cách sai

```java
// ❌ Synchronous REST call giữa services
@Service
public class BadOrderService {

  private final RestTemplate restTemplate;

  public Order createOrder(CreateOrderRequest request) {
    Order order = orderRepository.save(new Order(request));

    // ❌ Blocking synchronous call
    restTemplate.postForObject(
      "http://inventory-service/reserve",
      order.getItems(),
      Void.class
    );

    // ❌ Nếu inventory-service down → order creation fails
    // ❌ Tight coupling
    // ❌ No retry mechanism

    return order;
  }
}

// ❌ Fire-and-forget với @Async (không reliable)
@Service
public class BadOrderService2 {

  @Async
  public void notifyInventoryAsync(Order order) {
    // ❌ Nếu service restart → message lost
    // ❌ Không có persistence
    // ❌ Không có retry
    restTemplate.postForObject("http://inventory-service/reserve", order.getItems(), Void.class);
  }
}

// ❌ No error handling trong consumer
@RabbitListener(queues = "order.queue")
public void handleBadOrder(OrderEvent event) {
  // ❌ Không có try-catch
  // ❌ Không có idempotency check
  // ❌ Auto-ACK (mất message nếu crash)
  inventoryService.reserve(event.getItems());
}
```

### Phát hiện

```regex
# Tìm RestTemplate calls giữa services (suspicious)
restTemplate\.(post|get|put|delete)ForObject\("http://\w+-service

# Tìm @RabbitListener không có try-catch
@RabbitListener.*\n.*public\s+void\s+\w+[^{]*\{(?!.*try)

# Tìm auto-ACK listeners (risky)
@RabbitListener(?!.*ackMode\s*=\s*AcknowledgeMode\.MANUAL)
```

### Checklist

- [ ] Message queue cho cross-service events
- [ ] Durable queues (survive broker restart)
- [ ] Dead letter queue configured
- [ ] Publisher confirms enabled
- [ ] Manual ACK trong consumers
- [ ] Idempotency check trước xử lý
- [ ] Try-catch trong message handlers
- [ ] Structured event DTOs (versioned)
- [ ] Message TTL configured
- [ ] Monitoring (queue depth, lag)

---

## 11.06 - Idempotent message consumers 🔴

### Metadata
- **ID:** BP-11.06
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -15 points/vi phạm
- **Loại:** Reliability
- **Tag:** `idempotency`, `messaging`, `data-integrity`

### Tại sao?

Message queues **không guarantee exactly-once delivery**:
- At-least-once: RabbitMQ, Kafka (mặc định)
- Duplicate messages do:
  - Network retries
  - Consumer crashes trước khi ACK
  - Rebalancing (Kafka)

Hậu quả nếu không idempotent:
- Duplicate orders → charge customer 2 lần
- Duplicate emails → spam users
- Data inconsistency → inventory incorrect

**Idempotency** đảm bảo xử lý N lần = xử lý 1 lần.

### ✅ Cách đúng

```java
// ✅ Processed events tracking
@Entity
@Table(
  name = "processed_events",
  indexes = @Index(name = "idx_event_id", columnList = "event_id", unique = true)
)
public class ProcessedEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true, length = 100)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  @Column(name = "processor", length = 100)
  private String processor; // Service instance ID

  // Constructors, getters
}

// Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
  boolean existsByEventId(String eventId);
}

// ✅ Idempotent consumer
@Component
public class PaymentEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
  private final PaymentService paymentService;
  private final ProcessedEventRepository processedEventRepository;

  @Transactional
  @RabbitListener(queues = "payment.queue", ackMode = "MANUAL")
  public void handlePaymentEvent(PaymentEvent event,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  Channel channel) {
    try {
      // ✅ Idempotency check TRƯỚC khi xử lý
      if (processedEventRepository.existsByEventId(event.getEventId())) {
        log.warn("Event {} already processed, skipping", event.getEventId());
        channel.basicAck(deliveryTag, false);
        return;
      }

      log.info("Processing payment event: {}", event.getEventId());

      // Process payment
      paymentService.processPayment(event.getOrderId(), event.getAmount());

      // ✅ Mark as processed trong cùng transaction
      ProcessedEvent processed = new ProcessedEvent();
      processed.setEventId(event.getEventId());
      processed.setEventType("PAYMENT");
      processed.setProcessedAt(Instant.now());
      processed.setProcessor(getInstanceId());
      processedEventRepository.save(processed);

      // ACK sau khi commit transaction
      channel.basicAck(deliveryTag, false);

    } catch (Exception ex) {
      log.error("Failed to process payment event: {}", event.getEventId(), ex);
      try {
        channel.basicNack(deliveryTag, false, true); // Requeue
      } catch (IOException ioEx) {
        log.error("Failed to NACK", ioEx);
      }
    }
  }

  private String getInstanceId() {
    return ManagementFactory.getRuntimeMXBean().getName();
  }
}

// ✅ Alternative: Database unique constraint
@Entity
@Table(name = "orders")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", unique = true, nullable = false)
  private String idempotencyKey; // From event.eventId

  // ...
}

@Service
public class OrderService {

  @Transactional
  public Order createOrder(OrderEvent event) {
    try {
      Order order = new Order();
      order.setIdempotencyKey(event.getEventId());
      // ...
      return orderRepository.save(order);
    } catch (DataIntegrityViolationException ex) {
      // ✅ Duplicate key → already processed
      log.warn("Order with idempotency key {} already exists", event.getEventId());
      return orderRepository.findByIdempotencyKey(event.getEventId())
        .orElseThrow();
    }
  }
}
```

### ❌ Cách sai

```java
// ❌ Không có idempotency check
@RabbitListener(queues = "order.queue")
public void handleBadOrder(OrderEvent event) {
  // ❌ Xử lý trực tiếp, không check duplicate
  Order order = new Order(event);
  orderRepository.save(order); // Duplicate nếu message replay!

  // ❌ Charge payment nhiều lần
  paymentService.charge(order.getAmount());
}

// ❌ Check idempotency KHÔNG đúng cách
@RabbitListener(queues = "payment.queue")
public void handleBadPayment(PaymentEvent event) {
  // ❌ Race condition: 2 consumers check cùng lúc
  if (!processedEventRepository.existsByEventId(event.getEventId())) {
    // ❌ Consumer B cũng qua được check này!
    processPayment(event);
    processedEventRepository.save(new ProcessedEvent(event.getEventId()));
  }
}

// ❌ Không transaction
@RabbitListener(queues = "order.queue")
public void handleNonTransactionalOrder(OrderEvent event) {
  if (processedEventRepository.existsByEventId(event.getEventId())) {
    return;
  }

  processOrder(event); // ✅ Success

  // ❌ App crashes TRƯỚC khi save processed event
  processedEventRepository.save(new ProcessedEvent(event.getEventId()));

  // → Message replay → duplicate processing!
}
```

### Phát hiện

```regex
# Tìm @RabbitListener không có idempotency check
@RabbitListener.*\n.*public\s+void\s+\w+[^{]*\{(?!.*existsBy)

# Tìm message handler không có @Transactional
@(RabbitListener|KafkaListener).*\n(?!.*@Transactional).*public\s+void

# Tìm repository.save() không có unique constraint check
orderRepository\.save\((?!.*try)
```

### Checklist

- [ ] Mỗi event có unique eventId
- [ ] ProcessedEvent entity với unique index
- [ ] Check existsByEventId() TRƯỚC xử lý
- [ ] @Transactional bao quanh toàn bộ handler
- [ ] Save processed event TRONG transaction
- [ ] Manual ACK mode
- [ ] ACK CHỈ sau khi transaction commit
- [ ] Log duplicate events (monitoring)
- [ ] Alternative: unique constraint trên business entity
- [ ] Test: gửi duplicate message

---

## 11.07 - Dead letter queue cho failed messages 🟠

### Metadata
- **ID:** BP-11.07
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -8 points/vi phạm
- **Loại:** Reliability
- **Tag:** `dlq`, `error-handling`, `observability`

### Tại sao?

Messages có thể fail do:
- Transient errors: DB timeout, network blip
- Permanent errors: Invalid data, business rule violation
- Poison messages: Malformed JSON, schema mismatch

**Retry vô hạn** gây:
- Block queue → healthy messages stuck
- Resource exhaustion → CPU 100%
- Log spam → hide real issues

**Dead Letter Queue (DLQ)** giải quyết:
- Move failed messages ra khỏi main queue
- Allow main queue processing tiếp
- Investigate failures offline
- Manual replay sau khi fix

### ✅ Cách đúng

```java
// ✅ RabbitMQ DLQ configuration
@Configuration
public class DLQConfig {

  // Main queue với DLX configured
  @Bean
  public Queue orderQueue() {
    return QueueBuilder.durable("order.queue")
      .withArgument("x-dead-letter-exchange", "dlx.exchange")
      .withArgument("x-dead-letter-routing-key", "order.dlq")
      .withArgument("x-message-ttl", 3600000) // 1 hour TTL
      .build();
  }

  // Dead Letter Exchange
  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange("dlx.exchange", true, false);
  }

  // Dead Letter Queue
  @Bean
  public Queue orderDLQ() {
    return QueueBuilder.durable("order.dlq")
      .withArgument("x-message-ttl", 604800000) // 7 days TTL
      .build();
  }

  @Bean
  public Binding dlqBinding(Queue orderDLQ, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(orderDLQ).to(deadLetterExchange).with("order.dlq");
  }
}

// ✅ Consumer với retry + DLQ
@Component
public class OrderConsumerWithDLQ {

  private static final Logger log = LoggerFactory.getLogger(OrderConsumerWithDLQ.class);
  private static final int MAX_RETRIES = 3;

  private final OrderService orderService;

  @RabbitListener(queues = "order.queue", ackMode = "MANUAL")
  public void handleOrder(OrderEvent event,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath,
                          Channel channel) {
    try {
      int retryCount = getRetryCount(xDeath);
      log.info("Processing order {} (attempt {})", event.getOrderId(), retryCount + 1);

      // Idempotency check
      if (processedEventRepository.existsByEventId(event.getEventId())) {
        channel.basicAck(deliveryTag, false);
        return;
      }

      // Process
      orderService.processOrder(event);
      processedEventRepository.save(new ProcessedEvent(event.getEventId()));

      channel.basicAck(deliveryTag, false);

    } catch (RecoverableException ex) {
      // ✅ Transient error → retry
      log.warn("Recoverable error processing order {}, will retry", event.getOrderId(), ex);
      handleRecoverableError(deliveryTag, channel, xDeath);

    } catch (PermanentException ex) {
      // ✅ Permanent error → DLQ immediately
      log.error("Permanent error processing order {}, moving to DLQ", event.getOrderId(), ex);
      handlePermanentError(deliveryTag, channel);

    } catch (Exception ex) {
      // ✅ Unknown error → retry then DLQ
      log.error("Unknown error processing order {}", event.getOrderId(), ex);
      handleRecoverableError(deliveryTag, channel, xDeath);
    }
  }

  private int getRetryCount(List<Map<String, Object>> xDeath) {
    if (xDeath == null || xDeath.isEmpty()) {
      return 0;
    }
    return ((Long) xDeath.get(0).get("count")).intValue();
  }

  private void handleRecoverableError(long deliveryTag, Channel channel,
                                       List<Map<String, Object>> xDeath) {
    try {
      int retryCount = getRetryCount(xDeath);
      if (retryCount >= MAX_RETRIES) {
        log.error("Max retries exceeded, moving to DLQ");
        channel.basicReject(deliveryTag, false); // → DLQ
      } else {
        log.info("Retrying (attempt {})", retryCount + 1);
        channel.basicNack(deliveryTag, false, true); // Requeue
      }
    } catch (IOException ex) {
      log.error("Failed to handle recoverable error", ex);
    }
  }

  private void handlePermanentError(long deliveryTag, Channel channel) {
    try {
      channel.basicReject(deliveryTag, false); // → DLQ immediately
    } catch (IOException ex) {
      log.error("Failed to reject message", ex);
    }
  }
}

// ✅ DLQ monitoring
@Component
public class DLQMonitor {

  private static final Logger log = LoggerFactory.getLogger(DLQMonitor.class);

  @Scheduled(fixedDelay = 60000) // Every minute
  public void checkDLQ(RabbitAdmin rabbitAdmin) {
    Properties properties = rabbitAdmin.getQueueProperties("order.dlq");
    if (properties != null) {
      Integer messageCount = (Integer) properties.get("QUEUE_MESSAGE_COUNT");
      if (messageCount != null && messageCount > 0) {
        log.warn("DLQ has {} messages - investigation needed!", messageCount);
        // Send alert
      }
    }
  }
}

// Exception types
public class RecoverableException extends RuntimeException {
  // DB timeout, network error, etc.
}

public class PermanentException extends RuntimeException {
  // Invalid data, business rule violation, etc.
}
```

### ❌ Cách sai

```java
// ❌ Không có DLQ
@Bean
public Queue badQueue() {
  return QueueBuilder.durable("order.queue").build();
  // ❌ Không có x-dead-letter-exchange
}

// ❌ Retry vô hạn
@RabbitListener(queues = "order.queue")
public void handleBadOrder(OrderEvent event, Channel channel, long deliveryTag) {
  try {
    orderService.process(event);
    channel.basicAck(deliveryTag, false);
  } catch (Exception ex) {
    // ❌ Always requeue → infinite loop!
    channel.basicNack(deliveryTag, false, true);
  }
}

// ❌ Swallow errors
@RabbitListener(queues = "order.queue")
public void handleWorstOrder(OrderEvent event) {
  try {
    orderService.process(event);
  } catch (Exception ex) {
    log.error("Error", ex);
    // ❌ Auto-ACK → message lost!
  }
}

// ❌ Không phân biệt error types
@RabbitListener(queues = "order.queue", ackMode = "MANUAL")
public void handleUndifferentiatedOrder(OrderEvent event, Channel channel, long deliveryTag) {
  try {
    orderService.process(event);
    channel.basicAck(deliveryTag, false);
  } catch (Exception ex) {
    // ❌ Treat tất cả errors như recoverable
    channel.basicNack(deliveryTag, false, true);
    // → Poison message retry mãi!
  }
}
```

### Phát hiện

```regex
# Tìm queue không có DLX
QueueBuilder\.durable\([^)]+\)\.build\(\)

# Tìm basicNack với requeue=true không có retry limit
basicNack\([^,]+,\s*false,\s*true\)(?!.*MAX_RETRIES)

# Tìm @RabbitListener không có ackMode=MANUAL
@RabbitListener(?!.*ackMode\s*=\s*"MANUAL")
```

### Checklist

- [ ] DLX (Dead Letter Exchange) configured
- [ ] DLQ (Dead Letter Queue) created
- [ ] Main queue có x-dead-letter-exchange arg
- [ ] Max retry count defined
- [ ] x-death header parsing
- [ ] Phân biệt recoverable vs permanent errors
- [ ] Permanent errors → DLQ immediately
- [ ] Recoverable errors → retry với limit
- [ ] DLQ monitoring (scheduled job)
- [ ] DLQ message TTL (e.g. 7 days)
- [ ] Manual replay mechanism
- [ ] Alert khi DLQ > threshold

---

## 11.08 - Message retry với exponential backoff 🟠

### Metadata
- **ID:** BP-11.08
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -7 points/vi phạm
- **Loại:** Resilience
- **Tag:** `retry`, `backoff`, `reliability`

### Tại sao?

**Immediate retry** khi service down:
- Spam failed service → worse situation
- Waste resources → CPU thrashing
- No recovery time → cascade failure

**Exponential backoff** giải quyết:
- Delay tăng theo lũy thừa: 1s, 2s, 4s, 8s, 16s
- Give service time to recover
- Reduce load on failing dependencies
- Industry standard (AWS, GCP, Azure)

Formula: `delay = min(max_delay, base_delay * 2^attempt)`

### ✅ Cách đúng

```java
// ✅ RabbitMQ với delayed message plugin
@Configuration
public class RetryConfig {

  // Retry queue với delayed exchange
  @Bean
  public CustomExchange delayedExchange() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-delayed-type", "direct");
    return new CustomExchange("retry.exchange", "x-delayed-message", true, false, args);
  }

  @Bean
  public Queue retryQueue() {
    return QueueBuilder.durable("order.retry.queue").build();
  }

  @Bean
  public Binding retryBinding(Queue retryQueue, CustomExchange delayedExchange) {
    return BindingBuilder.bind(retryQueue).to(delayedExchange).with("order.retry").noargs();
  }
}

// ✅ Consumer với exponential backoff
@Component
public class OrderConsumerWithBackoff {

  private static final Logger log = LoggerFactory.getLogger(OrderConsumerWithBackoff.class);
  private static final int MAX_RETRIES = 5;
  private static final long BASE_DELAY_MS = 1000; // 1 second
  private static final long MAX_DELAY_MS = 300000; // 5 minutes

  private final RabbitTemplate rabbitTemplate;
  private final OrderService orderService;

  @RabbitListener(queues = "order.queue", ackMode = "MANUAL")
  public void handleOrder(OrderEvent event,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(value = "x-retry-count", required = false, defaultValue = "0") int retryCount,
                          Channel channel) {
    try {
      log.info("Processing order {} (attempt {})", event.getOrderId(), retryCount + 1);

      orderService.processOrder(event);
      channel.basicAck(deliveryTag, false);

    } catch (RecoverableException ex) {
      log.warn("Recoverable error on attempt {}: {}", retryCount + 1, ex.getMessage());

      if (retryCount < MAX_RETRIES) {
        // ✅ Calculate exponential delay
        long delay = calculateDelay(retryCount);
        log.info("Retrying after {} ms (attempt {})", delay, retryCount + 2);

        // ✅ Send to delayed exchange
        retryWithDelay(event, retryCount + 1, delay);
        channel.basicAck(deliveryTag, false); // ACK original
      } else {
        log.error("Max retries exceeded, moving to DLQ");
        channel.basicReject(deliveryTag, false); // → DLQ
      }

    } catch (Exception ex) {
      log.error("Permanent error, moving to DLQ", ex);
      try {
        channel.basicReject(deliveryTag, false);
      } catch (IOException ioEx) {
        log.error("Failed to reject", ioEx);
      }
    }
  }

  private long calculateDelay(int retryCount) {
    // Exponential backoff: 1s, 2s, 4s, 8s, 16s, ... max 5m
    long delay = BASE_DELAY_MS * (long) Math.pow(2, retryCount);
    return Math.min(delay, MAX_DELAY_MS);
  }

  private void retryWithDelay(OrderEvent event, int retryCount, long delayMs) {
    rabbitTemplate.convertAndSend(
      "retry.exchange",
      "order.retry",
      event,
      message -> {
        message.getMessageProperties().setHeader("x-delay", delayMs);
        message.getMessageProperties().setHeader("x-retry-count", retryCount);
        return message;
      }
    );
  }
}

// ✅ Alternative: Spring Retry annotation
@Service
public class OrderServiceWithRetry {

  private static final Logger log = LoggerFactory.getLogger(OrderServiceWithRetry.class);

  @Retryable(
    retryFor = RecoverableException.class,
    maxAttempts = 5,
    backoff = @Backoff(
      delay = 1000,        // Initial delay: 1s
      multiplier = 2.0,    // Exponential: 2x
      maxDelay = 300000    // Max delay: 5 minutes
    )
  )
  public void processOrder(OrderEvent event) {
    log.info("Processing order {}", event.getOrderId());
    // Business logic that may throw RecoverableException
    externalService.call(event);
  }

  @Recover
  public void recover(RecoverableException ex, OrderEvent event) {
    log.error("Recovery after max retries for order {}", event.getOrderId(), ex);
    // Move to DLQ or send alert
    dlqPublisher.send(event);
  }
}

// Enable Spring Retry
@Configuration
@EnableRetry
public class RetryConfiguration {
}
```

### ❌ Cách sai

```java
// ❌ Immediate retry (no delay)
@RabbitListener(queues = "order.queue")
public void handleBadOrder1(OrderEvent event, Channel channel, long deliveryTag) {
  try {
    orderService.process(event);
    channel.basicAck(deliveryTag, false);
  } catch (Exception ex) {
    // ❌ Immediate requeue → spam
    channel.basicNack(deliveryTag, false, true);
  }
}

// ❌ Fixed delay (linear backoff)
private void badRetryWithFixedDelay(OrderEvent event, int retryCount) {
  long delay = 5000; // ❌ Always 5s
  // Not exponential!
  retryWithDelay(event, retryCount, delay);
}

// ❌ No max delay cap
private long badCalculateDelay(int retryCount) {
  // ❌ Delay có thể vô cực lớn
  return BASE_DELAY_MS * (long) Math.pow(2, retryCount);
  // Attempt 20: 1s * 2^20 = 1048576s = 12 days!
}

// ❌ @Retryable không có backoff
@Retryable(maxAttempts = 5) // ❌ Thiếu @Backoff
public void badRetryableMethod() {
  // Retry immediately without delay
}
```

### Phát hiện

```regex
# Tìm basicNack requeue không có delay
basicNack\([^,]+,\s*false,\s*true\)(?!.*delay)

# Tìm @Retryable không có @Backoff
@Retryable(?!.*backoff\s*=)

# Tìm retry logic với fixed delay
delay\s*=\s*\d+;.*retry
```

### Checklist

- [ ] Exponential backoff formula implemented
- [ ] Base delay reasonable (1-5 seconds)
- [ ] Multiplier = 2.0 (standard)
- [ ] Max delay cap (e.g. 5 minutes)
- [ ] Max retries limit (e.g. 3-5)
- [ ] Use RabbitMQ delayed exchange HOẶC Spring @Retryable
- [ ] Log retry attempts với delay duration
- [ ] @Recover method cho max retries
- [ ] Jitter optional (randomization)
- [ ] Metrics: retry_count, retry_delay

---

## 11.09 - Transaction outbox pattern cho reliable messaging 🟠

### Metadata
- **ID:** BP-11.09
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -10 points/vi phạm
- **Loại:** Architecture
- **Tag:** `transactional-outbox`, `reliability`, `distributed-systems`

### Tại sao?

**Dual-write problem**: Update DB + publish message không atomic:

```
1. Save order to DB          ✅
2. Publish OrderCreated event ❌ (app crashes)
→ Order tồn tại nhưng không có event → downstream services không biết!
```

HOẶC:

```
1. Publish OrderCreated event ✅
2. Save order to DB           ❌ (DB error)
→ Event đã publish nhưng order không tồn tại → invalid state!
```

**Transactional Outbox Pattern** giải quyết:
- Save business entity + outbox message trong **1 transaction**
- Background worker poll outbox table → publish messages
- Guarantee: Nếu DB commit → message sẽ được publish
- At-least-once delivery → combine với idempotency

### ✅ Cách đúng

```java
// ✅ Outbox entity
@Entity
@Table(
  name = "outbox_messages",
  indexes = {
    @Index(name = "idx_status_created", columnList = "status,created_at"),
    @Index(name = "idx_aggregate_id", columnList = "aggregate_id")
  }
)
public class OutboxMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "aggregate_type", nullable = false, length = 50)
  private String aggregateType; // "ORDER", "PAYMENT"

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType; // "OrderCreated", "PaymentCompleted"

  @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
  private String payload; // JSON

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "retry_count")
  private Integer retryCount = 0;

  // Getters, setters
}

enum OutboxStatus {
  PENDING, PUBLISHED, FAILED
}

// Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

  @Query("SELECT o FROM OutboxMessage o WHERE o.status = :status ORDER BY o.createdAt ASC")
  List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}

// ✅ Service lưu business entity + outbox trong 1 transaction
@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final OutboxMessageRepository outboxRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public Order createOrder(CreateOrderRequest request) {
    // 1. Save business entity
    Order order = new Order();
    order.setCustomerId(request.customerId());
    order.setItems(request.items());
    order.setStatus(OrderStatus.CREATED);
    order = orderRepository.save(order);

    // 2. Save outbox message trong cùng transaction
    OrderCreatedEvent event = new OrderCreatedEvent(
      UUID.randomUUID().toString(),
      order.getId(),
      order.getCustomerId(),
      order.getItems(),
      Instant.now()
    );

    OutboxMessage outbox = new OutboxMessage();
    outbox.setAggregateType("ORDER");
    outbox.setAggregateId(order.getId().toString());
    outbox.setEventType("OrderCreated");
    outbox.setPayload(objectMapper.writeValueAsString(event));
    outboxRepository.save(outbox);

    // ✅ BOTH saved trong 1 transaction
    // Nếu 1 trong 2 fail → rollback all

    return order;
  }
}

// ✅ Background worker publish messages
@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final int BATCH_SIZE = 100;
  private static final int MAX_RETRIES = 3;

  private final OutboxMessageRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  @Scheduled(fixedDelay = 5000) // Every 5 seconds
  @Transactional
  public void publishPendingMessages() {
    List<OutboxMessage> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
      OutboxStatus.PENDING,
      PageRequest.of(0, BATCH_SIZE)
    );

    if (pending.isEmpty()) {
      return;
    }

    log.info("Publishing {} pending outbox messages", pending.size());

    for (OutboxMessage message : pending) {
      try {
        // Publish to message broker
        rabbitTemplate.convertAndSend(
          getExchange(message.getAggregateType()),
          getRoutingKey(message.getEventType()),
          message.getPayload()
        );

        // Mark as published
        message.setStatus(OutboxStatus.PUBLISHED);
        message.setPublishedAt(Instant.now());
        outboxRepository.save(message);

        log.debug("Published outbox message {}", message.getId());

      } catch (Exception ex) {
        log.error("Failed to publish outbox message {}", message.getId(), ex);

        message.setRetryCount(message.getRetryCount() + 1);
        if (message.getRetryCount() >= MAX_RETRIES) {
          message.setStatus(OutboxStatus.FAILED);
          // Send alert
        }
        outboxRepository.save(message);
      }
    }
  }

  private String getExchange(String aggregateType) {
    return aggregateType.toLowerCase() + ".exchange";
  }

  private String getRoutingKey(String eventType) {
    return eventType.replaceAll("([A-Z])", ".$1").toLowerCase().substring(1);
  }
}

// ✅ Cleanup old published messages
@Component
public class OutboxCleaner {

  @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
  @Transactional
  public void cleanupOldMessages(OutboxMessageRepository repository) {
    Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
    repository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, cutoff);
  }
}
```

### ❌ Cách sai

```java
// ❌ Dual-write problem
@Service
public class BadOrderService {

  @Transactional
  public Order createOrderBad(CreateOrderRequest request) {
    // 1. Save to DB
    Order order = orderRepository.save(new Order(request));

    // 2. Publish event OUTSIDE transaction
    // ❌ Nếu app crash giữa 1 và 2 → message lost!
    rabbitTemplate.convertAndSend("order.exchange", "order.created", order);

    return order;
  }
}

// ❌ Publish trước, save sau
@Service
public class BadOrderService2 {

  public Order createOrderWorse(CreateOrderRequest request) {
    Order order = new Order(request);

    // ❌ Publish TRƯỚC khi save
    rabbitTemplate.convertAndSend("order.exchange", "order.created", order);

    // ❌ Nếu save fail → message đã publish!
    return orderRepository.save(order);
  }
}

// ❌ Không có retry mechanism
@Scheduled(fixedDelay = 5000)
public void badPublisher() {
  List<OutboxMessage> pending = outboxRepository.findByStatus(OutboxStatus.PENDING);
  for (OutboxMessage msg : pending) {
    rabbitTemplate.send(msg.getPayload());
    // ❌ Nếu send fail → không retry, message stuck!
    outboxRepository.delete(msg);
  }
}
```

### Phát hiện

```regex
# Tìm rabbitTemplate trong @Transactional method (suspicious)
@Transactional.*\n.*rabbitTemplate\.(send|convertAndSend)

# Tìm repository.save() không có outbox
orderRepository\.save\((?!.*outboxRepository)

# Tìm publish không có try-catch
rabbitTemplate\.(send|convertAndSend)\([^;]+;(?!.*catch)
```

### Checklist

- [ ] OutboxMessage entity với indexes
- [ ] Business logic + outbox save trong 1 @Transactional
- [ ] Background scheduled job poll outbox
- [ ] Batch processing (e.g. 100 messages/batch)
- [ ] Retry mechanism với retry_count
- [ ] Mark PUBLISHED sau successful send
- [ ] Mark FAILED sau max retries
- [ ] Cleanup job xóa old published messages
- [ ] Monitoring: pending count, failed count
- [ ] Alert khi có FAILED messages

---

## 11.10 - CompletableFuture cho parallel async operations 🟡

### Metadata
- **ID:** BP-11.10
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -5 points/vi phạm
- **Loại:** Performance
- **Tag:** `async`, `parallelism`, `completablefuture`

### Tại sao?

**Sequential execution** chậm khi có independent tasks:

```
Task A: 100ms
Task B: 200ms  (independent)
Task C: 150ms  (independent)
Total: 100 + 200 + 150 = 450ms
```

**Parallel execution** với CompletableFuture:

```
Task A: 100ms ┐
Task B: 200ms ├─ Parallel
Task C: 150ms ┘
Total: max(100, 200, 150) = 200ms (2.25x faster!)
```

CompletableFuture cung cấp:
- Non-blocking async execution
- Composable operations (thenApply, thenCompose)
- Error handling (exceptionally, handle)
- Combine multiple futures (allOf, anyOf)

### ✅ Cách đúng

```java
// ✅ Service với parallel async operations
@Service
public class UserProfileService {

  private final UserRepository userRepository;
  private final OrderService orderService;
  private final RecommendationService recommendationService;
  private final NotificationService notificationService;
  private final Executor taskExecutor;

  @Async("taskExecutor")
  public CompletableFuture<User> getUserAsync(Long userId) {
    return CompletableFuture.completedFuture(
      userRepository.findById(userId).orElseThrow()
    );
  }

  @Async("taskExecutor")
  public CompletableFuture<List<Order>> getOrdersAsync(Long userId) {
    return CompletableFuture.completedFuture(
      orderService.findByUserId(userId)
    );
  }

  @Async("taskExecutor")
  public CompletableFuture<List<Product>> getRecommendationsAsync(Long userId) {
    return CompletableFuture.completedFuture(
      recommendationService.getRecommendations(userId)
    );
  }

  // ✅ Combine multiple futures
  public UserProfile buildUserProfile(Long userId) {
    CompletableFuture<User> userFuture = getUserAsync(userId);
    CompletableFuture<List<Order>> ordersFuture = getOrdersAsync(userId);
    CompletableFuture<List<Product>> recsFuture = getRecommendationsAsync(userId);

    // ✅ Wait for all to complete
    CompletableFuture<Void> allFutures = CompletableFuture.allOf(
      userFuture, ordersFuture, recsFuture
    );

    return allFutures.thenApply(v -> {
      User user = userFuture.join();
      List<Order> orders = ordersFuture.join();
      List<Product> recs = recsFuture.join();

      return new UserProfile(user, orders, recs);
    }).join();
  }

  // ✅ With error handling
  public CompletableFuture<UserProfile> buildUserProfileAsync(Long userId) {
    return CompletableFuture.supplyAsync(() -> userId, taskExecutor)
      .thenCompose(id -> {
        CompletableFuture<User> userFuture = getUserAsync(id);
        CompletableFuture<List<Order>> ordersFuture = getOrdersAsync(id)
          .exceptionally(ex -> {
            log.warn("Failed to load orders, using empty list", ex);
            return List.of();
          });
        CompletableFuture<List<Product>> recsFuture = getRecommendationsAsync(id)
          .exceptionally(ex -> {
            log.warn("Failed to load recommendations, using empty list", ex);
            return List.of();
          });

        return CompletableFuture.allOf(userFuture, ordersFuture, recsFuture)
          .thenApply(v -> new UserProfile(
            userFuture.join(),
            ordersFuture.join(),
            recsFuture.join()
          ));
      })
      .exceptionally(ex -> {
        log.error("Failed to build user profile", ex);
        throw new ProfileException("Failed to build profile", ex);
      });
  }

  // ✅ Timeout handling
  public UserProfile buildUserProfileWithTimeout(Long userId) {
    return buildUserProfileAsync(userId)
      .orTimeout(5, TimeUnit.SECONDS)
      .exceptionally(ex -> {
        if (ex instanceof TimeoutException) {
          log.error("User profile build timed out after 5s");
          throw new ProfileTimeoutException(ex);
        }
        throw new ProfileException(ex);
      })
      .join();
  }

  // ✅ Race condition (fastest wins)
  public CompletableFuture<String> getDataFromFastestSource(Long id) {
    CompletableFuture<String> source1 = getFromPrimaryDB(id);
    CompletableFuture<String> source2 = getFromCache(id);
    CompletableFuture<String> source3 = getFromBackupDB(id);

    return CompletableFuture.anyOf(source1, source2, source3)
      .thenApply(result -> (String) result);
  }

  // ✅ Sequential composition
  public CompletableFuture<Order> placeOrder(CreateOrderRequest request) {
    return validateStockAsync(request.items())
      .thenCompose(valid -> {
        if (!valid) {
          return CompletableFuture.failedFuture(new OutOfStockException());
        }
        return createOrderAsync(request);
      })
      .thenCompose(order -> reserveInventoryAsync(order))
      .thenCompose(order -> processPaymentAsync(order))
      .thenApply(order -> {
        notificationService.sendOrderConfirmation(order);
        return order;
      });
  }
}
```

### ❌ Cách sai

```java
// ❌ Sequential execution (slow)
public UserProfile badBuildProfile(Long userId) {
  User user = getUserSync(userId);              // 100ms
  List<Order> orders = getOrdersSync(userId);   // 200ms
  List<Product> recs = getRecsSync(userId);     // 150ms
  // Total: 450ms (should be 200ms with parallel!)

  return new UserProfile(user, orders, recs);
}

// ❌ Blocking on futures immediately
public UserProfile badBuildProfileAsync(Long userId) {
  CompletableFuture<User> userFuture = getUserAsync(userId);
  User user = userFuture.join(); // ❌ Block immediately!

  CompletableFuture<List<Order>> ordersFuture = getOrdersAsync(userId);
  List<Order> orders = ordersFuture.join(); // ❌ Block immediately!

  // ❌ Không parallel, vẫn sequential!
  return new UserProfile(user, orders, List.of());
}

// ❌ No error handling
public UserProfile badBuildProfileNoErrorHandling(Long userId) {
  CompletableFuture<User> userFuture = getUserAsync(userId);
  CompletableFuture<List<Order>> ordersFuture = getOrdersAsync(userId);

  // ❌ Nếu 1 future fail → unhandled exception
  return new UserProfile(
    userFuture.join(),
    ordersFuture.join(),
    List.of()
  );
}

// ❌ Use @Async void instead of CompletableFuture
@Async
public void badAsyncVoid(Long userId) {
  // ❌ Caller không biết khi nào xong
  // ❌ Không có return value
  // ❌ Không compose được
  processUser(userId);
}
```

### Phát hiện

```regex
# Tìm join() ngay sau async call
CompletableFuture.*=.*Async\([^;]+;.*\.join\(\)

# Tìm @Async void methods (should return CompletableFuture)
@Async.*\n.*public\s+void\s+\w+

# Tìm sequential calls có thể parallel
\w+Sync\([^)]+\);.*\n.*\w+Sync\([^)]+\);
```

### Checklist

- [ ] @Async methods return CompletableFuture<T>
- [ ] Parallel independent tasks với CompletableFuture.allOf()
- [ ] Error handling với exceptionally() hoặc handle()
- [ ] Timeout với orTimeout() hoặc completeOnTimeout()
- [ ] Sequential composition với thenCompose()
- [ ] Transformation với thenApply()
- [ ] Không block với join() trong async context
- [ ] Use anyOf() cho race conditions
- [ ] Specify executor cho supplyAsync()
- [ ] Log errors trong exception handlers

---

## Summary

| # | Best Practice | Mức | Points |
|---|--------------|-----|--------|
| 11.01 | @Async với custom TaskExecutor | 🔴 BẮT BUỘC | -15 |
| 11.02 | Thread pool sizing phù hợp workload | 🟠 KHUYẾN NGHỊ | -8 |
| 11.03 | @EnableAsync trên configuration class riêng | 🟡 NÊN CÓ | -3 |
| 11.04 | Error handling cho async methods | 🟠 KHUYẾN NGHỊ | -10 |
| 11.05 | Message queue cho cross-service communication | 🟠 KHUYẾN NGHỊ | -10 |
| 11.06 | Idempotent message consumers | 🔴 BẮT BUỘC | -15 |
| 11.07 | Dead letter queue cho failed messages | 🟠 KHUYẾN NGHỊ | -8 |
| 11.08 | Message retry với exponential backoff | 🟠 KHUYẾN NGHỊ | -7 |
| 11.09 | Transaction outbox pattern | 🟠 KHUYẾN NGHỊ | -10 |
| 11.10 | CompletableFuture cho parallel operations | 🟡 NÊN CÓ | -5 |

**Total Max Penalty:** -91 points
