package com.huongViet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Phục vụ các tệp tĩnh nằm trong thư mục lưu ảnh cục bộ.
 *
 * <p>Ảnh món ăn tải lên được lưu ở {@code app.upload.dir} và trả về cho trình
 * duyệt qua đường dẫn {@code /uploads/**}.</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String uploadDirectory;
    private final String publicPath;

    public WebMvcConfig(
            @Value("${app.upload.dir:./uploads}") String uploadDirectory,
            @Value("${app.upload.public-path:/uploads}") String publicPath) {
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize().toString();
        this.publicPath = publicPath.endsWith("/")
                ? publicPath.substring(0, publicPath.length() - 1)
                : publicPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(publicPath + "/**")
                .addResourceLocations("file:" + uploadDirectory + "/");
    }
}
