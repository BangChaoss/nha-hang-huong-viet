package com.huongViet.config;

import com.huongViet.auth.JwtAuthFilter;
import com.huongViet.reservation.ReservationRateLimiterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ReservationRateLimiterFilter reservationRateLimiterFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          ReservationRateLimiterFilter reservationRateLimiterFilter,
                          @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8081}") List<String> allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.reservationRateLimiterFilter = reservationRateLimiterFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/payments/vnpay/create", "/api/payments/vnpay/ipn", "/api/payments/vnpay/return", "/api/payments/vnpay/orders/**").permitAll()
                // UC08: Public reservation endpoints
                .requestMatchers(HttpMethod.POST, "/api/reservations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reservations/availability").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reservations/time-slots").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reservations/zones").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reservations/guest-lookup").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reservations/my-bookings").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/reservations/*/cancel").permitAll()
                .requestMatchers("/api/account/**").authenticated()
                .requestMatchers("/api/tables/**").authenticated()
                .requestMatchers("/api/zones/**").authenticated()
                .requestMatchers("/api/floor-plan/**").authenticated()
                .requestMatchers("/api/vouchers/validate", "/api/vouchers/redeem").permitAll()
                // UC-14: thực đơn là công khai, khách chưa đăng nhập vẫn xem được
                .requestMatchers("/api/menu/**").permitAll()
                // UC-15, UC-17: gọi món và xem chi tiết order — nhân viên phục vụ
                .requestMatchers("/api/orders/**").hasAnyAuthority(
                    "STAFF", "ROLE_STAFF", "CHEF", "ROLE_CHEF", "MANAGER", "ROLE_MANAGER", "ADMIN", "ROLE_ADMIN"
                )
                // UC-16: màn hình bếp chỉ để xem — đầu bếp không được đổi trạng thái phiếu.
                .requestMatchers(HttpMethod.PATCH, "/api/kitchen/tickets/*/status").hasAnyAuthority(
                    "MANAGER", "ROLE_MANAGER", "ADMIN", "ROLE_ADMIN"
                )
                // UC-16: hàng đợi phiếu bếp cho bếp, phục vụ và quản lý cùng theo dõi
                .requestMatchers("/api/kitchen/**").hasAnyAuthority(
                    "STAFF", "ROLE_STAFF", "CHEF", "ROLE_CHEF", "MANAGER", "ROLE_MANAGER", "ADMIN", "ROLE_ADMIN"
                )
                // Nhân viên phục vụ cần mở/đóng bàn ngay tại bàn (UC-15, BR-27)
                .requestMatchers("/api/admin/tables/*/status").hasAnyAuthority(
                    "STAFF", "ROLE_STAFF", "MANAGER", "ROLE_MANAGER", "ADMIN", "ROLE_ADMIN"
                )
                // UC09, UC10, UC13: Staff and Managers can manage reservations
                .requestMatchers("/api/admin/reservations/**").hasAnyAuthority(
                    "STAFF", "ROLE_STAFF", "MANAGER", "ROLE_MANAGER", "ADMIN", "ROLE_ADMIN"
                )
                .requestMatchers("/api/admin/**").hasAnyAuthority(
                    "MANAGER", "ROLE_MANAGER", "ADMIN", "ROLE_ADMIN"
                )
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(reservationRateLimiterFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
