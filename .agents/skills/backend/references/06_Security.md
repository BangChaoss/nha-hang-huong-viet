# Domain 06: Security

> **Số practices:** 12 | 🔴 8 | 🟠 3 | 🟡 1
> **Trọng số:** ×3 (QUAN TRỌNG NHẤT)
> **Tổng điểm tối đa bị trừ:** 120 (8×10 + 3×5 + 1×2)

Security là domain quan trọng nhất trong ứng dụng web. Vi phạm bảo mật có thể dẫn đến data breach, tổn thất tài chính nghiêm trọng, mất uy tín, và trách nhiệm pháp lý. Domain này tuân thủ OWASP Top 10 và các chuẩn mực bảo mật hiện đại.

---

## 06.01 — BCryptPasswordEncoder cho password hashing

### Metadata
- **Mã số:** 06.01
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `authentication`, `password-hashing`, `cryptography`

### Tại sao?

Password KHÔNG BAO GIỜ được lưu dạng plaintext hoặc mã hóa reversible (MD5, SHA1, Base64). BCrypt là thuật toán hashing được thiết kế riêng cho password với salt tự động và cost factor điều chỉnh được, chống lại brute-force và rainbow table attacks. Các thuật toán cũ như MD5/SHA1 quá nhanh (attacker hash hàng tỷ password/giây) và dễ bị collision attacks.

**Hậu quả vi phạm:** Data breach → toàn bộ password bị lộ → credential stuffing attacks → compromise tài khoản khác của user. **CWE-916** (Use of Password Hash With Insufficient Computational Effort), **CWE-759** (Use of a One-Way Hash without a Salt).

### ✅ Cách đúng

```java
// SecurityConfig.java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    // BCrypt với strength = 10 (2^10 = 1024 rounds)
    // Càng cao càng an toàn nhưng càng chậm (10-12 là cân bằng)
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/public/**").permitAll()
        .anyRequest().authenticated()
      )
      .formLogin(form -> form.permitAll())
      .logout(logout -> logout.permitAll());
    return http.build();
  }
}

// UserService.java
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public void registerUser(String username, String rawPassword) {
    // BCrypt tự động generate salt và embed vào hash
    String hashedPassword = passwordEncoder.encode(rawPassword);

    User user = User.builder()
      .username(username)
      .password(hashedPassword) // Lưu hash, không bao giờ lưu rawPassword
      .build();

    userRepository.save(user);
  }

  public boolean authenticate(String username, String rawPassword) {
    User user = userRepository.findByUsername(username)
      .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    // BCrypt so sánh hash một cách constant-time (chống timing attack)
    return passwordEncoder.matches(rawPassword, user.getPassword());
  }
}
```

### ❌ Cách sai

```java
// ❌ KHÔNG BAO GIỜ LÀM NHƯ NÀY
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class InsecureUserService {

  // ❌ SAI: Lưu plaintext password
  public void registerUser(String username, String password) {
    User user = new User(username, password); // Catastrophic!
    userRepository.save(user);
  }

  // ❌ SAI: Dùng MD5 (bị crack trong vài giây)
  public String hashPasswordMD5(String password) {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] hash = md.digest(password.getBytes());
    return Base64.getEncoder().encodeToString(hash); // Reversible!
  }

  // ❌ SAI: SHA-256 không có salt (dễ bị rainbow table)
  public String hashPasswordSHA256(String password) {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return Base64.getEncoder().encodeToString(md.digest(password.getBytes()));
  }

  // ❌ SAI: Custom weak hashing
  public String weakHash(String password) {
    return Integer.toHexString(password.hashCode()); // Collision heaven!
  }
}
```

### Phát hiện

```regex
# Tìm lưu plaintext password
\.setPassword\s*\(\s*(?!passwordEncoder|encoded|hashed).*\)

# Tìm MD5/SHA hashing
MessageDigest\.getInstance\s*\(\s*["'](MD5|SHA-1|SHA1)["']\s*\)

# Tìm Base64 encoding (có thể là password)
Base64\.getEncoder\(\)\.encodeToString.*password
```

### Checklist

- [ ] Bean `PasswordEncoder` (BCrypt) được khai báo trong SecurityConfig
- [ ] Tất cả password đều được hash bằng `passwordEncoder.encode()` trước khi lưu DB
- [ ] So sánh password dùng `passwordEncoder.matches()`, KHÔNG so sánh trực tiếp hash
- [ ] KHÔNG có MD5/SHA-1/SHA-256 cho password hashing
- [ ] KHÔNG có plaintext password trong log/database/memory dumps
- [ ] BCrypt strength >= 10 (default là 10)

---

## 06.02 — CSRF protection enabled (trừ stateless API)

### Metadata
- **Mã số:** 06.02
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `csrf`, `web-security`, `session`

### Tại sao?

Cross-Site Request Forgery (CSRF) tấn công bằng cách lừa user đã authenticated gửi request độc hại từ trang web khác. Nếu application dùng session-based authentication (cookie), phải enable CSRF protection. **Ngoại lệ:** Stateless API dùng JWT trong Authorization header thì tắt CSRF được (vì browser không tự động gửi header như cookie).

**Hậu quả vi phạm:** Attacker thực hiện unauthorized actions với quyền của victim (chuyển tiền, đổi password, xóa data). **CWE-352** (Cross-Site Request Forgery).

### ✅ Cách đúng

```java
// SecurityConfig.java - Session-based application
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/public/**").permitAll()
        .anyRequest().authenticated()
      )
      .formLogin(form -> form.permitAll())
      // ✅ CSRF enabled (mặc định) với cookie-based token
      .csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        // Frontend JavaScript đọc XSRF-TOKEN cookie và gửi trong X-XSRF-TOKEN header
      );
    return http.build();
  }
}

// Frontend (React/Vue) gửi CSRF token
// axios.defaults.headers.common['X-XSRF-TOKEN'] = getCookie('XSRF-TOKEN');
```

```java
// SecurityConfig.java - Stateless JWT API
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class JwtSecurityConfig {

  @Bean
  public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()
        .anyRequest().authenticated()
      )
      .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      // ✅ Tắt CSRF cho stateless API (JWT trong Authorization header)
      .csrf(csrf -> csrf.disable())
      .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter();
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Tắt CSRF cho session-based application
@Configuration
public class InsecureConfig {

  @Bean
  public SecurityFilterChain insecureFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
      .formLogin(form -> form.permitAll())
      .csrf(csrf -> csrf.disable()); // ❌ NGUY HIỂM nếu dùng session/cookie!
    return http.build();
  }
}

// ❌ SAI: CSRF enabled cho stateless JWT API (không cần thiết và gây lỗi)
@Configuration
public class ConfusedConfig {

  @Bean
  public SecurityFilterChain confusedFilterChain(HttpSecurity http) throws Exception {
    http
      .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      // ❌ CSRF không hoạt động với stateless (không có session lưu token)
      .csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
      );
    return http.build();
  }
}
```

### Phát hiện

```regex
# Tìm csrf().disable() (cần review xem có hợp lý không)
\.csrf\s*\(\s*csrf\s*->\s*csrf\.disable\(\)

# Tìm SessionCreationPolicy.STATELESS với CSRF enabled (conflict)
SessionCreationPolicy\.STATELESS.*\n.*\.csrf\((?!.*disable)
```

### Checklist

- [ ] Session-based app: CSRF **ENABLED** (mặc định hoặc explicit config)
- [ ] Stateless JWT API: CSRF **DISABLED** + `SessionCreationPolicy.STATELESS`
- [ ] Frontend gửi CSRF token trong header `X-XSRF-TOKEN` (session-based)
- [ ] CSRF token repository là `CookieCsrfTokenRepository` hoặc custom secure implementation
- [ ] Tất cả state-changing endpoints (POST/PUT/DELETE) được CSRF protect
- [ ] Public endpoints (login, register) exempt khỏi CSRF nếu cần

---

## 06.03 — Method-level security (@PreAuthorize, @Secured)

### Metadata
- **Mã số:** 06.03
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `security`, `authorization`, `rbac`, `method-security`

### Tại sao?

