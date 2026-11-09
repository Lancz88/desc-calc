/* Live itinerary estimates use the page's route, keeping different tabs independent. */
(function () {
    "use strict";

    const panel = document.getElementById("live-telemetry");
    const connection = document.getElementById("telemetry-connection");
    const status = document.getElementById("telemetry-status");
    const speed = document.getElementById("live-ground-speed");
    const waypoint = document.getElementById("live-next-waypoint");
    const distance = document.getElementById("live-waypoint-distance");
    const itineraryRows = document.querySelectorAll(".descent-table tbody tr[data-waypoint]");
    const empty = "\u2014";
    const localTime = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });

    function milestone(id) {
        const element = document.getElementById(id);
        return { waypoint: element.dataset.waypoint, offsetNm: Number(element.dataset.offset) };
    }

    // Hidden inputs retain the exact destination-first editor route used for this calculation.
    const fields = new FormData(document.getElementById("edit-plan"));
    const route = [];
    for (let index = 0; fields.has(`waypoints[${index}].name`); index++) {
        route.push({ name: fields.get(`waypoints[${index}].name`).trim(),
            distanceToNext: Number(fields.get(`waypoints[${index}].distanceToNext`)) });
    }
    const context = JSON.stringify({ route, topOfDescent: milestone("td-milestone"),
        startPreparation: milestone("prep-milestone") });

    function validNumber(value) {
        return typeof value === "number" && Number.isFinite(value) && value >= 0;
    }

    function formatDistance(value) {
        return validNumber(value) ? value.toFixed(value < 20 ? 1 : 0) : empty;
    }

    function duration(seconds) {
        const total = Math.max(0, Math.round(seconds));
        const hours = Math.floor(total / 3600);
        const minutes = Math.floor(total % 3600 / 60);
        const rest = String(total % 60).padStart(2, "0");
        return hours ? `${hours}h ${String(minutes).padStart(2, "0")}m ${rest}s` : `${minutes}m ${rest}s`;
    }

    function renderCountdown(id, target) {
        const label = document.getElementById(id);
        if (target && Number.isFinite(target.distanceNm) && target.distanceNm <= 0) {
            label.textContent = target.distanceNm === 0 ? "Reached" : `${formatDistance(-target.distanceNm)} NM past`;
            return;
        }
        const remaining = target ? formatDistance(target.distanceNm) : empty;
        const seconds = target && validNumber(target.secondsRemaining) ? target.secondsRemaining : null;
        const elapsed = seconds === null ? empty : duration(seconds);
        const eta = seconds === null ? empty : localTime.format(new Date(Date.now() + seconds * 1000));
        label.textContent = `${remaining} NM away \u00b7 in ${elapsed} \u00b7 ETA ${eta} local`;
    }

    function render(data, unavailable) {
        const readings = data && data.telemetry;
        const active = readings && readings.panelConnected === true;
        connection.dataset.state = active ? "connected" : "disconnected";
        status.textContent = unavailable ? "Server unavailable." : active ? "GlideLink connected" : "GlideLink disconnected";
        speed.textContent = active && validNumber(readings.groundSpeedKnots) ? readings.groundSpeedKnots.toFixed(0) : empty;
        waypoint.textContent = active && typeof readings.nextWaypoint === "string" && readings.nextWaypoint.trim()
            ? readings.nextWaypoint : empty;
        distance.textContent = active ? formatDistance(readings.distanceToNextWaypointNm) : empty;
        itineraryRows.forEach(row => {
            const current = !!(active && typeof readings.nextWaypoint === "string"
                && readings.nextWaypoint.trim() && row.dataset.waypoint === readings.nextWaypoint);
            row.classList.toggle("current-waypoint", current);
            if (current) row.setAttribute("aria-current", "step");
            else row.removeAttribute("aria-current");
        });
        renderCountdown("td-countdown", active ? data.topOfDescent : null);
        renderCountdown("prep-countdown", active ? data.startPreparation : null);
    }

    async function refresh() {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 2000);

        try {
            const response = await fetch(panel.dataset.url, { method: "POST", cache: "no-store",
                headers: { "Content-Type": "application/json" }, body: context, signal: controller.signal });
            if (!response.ok) throw new Error("Telemetry unavailable");
            const data = await response.json();
            if (!data || !data.telemetry || typeof data.telemetry.panelConnected !== "boolean") throw new Error("Invalid telemetry");
            render(data, false);
        } catch (error) {
            render(null, true);
        } finally {
            clearTimeout(timeout);
            setTimeout(refresh, 500);
        }
    }

    window.addEventListener("pageshow", () => render(null, false));
    refresh();
})();
