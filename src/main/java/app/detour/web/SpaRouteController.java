package app.detour.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the one public SPA document route; API authorization remains in SecurityConfiguration. */
@Controller
class SpaRouteController {
    @GetMapping("/profile")
    String profileDocument() {
        return "forward:/index.html";
    }
}
