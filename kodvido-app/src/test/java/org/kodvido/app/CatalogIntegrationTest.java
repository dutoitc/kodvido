package org.kodvido.app;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.kodvido.engine.CatalogService;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CatalogIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CatalogService service;
    @TempDir Path temp;
    @BeforeEach void clear() { service.list().forEach(p -> service.remove(p.id())); }
    @Test void completeOnboardingRelocationAndNonDestructiveRemovalThroughHttp() throws Exception {
        Files.writeString(temp.resolve("pyproject.toml"), "[project]");
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Kodvido")));
        var result = mvc.perform(post("/scan").with(csrf()).param("roots", temp.toString())).andExpect(status().isOk()).andExpect(view().name("review")).andReturn();
        var preview = (CatalogService.Preview) result.getModelAndView().getModel().get("preview");
        assertThat(service.list()).isEmpty();
        mvc.perform(post("/import").with(csrf()).param("previewId", preview.id().toString()).param("selected", "0")).andExpect(status().is3xxRedirection());
        var project = service.list().getFirst(); Path moved = temp.resolveSibling(temp.getFileName() + "-moved");
        try {
            Files.move(temp, moved);
            mvc.perform(post("/projects/" + project.id() + "/relocate").with(csrf()).param("path", moved.toString())).andExpect(status().is3xxRedirection());
            assertThat(service.list().getFirst().id()).isEqualTo(project.id());
            mvc.perform(get("/")).andExpect(status().isOk());
            mvc.perform(post("/projects/" + project.id() + "/remove").with(csrf())).andExpect(status().is3xxRedirection());
            assertThat(service.list()).isEmpty(); assertThat(Files.readString(moved.resolve("pyproject.toml"))).isEqualTo("[project]");
        } finally { if (Files.exists(moved)) Files.move(moved, temp); }
    }
    @Test void csrfAndDnsRebindingAreBlocked() throws Exception {
        mvc.perform(post("/scan").param("roots", temp.toString())).andExpect(status().isForbidden());
        mvc.perform(post("/import").param("previewId", UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(get("/").with(r -> { r.setServerName("evil.example"); return r; })).andExpect(status().isForbidden());
        mvc.perform(get("/LICENSE")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("GNU GENERAL PUBLIC LICENSE")));
        mvc.perform(get("/api/system")).andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("M2.10"));
    }
    @Test void errorsAndUnselectedImportsDoNotWrite() throws Exception {
        mvc.perform(post("/scan").with(csrf()).param("roots", "relative")).andExpect(status().isBadRequest());
        Files.writeString(temp.resolve("pom.xml"), "<project/>");
        var p = service.preview(List.of(temp.toString()));
        mvc.perform(post("/import").with(csrf()).param("previewId", p.id().toString())).andExpect(status().isBadRequest());
        assertThat(service.list()).isEmpty();
    }
    @Test void untrustedNamesAreHtmlEscaped() throws Exception {
        // Ampersand is a legal filename character on both target operating systems.
        Path project = Files.createDirectory(temp.resolve("a&b")); Files.writeString(project.resolve("pom.xml"), "<project/>");
        mvc.perform(post("/scan").with(csrf()).param("roots", temp.toString())).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("a&amp;b")));
    }
    @Test void projectInspectionAndLiveDocumentsAreReadOnlyEscapedAndCsrfProtected() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project/>");
        Path doc = temp.resolve("AGENTS.md"); Files.writeString(doc, "<script>bad()</script>");
        var preview = service.preview(List.of(temp.toString())); service.confirm(preview.id(), List.of(0));
        String url = "/projects/" + service.list().getFirst().id();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(view().name("project"));
        mvc.perform(post(url + "/inspect")).andExpect(status().isForbidden());
        mvc.perform(post(url + "/inspect").with(csrf())).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("git-head-1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("NO_LOCAL_GIT_DIRECTORY")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("filesystem-activity-1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("TODO.md")));
        mvc.perform(get(url + "/documents/AGENTS.md")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;bad()&lt;/script&gt;")));
        Files.writeString(doc, "updated source");
        mvc.perform(get(url + "/documents/AGENTS.md")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("updated source")));
        mvc.perform(get(url + "/documents/secret.txt")).andExpect(status().isBadRequest());
        Files.delete(doc);
        mvc.perform(get(url + "/documents/AGENTS.md")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("MISSING")));
        service.remove(service.list().getFirst().id());
        mvc.perform(get(url)).andExpect(status().isBadRequest());
        assertThat(Files.readString(temp.resolve("pom.xml"))).isEqualTo("<project/>");
    }
    @Test void stackDeclarationsAreRenderedEscapedOnlyOnExplicitInspection() throws Exception {
        String pom="<project><properties><java.version>&lt;script&gt;declared()&lt;/script&gt;</java.version></properties></project>";
        Files.writeString(temp.resolve("pom.xml"),pom);
        Files.writeString(temp.resolve("build&test.sh"),"never execute");
        var preview=service.preview(List.of(temp.toString()));service.confirm(preview.id(),List.of(0));
        String url="/projects/"+service.list().getFirst().id();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("declared()"))));
        mvc.perform(post(url+"/inspect").with(csrf())).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("stack-declarations-1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;declared()&lt;/script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("build&amp;test.sh")));
        assertThat(Files.readString(temp.resolve("pom.xml"))).isEqualTo(pom);
        assertThat(Files.readString(temp.resolve("build&test.sh"))).isEqualTo("never execute");
    }

}