URL-based security (`authorizeHttpRequests`) không đủ cho authorization phức tạp. Method-level security cho phép kiểm tra quyền dựa trên role, ownership, business logic ngay tại service layer. `@PreAuthorize` hỗ trợ SpEL expressions mạnh mẽ (check role + dynamic conditions). Đây là defense-in-depth: URL filter là layer đầu, method security là layer thứ hai.

**Hậu quả vi phạm:** Privilege escalation, unauthorized data access (user A đọc/sửa data của user B). **CWE-862** (Missing Authorization).

### ✅ Cách đúng

```java
// SecurityConfig.java - Enable method security
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class MethodSecurityConfig {
  // Chỉ cần annotation này, không cần thêm code
}

// DoctorService.java
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorService {

  private final DoctorRepository doctorRepository;
  private final AppointmentRepository appointmentRepository;

  // ✅ Chỉ ADMIN hoặc CLINIC_MANAGER được thêm doctor
  @PreAuthorize("hasAnyRole('ADMIN', 'CLINIC_MANAGER')")
  public Doctor createDoctor(CreateDoctorRequest request) {
    Doctor doctor = Doctor.builder()
      .name(request.name())
      .specialization(request.specialization())
      .build();
    return doctorRepository.save(doctor);
  }

  // ✅ Chỉ owner hoặc ADMIN được update
  @PreAuthorize("hasRole('ADMIN') or #doctorId == authentication.principal.id")
  public Doctor updateDoctor(Long doctorId, UpdateDoctorRequest request) {
    Doctor doctor = doctorRepository.findById(doctorId)
      .orElseThrow(() -> new NotFoundException("Doctor not found"));

    doctor.setName(request.name());
    doctor.setSpecialization(request.specialization());
    return doctorRepository.save(doctor);
  }

  // ✅ Chỉ doctor được xem appointment của chính mình
  @PreAuthorize("@appointmentSecurityService.isAppointmentDoctor(#appointmentId, authentication)")
  public Appointment getAppointment(Long appointmentId) {
    return appointmentRepository.findById(appointmentId)
      .orElseThrow(() -> new NotFoundException("Appointment not found"));
  }

  // ✅ Public method (không cần authorization)
  public List<Doctor> findAllDoctors() {
    return doctorRepository.findAll();
  }
}

// AppointmentSecurityService.java - Custom security logic
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service("appointmentSecurityService")
@RequiredArgsConstructor
public class AppointmentSecurityService {

  private final AppointmentRepository appointmentRepository;

  public boolean isAppointmentDoctor(Long appointmentId, Authentication auth) {
    Appointment appointment = appointmentRepository.findById(appointmentId)
      .orElse(null);
    if (appointment == null) {
      return false;
    }

    Long currentUserId = Long.parseLong(auth.getName());
    return appointment.getDoctorId().equals(currentUserId);
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Không có method-level security
@Service
public class InsecureDoctorService {

  // ❌ Bất kỳ ai authenticated đều tạo được doctor
  public Doctor createDoctor(CreateDoctorRequest request) {
    return doctorRepository.save(new Doctor(...));
  }

  // ❌ User A có thể update thông tin của User B
  public Doctor updateDoctor(Long doctorId, UpdateDoctorRequest request) {
    Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
    doctor.setName(request.name());
    return doctorRepository.save(doctor);
  }

  // ❌ Check authorization bằng if-else trong code (messy, dễ quên)
  public Appointment getAppointment(Long appointmentId, Long currentUserId) {
    Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();

    if (!appointment.getDoctorId().equals(currentUserId)) {
      throw new AccessDeniedException("Not authorized"); // Hardcoded logic
    }
    return appointment;
  }
}
```

### Phát hiện

```regex
# Tìm service methods không có @PreAuthorize/@Secured
public\s+\w+\s+\w+\s*\((?!.*@PreAuthorize|@Secured)

# Tìm hardcoded authorization checks (nên dùng @PreAuthorize)
if\s*\(.*hasRole|hasAuthority|isAuthenticated.*throw.*AccessDeniedException
```

### Checklist

- [ ] `@EnableMethodSecurity` được enable trong SecurityConfig
- [ ] Tất cả sensitive operations (CREATE/UPDATE/DELETE) có `@PreAuthorize`
- [ ] SpEL expressions kiểm tra role + ownership khi cần
- [ ] Custom security logic được tách ra `@Service` riêng (tái sử dụng)
- [ ] Public methods (READ all) không cần `@PreAuthorize`
- [ ] Test coverage cho authorization failures (403 Forbidden)

---

## 06.04 — JWT validation đầy đủ (signature, expiry, issuer)

### Metadata
- **Mã số:** 06.04
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `jwt`, `authentication`, `token-validation`

### Tại sao?

JWT (JSON Web Token) là stateless authentication mechanism phổ biến cho REST API. Tuy nhiên, JWT dễ bị tấn công nếu không validate đúng: signature forgery (dùng key sai hoặc algorithm "none"), expired token replay, issuer spoofing. Phải validate **signature** (bằng secret key), **expiry time** (exp claim), **issuer** (iss claim), và optional **audience** (aud claim).

**Hậu quả vi phạm:** Unauthorized access, token forgery, session hijacking. **CWE-347** (Improper Verification of Cryptographic Signature), **CWE-613** (Insufficient Session Expiration).

### ✅ Cách đúng

```java
// JwtService.java
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

  @Value("${jwt.secret}")
  private String secretKeyString; // Từ environment variable

  @Value("${jwt.expiration-ms:3600000}") // 1 hour
  private long expirationMs;

  @Value("${jwt.issuer:medicalbox-api}")
  private String issuer;

  private SecretKey getSecretKey() {
    // ✅ Dùng SecretKey từ string (hoặc generate bằng Keys.secretKeyFor())
    return Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(String username, String role) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
      .setSubject(username)
      .claim("role", role)
      .setIssuedAt(now)
      .setExpiration(expiryDate)
      .setIssuer(issuer) // ✅ Set issuer
      .signWith(getSecretKey(), SignatureAlgorithm.HS256) // ✅ Sign với HS256
      .compact();
  }

  public Claims validateTokenAndGetClaims(String token) {
    // ✅ Validate signature, expiry, issuer trong một bước
    return Jwts.parserBuilder()
      .setSigningKey(getSecretKey()) // ✅ Verify signature
      .requireIssuer(issuer) // ✅ Require issuer khớp
      .build()
      .parseClaimsJws(token) // ✅ Tự động check expiry (exp claim)
      .getBody();
    // Throws JwtException nếu invalid/expired/wrong signature
  }

  public String getUsernameFromToken(String token) {
    return validateTokenAndGetClaims(token).getSubject();
  }

  public String getRoleFromToken(String token) {
    return validateTokenAndGetClaims(token).get("role", String.class);
  }
}

// JwtAuthenticationFilter.java
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.RequiredArgsConstructor;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);

      try {
        // ✅ Validate token (signature, expiry, issuer)
        String username = jwtService.getUsernameFromToken(token);
        String role = jwtService.getRoleFromToken(token);

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var authentication = new UsernamePasswordAuthenticationToken(
          username, null, authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

      } catch (JwtException e) {
        // ✅ Log failed validation (không expose chi tiết ra response)
        logger.warn("JWT validation failed: " + e.getMessage());
        // Không set authentication → 401 Unauthorized
      }
    }

    filterChain.doFilter(request, response);
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Không validate signature
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Base64;

public class InsecureJwtService {

  // ❌ Parse JWT không verify signature (ai cũng forge được)
  public Claims parseTokenUnsafe(String token) {
    String[] parts = token.split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
    // Parse JSON manually → NO SIGNATURE VERIFICATION!
    return parseJson(payload);
  }

  // ❌ Dùng algorithm "none" (không có signature)
  public String generateInsecureToken(String username) {
    return Jwts.builder()
      .setSubject(username)
      .signWith(SignatureAlgorithm.NONE) // ❌ Catastrophic!
      .compact();
  }

  // ❌ Không check expiry
  public Claims parseWithoutExpiryCheck(String token) {
    return Jwts.parserBuilder()
      .setSigningKey(secretKey)
      .build()
      .parseClaimsJws(token)
      .getBody();
    // Nếu token expired, vẫn accept → replay attack!
  }

  // ❌ Hardcoded secret trong code
  private static final String SECRET = "mySecretKey123"; // ❌ Committed to Git!

  // ❌ Không validate issuer (accept token từ bất kỳ issuer nào)
  public Claims parseWithoutIssuerCheck(String token) {
    return Jwts.parserBuilder()
      .setSigningKey(secretKey)
      .build()
      .parseClaimsJws(token)
      .getBody();
    // Không call requireIssuer() → accept forged issuer
  }
}
```

