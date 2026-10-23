package com.alexmar.colombialex.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsAndOpenApiConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Bean
    public OpenAPI colombiaLexOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ColombiaLex API — Motor Legal, Laboral (CST / Ley 2101), Tributario (DIAN) y Territorial (DIVIPOLA)")
                        .version("1.0.0")
                        .description("API REST abierta para la comunidad construida en Java 21 + Spring Boot 3 bajo DDD Táctico y Arquitectura Hexagonal.")
                        .contact(new Contact().name("Alexmar Gómez").url("https://github.com/alexmargomez"))
                        .license(new License().name("MIT License")));
    }
}
