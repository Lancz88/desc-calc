package com.desc.calc.web.in;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/** Carries page-local source identity through generate, edit and failed save submissions. */
@ControllerAdvice(assignableTypes = {HomeController.class, SavedFlightPlanController.class})
public class FlightPlanSourceAdvice {
    @ModelAttribute
    public void source(@RequestParam(defaultValue = "ROUTE INPUT") String importSource,
                       @RequestParam(defaultValue = "Edited waypoints") String importDetail,
                       @RequestParam(defaultValue = "") String planName, Model model) {
        model.addAttribute("importSource", importSource);
        model.addAttribute("importDetail", importDetail);
        model.addAttribute("planName", planName);
    }
}
