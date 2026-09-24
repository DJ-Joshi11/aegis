package com.aegis;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * DAY-0 PLACEHOLDER ONLY. Person C's DashboardController (in com.aegis.payout)
 * should take over the "/" route once the real dashboard exists — remove
 * this class at that point so there's no route clash.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "dashboard";
    }
}
