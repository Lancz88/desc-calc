package com.desc.calc.web.service.save;

import com.desc.calc.web.service.WebPlanDTO;

import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Snapshot semantics: each save adds a new plan; keep the ten most recently saved snapshots. */
@Service
public class SavedFlightPlanService {
    private static final int LIMIT = 10;
    private final FlightPlanFileStore store;

    public SavedFlightPlanService(FlightPlanFileStore store) { this.store = store; }

    public synchronized List<Summary> list() {
        return readValidated().stream().map(plan -> new Summary(plan.id(), plan.name(), plan.savedAt(),
                plan.plan().getWaypoints().size(), plan.plan().getCruiseAltitude())).toList();
    }

    public synchronized SavedFlightPlan save(WebPlanDTO form, String name) {
        validate(form);
        if (name == null || name.isBlank()) {
            var rows = form.getWaypoints();
            name = rows.get(rows.size() - 1).getName().trim() + " to " + rows.get(0).getName().trim();
        }
        name = name.strip();
        if (name.length() > 80) throw new IllegalArgumentException("Use a plan name of at most 80 characters.");
        List<SavedFlightPlan> plans = new ArrayList<>(readValidated());
        SavedFlightPlan saved = new SavedFlightPlan(UUID.randomUUID().toString(), name, Instant.now().toString(), form);
        plans.add(0, saved);
        store.write(List.copyOf(plans.subList(0, Math.min(LIMIT, plans.size()))));
        return saved;
    }

    public synchronized SavedFlightPlan load(String id) {
        return readValidated().stream().filter(plan -> plan.id().equals(id)).findFirst()
                .orElseThrow(() -> new PlanNotFoundException("This saved plan is no longer available."));
    }

    private List<SavedFlightPlan> readValidated() {
        List<SavedFlightPlan> plans = store.read();
        try {
            var ids = new java.util.HashSet<String>();
            for (SavedFlightPlan saved : plans) {
                UUID.fromString(saved.id());
                Instant.parse(saved.savedAt());
                if (!ids.add(saved.id()) || saved.name() == null || saved.name().isBlank()
                        || saved.name().length() > 80) throw new IllegalArgumentException();
                validate(saved.plan());
            }
            return plans;
        } catch (RuntimeException exception) {
            throw new FlightPlanFileStore.StorageException("Couldn't read saved plans. Your saved file has been kept.", exception);
        }
    }

    private void validate(WebPlanDTO form) {
        if (form == null) throw new IllegalArgumentException("Missing flight plan.");
        var errors = new BeanPropertyBindingResult(form, "flightPlan");
        form.validate(errors);
        if (errors.hasErrors()) throw new IllegalArgumentException(errors.getAllErrors().get(0).getDefaultMessage());
    }

    public record Summary(String id, String name, String savedAt, int waypointCount, int cruiseAltitude) {}
    public static class PlanNotFoundException extends RuntimeException {
        public PlanNotFoundException(String message) { super(message); }
    }
}
