package org.kodvido.app;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.kodvido.engine.*;
import org.kodvido.domain.*;
import org.kodvido.domain.ProjectGuidance.*;
import org.kodvido.domain.ProjectInspection.Status;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:guidanceapp;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GuidanceIntegrationTest {
    @Autowired MockMvc mvc;@Autowired CatalogService catalog;@Autowired ProjectDetailsService details;
    @Autowired WorkflowService workflow;@Autowired GuidanceService guidance;
    @TempDir Path temp;UUID id;
    final String secret="PRIVATE-TOKEN-X42";
    @BeforeEach void setup()throws Exception{
        catalog.list().forEach(p->catalog.remove(p.id()));Files.writeString(temp.resolve("pom.xml"),"<project><properties><java.version>"+secret+"</java.version></properties></project>");
        Files.writeString(temp.resolve("PROJECT.md"),secret);var p=catalog.preview(List.of(temp.toString()));catalog.confirm(p.id(),List.of(0));id=catalog.list().getFirst().id();
        workflow.save(id,0,List.of(),secret,secret);
    }
    @Test void pageSuggestsWithoutScanningAndInspectionExposesOnlySanitizedDrafts()throws Exception{
        var history=details.interactions(id);var source=Files.readString(temp.resolve("pom.xml"));
        mvc.perform(get("/projects/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("NOT_OBSERVED")));
        assertThat(details.interactions(id)).isEqualTo(history);
        var result=mvc.perform(post("/projects/"+id+"/inspect").with(csrf())).andExpect(status().isOk())
            .andExpect(content().string(containsString("Brouillons de signalement"))).andReturn();
        @SuppressWarnings("unchecked") var drafts=(List<GuidanceView.Draft>)result.getModelAndView().getModel().get("gapDrafts");
        assertThat(drafts).hasSize(1);assertThat(drafts.getFirst().detector()).isEqualTo("STACK");assertThat(drafts.getFirst().status()).isEqualTo("PARTIAL");
        assertThat(drafts.getFirst().text()).doesNotContain(secret,temp.toString(),id.toString());
        var observed=details.interactions(id);
        byte[] bytes=mvc.perform(post("/detector-gap/draft").with(csrf()).param("detector","STACK").param("status","PARTIAL").param("notes",secret).param("path",temp.toString()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition","attachment; filename=kodvido-detector-gap.md"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(drafts.getFirst().text());
        assertThat(details.interactions(id)).isEqualTo(observed);assertThat(Files.readString(temp.resolve("pom.xml"))).isEqualTo(source);
        assertThat(Files.readString(temp.resolve("PROJECT.md"))).isEqualTo(secret);
        try(var paths=Files.list(temp)){assertThat(paths.count()).isEqualTo(2);}
    }
    @Test void downloadRequiresCsrfRejectsInvalidStatesAndNeverEchoesUntrustedInput()throws Exception{
        mvc.perform(get("/detector-gap/draft")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/detector-gap/draft").param("detector","GIT").param("status","FAILED")).andExpect(status().isForbidden());
        for(var detector:List.of("FILES","GIT","STACK"))for(var state:List.of("PARTIAL","UNKNOWN","UNSUPPORTED","FAILED")){
            mvc.perform(post("/detector-gap/draft").with(csrf()).param("detector",detector).param("status",state)).andExpect(status().isOk());
        }
        for(var state:List.of("MISSING","SUPPORTED",secret))mvc.perform(post("/detector-gap/draft").with(csrf()).param("detector","GIT").param("status",state))
            .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(containsString(secret))));
        mvc.perform(post("/detector-gap/draft").with(csrf()).param("detector",secret).param("status","FAILED")).andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(secret))));
    }
    @Test void everyReasonAndTopicHasAnActionablePresentation(){
        var advice=Arrays.stream(Reason.values()).map(reason->new Advice(Topic.FILES,reason)).toList();
        var cards=GuidanceView.cards(new ProjectGuidance(advice,List.of()));
        assertThat(cards).allSatisfy(c->{assertThat(c.title()).isNotBlank();assertThat(c.reason()).isNotBlank();assertThat(c.suggestion()).isNotBlank();});
        for(var topic:Topic.values())assertThat(GuidanceView.cards(new ProjectGuidance(List.of(new Advice(topic,Reason.UNKNOWN)),List.of())).getFirst().title()).isNotBlank();
    }
}
