package com.desc.calc.web;

import com.desc.calc.processor.obj.Waypoint;
import com.desc.calc.processor.service.CSVService;
import com.desc.calc.web.service.WebDescentService;
import com.desc.calc.web.service.WebPlanDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.desc.calc.processor.GlobalVar.CSV_FILE_PATH;

/**
 * Serves the landing page, imported route editor and calculated descent itinerary.
 */
@Controller
public class HomeController {

    private final CSVService csvService;
    private final WebDescentService webDescentService;

    public HomeController(CSVService csvService, WebDescentService webDescentService) {

        this.csvService = csvService;
        this.webDescentService = webDescentService;
    }

    /**
     * Restricts form binding to route inputs and limits automatically created rows.
     */
    @InitBinder("flightPlan")
    public void bindFlightPlan(WebDataBinder binder) {

        binder.setAutoGrowCollectionLimit(200);
        binder.setAllowedFields("cruiseAltitude", "initialSpeed", "tdExtraDistance", "preparationLeadDistance",
                "waypoints[*].idx", "waypoints[*].name",
                "waypoints[*].distanceToNext", "waypoints[*].altHold", "waypoints[*].altAbove",
                "waypoints[*].altBelow", "waypoints[*].speed");
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    /**
     * Starts a new editor from the bundled CSV without retaining previous edits.
     */
    @GetMapping("/flight-plan")
    public String flightPlan(Model model) {

        List<Waypoint> waypoints = csvService.read(CSV_FILE_PATH);
        WebPlanDTO form = WebPlanDTO.from(waypoints);

        model.addAttribute("flightPlan", form);
        summarize(form, model);

        return "flight-plan";
    }

    /**
     * Calculates from submitted inputs, retaining the editor values on success or failure.
     */
    @PostMapping("/flight-plan")
    public String generate(@ModelAttribute("flightPlan") WebPlanDTO form, BindingResult errors, Model model) {

        form.validate(errors);

        if (!errors.hasErrors()) {
            WebDescentService.Result result = webDescentService.generate(form);

            if (result.error() == null) {
                model.addAttribute("plan", result.plan());
                model.addAttribute("timing", result.timing());
                return "descent-plan";
            }

            errors.reject("calculation", result.error());
        }

        summarize(form, model);

        return "flight-plan";
    }

    /**
     * Reopens the submitted route without calculating again or reloading the CSV.
     */
    @PostMapping("/flight-plan/edit")
    public String edit(@ModelAttribute("flightPlan") WebPlanDTO form, BindingResult errors, Model model) {

        form.validate(errors);
        summarize(form, model);

        return "flight-plan";
    }

    private void summarize(WebPlanDTO form, Model model) {

        List<WebPlanDTO.Row> rows = form.getWaypoints() == null ? List.of() : form.getWaypoints();

        long totalDistance = rows.stream()
                .filter(row -> row != null && row.getDistanceToNext() != null)
                .mapToLong(WebPlanDTO.Row::getDistanceToNext)
                .sum();

        long constrainedWaypoints = rows.stream()
                .filter(row -> row != null && (row.getAltHold() != null || row.getAltAbove() != null
                        || row.getAltBelow() != null || row.getSpeed() != null))
                .count();

        model.addAttribute("totalDistance", totalDistance);
        model.addAttribute("constrainedWaypoints", constrainedWaypoints);
    }
}
