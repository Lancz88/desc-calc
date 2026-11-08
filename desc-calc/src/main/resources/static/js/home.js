/* Landing-page connection state only; flight plan imports remain normal form navigation. */
(function () {
    "use strict";

    const form = document.getElementById("msfs-import");
    const button = document.getElementById("import-flight-plan");
    const label = document.getElementById("import-label");
    const status = document.getElementById("connection-status");
    let importing = false;
    let connected = !button.disabled;

    const errorNotice = document.getElementById("import-error");
    const dismissError = document.getElementById("dismiss-import-error");

    if (errorNotice && dismissError) {
        dismissError.addEventListener("click", function () {
            errorNotice.hidden = true;
        });
    }

    function showConnection(state, message) {
        connected = state === "connected";
        button.disabled = importing || !connected;
        status.dataset.state = state;
        status.textContent = message;
    }

    async function refreshConnection() {
        const controller = new AbortController();
        const timeout = setTimeout(function () { controller.abort(); }, 3000);

        try {
            const response = await fetch(form.dataset.statusUrl, {
                cache: "no-store",
                signal: controller.signal
            });
            if (!response.ok) throw new Error("Connection check failed");

            const active = await response.json();
            if (typeof active !== "boolean") throw new Error("Unexpected connection state");

            if (!importing) {
                showConnection(active ? "connected" : "disconnected", active
                    ? "Panel connected. Once your route is ready in the MCDU, import your flight plan."
                    : "Open the GlideLink in-game panel in MSFS to connect.");
            }
        } catch (error) {
            if (!importing) showConnection("unavailable", "Server unavailable.");
        } finally {
            clearTimeout(timeout);
            setTimeout(refreshConnection, 2000);
        }
    }

    form.addEventListener("submit", function (event) {
        if (importing || !connected) {
            event.preventDefault();
            return;
        }

        importing = true;
        button.disabled = true;
        label.textContent = "Requesting flight plan…";
        status.textContent = "Reading the active route from MSFS. This can take a few seconds.";
    });

    window.addEventListener("pageshow", function (event) {
        if (event.persisted) {
            // Restored pages can contain an old connection state or a disabled loading button.
            importing = false;
            connected = false;
            button.disabled = true;
            label.textContent = "Import from MSFS";
            status.textContent = "Checking panel connection…";
        }
    });

    refreshConnection();
})();
