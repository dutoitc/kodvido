package org.kodvido.app;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.kodvido.engine.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:workflowapp;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class WorkflowIntegrationTest {
    @Autowired MockMvc mvc; @Autowired CatalogService catalog; @Autowired WorkflowService workflow;
    @TempDir Path temp; UUID id; String url;
    @BeforeEach void setup() throws Exception {
        catalog.list().forEach(p -> catalog.remove(p.id()));
        Files.writeString(temp.resolve("pom.xml"),"<project/>");
        Files.writeString(temp.resolve("PROJECT.md"),"project-owned document");
        var preview=catalog.preview(List.of(temp.toString()));catalog.confirm(preview.id(),List.of(0));
        id=catalog.list().getFirst().id();url="/projects/"+id+"/workflow";
    }
    MockHttpServletRequestBuilder form(long revision) {
        return post(url).param("revision",Long.toString(revision))
            .param("date","2026-01-01","","","","")
            .param("description","<script>phase()</script>","","","","")
            .param("state","DONE","PLANNED","PLANNED","PLANNED","PLANNED")
            .param("development","mvn verify\n<script>bad()</script>").param("operations","java -jar app.jar");
    }
    @Test void explicitSaveRendersEscapedNotesAndKeepsProjectFilesIntact() throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(view().name("workflow"));
        assertThat(workflow.load(id).revision()).isZero();
        mvc.perform(form(0)).andExpect(status().isForbidden());
        mvc.perform(form(0).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/projects/"+id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;phase()&lt;/script&gt;")))
            .andExpect(content().string(containsString("&lt;script&gt;bad()&lt;/script&gt;")))
            .andExpect(content().string(containsString("java -jar app.jar")));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(containsString("mvn verify")));
        assertThat(workflow.load(id).revision()).isEqualTo(1);
        assertThat(Files.readString(temp.resolve("PROJECT.md"))).isEqualTo("project-owned document");
        try(var files=Files.list(temp)) { assertThat(files.map(p->p.getFileName().toString()).toList()).containsExactlyInAnyOrder("pom.xml","PROJECT.md"); }
    }
    @Test void staleTabCannotOverwriteAndRemovalRejectsOldForms() throws Exception {
        mvc.perform(form(0).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(form(0).with(csrf()).with(r -> {r.setParameter("operations","stale <script>text</script>"); return r;})).andExpect(status().isConflict())
            .andExpect(content().string(containsString("stale &lt;script&gt;text&lt;/script&gt;")));
        assertThat(workflow.load(id).operations()).isEqualTo("java -jar app.jar");
        catalog.remove(id);
        mvc.perform(get(url)).andExpect(status().isBadRequest());
        mvc.perform(form(1).with(csrf())).andExpect(status().isBadRequest());
        assertThat(Files.exists(temp.resolve("PROJECT.md"))).isTrue();
    }
    @Test void incompleteDatesStatesAndOversizedNotesAreRejected() throws Exception {
        mvc.perform(post(url).with(csrf()).param("revision","0").param("date","2026-01-01").param("description","phase").param("state","DONE").param("development","").param("operations","")).andExpect(status().isBadRequest());
        // Separate requests replace the repeated field instead of appending values.
        mvc.perform(post(url).with(csrf()).param("revision","0").param("date","bad","","","","").param("description","phase","","","","")
            .param("state","DONE","PLANNED","PLANNED","PLANNED","PLANNED").param("development","").param("operations","")).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(csrf()).param("revision","0").param("date","2026-01-01","","","","").param("description","phase","","","","")
            .param("state","INVALID","PLANNED","PLANNED","PLANNED","PLANNED").param("development","").param("operations","")).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(csrf()).param("revision","0").param("date","","","","","").param("description","","","","","")
            .param("state","PLANNED","PLANNED","PLANNED","PLANNED","PLANNED").param("development","x".repeat(4001)).param("operations","")).andExpect(status().isBadRequest());
        assertThat(workflow.load(id).revision()).isZero();
    }
}