### Phát hiện

```regex
# Tìm JWT parse không verify signature
Jwts\.parser\(\)|parseClaimsJwt\((?!.*setSigningKey)

# Tìm SignatureAlgorithm.NONE
SignatureAlgorithm\.NONE

# Tìm hardcoded secret
(secret|key)\s*=\s*["'][^"']{8,}["']

# Tìm JWT parse không check issuer
parseClaimsJws\((?!.*requireIssuer)
```

### Checklist

- [ ] JWT signed bằng `HS256` hoặc `RS256` (KHÔNG dùng `NONE`)
- [ ] Secret key load từ environment variable (KHÔNG hardcode)
- [ ] `parseClaimsJws()` với `setSigningKey()` → verify signature
- [ ] `requireIssuer()` để validate issuer claim
- [ ] Expiry time được set và tự động validate bởi JJWT library
- [ ] JwtException được catch và log (không expose chi tiết ra client)
- [ ] Token expiration <= 1 giờ (refresh token mechanism nếu cần longer session)

---

## 06.05 — Rate limiting trên authentication endpoints

### Metadata
- **Mã số:** 06.05
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `rate-limiting`, `brute-force`, `ddos`

### Tại sao?

Authentication endpoints (`/api/auth/login`, `/api/auth/register`) là target chính của brute-force attacks và credential stuffing. Không có rate limiting → attacker thử hàng nghìn password/phút cho đến khi crack được tài khoản. Rate limiting giới hạn số requests từ cùng IP/user trong thời gian nhất định, làm chậm attacker và giảm tải server.

**Hậu quả vi phạm:** Account takeover, DDoS, server overload. **CWE-307** (Improper Restriction of Excessive Authentication Attempts), **CWE-799** (Improper Control of Interaction Frequency).

### ✅ Cách đúng

```java
// RateLimitingFilter.java - Custom filter dùng Bucket4j
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

  // ✅ Giới hạn 5 requests/phút cho mỗi IP
  private Bucket createNewBucket() {
    Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
    return Bucket.builder().addLimit(limit).build();
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    String path = request.getRequestURI();

    // ✅ Chỉ apply rate limit cho auth endpoints
    if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
      String clientIp = getClientIp(request);
      Bucket bucket = cache.computeIfAbsent(clientIp, k -> createNewBucket());

      if (bucket.tryConsume(1)) {
        // ✅ Còn quota → cho phép request
        filterChain.doFilter(request, response);
      } else {
        // ✅ Hết quota → 429 Too Many Requests
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.getWriter().write("Rate limit exceeded. Try again later.");
        return;
      }
    } else {
      // Không phải auth endpoint → không rate limit
      filterChain.doFilter(request, response);
    }
  }

  private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}

// SecurityConfig.java - Add filter
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final RateLimitingFilter rateLimitingFilter;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()
        .anyRequest().authenticated()
      )
      // ✅ Add rate limiting filter trước authentication filter
      .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
      .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
```

```java
// Hoặc dùng Spring Boot Bucket4j Starter với annotation
// pom.xml
// <dependency>
//   <groupId>com.giffing.bucket4j.spring.boot.starter</groupId>
//   <artifactId>bucket4j-spring-boot-starter</artifactId>
//   <version>0.10.1</version>
// </dependency>

// application.yml
/*
bucket4j:
  enabled: true
  filters:
    - cache-name: rate-limit-auth
      url: /api/auth/.*
      strategy: first
      rate-limits:
        - bandwidths:
            - capacity: 5
              time: 1
              unit: minutes
*/

// AuthController.java
import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RateLimiting(name = "rate-limit-auth") // ✅ Annotation-based rate limiting
public class AuthController {

  @PostMapping("/login")
  public TokenResponse login(@RequestBody LoginRequest request) {
    // Rate limit tự động apply bởi filter
    return authService.authenticate(request.username(), request.password());
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Không có rate limiting
@RestController
@RequestMapping("/api/auth")
public class InsecureAuthController {

  // ❌ Attacker gửi 10000 requests/giây để brute-force
  @PostMapping("/login")
  public TokenResponse login(@RequestBody LoginRequest request) {
    return authService.authenticate(request.username(), request.password());
  }

  // ❌ Không có captcha, không có rate limit, không có lockout
  @PostMapping("/register")
  public void register(@RequestBody RegisterRequest request) {
    userService.registerUser(request.username(), request.password());
  }
}

// ❌ SAI: Rate limiting dựa trên user (chưa authenticated!)
public class WrongRateLimiting {

  public boolean checkRateLimit(String username) {
    // ❌ Attacker dùng random username mỗi request → bypass
    Bucket bucket = cache.get(username);
    return bucket != null && bucket.tryConsume(1);
  }
}
```

### Phát hiện

```regex
# Tìm auth endpoints không có rate limiting filter
@PostMapping.*/(login|register|reset-password)(?!.*@RateLimiting)

# Tìm endpoints public mà không có throttling
@RequestMapping.*/api/auth.*\n.*@PostMapping(?!.*rate|throttle|limit)
```

### Checklist

- [ ] Rate limiting filter được add vào `/api/auth/**` endpoints
- [ ] Limit dựa trên IP address (X-Forwarded-For header nếu có proxy)
- [ ] Threshold hợp lý: 5-10 requests/phút cho login, 1-2 requests/phút cho register
- [ ] 429 Too Many Requests response khi exceed limit
- [ ] Cache buckets có TTL (tự động cleanup sau 1 giờ không dùng)
- [ ] Optional: Account lockout sau N failed login attempts (persistence needed)
- [ ] Optional: CAPTCHA sau 3 failed attempts

---

## 06.06 — Security headers (HSTS, X-Frame-Options, CSP)

### Metadata
- **Mã số:** 06.06
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `security`, `headers`, `xss`, `clickjacking`, `https`

### Tại sao?

Security headers là defense-in-depth layer chống lại các tấn công phổ biến: **HSTS** (HTTP Strict Transport Security) buộc browser dùng HTTPS; **X-Frame-Options** chống clickjacking; **Content-Security-Policy** chống XSS bằng cách whitelist nguồn script/style hợp lệ; **X-Content-Type-Options** chống MIME sniffing. Spring Security mặc định enable một số headers nhưng nên customize cho strict hơn.

**Hậu quả vi phạm:** Man-in-the-middle attacks, clickjacking, XSS, MIME confusion attacks. **CWE-1021** (Improper Restriction of Rendered UI Layers), **CWE-693** (Protection Mechanism Failure).

### ✅ Cách đúng

```java
// SecurityConfig.java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/public/**").permitAll()
        .anyRequest().authenticated()
      )
      .headers(headers -> headers
        // ✅ HSTS: Buộc HTTPS trong 1 năm, include subdomains
        .httpStrictTransportSecurity(hsts -> hsts
          .includeSubDomains(true)
          .maxAgeInSeconds(31536000) // 1 year
        )
        // ✅ X-Frame-Options: Chặn embedding trong iframe (chống clickjacking)
        .frameOptions(frame -> frame.deny())

        // ✅ X-Content-Type-Options: Chặn MIME sniffing
        .contentTypeOptions(contentType -> contentType.disable()) // Enabled by default

        // ✅ X-XSS-Protection: Legacy header (modern browsers dùng CSP)
        .xssProtection(xss -> xss
          .headerValue("1; mode=block")
        )

        // ✅ Content-Security-Policy: Whitelist script/style sources
        .contentSecurityPolicy(csp -> csp
          .policyDirectives("default-src 'self'; " +
            "script-src 'self' https://cdn.jsdelivr.net; " +
            "style-src 'self' 'unsafe-inline'; " + // Cho phép inline CSS (cân nhắc tắt)
            "img-src 'self' data: https:; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "connect-src 'self' https://api.example.com; " +
            "frame-ancestors 'none'") // Chặn embedding (thay X-Frame-Options)
        )

        // ✅ Referrer-Policy: Giới hạn referrer info
        .referrerPolicy(referrer -> referrer
          .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
        )

        // ✅ Permissions-Policy (thay Feature-Policy)
        .permissionsPolicy(permissions -> permissions
          .policy("geolocation=(), microphone=(), camera=()")
        )
      );
    return http.build();
  }
}
```

