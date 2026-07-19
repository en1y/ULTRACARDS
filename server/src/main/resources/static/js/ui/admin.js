(() => {
    const api = "/api/admin/v1";
    const page = document.body.dataset.adminPage;
    const params = new URLSearchParams(window.location.search);
    const state = {
        currentUser: null,
        currentLobby: null,
        notifications: new Map(),
        stats: null,
        userDetailId: page === "users" ? params.get("id") : null,
        dbTable: page === "database" ? params.get("table") : null,
        dbFilters: {}
    };
    if (page === "database") for (const [key, value] of params) if (key !== "table") state.dbFilters[key] = value;
    const modes = {
        briskula: ["TWO_PLAYERS", "TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH", "THREE_PLAYERS", "FOUR_PLAYERS_NO_TEAMS", "FOUR_PLAYERS_WITH_TEAMS"],
        treseta: ["TWO_PLAYERS", "THREE_PLAYERS", "FOUR_PLAYERS_WITH_TEAMS", "FOUR_PLAYERS_NO_TEAMS"]
    };
    const status = document.querySelector("#admin-status");
    const tr = (key, fallback, ...args) => typeof window.t === "function" && (window.__I18N__ || {})[key] ? window.t(key, ...args) : fallback;
    const setStatus = (message, error = false) => { status.textContent = message; status.classList.toggle("is-error", error); };
    const request = async (path, options = {}) => {
        const response = await fetch(`${api}${path}`, { headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options });
        const text = await response.text();
        if (response.ok) return text ? JSON.parse(text) : null;
        let message = `Request failed (${response.status})`;
        try { message = JSON.parse(text).message || message; } catch { /* non-JSON error body */ }
        throw new Error(message);
    };
    const element = (tag, text, className) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (className) node.className = className; return node; };
    const link = (href, text, className) => { const node = element("a", text, className); node.href = href; return node; };
    const userHref = id => `/admin/users?id=${encodeURIComponent(id)}`;
    const userLink = (id, label) => link(userHref(id), label ?? `${tr("admin.common.user", "User")} #${id}`, "admin-link");
    const tokenHref = id => `/admin/database?table=tokens&id=${encodeURIComponent(id)}`;
    const playerLink = name => { const match = /\(#(\d+)\)\s*$/.exec(name); return link(match ? userHref(match[1]) : `/admin/users?query=${encodeURIComponent(name)}&exact=true`, name, "admin-link"); };
    const playerLinks = names => names.flatMap((name, index) => index ? [", ", playerLink(name)] : [playerLink(name)]);
    const chip = (text, className) => element("span", text, `admin-chip${className ? ` ${className}` : ""}`);
    const statusClass = value => ({ ACTIVE: "is-good", DISABLED: "is-warn", DELETED: "is-bad" })[value] || "";
    const formatTime = value => value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
    const formDateTime = value => value ? new Date(value).toISOString().slice(0, 16) : "";
    const shortId = value => value ? `${String(value).slice(0, 8)}…` : "—";
    const query = values => { const search = new URLSearchParams(); Object.entries(values).forEach(([key, value]) => { if (value !== "" && value != null) search.set(key, value); }); return search.toString() ? `?${search}` : ""; };
    const results = name => document.querySelector(`[data-results="${name}"]`);
    const clear = name => results(name).replaceChildren();
    const empty = (name, message) => results(name).append(element("p", message, "admin-empty"));
    const button = (label, action, value, accent = false) => { const node = element("button", label, `btn${accent ? " btn-accent" : ""}`); node.type = "button"; node.dataset.action = action; node.dataset.value = value; return node; };
    const selectBox = (scope, id) => { const box = document.createElement("input"); box.type = "checkbox"; box.className = "admin-select"; box.dataset.scope = scope; box.dataset.id = id; box.setAttribute("aria-label", "Select item"); return box; };
    const selectAllBox = scope => { const box = document.createElement("input"); box.type = "checkbox"; box.setAttribute("aria-label", "Select every item on this page"); box.addEventListener("change", () => document.querySelectorAll(`input.admin-select[data-scope="${scope}"]`).forEach(item => { item.checked = box.checked; })); return box; };
    const selectedIds = scope => [...document.querySelectorAll(`input.admin-select[data-scope="${scope}"]:checked`)].map(box => box.dataset.id);
    const bulkBar = (label, action, scope) => {
        const bar = element("div", null, "admin-bulk-bar"); bar.hidden = true; bar.dataset.bulkScope = scope;
        const remove = button(label, action, scope); remove.classList.add("btn-danger"); remove.dataset.bulkLabel = label;
        bar.append(remove, element("span", tr("admin.bulk.hint", "Select rows, then enter one audited reason for the batch."), "admin-meta"));
        return bar;
    };
    const updateBulkBars = () => document.querySelectorAll("[data-bulk-scope]").forEach(bar => {
        const count = selectedIds(bar.dataset.bulkScope).length;
        bar.hidden = count === 0;
        const remove = bar.querySelector("button");
        remove.textContent = count ? `${remove.dataset.bulkLabel} (${count})` : remove.dataset.bulkLabel;
    });
    document.addEventListener("change", event => { if (event.target.matches("input[type=\"checkbox\"]")) updateBulkBars(); });
    const content = (target, value) => { (Array.isArray(value) ? value : [value]).forEach(part => { if (part == null) return; target.append(part instanceof Node ? part : document.createTextNode(String(part))); }); };
    const row = (name, title, meta, actions = []) => {
        const node = element("article", null, "admin-row");
        const details = element("div");
        const heading = element("h3"); content(heading, title);
        const metaLine = element("p", null, "admin-meta"); content(metaLine, meta);
        details.append(heading, metaLine); node.append(details);
        if (actions.length) { const controls = element("div", null, "admin-actions"); controls.append(...actions); node.append(controls); }
        results(name).append(node);
    };
    const table = (columns, rows, sortable = false) => {
        const wrap = element("div", null, "admin-table-wrap");
        const node = element("table", null, "admin-table");
        const head = element("thead"); const headRow = element("tr");
        const body = element("tbody");
        const actionsLabel = tr("admin.common.actions", "Actions");
        const updateSort = (column, direction) => {
            [...body.rows].sort((left, right) => new Intl.Collator(undefined, { numeric: true, sensitivity: "base" }).compare(left.cells[column].textContent, right.cells[column].textContent) * direction).forEach(row => body.append(row));
            headRow.querySelectorAll("button").forEach(button => { button.dataset.direction = ""; button.setAttribute("aria-sort", "none"); });
        };
        columns.forEach((column, index) => {
            const cell = element("th");
            if (sortable && typeof column === "string" && column !== actionsLabel && column !== "") {
                const control = element("button", column, "admin-sort"); control.type = "button"; control.dataset.direction = "";
                control.setAttribute("aria-sort", "none"); control.addEventListener("click", () => {
                    const direction = control.dataset.direction === "asc" ? -1 : 1;
                    updateSort(index, direction); control.dataset.direction = direction === 1 ? "asc" : "desc";
                    control.setAttribute("aria-sort", direction === 1 ? "ascending" : "descending");
                });
                cell.append(control);
            } else content(cell, column);
            headRow.append(cell);
        });
        head.append(headRow);
        rows.forEach(cells => {
            const line = element("tr");
            cells.forEach(cell => { const item = element("td"); if (cell == null || cell === "") item.textContent = "—"; else content(item, cell); line.append(item); });
            body.append(line);
        });
        node.append(head, body); wrap.append(node); return wrap;
    };
    const goToPage = (name, target) => (name === "database-browser" ? loadDatabaseTable(target) : loaders[name](target)).catch(error => setStatus(error.message, true));
    const renderPaged = (name, data) => {
        const totalPages = Math.max(1, data.totalPages);
        const controls = element("div", null, "admin-pagination");
        controls.append(element("span", tr("admin.page.of", `Page ${data.page + 1} of ${totalPages} · ${data.totalElements} total`, data.page + 1, totalPages, data.totalElements)));
        const buttons = element("div", null, "admin-page-buttons");
        const pageButton = (label, target, disabled, title) => { const node = button(label, "page", `${name}:${target}`); node.disabled = disabled; node.title = title; return node; };
        const atStart = data.page === 0;
        const atEnd = data.page + 1 >= totalPages;
        buttons.append(
            pageButton("«", 0, atStart, tr("admin.page.first", "First page")),
            pageButton("‹", Math.max(0, data.page - 1), atStart, tr("admin.page.previous", "Previous page")),
            pageButton("›", data.page + 1, atEnd, tr("admin.page.next", "Next page")),
            pageButton("»", totalPages - 1, atEnd, tr("admin.page.last", "Last page")));
        controls.append(buttons);
        if (totalPages > 1) {
            const jump = element("form", null, "admin-page-jump");
            const input = document.createElement("input");
            input.type = "number"; input.min = "1"; input.max = String(totalPages); input.required = true;
            input.placeholder = `1–${totalPages}`; input.setAttribute("aria-label", tr("admin.page.jump", "Jump to page"));
            const go = element("button", tr("admin.page.go", "Go"), "btn"); go.type = "submit";
            jump.append(input, go);
            jump.addEventListener("submit", event => { event.preventDefault(); goToPage(name, Math.min(totalPages, Math.max(1, Number(input.value))) - 1); });
            controls.append(jump);
        }
        results(name).append(controls);
    };
    const formValues = form => Object.fromEntries(new FormData(form));
    const askAction = ({ title, description, confirmLabel = tr("admin.common.confirmChange", "Confirm"), danger = false, fields = [] }) => new Promise(resolve => {
        const dialog = document.querySelector("#admin-action-dialog");
        const form = document.querySelector("#admin-action-form");
        const container = document.querySelector("#admin-action-fields");
        document.querySelector("#admin-action-title").textContent = title;
        document.querySelector("#admin-action-description").textContent = description;
        const confirmButton = document.querySelector("#admin-action-confirm"); confirmButton.textContent = confirmLabel; confirmButton.classList.toggle("btn-danger", danger);
        container.replaceChildren(...fields.map(field => {
            const label = element("label", field.label);
            let input;
            if (field.options) {
                input = element("select"); input.name = field.name;
                input.append(...field.options.map(option => { const node = element("option", option.label); node.value = option.value; return node; }));
            } else {
                input = document.createElement("input"); input.name = field.name; input.type = field.type || "text";
            }
            if (field.value != null) input.value = field.value;
            if (field.placeholder) input.placeholder = field.placeholder;
            if (field.min != null) input.min = field.min;
            if (field.maxLength != null) input.maxLength = field.maxLength;
            input.required = Boolean(field.required); label.append(input); return label;
        }));
        const finish = result => { form.removeEventListener("submit", submit); dialog.removeEventListener("close", close); resolve(result); };
        const submit = event => { const values = formValues(form); finish(event.submitter?.value === "confirm" ? values : null); };
        const close = () => finish(null);
        form.addEventListener("submit", submit); dialog.addEventListener("close", close, { once: true }); dialog.showModal();
    });
    const confirmAction = (title, description, danger = false) => askAction({ title, description, confirmLabel: danger ? tr("admin.common.confirmChange", "Confirm change") : tr("admin.common.continue", "Continue"), danger });
    const reasonField = () => ({ name: "reason", label: tr("admin.common.reason", "Reason"), required: true, maxLength: 250 });
    const populateModes = () => {
        const form = document.querySelector("#admin-stats-edit");
        if (!form) return;
        const mode = form.elements.mode;
        const selected = mode.value;
        mode.replaceChildren(...modes[form.elements.gameType.value].map(value => element("option", value)));
        if (modes[form.elements.gameType.value].includes(selected)) mode.value = selected;
    };

    const loadOverview = async () => {
        const [overview, system] = await Promise.all([request("/reports/overview"), request("/system/status")]);
        const container = document.querySelector("#admin-overview"); container.replaceChildren();
        const metric = (label, value, href) => {
            const node = href ? link(href, null, "admin-metric admin-metric-link") : element("article", null, "admin-metric");
            node.append(element("span", label), element("strong", String(value))); container.append(node);
        };
        metric(tr("admin.overview.users", "Users"), overview.users, "/admin/users");
        metric(tr("admin.overview.online", "Online now"), overview.onlineUsers, "/admin/sessions?valid=true");
        metric(tr("admin.overview.validSessions", "Valid sessions"), overview.validSessions, "/admin/sessions");
        metric(tr("admin.overview.liveLobbies", "Live lobbies"), system.activeLobbies, "/admin/lobbies");
        metric(tr("admin.overview.liveGames", "Live games"), system.activeGames, "/admin/games");
        metric(tr("admin.overview.database", "Database"), system.databaseAvailable ? tr("admin.overview.available", "Available") : tr("admin.overview.unavailable", "Unavailable"), "/admin/database");
        const breakdown = document.querySelector("#admin-overview-breakdown"); breakdown.replaceChildren();
        const group = (title, entries, href) => {
            const card = element("article", null, "admin-breakdown");
            card.append(element("h3", title));
            const list = element("div", null, "admin-chips");
            Object.entries(entries || {}).forEach(([key, count]) => list.append(link(href(key), `${key} · ${count}`, "admin-chip admin-chip-link")));
            card.append(list); breakdown.append(card);
        };
        group(tr("admin.overview.byStatus", "Users by status"), overview.usersByStatus, key => `/admin/users?status=${key}`);
        group(tr("admin.overview.byRole", "Users by role"), overview.usersByRole, key => `/admin/users?role=${key}`);
        group(tr("admin.overview.completed", "Completed games"), overview.completedGames, key => `/admin/games?gameType=${key}&completed=true`);
        group(tr("admin.overview.incomplete", "Incomplete games"), overview.incompleteGames, key => `/admin/games?gameType=${key}&completed=false`);
    };

    const userCard = user => {
        const card = link(userHref(user.id), null, "admin-row admin-row-link");
        const details = element("div");
        details.append(element("h3", `${user.username} · #${user.id}`));
        details.append(element("p", `${user.email} · ${tr("admin.common.created", "Created")} ${formatTime(user.createdAt)} · ${tr("admin.col.lastLogin", "Last login")} ${formatTime(user.lastLoginAt)}`, "admin-meta"));
        const chips = element("div", null, "admin-chips");
        chips.append(chip(user.status, statusClass(user.status)), ...[...user.roles].filter(role => role !== "USER").map(role => chip(role)));
        details.append(chips);
        card.append(details, element("span", "→", "admin-row-arrow"));
        return card;
    };
    const loadUsers = async (currentPage = 0) => {
        const values = formValues(document.querySelector('[data-filters="users"]'));
        const sort = values.sort === "userCreatedAtOldest" ? "userCreatedAt" : values.sort;
        const direction = values.sort === "userCreatedAtOldest" ? "asc" : values.direction;
        const data = await request(`/reports/users${query({ page: currentPage, size: 20, ...values, sort, direction })}`); clear("users");
        if (!data.items.length) return empty("users", tr("admin.user.empty", "No users match this exact ID, username, or email search."));
        data.items.forEach(user => results("users").append(userCard(user)));
        renderPaged("users", data);
    };

    const sectionCard = (title, href, linkLabel) => {
        const card = element("section", null, "admin-user-card");
        const head = element("header", null, "admin-user-card-head");
        head.append(element("h3", title));
        if (href) head.append(link(href, linkLabel, "admin-more"));
        const body = element("div", null, "admin-user-card-body");
        card.append(head, body);
        return { card, body };
    };
    const loadUserDetail = async id => {
        const list = document.querySelector("#admin-user-list");
        const detail = document.querySelector("#admin-user-detail");
        list.hidden = true; detail.hidden = false;
        detail.replaceChildren(element("p", tr("admin.user.loading", "Loading user…"), "admin-empty"));
        let user;
        try {
            user = await request(`/users/${id}`);
        } catch (error) {
            detail.replaceChildren(link("/admin/users", tr("admin.user.back", "← All users"), "admin-back"), element("p", error.message, "admin-empty"));
            throw error;
        }
        const [stats, sessions, audit, notifications] = await Promise.all([
            request(`/stats/users/${id}`).catch(() => null),
            request(`/reports/sessions${query({ userId: id, size: 5 })}`).catch(() => null),
            request(`/audit${query({ targetType: "USER", targetId: id, size: 5 })}`).catch(() => null),
            request(`/database/notifications${query({ userId: id, size: 5 })}`).catch(() => null)
        ]);
        state.currentUser = user;
        (notifications?.items || []).forEach(notification => state.notifications.set(notification.id, notification));
        detail.replaceChildren();
        detail.append(link("/admin/users", tr("admin.user.back", "← All users"), "admin-back"));

        const head = element("header", null, "admin-user-head");
        const identity = element("div");
        identity.append(element("h2", user.username));
        const chips = element("div", null, "admin-chips");
        chips.append(chip(user.status, statusClass(user.status)), ...[...user.roles].map(role => chip(role)));
        identity.append(chips, element("p", `#${user.id} · ${user.email}`, "admin-meta"));
        const actions = element("div", null, "admin-actions");
        actions.append(button(tr("admin.user.editAccount", "Edit account"), "edit-user", String(user.id), true), button(tr("admin.user.revokeSessions", "Revoke sessions"), "revoke-user-sessions", String(user.id)),
            link(`/admin/stats?userId=${user.id}`, tr("admin.user.editStats", "Edit stats"), "btn"), link(`/admin/notifications?userId=${user.id}`, tr("admin.user.sendNotification", "Send notification"), "btn"));
        head.append(identity, actions);
        detail.append(head);

        const facts = element("dl", null, "admin-user-facts");
        [[tr("admin.common.created", "Created"), formatTime(user.createdAt)], [tr("admin.user.updatedAt", "Updated"), formatTime(user.updatedAt)], [tr("admin.col.lastLogin", "Last login"), formatTime(user.lastLoginAt)],
         [tr("admin.sessions.title", "Sessions"), sessions ? String(sessions.totalElements) : "—"], [tr("admin.db.table.notifications", "Notifications"), notifications ? String(notifications.totalElements) : "—"],
         [tr("admin.user.adminActions", "Admin actions"), audit ? String(audit.totalElements) : "—"]].forEach(([label, value]) => {
            const item = element("div"); item.append(element("dt", label), element("dd", value)); facts.append(item);
        });
        detail.append(facts);

        const sections = element("div", null, "admin-user-sections");

        const statsCard = sectionCard(tr("admin.user.statsTitle", "Game statistics"), `/admin/stats?userId=${user.id}`, tr("admin.user.openEditor", "Open editor"));
        if (!stats) statsCard.body.append(element("p", tr("admin.user.noStats", "Stats are unavailable for this user."), "admin-empty"));
        else {
            const overall = Object.entries(stats.overall || {});
            statsCard.body.append(overall.length
                ? table([tr("admin.common.game", "Game"), tr("admin.user.played", "Played"), tr("admin.user.wins", "Wins"), tr("admin.user.winRate", "Win rate"), tr("admin.user.lastPlayed", "Last played")], overall.map(([game, line]) => [game, String(line.played), String(line.wins), line.played ? `${Math.round(line.wins / line.played * 100)}%` : "—", formatTime(line.lastPlayedAt)]))
                : element("p", tr("admin.user.noGames", "No games recorded yet."), "admin-empty"));
        }
        sections.append(statsCard.card);

        const sessionsCard = sectionCard(`${tr("admin.sessions.title", "Sessions")}${sessions ? ` · ${sessions.totalElements}` : ""}`, `/admin/sessions?userId=${user.id}`, tr("admin.user.viewAll", "View all"));
        if (!sessions || !sessions.items.length) sessionsCard.body.append(element("p", tr("admin.user.noSessions", "No sessions recorded for this user."), "admin-empty"));
        else sessionsCard.body.append(table([tr("admin.sessions.device", "Device"), tr("admin.sessions.os", "OS"), tr("admin.sessions.lastSeen", "Last seen"), tr("admin.sessions.token", "Token"), tr("admin.common.state", "State")], sessions.items.map(session => [
            session.clientType || tr("admin.sessions.unknownDevice", "Unknown device"), session.os || tr("admin.sessions.unknownOs", "Unknown OS"),
            formatTime(session.lastSeenAt),
            session.tokenId ? link(tokenHref(session.tokenId), shortId(session.tokenId), "admin-link") : "—",
            chip(session.active ? tr("admin.common.active", "Active") : tr("admin.common.expired", "Expired"), session.active ? "is-good" : "")
        ])));
        sections.append(sessionsCard.card);

        const notificationsCard = sectionCard(`${tr("admin.db.table.notifications", "Notifications")}${notifications ? ` · ${notifications.totalElements}` : ""}`, "/admin/database?table=notifications", tr("admin.user.browseAll", "Browse all"));
        if (!notifications || !notifications.items.length) notificationsCard.body.append(element("p", tr("admin.user.noNotifications", "No notifications for this user."), "admin-empty"));
        else notificationsCard.body.append(table([tr("admin.common.type", "Type"), tr("admin.notify.message", "Message"), tr("admin.common.read", "Read"), tr("admin.common.created", "Created"), ""], notifications.items.map(notification => [
            String(notification.type), notification.message || "—",
            chip(notification.read ? tr("admin.common.read", "Read") : tr("admin.common.unread", "Unread"), notification.read ? "" : "is-warn"), formatTime(notification.createdAt),
            [button(tr("admin.common.edit", "Edit"), "edit-database-notification", notification.id), button(tr("admin.common.delete", "Delete"), "delete-database-notification", notification.id, true)]
        ])));
        sections.append(notificationsCard.card);

        const auditCard = sectionCard(`${tr("admin.user.adminActions", "Admin actions")}${audit ? ` · ${audit.totalElements}` : ""}`, `/admin/audit?targetType=USER&targetId=${user.id}`, tr("admin.user.fullTrail", "Full trail"));
        if (!audit || !audit.items.length) auditCard.body.append(element("p", tr("admin.user.noAudit", "No administrative actions have touched this user."), "admin-empty"));
        else auditCard.body.append(table([tr("admin.common.when", "When"), tr("admin.audit.action", "Action"), tr("admin.audit.outcome", "Outcome"), tr("admin.common.reason", "Reason")], audit.items.map(event => [
            formatTime(event.occurredAt), event.action, event.outcome, event.reason || "—"
        ])));
        sections.append(auditCard.card);

        detail.append(sections);
    };

    const loadLobbies = async () => {
        const data = await request("/lobbies"); clear("lobbies"); if (!data.length) return empty("lobbies", tr("admin.lobbies.empty", "There are no live lobbies."));
        data.forEach(item => { const lobby = item.lobby; const game = lobby.gameType?.name || lobby.gameType?.game || tr("admin.common.game", "Game"); row("lobbies", lobby.name, `${game} · ${item.state} · ${tr("admin.lobbies.players", `${lobby.players?.length || 0}/${lobby.maxPlayers} players`, lobby.players?.length || 0, lobby.maxPlayers)}`, [button(tr("admin.common.edit", "Edit"), "edit-lobby", lobby.id), button(tr("admin.lobbies.extend", "Extend"), "extend-lobby", lobby.id), button(tr("admin.lobbies.close", "Close lobby"), "close-lobby", lobby.id, true)]); });
    };
    const gameMeta = game => {
        const meta = [`${game.gameType}${game.mode ? ` · ${game.mode}` : ""} · ${tr("admin.games.players", `${game.players?.length || 0} players`, game.players?.length || 0)} · ${tr("admin.games.rounds", `${game.rounds || 0} rounds`, game.rounds || 0)}`];
        if (game.ownerUserId != null) meta.push(` · ${tr("admin.games.owner", "Owner")} `, userLink(game.ownerUserId, `#${game.ownerUserId}`));
        if (game.players?.length) { meta.push(` · ${tr("admin.games.playersLabel", "Players")}: `, ...playerLinks(game.players)); }
        meta.push(` · ${tr("admin.games.winners", "Winners")}: `); if (game.winners?.length) meta.push(...playerLinks(game.winners)); else meta.push("—");
        meta.push(game.endedAt ? ` · ${tr("admin.games.ended", `Ended ${formatTime(game.endedAt)}`, formatTime(game.endedAt))}` : ` · ${tr("admin.common.incomplete", "Incomplete")}`);
        return meta;
    };
    const loadGames = async (currentPage = 0) => {
        const values = formValues(document.querySelector('[data-filters="games"]'));
        const sort = values.sort === "createdAtOldest" ? "createdAt" : values.sort;
        const direction = values.sort === "createdAtOldest" ? "asc" : values.sort === "name" ? "asc" : "desc";
        const data = await request(`/reports/games${query({ page: currentPage, size: 20, ...values, sort, direction })}`); clear("games");
        if (!data.items.length) return empty("games", tr("admin.games.empty", "No recorded games match these filters."));
        data.items.forEach(game => row("games", game.name || tr("admin.games.fallbackName", `${game.gameType} game`, game.gameType), gameMeta(game), [button(tr("admin.games.rename", "Rename"), "rename-game", game.id), button(tr("admin.common.delete", "Delete"), "delete-game", game.id, true)])); renderPaged("games", data);
    };
    const sessionMeta = session => [
        `${session.clientType || tr("admin.sessions.unknownDevice", "Unknown device")} · ${session.os || tr("admin.sessions.unknownOs", "Unknown OS")} · ${[session.country, session.region].filter(Boolean).join(", ") || tr("admin.sessions.unknownLocation", "Unknown location")} · ${tr("admin.sessions.seen", `Seen ${formatTime(session.lastSeenAt)}`, formatTime(session.lastSeenAt))} · ${tr("admin.sessions.tokenExpires", `Token expires ${formatTime(session.tokenExpiresAt)}`, formatTime(session.tokenExpiresAt))}`,
        ...(session.tokenId ? [` · ${tr("admin.sessions.token", "Token")}: `, link(tokenHref(session.tokenId), shortId(session.tokenId), "admin-link")] : [])
    ];
    const loadSessions = async (currentPage = 0) => {
        const values = formValues(document.querySelector('[data-filters="sessions"]')); const data = await request(`/reports/sessions${query({ page: currentPage, size: 20, ...values })}`); clear("sessions");
        if (!data.items.length) return empty("sessions", tr("admin.sessions.empty", "No sessions match these filters."));
        results("sessions").append(bulkBar(tr("admin.bulk.sessions", "Delete selected sessions"), "delete-selected-sessions", "sessions"));
        data.items.forEach(session => row("sessions",
            [userLink(session.userId), ` · ${session.active ? tr("admin.common.active", "Active") : tr("admin.common.expired", "Expired")}`],
            sessionMeta(session),
            [selectBox("sessions", session.id), ...(session.active ? [button(tr("admin.sessions.expire", "Expire session"), "expire-session", session.id, true)] : []), button(tr("admin.common.delete", "Delete"), "delete-session", session.id, true)]));
        renderPaged("sessions", data);
    };
    const loadAvailability = async () => {
        const data = await request("/games"); clear("availability"); if (!data.length) return empty("availability", tr("admin.availability.empty", "No game availability rules are configured."));
        const games = new Map();
        data.forEach(item => {
            const group = games.get(item.game) || { game: null, modes: [] };
            if (item.mode) group.modes.push(item); else group.game = item;
            games.set(item.game, group);
        });
        const availabilityRow = (item, label) => {
            const node = element("article", null, "admin-row"); const details = element("div");
            details.append(element("h3", label), element("p", item.enabled ? tr("admin.availability.available", "Available to players") : tr("admin.availability.unavailable", "Unavailable to players"), "admin-meta"));
            node.append(details, element("div", null, "admin-actions"));
            node.lastElementChild.append(button(item.enabled ? tr("admin.availability.disable", "Disable") : tr("admin.availability.enable", "Enable"), "toggle-game", `${item.game}|${item.mode || ""}|${item.enabled ? "disable" : "enable"}`, !item.enabled));
            return node;
        };
        [...games.entries()].sort(([left], [right]) => left.localeCompare(right)).forEach(([game, group]) => {
            const list = element("details", null, "admin-availability-group");
            const summary = element("summary"); summary.append(element("strong", game), chip(group.game.enabled ? tr("admin.common.enabled", "Enabled") : tr("admin.common.disabled", "Disabled"), group.game.enabled ? "is-good" : "is-bad"), element("span", tr("admin.availability.modes", `${group.modes.length} modes`, group.modes.length)));
            const modes = element("div", null, "admin-availability-modes");
            modes.append(availabilityRow(group.game, tr("admin.availability.entireGame", "Entire game")), ...group.modes.sort((left, right) => left.mode.localeCompare(right.mode)).map(item => availabilityRow(item, item.mode)));
            list.append(summary, modes); results("availability").append(list);
        });
    };
    const auditMeta = event => {
        const meta = [];
        if (event.targetType === "USER" && /^\d+$/.test(event.targetId || "")) meta.push(`${tr("admin.audit.target", "Target")} `, userLink(event.targetId, `${tr("admin.common.user", "user")} #${event.targetId}`));
        else meta.push(`${tr("admin.audit.target", "Target")} ${event.targetType} ${event.targetId}`);
        if (event.actorUserId != null) meta.push(` · ${tr("admin.audit.by", "By")} `, userLink(event.actorUserId, `#${event.actorUserId}`));
        meta.push(` · ${event.reason || tr("admin.audit.noReason", "No reason recorded")} · ${formatTime(event.occurredAt)}`);
        return meta;
    };
    const loadAudit = async (currentPage = 0) => {
        const values = formValues(document.querySelector('[data-filters="audit"]'));
        const data = await request(`/audit${query({ page: currentPage, size: 20, ...values })}`); clear("audit");
        if (!data.items.length) return empty("audit", tr("admin.audit.empty", "No administrative actions match this filter."));
        data.items.forEach(event => row("audit", `${event.action} · ${event.outcome}`, auditMeta(event),
            event.undoable && !event.undone ? [button(tr("admin.audit.undo", "Undo"), "undo-audit", event.id, true)] : [])); renderPaged("audit", data);
    };

    const notificationActions = notification => [button(tr("admin.common.edit", "Edit"), "edit-database-notification", notification.id), button(tr("admin.common.delete", "Delete"), "delete-database-notification", notification.id, true)];
    const allOption = { value: "", label: tr("admin.common.all", "All") };
    const stateChip = active => chip(active ? tr("admin.common.active", "Active") : tr("admin.common.expired", "Expired"), active ? "is-good" : "");
    const dbTables = {
        users: {
            label: tr("admin.db.table.users", "Users"), area: "Users",
            filters: [
                { name: "query", label: tr("admin.users.search", "Search"), type: "search" },
                { name: "status", label: tr("admin.users.status", "Status"), options: [allOption, { value: "ACTIVE", label: "ACTIVE" }, { value: "DISABLED", label: "DISABLED" }, { value: "DELETED", label: "DELETED" }] },
                { name: "role", label: tr("admin.users.role", "Role"), options: [allOption, { value: "USER", label: "USER" }, { value: "MODERATOR", label: "MODERATOR" }, { value: "ADMIN", label: "ADMIN" }] }
            ],
            load: async currentPage => {
                const data = await request(`/reports/users${query({ page: currentPage, size: 20, sort: "id", direction: "asc", ...state.dbFilters })}`);
                return { data, table: table(["ID", tr("admin.users.username", "Username"), tr("admin.users.email", "Email"), tr("admin.users.status", "Status"), tr("admin.col.roles", "Roles"), tr("admin.common.created", "Created"), tr("admin.col.lastLogin", "Last login")], data.items.map(user => [
                    userLink(user.id, `#${user.id}`), userLink(user.id, user.username), user.email, chip(user.status, statusClass(user.status)), [...user.roles].join(", "), formatTime(user.createdAt), formatTime(user.lastLoginAt)
                ]), true) };
            }
        },
        sessions: {
            label: tr("admin.db.table.sessions", "Sessions"), area: "Sessions",
            filters: [
                { name: "id", label: tr("admin.db.sessionId", "Session ID") },
                { name: "userId", label: tr("admin.common.userId", "User ID"), type: "number" },
                { name: "valid", label: tr("admin.common.state", "State"), options: [allOption, { value: "true", label: tr("admin.common.active", "Active") }, { value: "false", label: tr("admin.common.expired", "Expired") }] }
            ],
            load: async currentPage => {
                const data = await request(`/reports/sessions${query({ page: currentPage, size: 20, ...state.dbFilters })}`);
                return { data, toolbar: bulkBar(tr("admin.bulk.sessions", "Delete selected sessions"), "delete-selected-sessions", "db-sessions"), table: table([selectAllBox("db-sessions"), "ID", tr("admin.common.user", "User"), tr("admin.sessions.device", "Device"), tr("admin.sessions.os", "OS"), tr("admin.sessions.firstSeen", "First seen"), tr("admin.sessions.lastSeen", "Last seen"), tr("admin.sessions.token", "Token"), tr("admin.common.state", "State"), tr("admin.common.actions", "Actions")], data.items.map(session => [
                    selectBox("db-sessions", session.id), shortId(session.id), userLink(session.userId, `#${session.userId}`), session.clientType || "—", session.os || "—",
                    formatTime(session.firstSeenAt), formatTime(session.lastSeenAt),
                    session.tokenId ? link(tokenHref(session.tokenId), shortId(session.tokenId), "admin-link") : "—",
                    stateChip(session.active),
                    session.active ? button(tr("admin.sessions.expire", "Expire session"), "expire-session", session.id, true) : "—"
                ]), true) };
            }
        },
        tokens: {
            label: tr("admin.db.table.tokens", "Tokens"), area: "Tokens",
            filters: [
                { name: "id", label: tr("admin.db.tokenId", "Token ID") },
                { name: "userId", label: tr("admin.common.userId", "User ID"), type: "number" },
                { name: "active", label: tr("admin.common.state", "State"), options: [allOption, { value: "true", label: tr("admin.common.active", "Active") }, { value: "false", label: tr("admin.common.disabled", "Disabled") }] }
            ],
            load: async currentPage => {
                const data = await request(`/reports/tokens${query({ page: currentPage, size: 20, ...state.dbFilters })}`);
                return { data, table: table(["ID", tr("admin.common.user", "User"), tr("admin.db.session", "Session"), tr("admin.common.state", "State"), tr("admin.col.expires", "Expires"), tr("admin.db.reuseUntil", "Reuse until"), tr("admin.db.rotatedTo", "Rotated to")], data.items.map(token => [
                    shortId(token.id), userLink(token.userId, `#${token.userId}`),
                    token.sessionId ? link(`/admin/database?table=sessions&id=${encodeURIComponent(token.sessionId)}`, shortId(token.sessionId), "admin-link") : "—",
                    chip(token.valid ? tr("admin.db.valid", "Valid") : tr("admin.db.invalid", "Invalid"), token.valid ? "is-good" : ""),
                    formatTime(token.expiresAt), formatTime(token.reuseUntil),
                    token.rotatedToTokenId ? link(tokenHref(token.rotatedToTokenId), shortId(token.rotatedToTokenId), "admin-link") : "—"
                ]), true) };
            }
        },
        games: {
            label: tr("admin.db.table.games", "Recorded games"), area: "Recorded games",
            filters: [
                { name: "gameType", label: tr("admin.games.game", "Game"), options: [allOption, { value: "BRISKULA", label: "Briskula" }, { value: "TRESETA", label: "Treseta" }] },
                { name: "mode", label: tr("admin.games.mode", "Mode"), options: [allOption, ...[...new Set([...modes.briskula, ...modes.treseta])].map(value => ({ value, label: value }))] },
                { name: "completed", label: tr("admin.games.state", "State"), options: [allOption, { value: "true", label: tr("admin.games.completed", "Completed") }, { value: "false", label: tr("admin.games.incomplete", "Incomplete") }] }
            ],
            load: async currentPage => {
                const data = await request(`/reports/games${query({ page: currentPage, size: 20, ...state.dbFilters })}`);
                return { data, toolbar: bulkBar(tr("admin.bulk.games", "Delete selected games"), "delete-selected-games", "db-games"), table: table([selectAllBox("db-games"), "ID", tr("admin.common.type", "Type"), tr("admin.common.mode", "Mode"), tr("admin.common.name", "Name"), tr("admin.games.owner", "Owner"), tr("admin.games.playersLabel", "Players"), tr("admin.games.winners", "Winners"), tr("admin.games.ended", "Ended", "").trim() || "Ended", tr("admin.common.actions", "Actions")], data.items.map(game => [
                    selectBox("db-games", game.id), shortId(game.id), game.gameType, game.mode || "—", game.name || "—",
                    game.ownerUserId != null ? userLink(game.ownerUserId, `#${game.ownerUserId}`) : "—",
                    game.players?.length ? playerLinks(game.players) : "—",
                    game.winners?.length ? playerLinks(game.winners) : "—",
                    game.endedAt ? formatTime(game.endedAt) : chip(tr("admin.common.incomplete", "Incomplete"), "is-warn"),
                    button(tr("admin.common.delete", "Delete"), "delete-game", game.id, true)
                ]), true) };
            }
        },
        notifications: {
            label: tr("admin.db.table.notifications", "Notifications"), area: "Notifications",
            filters: [
                { name: "userId", label: tr("admin.common.userId", "User ID"), type: "number" }
            ],
            load: async currentPage => {
                const data = await request(`/database/notifications${query({ page: currentPage, size: 20, ...state.dbFilters })}`);
                state.notifications = new Map(data.items.map(item => [item.id, item]));
                return { data, toolbar: bulkBar(tr("admin.bulk.notifications", "Delete selected notifications"), "delete-selected-notifications", "db-notifications"), table: table([selectAllBox("db-notifications"), tr("admin.common.type", "Type"), tr("admin.col.recipient", "Recipient"), tr("admin.notify.message", "Message"), tr("admin.common.read", "Read"), tr("admin.common.created", "Created"), tr("admin.common.actions", "Actions")], data.items.map(notification => [
                    selectBox("db-notifications", notification.id),
                    String(notification.type),
                    notification.recipient?.id != null ? userLink(notification.recipient.id, notification.recipient.name || `#${notification.recipient.id}`) : "—",
                    notification.message || "—", chip(notification.read ? tr("admin.common.read", "Read") : tr("admin.common.unread", "Unread"), notification.read ? "" : "is-warn"), formatTime(notification.createdAt),
                    notificationActions(notification)
                ]), true) };
            }
        },
        audit: {
            label: tr("admin.db.table.audit", "Audit events"), area: "Admin audit events",
            filters: [
                { name: "targetType", label: tr("admin.audit.targetType", "Target type"), options: [allOption, ...["USER", "LOBBY", "GAME", "NOTIFICATION", "STATS", "SESSION"].map(value => ({ value, label: value }))] },
                { name: "targetId", label: tr("admin.audit.targetId", "Target ID") }
            ],
            load: async currentPage => {
                const data = await request(`/audit${query({ page: currentPage, size: 20, ...state.dbFilters })}`);
                return { data, table: table([tr("admin.common.when", "When"), tr("admin.audit.action", "Action"), tr("admin.audit.target", "Target"), tr("admin.audit.actor", "Actor"), tr("admin.audit.outcome", "Outcome"), tr("admin.common.reason", "Reason")], data.items.map(event => [
                    formatTime(event.occurredAt), event.action,
                    event.targetType === "USER" && /^\d+$/.test(event.targetId || "") ? userLink(event.targetId, `USER ${event.targetId}`) : `${event.targetType} ${event.targetId}`,
                    event.actorUserId != null ? userLink(event.actorUserId, `#${event.actorUserId}`) : "—",
                    event.outcome, event.reason || "—"
                ]), true) };
            }
        },
        availability: {
            label: tr("admin.db.table.availability", "Game availability"), area: null,
            load: async () => {
                const data = await request("/games");
                return { data: null, table: table([tr("admin.games.game", "Game"), tr("admin.common.mode", "Mode"), tr("admin.common.state", "State"), tr("admin.common.actions", "Actions")], data.map(item => [
                    item.game, item.mode || tr("admin.availability.entireGame", "All modes"), chip(item.enabled ? tr("admin.common.enabled", "Enabled") : tr("admin.common.disabled", "Disabled"), item.enabled ? "is-good" : "is-bad"),
                    [button(item.enabled ? tr("admin.availability.disable", "Disable") : tr("admin.availability.enable", "Enable"), "toggle-game", `${item.game}|${item.mode || ""}|${item.enabled ? "disable" : "enable"}`, !item.enabled), button(tr("admin.availability.reset", "Reset"), "reset-availability", `${item.game}|${item.mode || ""}`)]
                ]), true) };
            }
        }
    };
    const renderDatabaseFilters = () => {
        const form = document.querySelector("#admin-database-filters");
        if (!form) return;
        const definition = dbTables[state.dbTable];
        if (!definition || !definition.filters) { form.hidden = true; form.replaceChildren(); form.dataset.table = ""; return; }
        if (form.dataset.table === state.dbTable) return;
        form.dataset.table = state.dbTable;
        form.replaceChildren(...definition.filters.map(field => {
            const label = element("label");
            label.append(element("span", field.label), document.createTextNode(" "));
            let input;
            if (field.options) {
                input = element("select"); input.name = field.name;
                input.append(...field.options.map(option => { const node = element("option", option.label); node.value = option.value; return node; }));
            } else {
                input = document.createElement("input"); input.name = field.name; input.type = field.type || "text"; input.autocomplete = "off";
            }
            if (state.dbFilters[field.name] != null) input.value = state.dbFilters[field.name];
            label.append(input); return label;
        }));
        const apply = element("button", tr("admin.db.filter", "Filter"), "btn"); apply.type = "submit";
        form.append(apply);
        form.hidden = false;
    };
    document.querySelector("#admin-database-filters")?.addEventListener("submit", event => {
        event.preventDefault();
        state.dbFilters = Object.fromEntries(Object.entries(formValues(event.currentTarget)).filter(([, value]) => value !== ""));
        history.replaceState(null, "", `/admin/database${query({ table: state.dbTable, ...state.dbFilters })}`);
        loadDatabaseTable().catch(error => setStatus(error.message, true));
    });
    const loadDatabaseTable = async (currentPage = 0) => {
        const definition = dbTables[state.dbTable];
        renderDatabaseFilters();
        clear("database-browser");
        if (!definition) return empty("database-browser", tr("admin.db.pick", "Pick a table above to browse its records."));
        results("database-browser").append(element("h3", definition.label, "admin-db-title"));
        const { data, table: rendered, toolbar } = await definition.load(currentPage);
        if (data && !data.items.length) return empty("database-browser", tr("admin.db.emptyTable", "This table is empty."));
        if (toolbar) results("database-browser").append(toolbar);
        results("database-browser").append(rendered);
        if (data) renderPaged("database-browser", data);
    };
    const loadDatabase = async () => {
        const overview = await request("/reports/database"); clear("database");
        row("database", overview.available ? tr("admin.db.available", "Database available") : tr("admin.db.unavailable", "Database unavailable"), tr("admin.db.meta", `Flyway ${overview.flywayVersion} · refreshed ${formatTime(overview.generatedAt)}`, overview.flywayVersion, formatTime(overview.generatedAt)));
        const picker = document.querySelector("#admin-database-tables"); picker.replaceChildren();
        const counts = overview.recordsByArea || {};
        const used = new Set();
        Object.entries(dbTables).forEach(([key, definition]) => {
            if (definition.area) used.add(definition.area);
            const card = link(`/admin/database?table=${key}`, null, `admin-db-card${state.dbTable === key ? " is-active" : ""}`);
            card.append(element("strong", definition.area != null && counts[definition.area] != null ? String(counts[definition.area]) : tr("admin.db.rules", "Rules")), element("span", definition.label));
            picker.append(card);
        });
        const areaLabels = {
            "Game-stat profiles": tr("admin.db.area.profiles", "Game-stat profiles"),
            "Briskula stat rows": tr("admin.db.area.briskulaRows", "Briskula stat rows"),
            "Treseta stat rows": tr("admin.db.area.tresetaRows", "Treseta stat rows")
        };
        Object.entries(counts).forEach(([area, count]) => {
            if (used.has(area)) return;
            const card = element("div", null, "admin-db-card is-static");
            card.append(element("strong", String(count)), element("span", areaLabels[area] || area));
            picker.append(card);
        });
        await loadDatabaseTable();
    };

    const renderStats = stats => {
        clear("stats");
        const diff = document.querySelector("#admin-stats-diff"); if (diff) { diff.hidden = true; diff.replaceChildren(); }
        const context = document.querySelector("#admin-stats-context");
        if (context) { context.replaceChildren(); content(context, [userLink(stats.userId, `${tr("admin.common.user", "User")} #${stats.userId}`), ` · ${stats.briskulaOpponentRows + stats.briskulaTeammateRows} Briskula · ${stats.tresetaOpponentRows + stats.tresetaTeammateRows} Treseta`]); }
        const addGroup = (label, entries) => Object.entries(entries || {}).forEach(([mode, line]) => row("stats", `${label} · ${mode}`, `${line.played} ${tr("admin.user.played", "played").toLowerCase()} · ${line.wins} ${tr("admin.user.wins", "wins").toLowerCase()} · ${tr("admin.user.lastPlayed", "Last played")} ${formatTime(line.lastPlayedAt)}`));
        addGroup("Overall", stats.overall);
        Object.entries(modes).forEach(([gameType, gameModes]) => {
            const entries = gameType === "briskula" ? stats.briskulaModes || {} : stats.tresetaModes || {};
            gameModes.forEach(mode => {
                const line = entries[mode];
                const values = line || { played: 0, wins: 0, lastPlayedAt: null };
                const label = line ? tr("admin.common.edit", "Edit") : "Add";
                const target = encodeURIComponent(JSON.stringify({ gameType, mode, played: values.played, wins: values.wins, lastPlayedAt: values.lastPlayedAt }));
                row("stats", `${gameType[0].toUpperCase()}${gameType.slice(1)} · ${mode}`, line ? `${values.played} · ${values.wins} · ${formatTime(values.lastPlayedAt)}` : "—", [button(label, "edit-stat-row", target, !line)]);
            });
        });
    };
    const renderStatsDiff = (before, after) => {
        const diff = document.querySelector("#admin-stats-diff"); if (!diff) return;
        diff.replaceChildren(element("strong", "Preview result"));
        const changed = [];
        ["BRISKULA", "TRESETA"].forEach(game => {
            const key = game === "BRISKULA" ? "briskulaModes" : "tresetaModes";
            const modesAfter = after[key] || {}; const modesBefore = before?.[key] || {};
            Object.keys({ ...modesBefore, ...modesAfter }).forEach(mode => {
                const from = modesBefore[mode] || { played: 0, wins: 0 };
                const to = modesAfter[mode] || { played: 0, wins: 0 };
                if (from.played !== to.played || from.wins !== to.wins || from.lastPlayedAt !== to.lastPlayedAt) changed.push(`${game} · ${mode}: ${from.played} / ${from.wins} → ${to.played} / ${to.wins}`);
            });
        });
        (changed.length ? changed : ["No values changed."]).forEach(line => diff.append(element("p", line, "admin-meta")));
        diff.hidden = false;
    };
    const loadStats = async userId => {
        const data = await request(`/stats/users/${userId}`); state.stats = data; renderStats(data);
        const lookup = document.querySelector("#admin-stats-lookup"); lookup.elements.userQuery.value = userId;
        const form = document.querySelector("#admin-stats-edit"); form.hidden = false; form.elements.userId.value = userId;
    };
    const loadStatsForQuery = async value => {
        if (/^\d+$/.test(value.trim())) return loadStats(value.trim());
        const data = await request(`/reports/users${query({ page: 0, size: 10, query: value.trim(), exact: true })}`);
        clear("stats");
        if (!data.items.length) return empty("stats", "No exact username or email match. Try the Users page for a contains search.");
        if (data.items.length === 1) return loadStats(data.items[0].id);
        data.items.forEach(user => row("stats", `${user.username} · #${user.id}`, user.email, [button(tr("admin.stats.load", "Load stats"), "load-stats", String(user.id))]));
        setStatus("Choose the user whose stats you want to load.");
    };

    const loaders = {
        dashboard: loadOverview,
        users: currentPage => state.userDetailId ? loadUserDetail(state.userDetailId) : loadUsers(currentPage),
        lobbies: loadLobbies, games: loadGames, sessions: loadSessions, availability: loadAvailability,
        database: loadDatabase, audit: loadAudit, stats: () => {}, notifications: () => {}
    };
    const refresh = async () => { setStatus(tr("admin.refreshing", "Refreshing…")); try { await loaders[page](); setStatus(tr("admin.updated", `Updated ${new Date().toLocaleTimeString()}.`, new Date().toLocaleTimeString())); } catch (error) { setStatus(error.message, true); } };
    const refreshNotificationsContext = () => state.userDetailId ? loadUserDetail(state.userDetailId) : loadDatabaseTable();
    const refreshAvailabilityContext = () => page === "database" ? loadDatabaseTable() : loadAvailability();

    document.querySelectorAll(".admin-filters[data-filters]").forEach(form => form.addEventListener("submit", event => {
        event.preventDefault();
        if (form.dataset.filters === "users" && state.userDetailId) { state.userDetailId = null; history.replaceState(null, "", "/admin/users"); document.querySelector("#admin-user-detail").hidden = true; document.querySelector("#admin-user-list").hidden = false; }
        refresh();
    }));
    document.querySelector('[data-action="refresh"]').addEventListener("click", refresh);
    document.querySelector("#admin-stats-lookup")?.addEventListener("submit", event => { event.preventDefault(); loadStatsForQuery(formValues(event.currentTarget).userQuery).catch(error => setStatus(error.message, true)); });
    document.querySelector("#admin-stats-edit")?.addEventListener("submit", async event => {
        event.preventDefault(); const values = formValues(event.currentTarget); const body = { played: values.played === "" ? null : Number(values.played), wins: values.wins === "" ? null : Number(values.wins), lastPlayedAt: values.lastPlayedAt ? new Date(values.lastPlayedAt).toISOString() : null, reason: values.reason, dryRun: values.dryRun === "on" };
        try { const result = await request(`/stats/users/${values.userId}/${encodeURIComponent(values.gameType)}/${encodeURIComponent(values.mode)}`, { method: "PATCH", body: JSON.stringify(body) }); if (result.dryRun) { renderStats(result.after); renderStatsDiff(result.before, result.after); } else await loadStats(values.userId); setStatus(result.warning || (result.dryRun ? "Stats preview is ready; nothing changed." : "Stats updated.")); } catch (error) { setStatus(error.message, true); }
    });
    document.querySelector("#admin-stats-edit")?.elements.gameType.addEventListener("change", populateModes);
    const gamesFilter = document.querySelector('[data-filters="games"]');
    gamesFilter?.elements.gameType.addEventListener("change", () => {
        const available = { BRISKULA: modes.briskula, TRESETA: modes.treseta }[gamesFilter.elements.gameType.value] || [...new Set([...modes.briskula, ...modes.treseta])];
        const select = gamesFilter.elements.mode; const selected = select.value;
        const all = element("option", tr("admin.common.all", "All")); all.value = "";
        select.replaceChildren(all, ...available.map(value => element("option", value)));
        if (available.includes(selected)) select.value = selected;
    });
    document.addEventListener("click", async event => {
        const control = event.target.closest("button[data-action]"); if (!control || control.dataset.action === "refresh") return;
        try {
            if (control.dataset.action === "undo-audit") {
                const values = await askAction({ title: tr("admin.audit.undoTitle", "Undo audit action"), description: tr("admin.audit.undoCopy", "Restore the database state from before this action. Undo is available for 24 hours."), confirmLabel: tr("admin.audit.undo", "Undo"), danger: true, fields: [reasonField()] });
                if (!values) return; await request(`/audit/${control.dataset.value}/undo${query({ reason: values.reason })}`, { method: "POST" }); await loadAudit(); setStatus(tr("admin.audit.undone", "Audit action undone.")); return;
            }
            if (control.dataset.action === "page") { const [name, nextPage] = control.dataset.value.split(":"); if (name === "database-browser") await loadDatabaseTable(Number(nextPage)); else await loaders[name](Number(nextPage)); return; }
            if (control.dataset.action === "close-user-editor") { document.querySelector("#admin-user-editor").close(); return; }
            if (control.dataset.action === "close-lobby-editor") { document.querySelector("#admin-lobby-editor").close(); return; }
            if (control.dataset.action === "edit-user") {
                const user = await request(`/users/${control.dataset.value}`); state.currentUser = user;
                const form = document.querySelector("#admin-user-form"); form.elements.id.value = user.id; form.elements.username.value = user.username; form.elements.email.value = user.email; form.elements.status.value = user.status; form.elements.MODERATOR.checked = user.roles.includes("MODERATOR"); form.elements.ADMIN.checked = user.roles.includes("ADMIN"); form.elements.reason.value = ""; form.elements.dryRun.checked = false;
                document.querySelector("#admin-user-caption").textContent = `${user.username} · #${user.id}`; document.querySelector("#admin-user-editor").showModal(); return;
            }
            if (control.dataset.action === "load-stats") { await loadStats(control.dataset.value); return; }
            if (control.dataset.action === "edit-stat-row") {
                const target = JSON.parse(decodeURIComponent(control.dataset.value)); const form = document.querySelector("#admin-stats-edit");
                form.hidden = false; form.elements.gameType.value = target.gameType; populateModes(); form.elements.mode.value = target.mode; form.elements.played.value = target.played; form.elements.wins.value = target.wins; form.elements.lastPlayedAt.value = formDateTime(target.lastPlayedAt); form.elements.reason.value = ""; form.elements.dryRun.checked = true; form.elements.reason.focus(); return;
            }
            if (control.dataset.action === "save-user") {
                const form = document.querySelector("#admin-user-form"); const values = formValues(form); const user = state.currentUser; const dryRun = values.dryRun === "on"; const changedFields = user.username !== values.username || user.email !== values.email || user.status !== values.status;
                if (!values.reason.trim()) throw new Error(tr("admin.common.reasonRequired", "A reason is required."));
                if (!dryRun && !await confirmAction(tr("admin.user.saveTitle", "Save user"), tr("admin.user.saveCopy", `Save changes for ${user.username}?`, user.username))) return;
                if (changedFields) await request(`/users/${user.id}`, { method: "PATCH", body: JSON.stringify({ username: values.username, email: values.email, status: values.status, reason: values.reason, dryRun }) });
                if (!dryRun) for (const role of ["MODERATOR", "ADMIN"]) { const hasRole = user.roles.includes(role); const wantsRole = values[role] === "on"; if (hasRole !== wantsRole) await request(`/users/${user.id}/roles/${role}${query({ reason: values.reason })}`, { method: wantsRole ? "PUT" : "DELETE" }); }
                setStatus(dryRun ? "User field preview is ready; role changes were not applied." : tr("admin.user.saved", "User updated."));
                if (!dryRun) { document.querySelector("#admin-user-editor").close(); await (state.userDetailId ? loadUserDetail(state.userDetailId) : loadUsers()); } return;
            }
            if (control.dataset.action === "revoke-user-sessions") {
                const values = await askAction({ title: tr("admin.user.revokeSessions", "Revoke sessions"), description: tr("admin.user.revokeCopy", "Sign this user out of every device."), confirmLabel: tr("admin.user.revokeSessions", "Revoke sessions"), danger: true, fields: [reasonField()] });
                if (!values) return; await request(`/users/${control.dataset.value}/sessions${query({ reason: values.reason })}`, { method: "DELETE" });
                setStatus(tr("admin.user.revoked", "All sessions revoked.")); if (state.userDetailId) await loadUserDetail(state.userDetailId); return;
            }
            if (control.dataset.action === "expire-session") {
                const values = await askAction({ title: tr("admin.sessions.expire", "Expire session"), description: tr("admin.sessions.expireCopy", "Immediately sign this device out while keeping its session record for audit history."), confirmLabel: tr("admin.sessions.expire", "Expire session"), danger: true, fields: [reasonField()] });
                if (!values) return; await request(`/sessions/${control.dataset.value}/expire${query({ reason: values.reason })}`, { method: "POST" });
                await (page === "database" ? loadDatabaseTable() : loadSessions()); setStatus(tr("admin.sessions.expired", "Session expired.")); return;
            }
            if (control.dataset.action === "delete-session") {
                const values = await askAction({ title: tr("admin.sessions.deleteTitle", "Delete session"), description: tr("admin.sessions.deleteCopy", "Permanently remove this session record and its token."), confirmLabel: tr("admin.sessions.deleteTitle", "Delete session"), danger: true, fields: [reasonField()] });
                if (!values) return; await request(`/sessions/${control.dataset.value}${query({ reason: values.reason })}`, { method: "DELETE" });
                await (page === "database" ? loadDatabaseTable() : loadSessions()); setStatus(tr("admin.sessions.deleted", "Session deleted.")); return;
            }
            if (control.dataset.action === "delete-selected-sessions") {
                const ids = selectedIds(control.dataset.value); if (!ids.length) throw new Error(tr("admin.bulk.selectFirst", "Select at least one row first."));
                const values = await askAction({ title: tr("admin.sessions.bulkDeleteTitle", "Delete sessions"), description: tr("admin.sessions.bulkDeleteCopy", `Permanently remove ${ids.length} sessions and their tokens.`, ids.length), confirmLabel: tr("admin.bulk.deleteCount", `Delete ${ids.length}`, ids.length), danger: true, fields: [reasonField()] });
                if (!values) return; for (const id of ids) await request(`/sessions/${id}${query({ reason: values.reason })}`, { method: "DELETE" });
                await (page === "database" ? loadDatabaseTable() : loadSessions()); setStatus(tr("admin.bulk.deletedCount", `Deleted ${ids.length} records.`, ids.length)); return;
            }
            if (control.dataset.action === "edit-lobby") {
                const data = await request(`/lobbies/${control.dataset.value}`); state.currentLobby = data;
                const lobby = data.lobby; const form = document.querySelector("#admin-lobby-form"); form.elements.id.value = lobby.id; form.elements.name.value = lobby.name; form.elements.visibility.value = lobby.isPublic ? "public" : "private"; form.elements.reason.value = "";
                document.querySelector("#admin-lobby-caption").textContent = `${lobby.name} · ${data.state}`;
                const players = document.querySelector("#admin-lobby-players"); players.replaceChildren();
                (lobby.players || []).forEach(player => { const id = player.id ?? player.userId; const playerName = player.name || player.username || `#${id}`; const item = element("article", null, "admin-row"); const label = element("div"); label.append(userLink(id, `${playerName} · #${id}`)); item.append(label, button(tr("admin.lobbies.removePlayer", "Remove player"), "kick-lobby-player", String(id))); players.append(item); });
                if (!lobby.players?.length) players.append(element("p", tr("admin.lobbies.noPlayers", "No players are currently in this lobby."), "admin-empty"));
                document.querySelector("#admin-lobby-editor").showModal(); return;
            }
            if (control.dataset.action === "save-lobby") {
                const form = document.querySelector("#admin-lobby-form"); const values = formValues(form); if (!values.reason.trim()) throw new Error(tr("admin.common.reasonRequired", "A reason is required.")); if (!await confirmAction(tr("admin.lobbies.saveTitle", "Save lobby"), tr("admin.lobbies.saveCopy", `Save changes for ${state.currentLobby.lobby.name}?`, state.currentLobby.lobby.name))) return;
                await request(`/lobbies/${values.id}`, { method: "PATCH", body: JSON.stringify({ name: values.name, visibility: values.visibility, mode: null, reason: values.reason }) });
                document.querySelector("#admin-lobby-editor").close(); await loadLobbies(); setStatus(tr("admin.lobbies.updated", "Lobby updated.")); return;
            }
            if (control.dataset.action === "kick-lobby-player") {
                const form = document.querySelector("#admin-lobby-form"); const values = formValues(form); if (!values.reason.trim()) throw new Error(tr("admin.common.reasonRequired", "A reason is required.")); if (!await confirmAction(tr("admin.lobbies.kickTitle", "Remove lobby player"), tr("admin.lobbies.kickCopy", "Remove this player from the lobby?"), true)) return;
                await request(`/lobbies/${values.id}/players/${control.dataset.value}${query({ reason: values.reason })}`, { method: "DELETE" });
                document.querySelector("#admin-lobby-editor").close(); await loadLobbies(); setStatus(tr("admin.lobbies.kicked", "Player removed from lobby.")); return;
            }
            if (control.dataset.action === "edit-database-notification") {
                const notification = state.notifications.get(control.dataset.value);
                if (!notification) throw new Error(tr("admin.notifications.gone", "Notification is no longer available."));
                const values = await askAction({ title: tr("admin.notifications.editTitle", "Edit notification"), description: tr("admin.notifications.editCopy", "Update the message or read state."), confirmLabel: tr("admin.notifications.save", "Save notification"), fields: [{ name: "message", label: tr("admin.notify.message", "Message"), value: notification.message || "", required: true, maxLength: 512 }, { name: "read", label: tr("admin.notifications.readState", "Read state"), value: String(notification.read), options: [{ value: "false", label: tr("admin.common.unread", "Unread") }, { value: "true", label: tr("admin.common.read", "Read") }] }, reasonField()] });
                if (!values) return; await request(`/database/notifications/${control.dataset.value}`, { method: "PATCH", body: JSON.stringify({ message: values.message, read: values.read === "true", reason: values.reason }) }); await refreshNotificationsContext(); setStatus(tr("admin.notifications.updated", "Notification updated.")); return;
            }
            if (control.dataset.action === "delete-selected-notifications") {
                const ids = selectedIds(control.dataset.value);
                if (!ids.length) throw new Error(tr("admin.bulk.selectFirst", "Select at least one row first."));
                const values = await askAction({ title: tr("admin.notifications.bulkDeleteTitle", "Delete notifications"), description: tr("admin.notifications.bulkDeleteCopy", `Permanently delete ${ids.length} notifications.`, ids.length), confirmLabel: tr("admin.bulk.deleteCount", `Delete ${ids.length}`, ids.length), danger: true, fields: [reasonField()] });
                if (!values) return;
                for (const id of ids) await request(`/database/notifications/${id}${query({ reason: values.reason })}`, { method: "DELETE" });
                await loadDatabaseTable(); setStatus(tr("admin.bulk.deletedCount", `Deleted ${ids.length} records.`, ids.length)); return;
            }
            if (control.dataset.action === "delete-database-notification") {
                const values = await askAction({ title: tr("admin.notifications.deleteTitle", "Delete notification"), description: tr("admin.notifications.deleteCopy", "Remove this notification permanently."), confirmLabel: tr("admin.notifications.deleteTitle", "Delete notification"), danger: true, fields: [reasonField()] });
                if (!values) return; await request(`/database/notifications/${control.dataset.value}${query({ reason: values.reason })}`, { method: "DELETE" }); await refreshNotificationsContext(); setStatus(tr("admin.notifications.deleted", "Notification deleted.")); return;
            }
            if (control.dataset.action === "reset-availability") {
                const [game, mode] = control.dataset.value.split("|"); const rule = `${game}${mode ? ` ${mode}` : ""}`;
                const values = await askAction({ title: tr("admin.availability.resetTitle", "Reset availability rule"), description: tr("admin.availability.resetCopy", `Remove the explicit ${rule} rule and return to the default enabled state.`, rule), confirmLabel: tr("admin.availability.reset", "Reset"), fields: [reasonField()] });
                if (!values) return; await request(`/games/${encodeURIComponent(game)}${query({ mode, reason: values.reason })}`, { method: "DELETE" }); await refreshAvailabilityContext(); setStatus(tr("admin.availability.resetDone", "Availability rule reset.")); return;
            }
            if (control.dataset.action === "rebuild-stats") { const values = formValues(document.querySelector("#admin-stats-edit")); if (!values.reason.trim()) throw new Error(tr("admin.common.reasonRequired", "A reason is required.")); const data = await request(`/stats/users/${values.userId}/rebuild${query({ reason: values.reason, gameType: values.gameType, dryRun: values.dryRun === "on" })}`, { method: "POST" }); if (data.dryRun) { renderStats(data.after); renderStatsDiff(data.before || state.stats, data.after); } else await loadStats(values.userId); setStatus(data.warning || (data.dryRun ? "Rebuild preview is ready; nothing changed." : "Stats rebuilt.")); return; }
            if (control.dataset.action === "close-lobby") { const values = await askAction({ title: tr("admin.lobbies.close", "Close lobby"), description: tr("admin.lobbies.closeCopy", "This closes the lobby for everyone and cannot be undone."), confirmLabel: tr("admin.lobbies.close", "Close lobby"), danger: true, fields: [reasonField()] }); if (!values) return; await request(`/lobbies/${control.dataset.value}${query({ reason: values.reason })}`, { method: "DELETE" }); await loadLobbies(); }
            if (control.dataset.action === "extend-lobby") { const values = await askAction({ title: tr("admin.lobbies.extend", "Extend lobby"), description: tr("admin.lobbies.extendCopy", "Add time to this lobby."), confirmLabel: tr("admin.lobbies.extend", "Extend"), fields: [{ name: "seconds", label: tr("admin.lobbies.seconds", "Seconds"), type: "number", value: "300", min: 1, required: true }, reasonField()] }); if (!values) return; const seconds = Number(values.seconds); if (!Number.isInteger(seconds) || seconds < 1) throw new Error(tr("admin.lobbies.secondsError", "Enter a whole number of seconds greater than zero.")); await request(`/lobbies/${control.dataset.value}/extend`, { method: "POST", body: JSON.stringify({ seconds, reason: values.reason }) }); await loadLobbies(); }
            if (control.dataset.action === "rename-game") { const game = await request(`/game-records/${control.dataset.value}`); const values = await askAction({ title: tr("admin.games.renameTitle", "Rename recorded game"), description: tr("admin.games.renameCopy", "Choose a clear name for this history entry."), confirmLabel: tr("admin.games.rename", "Rename"), fields: [{ name: "name", label: tr("admin.common.name", "Name"), value: game.name || "", required: true, maxLength: 100 }, reasonField()] }); if (!values) return; await request(`/game-records/${control.dataset.value}`, { method: "PATCH", body: JSON.stringify({ name: values.name.trim(), reason: values.reason, dryRun: false }) }); await loadGames(); }
            if (control.dataset.action === "delete-game") {
                const values = await askAction({ title: tr("admin.games.deleteTitle", "Delete recorded game"), description: tr("admin.games.deleteCopy", "Permanently delete this recorded game and its rounds. This does not recalculate user statistics."), confirmLabel: tr("admin.games.deleteTitle", "Delete recorded game"), danger: true, fields: [reasonField()] });
                if (!values) return; await request(`/game-records/${control.dataset.value}${query({ reason: values.reason })}`, { method: "DELETE" });
                await (page === "database" ? loadDatabaseTable() : loadGames()); setStatus(tr("admin.games.deleted", "Recorded game deleted.")); return;
            }
            if (control.dataset.action === "delete-selected-games") {
                const ids = selectedIds(control.dataset.value); if (!ids.length) throw new Error(tr("admin.bulk.selectFirst", "Select at least one row first."));
                const values = await askAction({ title: tr("admin.games.bulkDeleteTitle", "Delete recorded games"), description: tr("admin.games.bulkDeleteCopy", `Permanently delete ${ids.length} games and their rounds. Statistics are not recalculated.`, ids.length), confirmLabel: tr("admin.bulk.deleteCount", `Delete ${ids.length}`, ids.length), danger: true, fields: [reasonField()] });
                if (!values) return; for (const id of ids) await request(`/game-records/${id}${query({ reason: values.reason })}`, { method: "DELETE" });
                await loadDatabaseTable(); setStatus(tr("admin.bulk.deletedCount", `Deleted ${ids.length} records.`, ids.length)); return;
            }
            if (control.dataset.action === "toggle-game") {
                const [game, mode, wanted] = control.dataset.value.split("|"); const enabled = wanted === "enable"; const rule = `${game}${mode ? ` ${mode}` : ""}`;
                const values = await askAction({ title: enabled ? tr("admin.availability.enableTitle", "Enable game") : tr("admin.availability.disableTitle", "Disable game"), description: enabled ? tr("admin.availability.enableCopy", `Enable ${rule} for players?`, rule) : tr("admin.availability.disableCopy", `Disable ${rule} for players?`, rule), confirmLabel: enabled ? tr("admin.availability.enable", "Enable") : tr("admin.availability.disable", "Disable"), danger: !enabled, fields: [reasonField()] });
                if (!values) return; await request(`/games/${encodeURIComponent(game)}`, { method: "PATCH", body: JSON.stringify({ mode: mode || null, enabled, reason: values.reason }) }); await refreshAvailabilityContext();
            }
        } catch (error) { setStatus(error.message, true); }
    });
    document.querySelector("#admin-notification-form")?.addEventListener("submit", async event => { event.preventDefault(); const values = formValues(event.currentTarget); const recipient = values.userId ? `/users/${values.userId}` : "/all"; if (!await confirmAction(tr("admin.notify.send", "Send notification"), values.userId ? tr("admin.notifications.sendOneCopy", `Send this notification to user #${values.userId}?`, values.userId) : tr("admin.notifications.sendAllCopy", "Send this notification to every user?"), !values.userId)) return; try { await request(`/notifications${recipient}`, { method: "POST", body: JSON.stringify({ message: values.message, reason: values.reason }) }); event.currentTarget.reset(); setStatus(tr("admin.notifications.sent", "Notification sent.")); } catch (error) { setStatus(error.message, true); } });
    populateModes();

    document.querySelectorAll("form.admin-filters[data-filters]").forEach(form => {
        for (const [key, value] of params) { const field = form.elements[key]; if (field && !(field instanceof RadioNodeList) && field.tagName !== "FIELDSET") field.value = value; }
    });
    if (page === "notifications" && params.get("userId")) document.querySelector("#admin-notification-form").elements.userId.value = params.get("userId");
    const statsUser = params.get("userId");
    if (page === "stats" && statsUser) { document.querySelector("#admin-stats-lookup").elements.userQuery.value = statsUser; loadStats(statsUser).then(() => setStatus(tr("admin.updated", `Updated ${new Date().toLocaleTimeString()}.`, new Date().toLocaleTimeString()))).catch(error => setStatus(error.message, true)); } else refresh();
})();
