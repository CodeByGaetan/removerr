package com.removerr.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards all non-API, non-static requests to index.html so the Angular
 * router handles client-side navigation (deep links, page refresh).
 * The regex excludes paths with a dot so static assets are served normally.
 */
@Controller
public class SpaController {

    @GetMapping({
        "/",
        "/{a:[^\\.]*}",
        "/{a:[^\\.]*}/{b:[^\\.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
