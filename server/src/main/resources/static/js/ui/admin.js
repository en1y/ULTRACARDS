(() => {
    const api = "/api/admin/v1";
    const page = document.body.dataset.adminPage;
    const params = new URLSearchParams(window.location.search);
    const state = {
        currentUser: null,
        currentLobby: null,
        notifications: new Map(),
        pointEvents: new Map(),
        stats: null,
        userDetailId: page === "users" ? params.get("id") : null,
        dbTable: page === "database" ? params.get("table") : null,
        dbFilters: {}
    };
    let goalDescriptionId = 0;
    if (page === "database") for (const [key, value] of params) if (key !== "table") state.dbFilters[key] = value;
    const durakModes = [];
    for (let players = 2; players <= 6; players++) {
        for (const deck of [24, 36, 54]) {
            if (deck === 24 && players > 4) continue;
            for (const jokers of deck === 54 ? ["NO_JOKERS", "JOKERS"] : ["NO_JOKERS"])
                for (const throwers of ["NEIGHBORS", "EVERYONE"])
                    for (const passing of ["NO_PASS", "PASS"])
                        durakModes.push(`P${players}_D${deck}_${jokers}_${throwers}_${passing}`);
        }
    }
    const modes = {
        briskula: ["TWO_PLAYERS", "TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH", "THREE_PLAYERS", "FOUR_PLAYERS_NO_TEAMS", "FOUR_PLAYERS_WITH_TEAMS"],
        durak: durakModes,
        treseta: ["TWO_PLAYERS", "THREE_PLAYERS", "FOUR_PLAYERS_WITH_TEAMS", "FOUR_PLAYERS_NO_TEAMS",
            "TWO_PLAYERS_WITH_DECLARATIONS", "THREE_PLAYERS_WITH_DECLARATIONS",
            "FOUR_PLAYERS_WITH_TEAMS_WITH_DECLARATIONS", "FOUR_PLAYERS_NO_TEAMS_WITH_DECLARATIONS"]
    };
    const status = document.querySelector("#admin-status");
    const tr = (key, fallback, ...args) => typeof window.t === "function" && (window.__I18N__ || {})[key] ? window.t(key, ...args) : fallback;
    const modeLabel = (gameType, mode) => mode && typeof getGameConfigDisplayName === "function"
        ? getGameConfigDisplayName(gameType || (modes.durak.includes(mode) ? "durak" : ""), mode)
        : mode || "";
    const setStatus = (message, error = false) => { status.textContent = message; status.classList.toggle("is-error", error); };
    const request = async (path, options = {}) => {
        const response = await fetch(path.startsWith("/api/") ? path : `${api}${path}`, { headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options });
        const text = await response.text();
        if (response.ok) return text ? JSON.parse(text) : null;
        let message = `Request failed (${response.status})`;
        try { message = JSON.parse(text).message || message; } catch { /* non-JSON error body */ }
        throw new Error(message);
    };
    const element = (tag, text, className) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (className) node.className = className; return node; };
    // HTML comes only from MarkdownRenderer after raw HTML escaping and OWASP sanitization.
    const renderTrustedMarkdown = (target, html) => { target.innerHTML = html || ""; };
    const link = (href, text, className) => { const node = element("a", text, className); node.href = href; return node; };
    const userHref = id => `/admin/users?id=${encodeURIComponent(id)}`;
    const userLink = (id, label) => link(userHref(id), label ?? `${tr("admin.common.user", "User")} #${id}`, "admin-link");
    const tokenHref = id => `/admin/database?table=tokens&id=${encodeURIComponent(id)}`;
    const playerLink = name => { const match = /\(#(\d+)\)\s*$/.exec(name); return link(match ? userHref(match[1]) : `/admin/users?query=${encodeURIComponent(name)}&exact=true`, name, "admin-link"); };
    const playerLinks = names => names.flatMap((name, index) => index ? [", ", playerLink(name)] : [playerLink(name)]);
    const chip = (text, className) => element("span", text, `admin-chip${className ? ` ${className}` : ""}`);
    const statusClass = value => ({ ACTIVE: "is-good", DISABLED: "is-warn", DELETED: "is-bad" })[value] || "";
    // dd.mm.yyyy and 24-hour, per AGENTS.md — locale formats drift to am/pm.
    const pad = number => String(number).padStart(2, "0");
    const formatClock = date => `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    const formatTime = value => {
        if (!value) return "—";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "—";
        return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()} ${formatClock(date)}`;
    };
    const formDateTime = value => value ? formatTime(value) : "";
    const parseDateTime = value => {
        const match = /^(\d{2})\.(\d{2})\.(\d{4}) (\d{2}):(\d{2})$/.exec(value.trim());
        if (!match) throw new Error(tr("admin.dateTime.invalid", "Use dd.mm.yyyy HH:mm with 24-hour time."));
        const [, day, month, year, hour, minute] = match.map(Number);
        const date = new Date(year, month - 1, day, hour, minute);
        if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day
            || date.getHours() !== hour || date.getMinutes() !== minute)
            throw new Error(tr("admin.dateTime.invalid", "Use dd.mm.yyyy HH:mm with 24-hour time."));
        return date;
    };
    // The picker in js/ui/fragments/date-time.js owns the widget; this only pushes a programmatic
    // hidden-input change back into the visible field.
    const syncDateTimeWidgets = scope => window.UltracardsDateTime?.sync(scope);
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
    const row = (name, title, meta, actions = [], chips = []) => {
        const node = element("article", null, "admin-row");
        const details = element("div");
        const headRow = element("div", null, "admin-row-head");
        const heading = element("h3"); content(heading, title);
        headRow.append(heading);
        if (chips.length) { const badges = element("div", null, "admin-chips"); badges.append(...chips); headRow.append(badges); }
        const metaLine = element("p", null, "admin-meta"); content(metaLine, meta);
        details.append(headRow, metaLine); node.append(details);
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
            if (field.max != null) input.max = field.max;
            if (field.step != null) input.step = field.step;
            if (field.maxLength != null) input.maxLength = field.maxLength;
            input.required = Boolean(field.required); label.append(input); return label;
        }));
        const finish = result => { form.removeEventListener("submit", submit); form.removeEventListener("keydown", submitOnEnter); dialog.removeEventListener("close", close); resolve(result); };
        const submit = event => { const values = formValues(form); finish(event.submitter?.value === "confirm" ? values : null); };
        const submitOnEnter = event => {
            const target = event.target;
            if (!(target instanceof HTMLElement) || event.key !== "Enter" || event.shiftKey || target.matches("textarea")) return;
            event.preventDefault();
            if (form.reportValidity()) confirmButton.click();
        };
        const close = () => finish(null);
        form.addEventListener("submit", submit); form.addEventListener("keydown", submitOnEnter); dialog.addEventListener("close", close, { once: true }); dialog.showModal();
    });
    const confirmAction = (title, description, danger = false) => askAction({ title, description, confirmLabel: danger ? tr("admin.common.confirmChange", "Confirm change") : tr("admin.common.continue", "Continue"), danger });
    const reasonField = () => ({ name: "reason", label: tr("admin.common.reason", "Reason"), required: true, maxLength: 250 });
    const modeOption = (gameType, value) => {
        const option = element("option", modeLabel(gameType, value));
        option.value = value;
        return option;
    };
    const populateModes = () => {
        const form = document.querySelector("#admin-stats-edit");
        if (!form) return;
        const mode = form.elements.mode;
        const selected = mode.value;
        mode.replaceChildren(...modes[form.elements.gameType.value].map(value => modeOption(form.elements.gameType.value, value)));
        if (modes[form.elements.gameType.value].includes(selected)) mode.value = selected;
    };

    const loadOverview = async () => {
        const [overview, system, db] = await Promise.all([request("/reports/overview"), request("/system/status"), request("/reports/database").catch(() => null)]);
        const container = document.querySelector("#admin-overview"); container.replaceChildren();
        const heroes = element("div", null, "admin-metrics-row");
        const hero = (label, value, href, variant) => {
            const node = link(href, null, `admin-hero admin-hero-${variant}`);
            const output = element("strong");
            content(output, value);
            node.append(element("span", label, "admin-hero-label"), output);
            heroes.append(node);
            return node;
        };
        hero(tr("admin.overview.users", "Registered players"), overview.users, "/admin/users", "a");
        hero(tr("admin.overview.online", "Playing right now"), overview.onlineUsers, "/admin/sessions?valid=true", "b");
        hero(tr("admin.overview.points", "Points in circulation"), pointsCompactNode(overview.totalPoints), "/admin/points", "d");
        const activeToday = overview.onlineUsersToday ?? 0;
        const percent = overview.users ? Math.round(activeToday / overview.users * 100) : 0;
        const activeHero = link("/admin/sessions", null, "admin-hero admin-hero-c");
        const ring = element("span", null, "admin-ring");
        ring.style.setProperty("--ring", percent);
        ring.append(element("strong", `${percent}%`));
        const activeCopy = element("span", null, "admin-hero-ring-copy");
        activeCopy.append(element("span", tr("admin.overview.activeToday", "Active today"), "admin-hero-label"), element("strong", `${activeToday} / ${overview.users}`));
        activeHero.append(activeCopy, ring);
        heroes.append(activeHero);
        container.append(heroes);
        const counts = db?.recordsByArea || {};
        // Tiles are grouped under the same headings the sidebar uses, strictly by the page each one opens:
        // a Points number never sends you to Users, and the raw table counts sit under System because the
        // only place they exist is the database browser.
        let tiles;
        const group = title => {
            const section = element("section", null, "admin-overview-group");
            tiles = element("div", null, "admin-tiles");
            section.append(element("h3", title, "admin-overview-group-title"), tiles);
            container.append(section);
        };
        const tile = (label, value, href, state) => {
            const node = link(href, null, "admin-tile");
            const output = element("strong", null, state === undefined ? "" : state ? "is-good" : "is-bad");
            content(output, value);
            node.append(output, element("span", label));
            tiles.append(node);
        };
        group(tr("admin.tab.points", "Points"));
        tile(tr("admin.overview.pointsDelta", "Points change · 7 days"), pointsDeltaNode(overview.pointsChangeLast7Days), "/admin/points");
        tile(tr("admin.overview.pointsMinted", "Points minted · 7 days"), pointsCompactNode(overview.pointsMintedLast7Days), "/admin/points");
        tile(tr("admin.overview.pointsRaked", "Bet fees · 7 days"), pointsCompactNode(overview.pointsRakedLast7Days), "/admin/points");
        tile(tr("admin.overview.pointsEscrowed", "Points in escrow"), pointsCompactNode(overview.pointsEscrowed), "/admin/points");
        tile(tr("admin.overview.dailyClaims", "Daily claims today"), Number(overview.dailyClaimsToday || 0).toLocaleString(), "/admin/points");
        group(tr("admin.nav.people", "People"));
        tile(tr("admin.overview.validSessions", "Enabled sessions"), overview.validSessions, "/admin/sessions?valid=true");
        group(tr("admin.nav.live", "Live activity"));
        tile(tr("admin.overview.liveLobbies", "Live lobbies"), system.activeLobbies, "/admin/lobbies");
        tile(tr("admin.overview.liveGames", "Live games"), system.activeGames, "/admin/games");
        if (counts["Recorded games"] != null) tile(tr("admin.db.table.games", "Recorded games"), counts["Recorded games"], "/admin/games");
        group(tr("admin.nav.system", "System"));
        if (counts["Sessions"] != null) tile(tr("admin.db.table.sessions", "Sessions"), counts["Sessions"], "/admin/database?table=sessions");
        if (counts["Tokens"] != null) tile(tr("admin.db.table.tokens", "Tokens"), counts["Tokens"], "/admin/database?table=tokens");
        if (counts["Notifications"] != null) tile(tr("admin.db.table.notifications", "Notifications"), counts["Notifications"], "/admin/database?table=notifications");
        if (counts["Admin audit events"] != null) tile(tr("admin.db.table.audit", "Audit events"), counts["Admin audit events"], "/admin/audit");
        tile(`${tr("admin.overview.database", "Database")}${db?.flywayVersion ? ` · Flyway ${db.flywayVersion}` : ""}`,
            system.databaseAvailable ? tr("admin.overview.available", "Available") : tr("admin.overview.unavailable", "Unavailable"),
            "/admin/database", system.databaseAvailable);
        const breakdown = document.querySelector("#admin-overview-breakdown"); breakdown.replaceChildren();
        const seriesColor = index => `var(--admin-c${index % 5})`;
        const card = title => { const node = element("article", null, "admin-breakdown"); node.append(element("h3", title)); breakdown.append(node); return node; };
        const legend = (entries, href) => {
            const chips = element("div", null, "admin-chips");
            entries.forEach(([key, count], index) => {
                const item = link(href(key), null, "admin-chip admin-chip-link");
                const dot = element("span", null, "admin-dot"); dot.style.background = seriesColor(index);
                item.append(dot, document.createTextNode(`${key} · ${count}`));
                chips.append(item);
            });
            return chips;
        };
        const donutCard = (title, values, href) => {
            const node = card(title);
            const entries = Object.entries(values || {});
            if (!entries.length) return node.append(element("p", tr("admin.overview.noData", "No data yet."), "admin-empty"));
            const total = entries.reduce((sum, [, count]) => sum + count, 0);
            const donut = element("div", null, "admin-donut");
            let angle = 0;
            donut.style.background = total ? `conic-gradient(${entries.map(([, count], index) => {
                const start = angle; angle += count / total * 360;
                return `${seriesColor(index)} ${start}deg ${angle}deg`;
            }).join(", ")})` : "var(--color-surface-2)";
            const hole = element("span", null, "admin-donut-hole");
            hole.append(element("strong", String(total)), element("span", tr("admin.overview.total", "Total")));
            donut.append(hole);
            const chart = element("div", null, "admin-chart-row");
            chart.append(donut, legend(entries, href));
            node.append(chart);
        };
        const barsCard = (title, values, href) => {
            const node = card(title);
            const entries = Object.entries(values || {});
            if (!entries.length) return node.append(element("p", tr("admin.overview.noData", "No data yet."), "admin-empty"));
            const max = Math.max(...entries.map(([, count]) => count), 1);
            const bars = element("div", null, "admin-bars");
            entries.forEach(([key, count]) => {
                const column = link(href(key), null, "admin-bar-col");
                column.title = `${key} · ${count}`;
                const bar = element("span", null, "admin-bar");
                bar.style.height = `${Math.max(6, Math.round(count / max * 78))}px`;
                column.append(element("strong", String(count)), bar, element("span", key, "admin-bar-label"));
                bars.append(column);
            });
            node.append(bars);
        };
        donutCard(tr("admin.overview.byStatus", "Users by status"), overview.usersByStatus, key => `/admin/users?status=${key}`);
        donutCard(tr("admin.overview.byRole", "Users by role"), overview.usersByRole, key => `/admin/users?role=${key}`);
        barsCard(tr("admin.overview.completed", "Recorded games"), overview.completedGames, key => `/admin/games?gameType=${key}&completed=true`);
    };

    const syncEventAchievementEditors = () => {
        const container = document.querySelector("#admin-event-achievements");
        if (!container) return;
        const achievements = [...container.querySelectorAll(":scope > .admin-event-achievement")];
        const emptyState = document.querySelector("#admin-event-achievements-empty");
        if (emptyState) emptyState.hidden = achievements.length > 0;
        achievements.forEach((achievement, index) => {
            achievement.querySelector(":scope > legend").textContent = `${tr("admin.economy.achievement", "Goal")} ${index + 1}`;
            const remove = achievement.querySelector(".admin-event-achievement-head .btn");
            remove.disabled = achievements.length === 1;
            remove.title = achievements.length === 1
                ? tr("admin.economy.oneAchievementRequired", "Every event needs at least one goal.") : "";
        });
    };

    const selectedEventGameTypes = () => {
        const boxes = [...document.querySelectorAll('#admin-event-form .admin-event-games input[name="gameTypes"]')];
        const selected = boxes.filter(box => box.checked).map(box => box.value);
        return selected.length ? selected : boxes.map(box => box.value);
    };

    const toggleField = (name, value, label, checked, hint = null) => {
        const wrapper = element("label", null, "uc-toggle");
        const input = document.createElement("input");
        input.type = "checkbox"; input.name = name; input.value = value; input.checked = checked;
        const control = element("span", null, "visibility-switch"); control.setAttribute("aria-hidden", "true");
        const copy = element("span", null, "uc-toggle-copy");
        copy.append(element("span", label, "uc-toggle-label"));
        if (hint) copy.append(element("span", hint, "uc-toggle-hint"));
        wrapper.append(input, control, copy);
        return wrapper;
    };

    const renderAchievementGameTypes = (node, selected = null) => {
        const selectedTypes = selected || new Set([...node.querySelectorAll('[name="gameTypes"]:checked')].map(box => box.value));
        const list = node.querySelector(".admin-event-goal-game-list");
        list.replaceChildren(...selectedEventGameTypes().map(game =>
            toggleField("gameTypes", game, game[0] + game.slice(1).toLowerCase(), selectedTypes.has(game))));
    };

    const achievementGameTypes = node => [...node.querySelectorAll('.admin-event-goal-game-list [name="gameTypes"]:checked')]
        .map(box => box.value);

    const renderDescriptionPreview = async (description, preview) => {
        if (!preview) return;
        if (!description) {
            preview.replaceChildren(element("p", tr("admin.economy.descriptionPreviewEmpty", "Nothing to preview yet."), "admin-empty"));
            return;
        }
        preview.setAttribute("aria-busy", "true");
        try {
            const result = await request("/economy/events/preview", {
                method: "POST", body: JSON.stringify({ description })
            });
            renderTrustedMarkdown(preview, result.html);
        } catch (error) {
            preview.replaceChildren(element("p", tr("admin.economy.descriptionPreviewError", "Preview could not be loaded."), "admin-empty is-error"));
        } finally {
            preview.removeAttribute("aria-busy");
        }
    };

    const renderEventDescriptionPreview = () => renderDescriptionPreview(
        document.querySelector('#admin-event-form [name="description"]')?.value.trim(),
        document.querySelector("#admin-event-description-preview"));

    const moveMarkdownTabSelection = event => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        const tabs = [...event.currentTarget.querySelectorAll('[role="tab"]')];
        const current = tabs.indexOf(document.activeElement);
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
            : (current + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
        event.preventDefault();
        tabs[next].click();
        tabs[next].focus();
    };

    const setEventDescriptionMode = mode => {
        const edit = document.querySelector("#admin-event-description-edit");
        const preview = document.querySelector("#admin-event-description-preview-panel");
        if (!edit || !preview) return;
        const showPreview = mode === "preview";
        edit.hidden = showPreview;
        preview.hidden = !showPreview;
        document.querySelectorAll("[data-event-description-mode]").forEach(tab => {
            const selected = tab.dataset.eventDescriptionMode === mode;
            tab.setAttribute("aria-selected", String(selected));
            tab.tabIndex = selected ? 0 : -1;
        });
        if (showPreview) renderEventDescriptionPreview();
    };

    const durakFilterFromModes = gameModes => {
        const configs = (gameModes || []).map(mode => parseDurakModeKey(mode)).filter(Boolean);
        if (!configs.length) return {};
        const common = property => {
            const values = new Set(configs.map(config => String(config[property])));
            return values.size === 1 ? values.values().next().value : "";
        };
        return {
            players: common("numberOfPlayers"), deck: common("deckSize"),
            throwin: common("throwInPolicy"), jokers: common("jokersEnabled"),
            passing: common("passingEnabled")
        };
    };

    const gameModeSummary = (gameType, gameModes) => {
        if (!gameModes?.length) return "";
        if (gameType === "DURAK") return describeDurakFilter(durakFilterFromModes(gameModes));
        if (gameModes.length === 1) return modeLabel(gameType, gameModes[0]);
        return tr("admin.economy.modeCount", `${gameModes.length} exact modes`, gameModes.length);
    };

    const achievementGameModes = node => {
        const type = achievementGameTypes(node)[0];
        const editor = node.querySelector(".admin-event-goal-mode-editor");
        if (!type || !editor || editor.hidden) return [];
        if (type === "DURAK") {
            const filter = readDurakFilter(node.dataset.modeEditorId);
            if (durakFilterIsEmpty(filter)) return [];
            return durakAllModeKeys().filter(mode => durakConfigMatchesFilter(mode, filter));
        }
        const mode = editor.querySelector("select")?.value;
        return mode ? [mode] : [];
    };

    let achievementModeEditorId = 0;
    const renderAchievementModes = (node, selectedModes = null) => {
        const previous = selectedModes || achievementGameModes(node);
        const gameTypes = achievementGameTypes(node);
        const editor = node.querySelector(".admin-event-goal-mode-editor");
        const note = node.querySelector(".admin-event-goal-mode-note");
        editor.replaceChildren();
        const exactGame = gameTypes.length === 1 ? gameTypes[0] : null;
        const available = exactGame ? modes[exactGame.toLowerCase()] || [] : [];
        editor.hidden = !exactGame || !available.length;
        note.hidden = !editor.hidden;
        if (editor.hidden) return;
        if (exactGame === "DURAK") {
            node.dataset.modeEditorId ||= `admin-event-durak-${++achievementModeEditorId}`;
            renderDurakFilter(editor, node.dataset.modeEditorId, durakFilterFromModes(previous));
            return;
        }
        const label = element("label", null, "admin-event-mode-select");
        label.append(element("span", tr("admin.economy.exactMode", "Exact mode")));
        const select = document.createElement("select");
        select.name = "gameMode";
        const any = element("option", tr("admin.economy.anyMode", "Any mode")); any.value = "";
        select.append(any, ...available.map(mode => {
            const option = element("option", modeLabel(exactGame, mode)); option.value = mode; return option;
        }));
        select.value = previous.length === 1 ? previous[0] : "";
        label.append(select); editor.append(label);
    };

    const eventAchievementEditor = (achievement = {}) => {
        const node = element("fieldset", null, "admin-event-achievement");
        if (achievement.id) node.dataset.id = achievement.id;
        node.append(element("legend", tr("admin.economy.achievement", "Goal")));
        const head = element("div", null, "admin-event-achievement-head");
        const remove = element("button", tr("admin.common.delete", "Delete"), "btn btn-danger");
        remove.type = "button";
        remove.addEventListener("click", () => { node.remove(); syncEventAchievementEditors(); });
        head.append(remove);
        node.append(head);
        const field = (name, label, type = "number", value = 0) => {
            const wrapper = element("label");
            wrapper.append(element("span", label));
            const input = document.createElement("input");
            input.name = name; input.type = type; input.value = value ?? "";
            if (type === "number") { input.min = "0"; input.step = "1"; input.inputMode = "numeric"; }
            if (name === "goalName") { input.required = true; input.maxLength = 120; }
            wrapper.append(input);
            return wrapper;
        };
        // Goal fields must not reuse the event's own field names: two controls sharing a name inside
        // one form makes form.elements[name] a RadioNodeList whose .value is always "".
        const identity = element("div", null, "admin-event-achievement-fields");
        const name = field("goalName", tr("admin.common.name", "Name"), "text", achievement.name || "");
        name.classList.add("admin-event-goal-name"); identity.append(name);
        const description = element("div", null, "admin-markdown-editor admin-goal-markdown-editor");
        const descriptionPrefix = `admin-goal-description-${++goalDescriptionId}`;
        const descriptionTabs = element("div", null, "admin-markdown-tabs");
        descriptionTabs.setAttribute("role", "tablist");
        descriptionTabs.setAttribute("aria-label", tr("admin.economy.descriptionMode", "Description mode"));
        const editTab = element("button", tr("admin.economy.descriptionEdit", "Edit"), "btn");
        editTab.type = "button"; editTab.id = `${descriptionPrefix}-edit-tab`; editTab.setAttribute("role", "tab");
        editTab.setAttribute("aria-selected", "true"); editTab.setAttribute("aria-controls", `${descriptionPrefix}-edit`);
        const previewTab = element("button", tr("admin.economy.descriptionPreview", "Preview"), "btn");
        previewTab.type = "button"; previewTab.id = `${descriptionPrefix}-preview-tab`; previewTab.setAttribute("role", "tab");
        previewTab.setAttribute("aria-selected", "false"); previewTab.setAttribute("aria-controls", `${descriptionPrefix}-preview`);
        previewTab.tabIndex = -1; descriptionTabs.append(editTab, previewTab);
        const editPanel = element("div"); editPanel.id = `${descriptionPrefix}-edit`; editPanel.setAttribute("role", "tabpanel");
        editPanel.setAttribute("aria-labelledby", editTab.id);
        const descriptionLabel = element("label"); descriptionLabel.append(element("span", tr("admin.economy.description", "Description")));
        const descriptionInput = document.createElement("textarea");
        descriptionInput.name = "goalDescription"; descriptionInput.maxLength = 500; descriptionInput.rows = 3;
        descriptionInput.value = achievement.description || ""; descriptionInput.setAttribute("aria-describedby", `${descriptionPrefix}-hint`);
        const descriptionHint = element("small", tr("admin.economy.descriptionMarkdownHint", "Markdown is supported."), "admin-hint");
        descriptionHint.id = `${descriptionPrefix}-hint`; descriptionLabel.append(descriptionInput, descriptionHint); editPanel.append(descriptionLabel);
        const previewPanel = element("div"); previewPanel.id = `${descriptionPrefix}-preview`; previewPanel.setAttribute("role", "tabpanel");
        previewPanel.setAttribute("aria-labelledby", previewTab.id); previewPanel.hidden = true;
        const preview = element("div", null, "admin-markdown-preview markdown-body"); preview.setAttribute("aria-live", "polite");
        previewPanel.append(preview);
        const setDescriptionMode = mode => {
            const showPreview = mode === "preview";
            editPanel.hidden = showPreview; previewPanel.hidden = !showPreview;
            editTab.setAttribute("aria-selected", String(!showPreview)); editTab.tabIndex = showPreview ? -1 : 0;
            previewTab.setAttribute("aria-selected", String(showPreview)); previewTab.tabIndex = showPreview ? 0 : -1;
            if (showPreview) renderDescriptionPreview(descriptionInput.value.trim(), preview);
        };
        editTab.addEventListener("click", () => setDescriptionMode("edit"));
        previewTab.addEventListener("click", () => setDescriptionMode("preview"));
        descriptionTabs.addEventListener("keydown", moveMarkdownTabSelection);
        description.append(descriptionTabs, editPanel, previewPanel); identity.append(description); node.append(identity);
        const targets = element("div", null, "admin-event-targets");
        const requirements = element("div", null, "admin-event-requirements");
        requirements.append(
            field("gamesRequired", tr("admin.economy.gamesRequired", "Games"), "number", achievement.gamesRequired),
            field("winsRequired", tr("admin.economy.winsRequired", "Wins"), "number", achievement.winsRequired),
            field("lossesRequired", tr("admin.economy.lossesRequired", "Losses"), "number", achievement.lossesRequired),
            field("drawsRequired", tr("admin.economy.drawsRequired", "Draws"), "number", achievement.drawsRequired));
        const reward = element("label", null, "admin-event-reward");
        reward.append(element("span", tr("admin.economy.reward", "Reward (P)").replace(/\s*\(P\)\s*$/, "")));
        const pointsInput = element("span", null, "admin-points-input");
        const rewardInput = document.createElement("input");
        rewardInput.name = "rewardPoints"; rewardInput.type = "number"; rewardInput.min = "0"; rewardInput.step = "1";
        rewardInput.inputMode = "numeric"; rewardInput.value = achievement.rewardPoints ?? 0;
        pointsInput.append(rewardInput, element("span", "P", "points-symbol")); reward.append(pointsInput);
        targets.append(requirements, reward);
        node.append(targets);
        const options = element("div", null, "admin-event-achievement-options");
        const games = element("fieldset", null, "admin-event-goal-games");
        games.append(element("legend", tr("admin.economy.goalGames", "Goal games")),
            element("p", tr("admin.economy.goalGamesHint", "Leave every game off to inherit all games allowed by the event."), "admin-hint"),
            element("div", null, "admin-event-goal-game-list"));
        options.append(games, toggleField("hidden", "true", tr("admin.economy.hiddenGoal", "Hidden goal"), achievement.hidden === true,
            tr("admin.economy.hiddenGoalHint", "Players see the task only after completing it.")));
        const mode = element("fieldset", null, "admin-event-goal-mode");
        mode.append(element("legend", tr("admin.economy.gameSetup", "Game setup")),
            element("p", tr("admin.economy.gameSetupHint", "Choose exactly one goal game to require a specific mode or Durak setup."), "admin-hint admin-event-goal-mode-note"),
            element("div", null, "admin-event-goal-mode-editor"));
        node.append(options, mode, element("p", tr("admin.economy.requirementsHint",
            "Totals counted across the whole event window, not per game. A target left at 0 is ignored."), "admin-hint"));
        renderAchievementGameTypes(node, new Set(achievement.gameTypes || []));
        renderAchievementModes(node, achievement.gameModes || []);
        node.addEventListener("change", event => {
            if (event.target.matches('.admin-event-goal-game-list [name="gameTypes"]')) renderAchievementModes(node);
        });
        return node;
    };

    const resetEventForm = event => {
        const form = document.querySelector("#admin-event-form");
        form.reset(); form.elements.id.value = event?.id || "";
        form.elements.name.value = event?.name || "";
        form.elements.description.value = event?.description || "";
        setEventDescriptionMode("edit");
        form.elements.startsAt.value = formDateTime(event?.startsAt || new Date());
        form.elements.endsAt.value = formDateTime(event?.endsAt);
        syncDateTimeWidgets(form);
        form.elements.completionRewardPoints.value = event?.completionRewardPoints || 0;
        form.elements.enabled.checked = event?.enabled ?? true;
        const selected = new Set(event?.gameTypes || []);
        form.querySelectorAll('.admin-event-games input[name="gameTypes"]').forEach(box => { box.checked = selected.has(box.value); });
        const achievements = event?.achievements?.length ? event.achievements : [{}];
        document.querySelector("#admin-event-achievements").replaceChildren(...achievements.map(eventAchievementEditor));
        syncEventAchievementEditors();
        document.querySelector("#admin-event-editor-title").textContent = event
            ? tr("admin.economy.editEvent", `Edit ${event.name}`, event.name)
            : tr("admin.economy.newEvent", "New event");
        document.querySelector("#admin-event-submit").textContent = event
            ? tr("admin.economy.updateEvent", "Update event")
            : tr("admin.economy.createEvent", "Create event");
        const status = document.querySelector("#admin-event-status");
        status.hidden = true;
        status.classList.remove("is-error");
    };

    const eventPatch = form => ({
        name: form.elements.name.value.trim(),
        description: form.elements.description.value.trim(),
        startsAt: parseDateTime(form.elements.startsAt.value).toISOString(),
        endsAt: form.elements.endsAt.value ? parseDateTime(form.elements.endsAt.value).toISOString() : null,
        gameTypes: [...form.querySelectorAll('.admin-event-games input[name="gameTypes"]:checked')].map(box => box.value),
        completionRewardPoints: Number(form.elements.completionRewardPoints.value || 0),
        enabled: form.elements.enabled.checked,
        achievements: [...document.querySelectorAll("#admin-event-achievements > .admin-event-achievement")].map(node => ({
            id: node.dataset.id || null,
            name: node.querySelector('[name="goalName"]').value.trim(),
            description: node.querySelector('[name="goalDescription"]').value.trim(),
            gamesRequired: Number(node.querySelector('[name="gamesRequired"]').value || 0),
            winsRequired: Number(node.querySelector('[name="winsRequired"]').value || 0),
            lossesRequired: Number(node.querySelector('[name="lossesRequired"]').value || 0),
            drawsRequired: Number(node.querySelector('[name="drawsRequired"]').value || 0),
            rewardPoints: Number(node.querySelector('[name="rewardPoints"]').value || 0),
            gameTypes: [...node.querySelectorAll('[name="gameTypes"]:checked')].map(box => box.value),
            gameModes: achievementGameModes(node),
            hidden: node.querySelector('[name="hidden"]').checked
        })),
        reason: form.elements.reason.value.trim()
    });

    const feeRule = (game, mode) => `${game}${mode ? ` · ${modeLabel(game, mode)}` : ""}`;
    const saveFee = async (game, mode, current) => {
        const rule = feeRule(game, mode);
        const values = await askAction({
            title: tr("admin.economy.feeRuleTitle", "Set bet fee"), description: tr("admin.economy.feeRuleCopy", `New ${rule} games will charge this fee.`, rule),
            confirmLabel: tr("admin.economy.saveFee", "Save fee"),
            fields: [{ name: "feePercent", label: tr("admin.economy.feePercent", "Fee percentage"), type: "number", value: current, min: 0, max: 100, step: 1, required: true }, reasonField()]
        });
        if (!values) return;
        await request(`/economy/fees/${encodeURIComponent(game)}`, { method: "PATCH", body: JSON.stringify({ mode: mode || null, feePercent: Number(values.feePercent), reason: values.reason }) });
        await loadFees();
        setStatus(tr("admin.economy.feeSaved", "Bet fee updated. New games will use it."));
    };
    const clearFee = async (game, mode) => {
        const rule = feeRule(game, mode);
        const values = await askAction({
            title: tr("admin.economy.feeResetTitle", "Reset bet fee"), description: tr("admin.economy.feeResetCopy", `Drop the ${rule} fee and inherit the wider one.`, rule),
            confirmLabel: tr("admin.availability.reset", "Reset"), fields: [reasonField()]
        });
        if (!values) return;
        await request(`/economy/fees/${encodeURIComponent(game)}${query({ mode, reason: values.reason })}`, { method: "DELETE" });
        await loadFees();
        setStatus(tr("admin.economy.feeResetDone", "Bet fee reset."));
    };
    const loadFees = async () => {
        const data = await request("/economy/fees"); clear("fees");
        if (!data.length) return empty("fees", tr("admin.economy.noFees", "No games are configured."));
        const games = new Map();
        data.forEach(item => {
            const group = games.get(item.game) || { game: null, modes: [] };
            if (item.mode) group.modes.push(item); else group.game = item;
            games.set(item.game, group);
        });
        const feeRow = (item, label) => {
            const node = element("article", null, "admin-row"); const details = element("div");
            const headRow = element("div", null, "admin-row-head");
            headRow.append(element("h3", label), chip(`${item.feePercent}%`, item.inherited ? "" : "is-good"));
            details.append(headRow, element("p", item.inherited
                ? tr("admin.economy.feeInherited", "Inherited") : tr("admin.economy.feeOwn", "Set here"), "admin-meta"));
            const controls = element("div", null, "admin-actions");
            controls.append(button(tr("admin.economy.setFee", "Set fee"), "set-fee", `${item.game}|${item.mode || ""}|${item.feePercent}`));
            if (!item.inherited) controls.append(button(tr("admin.availability.reset", "Reset"), "reset-fee", `${item.game}|${item.mode || ""}`));
            node.append(details, controls);
            return node;
        };
        [...games.entries()].sort(([left], [right]) => left.localeCompare(right)).forEach(([game, group]) => {
            const list = element("details", null, "admin-availability-group");
            const summary = element("summary");
            summary.append(element("strong", game), chip(`${group.game.feePercent}%`, group.game.inherited ? "" : "is-good"),
                element("span", tr("admin.availability.modes", `${group.modes.length} modes`, group.modes.length)));
            const modes = element("div", null, "admin-availability-modes");
            modes.append(feeRow(group.game, tr("admin.availability.entireGame", "Entire game")),
                ...group.modes.sort((left, right) => left.mode.localeCompare(right.mode)).map(item => feeRow(item, modeLabel(item.game, item.mode))));
            list.append(summary, modes); results("fees").append(list);
        });
    };

    const drawPointsDashboardChart = dashboard => {
        const series = (dashboard.dailyTotals || []).map(item => ({
            at: `${item.day}T12:00:00`, balance: item.balance, change: item.change, reason: ""
        }));
        window.renderPointsChart?.(document.querySelector("#admin-points-total-chart"), series, {
            days: 30,
            balance: dashboard.pointsInAccounts
        });
    };

    const renderPointsSettings = settings => {
        const form = document.querySelector("#admin-points-settings");
        form.elements.wagerFeePercent.value = settings.wagerFeePercent;
        form.elements.startingBalance.value = settings.startingBalance;
        form.elements.dailyReward.value = settings.dailyReward;
    };

    const renderPointsDashboard = dashboard => {
        const metrics = document.querySelector("#admin-points-metrics");
        metrics.replaceChildren();
        const pointValue = value => {
            const output = element("strong");
            output.append(document.createTextNode(Number(value || 0).toLocaleString()), element("span", " P", "points-symbol"));
            return output;
        };
        const metric = (label, value, delta = false) => {
            const card = element("article", null, "admin-tile");
            const output = delta ? element("strong") : pointValue(value);
            if (delta) output.append(pointsDeltaNode(value));
            card.append(output, element("span", label));
            metrics.append(card);
        };
        metric(tr("admin.pointsAdmin.circulation", "Points in circulation"), dashboard.pointsInCirculation);
        metric(tr("admin.pointsAdmin.accounts", "Points in accounts"), dashboard.pointsInAccounts);
        metric(tr("admin.pointsAdmin.escrow", "Points in escrow"), dashboard.pointsInEscrow);
        metric(tr("admin.pointsAdmin.change24h", "Change · 24 hours"), dashboard.changeLast24Hours, true);
        metric(tr("admin.pointsAdmin.change7d", "Change · 7 days"), dashboard.changeLast7Days, true);
        metric(tr("admin.pointsAdmin.change30d", "Change · 30 days"), dashboard.changeLast30Days, true);
        metric(tr("admin.pointsAdmin.minted", "Minted · all time"), dashboard.mintedAllTime);
        metric(tr("admin.pointsAdmin.removed", "Removed · all time"), dashboard.removedAllTime);
        metric(tr("admin.pointsAdmin.biggestWager", "Biggest stake"), dashboard.biggestWager);
        metric(tr("admin.pointsAdmin.staked", "Total staked"), dashboard.totalStaked);
        metric(tr("admin.pointsAdmin.paid", "Total paid out"), dashboard.totalPaidOut);
        metric(tr("admin.pointsAdmin.rake", "Bet fees · all time"), dashboard.totalRake);
        const countMetric = (label, value) => {
            const card = element("article", null, "admin-tile");
            card.append(element("strong", Number(value || 0).toLocaleString()), element("span", label));
            metrics.append(card);
        };
        countMetric(tr("admin.pointsAdmin.wagers", "Wagers"), dashboard.totalWagers);
        countMetric(tr("admin.pointsAdmin.openWagers", "Open wagers"), dashboard.openWagers);
        countMetric(tr("admin.pointsAdmin.claims", "Daily claims today"), dashboard.dailyClaimsToday);
        drawPointsDashboardChart(dashboard);
        const leaders = document.querySelector("#admin-points-leaderboard");
        leaders.replaceChildren();
        (dashboard.leaderboard || []).forEach(entry => {
            const row = element("a", null, "admin-points-leader-row");
            row.href = userHref(entry.userId);
            row.append(element("span", `#${entry.position}`, "admin-points-rank"),
                element("strong", entry.username || `${tr("admin.common.user", "User")} #${entry.userId}`));
            const amount = element("span", null, "admin-points-leader-value");
            amount.append(document.createTextNode(Number(entry.points).toLocaleString()), element("span", " P", "points-symbol"));
            row.append(amount); leaders.append(row);
        });
        if (!(dashboard.leaderboard || []).length)
            leaders.append(element("p", tr("admin.pointsAdmin.noLeaders", "No active accounts yet."), "admin-empty"));
    };

    const metricLabels = () => ({
        GAMES: tr("admin.economy.metricGames", "Games played"),
        WINS: tr("admin.economy.metricWins", "Wins"),
        LOSSES: tr("admin.economy.metricLosses", "Losses"),
        DRAWS: tr("admin.economy.metricDraws", "Draws")
    });

    const syncDailyGoalEditors = () => {
        const goals = [...document.querySelectorAll("#admin-daily-goals > .admin-daily-goal")];
        const emptyState = document.querySelector("#admin-daily-goals-empty");
        if (emptyState) emptyState.hidden = goals.length > 0;
        goals.forEach((goal, index) => {
            goal.querySelector(":scope > legend").textContent = `${tr("admin.economy.dailyGoal", "Goal")} ${index + 1}`;
        });
    };

    const dailyGoalEditor = (goal = {}) => {
        const node = element("fieldset", null, "admin-daily-goal");
        node.dataset.code = goal.code || "";
        node.append(element("legend", tr("admin.economy.dailyGoal", "Goal")));
        const head = element("div", null, "admin-event-achievement-head");
        const remove = element("button", tr("admin.common.delete", "Delete"), "btn btn-danger");
        remove.type = "button";
        remove.addEventListener("click", () => { node.remove(); syncDailyGoalEditors(); });
        head.append(remove);
        const fields = element("div", null, "admin-daily-goal-fields");
        const name = element("label", null, "admin-daily-goal-name");
        const nameInput = document.createElement("input");
        nameInput.name = "goalName"; nameInput.required = true; nameInput.maxLength = 120; nameInput.value = goal.name || "";
        name.append(element("span", tr("admin.common.name", "Name")), nameInput);
        const metric = element("label");
        const metricSelect = document.createElement("select");
        metricSelect.name = "metric";
        metricSelect.append(...Object.entries(metricLabels()).map(([value, label]) => {
            const option = element("option", label); option.value = value; return option;
        }));
        metricSelect.value = goal.metric || "GAMES";
        metric.append(element("span", tr("admin.economy.metric", "Counts")), metricSelect);
        const number = (field, label, value, min, suffix) => {
            const wrapper = element("label");
            wrapper.append(element("span", label));
            const input = document.createElement("input");
            input.name = field; input.type = "number"; input.min = String(min); input.step = "1";
            input.inputMode = "numeric"; input.required = true; input.value = value;
            if (!suffix) { wrapper.append(input); return wrapper; }
            const shell = element("span", null, "admin-points-input");
            shell.append(input, element("span", suffix, "points-symbol"));
            wrapper.append(shell);
            return wrapper;
        };
        fields.append(name, metric,
            number("target", tr("admin.economy.dailyTarget", "Target"), goal.target ?? 1, 1, null),
            number("rewardPoints", tr("admin.economy.reward", "Reward (P)").replace(/\s*\(P\)\s*$/, ""),
                goal.rewardPoints ?? 0, 0, "P"),
            toggleField("enabled", "true", tr("admin.economy.enabled", "Enabled"), goal.enabled !== false));
        node.append(head, fields);
        return node;
    };

    const renderDailyGoals = goals => {
        document.querySelector("#admin-daily-goals").replaceChildren(...goals.map(dailyGoalEditor));
        syncDailyGoalEditors();
    };

    const loadDailyGoals = async () => renderDailyGoals(await request("/economy/daily-goals"));

    const achievementMetricLabels = () => ({
        STREAK: tr("admin.achievements.metricStreak", "Days in a row"),
        GAMES: tr("admin.achievements.metricGames", "Games played (lifetime)"),
        WINS: tr("admin.achievements.metricWins", "Games won (lifetime)")
    });

    const applyAchievementView = () => {
        const rows = [...document.querySelectorAll("#admin-achievements > .admin-daily-goal")];
        const filter = (document.querySelector("#admin-achievement-filter")?.value || "").trim().toLowerCase();
        const metric = document.querySelector("#admin-achievement-metric")?.value || "";
        const stateFilter = document.querySelector("#admin-achievement-state")?.value || "";
        const sort = document.querySelector("#admin-achievement-sort")?.value || "configured";
        const value = (node, selector) => node.querySelector(selector)?.value || "";
        const visible = [];
        rows.forEach((node, index) => {
            const enabled = Boolean(node.querySelector('[name="enabled"]')?.checked);
            const searchText = `${value(node, '[name="achievementName"]')} ${node.dataset.code || ""}`.toLowerCase();
            const shown = (!filter || searchText.includes(filter))
                && (!metric || value(node, '[name="metric"]') === metric)
                && (!stateFilter || (stateFilter === "enabled") === enabled);
            node.hidden = !shown;
            node.dataset.configuredOrder = String(index);
            if (shown) visible.push(node);
        });
        const number = (node, name) => Number(value(node, `[name="${name}"]`)) || 0;
        const sorted = [...visible].sort((left, right) => {
            if (sort === "name") return value(left, '[name="achievementName"]').localeCompare(value(right, '[name="achievementName"]'));
            if (sort === "target") return number(left, "target") - number(right, "target");
            if (sort === "reward") return number(left, "rewardPoints") - number(right, "rewardPoints");
            return Number(left.dataset.configuredOrder) - Number(right.dataset.configuredOrder);
        });
        rows.forEach(node => { node.style.order = sort === "configured" ? "" : String(sorted.indexOf(node)); });
        rows.forEach((node, index) => node.querySelectorAll('[data-achievement-move]').forEach(button => {
            const boundary = button.dataset.achievementMove === "up" ? index === 0 : index === rows.length - 1;
            button.disabled = sort !== "configured" || boundary;
        }));
        const summary = document.querySelector("#admin-achievement-summary");
        if (summary) summary.textContent = tr("admin.achievements.shownSummary",
            `${visible.length} shown of ${rows.length}`, visible.length, rows.length);
    };

    const syncAchievementEditors = () => {
        const rows = [...document.querySelectorAll("#admin-achievements > .admin-daily-goal")];
        const emptyState = document.querySelector("#admin-achievements-empty");
        if (emptyState) emptyState.hidden = rows.length > 0;
        rows.forEach((node, index) => {
            node.querySelector(":scope > legend").textContent =
                `${tr("admin.achievements.one", "Achievement")} ${index + 1}`;
            const up = node.querySelector('[data-achievement-move="up"]');
            const down = node.querySelector('[data-achievement-move="down"]');
            if (up) up.disabled = index === 0;
            if (down) down.disabled = index === rows.length - 1;
        });
        applyAchievementView();
    };

    const achievementEditor = (achievement = {}) => {
        const node = element("fieldset", null, "admin-daily-goal");
        node.dataset.code = achievement.code || "";
        node.append(element("legend", tr("admin.achievements.one", "Achievement")));
        const head = element("div", null, "admin-event-achievement-head");
        const identity = element("div", null, "admin-achievement-identity");
        identity.append(element("strong", achievement.name || tr("admin.achievements.one", "Achievement")));
        if (achievement.code) identity.append(element("code", `${tr("admin.achievements.code", "Code")}: ${achievement.code}`));
        const actions = element("div", null, "admin-achievement-row-actions");
        const move = (direction, label) => {
            const button = element("button", direction === "up" ? "↑" : "↓", "btn admin-achievement-icon-button");
            button.type = "button"; button.dataset.achievementMove = direction; button.title = label;
            button.setAttribute("aria-label", label);
            button.addEventListener("click", () => {
                const sibling = direction === "up" ? node.previousElementSibling : node.nextElementSibling;
                if (!sibling) return;
                if (direction === "up") node.parentElement.insertBefore(node, sibling);
                else node.parentElement.insertBefore(sibling, node);
                syncAchievementEditors();
            });
            return button;
        };
        const duplicate = element("button", tr("admin.achievements.duplicate", "Duplicate"), "btn");
        duplicate.type = "button";
        duplicate.addEventListener("click", () => {
            const copy = achievementEditor({
                name: `${node.querySelector('[name="achievementName"]').value} (${tr("admin.achievements.copySuffix", "copy")})`,
                metric: node.querySelector('[name="metric"]').value,
                target: Number(node.querySelector('[name="target"]').value),
                rewardPoints: Number(node.querySelector('[name="rewardPoints"]').value),
                enabled: node.querySelector('[name="enabled"]').checked
            });
            node.after(copy); syncAchievementEditors();
            copy.querySelector('[name="achievementName"]').focus();
        });
        const collapse = element("button", tr("admin.achievements.collapse", "Collapse"), "btn");
        collapse.type = "button";
        collapse.setAttribute("aria-expanded", "true");
        collapse.addEventListener("click", () => {
            const collapsed = node.classList.toggle("is-collapsed");
            collapse.textContent = collapsed
                ? tr("admin.achievements.expand", "Expand")
                : tr("admin.achievements.collapse", "Collapse");
            collapse.setAttribute("aria-expanded", String(!collapsed));
        });
        const remove = element("button", tr("admin.common.delete", "Delete"), "btn btn-danger");
        remove.type = "button";
        remove.addEventListener("click", () => { node.remove(); syncAchievementEditors(); });
        actions.append(
            move("up", tr("admin.achievements.moveUp", "Move up")),
            move("down", tr("admin.achievements.moveDown", "Move down")),
            duplicate, collapse, remove
        );
        head.append(identity, actions);

        const fields = element("div", null, "admin-daily-goal-fields");
        const name = element("label", null, "admin-daily-goal-name");
        const nameInput = document.createElement("input");
        nameInput.name = "achievementName"; nameInput.required = true; nameInput.maxLength = 120;
        nameInput.value = achievement.name || "";
        nameInput.addEventListener("input", () => {
            identity.querySelector("strong").textContent = nameInput.value || tr("admin.achievements.one", "Achievement");
            applyAchievementView();
        });
        name.append(element("span", tr("admin.common.name", "Name")), nameInput);

        const metric = element("label");
        const metricSelect = document.createElement("select");
        metricSelect.name = "metric";
        metricSelect.append(...Object.entries(achievementMetricLabels()).map(([value, label]) => {
            const option = element("option", label); option.value = value; return option;
        }));
        metricSelect.value = achievement.metric || "STREAK";
        metric.append(element("span", tr("admin.economy.metric", "Counts")), metricSelect);

        const number = (field, label, value, min, suffix) => {
            const wrapper = element("label");
            wrapper.append(element("span", label));
            const input = document.createElement("input");
            input.name = field; input.type = "number"; input.min = String(min); input.step = "1";
            input.inputMode = "numeric"; input.required = true; input.value = value;
            if (!suffix) { wrapper.append(input); return wrapper; }
            const shell = element("span", null, "admin-points-input");
            shell.append(input, element("span", suffix, "points-symbol"));
            wrapper.append(shell);
            return wrapper;
        };

        fields.append(name, metric,
            number("target", tr("admin.achievements.target", "Target"), achievement.target ?? 1, 1, null),
            number("rewardPoints", tr("admin.economy.reward", "Reward").replace(/\s*\(P\)\s*$/, ""),
                achievement.rewardPoints ?? 0, 0, "P"),
            toggleField("enabled", "true", tr("admin.economy.enabled", "Enabled"), achievement.enabled !== false));
        fields.querySelectorAll("input, select").forEach(control => {
            if (control !== nameInput) control.addEventListener("input", applyAchievementView);
            control.addEventListener("change", applyAchievementView);
        });
        node.append(head, fields);
        return node;
    };

    const renderAchievementEditors = achievements => {
        document.querySelector("#admin-achievements").replaceChildren(...achievements.map(achievementEditor));
        syncAchievementEditors();
    };

    const loadAchievements = async () => {
        const achievements = await request("/economy/achievements");
        renderAchievementEditors(achievements);
        const select = document.querySelector("#admin-holders-select");
        if (select) {
            const previous = select.value;
            select.replaceChildren(...achievements.map(achievement => {
                const option = element("option", `${achievement.name} (${achievement.metric} ${achievement.target})`);
                option.value = achievement.code;
                return option;
            }));
            if (previous) select.value = previous;
        }
    };

    const renderStreak = (userId, streak) => {
        clear("streak");
        row("streak", `${tr("admin.common.user", "User")} #${userId}`,
            [`${tr("admin.achievements.current", "Current")}: ${streak.current}`,
                ` · ${tr("admin.achievements.longest", "Longest")}: ${streak.longest}`,
                ` · ${tr("admin.achievements.freezes", "Streak freezes")}: ${streak.freezes} / 3`,
                ` · ${tr("admin.achievements.lastPlayed", "Last played")}: ${streak.lastPlayedDate || "—"}`],
            [], streak.atRisk ? [chip(tr("admin.achievements.atRisk", "At risk"), "is-warn")] : []);
        const form = document.querySelector("#admin-streak-form");
        form.hidden = false;
        form.elements.userId.value = userId;
        // Prefilled with what is stored, so submitting an untouched form changes nothing.
        form.elements.currentStreak.value = String(streak.current);
        form.elements.longestStreak.value = String(streak.longest);
        form.elements.freezes.value = String(streak.freezes);
        form.elements.lastPlayedDate.value = streak.lastPlayedDate || "";
    };

    const loadStreak = async userId => renderStreak(userId, await request(`/economy/streaks/${userId}`));

    const loadHolders = async code => {
        clear("holders");
        if (!code) return empty("holders", tr("admin.achievements.pickAchievement", "Pick an achievement first."));
        const data = await request(`/economy/achievements/${encodeURIComponent(code)}/users${query({ page: 0, size: 50 })}`);
        if (!data.items.length)
            return empty("holders", tr("admin.achievements.noHolders", "Nobody has earned this yet."));
        results("holders").append(table(
            [tr("admin.common.user", "User"), tr("admin.users.email", "Email"),
                tr("admin.achievements.earnedAt", "Earned"), tr("admin.economy.reward", "Reward")],
            data.items.map(holder => [
                userLink(holder.userId, `${holder.username} · #${holder.userId}`),
                holder.email || "—",
                formatTime(holder.earnedAt),
                pointsCompactNode(holder.rewardPoints)
            ]), true));
        setStatus(tr("admin.achievements.holderCount", `Players: ${data.totalElements}`, data.totalElements));
    };

    const loadPoints = async () => {
        renderPointsSettings(await request("/economy/settings"));
        await loadFees();
        await loadDailyGoals();
        renderPointsDashboard(await request("/economy/dashboard"));
    };

    const loadEvents = async () => {
        const events = await request("/economy/events");
        state.pointEvents = new Map(events.map(event => [event.id, event]));
        clear("economy-events");
        if (!events.length) empty("economy-events", tr("admin.economy.noEvents", "None configured."));
        const now = Date.now();
        const statusOf = event => {
            const start = new Date(event.startsAt).getTime(); const end = event.endsAt ? new Date(event.endsAt).getTime() : null;
            return !event.enabled ? "disabled" : start > now ? "upcoming" : end != null && end <= now ? "ended" : "active";
        };
        // The server orders by start date, which buries the live event under old ones. Admins work on
        // what is running now: active first, then what starts soonest, with ended and disabled at the bottom.
        const rank = { active: 0, upcoming: 1, disabled: 2, ended: 3 };
        [...events].sort((left, right) => rank[statusOf(left)] - rank[statusOf(right)]
            || (statusOf(left) === "upcoming" ? 1 : -1) * (new Date(left.startsAt) - new Date(right.startsAt))
            || left.name.localeCompare(right.name)).forEach(event => {
            const statusKey = statusOf(event);
            const eventState = tr(`admin.economy.${statusKey}`, statusKey.charAt(0).toUpperCase() + statusKey.slice(1));
            const gameTypes = event.gameTypes || [];
            const achievements = event.achievements || [];
            const games = gameTypes.length ? gameTypes.join(", ") : tr("admin.economy.allGames", "All games");
            const goals = tr("admin.economy.goalCount", `${achievements.length} goals`, achievements.length);
            const schedule = `${formatTime(event.startsAt)} → ${event.endsAt ? formatTime(event.endsAt) : "∞"}`;
            const totalReward = Number(event.completionRewardPoints || 0) + achievements.reduce((sum, achievement) => sum + Number(achievement.rewardPoints || 0), 0);
            const card = element("article", null, `admin-event-summary-card is-${statusKey}`);
            const head = element("header", null, "admin-event-summary-head");
            head.append(element("h3", event.name), chip(eventState, statusKey === "active" ? "is-good" : event.enabled ? "is-warn" : "is-bad"));
            card.append(head);
            if (event.descriptionHtml) {
                const description = element("div", null, "admin-meta markdown-body");
                renderTrustedMarkdown(description, event.descriptionHtml);
                card.append(description);
            }
            const facts = element("dl", null, "admin-event-summary-facts");
            const fact = (label, value) => { const wrapper = element("div"); wrapper.append(element("dt", label)); const output = element("dd"); content(output, value); wrapper.append(output); facts.append(wrapper); };
            fact(tr("admin.common.when", "When"), schedule);
            fact(tr("admin.economy.games", "Eligible games"), games);
            fact(tr("admin.economy.achievements", "Sub-achievements"), goals);
            const reward = element("span"); reward.append(document.createTextNode(totalReward.toLocaleString()), element("span", " P", "points-symbol"));
            fact(tr("admin.economy.totalReward", "Total listed reward"), reward);
            card.append(facts);
            const goalDetails = element("details", null, "admin-event-summary-goals");
            goalDetails.append(element("summary", goals));
            const goalList = element("div", null, "admin-event-summary-goal-list");
            achievements.forEach(achievement => {
                const goal = element("article");
                const goalCopy = element("div", null, "admin-event-summary-goal-copy");
                const criteria = achievement.gameTypes?.length === 1 && achievement.gameModes?.length
                    ? gameModeSummary(achievement.gameTypes[0], achievement.gameModes)
                    : achievement.gameTypes?.length ? achievement.gameTypes.join(", ") : games;
                goalCopy.append(element("strong", achievement.name));
                if (achievement.descriptionHtml) {
                    const description = element("div", null, "admin-meta markdown-body");
                    renderTrustedMarkdown(description, achievement.descriptionHtml);
                    goalCopy.append(description);
                }
                goal.append(goalCopy, element("span", criteria, "admin-meta"));
                goalList.append(goal);
            });
            goalDetails.append(goalList); card.append(goalDetails);
            const foot = element("footer", null, "admin-event-summary-foot");
            const gameChips = element("div", null, "admin-chips");
            gameChips.append(...(gameTypes.length ? gameTypes : [tr("admin.economy.allGames", "All games")]).map(game => chip(game)));
            const actions = element("div", null, "admin-actions");
            const deleteButton = button(tr("admin.economy.deleteEvent", "Delete event"), "delete-point-event", event.id);
            deleteButton.classList.add("btn-danger");
            actions.append(button(tr("admin.common.edit", "Edit"), "edit-point-event", event.id),
                button(event.enabled ? tr("admin.economy.disable", "Disable") : tr("admin.economy.enable", "Enable"), "toggle-point-event", event.id),
                deleteButton);
            foot.append(gameChips, actions); card.append(foot); results("economy-events").append(card);
        });
    };

    const userCard = user => {
        const card = link(userHref(user.id), null, "admin-row admin-row-link admin-user-row");
        const avatar = element("span", (user.username || "?").trim().charAt(0).toUpperCase() || "?", "admin-avatar");
        const details = element("div");
        const headRow = element("div", null, "admin-row-head");
        headRow.append(element("h3", `${user.username} · #${user.id}`));
        const chips = element("div", null, "admin-chips");
        const balance = element("span", null, "admin-chip admin-chip-points");
        content(balance, pointsCompactNode(user.points));
        chips.append(balance, chip(user.status, statusClass(user.status)), ...[...user.roles].filter(role => role !== "USER").map(role => chip(role)), ...(user.fakeAdmin ? [chip(tr("admin.user.fakeAdmin", "Fake admin"), "is-warn")] : []));
        headRow.append(chips);
        details.append(headRow);
        const meta = element("p", null, "admin-meta");
        content(meta, [`${user.email} · `, pointsDeltaNode(user.pointsChangeLast7Days),
            ` / 7d · ${tr("admin.common.created", "Created")} ${formatTime(user.createdAt)} · ${tr("admin.col.lastLogin", "Last login")} ${formatTime(user.lastLoginAt)}`]);
        details.append(meta);
        card.append(avatar, details, element("span", "→", "admin-row-arrow"));
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
        const [stats, sessions, audit, notifications, ledger, series] = await Promise.all([
            request(`/stats/users/${id}`).catch(() => null),
            request(`/reports/sessions${query({ userId: id, size: 5 })}`).catch(() => null),
            request(`/audit${query({ targetType: "USER", targetId: id, size: 5 })}`).catch(() => null),
            request(`/database/notifications${query({ userId: id, size: 5 })}`).catch(() => null),
            request(`/users/${id}/points${query({ size: 10 })}`).catch(() => null),
            request(`/users/${id}/points/series${query({ days: 30 })}`).catch(() => null)
        ]);
        state.currentUser = user;
        (notifications?.items || []).forEach(notification => state.notifications.set(notification.id, notification));
        detail.replaceChildren();
        detail.append(link("/admin/users", tr("admin.user.back", "← All users"), "admin-back"));

        const head = element("header", null, "admin-user-head");
        const identity = element("div");
        identity.append(element("h2", user.username));
        const chips = element("div", null, "admin-chips");
        chips.append(chip(user.status, statusClass(user.status)), ...[...user.roles].map(role => chip(role)), ...(user.fakeAdmin ? [chip(tr("admin.user.fakeAdmin", "Fake admin"), "is-warn")] : []));
        identity.append(chips, element("p", `#${user.id} · ${user.email}`, "admin-meta"));
        const actions = element("div", null, "admin-actions");
        actions.append(button(tr("admin.user.editAccount", "Edit account"), "edit-user", String(user.id), true), button(tr("admin.user.adjustPoints", "Adjust Points"), "adjust-points", String(user.id)), button(user.fakeAdmin ? tr("admin.user.removeFakeAdmin", "Remove fake admin") : tr("admin.user.makeFakeAdmin", "Make fake admin"), "toggle-fake-admin", String(user.id)), button(tr("admin.user.revokeSessions", "Revoke sessions"), "revoke-user-sessions", String(user.id)),
            link(`/admin/stats?userId=${user.id}`, tr("admin.user.editStats", "Edit stats"), "btn"), link(`/admin/notifications?userId=${user.id}`, tr("admin.user.sendNotification", "Send notification"), "btn"));
        head.append(identity, actions);
        detail.append(head);

        const facts = element("dl", null, "admin-user-facts");
        [[tr("points.balance", "Points balance"), [pointsCompactNode(user.points), " · ", pointsDeltaNode(user.pointsChangeLast7Days), " / 7d"]], [tr("admin.common.created", "Created"), formatTime(user.createdAt)], [tr("admin.user.updatedAt", "Updated"), formatTime(user.updatedAt)], [tr("admin.col.lastLogin", "Last login"), formatTime(user.lastLoginAt)],
         [tr("admin.sessions.title", "Sessions"), sessions ? String(sessions.totalElements) : "—"], [tr("admin.db.table.notifications", "Notifications"), notifications ? String(notifications.totalElements) : "—"],
         [tr("admin.user.adminActions", "Admin actions"), audit ? String(audit.totalElements) : "—"]].forEach(([label, value]) => {
            const item = element("div"); const definition = element("dd"); content(definition, value);
            item.append(element("dt", label), definition); facts.append(item);
        });
        detail.append(facts);

        const sections = element("div", null, "admin-user-sections");

        // Where this account's Points went, with the same chart the player sees.
        const pointsCard = sectionCard(tr("points.ledger.title", "Point activity"), `/admin/users?userId=${user.id}`, tr("admin.user.adjustPoints", "Adjust Points"));
        const chartShell = element("div", null, "admin-points-chart");
        const canvas = document.createElement("canvas");
        chartShell.append(canvas);
        pointsCard.body.append(chartShell);
        pointsCard.body.append(ledger?.items?.length
            ? table([tr("points.ledger.reason", "Reason"), tr("points.ledger.amount", "Amount"), tr("points.balance", "Balance"), tr("points.ledger.when", "When")],
                ledger.items.map(item => [
                    tr(`points.transaction.${String(item.type || "").toLowerCase()}`, item.type),
                    pointsDeltaNode(item.amount, false),
                    pointsNode(item.balanceAfter),
                    formatTime(item.createdAt)]))
            : element("p", tr("points.ledger.empty", "No Point activity yet."), "admin-empty"));
        sections.append(pointsCard.card);
        // Chart.js measures its canvas, so draw once the card is in the document.
        window.requestAnimationFrame(() => window.renderPointsChart?.(canvas, series || [], { balance: user.points, days: 30 }));

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
        data.forEach(item => {
            const lobby = item.lobby; const game = lobby.gameType?.name || lobby.gameType?.game || tr("admin.common.game", "Game");
            row("lobbies", lobby.name, game,
                [button(tr("admin.common.edit", "Edit"), "edit-lobby", lobby.id), button(tr("admin.lobbies.extend", "Extend"), "extend-lobby", lobby.id), button(tr("admin.lobbies.close", "Close lobby"), "close-lobby", lobby.id, true)],
                [chip(item.state, "is-good"), chip(tr("admin.lobbies.players", `${lobby.players?.length || 0}/${lobby.maxPlayers} players`, lobby.players?.length || 0, lobby.maxPlayers))]);
        });
    };
    const gameChips = game => [
        chip(game.gameType),
        ...(game.mode ? [chip(modeLabel(game.gameType, game.mode))] : []),
        game.endedAt ? chip(tr("admin.games.completed", "Completed"), "is-good") : chip(tr("admin.common.incomplete", "Incomplete"), "is-warn")
    ];
    const gameMeta = game => {
        const meta = [`${tr("admin.games.rounds", `${game.rounds || 0} rounds`, game.rounds || 0)}`];
        if (game.ownerUserId != null) meta.push(` · ${tr("admin.games.owner", "Owner")} `, userLink(game.ownerUserId, `#${game.ownerUserId}`));
        if (game.players?.length) { meta.push(` · ${tr("admin.games.playersLabel", "Players")}: `, ...playerLinks(game.players)); }
        meta.push(` · ${tr("admin.games.winners", "Winners")}: `); if (game.winners?.length) meta.push(...playerLinks(game.winners)); else meta.push("—");
        if (game.endedAt) meta.push(` · ${tr("admin.games.ended", `Ended ${formatTime(game.endedAt)}`, formatTime(game.endedAt))}`);
        return meta;
    };
    const loadGames = async (currentPage = 0) => {
        const values = formValues(document.querySelector('[data-filters="games"]'));
        const sort = values.sort === "createdAtOldest" ? "createdAt" : values.sort;
        const direction = values.sort === "createdAtOldest" ? "asc" : values.sort === "name" ? "asc" : "desc";
        const data = await request(`/reports/games${query({ ...values, sort, direction, page: currentPage, size: 20 })}`); clear("games");
        if (!data.items.length) return empty("games", tr("admin.games.empty", "No recorded games match these filters."));
        data.items.forEach(game => row("games", game.name || tr("admin.games.fallbackName", `${game.gameType} game`, game.gameType), gameMeta(game), [button(tr("admin.games.rename", "Rename"), "rename-game", game.id), button(tr("admin.common.delete", "Delete"), "delete-game", game.id, true)], gameChips(game))); renderPaged("games", data);
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
            [userLink(session.userId)],
            sessionMeta(session),
            [selectBox("sessions", session.id), ...(session.active ? [button(tr("admin.sessions.expire", "Expire session"), "expire-session", session.id, true)] : []), button(tr("admin.common.delete", "Delete"), "delete-session", session.id, true)],
            [stateChip(session.active)]));
        renderPaged("sessions", data);
    };
    const toggleGame = async (game, mode, wanted) => {
        const enabled = wanted === "enable"; const rule = `${game}${mode ? ` · ${modeLabel(game, mode)}` : ""}`;
        const values = await askAction({ title: enabled ? tr("admin.availability.enableTitle", "Enable game") : tr("admin.availability.disableTitle", "Disable game"), description: enabled ? tr("admin.availability.enableCopy", `Enable ${rule} for players?`, rule) : tr("admin.availability.disableCopy", `Disable ${rule} for players?`, rule), confirmLabel: enabled ? tr("admin.availability.enable", "Enable") : tr("admin.availability.disable", "Disable"), danger: !enabled, fields: [reasonField()] });
        if (!values) return false;
        await request(`/games/${encodeURIComponent(game)}`, { method: "PATCH", body: JSON.stringify({ mode: mode || null, enabled, reason: values.reason }) });
        await refreshAvailabilityContext();
        return true;
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
            const headRow = element("div", null, "admin-row-head");
            headRow.append(element("h3", label));
            details.append(headRow, element("p", item.enabled ? tr("admin.availability.available", "Available to players") : tr("admin.availability.unavailable", "Unavailable to players"), "admin-meta"));
            const select = element("select", null, `admin-toggle ${item.enabled ? "is-good" : "is-bad"}`);
            const enabledOption = element("option", tr("admin.common.enabled", "Enabled")); enabledOption.value = "enable";
            const disabledOption = element("option", tr("admin.common.disabled", "Disabled")); disabledOption.value = "disable";
            select.append(enabledOption, disabledOption);
            select.value = item.enabled ? "enable" : "disable";
            select.setAttribute("aria-label", `${item.game}${item.mode ? ` ${modeLabel(item.game, item.mode)}` : ""}`);
            select.addEventListener("change", async () => {
                try {
                    if (!await toggleGame(item.game, item.mode || "", select.value)) select.value = item.enabled ? "enable" : "disable";
                } catch (error) { select.value = item.enabled ? "enable" : "disable"; setStatus(error.message, true); }
            });
            const controls = element("div", null, "admin-actions");
            controls.append(select);
            node.append(details, controls);
            return node;
        };
        [...games.entries()].sort(([left], [right]) => left.localeCompare(right)).forEach(([game, group]) => {
            const list = element("details", null, "admin-availability-group");
            const summary = element("summary"); summary.append(element("strong", game), chip(group.game.enabled ? tr("admin.common.enabled", "Enabled") : tr("admin.common.disabled", "Disabled"), group.game.enabled ? "is-good" : "is-bad"), element("span", tr("admin.availability.modes", `${group.modes.length} modes`, group.modes.length)));
            const modes = element("div", null, "admin-availability-modes");
            modes.append(availabilityRow(group.game, tr("admin.availability.entireGame", "Entire game")), ...group.modes.sort((left, right) => left.mode.localeCompare(right.mode)).map(item => availabilityRow(item, modeLabel(item.game, item.mode))));
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
        data.items.forEach(event => row("audit", event.action, auditMeta(event),
            event.undoable && !event.undone ? [button(tr("admin.audit.undo", "Undo"), "undo-audit", event.id, true)] : [],
            [chip(event.outcome, event.outcome === "SUCCESS" ? "is-good" : "is-bad"), ...(event.undone ? [chip(tr("admin.audit.undoneChip", "Undone"), "is-warn")] : [])])); renderPaged("audit", data);
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
                return { data, table: table(["ID", tr("admin.users.username", "Username"), tr("admin.users.email", "Email"), tr("points.balance", "Points balance"), tr("admin.users.status", "Status"), tr("admin.col.roles", "Roles"), tr("admin.user.fakeAdmin", "Fake admin"), tr("admin.common.created", "Created"), tr("admin.col.lastLogin", "Last login")], data.items.map(user => [
                    userLink(user.id, `#${user.id}`), userLink(user.id, user.username), user.email, pointsCompactNode(user.points), chip(user.status, statusClass(user.status)), [...user.roles].join(", "), user.fakeAdmin ? chip(tr("admin.user.fakeAdmin", "Fake admin"), "is-warn") : "—", formatTime(user.createdAt), formatTime(user.lastLoginAt)
                ]), true) };
            }
        },
        sessions: {
            label: tr("admin.db.table.sessions", "Sessions"), area: "Sessions",
            filters: [
                { name: "query", label: tr("admin.users.search", "Search"), type: "search", placeholder: tr("admin.sessions.searchPlaceholder", "Device, OS, or location") },
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
                { name: "query", label: tr("admin.users.search", "Search"), type: "search", placeholder: tr("admin.games.namePlaceholder", "Game name") },
                { name: "gameType", label: tr("admin.games.game", "Game"), options: [allOption, { value: "BRISKULA", label: "Briskula" }, { value: "DURAK", label: "Durak" }, { value: "TRESETA", label: "Treseta" }] },
                { name: "mode", label: tr("admin.games.mode", "Mode"), options: [allOption, ...[...new Set([...modes.briskula, ...modes.durak, ...modes.treseta])].map(value => ({ value, label: modeLabel("", value) }))] },
                { name: "completed", label: tr("admin.games.state", "State"), options: [allOption, { value: "true", label: tr("admin.games.completed", "Completed") }, { value: "false", label: tr("admin.games.incomplete", "Incomplete") }] }
            ],
            load: async currentPage => {
                const data = await request(`/reports/games${query({ ...state.dbFilters, page: currentPage, size: 20 })}`);
                return { data, toolbar: bulkBar(tr("admin.bulk.games", "Delete selected games"), "delete-selected-games", "db-games"), table: table([selectAllBox("db-games"), "ID", tr("admin.common.type", "Type"), tr("admin.common.mode", "Mode"), tr("admin.common.name", "Name"), tr("admin.games.owner", "Owner"), tr("admin.games.playersLabel", "Players"), tr("admin.games.winners", "Winners"), tr("admin.games.ended", "Ended", "").trim() || "Ended", tr("admin.common.actions", "Actions")], data.items.map(game => [
                    selectBox("db-games", game.id), shortId(game.id), game.gameType, modeLabel(game.gameType, game.mode) || "—", game.name || "—",
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
                { name: "query", label: tr("admin.users.search", "Search"), type: "search", placeholder: tr("admin.notify.message", "Message") },
                { name: "userId", label: tr("admin.common.userId", "User ID"), type: "number" },
                { name: "type", label: tr("admin.common.type", "Type"), options: [allOption, ...["GAME_INVITE", "FRIEND_INVITE", "TEXT"].map(value => ({ value, label: value }))] },
                { name: "read", label: tr("admin.common.read", "Read"), options: [allOption, { value: "true", label: tr("admin.common.read", "Read") }, { value: "false", label: tr("admin.common.unread", "Unread") }] }
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
                    item.game, modeLabel(item.game, item.mode) || tr("admin.availability.entireGame", "All modes"), chip(item.enabled ? tr("admin.common.enabled", "Enabled") : tr("admin.common.disabled", "Disabled"), item.enabled ? "is-good" : "is-bad"),
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
                if (field.placeholder) input.placeholder = field.placeholder;
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
            "Durak stat rows": tr("admin.db.area.durakRows", "Durak stat rows"),
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
        if (context) { context.replaceChildren(); content(context, [userLink(stats.userId, `${tr("admin.common.user", "User")} #${stats.userId}`), ` · ${stats.briskulaOpponentRows + stats.briskulaTeammateRows} Briskula · ${stats.durakOpponentRows} Durak · ${stats.tresetaOpponentRows + stats.tresetaTeammateRows} Treseta`]); }
        const addGroup = (label, entries) => Object.entries(entries || {}).forEach(([mode, line]) => row("stats", `${label} · ${mode}`, `${line.played} ${tr("admin.user.played", "played").toLowerCase()} · ${line.wins} ${tr("admin.user.wins", "wins").toLowerCase()} · ${tr("admin.user.lastPlayed", "Last played")} ${formatTime(line.lastPlayedAt)}`));
        addGroup("Overall", stats.overall);
        Object.entries(modes).forEach(([gameType, gameModes]) => {
            const entries = stats[`${gameType}Modes`] || {};
            gameModes.forEach(mode => {
                const line = entries[mode];
                const values = line || { played: 0, wins: 0, lastPlayedAt: null };
                const label = line ? tr("admin.common.edit", "Edit") : "Add";
                const target = encodeURIComponent(JSON.stringify({ gameType, mode, played: values.played, wins: values.wins, lastPlayedAt: values.lastPlayedAt }));
                row("stats", `${gameType[0].toUpperCase()}${gameType.slice(1)} · ${modeLabel(gameType, mode)}`, line ? `${values.played} · ${values.wins} · ${formatTime(values.lastPlayedAt)}` : "—", [button(label, "edit-stat-row", target, !line)]);
            });
        });
    };
    const renderStatsDiff = (before, after) => {
        const diff = document.querySelector("#admin-stats-diff"); if (!diff) return;
        diff.replaceChildren(element("strong", "Preview result"));
        const changed = [];
        ["BRISKULA", "DURAK", "TRESETA"].forEach(game => {
            const key = `${game.toLowerCase()}Modes`;
            const modesAfter = after[key] || {}; const modesBefore = before?.[key] || {};
            Object.keys({ ...modesBefore, ...modesAfter }).forEach(mode => {
                const from = modesBefore[mode] || { played: 0, wins: 0 };
                const to = modesAfter[mode] || { played: 0, wins: 0 };
                if (from.played !== to.played || from.wins !== to.wins || from.lastPlayedAt !== to.lastPlayedAt) changed.push(`${game} · ${modeLabel(game, mode)}: ${from.played} / ${from.wins} → ${to.played} / ${to.wins}`);
            });
        });
        (changed.length ? changed : ["No values changed."]).forEach(line => diff.append(element("p", line, "admin-meta")));
        diff.hidden = false;
    };
    const loadStats = async userId => {
        const data = await request(`/stats/users/${userId}`); state.stats = data; renderStats(data);
        const form = document.querySelector("#admin-stats-edit"); form.hidden = false; form.elements.userId.value = userId;
    };

    const loaders = {
        dashboard: loadOverview,
        points: loadPoints,
        events: loadEvents,
        achievements: loadAchievements,
        users: currentPage => state.userDetailId ? loadUserDetail(state.userDetailId) : loadUsers(currentPage),
        lobbies: loadLobbies, games: loadGames, sessions: loadSessions, availability: loadAvailability,
        database: loadDatabase, audit: loadAudit, stats: () => {}, notifications: () => {}
    };
    const refresh = async () => { setStatus(tr("admin.refreshing", "Refreshing…")); try { await loaders[page](); setStatus(tr("admin.updated", `Updated ${formatClock(new Date())}.`, formatClock(new Date()))); } catch (error) { setStatus(error.message, true); } };
    const refreshNotificationsContext = () => state.userDetailId ? loadUserDetail(state.userDetailId) : loadDatabaseTable();
    const refreshAvailabilityContext = () => page === "database" ? loadDatabaseTable() : loadAvailability();

    document.querySelectorAll(".admin-filters[data-filters]").forEach(form => form.addEventListener("submit", event => {
        event.preventDefault();
        if (form.dataset.filters === "users" && state.userDetailId) { state.userDetailId = null; history.replaceState(null, "", "/admin/users"); document.querySelector("#admin-user-detail").hidden = true; document.querySelector("#admin-user-list").hidden = false; }
        refresh();
    }));
    // Dropdown filters apply themselves; the button stays for text inputs.
    document.querySelectorAll("form.admin-filters").forEach(form => form.addEventListener("change", event => {
        if (event.target instanceof HTMLSelectElement) form.requestSubmit();
    }));
    document.querySelector('[data-action="refresh"]').addEventListener("click", refresh);
    document.querySelector("#admin-event-add-achievement")?.addEventListener("click", () => {
        const achievement = eventAchievementEditor();
        document.querySelector("#admin-event-achievements").append(achievement);
        syncEventAchievementEditors();
        achievement.querySelector('[name="goalName"]').focus();
    });
    document.querySelectorAll('#admin-event-form .admin-event-games input[name="gameTypes"]').forEach(box => box.addEventListener("change", () =>
        document.querySelectorAll("#admin-event-achievements > .admin-event-achievement")
            .forEach(achievement => {
                const selectedModes = achievementGameModes(achievement);
                renderAchievementGameTypes(achievement);
                renderAchievementModes(achievement, selectedModes);
            })));
    document.querySelector("#admin-event-new")?.addEventListener("click", () => resetEventForm());
    document.querySelectorAll("[data-event-description-mode]").forEach(tab => tab.addEventListener("click", () =>
        setEventDescriptionMode(tab.dataset.eventDescriptionMode)));
    document.querySelector(".admin-markdown-tabs")?.addEventListener("keydown", moveMarkdownTabSelection);
    document.querySelector("#admin-points-settings")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const values = formValues(form);
        const button = form.querySelector('button[type="submit"]');
        const feeStatus = document.querySelector("#admin-fee-status");
        button.disabled = true;
        form.setAttribute("aria-busy", "true");
        try {
            const saved = await request("/economy/settings", { method: "PATCH", body: JSON.stringify({
                wagerFeePercent: Number(values.wagerFeePercent), startingBalance: Number(values.startingBalance),
                dailyReward: Number(values.dailyReward), reason: values.reason }) });
            renderPointsSettings(saved);
            form.elements.reason.value = "";
            await loadFees();
            const message = tr("admin.economy.rulesSaved", "Points rules updated.");
            feeStatus.textContent = message; feeStatus.hidden = false; feeStatus.classList.remove("is-error");
            setStatus(message);
        } catch (error) {
            feeStatus.textContent = error.message; feeStatus.hidden = false; feeStatus.classList.add("is-error");
            setStatus(error.message, true);
        } finally {
            button.disabled = false;
            form.removeAttribute("aria-busy");
        }
    });
    document.querySelector("#admin-holders-form")?.addEventListener("submit", event => {
        event.preventDefault();
        loadHolders(formValues(event.currentTarget).code).catch(error => setStatus(error.message, true));
    });
    document.querySelector('[data-action="reset-streak"]')?.addEventListener("click", async () => {
        const form = document.querySelector("#admin-streak-form");
        const values = formValues(form);
        const streakStatus = document.querySelector("#admin-streak-status");
        if (!values.reason) {
            streakStatus.textContent = tr("admin.achievements.resetNeedsReason", "A reason is required to reset a streak.");
            streakStatus.hidden = false; streakStatus.classList.add("is-error");
            return;
        }
        if (!await confirmAction(tr("admin.achievements.resetStreak", "Reset streak"),
            tr("admin.achievements.resetCopy", `Clear the streak, its record, and every freeze for user #${values.userId}?`, values.userId), true)) return;
        try {
            const streak = await request(`/economy/streaks/${values.userId}${query({ reason: values.reason })}`,
                { method: "DELETE" });
            renderStreak(values.userId, streak);
            form.elements.reason.value = "";
            const message = tr("admin.achievements.streakReset", "Streak reset.");
            streakStatus.textContent = message; streakStatus.hidden = false;
            streakStatus.classList.remove("is-error");
            setStatus(message);
        } catch (error) {
            streakStatus.textContent = error.message; streakStatus.hidden = false;
            streakStatus.classList.add("is-error");
            setStatus(error.message, true);
        }
    });
    document.querySelector("#admin-achievement-add")?.addEventListener("click", () => {
        const achievement = achievementEditor();
        document.querySelector("#admin-achievements").append(achievement);
        syncAchievementEditors();
        achievement.querySelector('[name="achievementName"]').focus();
    });
    document.querySelector("#admin-achievement-filter")?.addEventListener("input", applyAchievementView);
    ["#admin-achievement-metric", "#admin-achievement-state", "#admin-achievement-sort"].forEach(selector =>
        document.querySelector(selector)?.addEventListener("change", applyAchievementView));
    document.querySelector("#admin-achievement-reset-view")?.addEventListener("click", () => {
        document.querySelector("#admin-achievement-filter").value = "";
        document.querySelector("#admin-achievement-metric").value = "";
        document.querySelector("#admin-achievement-state").value = "";
        document.querySelector("#admin-achievement-sort").value = "configured";
        applyAchievementView();
    });
    document.querySelectorAll("[data-achievement-bulk]").forEach(button => button.addEventListener("click", () => {
        const enabled = button.dataset.achievementBulk === "enable";
        document.querySelectorAll("#admin-achievements > .admin-daily-goal:not([hidden])").forEach(node => {
            const checkbox = node.querySelector('[name="enabled"]');
            if (checkbox) checkbox.checked = enabled;
        });
        applyAchievementView();
    }));
    document.querySelector("#admin-achievements-form")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector('button[type="submit"]');
        const achievementStatus = document.querySelector("#admin-achievements-status");
        button.disabled = true;
        form.setAttribute("aria-busy", "true");
        try {
            const achievements = [...document.querySelectorAll("#admin-achievements > .admin-daily-goal")].map(node => ({
                code: node.dataset.code || null,
                name: node.querySelector('[name="achievementName"]').value.trim(),
                metric: node.querySelector('[name="metric"]').value,
                target: Number(node.querySelector('[name="target"]').value || 0),
                rewardPoints: Number(node.querySelector('[name="rewardPoints"]').value || 0),
                enabled: node.querySelector('[name="enabled"]').checked
            }));
            renderAchievementEditors(await request("/economy/achievements", {
                method: "PUT",
                body: JSON.stringify({ achievements, reason: form.elements.reason.value.trim() })
            }));
            form.elements.reason.value = "";
            const message = tr("admin.achievements.saved", "Achievements updated.");
            achievementStatus.textContent = message; achievementStatus.hidden = false;
            achievementStatus.classList.remove("is-error");
            setStatus(message);
        } catch (error) {
            achievementStatus.textContent = error.message; achievementStatus.hidden = false;
            achievementStatus.classList.add("is-error");
            setStatus(error.message, true);
        } finally {
            button.disabled = false;
            form.removeAttribute("aria-busy");
        }
    });
    document.querySelector("#admin-streak-form")?.addEventListener("submit", async event => {
        event.preventDefault();
        // currentTarget is null once the handler resumes after an await.
        const form = event.currentTarget;
        const values = formValues(form);
        const streakStatus = document.querySelector("#admin-streak-status");
        try {
            const number = value => value === "" || value == null ? null : Number(value);
            const streak = await request(`/economy/streaks/${values.userId}`, {
                method: "PATCH",
                body: JSON.stringify({
                    currentStreak: number(values.currentStreak),
                    longestStreak: number(values.longestStreak),
                    freezes: number(values.freezes),
                    lastPlayedDate: values.lastPlayedDate || null,
                    reason: values.reason
                })
            });
            renderStreak(values.userId, streak);
            form.elements.reason.value = "";
            const message = tr("admin.achievements.streakSaved", "Streak updated.");
            streakStatus.textContent = message; streakStatus.hidden = false;
            streakStatus.classList.remove("is-error");
            setStatus(message);
        } catch (error) {
            streakStatus.textContent = error.message; streakStatus.hidden = false;
            streakStatus.classList.add("is-error");
            setStatus(error.message, true);
        }
    });
    document.querySelector("#admin-daily-goal-add")?.addEventListener("click", () => {
        const goal = dailyGoalEditor();
        document.querySelector("#admin-daily-goals").append(goal);
        syncDailyGoalEditors();
        goal.querySelector('[name="goalName"]').focus();
    });
    document.querySelector("#admin-daily-goals-form")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector('button[type="submit"]');
        const goalStatus = document.querySelector("#admin-daily-goals-status");
        button.disabled = true;
        form.setAttribute("aria-busy", "true");
        try {
            const goals = [...document.querySelectorAll("#admin-daily-goals > .admin-daily-goal")].map(node => ({
                code: node.dataset.code || null,
                name: node.querySelector('[name="goalName"]').value.trim(),
                metric: node.querySelector('[name="metric"]').value,
                target: Number(node.querySelector('[name="target"]').value || 0),
                rewardPoints: Number(node.querySelector('[name="rewardPoints"]').value || 0),
                enabled: node.querySelector('[name="enabled"]').checked
            }));
            renderDailyGoals(await request("/economy/daily-goals", { method: "PUT", body: JSON.stringify({ goals, reason: form.elements.reason.value.trim() }) }));
            form.elements.reason.value = "";
            const message = tr("admin.economy.dailyGoalsSaved", "Daily goals updated.");
            goalStatus.textContent = message; goalStatus.hidden = false; goalStatus.classList.remove("is-error");
            setStatus(message);
        } catch (error) {
            goalStatus.textContent = error.message; goalStatus.hidden = false; goalStatus.classList.add("is-error");
            setStatus(error.message, true);
        } finally {
            button.disabled = false;
            form.removeAttribute("aria-busy");
        }
    });
    document.querySelector("#admin-event-form")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const submit = document.querySelector("#admin-event-submit");
        const eventStatus = document.querySelector("#admin-event-status");
        submit.disabled = true;
        form.setAttribute("aria-busy", "true");
        eventStatus.hidden = true;
        eventStatus.classList.remove("is-error");
        try {
            const patch = eventPatch(form);
            const id = form.elements.id.value;
            await request(id ? `/economy/events/${id}` : "/economy/events", { method: id ? "PUT" : "POST", body: JSON.stringify(patch) });
            const message = id
                ? tr("admin.economy.eventUpdated", "Event updated.")
                : tr("admin.economy.eventCreated", "Event created.");
            resetEventForm();
            await loadEvents();
            eventStatus.textContent = message;
            eventStatus.hidden = false;
            setStatus(message);
        } catch (error) {
            eventStatus.textContent = error.message;
            eventStatus.hidden = false;
            eventStatus.classList.add("is-error");
            setStatus(error.message, true);
        } finally {
            submit.disabled = false;
            form.removeAttribute("aria-busy");
        }
    });
    // One listener per host screen: the shared user-search fragment reports its pick and
    // each screen decides what that means.
    document.querySelector('[data-user-search="stats"]')?.addEventListener("uc:admin-user-picked", event => {
        loadStats(event.detail.id).catch(error => setStatus(error.message, true));
    });
    document.querySelector('[data-user-search="streak"]')?.addEventListener("uc:admin-user-picked", event => {
        loadStreak(event.detail.id).catch(error => setStatus(error.message, true));
    });
    document.querySelector("#admin-stats-edit")?.addEventListener("submit", async event => {
        event.preventDefault(); const values = formValues(event.currentTarget);
        try { const body = { played: values.played === "" ? null : Number(values.played), wins: values.wins === "" ? null : Number(values.wins), lastPlayedAt: values.lastPlayedAt ? parseDateTime(values.lastPlayedAt).toISOString() : null, reason: values.reason, dryRun: values.dryRun === "on" }; const result = await request(`/stats/users/${values.userId}/${encodeURIComponent(values.gameType)}/${encodeURIComponent(values.mode)}`, { method: "PATCH", body: JSON.stringify(body) }); if (result.dryRun) { renderStats(result.after); renderStatsDiff(result.before, result.after); } else await loadStats(values.userId); setStatus(result.warning || (result.dryRun ? "Stats preview is ready; nothing changed." : "Stats updated.")); } catch (error) { setStatus(error.message, true); }
    });
    document.querySelector("#admin-stats-edit")?.elements.gameType.addEventListener("change", populateModes);
    document.addEventListener("keydown", event => {
        const target = event.target;
        if (!(target instanceof HTMLElement) || event.key !== "Enter" || event.shiftKey || target.matches("textarea")) return;
        const form = target.closest("#admin-user-form, #admin-lobby-form");
        if (!form) return;
        event.preventDefault();
        form.querySelector('[data-action="save-user"], [data-action="save-lobby"]')?.click();
    });
    const gamesFilter = document.querySelector('[data-filters="games"]');
    const syncGamesModeFilter = () => {
        if (!gamesFilter) return;
        const available = { BRISKULA: modes.briskula, DURAK: modes.durak, TRESETA: modes.treseta }[gamesFilter.elements.gameType.value] || [...new Set([...modes.briskula, ...modes.durak, ...modes.treseta])];
        const select = gamesFilter.elements.mode; const selected = select.value;
        const all = element("option", tr("admin.common.all", "All")); all.value = "";
        select.replaceChildren(all, ...available.map(value => modeOption(gamesFilter.elements.gameType.value, value)));
        if (available.includes(selected)) select.value = selected;
    };
    gamesFilter?.elements.gameType.addEventListener("change", syncGamesModeFilter);
    document.addEventListener("click", async event => {
        const control = event.target.closest("button[data-action]"); if (!control || control.dataset.action === "refresh") return;
        try {
            if (control.dataset.action === "edit-point-event") {
                resetEventForm(state.pointEvents.get(control.dataset.value));
                document.querySelector("#admin-event-form").scrollIntoView({ behavior: "smooth", block: "start" });
                return;
            }
            if (control.dataset.action === "toggle-point-event") {
                const current = state.pointEvents.get(control.dataset.value);
                const values = await askAction({ title: current.enabled ? tr("admin.economy.disable", "Disable event") : tr("admin.economy.enable", "Enable event"), description: current.name, fields: [reasonField()] });
                if (!values) return;
                await request(`/economy/events/${current.id}/enabled${query({ enabled: !current.enabled, reason: values.reason })}`, { method: "PATCH" });
                await loadEvents(); setStatus(tr("admin.economy.eventSaved", "Point event saved.")); return;
            }
            if (control.dataset.action === "delete-point-event") {
                const current = state.pointEvents.get(control.dataset.value);
                const values = await askAction({
                    title: tr("admin.economy.deleteEvent", "Delete event"),
                    description: tr("admin.economy.deleteEventCopy", `Delete ${current.name}? Earned rewards remain in the ledger.`, current.name),
                    confirmLabel: tr("admin.economy.deleteEvent", "Delete event"),
                    danger: true,
                    fields: [reasonField()]
                });
                if (!values) return;
                await request(`/economy/events/${current.id}${query({ reason: values.reason })}`, { method: "DELETE" });
                if (document.querySelector("#admin-event-form").elements.id.value === current.id) resetEventForm();
                await loadEvents();
                setStatus(tr("admin.economy.eventDeleted", "Event deleted."));
                return;
            }
            if (control.dataset.action === "undo-audit") {
                const values = await askAction({ title: tr("admin.audit.undoTitle", "Undo audit action"), description: tr("admin.audit.undoCopy", "Restore the database state from before this action. Undo is available for 24 hours."), confirmLabel: tr("admin.audit.undo", "Undo"), danger: true, fields: [reasonField()] });
                if (!values) return; await request(`/audit/${control.dataset.value}/undo${query({ reason: values.reason })}`, { method: "POST" }); await loadAudit(); setStatus(tr("admin.audit.undone", "Audit action undone.")); return;
            }
            if (control.dataset.action === "page") { const [name, nextPage] = control.dataset.value.split(":"); if (name === "database-browser") await loadDatabaseTable(Number(nextPage)); else await loaders[name](Number(nextPage)); return; }
            if (control.dataset.action === "close-user-editor") { document.querySelector("#admin-user-editor").close(); return; }
            if (control.dataset.action === "close-lobby-editor") { document.querySelector("#admin-lobby-editor").close(); return; }
            if (control.dataset.action === "edit-user") {
                const user = await request(`/users/${control.dataset.value}`); state.currentUser = user;
                const form = document.querySelector("#admin-user-form"); form.elements.id.value = user.id; form.elements.username.value = user.username; form.elements.email.value = user.email; form.elements.status.value = user.status; form.elements.fakeAdmin.checked = user.fakeAdmin; form.elements.MODERATOR.checked = user.roles.includes("MODERATOR"); form.elements.ADMIN.checked = user.roles.includes("ADMIN"); form.elements.reason.value = ""; form.elements.dryRun.checked = false;
                document.querySelector("#admin-user-caption").textContent = `${user.username} · #${user.id}`; document.querySelector("#admin-user-editor").showModal(); return;
            }
            if (control.dataset.action === "load-stats") { await loadStats(control.dataset.value); return; }
            if (control.dataset.action === "edit-stat-row") {
                const target = JSON.parse(decodeURIComponent(control.dataset.value)); const form = document.querySelector("#admin-stats-edit");
                form.hidden = false; form.elements.gameType.value = target.gameType; populateModes(); form.elements.mode.value = target.mode; form.elements.played.value = target.played; form.elements.wins.value = target.wins; form.elements.lastPlayedAt.value = formDateTime(target.lastPlayedAt); form.elements.reason.value = ""; form.elements.dryRun.checked = true; form.elements.reason.focus(); return;
            }
            if (control.dataset.action === "save-user") {
                const form = document.querySelector("#admin-user-form"); const values = formValues(form); const user = state.currentUser; const dryRun = values.dryRun === "on"; const fakeAdmin = values.fakeAdmin === "on"; const changedFields = user.username !== values.username || user.email !== values.email || user.status !== values.status || user.fakeAdmin !== fakeAdmin;
                if (!values.reason.trim()) throw new Error(tr("admin.common.reasonRequired", "A reason is required."));
                if (!dryRun && !await confirmAction(tr("admin.user.saveTitle", "Save user"), tr("admin.user.saveCopy", `Save changes for ${user.username}?`, user.username))) return;
                if (changedFields) await request(`/users/${user.id}`, { method: "PATCH", body: JSON.stringify({ username: values.username, email: values.email, status: values.status, fakeAdmin, reason: values.reason, dryRun }) });
                if (!dryRun) for (const role of ["MODERATOR", "ADMIN"]) { const hasRole = user.roles.includes(role); const wantsRole = values[role] === "on"; if (hasRole !== wantsRole) await request(`/users/${user.id}/roles/${role}${query({ reason: values.reason })}`, { method: wantsRole ? "PUT" : "DELETE" }); }
                setStatus(dryRun ? "User field preview is ready; role changes were not applied." : tr("admin.user.saved", "User updated."));
                if (!dryRun) { document.querySelector("#admin-user-editor").close(); await (state.userDetailId ? loadUserDetail(state.userDetailId) : loadUsers()); } return;
            }
            if (control.dataset.action === "toggle-fake-admin") {
                const user = state.currentUser; const fakeAdmin = !user.fakeAdmin;
                const label = fakeAdmin ? tr("admin.user.makeFakeAdmin", "Make fake admin") : tr("admin.user.removeFakeAdmin", "Remove fake admin");
                const values = await askAction({ title: label, description: tr("admin.user.fakeAdminCopy", "Show or hide this user's inert Admin button.", user.username), confirmLabel: label, fields: [reasonField()] });
                if (!values) return;
                await request(`/users/${user.id}`, { method: "PATCH", body: JSON.stringify({ fakeAdmin, reason: values.reason, dryRun: false }) });
                setStatus(fakeAdmin ? tr("admin.user.fakeAdminEnabled", "Fake admin enabled.") : tr("admin.user.fakeAdminDisabled", "Fake admin removed."));
                await loadUserDetail(user.id); return;
            }
            if (control.dataset.action === "adjust-points") {
                const user = state.currentUser;
                const values = await askAction({ title: tr("admin.user.adjustPoints", "Adjust Points"), description: tr("admin.user.adjustPointsCopy", `Add or remove Points for ${user.username}. Use a negative number to remove Points.`, user.username), confirmLabel: tr("admin.common.confirmChange", "Confirm change"), fields: [{ name: "amount", label: tr("admin.user.pointsAmount", "Point adjustment"), type: "number", step: "1", required: true }, reasonField(), { name: "dryRun", label: tr("admin.stats.preview", "Preview only"), value: "true", options: [{value: "true", label: tr("admin.stats.preview", "Preview only")}, {value: "false", label: tr("admin.user.applyPoints", "Apply adjustment")}]}] });
                if (!values) return;
                const amount = Number(values.amount);
                if (!Number.isSafeInteger(amount) || amount === 0) {
                    setStatus(tr("admin.user.pointsInvalid", "Point adjustment must be a non-zero whole number."), true);
                    return;
                }
                const result = await request(`/users/${user.id}/points`, {method: "PATCH", body: JSON.stringify({amount, reason: values.reason, dryRun: values.dryRun === "true"})});
                setStatus(result.dryRun ? tr("admin.user.pointsPreview", `Preview: ${result.previousBalance} Points → ${result.newBalance} Points`, result.previousBalance, result.newBalance) : tr("admin.user.pointsUpdated", "Points updated."));
                if (!result.dryRun) await loadUserDetail(user.id);
                return;
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
            if (control.dataset.action === "set-fee") {
                const [game, mode, current] = control.dataset.value.split("|");
                await saveFee(game, mode, current); return;
            }
            if (control.dataset.action === "reset-fee") {
                const [game, mode] = control.dataset.value.split("|");
                await clearFee(game, mode); return;
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
                const [game, mode, wanted] = control.dataset.value.split("|");
                await toggleGame(game, mode, wanted);
            }
        } catch (error) { setStatus(error.message, true); }
    });
    const notifyForm = document.querySelector("#admin-notification-form");
    if (notifyForm) {
        const userBlock = document.querySelector("#admin-notify-user");
        const picked = document.querySelector("#admin-notify-picked");
        const userField = notifyForm.elements.userRef;
        const syncMode = () => {
            const oneUser = notifyForm.elements.mode.value === "user";
            userBlock.hidden = !oneUser; userField.required = oneUser;
            if (!oneUser) { picked.hidden = true; }
        };
        notifyForm.elements.mode.addEventListener("change", syncMode);
        // The shared user-search fragment does the finding; this only records the pick.
        userBlock.addEventListener("uc:admin-user-picked", event => {
            userField.value = String(event.detail.id);
            picked.textContent = `${event.detail.username} · #${event.detail.id} · ${event.detail.email}`;
            picked.hidden = false;
        });
        notifyForm.addEventListener("submit", async event => {
            event.preventDefault();
            const values = formValues(notifyForm);
            try {
                if (values.mode === "user" && !values.userRef)
                    throw new Error(tr("admin.notify.pickUser", "Search for a user and pick one from the results."));
                const userId = values.mode === "user" ? values.userRef : null;
                if (!await confirmAction(tr("admin.notify.send", "Send notification"), userId ? tr("admin.notifications.sendOneCopy", `Send this notification to user #${userId}?`, userId) : tr("admin.notifications.sendAllCopy", "Send this notification to every user?"), !userId)) return;
                await request(`/notifications${userId ? `/users/${userId}` : "/all"}`, { method: "POST", body: JSON.stringify({ message: values.message, reason: values.reason }) });
                notifyForm.reset(); syncMode(); picked.hidden = true;
                setStatus(tr("admin.notifications.sent", "Notification sent."));
            } catch (error) { setStatus(error.message, true); }
        });
        syncMode();
    }
    populateModes();

    document.querySelectorAll("form.admin-filters[data-filters]").forEach(form => {
        for (const [key, value] of params) { const field = form.elements[key]; if (field && !(field instanceof RadioNodeList) && field.tagName !== "FIELDSET") field.value = value; }
    });
    syncGamesModeFilter();
    if (page === "events") resetEventForm();
    if (page === "notifications" && params.get("userId")) {
        notifyForm.elements.mode.value = "user";
        notifyForm.elements.userRef.value = params.get("userId");
        // `picked` is scoped to the notifyForm block above, so re-query it here.
        const preselected = document.querySelector("#admin-notify-picked");
        preselected.textContent = `#${params.get("userId")}`;
        preselected.hidden = false;
        notifyForm.elements.mode.dispatchEvent(new Event("change"));
    }
    const statsUser = params.get("userId");
    if (page === "stats" && statsUser) {
        loadStats(statsUser).then(() => setStatus(tr("admin.updated", `Updated ${formatClock(new Date())}.`, formatClock(new Date())))).catch(error => setStatus(error.message, true));
    } else refresh();
})();
