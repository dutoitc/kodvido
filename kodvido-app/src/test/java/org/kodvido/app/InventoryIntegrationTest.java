package org.kodvido.app;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.kodvido.engine.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:inventory;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InventoryIntegrationTest {
 @Autowired MockMvc mvc;@Autowired CatalogService catalog;@Autowired InventoryService service;@TempDir Path temp;
 UUID id;
 @BeforeEach void setup()throws Exception{catalog.list().forEach(p->catalog.remove(p.id()));Files.writeString(temp.resolve("pom.xml"),"<project><properties><java.version>17</java.version></properties></project>");var preview=catalog.preview(List.of(temp.toString()));catalog.confirm(preview.id(),List.of(0));id=catalog.list().getFirst().id();}
 @Test void dashboardDetailNotesFilteringAndRescanAreUsableAndEscaped()throws Exception{
  mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("inventory-table")));
  service.refresh(id,false);
  mvc.perform(get("/").param("technology","Java").param("sort","JAVA")).andExpect(status().isOk()).andExpect(content().string(containsString("17")));
  mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("Rescanner")));
  mvc.perform(post("/inventory/"+id+"/notes").param("note","no")).andExpect(status().isForbidden());
  mvc.perform(post("/inventory/"+id+"/notes").with(csrf()).param("lifecycle","PAUSED").param("note","<script>next</script>").param("tasks","task").param("revision","0")).andExpect(status().is3xxRedirection());
  mvc.perform(get("/").param("q","next").param("lifecycle","PAUSED")).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;next&lt;/script&gt;")));
  mvc.perform(post("/inventory/"+id+"/notes").with(csrf()).param("lifecycle","ACTIVE").param("note","recover me").param("tasks","task").param("revision","0")).andExpect(status().isOk()).andExpect(content().string(containsString("recover me")));
  assertThat(service.row(id).notes().lifecycle().name()).isEqualTo("PAUSED");
  mvc.perform(get("/").param("q","absent")).andExpect(status().isOk()).andExpect(content().string(containsString("Aucun projet ne correspond")));
  mvc.perform(get("/").param("page","-1")).andExpect(status().isBadRequest());mvc.perform(get("/").param("sort","bad")).andExpect(status().isBadRequest());
  mvc.perform(post("/inventory/"+id+"/refresh").with(csrf())).andExpect(status().is3xxRedirection());
  assertThat(Files.readString(temp.resolve("pom.xml"))).contains("<java.version>17</java.version>");
 }
 @Test void rootConfigurationPersistsAndHasCsrfProtection()throws Exception{
  mvc.perform(post("/inventory/roots").param("roots",temp.toString())).andExpect(status().isForbidden());
  mvc.perform(post("/inventory/roots").with(csrf()).param("roots",temp.toString())).andExpect(status().is3xxRedirection());assertThat(service.roots()).containsExactly(temp.toString());
  mvc.perform(post("/inventory/roots").with(csrf()).param("roots","relative")).andExpect(status().isBadRequest());
  mvc.perform(post("/inventory/refresh").with(csrf())).andExpect(status().is3xxRedirection());
 }
}
