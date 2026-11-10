package com.desc.calc.web.service;

import com.desc.calc.web.service.save.FlightPlanFileStore;
import com.desc.calc.web.service.save.SavedFlightPlanService;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import java.util.List;

/** Shared editor presentation for imported, edited and restored plans. */
@Component
public class FlightPlanEditorModel {
    private final SavedFlightPlanService savedPlans;
    public FlightPlanEditorModel(SavedFlightPlanService savedPlans) { this.savedPlans = savedPlans; }

    public void populate(WebPlanDTO form, Model model) {
        List<WebPlanDTO.Row> rows = form.getWaypoints() == null ? List.of() : form.getWaypoints();
        model.addAttribute("totalDistance", rows.stream().filter(row -> row != null && row.getDistanceToNext() != null)
                .mapToLong(WebPlanDTO.Row::getDistanceToNext).sum());
        model.addAttribute("constrainedWaypoints", rows.stream().filter(row -> row != null
                && (row.getAltHold() != null || row.getAltAbove() != null || row.getAltBelow() != null || row.getSpeed() != null)).count());
        try {
            model.addAttribute("savedPlans", savedPlans.list());
        } catch (FlightPlanFileStore.StorageException exception) {
            model.addAttribute("savedPlans", List.of());
            model.addAttribute("saveError", exception.getMessage());
        }
    }
}
