package com.desc.calc.web.in;

import com.desc.calc.msfs.MSFSTelemetry;
import com.desc.calc.web.service.TelemetryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Serves live itinerary readings separately from page navigation and panel requests. */
@RestController
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {

        this.telemetryService = telemetryService;
    }

    @GetMapping("/flight-plan/telemetry")
    public MSFSTelemetry telemetry(HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        return telemetryService.getTelemetry();
    }

    /** Page-specific route context avoids shared state between different itineraries/tabs. */
    @PostMapping("/flight-plan/telemetry")
    public TelemetryService.LivePlan itinerary(@RequestBody TelemetryService.PlanContext plan,
                                              HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        return telemetryService.getLivePlan(plan);
    }
}
