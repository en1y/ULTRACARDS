/* Shared live-game contract. Game scripts provide only rules, copy, and optional
 * board capabilities; card rendering and motion stay in game.js. */
(function () {
    const adapters = Object.create(null);

    function register(adapter) {
        if (!adapter || !adapter.name) throw new Error('A live-game adapter needs a name');
        const name = String(adapter.name).toLowerCase();
        const existing = adapters[name] || {};
        const merged = {
            ...existing,
            name,
            ...adapter,
            features: {...(existing.features || {}), ...(adapter.features || {})}
        };
        adapters[name] = merged;
        return merged;
    }

    function get(name) {
        return adapters[String(name || '').toLowerCase()] || null;
    }

    function copy(adapter, key, ...args) {
        if (typeof adapter?.copy === 'function') return adapter.copy(key, ...args);
        return typeof window.t === 'function' ? window.t(key, ...args) : key;
    }

    function feature(adapter, name, fallback = false) {
        return adapter?.features?.[name] ?? fallback;
    }

    window.UltracardsGameRuntime = {register, get, copy, feature};
})();
