package org.kodvido.app;
import org.kodvido.detection.ProjectDirectoryScanner;
import org.kodvido.engine.CatalogService;
import org.kodvido.persistence.JdbcCatalog;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import javax.sql.DataSource;
import java.time.Clock;
@Configuration
public class AppConfiguration {
    @Bean CatalogService catalogService(DataSource source) {
        return new CatalogService(new ProjectDirectoryScanner(), new JdbcCatalog(source), Clock.systemUTC());
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")))
            .build(); // CSRF enabled, no CORS, no remote assets.
    }
}