```java
// Hoặc custom bằng Filter
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    // ✅ Add custom security headers
    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-XSS-Protection", "1; mode=block");
    response.setHeader("Content-Security-Policy",
      "default-src 'self'; frame-ancestors 'none'");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    response.setHeader("Permissions-Policy", "geolocation=(), camera=()");

    filterChain.doFilter(request, response);
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Tắt tất cả security headers
@Configuration
public class InsecureConfig {

  @Bean
  public SecurityFilterChain insecureFilterChain(HttpSecurity http) throws Exception {
    http
      .headers(headers -> headers.disable()); // ❌ Tắt hết headers!
    return http.build();
  }
}

// ❌ SAI: Cho phép iframe từ bất kỳ nguồn nào (clickjacking risk)
http.headers(headers -> headers
  .frameOptions(frame -> frame.disable()) // ❌ NGUY HIỂM
);

// ❌ SAI: CSP quá lỏng (cho phép 'unsafe-eval', 'unsafe-inline')
http.headers(headers -> headers
  .contentSecurityPolicy(csp -> csp
    .policyDirectives("script-src 'self' 'unsafe-eval' 'unsafe-inline'") // ❌ XSS risk
  )
);

// ❌ SAI: HSTS quá ngắn hoặc không có
http.headers(headers -> headers
  .httpStrictTransportSecurity(hsts -> hsts
    .maxAgeInSeconds(300) // ❌ Chỉ 5 phút (quá ngắn)
  )
);
```

### Phát hiện

```regex
# Tìm headers().disable()
\.headers\s*\(\s*headers\s*->\s*headers\.disable\(\)

# Tìm frameOptions().disable()
\.frameOptions\s*\(\s*frame\s*->\s*frame\.disable\(\)

# Tìm CSP với unsafe-eval/unsafe-inline
contentSecurityPolicy.*['"].*unsafe-(eval|inline)

# Tìm HSTS maxAge < 1 năm
maxAgeInSeconds\s*\(\s*[0-9]{1,6}\s*\)(?!.*31536000)
```

### Checklist

- [ ] HSTS enabled với `max-age >= 31536000` (1 năm)
- [ ] `X-Frame-Options: DENY` hoặc `SAMEORIGIN` (chặn clickjacking)
- [ ] `X-Content-Type-Options: nosniff` enabled
- [ ] CSP configured với `default-src 'self'` + whitelist cho CDN
- [ ] CSP KHÔNG có `'unsafe-eval'` hoặc `'unsafe-inline'` (trừ khi thật sự cần)
- [ ] `Referrer-Policy` set to `strict-origin-when-cross-origin`
- [ ] `Permissions-Policy` disable unnecessary features (geolocation, camera)

---

## 06.07 — Không log sensitive data (password, token, PII)

### Metadata
- **Mã số:** 06.07
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `logging`, `pii`, `gdpr`, `sensitive-data`

### Tại sao?

Log files có thể bị attacker access (log injection, server compromise, misconfigured permissions). Nếu log chứa password, JWT token, credit card, SSN, email → data breach nghiêm trọng. GDPR/CCPA yêu cầu bảo vệ PII (Personally Identifiable Information). Phải sanitize hoặc mask sensitive fields trước khi log.

**Hậu quả vi phạm:** Data breach, GDPR fines (lên đến €20M hoặc 4% revenue), compliance violations. **CWE-532** (Insertion of Sensitive Information into Log File), **CWE-200** (Exposure of Sensitive Information).

### ✅ Cách đúng

```java
// LoggingFilter.java - Log requests KHÔNG bao gồm sensitive headers
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class LoggingFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

  // ✅ Blacklist sensitive headers
  private static final Set<String> SENSITIVE_HEADERS = Set.of(
    "authorization", "cookie", "x-api-key", "x-auth-token"
  );

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    String method = request.getMethod();
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();

    // ✅ Log request INFO (không log headers)
    logger.info("Request: {} {} {}", method, uri, queryString != null ? queryString : "");

    // ✅ KHÔNG log Authorization header
    // ❌ logger.debug("Headers: {}", Collections.list(request.getHeaderNames()));

    filterChain.doFilter(request, response);

    logger.info("Response: {} - Status {}", uri, response.getStatus());
  }
}

// AuthService.java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public TokenResponse authenticate(String username, String password) {
    // ✅ Log username (public info), KHÔNG log password
    logger.info("Authentication attempt for user: {}", username);

    User user = userRepository.findByUsername(username)
      .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

    if (!passwordEncoder.matches(password, user.getPassword())) {
      // ✅ Log failure KHÔNG expose chi tiết (timing attack)
      logger.warn("Failed login attempt for user: {}", username);
      throw new BadCredentialsException("Invalid credentials");
    }

    String token = jwtService.generateToken(username, user.getRole());

    // ✅ Log success KHÔNG log token
    logger.info("User logged in successfully: {}", username);
    // ❌ logger.debug("Generated token: {}", token); // NEVER!

    return new TokenResponse(token);
  }
}

// MaskingConverter.java - Logback converter để mask sensitive data
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

public class MaskingConverter extends ClassicConverter {

  // ✅ Regex patterns cho sensitive data
  private static final Pattern EMAIL_PATTERN =
    Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
  private static final Pattern CREDIT_CARD_PATTERN =
    Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
  private static final Pattern TOKEN_PATTERN =
    Pattern.compile("(token|jwt|bearer)[\\s:=]+([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)");

  @Override
  public String convert(ILoggingEvent event) {
    String message = event.getFormattedMessage();

    // ✅ Mask email: john@example.com → j***@example.com
    message = EMAIL_PATTERN.matcher(message).replaceAll("$1***@$2");

    // ✅ Mask credit card: 1234-5678-9012-3456 → ****-****-****-3456
    message = CREDIT_CARD_PATTERN.matcher(message)
      .replaceAll(match -> "****-****-****-" + match.group().substring(match.group().length() - 4));

    // ✅ Mask JWT token
    message = TOKEN_PATTERN.matcher(message).replaceAll("$1: [REDACTED]");

    return message;
  }
}

// logback-spring.xml
/*
<configuration>
  <conversionRule conversionWord="mask"
    converterClass="jp.medicalbox.config.MaskingConverter" />

  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss} [%thread] %-5level %logger{36} - %mask%n</pattern>
    </encoder>
  </appender>
</configuration>
*/
```

### ❌ Cách sai

```java
// ❌ SAI: Log password plaintext
@Service
public class InsecureAuthService {

  public void authenticate(String username, String password) {
    // ❌ CATASTROPHIC: Password trong log file!
    logger.info("Login attempt: username={}, password={}", username, password);

    // ❌ Log toàn bộ request object (có thể chứa password)
    logger.debug("Request: {}", loginRequest.toString());
  }
}

// ❌ SAI: Log JWT token
public String generateToken(String username) {
  String token = jwtService.generate(username);
  logger.info("Generated JWT for {}: {}", username, token); // ❌ Token leak!
  return token;
}

// ❌ SAI: Log exception stack trace có sensitive data
try {
  userService.updateEmail(userId, newEmail);
} catch (Exception e) {
  // ❌ Exception message có thể chứa email/PII
  logger.error("Failed to update user: " + e.getMessage(), e);
  throw e;
}

// ❌ SAI: Log toàn bộ entity object (có password hash)
User user = userRepository.findById(userId).orElseThrow();
logger.debug("User details: {}", user); // ❌ toString() có password field!
```

