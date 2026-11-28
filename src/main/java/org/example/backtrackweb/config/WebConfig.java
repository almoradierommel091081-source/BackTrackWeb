package org.example.backtrackweb.config;

import org.example.backtrackweb.tenant.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Binds the tenant for every browser page and form post.
 *
 * <p>The machine API is excluded because its own filter binds the tenant from
 * the device token (and clears it when the request ends).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    public WebConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/**", "/css/**", "/js/**", "/images/**", "/icons/**",
                        "/manifest.json", "/sw.js", "/favicon.ico");
    }
}
