package org.kodvido.app;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.kodvido.engine.*;
import org.kodvido.domain.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:backupapp;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BackupIntegrationTest {
    @Autowired MockMvc mvc; @Autowired CatalogService catalog; @Autowired WorkflowService workflow;
    @TempDir Path temp; UUID id;
    @BeforeEach void setup() throws Exception {
        catalog.list().forEach(p->catalog.remove(p.id()));
        Files.writeString(temp.resolve("pom.xml"),"<project/>");Files.writeString(temp.resolve("PROJECT.md"),"owned document");
        var scan=catalog.preview(List.of(temp.toString()));catalog.confirm(scan.id(),List.of(0));id=catalog.list().getFirst().id();
        workflow.save(id,0,List.of(new WorkflowNotes.Phase(LocalDate.of(2026,1,1),"phase",WorkflowNotes.State.DONE)),"<script>build()</script>","run");
    }
    byte[] export() throws Exception {
        return mvc.perform(post("/backup/export").with(csrf())).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition","attachment; filename=kodvido-catalog.json"))
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
    }
    BackupService.Preview preview(byte[] bytes) throws Exception {
        var result=mvc.perform(multipart("/backup/preview").file(new MockMultipartFile("file","catalog.json","application/json",bytes)).with(csrf()))
            .andExpect(status().isOk()).andExpect(view().name("backup-preview"))
            .andExpect(content().string(containsString("&lt;script&gt;build()&lt;/script&gt;"))).andReturn();
        return (BackupService.Preview)result.getModelAndView().getModel().get("preview");
    }
    @Test void restoreRequiresReviewAndPreservesNotesIdentityDatesAndFiles() throws Exception {
        mvc.perform(get("/backup")).andExpect(status().isOk());
        var original=catalog.list().getFirst();var notes=workflow.load(id);var bytes=export();
        assertThat(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("owned document");
        var p=preview(bytes);assertThat(catalog.list()).containsExactly(original);
        mvc.perform(post("/backup/confirm").with(csrf()).param("token",p.id().toString()).param("path",original.path().toString()))
            .andExpect(status().isBadRequest());assertThat(workflow.load(id)).isEqualTo(notes);
        catalog.remove(id);
        String moved=temp.resolve("missing, with comma").toString();
        mvc.perform(post("/backup/confirm").with(csrf()).param("token",p.id().toString()).param("path",moved)).andExpect(status().is3xxRedirection());
        var restored=catalog.list().getFirst();assertThat(restored.id()).isEqualTo(id);assertThat(restored.createdAt()).isEqualTo(original.createdAt());
        assertThat(restored.updatedAt()).isEqualTo(original.updatedAt());assertThat(restored.path()).isEqualTo(Path.of(moved));
        assertThat(workflow.load(id)).isEqualTo(notes);assertThat(Files.exists(Path.of(moved))).isFalse();
        assertThat(Files.readString(temp.resolve("PROJECT.md"))).isEqualTo("owned document");
        mvc.perform(post("/backup/confirm").with(csrf()).param("token",p.id().toString()).param("path",moved)).andExpect(status().isBadRequest());
    }
    @Test void exportPreviewAndConfirmRequireCsrf() throws Exception {
        mvc.perform(get("/backup/export")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/backup/export")).andExpect(status().isForbidden());
        mvc.perform(multipart("/backup/preview").file(new MockMultipartFile("file",export()))).andExpect(status().isForbidden());
        mvc.perform(post("/backup/confirm").param("token",UUID.randomUUID().toString()).param("path",temp.toString())).andExpect(status().isForbidden());
    }
    @Test void invalidJsonIsBoundedStrictAndDoesNotEchoPrivateInput() throws Exception {
        String valid=new String(export(),java.nio.charset.StandardCharsets.UTF_8);
        for(String bad:List.of("null","{}","{secret-path",valid+" {}",valid.replace("\"version\" : 2","\"version\" : 3"),
            valid.replace("\"version\" : 2","\"version\" : 2, \"version\" : 2"),
            valid.replace("\"version\" : 2","\"unknown\" : \"secret-path\", \"version\" : 2"),
            "[".repeat(40)+"]".repeat(40),"x".repeat(8*1024*1024+1))) {
            mvc.perform(multipart("/backup/preview").file(new MockMultipartFile("file",bad.getBytes(java.nio.charset.StandardCharsets.UTF_8))).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-path"))));
        }
        assertThat(catalog.list()).hasSize(1);assertThat(workflow.load(id).revision()).isEqualTo(1);
    }
}