### Phát hiện

```regex
# Tìm log password
logger\.(info|debug|trace|warn|error).*password

# Tìm log token/jwt
logger\.(info|debug|trace).*\b(token|jwt|bearer)\b

# Tìm log Authorization header
logger.*Authorization|logger.*Cookie

# Tìm log request/response body (có thể chứa sensitive data)
logger.*request\.getBody|logger.*response\.getBody
```

### Checklist

- [ ] KHÔNG log password (plaintext hoặc hash)
- [ ] KHÔNG log JWT token, API keys, session IDs
- [ ] KHÔNG log Authorization/Cookie headers
- [ ] KHÔNG log PII (email, SSN, credit card) HOẶC mask trước khi log
- [ ] Log exceptions KHÔNG include sensitive data trong message
- [ ] Entity toString() methods KHÔNG include password field
- [ ] Logback/Log4j configured với masking converter cho sensitive patterns
- [ ] Production logs có retention policy (xóa sau 30-90 ngày)

---

## 06.08 — Parameterized queries (tránh SQL injection)

### Metadata
- **Mã số:** 06.08
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `sql-injection`, `database`, `jpa`

### Tại sao?

SQL Injection là #3 trong OWASP Top 10 (2021). String concatenation trong SQL queries cho phép attacker inject malicious SQL code, đọc/sửa/xóa toàn bộ database, bypass authentication, RCE (Remote Code Execution). Spring Data JPA mặc định dùng parameterized queries (safe), nhưng `@Query` với string concatenation hoặc native queries không cẩn thận vẫn vulnerable.

**Hậu quả vi phạm:** Full database compromise, data theft, data loss, privilege escalation. **CWE-89** (SQL Injection).

### ✅ Cách đúng

```java
// UserRepository.java - JPA Query Methods (tự động parameterized)
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

  // ✅ Method name query (tự động generate SQL safe)
  Optional<User> findByUsername(String username);

  List<User> findByRoleAndStatusOrderByCreatedAtDesc(String role, String status);

  // ✅ JPQL với named parameters (parameterized)
  @Query("SELECT u FROM User u WHERE u.email = :email AND u.status = :status")
  Optional<User> findByEmailAndStatus(@Param("email") String email, @Param("status") String status);

  // ✅ Native query với named parameters (safe)
  @Query(value = "SELECT * FROM users WHERE username = :username LIMIT 1", nativeQuery = true)
  Optional<User> findByUsernameNative(@Param("username") String username);

  // ✅ JPQL với IN clause (parameterized list)
  @Query("SELECT u FROM User u WHERE u.role IN :roles")
  List<User> findByRoles(@Param("roles") List<String> roles);

  // ✅ Criteria API (fully type-safe, không thể SQL inject)
  default List<User> findByDynamicCriteria(String username, String email) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<User> query = cb.createQuery(User.class);
    Root<User> user = query.from(User.class);

    List<Predicate> predicates = new ArrayList<>();
    if (username != null) {
      predicates.add(cb.equal(user.get("username"), username));
    }
    if (email != null) {
      predicates.add(cb.like(user.get("email"), "%" + email + "%"));
    }

    query.where(predicates.toArray(new Predicate[0]));
    return entityManager.createQuery(query).getResultList();
  }
}
```

```java
// DoctorService.java - Dynamic queries với CriteriaBuilder
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {

  private final EntityManager entityManager;

  public List<Doctor> searchDoctors(String name, String specialization, String status) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Doctor> query = cb.createQuery(Doctor.class);
    Root<Doctor> doctor = query.from(Doctor.class);

    List<Predicate> predicates = new ArrayList<>();

    // ✅ Tất cả điều kiện đều parameterized (không string concat)
    if (name != null && !name.isEmpty()) {
      predicates.add(cb.like(cb.lower(doctor.get("name")), "%" + name.toLowerCase() + "%"));
    }
    if (specialization != null && !specialization.isEmpty()) {
      predicates.add(cb.equal(doctor.get("specialization"), specialization));
    }
    if (status != null && !status.isEmpty()) {
      predicates.add(cb.equal(doctor.get("status"), status));
    }

    query.where(predicates.toArray(new Predicate[0]));
    return entityManager.createQuery(query).getResultList();
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: String concatenation trong native query
public interface VulnerableUserRepository extends JpaRepository<User, Long> {

  // ❌ CATASTROPHIC SQL INJECTION!
  @Query(value = "SELECT * FROM users WHERE username = '" + username + "'", nativeQuery = true)
  Optional<User> findByUsernameDangerous(String username);
  // Attacker input: "admin' OR '1'='1" → bypass authentication!

  // ❌ String concatenation trong JPQL (vẫn vulnerable)
  default List<User> searchByName(String name) {
    String jpql = "SELECT u FROM User u WHERE u.name LIKE '%" + name + "%'";
    return entityManager.createQuery(jpql, User.class).getResultList();
    // Attacker input: "%' OR 1=1 --" → return all users
  }
}

// ❌ SAI: Dynamic SQL với string builder
@Service
public class VulnerableDoctorService {

  public List<Doctor> searchDoctors(String name, String specialization) {
    StringBuilder sql = new StringBuilder("SELECT * FROM doctors WHERE 1=1");

    // ❌ String concatenation → SQL injection
    if (name != null) {
      sql.append(" AND name = '").append(name).append("'");
    }
    if (specialization != null) {
      sql.append(" AND specialization = '").append(specialization).append("'");
    }

    Query query = entityManager.createNativeQuery(sql.toString(), Doctor.class);
    return query.getResultList();
    // Attacker input: "'; DROP TABLE doctors; --" → catastrophic!
  }
}

// ❌ SAI: jdbcTemplate.execute với string concat
@Repository
public class VulnerableJdbcRepository {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  public User findByUsername(String username) {
    // ❌ SQL injection vulnerable
    String sql = "SELECT * FROM users WHERE username = '" + username + "'";
    return jdbcTemplate.queryForObject(sql, new UserRowMapper());
  }
}
```

### Phát hiện

```regex
# Tìm string concatenation trong SQL
@Query.*\+.*\).*nativeQuery

# Tìm createNativeQuery với string concat
createNativeQuery\s*\(\s*[^)]*\+[^)]*\)

# Tìm string format trong SQL
String\.format.*SELECT|FROM|WHERE.*%s

# Tìm jdbcTemplate.execute với concat
jdbcTemplate\.(execute|query).*\+
```

### Checklist

- [ ] Tất cả queries dùng JPA method names HOẶC `@Query` với named parameters
- [ ] KHÔNG có string concatenation (`+`) trong SQL queries
- [ ] Native queries dùng `:paramName` syntax
- [ ] Dynamic queries dùng Criteria API hoặc Specifications
- [ ] KHÔNG dùng `String.format()` hoặc `StringBuilder` cho SQL
- [ ] JdbcTemplate dùng `?` placeholders hoặc named parameters (`NamedParameterJdbcTemplate`)
- [ ] Input validation bổ sung (whitelist allowed characters) trước khi query

---

## 06.09 — Input sanitization cho XSS prevention

### Metadata
- **Mã số:** 06.09
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `xss`, `input-validation`, `sanitization`

### Tại sao?

Cross-Site Scripting (XSS) xảy ra khi user input chứa JavaScript code được render trực tiếp trên browser mà không sanitize. Attacker inject `<script>` tags để steal cookies, session tokens, redirect user, hoặc deface website. Backend phải validate/sanitize input, frontend phải escape output. Spring Boot không tự động sanitize (trách nhiệm của developer).

**Hậu quả vi phạm:** Session hijacking, cookie theft, phishing, malware distribution. **CWE-79** (Cross-Site Scripting).

### ✅ Cách đúng

