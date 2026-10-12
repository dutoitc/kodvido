package org.kodvido.app;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.kodvido.domain.Interaction;
import org.kodvido.engine.CatalogService;
import org.kodvido.persistence.JdbcInteractionStore;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:interactionsapp;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InteractionIntegrationTest {
    @Autowired MockMvc mvc;@Autowired CatalogService catalog;@Autowired DataSource source;
    @TempDir Path temp;UUID id;String url;JdbcInteractionStore history;
    @BeforeEach void setup() throws Exception {
        catalog.list().forEach(p->catalog.remove(p.id()));history=new JdbcInteractionStore(source);
        Files.writeString(temp.resolve("pom.xml"),"<project/>");Files.writeString(temp.resolve("PROJECT.md"),"source document");
        var scan=catalog.preview(List.of(temp.toString()));catalog.confirm(scan.id(),List.of(0));
        id=catalog.list().getFirst().id();url="/projects/"+id;
    }
    @Test void consultationsAndRejectedRequestsDoNotInventInteractions() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(containsString("Aucune interaction enregistrée")));
        mvc.perform(get(url+"/workflow")).andExpect(status().isOk());
        mvc.perform(get(url+"/documents/PROJECT.md")).andExpect(status().isOk());
        mvc.perform(post(url+"/inspect")).andExpect(status().isForbidden());
        assertThat(history.recent(id)).isEmpty();
        mvc.perform(post(url+"/inspect").with(csrf())).andExpect(status().isOk()).andExpect(content().string(containsString("Inspection demandée")));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(containsString("Dernière interaction enregistrée")));
        assertThat(history.recent(id)).extracting(Interaction::kind).containsExactly(Interaction.Kind.INSPECTION_REQUESTED);
        mvc.perform(get(url)).andExpect(status().isOk());assertThat(history.recent(id)).hasSize(1);
    }
    @Test void savedNotesAndRelocationAppearAndRemovalDeletesOnlyLocalMetadata() throws Exception {
        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(post(url+"/workflow").with(csrf()).param("revision","0")
                .param("date","","","","","").param("description","","","","","")
                .param("state","PLANNED","PLANNED","PLANNED","PLANNED","PLANNED")
                .param("development","build").param("operations","run"))
                .andExpect(attempt==0?status().is3xxRedirection():status().isConflict());
        }
        assertThat(history.recent(id)).hasSize(1);
        mvc.perform(post(url+"/relocate").with(csrf()).param("path","relative")).andExpect(status().isBadRequest());
        assertThat(history.recent(id)).hasSize(1);
        Path moved=Files.createDirectory(temp.resolve("moved"));Files.writeString(moved.resolve("pom.xml"),"<project/>");
        mvc.perform(post(url+"/relocate").with(csrf()).param("path",moved.toString())).andExpect(status().is3xxRedirection());
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(containsString("Notes sauvegardées")))
            .andExpect(content().string(containsString("Projet relocalisé")));
        assertThat(history.recent(id)).extracting(Interaction::kind).containsExactly(Interaction.Kind.RELOCATED,Interaction.Kind.WORKFLOW_SAVED);
        mvc.perform(post(url+"/remove").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(history.recent(id)).isEmpty();assertThat(Files.readString(temp.resolve("PROJECT.md"))).isEqualTo("source document");
        assertThat(Files.readString(moved.resolve("pom.xml"))).isEqualTo("<project/>");
    }
}
