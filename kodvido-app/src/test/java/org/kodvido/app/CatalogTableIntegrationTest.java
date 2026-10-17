package org.kodvido.app;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;
import org.kodvido.engine.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:catalogtable;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CatalogTableIntegrationTest {
    @Autowired MockMvc mvc;@Autowired CatalogService catalog;@Autowired ProjectDetailsService details;
    @Autowired ObservationCache cache;@Autowired Environment environment;
    @TempDir Path temp;UUID id;
    @BeforeEach void setup()throws Exception{
        catalog.list().forEach(p->catalog.remove(p.id()));Files.writeString(temp.resolve("pom.xml"),"<project><properties><java.version>&lt;script&gt;21&lt;/script&gt;</java.version></properties></project>");
        var p=catalog.preview(List.of(temp.toString()));catalog.confirm(p.id(),List.of(0));id=catalog.list().getFirst().id();
    }
    CatalogOverviewService.Page page(org.springframework.test.web.servlet.MvcResult r){return (CatalogOverviewService.Page)r.getModelAndView().getModel().get("catalog");}
    @Test void observationsAppearInFilteredTableWithoutRescansAndRelocationClearsThem()throws Exception{
        assertThat(environment.getProperty("server.port")).isEqualTo("8930");
        var initial=mvc.perform(get("/catalog")).andExpect(status().isOk()).andExpect(content().string(containsString("catalog-table"))).andReturn();
        assertThat(page(initial).rows().getFirst().observation()).isNull();assertThat(details.interactions(id)).isEmpty();
        mvc.perform(post("/projects/"+id+"/inspect").with(csrf())).andExpect(status().isOk());var history=details.interactions(id);
        Files.delete(temp.resolve("pom.xml"));
        var filtered=mvc.perform(get("/catalog").param("q","21").param("technology","JAVA").param("filter","OBSERVED").param("sort","OBSERVED"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;21&lt;/script&gt;"))).andReturn();
        assertThat(page(filtered).filtered()).isEqualTo(1);assertThat(details.interactions(id)).isEqualTo(history);
        assertThat(Files.exists(temp.resolve("pom.xml"))).isFalse();
        Path moved=Files.createDirectory(temp.resolve("moved"));
        mvc.perform(post("/projects/"+id+"/relocate").with(csrf()).param("path",moved.toString())).andExpect(status().is3xxRedirection());
        var after=mvc.perform(get("/catalog").param("filter","UNOBSERVED")).andExpect(status().isOk()).andReturn();
        assertThat(page(after).filtered()).isEqualTo(1);assertThat(page(after).rows().getFirst().observation()).isNull();
        mvc.perform(post("/projects/"+id+"/inspect").with(csrf())).andExpect(status().isOk());var project=catalog.list().getFirst();assertThat(cache.find(project)).isPresent();
        mvc.perform(post("/projects/"+id+"/remove").with(csrf())).andExpect(status().is3xxRedirection());assertThat(cache.find(project)).isEmpty();assertThat(Files.isDirectory(moved)).isTrue();
    }
    @Test void emptyResultsEscapedFiltersAndInvalidRequestsAreHandled()throws Exception{
        mvc.perform(get("/catalog").param("q","<script>absent</script>")).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;absent&lt;/script&gt;")))
            .andExpect(content().string(containsString("Aucun projet ne correspond")));
        mvc.perform(get("/catalog").param("page","-1")).andExpect(status().isBadRequest());mvc.perform(get("/catalog").param("q","x".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(get("/catalog").param("sort","invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/catalog").param("page","999")).andExpect(status().isOk());
        assertThat(details.interactions(id)).isEmpty();
    }
}
