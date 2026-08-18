// Calendar + clock picker for the [data-date-time] fragment. The canonical value lives in the widget's
// hidden input as dd.mm.yyyy HH:mm (AGENTS.md); the text field and the popup are both just editors of it.
(() => {
    const pad = number => String(number).padStart(2, "0");
    const PATTERN = /^(\d{2})\.(\d{2})\.(\d{4}) (\d{2}):(\d{2})$/;
    const tr = (key, fallback) => typeof window.t === "function" && (window.__I18N__ || {})[key] ? window.t(key) : fallback;
    const locale = () => document.documentElement.lang || "en-GB";

    const parse = value => {
        const match = PATTERN.exec((value || "").trim());
        if (!match) return null;
        const [, day, month, year, hour, minute] = match.map(Number);
        const date = new Date(year, month - 1, day, hour, minute);
        // Rejects 31.02 and friends, which Date would silently roll over.
        return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
            && date.getHours() === hour && date.getMinutes() === minute ? date : null;
    };
    const format = date => `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    const sameDay = (left, right) => left.getFullYear() === right.getFullYear()
        && left.getMonth() === right.getMonth() && left.getDate() === right.getDate();

    const el = (tag, text, className) => {
        const node = document.createElement(tag);
        if (text != null) node.textContent = text;
        if (className) node.className = className;
        return node;
    };
    const button = (text, className) => { const node = el("button", text, className); node.type = "button"; return node; };

    let popup = null;
    let parts = null;
    let active = null;
    let draft = new Date();
    let view = new Date();
    let mode = "hour";

    const build = () => {
        popup = el("div", null, "uc-datetime-popup");
        popup.setAttribute("role", "dialog");
        popup.setAttribute("aria-label", tr("admin.dateTime.open", "Open the calendar and clock"));
        popup.hidden = true;

        const readout = el("div", null, "uc-datetime-readout");
        const dateTab = button("", "uc-datetime-tab");
        const hourTab = button("", "uc-datetime-unit");
        const minuteTab = button("", "uc-datetime-unit");
        const time = el("span", null, "uc-datetime-readout-time");
        time.append(hourTab, el("span", ":", "uc-datetime-colon"), minuteTab);
        readout.append(dateTab, time);

        const calendar = el("div", null, "uc-datetime-pane");
        calendar.setAttribute("role", "group");
        calendar.setAttribute("aria-label", tr("admin.dateTime.date", "Date"));
        const head = el("div", null, "uc-datetime-month");
        const previous = button("‹", "uc-datetime-step");
        const next = button("›", "uc-datetime-step");
        const title = el("strong");
        previous.setAttribute("aria-label", tr("admin.dateTime.previousMonth", "Previous month"));
        next.setAttribute("aria-label", tr("admin.dateTime.nextMonth", "Next month"));
        head.append(previous, title, next);
        const weekdays = el("div", null, "uc-datetime-weekdays");
        const grid = el("div", null, "uc-datetime-grid");
        calendar.append(head, weekdays, grid);

        const clockPane = el("div", null, "uc-datetime-pane");
        clockPane.setAttribute("role", "group");
        clockPane.setAttribute("aria-label", tr("admin.dateTime.time", "Time · 24-hour"));
        const dial = el("div", null, "uc-clock");
        const hand = el("span", null, "uc-clock-hand");
        const centre = el("span", null, "uc-clock-centre");
        dial.append(hand, centre);
        clockPane.append(dial);

        const foot = el("div", null, "uc-datetime-foot");
        const now = button(tr("admin.dateTime.now", "Now"), "btn");
        const clear = button(tr("admin.dateTime.clear", "Clear"), "btn");
        const done = button(tr("admin.dateTime.done", "Done"), "btn btn-accent");
        foot.append(now, clear, done);

        popup.append(readout, calendar, clockPane, foot);
        document.body.append(popup);
        parts = { dateTab, hourTab, minuteTab, calendar, clockPane, title, weekdays, grid, dial, hand };

        dateTab.addEventListener("click", () => show("date"));
        hourTab.addEventListener("click", () => show("hour"));
        minuteTab.addEventListener("click", () => show("minute"));
        previous.addEventListener("click", () => { view = new Date(view.getFullYear(), view.getMonth() - 1, 1); renderCalendar(); });
        next.addEventListener("click", () => { view = new Date(view.getFullYear(), view.getMonth() + 1, 1); renderCalendar(); });
        now.addEventListener("click", () => { draft = new Date(); draft.setSeconds(0, 0); view = new Date(draft); commit(); render(); });
        clear.addEventListener("click", () => { write(""); close(); });
        done.addEventListener("click", close);

        let dragging = false;
        const track = event => {
            if (mode === "date") return;
            const rect = dial.getBoundingClientRect();
            const x = event.clientX - (rect.left + rect.width / 2);
            const y = event.clientY - (rect.top + rect.height / 2);
            let degrees = Math.atan2(y, x) * 180 / Math.PI + 90;
            if (degrees < 0) degrees += 360;
            if (mode === "minute") { draft.setMinutes(Math.round(degrees / 6) % 60); }
            else {
                const index = Math.round(degrees / 30) % 12;
                // Inner ring is 00 and 13-23, matching a 24-hour Material dial.
                const inner = Math.hypot(x, y) / (rect.width / 2) < 0.68;
                draft.setHours(inner ? (index === 0 ? 0 : index + 12) : (index === 0 ? 12 : index));
            }
            commit();
            render();
        };
        dial.addEventListener("pointerdown", event => {
            dragging = true;
            dial.setPointerCapture(event.pointerId);
            track(event);
        });
        dial.addEventListener("pointermove", event => { if (dragging) track(event); });
        dial.addEventListener("pointerup", () => {
            dragging = false;
            if (mode === "hour") show("minute");
        });
    };

    const write = value => {
        if (!active) return;
        active.hidden.value = value;
        active.input.value = value;
        active.input.setCustomValidity("");
    };
    const commit = () => write(format(draft));

    const renderReadout = () => {
        parts.dateTab.textContent = `${pad(draft.getDate())}.${pad(draft.getMonth() + 1)}.${draft.getFullYear()}`;
        parts.hourTab.textContent = pad(draft.getHours());
        parts.minuteTab.textContent = pad(draft.getMinutes());
        parts.dateTab.classList.toggle("is-active", mode === "date");
        parts.hourTab.classList.toggle("is-active", mode === "hour");
        parts.minuteTab.classList.toggle("is-active", mode === "minute");
        parts.calendar.hidden = mode !== "date";
        parts.clockPane.hidden = mode === "date";
    };

    const renderCalendar = () => {
        parts.title.textContent = new Intl.DateTimeFormat(locale(), { month: "long", year: "numeric" }).format(view);
        const short = new Intl.DateTimeFormat(locale(), { weekday: "short" });
        // 2024-01-01 was a Monday, so this always starts the week on Monday.
        parts.weekdays.replaceChildren(...Array.from({ length: 7 },
            (unused, index) => el("span", short.format(new Date(2024, 0, 1 + index)))));
        const first = new Date(view.getFullYear(), view.getMonth(), 1);
        const start = new Date(first.getFullYear(), first.getMonth(), 1 - ((first.getDay() + 6) % 7));
        const today = new Date();
        parts.grid.replaceChildren(...Array.from({ length: 42 }, (unused, index) => {
            const day = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index);
            const node = button(String(day.getDate()), "uc-datetime-day");
            node.classList.toggle("is-outside", day.getMonth() !== view.getMonth());
            node.classList.toggle("is-today", sameDay(day, today));
            node.classList.toggle("is-selected", sameDay(day, draft));
            if (sameDay(day, draft)) node.setAttribute("aria-current", "date");
            node.addEventListener("click", () => {
                draft.setFullYear(day.getFullYear(), day.getMonth(), day.getDate());
                view = new Date(day.getFullYear(), day.getMonth(), 1);
                commit();
                show("hour");
            });
            return node;
        }));
    };

    const renderClock = () => {
        const minute = mode === "minute";
        const values = minute
            ? Array.from({ length: 12 }, (unused, index) => ({ value: index * 5, index, radius: 42 }))
            : [...Array.from({ length: 12 }, (unused, index) => ({ value: index === 0 ? 12 : index, index, radius: 42 })),
                ...Array.from({ length: 12 }, (unused, index) => ({ value: index === 0 ? 0 : index + 12, index, radius: 24 }))];
        const current = minute ? draft.getMinutes() : draft.getHours();
        parts.dial.querySelectorAll(".uc-clock-number").forEach(node => node.remove());
        values.forEach(({ value, index, radius }) => {
            const angle = index / 12 * 2 * Math.PI - Math.PI / 2;
            const node = button(pad(value), "uc-clock-number");
            node.style.left = `${50 + Math.cos(angle) * radius}%`;
            node.style.top = `${50 + Math.sin(angle) * radius}%`;
            node.classList.toggle("is-inner", radius < 42);
            node.classList.toggle("is-selected", value === current);
            node.addEventListener("click", () => {
                if (minute) draft.setMinutes(value); else draft.setHours(value);
                commit();
                if (minute) render(); else show("minute");
            });
            parts.dial.append(node);
        });
        const steps = minute ? 60 : 12;
        const position = minute ? current : current % 12;
        parts.hand.style.height = `${!minute && (current === 0 || current > 12) ? 24 : 42}%`;
        parts.hand.style.transform = `translate(-50%, -100%) rotate(${position / steps * 360}deg)`;
    };

    const render = () => { renderReadout(); if (mode === "date") renderCalendar(); else renderClock(); };
    const show = next => { mode = next; render(); if (active) place(); };

    const place = () => {
        const rect = active.widget.getBoundingClientRect();
        const width = popup.offsetWidth;
        const height = popup.offsetHeight;
        const room = window.innerHeight - rect.bottom;
        const top = room < height + 12 && rect.top > height + 12 ? rect.top - height - 8 : rect.bottom + 8;
        popup.style.top = `${Math.max(8, Math.min(top, window.innerHeight - height - 8))}px`;
        popup.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - width - 8))}px`;
    };

    const close = (restoreFocus = true) => {
        if (!active) return;
        const trigger = active.trigger;
        popup.hidden = true;
        trigger.setAttribute("aria-expanded", "false");
        active = null;
        if (restoreFocus) trigger.focus();
    };

    const open = widget => {
        if (!popup) build();
        if (active && active.widget === widget) return close();
        if (active) close(false);
        active = {
            widget,
            hidden: widget.querySelector('input[type="hidden"]'),
            input: widget.querySelector("[data-date-time-input]"),
            trigger: widget.querySelector("[data-date-time-open]")
        };
        draft = parse(active.hidden.value) || (() => { const start = new Date(); start.setSeconds(0, 0); return start; })();
        view = new Date(draft.getFullYear(), draft.getMonth(), 1);
        popup.hidden = false;
        active.trigger.setAttribute("aria-expanded", "true");
        show("date");
        parts.dateTab.focus();
    };

    // Typing stays first-class: the field owns the value, the popup only writes into it.
    const readInput = widget => {
        const hidden = widget.querySelector('input[type="hidden"]');
        const input = widget.querySelector("[data-date-time-input]");
        const value = input.value.trim();
        const date = value ? parse(value) : null;
        hidden.value = date ? format(date) : "";
        input.setCustomValidity(value && !date ? tr("admin.dateTime.invalid", "Use dd.mm.yyyy HH:mm with 24-hour time.") : "");
    };

    const sync = scope => (scope || document).querySelectorAll("[data-date-time]").forEach(widget => {
        const hidden = widget.querySelector('input[type="hidden"]');
        const input = widget.querySelector("[data-date-time-input]");
        input.value = hidden.value || "";
        input.setCustomValidity("");
    });

    document.addEventListener("click", event => {
        const trigger = event.target.closest("[data-date-time-open]");
        if (trigger) return open(trigger.closest("[data-date-time]"));
        // Clock buttons are replaced after each selection. composedPath still records that the click
        // came from inside the popup even after the original button has been detached.
        if (active && !event.composedPath().includes(popup)) close(false);
    });
    document.addEventListener("input", event => {
        const widget = event.target.closest("[data-date-time]");
        if (widget && event.target.matches("[data-date-time-input]")) readInput(widget);
    });
    document.addEventListener("keydown", event => { if (event.key === "Escape" && active) { close(); } });
    window.addEventListener("resize", () => { if (active) place(); });
    window.addEventListener("scroll", () => { if (active) place(); }, true);

    window.UltracardsDateTime = { sync, parse, format };
    document.addEventListener("DOMContentLoaded", () => sync(document));
})();