```java
// ValidationConfig.java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import jakarta.validation.Validator;

@Configuration
public class ValidationConfig {

  @Bean
  public Validator validator() {
    return new LocalValidatorFactoryBean();
  }
}

// CreateDoctorRequest.java - Input validation với Bean Validation
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.SafeHtml;
import org.hibernate.validator.constraints.SafeHtml.WhiteListType;

public record CreateDoctorRequest(
  // ✅ Validate name (không cho phép HTML tags)
  @NotBlank(message = "Name is required")
  @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
  @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Name must contain only letters and spaces")
  String name,

  // ✅ Validate email format
  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  String email,

  // ✅ Validate specialization (whitelist)
  @NotNull(message = "Specialization is required")
  @Pattern(regexp = "^(CARDIOLOGY|NEUROLOGY|PEDIATRICS|ORTHOPEDICS)$",
    message = "Invalid specialization")
  String specialization,

  // ✅ SafeHtml annotation (Hibernate Validator) - strip HTML tags
  @SafeHtml(whitelistType = WhiteListType.NONE, message = "Bio must not contain HTML")
  @Size(max = 500)
  String bio
) {}

// DoctorController.java
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctors")
@Validated // ✅ Enable validation
public class DoctorController {

  @PostMapping
  public DoctorResponse createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
    // ✅ @Valid trigger validation → 400 Bad Request nếu invalid
    return doctorService.createDoctor(request);
  }
}

// HtmlSanitizer.java - Custom sanitization utility
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer {

  // ✅ Strip tất cả HTML tags (cho plaintext fields)
  public String sanitizePlainText(String input) {
    if (input == null) {
      return null;
    }
    // Jsoup.clean() removes all HTML tags
    return Jsoup.clean(input, Safelist.none());
  }

  // ✅ Allow basic formatting tags (cho rich text fields)
  public String sanitizeRichText(String input) {
    if (input == null) {
      return null;
    }
    // Whitelist: <b>, <i>, <u>, <p>, <br>, <a>
    Safelist safelist = Safelist.basicWithImages();
    return Jsoup.clean(input, safelist);
  }

  // ✅ Escape HTML entities (cho display trong HTML)
  public String escapeHtml(String input) {
    if (input == null) {
      return null;
    }
    return input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#x27;");
  }
}

// DoctorService.java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorService {

  private final DoctorRepository doctorRepository;
  private final HtmlSanitizer htmlSanitizer;

  public Doctor createDoctor(CreateDoctorRequest request) {
    // ✅ Sanitize input trước khi lưu DB
    String sanitizedName = htmlSanitizer.sanitizePlainText(request.name());
    String sanitizedBio = htmlSanitizer.sanitizeRichText(request.bio());

    Doctor doctor = Doctor.builder()
      .name(sanitizedName)
      .email(request.email())
      .specialization(request.specialization())
      .bio(sanitizedBio)
      .build();

    return doctorRepository.save(doctor);
  }
}
```

```java
// pom.xml - Add Jsoup dependency
/*
<dependency>
  <groupId>org.jsoup</groupId>
  <artifactId>jsoup</artifactId>
  <version>1.17.2</version>
</dependency>
*/
```

### ❌ Cách sai

```java
// ❌ SAI: Không validate/sanitize input
@RestController
@RequestMapping("/api/doctors")
public class VulnerableController {

  @PostMapping
  public Doctor createDoctor(@RequestBody CreateDoctorRequest request) {
    // ❌ Lưu trực tiếp user input (có thể chứa <script>alert('XSS')</script>)
    Doctor doctor = new Doctor();
    doctor.setName(request.name()); // ❌ NO VALIDATION!
    doctor.setBio(request.bio()); // ❌ NO SANITIZATION!
    return doctorRepository.save(doctor);
  }
}

// ❌ SAI: Validate nhưng không sanitize HTML
public record VulnerableRequest(
  @NotBlank String name, // ❌ Chỉ check not blank, không check HTML tags
  @Size(max = 500) String bio // ❌ Chỉ check length, không strip <script>
) {}

// ❌ SAI: Frontend sanitization only (attacker bypass bằng curl)
// Backend không sanitize → trust frontend (BIG MISTAKE!)

// ❌ SAI: Blacklist approach (dễ bypass)
public String insecureSanitize(String input) {
  // ❌ Blacklist incomplete (attacker dùng <img onerror="alert(1)">)
  return input.replace("<script>", "").replace("</script>", "");
}
```

### Phát hiện

```regex
# Tìm setters không có validation
\.set(Name|Bio|Description)\s*\(\s*request\.\w+\(\)\s*\)(?!.*sanitize|clean)

# Tìm @RequestBody không có @Valid
@RequestBody(?!\s+@Valid)

# Tìm String fields không có validation annotations
String\s+\w+(?!.*@NotBlank|@Pattern|@SafeHtml)
```

### Checklist

- [ ] Tất cả `@RequestBody` DTOs có `@Valid` annotation
- [ ] String fields có `@NotBlank`, `@Size`, `@Pattern` validation
- [ ] Text fields có `@SafeHtml` hoặc manual sanitization với Jsoup
- [ ] Whitelist approach (cho phép tags an toàn) thay vì blacklist
- [ ] Frontend CŨNG escape output khi render (defense-in-depth)
- [ ] Content-Security-Policy header enabled (domain 06.06)
- [ ] Rich text editors (TinyMCE, CKEditor) configured với whitelist tags

---

## 06.10 — Principle of least privilege cho roles/authorities

### Metadata
- **Mã số:** 06.10
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `security`, `authorization`, `rbac`, `principle-of-least-privilege`

### Tại sao?

Principle of Least Privilege (PoLP) quy định mỗi user/role chỉ được quyền tối thiểu cần thiết để thực hiện công việc. Tránh "god mode" (ADMIN có quyền tất cả) hoặc quá granular (quản lý 100 permissions khó). Thiết kế role hierarchy hợp lý: ADMIN > MANAGER > USER, mỗi role kế thừa quyền của role dưới + thêm quyền riêng.

**Hậu quả vi phạm:** Privilege escalation, insider threats, accidental data deletion bởi user không có training. **CWE-250** (Execution with Unnecessary Privileges).

### ✅ Cách đúng

