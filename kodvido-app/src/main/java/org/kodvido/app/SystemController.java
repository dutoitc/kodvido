package org.kodvido.app;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
public class SystemController {
 @GetMapping("/api/system") public Map<String, String> system() {
   return Map.of("name", "Kodvido", "stage", "M2.6", "status", "local project cockpit");
 }
}
