package com.desc.calc.web.in;

import com.desc.calc.msfs.MSFSService;
import com.desc.calc.processor.obj.Waypoint;
import com.desc.calc.processor.service.CSVService;
import com.desc.calc.web.service.WebDescentService;
import com.desc.calc.web.service.WebPlanDTO;
import com.desc.calc.web.service.FlightPlanEditorModel;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

import static com.desc.calc.processor.GlobalVar.CSV_FILE_PATH;

/**
 * Serves the landing page, imported route editor and calculated descent itinerary.
 */
@Controller
public class HomeController {

    private final CSVService csvService;
    private final WebDescentService webDescentService;
    private final MSFSService msfsService;
    private final FlightPlanEditorModel editorModel;

    public HomeController(CSVService csvService, WebDescentService webDescentService, MSFSService msfsService,
                          FlightPlanEditorModel editorModel) {

        this.csvService = csvService;
        this.webDescentService = webDescentService;
        this.msfsService = msfsService;
        this.editorModel = editorModel;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("panelConnected", msfsService.getStatus().panelConnected());

        return "home";
    }

    /** Minimal connection state for the landing page; separate from the panel's POST protocol. */
    @GetMapping("/flight-plan/connection")
    @ResponseBody
    public boolean panelConnected(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return msfsService.getStatus().panelConnected();
    }


    /**
     * Requests a fresh simulator snapshot through the MSFS panel connection.
     */
    @GetMapping("/flight-plan")
    public String flightPlan(Model model, HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("importSource", "MSFS SNAPSHOT");
        model.addAttribute("importDetail", "FlyByWire active plan");

        return showEditor(msfsService.getFlightPlan(), model);
    }

    /**
     * Keeps the static CSV workflow available independently of the simulator connection.
     */
    @GetMapping("/flight-plan/csv")
    public String csvFlightPlan(Model model) {

        model.addAttribute("importSource", "CSV SNAPSHOT");
        model.addAttribute("importDetail", CSV_FILE_PATH);

        return showEditor(csvService.read(CSV_FILE_PATH), model);
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

        editorModel.populate(form, model);

        return "flight-plan";
    }

    /**
     * Reopens the submitted route without calculating again or reloading the CSV.
     */
    @PostMapping("/flight-plan/edit")
    public String edit(@ModelAttribute("flightPlan") WebPlanDTO form, BindingResult errors, Model model) {

        form.validate(errors);
        editorModel.populate(form, model);

        return "flight-plan";
    }

    private String showEditor(List<Waypoint> waypoints, Model model) {

        WebPlanDTO form = WebPlanDTO.from(waypoints);

        model.addAttribute("flightPlan", form);
        editorModel.populate(form, model);

        return "flight-plan";
    }

}
