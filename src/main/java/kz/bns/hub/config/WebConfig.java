package kz.bns.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.docs.path:./documents}")
    private String docsPath;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Старый путь (для обратной совместимости)
        String oldLocation = Path.of(docsPath, "images").toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(oldLocation)
                .setCachePeriod(3600);

        // META скриншоты → /meta/images/**
        String metaLocation = Path.of(docsPath, "meta", "images").toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/meta/images/**")
                .addResourceLocations(metaLocation)
                .setCachePeriod(3600);

        // БДАП скриншоты → /bdap/images/**
        String bdapLocation = Path.of(docsPath, "bdap", "images").toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/bdap/images/**")
                .addResourceLocations(bdapLocation)
                .setCachePeriod(3600);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/images/**")
                .allowedOrigins(origins)
                .allowedMethods("GET");
        registry.addMapping("/meta/images/**")
                .allowedOrigins(origins)
                .allowedMethods("GET");
        registry.addMapping("/bdap/images/**")
                .allowedOrigins(origins)
                .allowedMethods("GET");
    }
}