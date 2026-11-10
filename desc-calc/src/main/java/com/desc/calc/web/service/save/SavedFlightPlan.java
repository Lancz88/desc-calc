package com.desc.calc.web.service.save;

import com.desc.calc.web.service.WebPlanDTO;

/** Persisted editor inputs and snapshot metadata; independent of processor output. */
public record SavedFlightPlan(String id, String name, String savedAt, WebPlanDTO plan) {}
