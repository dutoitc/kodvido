package org.kodvido.app;
import org.kodvido.detection.ProjectDirectoryScanner;
import org.kodvido.detection.ProjectInspector;
import org.kodvido.engine.ProjectDetailsService;
import org.kodvido.engine.CatalogService;
import org.kodvido.persistence.JdbcCatalog;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import javax.sql.DataSource;
import java.time.Clock;
@Configuration
public class AppConfiguration {
    @Bean org.kodvido.engine.ObservationCache observationCache() { return new org.kodvido.engine.ObservationCache(); }
    @Bean org.kodvido.engine.CatalogOverviewService catalogOverviewService(DataSource source,org.kodvido.engine.ObservationCache cache) {
        return new org.kodvido.engine.CatalogOverviewService(new JdbcCatalog(source),new org.kodvido.persistence.JdbcWorkflowStore(source),
            new org.kodvido.persistence.JdbcBackupAssessmentStore(source),new org.kodvido.persistence.JdbcInteractionStore(source),cache);
    }
    @Bean CatalogService catalogService(DataSource source,org.kodvido.engine.ObservationCache cache) {
        return new CatalogService(new ProjectDirectoryScanner(), new JdbcCatalog(source), Clock.systemUTC(),cache);
    }
    @Bean ProjectDetailsService projectDetailsService(DataSource source,org.kodvido.engine.ObservationCache cache) {
        return new ProjectDetailsService(new JdbcCatalog(source),
            new ProjectInspector(Clock.systemUTC()), new org.kodvido.persistence.JdbcInteractionStore(source), Clock.systemUTC(),cache);
    }
    @Bean org.kodvido.engine.WorkflowService workflowService(DataSource source) {
        return new org.kodvido.engine.WorkflowService(new JdbcCatalog(source),
            new org.kodvido.persistence.JdbcWorkflowStore(source), Clock.systemUTC());
    }
    @Bean org.kodvido.engine.BackupService backupService(DataSource source) {
        return new org.kodvido.engine.BackupService(new org.kodvido.persistence.JdbcBackupStore(source), Clock.systemUTC());
    }
    @Bean org.kodvido.engine.BackupAssessmentService backupAssessmentService(DataSource source) {
        return new org.kodvido.engine.BackupAssessmentService(new JdbcCatalog(source),
            new org.kodvido.persistence.JdbcBackupAssessmentStore(source),Clock.systemUTC());
    }
    @Bean org.kodvido.engine.GuidanceService guidanceService() { return new org.kodvido.engine.GuidanceService(); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")))
            .build(); // CSRF enabled, no CORS, no remote assets.
    }
}
