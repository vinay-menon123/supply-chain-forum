package com.cscen.forum.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final File uploadDir = new File("uploads").getAbsoluteFile();
    private final File publicDir = new File("public").getAbsoluteFile();

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("*").allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        uploadDir.mkdirs();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDir.toURI().toString());

        // Single-image deployments (e.g. Railway) bake the frontend build into
        // ./public; docker-compose and k8s use a separate nginx container, so
        // this handler simply never matches there.
        if (publicDir.isDirectory()) {
            registry.addResourceHandler("/**")
                    .addResourceLocations(publicDir.toURI().toString())
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) throws IOException {
                            if (resourcePath.startsWith("api/") || resourcePath.startsWith("uploads/")) {
                                return null;
                            }
                            Resource requested = location.createRelative(resourcePath);
                            if (requested.exists() && requested.isReadable()) {
                                return requested;
                            }
                            // SPA fallback: client-side routes resolve to index.html
                            Resource index = new FileSystemResource(new File(publicDir, "index.html"));
                            return index.exists() ? index : null;
                        }
                    });
        }
    }
}