```java
// Role.java - Enum roles với hierarchy
public enum Role {
  USER(1),          // Base role: Đọc data của chính mình
  DOCTOR(2),        // Đọc/ghi appointments của chính mình
  STAFF(3),         // Quản lý appointments, patients
  CLINIC_MANAGER(4), // Quản lý doctors, staff, clinic settings
  ADMIN(5);         // Full access

  private final int level;

  Role(int level) {
    this.level = level;
  }

  public boolean hasPrivilege(Role requiredRole) {
    return this.level >= requiredRole.level;
  }
}

// SecurityConfig.java - Role-based access control
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        // ✅ Public endpoints
        .requestMatchers("/api/auth/**", "/api/public/**").permitAll()

        // ✅ USER role: Chỉ đọc data của chính mình
        .requestMatchers("/api/users/me").hasRole("USER")
        .requestMatchers("/api/appointments/my").hasAnyRole("USER", "DOCTOR")

        // ✅ DOCTOR role: Quản lý appointments của mình
        .requestMatchers("/api/doctors/me/**").hasRole("DOCTOR")
        .requestMatchers("/api/appointments/{id}/complete").hasRole("DOCTOR")

        // ✅ STAFF role: Quản lý patients, appointments (read-only for doctors)
        .requestMatchers("/api/patients/**").hasAnyRole("STAFF", "CLINIC_MANAGER", "ADMIN")
        .requestMatchers("/api/appointments/create").hasAnyRole("STAFF", "CLINIC_MANAGER")

        // ✅ CLINIC_MANAGER: Quản lý doctors, staff
        .requestMatchers("/api/doctors/**").hasAnyRole("CLINIC_MANAGER", "ADMIN")
        .requestMatchers("/api/staff/**").hasAnyRole("CLINIC_MANAGER", "ADMIN")

        // ✅ ADMIN: Full access to system settings
        .requestMatchers("/api/admin/**").hasRole("ADMIN")

        // ✅ Default: Require authentication
        .anyRequest().authenticated()
      )
      .formLogin(form -> form.permitAll())
      .csrf(csrf -> csrf.disable());
    return http.build();
  }
}

// DoctorService.java - Method-level authorization
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorService {

  private final DoctorRepository doctorRepository;

  // ✅ Chỉ CLINIC_MANAGER hoặc ADMIN được tạo doctor
  @PreAuthorize("hasAnyRole('CLINIC_MANAGER', 'ADMIN')")
  public Doctor createDoctor(CreateDoctorRequest request) {
    return doctorRepository.save(Doctor.builder()
      .name(request.name())
      .specialization(request.specialization())
      .build());
  }

  // ✅ Doctor chỉ update thông tin của chính mình, ADMIN update bất kỳ doctor nào
  @PreAuthorize("hasRole('ADMIN') or (hasRole('DOCTOR') and #doctorId == authentication.principal.id)")
  public Doctor updateDoctor(Long doctorId, UpdateDoctorRequest request) {
    Doctor doctor = doctorRepository.findById(doctorId)
      .orElseThrow(() -> new NotFoundException("Doctor not found"));
    doctor.setName(request.name());
    return doctorRepository.save(doctor);
  }

  // ✅ Chỉ CLINIC_MANAGER hoặc ADMIN được xóa doctor
  @PreAuthorize("hasAnyRole('CLINIC_MANAGER', 'ADMIN')")
  public void deleteDoctor(Long doctorId) {
    doctorRepository.deleteById(doctorId);
  }

  // ✅ Public: Ai cũng đọc được list doctors
  public List<Doctor> findAllDoctors() {
    return doctorRepository.findAll();
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Tất cả authenticated users đều có quyền như nhau
@Configuration
public class InsecureConfig {

  @Bean
  public SecurityFilterChain insecureFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()
        .anyRequest().authenticated() // ❌ Không phân biệt role!
      );
    return http.build();
  }
}

// ❌ SAI: Admin có tất cả quyền (không cần, quá rủi ro)
@Service
public class InsecureDoctorService {

  // ❌ Bất kỳ user nào authenticated đều tạo được doctor
  public Doctor createDoctor(CreateDoctorRequest request) {
    return doctorRepository.save(new Doctor(...));
  }

  // ❌ Không check ownership (User A xóa doctor của User B)
  public void deleteDoctor(Long doctorId) {
    doctorRepository.deleteById(doctorId);
  }
}

// ❌ SAI: Hardcoded user ID trong code (bypass authorization)
@GetMapping("/api/users/{userId}")
public User getUser(@PathVariable Long userId) {
  // ❌ Không check nếu userId == current user ID
  return userRepository.findById(userId).orElseThrow();
  // User 1 có thể đọc data của User 2!
}
```

### Phát hiện

```regex
# Tìm anyRequest().authenticated() không có role-based rules
anyRequest\(\)\.authenticated\(\)(?!.*hasRole)

# Tìm service methods không có @PreAuthorize
public\s+(void|\w+)\s+(create|update|delete)\w+\((?!.*@PreAuthorize)

# Tìm hardcoded authorization bypasses
if\s*\(\s*userId\s*==\s*\d+\s*\)
```

### Checklist

- [ ] Mỗi role có scope quyền rõ ràng (documented)
- [ ] URL-based rules trong `SecurityFilterChain` cover tất cả sensitive endpoints
- [ ] Method-level security (`@PreAuthorize`) cho business logic
- [ ] Ownership check (user chỉ access data của mình hoặc role cao hơn)
- [ ] Role hierarchy: ADMIN > MANAGER > STAFF > USER
- [ ] KHÔNG có "god mode" role (nếu có ADMIN, limit số admin accounts)
- [ ] Audit log cho actions của privileged roles (ADMIN, MANAGER)

---

## 06.11 — Secure session management (timeout, invalidation)

### Metadata
- **Mã số:** 06.11
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** `security`, `session`, `timeout`, `logout`, `session-fixation`

### Tại sao?

Session management không đúng dẫn đến session hijacking, session fixation, và unauthorized access. Session timeout buộc user re-authenticate sau thời gian idle (giảm risk nếu user quên logout trên public computer). Session invalidation khi logout đảm bảo old session không thể reuse. HttpOnly + Secure cookies chống XSS/MITM attacks.

**Lưu ý:** Domain này có mức 🟠 KHUYẾN NGHỊ thay vì 🔴 vì nhiều modern APIs dùng stateless JWT (không có session), nhưng nếu dùng session-based authentication thì practices này là BẮT BUỘC.

**Hậu quả vi phạm:** Session hijacking, session fixation, unauthorized access. **CWE-384** (Session Fixation), **CWE-613** (Insufficient Session Expiration).

### ✅ Cách đúng

```java
// SecurityConfig.java - Session management với timeout
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()
        .anyRequest().authenticated()
      )
      .formLogin(form -> form.permitAll())
      .logout(logout -> logout
        .logoutUrl("/api/auth/logout")
        .logoutSuccessUrl("/login")
        // ✅ Invalidate session khi logout
        .invalidateHttpSession(true)
        // ✅ Delete cookies
        .deleteCookies("JSESSIONID")
        .permitAll()
      )
      .sessionManagement(session -> session
        // ✅ Tạo session mới sau khi login (chống session fixation)
        .sessionFixation().newSession()
        // ✅ Giới hạn 1 session per user (kick old sessions)
        .maximumSessions(1)
        .maxSessionsPreventsLogin(false) // Allow new login (kick old session)
      );
    return http.build();
  }

  // ✅ HttpOnly + Secure cookies
  @Bean
  public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("SESSION");
    serializer.setUseHttpOnlyCookie(true); // ✅ Chặn JavaScript access (XSS protection)
    serializer.setUseSecureCookie(true);   // ✅ HTTPS only (MITM protection)
    serializer.setSameSite("Strict");      // ✅ CSRF protection
    serializer.setCookieMaxAge(1800);      // ✅ 30 minutes timeout
    return serializer;
  }

  // ✅ Session timeout listener
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }
}

// application.yml - Session timeout configuration
/*
server:
  servlet:
    session:
      timeout: 30m  # ✅ 30 minutes idle timeout
      cookie:
        http-only: true
        secure: true
        same-site: strict
*/

// AuthController.java - Explicit logout
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  @PostMapping("/logout")
  public void logout(HttpServletRequest request) {
    // ✅ Invalidate session
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    // ✅ Clear security context
    SecurityContextHolder.clearContext();
  }

  @PostMapping("/logout-all")
  public void logoutAllSessions(HttpServletRequest request) {
    // ✅ Invalidate all sessions của user (cần Spring Session + Redis)
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    sessionRegistry.getAllSessions(username, false)
      .forEach(SessionInformation::expireNow);
  }
}
```

```java
// SessionEventListener.java - Audit session events
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SessionEventListener implements HttpSessionListener {

  private static final Logger logger = LoggerFactory.getLogger(SessionEventListener.class);

  @Override
  public void sessionCreated(HttpSessionEvent event) {
    // ✅ Log session creation
    logger.info("Session created: {}", event.getSession().getId());
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent event) {
    // ✅ Log session destruction (timeout or logout)
    logger.info("Session destroyed: {}", event.getSession().getId());
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Session timeout quá dài (hoặc không có)
// application.yml
/*
server:
  servlet:
    session:
      timeout: -1  # ❌ NEVER EXPIRES!
*/

// ❌ SAI: Không invalidate session khi logout
@PostMapping("/logout")
public void insecureLogout(HttpServletRequest request) {
  // ❌ Chỉ clear security context, session vẫn valid
  SecurityContextHolder.clearContext();
  // Session cookie vẫn hoạt động → replay attack!
}

// ❌ SAI: Cookie không có HttpOnly/Secure flags
@Bean
public CookieSerializer insecureCookieSerializer() {
  DefaultCookieSerializer serializer = new DefaultCookieSerializer();
  serializer.setUseHttpOnlyCookie(false); // ❌ JavaScript có thể steal cookie (XSS)
  serializer.setUseSecureCookie(false);   // ❌ Cookie gửi qua HTTP (MITM)
  return serializer;
}

// ❌ SAI: Không có session fixation protection
http.sessionManagement(session -> session
  .sessionFixation().none() // ❌ Không tạo session mới sau login
);
// Attacker set session ID trước → user login với session ID của attacker

// ❌ SAI: Unlimited concurrent sessions
http.sessionManagement(session -> session
  .maximumSessions(-1) // ❌ User có thể có vô số sessions
);
// Session leak, resource exhaustion
```

