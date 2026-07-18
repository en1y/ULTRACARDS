(() => {
    const api = "/api/admin/v1";
    const state = {};
    const status = document.querySelector("#admin-status");

    const setStatus = (message, error = false) => {
        status.textContent = message;
        status.classList.toggle("is-error", error);
    };

    const request = async (path, options = {}) => {
        const response = await fetch(`${api}${path}`, {
            headers: { "Content-Type": "application/json", ...(options.headers || {}) },
            ...options
        });
        if (response.ok) return response.status === 204 ? null : response.json();
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || `Request failed (${response.status})`);
    };

    const element = (tag, text, className) => {
        const node = document.createElement(tag);
        if (text != null) node.textContent = text;
        if (className) node.className = className;
        return node;
    };

    const formatTime = value => value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
    const query = values => {
        const params = new URLSearchParams();
        Object.entries(values).forEach(([key, value]) => { if (value !== "" && value != null) params.set(key, value); });
        return params.toString() ? `?${params}` : "";
    };
    const results = name => document.querySelector(`[data-results="${name}"]`);
    const clear = name => results(name).replaceChildren();
    const empty = (name, message) => results(name).append(element("p", message, "admin-empty"));
    const button = (label, action, value, accent = false) => {
        const node = element("button", label, `btn${accent ? " btn-accent" : ""}`);
        node.type = "button";
        node.dataset.action = action;
        node.dataset.value = value;
        return node;
    };
    const row = (name, title, meta, actions = []) => {
        const node = element("article", null, "admin-row");
        const details = element("div");
        details.append(element("h3", title));
        details.append(element("p", meta, "admin-meta"));
        node.append(details);
        if (actions.length) {
            const controls = element("div", null, "admin-actions");
            controls.append(...actions);
            node.append(controls);
        }
        results(name).append(node);
    };

    const renderPaged = (name, page) => {
        const controls = element("div", null, "admin-pagination");
        const previous = button("Previous", "page", `${name}:${Math.max(0, page.page - 1)}`);
        previous.disabled = page.page === 0;
        const next = button("Next", "page", `${name}:${page.page + 1}`);
        next.disabled = page.page + 1 >= page.totalPages;
        controls.append(previous, element("span", `${page.totalElements} total`), next);
        results(name).append(controls);
    };

    const loadOverview = async () => {
        const [overview, system] = await Promise.all([request("/reports/overview"), request("/system/status")]);
        const container = document.querySelector("#admin-overview");
        container.replaceChildren();
        [["Users", overview.users], ["Online", overview.onlineUsers], ["Live lobbies", system.activeLobbies], ["Live games", system.activeGames], ["Valid sessions", overview.validSessions], ["Database", system.databaseAvailable ? "Available" : "Unavailable"]]
            .forEach(([label, value]) => {
                const metric = element("article", null, "admin-metric");
                metric.append(element("span", label), element("strong", String(value)));
                container.append(metric);
            });
    };

    const loadUsers = async (page = 0) => {
        const form = document.querySelector('[data-filters="users"]');
        const values = Object.fromEntries(new FormData(form));
        const data = await request(`/reports/users${query({ page, size: 20, ...values })}`);
        clear("users");
        if (!data.items.length) return empty("users", "No users match these filters.");
        data.items.forEach(user => row("users", `${user.username} · #${user.id}`, `${user.email} · ${user.status} · ${[...user.roles].join(", ")}`, [
            button("Preview stats rebuild", "preview-stats", String(user.id)),
            button(user.enabled ? "Disable" : "Enable", "toggle-user", String(user.id), !user.enabled)
        ]));
        renderPaged("users", data);
    };

    const loadLobbies = async () => {
        const data = await request("/lobbies");
        clear("lobbies");
        if (!data.length) return empty("lobbies", "There are no live lobbies.");
        data.forEach(item => {
            const lobby = item.lobby;
            const game = lobby.gameType?.name || lobby.gameType?.game || "Game";
            row("lobbies", lobby.name, `${game} · ${item.state} · ${lobby.players?.length || 0}/${lobby.maxPlayers} players`, [button("Close lobby", "close-lobby", lobby.id, true)]);
        });
    };

    const loadGames = async (page = 0) => {
        const values = Object.fromEntries(new FormData(document.querySelector('[data-filters="games"]')));
        const data = await request(`/reports/games${query({ page, size: 20, ...values })}`);
        clear("games");
        if (!data.items.length) return empty("games", "No recorded games match these filters.");
        data.items.forEach(game => row("games", game.name || `${game.gameType} game`, `${game.gameType} · Owner #${game.ownerUserId ?? "—"} · ${game.endedAt ? `Ended ${formatTime(game.endedAt)}` : "Incomplete"}`));
        renderPaged("games", data);
    };

    const loadSessions = async (page = 0) => {
        const values = Object.fromEntries(new FormData(document.querySelector('[data-filters="sessions"]')));
        const data = await request(`/reports/sessions${query({ page, size: 20, ...values })}`);
        clear("sessions");
        if (!data.items.length) return empty("sessions", "No sessions match these filters.");
        data.items.forEach(session => row("sessions", `User #${session.userId} · ${session.active ? "Active" : "Expired"}`, `${session.clientType || "Unknown device"} · ${session.os || "Unknown OS"} · Seen ${formatTime(session.lastSeenAt)}`));
        renderPaged("sessions", data);
    };

    const loadAvailability = async () => {
        const data = await request("/games");
        clear("availability");
        if (!data.length) return empty("availability", "No game availability rules are configured.");
        data.forEach(item => row("availability", item.mode ? `${item.game} · ${item.mode}` : item.game, item.enabled ? "Available to players" : "Unavailable to players", [button(item.enabled ? "Disable" : "Enable", "toggle-game", `${item.game}|${item.mode || ""}`, !item.enabled)]));
    };

    const loadAudit = async (page = 0) => {
        const data = await request(`/audit${query({ page, size: 20 })}`);
        clear("audit");
        if (!data.items.length) return empty("audit", "No administrative actions have been recorded.");
        data.items.forEach(event => row("audit", `${event.action} · ${event.outcome}`, `${event.targetType} ${event.targetId} · ${event.reason} · ${formatTime(event.occurredAt)}`));
        renderPaged("audit", data);
    };

    const loaders = { users: loadUsers, lobbies: loadLobbies, games: loadGames, sessions: loadSessions, availability: loadAvailability, audit: loadAudit };
    state.loaders = loaders;
    const refresh = async (section) => {
        setStatus("Refreshing…");
        try {
            await loadOverview();
            await loaders[section]();
            setStatus(`Updated ${new Date().toLocaleTimeString()}.`);
        } catch (error) { setStatus(error.message, true); }
    };

    document.querySelector(".admin-tabs").addEventListener("click", event => {
        const tab = event.target.closest("button[data-panel]");
        if (!tab) return;
        document.querySelectorAll(".admin-tabs button").forEach(button => button.classList.toggle("is-active", button === tab));
        document.querySelectorAll(".admin-panel").forEach(panel => { panel.hidden = panel.id !== `admin-${tab.dataset.panel}`; });
        refresh(tab.dataset.panel);
    });
    document.querySelectorAll(".admin-filters").forEach(form => form.addEventListener("submit", event => { event.preventDefault(); refresh(form.dataset.filters); }));
    document.querySelector('[data-action="refresh"]').addEventListener("click", () => {
        const current = document.querySelector(".admin-tabs .is-active").dataset.panel;
        refresh(current);
    });
    document.addEventListener("click", async event => {
        const control = event.target.closest("button[data-action]");
        if (!control || control.dataset.action === "refresh") return;
        try {
            if (control.dataset.action === "page") {
                const [name, page] = control.dataset.value.split(":");
                await state.loaders[name](Number(page));
                return;
            }
            const reason = prompt("Reason for this administrative change:");
            if (!reason?.trim()) return;
            if (control.dataset.action === "toggle-user") {
                const action = control.textContent.toLowerCase();
                if (!confirm(`${action[0].toUpperCase()}${action.slice(1)} this user?`)) return;
                const user = await request(`/users/${control.dataset.value}`);
                await request(`/users/${user.id}`, { method: "PATCH", body: JSON.stringify({ enabled: !user.enabled, reason, dryRun: false }) });
                await refresh("users");
            }
            if (control.dataset.action === "preview-stats") {
                const preview = await request(`/stats/users/${control.dataset.value}/rebuild${query({ reason, dryRun: true })}`, { method: "POST" });
                setStatus(preview.warning || "Stats rebuild preview is ready; no data was changed.");
            }
            if (control.dataset.action === "close-lobby") {
                if (!confirm("Close this lobby? This cannot be undone.")) return;
                await request(`/lobbies/${control.dataset.value}${query({ reason })}`, { method: "DELETE" });
                await refresh("lobbies");
            }
            if (control.dataset.action === "toggle-game") {
                const [game, mode] = control.dataset.value.split("|");
                const enabled = control.textContent === "Enable";
                if (!confirm(`${enabled ? "Enable" : "Disable"} ${game}${mode ? ` ${mode}` : ""}?`)) return;
                await request(`/games/${encodeURIComponent(game)}`, { method: "PATCH", body: JSON.stringify({ mode: mode || null, enabled, reason }) });
                await refresh("availability");
            }
        } catch (error) { setStatus(error.message, true); }
    });
    document.querySelector("#admin-notification-form").addEventListener("submit", async event => {
        event.preventDefault();
        const values = Object.fromEntries(new FormData(event.currentTarget));
        const recipient = values.userId ? `/users/${values.userId}` : "/all";
        if (!confirm(values.userId ? `Send this notification to user #${values.userId}?` : "Send this notification to every user?")) return;
        try {
            await request(`/notifications${recipient}`, { method: "POST", body: JSON.stringify({ message: values.message, reason: values.reason }) });
            event.currentTarget.reset();
            setStatus("Notification sent.");
        } catch (error) { setStatus(error.message, true); }
    });
    refresh("users");
})();
