package org.kodvido.app;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.kodvido.engine.*;
import org.kodvido.domain.BackupAssessment.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:evidenceapp;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BackupAssessmentIntegrationTest {
    @Autowired MockMvc mvc;@Autowired CatalogService catalog;@Autowired BackupAssessmentService service;
    @TempDir Path temp;UUID id;String url;
    @BeforeEach void setup()throws Exception{
        catalog.list().forEach(p->catalog.remove(p.id()));Files.writeString(temp.resolve("pom.xml"),"<project/>");
        var p=catalog.preview(List.of(temp.toString()));catalog.confirm(p.id(),List.of(0));id=catalog.list().getFirst().id();url="/projects/"+id+"/backup-evidence";
    }
    MockHttpServletRequestBuilder form(){
        var request=post(url).param("revision","0");
        for(var d:Dimension.values())request.param(d+".state","DECLARED_OK").param(d+".date","2026-01-01").param(d+".evidence","proof, <script>unsafe()</script>");
        return request;
    }
    @Test void csrfEscapingConflictAndNonDestructiveSave()throws Exception{
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(containsString("UNKNOWN")));
        mvc.perform(form()).andExpect(status().isForbidden());mvc.perform(form().with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get(url)).andExpect(status().isOk());mvc.perform(get("/projects/"+id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("proof, &lt;script&gt;unsafe()&lt;/script&gt;")));
        mvc.perform(form().with(csrf())).andExpect(status().isConflict()).andExpect(content().string(containsString("proof, &lt;script&gt;unsafe()&lt;/script&gt;")));
        assertThat(service.load(id).revision()).isEqualTo(1);assertThat(Files.readString(temp.resolve("pom.xml"))).isEqualTo("<project/>");
        try(var files=Files.list(temp)){assertThat(files.count()).isEqualTo(1);}
        catalog.remove(id);mvc.perform(get(url)).andExpect(status().isBadRequest());mvc.perform(form().with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void malformedRequestsNeverSave()throws Exception{
        mvc.perform(post(url).with(csrf()).param("revision","0")).andExpect(status().isBadRequest());
        mvc.perform(form().with(csrf()).param("GIT_REMOTE.state","UNKNOWN")).andExpect(status().isBadRequest());
        for(var change:List.of(Map.entry("GIT_REMOTE.date","not-date"),Map.entry("GIT_REMOTE.state","VERIFIED"),Map.entry("GIT_REMOTE.date","2999-01-01"),Map.entry("GIT_REMOTE.evidence",""))){
            mvc.perform(form().with(csrf()).with(q->{q.setParameter(change.getKey(),change.getValue());return q;})).andExpect(status().isBadRequest());
        }
        assertThat(service.load(id).revision()).isZero();
    }
    @Test void jsonRoundtripKeepsDeclarationsAndSupportsLegacy()throws Exception{
        mvc.perform(form().with(csrf())).andExpect(status().is3xxRedirection());var original=service.load(id);
        String json=mvc.perform(post("/backup/export").with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"version\" : 2","DECLARED_OK");catalog.remove(id);
        var result=mvc.perform(multipart("/backup/preview").file(new MockMultipartFile("file","catalog.json","application/json",json.getBytes(java.nio.charset.StandardCharsets.UTF_8))).with(csrf()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("non vérifiées"))).andReturn();
        var preview=(BackupService.Preview)result.getModelAndView().getModel().get("preview");
        mvc.perform(post("/backup/confirm").with(csrf()).param("token",preview.id().toString()).param("path",temp.toString())).andExpect(status().is3xxRedirection());
        assertThat(service.load(id)).isEqualTo(original);
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("version",1);
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("projects").get(0)).remove("assessment");
        catalog.remove(id);
        var legacyResult=mvc.perform(multipart("/backup/preview").file(new MockMultipartFile("file","legacy.json","application/json",tree.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).with(csrf())).andExpect(status().isOk()).andReturn();
        var legacyPreview=(BackupService.Preview)legacyResult.getModelAndView().getModel().get("preview");
        mvc.perform(post("/backup/confirm").with(csrf()).param("token",legacyPreview.id().toString()).param("path",temp.toString())).andExpect(status().is3xxRedirection());
        assertThat(service.load(id).revision()).isZero();assertThat(service.load(id).items()).allMatch(i->i.state()==State.UNKNOWN);
    }
}
