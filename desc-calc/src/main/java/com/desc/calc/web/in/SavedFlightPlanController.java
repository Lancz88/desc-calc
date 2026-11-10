package com.desc.calc.web.in;

import com.desc.calc.web.service.FlightPlanEditorModel;
import com.desc.calc.web.service.save.FlightPlanFileStore;
import com.desc.calc.web.service.save.SavedFlightPlanService;
import com.desc.calc.web.service.WebPlanDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Local save/load workflow; never requests a simulator snapshot or runs the descent processor. */
@Controller
@RequestMapping("/flight-plan/saved")
public class SavedFlightPlanController {
    private final SavedFlightPlanService savedPlans;
    private final FlightPlanEditorModel editorModel;

    public SavedFlightPlanController(SavedFlightPlanService savedPlans, FlightPlanEditorModel editorModel) {
        this.savedPlans = savedPlans;
        this.editorModel = editorModel;
    }

    @GetMapping
    public String library(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("savedPlans", savedPlans.list());
        return "saved-plans";
    }

    @GetMapping("/{id}")
    public String load(@PathVariable String id, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var saved = savedPlans.load(id);
        model.addAttribute("flightPlan", saved.plan());
        model.addAttribute("planName", saved.name());
        model.addAttribute("importSource", "SAVED PLAN");
        model.addAttribute("importDetail", saved.name());
        editorModel.populate(saved.plan(), model);
        return "flight-plan";
    }

    @PostMapping
    public String save(@ModelAttribute("flightPlan") WebPlanDTO form, BindingResult errors,
                       @RequestParam(defaultValue = "") String planName, Model model, RedirectAttributes redirect,
                       HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        form.validate(errors);
        if (!errors.hasErrors()) {
            try {
                var saved = savedPlans.save(form, planName);
                redirect.addFlashAttribute("saveMessage", "Plan saved.");
                return "redirect:/flight-plan/saved/" + saved.id();
            } catch (IllegalArgumentException | FlightPlanFileStore.StorageException exception) {
                model.addAttribute("saveError", exception.getMessage());
            }
        }
        model.addAttribute("planName", planName);
        editorModel.populate(form, model);
        return "flight-plan";
    }

    @ExceptionHandler({FlightPlanFileStore.StorageException.class, SavedFlightPlanService.PlanNotFoundException.class})
    public String savedPlanError(RuntimeException exception, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(exception instanceof SavedFlightPlanService.PlanNotFoundException ? 404 : 503);
        model.addAttribute("saveError", exception.getMessage());
        // Keep failures readable even when the archive itself cannot be listed.
        model.addAttribute("savedPlans", List.of());
        return "saved-plans";
    }
}