### Phát hiện

```regex
# Tìm session timeout = -1 hoặc > 1 giờ
timeout:\s*(-1|[2-9]\d{3,}m|[2-9]h)

# Tìm logout không invalidate session
logout.*\n(?!.*invalidateHttpSession)

# Tìm cookie không HttpOnly
setUseHttpOnlyCookie\s*\(\s*false\s*\)

# Tìm sessionFixation().none()
sessionFixation\(\)\.none\(\)
```

### Checklist

- [ ] Session timeout <= 30 phút cho sensitive apps, <= 2 giờ cho general apps
- [ ] Logout endpoint invalidates session (`invalidateHttpSession(true)`)
- [ ] Session cookies có `HttpOnly=true` (chống XSS)
- [ ] Session cookies có `Secure=true` (HTTPS only)
- [ ] Session cookies có `SameSite=Strict` hoặc `Lax` (chống CSRF)
- [ ] Session fixation protection enabled (`sessionFixation().newSession()`)
- [ ] Maximum sessions per user configured (1-3 sessions)
- [ ] Session events logged (creation, destruction, timeout)

---

## 06.12 — Secret management qua environment variables / Vault

### Metadata
- **Mã số:** 06.12
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** `security`, `secrets`, `configuration`, `vault`, `credentials`

### Tại sao?

Hardcoded secrets (API keys, database passwords, JWT secret) trong source code là critical security vulnerability. Source code thường được commit vào Git (public hoặc private repos có nhiều người access), CI/CD logs, Docker images. Secrets phải load từ environment variables (local dev) hoặc secret management systems như HashiCorp Vault, AWS Secrets Manager (production).

**Hậu quả vi phạm:** Full system compromise, database breach, third-party API abuse, financial loss. **CWE-798** (Use of Hard-coded Credentials), **CWE-259** (Use of Hard-coded Password).

### ✅ Cách đúng

```java
// application.yml - Placeholder cho environment variables
/*
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}  # ✅ Load từ env var

jwt:
  secret: ${JWT_SECRET}  # ✅ Load từ env var
  expiration-ms: 3600000

mail:
  smtp:
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
*/

// JwtService.java - Load secret từ @Value
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class JwtService {

  // ✅ Inject từ environment variable
  @Value("${jwt.secret}")
  private String secretKeyString;

  @Value("${jwt.expiration-ms}")
  private long expirationMs;

  private SecretKey getSecretKey() {
    return Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(String username) {
    return Jwts.builder()
      .setSubject(username)
      .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
      .signWith(getSecretKey())
      .compact();
  }
}
```

```bash
# .env file (local development only, NEVER commit to Git)
# Add .env to .gitignore!

DB_URL=jdbc:postgresql://localhost:5432/medicalbox
DB_USERNAME=postgres
DB_PASSWORD=supersecretpassword
JWT_SECRET=your-256-bit-secret-key-here-at-least-32-characters
SMTP_USERNAME=noreply@medicalbox.com
SMTP_PASSWORD=smtp-password-here
```

```bash
# .gitignore (CRITICAL)
.env
*.env
application-local.yml
application-dev.yml  # Nếu chứa real credentials
secrets/
*.key
*.pem
```

```java
// HashiCorp Vault integration (production)
// pom.xml
/*
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
*/

// bootstrap.yml (load trước application.yml)
/*
spring:
  application:
    name: medicalbox-api
  cloud:
    vault:
      uri: https://vault.example.com:8200
      token: ${VAULT_TOKEN}  # ✅ Vault token từ env var
      kv:
        enabled: true
        backend: secret
        default-context: medicalbox
*/

// Vault stores secrets như:
// secret/medicalbox/db-password
// secret/medicalbox/jwt-secret
// Spring tự động inject vào application.yml placeholders
```

```java
// AWS Secrets Manager integration
// pom.xml
/*
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter-secrets-manager</artifactId>
</dependency>
*/

// application.yml
/*
aws:
  secretsmanager:
    region: ap-southeast-1
    name: medicalbox-secrets  # ✅ Secret name trong AWS Secrets Manager

spring:
  datasource:
    password: ${db-password}  # ✅ Auto-injected từ AWS Secrets Manager
*/
```

### ❌ Cách sai

```java
// ❌ SAI: Hardcoded database password
@Configuration
public class InsecureDataSourceConfig {

  @Bean
  public DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl("jdbc:postgresql://localhost:5432/medicalbox");
    dataSource.setUsername("postgres");
    dataSource.setPassword("password123"); // ❌ CATASTROPHIC!
    return dataSource;
  }
}

// ❌ SAI: Hardcoded JWT secret
@Service
public class InsecureJwtService {

  // ❌ Committed to Git → anyone có thể forge tokens
  private static final String SECRET_KEY = "my-secret-key-12345";

  public String generateToken(String username) {
    return Jwts.builder()
      .setSubject(username)
      .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes()))
      .compact();
  }
}

// ❌ SAI: API key trong code
@Service
public class InsecureEmailService {

  // ❌ SendGrid API key hardcoded
  private static final String SENDGRID_API_KEY = "SG.xxxxx";

  public void sendEmail(String to, String subject, String body) {
    // Use SENDGRID_API_KEY...
  }
}

// ❌ SAI: application.yml với real passwords committed to Git
/*
spring:
  datasource:
    password: supersecretpassword  # ❌ CATASTROPHIC!

jwt:
  secret: production-jwt-secret-key-do-not-share  # ❌ CATASTROPHIC!
*/
```

### Phát hiện

```regex
# Tìm hardcoded passwords
(password|passwd|pwd)\s*=\s*["'][^"']{3,}["']

# Tìm hardcoded API keys
(api[-_]?key|apikey|secret[-_]?key)\s*=\s*["'][A-Za-z0-9_-]{20,}["']

# Tìm JDBC URLs với credentials
jdbc:.*://.*:.*@

# Tìm Bearer tokens hardcoded
Authorization.*Bearer\s+[A-Za-z0-9._-]{20,}
```

### Checklist

- [ ] KHÔNG có hardcoded passwords, API keys, JWT secrets trong code
- [ ] `application.yml` dùng placeholders `${ENV_VAR}` cho sensitive values
- [ ] `.env` file trong `.gitignore` (local dev only)
- [ ] Production dùng secret management system (Vault, AWS Secrets Manager, Azure Key Vault)
- [ ] CI/CD inject secrets qua environment variables (GitHub Secrets, GitLab CI/CD Variables)
- [ ] Database passwords rotated định kỳ (3-6 tháng)
- [ ] Secret scanning tools enabled (GitGuardian, TruffleHog, GitHub secret scanning)
- [ ] Developers trained về secret management best practices

---

## Tổng kết Domain 06: Security

**Trọng số ×3 → Mức độ ưu tiên cao nhất trong tất cả 13 domains.**

### Điểm chính:
1. **Authentication/Authorization:** BCrypt password hashing + JWT validation đầy đủ + method-level security
2. **Web Security:** CSRF protection (session-based) + Security headers + Rate limiting
3. **Data Protection:** Không log sensitive data + Secret management + Input sanitization
4. **Database Security:** Parameterized queries (chống SQL injection)
5. **Session Security:** Secure cookies + timeout + invalidation (cho session-based apps)

### Critical violations (🔴 -10 điểm):
- Password plaintext/MD5 hashing
- SQL injection vulnerabilities
- XSS vulnerabilities (không sanitize input)
- JWT không validate signature/expiry
- Hardcoded secrets
- Sensitive data trong logs
- Không có rate limiting trên auth endpoints
- CSRF disabled cho session-based apps

### Recommended practices (🟠 -5 điểm):
- Method-level security (`@PreAuthorize`)
- Security headers (HSTS, CSP, X-Frame-Options)
- Principle of least privilege
- Secure session management (nếu dùng session)

**Lưu ý:** Nhiều best practices trong domain này là compliance requirements (OWASP, PCI-DSS, GDPR), không chỉ là coding style. Vi phạm có thể dẫn đến legal liability và financial penalties.
