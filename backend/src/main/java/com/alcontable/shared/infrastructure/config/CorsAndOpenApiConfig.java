package com.alcontable.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsAndOpenApiConfig implements WebMvcConfigurer {

    @Value("${CORS_ALLOWED_ORIGINS:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns.length > 0 ? patterns : new String[]{"*"})
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Bean
    public OpenAPI alcontableOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ALcontable API — Suite Contable, Tributaria (DIAN), Laboral (CST / Ley 2101 / UGPP) y Territorial (DIVIPOLA)")
                        .version("1.0.0")
                        .description("API REST abierta para la comunidad construida en Java 21 + Spring Boot 3 bajo DDD Táctico y Arquitectura Hexagonal.")
                        .contact(new Contact().name("Alexmar Gómez").url("https://github.com/alexmargomez/alcontable"))
                        .license(new License().name("MIT License")));
    }
}
