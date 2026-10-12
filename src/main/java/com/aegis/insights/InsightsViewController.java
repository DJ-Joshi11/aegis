package com.aegis.insights;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the analytics page; all data comes from {@code /api/insights} in the browser. */
@Controller
public class InsightsViewController {

    @GetMapping({"/insights", "/analytics"})
    public String insights() {
        return "insights";
    }
}
