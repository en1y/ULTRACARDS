(() => {
    // Reclaim space from the old localStorage card-image cache (now browser-cached).
    try {
        for (let i = localStorage.length - 1; i >= 0; i--) {
            const k = localStorage.key(i);
            if (k && k.startsWith('uc-card-img:')) localStorage.removeItem(k);
        }
    } catch (_) {}
})();

(() => {
    // Keep the screen awake while a game or replay is open, like a video player.
    // The browser releases the lock whenever the tab is hidden; re-acquire it on
    // return. Unsupported browsers / denied requests just fall back to normal
    // screen-off behavior.
    if (!('wakeLock' in navigator)) return;
    const acquire = () => {
        navigator.wakeLock.request('screen').catch(() => undefined);
    };
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') acquire();
    });
    acquire();
})();

(function () {
    const MOTION = {
        quickMs: 160,
        standardMs: 300,
        dealMs: 520,
        ease: 'out(3)',
        snapEase: 'out(4)'
    };
    const CARD_ASSET_VERSION = '2';
    const VIEWPORT_DRAG_PADDING = 14;
    const ITALIAN_SUIT_MAP = {C: 'COPPE', D: 'DENARI', S: 'SPADE', B: 'BASTONI'};
    const ITALIAN_VALUE_MAP = {
        '1': 'ACE',
        '2': 'TWO',
        '3': 'THREE',
        '4': 'FOUR',
        '5': 'FIVE',
        '6': 'SIX',
        '7': 'SEVEN',
        '11': 'JACK',
        '12': 'KNIGHT',
        '13': 'KING'
    };
    const POKER_SUIT_MAP = {H: 'HEARTS', D: 'DIAMONDS', C: 'CLUBS', S: 'SPADES'};
    const POKER_VALUE_MAP = {
        '2': 'TWO',
        '3': 'THREE',
        '4': 'FOUR',
        '5': 'FIVE',
        '6': 'SIX',
        '7': 'SEVEN',
        '8': 'EIGHT',
        '9': 'NINE',
        '10': 'TEN',
        '11': 'JACK',
        '12': 'QUEEN',
        '13': 'KING',
        '14': 'ACE'
    };

    const animeApi = window.anime || {};
    const interactApi = window.interact;
    const gsap = window.gsap || null;
    const Flip = window.Flip || null;
    const useNativeCardAnimations = window.CSS?.supports?.('-moz-appearance', 'none') === true;
    if (gsap && Flip) gsap.registerPlugin(Flip);

    function prefersReducedMotion() {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    }

    function normalizeCardType(cardType) {
        return String(cardType || 'ITALIAN').toUpperCase();
    }

    function cardUrl(card) {
        const cardType = normalizeCardType(typeof card === 'object' ? card?.cardType : null);
        const code = typeof card === 'object' ? card?.card : card;
        if (!code) return cardBackUrl(cardType);
        if (cardType === 'POKER') return pokerCardUrl(code);
        return italianCardUrl(code);
    }

    function italianCardUrl(code) {
        if (!code) return '';
        const suitLetter = String(code).charAt(0).toUpperCase();
        const valueNum = String(code).slice(1);
        const suit = ITALIAN_SUIT_MAP[suitLetter];
        const value = ITALIAN_VALUE_MAP[valueNum];
        return suit && value ? `/api/cards/italian/${suit}/${value}` : '';
    }

    function pokerCardUrl(code) {
        if (!code) return '';
        const suitLetter = String(code).charAt(0).toUpperCase();
        const valueNum = String(code).slice(1);
        const suit = POKER_SUIT_MAP[suitLetter];
        const value = POKER_VALUE_MAP[valueNum];
        return suit && value ? `/api/cards/poker/${suit}/${value}` : '';
    }

    function cardBackUrl(cardType) {
        const url = normalizeCardType(cardType) === 'POKER'
            ? '/api/cards/poker/back'
            : '/api/cards/italian/back';
        return `${url}?v=${CARD_ASSET_VERSION}`;
    }

    function cardKey(card) {
        if (!card) return '';
        return `${normalizeCardType(card.cardType)}:${card.card || ''}`;
    }

    // Set a card image's src. The browser caches the PNG itself (Cache-Control on
    // /api/cards/*), so there is no JS-side cache. On a transient load failure we
    // re-request a few times so a card never stays blank ("Played card" alt).
    function applyCardImage(img, url) {
        if (!img) return;
        if (!url) { img.src = ''; img.onerror = null; return; }
        let attempts = 0;
        img.onerror = () => {
            if (attempts >= 3) { img.onerror = null; return; }
            attempts += 1;
            // small backoff, then re-request; cache-bust only on the last try
            setTimeout(() => {
                img.src = attempts >= 3 ? url + (url.includes('?') ? '&' : '?') + 'r=' + attempts : url;
            }, 300 * attempts);
        };
        img.src = url;
    }

    // Fresh card <img>s decode lazily on first paint; on mobile (especially Gecko)
    // that decode lands mid-flight as a dropped frame and a blank card. Decode
    // before animating, bounded so a slow decode can never stall the game.
    function decodeCardImages(el, timeoutMs) {
        const images = el ? Array.from(el.querySelectorAll('img')) : [];
        if (!images.length) return Promise.resolve();
        const decodes = images.map((img) => (typeof img.decode === 'function'
            ? img.decode().catch(() => undefined)
            : Promise.resolve()));
        return Promise.race([
            Promise.all(decodes),
            new Promise((resolve) => setTimeout(resolve, timeoutMs ?? 150))
        ]);
    }

    function createCardSide(className, alt, url) {
        const image = document.createElement('img');
        image.className = className;
        image.alt = alt;
        image.decoding = 'async';
        image.loading = 'eager';
        applyCardImage(image, url);
        return image;
    }

    function renderCardImage(options) {
        const card = options?.card || null;
        const cardType = normalizeCardType(card?.cardType || options?.cardType);
        const flippable = options?.flippable === true;

        const wrap = document.createElement('div');
        wrap.className = (options?.className || 'game-card') + ' card-wrap';
        wrap.classList.toggle('is-flippable', flippable);
        wrap.dataset.cardType = cardType;
        wrap.dataset.cardFace = card ? 'true' : 'false';
        if (card) {
            wrap.dataset.cardCode = card.card || '';
            wrap.dataset.cardKey = cardKey(card);
        }

        let inner = null;
        if (flippable) {
            inner = document.createElement('div');
            inner.className = 'card-inner';
            inner.appendChild(createCardSide('card-front', options?.alt || (card ? 'Card' : ''), card ? cardUrl(card) : ''));
            inner.appendChild(createCardSide('card-back', card ? '' : (options?.alt || t('game.cardBack.alt')), cardBackUrl(cardType)));
            wrap.appendChild(inner);
        } else {
            wrap.appendChild(createCardSide(
                card ? 'card-front' : 'card-back',
                options?.alt || (card ? 'Card' : t('game.cardBack.alt')),
                card ? cardUrl(card) : cardBackUrl(cardType)
            ));
        }

        // Every card carries its own flip ("turn around") API, so callers never
        // need to spawn a second element to reveal a face.
        const resolveCardData = (c) => c
            || (wrap.dataset.cardCode ? {cardType: wrap.dataset.cardType, card: wrap.dataset.cardCode} : null);
        wrap.cardApi = {
            el: wrap,
            flip(c) { return flipCardReveal(wrap, resolveCardData(c)); },
            showBack() {
                if (!inner) return;
                wrap.dataset.cardFace = 'false';
                if (gsap && !useNativeCardAnimations) gsap.set(inner, {rotateY: 0});
                else inner.style.transform = 'rotateY(0deg)';
            }
        };
        return wrap;
    }

    function createCard(options) {
        return renderCardImage(options);
    }

    function hydrateCardImages(root) {
        const scope = root || document;
        // card-wrap containers (new structure)
        scope.querySelectorAll('.card-wrap[data-card-face="true"]').forEach((wrap) => {
            const src = cardUrl({
                cardType: wrap.dataset.cardType || 'ITALIAN',
                card: wrap.dataset.cardCode
            });
            const front = wrap.querySelector('.card-front');
            if (front && src) applyCardImage(front, src);
        });
        scope.querySelectorAll('.card-wrap[data-card-face="false"]').forEach((wrap) => {
            const back = wrap.querySelector('.card-back');
            if (back) applyCardImage(back, cardBackUrl(wrap.dataset.cardType || 'ITALIAN'));
        });
        // legacy bare img elements (for game.html generic fallback)
        scope.querySelectorAll('img[data-card-code][data-card-face="true"]:not(.card-front):not(.card-back)').forEach((img) => {
            const src = cardUrl({cardType: img.dataset.cardType || 'ITALIAN', card: img.dataset.cardCode});
            if (src) applyCardImage(img, src);
        });
        scope.querySelectorAll('img[data-card-face="false"]:not(.card-front):not(.card-back)').forEach((img) => {
            applyCardImage(img, cardBackUrl(img.dataset.cardType || 'ITALIAN'));
        });
    }

    function ensureOverlayLayer() {
        let layer = document.getElementById('game-animation-layer');
        if (!layer) {
            layer = document.createElement('div');
            layer.id = 'game-animation-layer';
            layer.className = 'game-animation-layer';
            document.body.appendChild(layer);
        }
        return layer;
    }

    function mapEase(animeEase) {
        if (!animeEase) return 'power2.out';
        const str = String(animeEase);
        if (str.startsWith('out(')) {
            const n = Math.min(Math.round(parseFloat(str.slice(4))), 4);
            return `power${n}.out`;
        }
        if (str.startsWith('inOut(')) {
            const n = Math.min(Math.round(parseFloat(str.slice(6))), 4);
            return `power${n}.inOut`;
        }
        if (str.startsWith('in(')) {
            const n = Math.min(Math.round(parseFloat(str.slice(3))), 4);
            return `power${n}.in`;
        }
        return 'power2.out';
    }

    // Gecko keeps the transition "before-change style" at the last MAIN-THREAD
    // restyle — for a compositor-driven flight that's the flight START. If a
    // transform transition re-enables in the same restyle that commits the flight's
    // end state, Gecko transitions from that stale start position: the card visibly
    // flies twice. Commit with transitions off, flush, then restore.
    function commitWithoutTransition(el, mutate) {
        const previous = el.style.transition;
        el.style.transition = 'none';
        mutate();
        void el.offsetWidth;   // restyle now: the landed state becomes the baseline
        el.style.transition = previous;
    }

    function playAnimation(target, parameters) {
        if (!target) return Promise.resolve();
        if (prefersReducedMotion()) {
            applyFinalAnimationState(target, parameters);
            return Promise.resolve();
        }
        if (useNativeCardAnimations && typeof target.animate === 'function') {
            return playWebAnimationFallback(target, parameters);
        }
        if (gsap) return playGsapAnimation(target, parameters);
        return playWebAnimationFallback(target, parameters);
    }

    function playGsapAnimation(target, parameters) {
        const durationSec = (Number(parameters?.duration) || MOTION.standardMs) / 1000;
        const delaySec = (Number(parameters?.delay) || 0) / 1000;
        const ease = mapEase(parameters?.ease || MOTION.ease);

        const transforms = Array.isArray(parameters?.transform) ? parameters.transform : (parameters?.transform != null ? [parameters.transform] : null);
        const opacities = Array.isArray(parameters?.opacity) ? parameters.opacity : (parameters?.opacity != null ? [parameters.opacity] : null);

        // Set initial state
        if (transforms && transforms.length > 1) gsap.set(target, {transform: transforms[0]});
        if (opacities && opacities.length > 1) gsap.set(target, {opacity: opacities[0]});

        const toVars = {duration: durationSec, delay: delaySec, ease};
        if (transforms) toVars.transform = transforms[transforms.length - 1];
        if (opacities) toVars.opacity = opacities[opacities.length - 1];

        // Three-keyframe arc: use timeline
        if (transforms && transforms.length === 3) {
            return new Promise((resolve) => {
                const tl = gsap.timeline({delay: delaySec, onComplete: resolve});
                tl.to(target, {
                    transform: transforms[1],
                    opacity: opacities?.[1],
                    duration: durationSec * 0.55,
                    ease
                });
                tl.to(target, {
                    transform: transforms[2],
                    opacity: opacities?.[2],
                    duration: durationSec * 0.45,
                    ease
                });
            });
        }

        return new Promise((resolve) => {
            gsap.to(target, {...toVars, onComplete: resolve});
        });
    }

    function applyFinalAnimationState(target, parameters) {
        if (Array.isArray(parameters?.transform)) {
            target.style.transform = parameters.transform[parameters.transform.length - 1] || '';
        } else if (parameters?.transform != null) {
            target.style.transform = parameters.transform;
        }
        if (Array.isArray(parameters?.opacity)) {
            target.style.opacity = parameters.opacity[parameters.opacity.length - 1];
        } else if (parameters?.opacity != null) {
            target.style.opacity = parameters.opacity;
        }
    }

    function playWebAnimationFallback(target, parameters) {
        const from = {};
        const to = {};
        if (Array.isArray(parameters?.transform)) {
            from.transform = parameters.transform[0];
            to.transform = parameters.transform[parameters.transform.length - 1];
        }
        if (Array.isArray(parameters?.opacity)) {
            from.opacity = parameters.opacity[0];
            to.opacity = parameters.opacity[parameters.opacity.length - 1];
        }
        if (!Object.keys(from).length) {
            applyFinalAnimationState(target, parameters);
            return Promise.resolve();
        }
        const animation = target.animate([from, to], {
            duration: parameters.duration || MOTION.standardMs,
            delay: parameters.delay || 0,
            easing: 'cubic-bezier(.22,.9,.3,1)',
            fill: 'both'
        });
        return animation.finished.catch(() => undefined).then(() => {
            applyFinalAnimationState(target, parameters);
            animation.cancel();
        });
    }

    function registerZone(element, options) {
        if (!element) return null;
        const zone = {
            element,
            options: {...(options || {})},
            reservedIndex: null,
            placeholder: null
        };
        element.classList.add('game-hand', 'game-zone');
        if (zone.options.type) element.dataset.zoneType = zone.options.type;
        if (zone.options.zoneId) element.dataset.zoneId = zone.options.zoneId;
        element.__ucGameZone = zone;
        layoutZone(zone);
        return zone;
    }

    function getZone(zoneOrElement) {
        if (!zoneOrElement) return null;
        if (zoneOrElement.element) return zoneOrElement;
        return zoneOrElement.__ucGameZone || registerZone(zoneOrElement, {});
    }

    function getZoneCards(zone) {
        const resolved = getZone(zone);
        if (!resolved) return [];
        return Array.from(resolved.element.children)
            .filter((el) => el.nodeType === 1)
            .filter((el) => !el.classList.contains('game-hand-placeholder'))
            .filter((el) => !el.hidden);
    }

    function ensurePlaceholder(zone) {
        const resolved = getZone(zone);
        if (!resolved) return null;
        if (!resolved.placeholder) {
            const placeholder = document.createElement('div');
            placeholder.className = 'game-hand-placeholder';
            placeholder.setAttribute('aria-hidden', 'true');
            resolved.placeholder = placeholder;
        }
        return resolved.placeholder;
    }

    function reserveSlot(zone, index) {
        const resolved = getZone(zone);
        if (!resolved) return null;
        const cards = getZoneCards(resolved);
        resolved.reservedIndex = Math.max(0, Math.min(index ?? cards.length, cards.length));
        const placeholder = ensurePlaceholder(resolved);
        animateZoneChange(resolved, () => {
            const currentCards = getZoneCards(resolved);
            const before = currentCards[resolved.reservedIndex] || null;
            resolved.element.insertBefore(placeholder, before);
        });
        return placeholder;
    }

    function clearReservedSlot(zone) {
        const resolved = getZone(zone);
        if (!resolved) return;
        resolved.reservedIndex = null;
        const placeholder = resolved.placeholder;
        if (!placeholder || !placeholder.parentElement) return;
        animateZoneChange(resolved, () => placeholder.remove());
    }

    function layoutZone(zone, cards, options) {
        const resolved = getZone(zone);
        if (!resolved) return [];
        if (options) resolved.options = {...resolved.options, ...options};
        const element = resolved.element;
        // Skip fan positioning for deck-type hands
        const handType = element.dataset.handType || resolved.options.handType;
        if (handType === 'deck') return getZoneCards(resolved);
        const list = cards ? Array.from(cards) : getZoneCards(resolved);
        const placeholder = resolved.placeholder?.parentElement === element ? resolved.placeholder : null;
        const layoutItems = [];
        list.forEach((card) => {
            if (!card.classList.contains('is-dragging')) layoutItems.push({el: card, placeholder: false});
        });
        if (placeholder) {
            const index = Math.max(0, Math.min(resolved.reservedIndex ?? layoutItems.length, layoutItems.length));
            layoutItems.splice(index, 0, {el: placeholder, placeholder: true});
        }

        const total = layoutItems.length;
        const rect = element.getBoundingClientRect();
        // Prefer a real card to size the zone; otherwise measure the placeholder itself.
        // Its CSS width is the zone's correct card size (e.g. --trick-card-width for the
        // trick zone), so a first-played card's reserved-slot preview and fly target are
        // sized right — not the --hand-card-width / 100px fallback.
        let sample = layoutItems.find((item) => !item.placeholder)?.el;
        if (!sample && placeholder) {
            placeholder.style.width = '';   // drop any stale inline width before measuring
            sample = placeholder;
        }
        const style = getComputedStyle(element);
        // Use the UNROTATED layout size (offsetWidth/Height), not the rotated bounding
        // box — otherwise spacing depends on each card's tilt/spin, making a reserved
        // placeholder (no spin) land a few px off from the real card.
        const baseWidth = resolved.options.cardWidth
            || sample?.offsetWidth
            || Number.parseFloat(style.getPropertyValue('--hand-card-width'))
            || 100;
        const baseHeight = resolved.options.cardHeight || sample?.offsetHeight || baseWidth * 1.38;
        const zoneType = resolved.options.type || resolved.options.zoneType || element.dataset.zoneType;
        const maxTilt = resolved.options.maxTilt ?? (zoneType === 'center' ? 4 : 6);
        // The fan must FIT the zone including the edge cards' tilt: a rotated card's
        // bounding box is wider than the card itself, so spreading over
        // (width - baseWidth) made a full strongly-arced hand overhang the screen
        // edges. Budget the rotated visual width instead. Cards rotate about a LOW
        // origin (50% ~88%), so the top corners sweep ~2·0.88·h·sin(tilt) horizontally.
        const tiltRad = Math.abs(maxTilt) * Math.PI / 180;
        const visualWidth = baseWidth * Math.cos(tiltRad) + 1.76 * baseHeight * Math.sin(tiltRad);
        const available = Math.max(rect.width - visualWidth, baseWidth);
        // Optional fixed slot count reserves the full footprint. Fan zones still center
        // partial hands, so 1-2 cards do not sit in the left side of a 3-card fan.
        const fixedSlots = Number(resolved.options.slotTotal) || 0;
        const slots = Math.max(fixedSlots, total);
        const positionSlots = zoneType === 'fan' ? Math.max(total, 1) : slots;
        // Tighter, overlap-based spacing — flexible for any card count.
        const spacingScale = resolved.options.spacingScale ?? (zoneType === 'center' ? 0.45 : 0.4);
        const naturalSpacing = baseWidth * spacingScale;
        const spacing = slots > 1 ? Math.min(naturalSpacing, available / (slots - 1)) : 0;
        const yArc = resolved.options.yArc ?? (zoneType === 'center' ? 3 : 5);
        const baseOffsetY = Number(resolved.options.baseOffsetY) || 0;

        if (zoneType !== 'mini' && zoneType !== 'fan') {
            const minWidth = Math.ceil(baseWidth + (slots > 1 ? spacing * (slots - 1) : 0) + 28);
            const minHeight = Math.ceil(baseHeight + yArc + Math.abs(baseOffsetY) + 28);
            element.style.minWidth = `${minWidth}px`;
            element.style.minHeight = `${minHeight}px`;
        }

        layoutItems.forEach((item, index) => {
            const centered = index - ((positionSlots - 1) / 2);
            const normalized = positionSlots > 1 ? centered / ((positionSlots - 1) / 2) : 0;
            const x = centered * spacing;
            const y = Math.abs(normalized) * yArc + baseOffsetY;
            const rotation = normalized * maxTilt;
            const el = item.el;
            el.classList.add('game-hand-card');
            // Positions are driven purely by the CSS-var slot transform. Clear any
            // stale inline transform/opacity a prior animation may have left behind,
            // otherwise it would override the slot transform and strand the card.
            // Also reset the deal-offset vars on any card that is NOT mid-deal, so a
            // deal whose tween was interrupted can never leave a card permanently
            // shifted away from its slot.
            if (!item.placeholder) {
                el.style.transform = '';
                el.style.opacity = '';
                if (!el.classList.contains('is-dealing') && !el.classList.contains('is-flying')) {
                    el.style.removeProperty('--deal-x');
                    el.style.removeProperty('--deal-y');
                    el.style.removeProperty('--deal-rot');
                    el.style.removeProperty('--deal-scale');
                }
            }
            el.style.width = item.placeholder ? `${Math.round(baseWidth)}px` : '';
            el.style.setProperty('--slot-x', `${x.toFixed(2)}px`);
            el.style.setProperty('--slot-y', `${y.toFixed(2)}px`);
            el.style.setProperty('--slot-rot', `${rotation.toFixed(2)}deg`);
            el.style.setProperty('--tilt', `${rotation.toFixed(2)}deg`);
            el.style.setProperty('--slot-scale', item.placeholder ? '0.96' : '1');
            el.style.zIndex = String(index + 1);
        });
        return layoutItems.map((item) => item.el);
    }

    function animateFromRect(el, firstRect, lastRect, duration, ease) {
        if (!el || !firstRect || !lastRect) return Promise.resolve();
        const dx = firstRect.left - lastRect.left;
        const dy = firstRect.top - lastRect.top;
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return Promise.resolve();
        const computedTransform = getComputedStyle(el).transform;
        const baseTransform = computedTransform === 'none' ? '' : computedTransform;
        const flipToken = (el.__ucFlipToken || 0) + 1;
        el.__ucFlipToken = flipToken;
        el.classList.add('is-flipping');
        const finish = () => {
            // A newer reflow may have started before this one finished. Only the
            // latest pass may clear the animation state and inline transform.
            if (el.__ucFlipToken !== flipToken) return;
            commitWithoutTransition(el, () => {
                el.style.transform = '';
                el.classList.remove('is-flipping');
            });
        };
        return playAnimation(el, {
            // Both keyframes share one transform-function list so the browser
            // interpolates per-function (compositor-friendly) instead of falling
            // back to matrix decomposition.
            transform: [
                `translate3d(${dx}px, ${dy}px, 0) ${baseTransform}`,
                `translate3d(0, 0, 0) ${baseTransform}`.trim()
            ],
            duration: duration ?? MOTION.standardMs,
            ease: ease ?? MOTION.ease
        }).then(finish, finish);
    }

    // Reflow a zone through the CSS-variable slot transform and a FLIP pass. The
    // temporary is-flipping class prevents the slot transition from competing with
    // FLIP, and is removed safely when the newest pass completes.
    function animateZoneChange(zone, mutation, options) {
        const resolved = getZone(zone);
        if (!resolved) {
            mutation?.();
            return Promise.resolve();
        }

        // Capture the current card positions before changing order or legality
        // classes.  The hand can move vertically when cards become playable or
        // illegal; a FLIP pass makes that movement visible even when the slot
        // CSS variables are updated in the same frame.
        const before = new Map(
            getZoneCards(resolved).map((card) => [card, card.getBoundingClientRect()])
        );
        mutation?.();
        const cards = options?.cards ? Array.from(options.cards) : null;
        if (cards) cards.forEach((card) => resolved.element.appendChild(card));
        layoutZone(resolved, cards || undefined, options?.layout);

        const cardsAfter = cards || getZoneCards(resolved);
        const animations = cardsAfter.map((card) => {
            const first = before.get(card);
            if (!first) return Promise.resolve();
            return animateFromRect(
                card,
                first,
                card.getBoundingClientRect(),
                options?.duration ?? MOTION.quickMs,
                options?.ease ?? 'inOut(2)'
            );
        });
        return Promise.all(animations);
    }

    function applyHandFan(cards) {
        const list = Array.from(cards || []);
        if (!list.length) return;
        const parent = list[0].parentElement;
        const zone = parent ? getZone(parent) : null;
        if (zone) {
            layoutZone(zone, list);
            return;
        }
        const total = list.length;
        list.forEach((el, index) => {
            const centeredIndex = index - ((total - 1) / 2);
            el.style.setProperty('--tilt', `${centeredIndex * 5}deg`);
        });
    }

    function syncBackCards(cardsEl, cardsCount, options) {
        if (!cardsEl) return;
        const count = Math.max(Number(cardsCount) || 0, 0);
        const existingCards = Array.from(cardsEl.children).filter((el) => !el.classList.contains('game-hand-placeholder'));
        const forceDealCount = Math.min(
            Math.max(Number(options?.forceDealCount) || 0, 0),
            count
        );
        if (existingCards.length === count && forceDealCount === 0) return;
        const first = new Map(existingCards.map((el) => [el, el.getBoundingClientRect()]));
        while (existingCards.length > count) existingCards.pop()?.remove();
        while (existingCards.length < count) {
            const card = renderCardImage({
                cardType: options?.cardType,
                className: options?.className || 'seat-card',
                alt: options?.alt || t('game.cardBack.alt'),
                flippable: options?.flippable
            });
            cardsEl.appendChild(card);
            existingCards.push(card);
        }
        const total = existingCards.length;
        const forceDealStart = total - forceDealCount;
        existingCards.forEach((card, index) => {
            const centeredIndex = index - ((total - 1) / 2);
            const rotate = centeredIndex * (options?.spread ?? 5);
            const lift = Math.abs(centeredIndex) * (options?.lift ?? 1.5);
            card.style.transform = `translateY(${lift}px) rotate(${rotate}deg)`;
            card.style.zIndex = String(index + 1);
        });
        // Generic deal/fly-in: cards that simply MOVED slide to their new slot (FLIP);
        // BRAND-NEW cards fly in from `options.fromRect` (e.g. the deck) to their exact
        // slot — so any hand (opponent seats, other game modes) gets the same animation
        // the main hand has, landing in the right place rather than the seat centre.
        const source = options?.fromRect;
        const hasSource = source && (source.width || source.height);
        let dealtIndex = 0;
        existingCards.forEach((card, index) => {
            const previous = first.get(card);
            const next = card.getBoundingClientRect();
            const forceDeal = index >= forceDealStart;
            if (previous && !forceDeal) {
                animateFromRect(card, previous, next, MOTION.quickMs);
                return;
            }
            const slotTransform = card.style.transform || 'translate3d(0,0,0)';
            // Cards fly and pop in at FULL size — a scaled-down start made the
            // back look smaller than the card it lands as.
            if (hasSource && next.width) {
                const dx = (source.left + (source.width || 0) / 2) - (next.left + next.width / 2);
                const dy = (source.top + (source.height || 0) / 2) - (next.top + next.height / 2);
                card.classList.add('is-dealing');
                card.style.opacity = '0';   // hidden while its images decode; the tween restores it
                const dealDelay = (dealtIndex++) * (options?.dealStagger ?? 70);
                decodeCardImages(card).then(() => playAnimation(card, {
                    opacity: [0.35, 1],
                    transform: [
                        `translate3d(${dx}px, ${dy}px, 0) ${slotTransform}`,
                        `translate3d(0, 0, 0) ${slotTransform}`.trim()
                    ],
                    duration: options?.dealDuration ?? MOTION.dealMs,
                    delay: dealDelay,
                    ease: MOTION.ease
                })).finally(() => commitWithoutTransition(card, () => card.classList.remove('is-dealing')));
                return;
            }
            playAnimation(card, {
                opacity: [0, 1],
                transform: [slotTransform, slotTransform],
                duration: MOTION.quickMs,
                ease: MOTION.ease
            });
        });
    }

    // Deal real cards into an existing hand.  The caller owns game state; this
    // helper only owns the shared flight/flip animation and always leaves the
    // card in its normal slot, even when a tween is interrupted.
    function dealCardsIntoHand(handEl, cards, options) {
        if (!handEl || !Array.isArray(cards) || !cards.length) return;
        const deckRect = options?.fromRect || null;
        // The server's hand order is not necessarily the rendered/sorted hand order.
        // Stagger by visual position so cards always deal from left to right.
        const orderedCards = [...cards].sort((left, right) => {
            const leftEl = handEl.querySelector(`[data-card-key="${CSS.escape(cardKey(left))}"]`);
            const rightEl = handEl.querySelector(`[data-card-key="${CSS.escape(cardKey(right))}"]`);
            return (leftEl?.getBoundingClientRect().left ?? 0) - (rightEl?.getBoundingClientRect().left ?? 0);
        });
        const finish = (el, card, index) => {
            if (!el || el.dataset.dealFinished === '1') return;
            delete el.dataset.dealRunning;
            el.dataset.dealFinished = '1';
            el.classList.remove('is-dealing');
            ['--deal-x', '--deal-y', '--deal-rot', '--deal-scale'].forEach((prop) => el.style.removeProperty(prop));
            el.cardApi?.flip(card);
            options?.onFinish?.(el, card, index === orderedCards.length - 1);
        };

        orderedCards.forEach((card, index) => {
            const key = cardKey(card);
            const el = handEl.querySelector(`[data-card-key="${CSS.escape(key)}"]`);
            if (!el || el.dataset.dealRunning === '1' || el.dataset.dealFinished === '1') return;
            el.dataset.dealRunning = '1';
            const slot = el.getBoundingClientRect();
            const from = options?.fromFeaturedLast && index === orderedCards.length - 1
                ? options.featuredRect || deckRect
                : deckRect;
            if (!from?.width || !slot.width) {
                finish(el, card, index);
                return;
            }
            const fromScale = from.width / Math.max(el.offsetWidth || slot.width, 1);
            flyIntoSlot(el, from, {
                faceDown: true,
                spin: -10 + index * 4,
                fromScale,
                duration: options?.duration ?? MOTION.dealMs,
                delay: index * (options?.stagger ?? 80),
                ease: options?.ease || 'power3.out',
                onLand: () => finish(el, card, index)
            });
            // ponytail: the timeout is the small, explicit recovery path for an
            // interrupted tween; a full animation lifecycle abstraction adds no value.
            setTimeout(() => finish(el, card, index), (options?.duration ?? MOTION.dealMs) + index * (options?.stagger ?? 80) + 350);
        });
    }

    function renderDeckTower(deckTower, deckStack, cardsLeft, options) {
        if (!deckTower) return;
        const deckTowerCount = Math.max((cardsLeft ?? 0) - (options?.featuredCard ? 1 : 0), 0);
        const exhausting = options?.exhausting === true;
        if (deckStack) deckStack.classList.toggle('is-empty', deckTowerCount <= 0 && !exhausting);
        if (deckTowerCount <= 0 && !exhausting) { deckTower.replaceChildren(); return; }

        // The deck is decorative: four layers sell the stack without creating up to
        // eighteen large GPU textures on a fresh mobile page load.
        const target = exhausting ? 1 : Math.min(deckTowerCount, 4);
        // Rebuild only if the card style changed (never within a game). Offsets are
        // anchored to the index FROM THE BOTTOM so existing cards never need their
        // transforms recomputed — drawing just removes the top element(s).
        if (deckTower.dataset.deckType !== String(options?.cardType || '')) {
            deckTower.replaceChildren();
            deckTower.dataset.deckType = String(options?.cardType || '');
        }
        let current = deckTower.children.length;
        while (current > target) { deckTower.lastElementChild?.remove(); current -= 1; }
        while (current < target) {
            const i = current;   // index from bottom — stable offset
            const img = renderCardImage({
                cardType: options?.cardType,
                className: options?.className || '',
                alt: options?.alt || t('game.deck.alt')
            });
            img.style.setProperty('--deck-offset-x', String(Number((i * 0.65).toFixed(2))));
            img.style.setProperty('--deck-offset-y', String(Number((-i * 0.55).toFixed(2))));
            img.style.setProperty('--deck-rot', String(Number((((i % 3) - 1) * 0.65).toFixed(3))));
            img.style.zIndex = String(i + 1);
            deckTower.appendChild(img);
            current += 1;
        }
    }

    function normalizeRect(rect) {
        if (!rect) return null;
        return {
            left: rect.left,
            top: rect.top,
            right: rect.right ?? rect.left + rect.width,
            bottom: rect.bottom ?? rect.top + rect.height,
            width: rect.width,
            height: rect.height
        };
    }

    function resolveDragBounds(options) {
        const elementRect = options?.boundsElement?.getBoundingClientRect?.();
        const rect = normalizeRect(options?.boundsRect || elementRect);
        const viewport = {
            left: VIEWPORT_DRAG_PADDING,
            top: VIEWPORT_DRAG_PADDING,
            right: window.innerWidth - VIEWPORT_DRAG_PADDING,
            bottom: window.innerHeight - VIEWPORT_DRAG_PADDING
        };
        if (!rect) return viewport;
        return {
            left: Math.max(rect.left, viewport.left),
            top: Math.max(rect.top, viewport.top),
            right: Math.min(rect.right, viewport.right),
            bottom: Math.min(rect.bottom, viewport.bottom)
        };
    }

    function clamp(value, min, max) {
        if (max < min) return min;
        return Math.min(Math.max(value, min), max);
    }

    function overlayCloneFromElement(sourceEl, options) {
        if (!sourceEl) return null;
        const rect = sourceEl.getBoundingClientRect();
        const clone = sourceEl.cloneNode(true);
        clone.removeAttribute('id');
        // Strip layout/interaction classes so the clone inherits none of their
        // transitions (which would make it lag the cursor) or positioning rules.
        // Keep `card-wrap` for the flip visual. Reset any slot transform vars.
        clone.classList.remove('hand-card', 'trick-card', 'game-hand-card', 'seat-card', 'is-dragging', 'is-dealing');
        ['--slot-x', '--slot-y', '--slot-rot', '--tilt', '--lift', '--press-y', '--hover-scale', '--slot-scale']
            .forEach((prop) => clone.style.removeProperty(prop));
        clone.classList.add(options?.className || 'game-moving-card');
        clone.style.width = `${rect.width}px`;
        clone.style.height = `${rect.height}px`;
        clone.style.transform = `translate3d(${rect.left}px, ${rect.top}px, 0) rotate(${options?.fromRot || '0deg'})`;
        ensureOverlayLayer().appendChild(clone);
        return {clone, rect};
    }

    function animateOverlayToRect(clone, fromRect, toRect, options) {
        if (!clone || !fromRect || !toRect) {
            clone?.remove();
            return Promise.resolve();
        }
        const duration = prefersReducedMotion() ? 80 : (options?.duration ?? MOTION.dealMs);
        const delay = Number(options?.delay) || 0;
        const width = clone.offsetWidth || fromRect.width;
        const height = clone.offsetHeight || fromRect.height;
        const fromX = fromRect.left + (fromRect.width - width) / 2;
        const fromY = fromRect.top + (fromRect.height - height) / 2;
        const toX = toRect.left + (toRect.width - width) / 2;
        const toY = toRect.top + (toRect.height - height) / 2;
        const fromScaleX = options?.fromScale ?? fromRect.width / width;
        const fromScaleY = options?.fromScale ?? fromRect.height / height;
        const toScaleX = options?.toScale ?? toRect.width / width;
        const toScaleY = options?.toScale ?? toRect.height / height;
        clone.style.transformOrigin = '50% 50%';
        let settled = false;
        const cleanup = () => {
            if (settled) return;
            settled = true;
            clone.remove();
            options?.onDone?.();
        };
        // Safety net: always remove the overlay clone even if the animation
        // is interrupted or its promise never settles (includes the decode wait).
        const safety = setTimeout(cleanup, duration + delay + 600);
        // The clone's <img>s are new elements: decode them before flying so the
        // decode doesn't land mid-flight as a dropped frame (the clone is already
        // parked at its start position by the caller).
        const animation = decodeCardImages(clone).then(() => gsap && !useNativeCardAnimations ? new Promise((resolve) => {
            gsap.set(clone, {
                x: fromX, y: fromY, rotation: options?.fromRot || '0deg',
                scaleX: fromScaleX, scaleY: fromScaleY, opacity: 1
            });
            gsap.to(clone, {
                x: toX, y: toY, rotation: options?.toRot || '0deg',
                scaleX: toScaleX, scaleY: toScaleY,
                opacity: options?.fadeOut ? 0 : 1,
                duration: duration / 1000,
                delay: delay / 1000,
                ease: mapEase(options?.easing || 'out(3)'),
                onComplete: resolve
            });
        }) : playAnimation(clone, {
            transform: [
                `translate3d(${fromX}px, ${fromY}px, 0) rotate(${options?.fromRot || '0deg'}) scale(${fromScaleX}, ${fromScaleY})`,
                `translate3d(${toX}px, ${toY}px, 0) rotate(${options?.toRot || '0deg'}) scale(${toScaleX}, ${toScaleY})`
            ],
            opacity: options?.fadeOut ? [1, 0] : [1, 1],
            duration, delay, ease: options?.easing || 'out(3)'
        }));
        return animation.catch(() => undefined).then(() => {
            clearTimeout(safety);
            cleanup();
        });
    }

    // Resolve the on-screen rect for a slot in a zone — the shared "coordinate"
    // calculator for cross-zone card movement. `index == null` returns the zone's
    // own rect (e.g. the deck pile as a whole); otherwise a slot is reserved.
    function zoneSlotRect(zoneOrEl, index) {
        const resolved = getZone(zoneOrEl);
        if (!resolved) return null;
        if (index == null) return resolved.element.getBoundingClientRect();
        const placeholder = reserveSlot(resolved, index);
        return placeholder?.getBoundingClientRect() || resolved.element.getBoundingClientRect();
    }

    // Animate a card from one zone/element to another. A single subfunction computes
    // BOTH endpoint coordinates up-front (no rect reads mid-flight, avoiding jank),
    // builds an overlay clone, and flies it along a smooth arc.
    //
    // Source resolution order:  fromEl → sourceRect → zone(fromZone, fromIndex)
    // Target resolution order:  toEl  → targetRect → zone(toZone/toHand, toIndex)
    function animateCardBetweenZones(options) {
        const sourceEl = options?.fromEl || options?.cardEl || options?.sourceEl;
        const toZone = options?.toZone || options?.toHand;
        // Compute all coordinates before touching the clone.
        const sourceRect = sourceEl
            ? sourceEl.getBoundingClientRect()
            : (options?.sourceRect ? normalizeRect(options.sourceRect) : zoneSlotRect(options?.fromZone, options?.fromIndex));
        const targetRect = options?.toEl
            ? options.toEl.getBoundingClientRect()
            : (options?.targetRect ? normalizeRect(options.targetRect) : zoneSlotRect(toZone, options?.toIndex));
        if (!sourceRect || !targetRect) {
            if (toZone) clearReservedSlot(toZone);
            return Promise.resolve();
        }
        let clone;
        if (sourceEl) {
            clone = overlayCloneFromElement(sourceEl, options)?.clone;
        } else {
            clone = renderCardImage({
                card: options?.card,
                cardType: options?.cardType,
                className: options?.className || 'game-moving-card',
                alt: options?.alt || t('game.card.alt')
            });
            clone.style.width = `${options?.toEl?.offsetWidth || targetRect.width || sourceRect.width || 96}px`;
            clone.style.height = `${options?.toEl?.offsetHeight || targetRect.height || sourceRect.height}px`;
            clone.style.transform = `translate3d(${sourceRect.left}px, ${sourceRect.top}px, 0)`;
            ensureOverlayLayer().appendChild(clone);
        }
        return animateOverlayToRect(clone, sourceRect, targetRect, options)
            .then(() => {
                if (toZone) clearReservedSlot(toZone);
            });
    }

    // Backwards-compatible alias for existing call sites.
    function animateCardTransfer(options) {
        return animateCardBetweenZones(options);
    }

    // Set while any card drag session is live, so per-pointermove work that only
    // matters outside a drag (hover raise) can bail without touching layout.
    let activeDragSession = null;

    // Drag the REAL card element (no clone). The card is lifted into the fixed
    // overlay layer so it can move across the whole page without clipping and stops
    // participating in the hand fan; on cancel it is returned to its slot.
    function startDragCard(options) {
        const sourceEl = options?.sourceEl;
        if (!sourceEl) return null;
        const pointer = options?.pointer || {x: 0, y: 0};
        const rect = sourceEl.getBoundingClientRect();
        const width = sourceEl.offsetWidth || rect.width;
        const height = sourceEl.offsetHeight || rect.height;
        const originZone = getZone(options?.originZone || options?.originHand || sourceEl.parentElement);
        const session = {
            el: sourceEl,
            originEl: sourceEl,
            originRect: rect,
            originParent: sourceEl.parentElement,
            originNextSibling: sourceEl.nextElementSibling,
            // Grab the card at the point under the cursor so it doesn't jump.
            pointerOffsetX: rect.width
                ? clamp((pointer.x - rect.left) * width / rect.width, 0, width)
                : width / 2,
            pointerOffsetY: rect.height
                ? clamp((pointer.y - rect.top) * height / rect.height, 0, height)
                : height / 2,
            dragWidth: width,
            dragHeight: height,
            originZone,
            bounds: resolveDragBounds(options),
            accepted: false,
            overDropZone: false,
            lastPoint: pointer
        };
        // Preserve the unrotated card box. A rotated bounding rect has a different
        // aspect ratio, which distorted the flying card and made it miss its final slot.
        sourceEl.style.width = `${width}px`;
        sourceEl.style.height = `${height}px`;
        sourceEl.classList.add(options?.className || 'drag-ghost');
        const overlay = ensureOverlayLayer();
        // Reflow the cards left behind through the shared FLIP path.  With only
        // two cards this is the large center-to-edge move that otherwise snaps.
        if (originZone) {
            animateZoneChange(originZone, () => overlay.appendChild(sourceEl));
        } else {
            overlay.appendChild(sourceEl);
        }
        sourceEl.style.transform = `translate3d(${rect.left + (rect.width - width) / 2}px, ${rect.top + (rect.height - height) / 2}px, 0)`;
        activeDragSession = session;
        updateDragCard(session, pointer.x, pointer.y);
        return session;
    }

    // Touch move events can outpace the display (Gecko on Android fires them at
    // input frequency); coalesce position writes to one per frame so the drag
    // never forces more style/layout work than the screen can show.
    function updateDragCard(session, x, y) {
        if (!session?.el) return;
        session.lastPoint = {x, y};
        // First write lands immediately so the grab doesn't lag by a frame.
        if (!session.hasPositioned) {
            session.hasPositioned = true;
            applyDragPosition(session);
            return;
        }
        if (session.moveFrame != null) return;
        session.moveFrame = requestAnimationFrame(() => {
            session.moveFrame = null;
            applyDragPosition(session);
        });
    }

    function applyDragPosition(session) {
        if (!session?.el) return;
        const width = session.dragWidth || session.originRect?.width || 96;
        const height = session.dragHeight || session.originRect?.height || 134;
        const bounds = session.bounds || resolveDragBounds();
        const moveX = clamp(session.lastPoint.x - session.pointerOffsetX, bounds.left, bounds.right - width);
        const moveY = clamp(session.lastPoint.y - session.pointerOffsetY, bounds.top, bounds.bottom - height);
        session.el.style.transform = `translate3d(${moveX}px, ${moveY}px, 0) rotate(var(--drag-rot, 0deg)) scale(1.04)`;
    }

    function endDragSession(session) {
        if (!session) return;
        if (session.moveFrame != null) {
            cancelAnimationFrame(session.moveFrame);
            session.moveFrame = null;
        }
        if (activeDragSession === session) activeDragSession = null;
    }

    function restoreDraggedElement(session) {
        endDragSession(session);
        const el = session?.el;
        if (!el) return;
        el.classList.remove('drag-ghost');
        el.style.transform = '';
        el.style.opacity = '';
        el.style.width = '';
        el.style.height = '';
        const parent = session.originParent;
        if (parent) {
            const next = session.originNextSibling;
            if (next && next.parentElement === parent) parent.insertBefore(el, next);
            else parent.appendChild(el);
        }
        if (session.originZone) layoutZone(session.originZone);
    }

    function finishDragCard(session, options) {
        endDragSession(session);
        const el = session?.el;
        if (!el) return Promise.resolve();
        // The dragged card was removed mid-drag (e.g. auto-played): nothing to fly.
        if (!el.isConnected) {
            options?.onDone?.();
            return Promise.resolve();
        }
        const targetRect = options?.targetRect || session.originRect;
        const currentRect = el.getBoundingClientRect();
        const duration = options?.duration ?? (options?.accepted ? 180 : 240);
        const fromX = currentRect.left;
        const fromY = currentRect.top;
        const toX = targetRect.left + (targetRect.width - currentRect.width) / 2;
        const toY = targetRect.top + (targetRect.height - currentRect.height) / 2;
        return playAnimation(el, {
            transform: [
                `translate3d(${fromX}px, ${fromY}px, 0) rotate(${options?.toRot || '0deg'}) scale(1.04)`,
                `translate3d(${toX}px, ${toY}px, 0) rotate(${options?.toRot || '0deg'}) scale(${options?.accepted ? 0.96 : 1})`
            ],
            opacity: options?.accepted ? [1, 0] : [1, 1],
            duration,
            ease: options?.accepted ? 'out(3)' : 'out(4)'
        }).then(() => {
            if (options?.accepted) {
                el.remove();
            } else {
                restoreDraggedElement(session);
            }
            options?.onDone?.();
        });
    }

    // ---- Reusable flying-animation helpers (customizable parameters) ----

    // Fly a card that is ALREADY placed in its slot, starting visually from `fromRect`.
    // Firefox uses a native transform animation; other browsers keep the composable
    // deal variables. `faceDown:true` shows the back during flight
    // (deal); otherwise the card keeps its face (return). Params: duration(ms), ease,
    // spin(deg), fromScale, faceDown, delay(ms), onLand(el).
    function flyIntoSlot(el, fromRect, options) {
        if (!el || !fromRect) return Promise.resolve();
        const gsap = window.gsap;
        const faceDown = options?.faceDown === true;
        const flightClass = faceDown ? 'is-dealing' : 'is-flying';
        el.classList.add(flightClass);
        const slot = el.getBoundingClientRect();
        const dx = (fromRect.left + fromRect.width / 2) - (slot.left + slot.width / 2);
        const dy = (fromRect.top + fromRect.height / 2) - (slot.top + slot.height / 2);
        const DEAL = ['--deal-x', '--deal-y', '--deal-rot', '--deal-scale'];
        const cleanup = () => {
            commitWithoutTransition(el, () => {
                el.classList.remove(flightClass);
                DEAL.forEach((p) => el.style.removeProperty(p));
                el.style.transform = '';
            });
            options?.onLand?.(el);
        };
        if (useNativeCardAnimations && typeof el.animate === 'function') {
            const computedTransform = getComputedStyle(el).transform;
            const baseTransform = computedTransform === 'none' ? '' : computedTransform;
            const startTransform = `translate3d(${dx}px, ${dy}px, 0) rotate(${options?.spin ?? 8}deg) scale(${options?.fromScale ?? 1.04}) ${baseTransform}`.trim();
            const endTransform = `translate3d(0, 0, 0) rotate(0deg) scale(1) ${baseTransform}`.trim();
            // Park the card at its start position while its images decode, so the
            // flight's first painted frame is never a blank card at the slot.
            el.style.transform = startTransform;
            return decodeCardImages(el).then(() => playWebAnimationFallback(el, {
                transform: [startTransform, endTransform],
                duration: options?.duration ?? 300,
                delay: options?.delay ?? 0,
                ease: options?.ease || 'power3.out'
            })).then(cleanup, cleanup);
        }
        if (!gsap) { cleanup(); return Promise.resolve(); }
        gsap.set(el, {
            '--deal-x': `${dx}px`, '--deal-y': `${dy}px`,
            '--deal-rot': `${options?.spin ?? 8}deg`, '--deal-scale': options?.fromScale ?? 1.04
        });
        return decodeCardImages(el).then(() => new Promise((resolve) => {
            gsap.to(el, {
                '--deal-x': '0px', '--deal-y': '0px', '--deal-rot': '0deg', '--deal-scale': 1,
                duration: (options?.duration ?? 300) / 1000,
                delay: (options?.delay ?? 0) / 1000,
                ease: options?.ease || 'power3.out',
                onComplete() { cleanup(); resolve(); }
            });
        }));
    }

    // Fly an OVERLAY-layer element (a re-parented real card or a clone) to a target
    // rect via a single inline-transform tween. Params: duration(ms), ease, fromScale,
    // toScale, fromRot, toRot, fade(boolean → fade to 0 on arrival), onLand(el).
    function flyOverlayTo(el, toRect, options) {
        if (!el || !toRect) return Promise.resolve();
        const fromRect = el.getBoundingClientRect();
        const duration = prefersReducedMotion() ? 80 : (options?.duration ?? MOTION.dealMs);
        // Scale is applied about the element's center, so center the destination on the
        // UNSCALED box (offsetWidth/Height), not the scaled getBoundingClientRect size.
        const nw = el.offsetWidth || fromRect.width;
        const nh = el.offsetHeight || fromRect.height;
        const fromX = fromRect.left + (fromRect.width - nw) / 2;
        const fromY = fromRect.top + (fromRect.height - nh) / 2;
        const toX = toRect.left + (toRect.width - nw) / 2;
        const toY = toRect.top + (toRect.height - nh) / 2;
        const fromScale = options?.fromScale ?? (fromRect.width / Math.max(nw, 1));
        // A card can be released while a previous frame is still settling. Start
        // from its actual rendered geometry, not a hard-coded drag scale.
        window.gsap?.killTweensOf(el);
        return playAnimation(el, {
            transform: [
                `translate3d(${fromX}px, ${fromY}px, 0) rotate(${options?.fromRot || '0deg'}) scale(${fromScale})`,
                `translate3d(${toX}px, ${toY}px, 0) rotate(${options?.toRot || '0deg'}) scale(${options?.toScale ?? 1})`
            ],
            opacity: options?.fade ? [1, 0] : [1, 1],
            duration,
            ease: options?.ease || 'out(3)'
        }).then(() => { options?.onLand?.(el); });
    }

    // Cross-hand transfer: animate a card element from a SOURCE hand into its slot in
    // its (destination) hand. The card must already be placed at its destination slot;
    // this flies it in from the source hand's on-screen position. `fromHand` may be a
    // registered zone, a DOM element, or pass an explicit `fromRect`. This is the
    // reusable "a card moves from one hand to another" primitive (e.g. an opponent's
    // played card flying from their hand to the table). Params mirror flyIntoSlot:
    // faceDown, spin, fromScale, duration, ease, delay.
    function crossHandTransfer(options) {
        const cardEl = options?.cardEl || options?.el;
        if (!cardEl) return Promise.resolve();
        let fromRect = options?.fromRect ? normalizeRect(options.fromRect) : null;
        if (!fromRect) {
            const fromHand = options?.fromHand || options?.from;
            const fromEl = fromHand && (fromHand.element || fromHand);
            fromRect = fromEl?.getBoundingClientRect?.();
        }
        if (!fromRect || !fromRect.width) return Promise.resolve();
        return flyIntoSlot(cardEl, fromRect, {
            faceDown: options?.faceDown,
            spin: options?.spin,
            fromScale: options?.fromScale,
            duration: options?.duration,
            ease: options?.ease,
            delay: options?.delay
        });
    }

    function enableDropZone(element, options) {
        if (!element || !interactApi) return null;
        interactApi.dynamicDrop?.(true);
        const interactable = interactApi(element).dropzone({
            accept: options?.accept || '.hand-card',
            overlap: options?.overlap ?? 0.28,
            ondragenter(event) {
                options?.onEnter?.(event);
            },
            ondragleave(event) {
                options?.onLeave?.(event);
            },
            ondrop(event) {
                options?.onDrop?.(event);
            }
        });
        element.__ucDropZone = interactable;
        return interactable;
    }

    function enableCardDrag(element, options) {
        if (!element) return null;
        disableCardDrag(element);
        if (!interactApi) return null;
        const interactable = interactApi(element)
            .styleCursor(false)
            .draggable({
                inertia: options?.inertia ?? false,
                autoScroll: false,
                listeners: {
                    start(event) {
                        const pointer = eventToPoint(event);
                        const session = startDragCard({
                            sourceEl: element,
                            originZone: options?.originZone || options?.originHand,
                            pointer,
                            className: options?.className || 'drag-ghost',
                            boundsElement: options?.boundsElement,
                            boundsRect: options?.boundsRect
                        });
                        element.__ucDragSession = session;
                        options?.onStart?.(session, event);
                    },
                    move(event) {
                        const session = element.__ucDragSession;
                        if (!session) return;
                        const pointer = eventToPoint(event);
                        updateDragCard(session, pointer.x, pointer.y);
                        options?.onMove?.(session, event);
                    },
                    end(event) {
                        const session = element.__ucDragSession;
                        element.__ucDragSession = null;
                        endDragSession(session);
                        options?.onEnd?.(session, event);
                    }
                }
            });
        element.__ucCardDrag = interactable;
        return interactable;
    }

    function disableCardDrag(element) {
        if (!element?.__ucCardDrag) return;
        try {
            element.__ucCardDrag.unset();
        } catch (err) {
            console.warn('Unable to unset card drag interaction.', err);
        }
        element.__ucCardDrag = null;
    }

    function eventToPoint(event) {
        return {
            x: Number(event.clientX ?? event.pageX ?? 0),
            y: Number(event.clientY ?? event.pageY ?? 0)
        };
    }

    // The card the user is visually pointing at in an overlapped fan is the TOPMOST
    // card under the pointer: later cards stack on top of earlier ones, so it is the
    // containing card with the RIGHTMOST left edge. "Nearest center" is wrong here —
    // with deep overlap (a 10-card Treseta hand shows ~1/4 of each card) the middle
    // of a card's visible strip lies closer to its LEFT neighbour's center, which
    // raised and played the wrong card. Falls back to nearest-center only when the
    // pointer is outside every card (edge grabs).
    function pickFanCard(cards, x) {
        let owner = null;
        let ownerLeft = -Infinity;
        let nearest = null;
        let nearestDist = Infinity;
        const bounds = {left: Infinity, right: -Infinity, top: Infinity, bottom: -Infinity};
        cards.forEach((card) => {
            const r = card.getBoundingClientRect();
            bounds.left = Math.min(bounds.left, r.left);
            bounds.right = Math.max(bounds.right, r.right);
            bounds.top = Math.min(bounds.top, r.top);
            bounds.bottom = Math.max(bounds.bottom, r.bottom);
            if (x >= r.left && x <= r.right && r.left > ownerLeft) {
                owner = card;
                ownerLeft = r.left;
            }
            const dist = Math.abs(x - (r.left + r.width / 2));
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = card;
            }
        });
        return {card: owner || nearest, bounds};
    }

    // Make a fanned hand grab the card the user is POINTING AT. Because cards overlap,
    // a card's face is covered by the next one, so a plain click/drag would grab the
    // wrong (front-most) card. This raises the card whose center is nearest the cursor
    // X to the top (inline z-index beats the per-card index set by layoutZone), so the
    // intended card is on top everywhere it matters and gets dragged — the real card,
    // never a copy.
    function enableHandHoverRaise(zoneOrEl, options) {
        const resolved = getZone(zoneOrEl);
        const element = resolved ? resolved.element : zoneOrEl;
        if (!element || element.__ucHoverRaise) return;
        element.__ucHoverRaise = true;
        let raised = null;
        const lower = () => {
            if (!raised) return;
            raised.style.zIndex = raised.__ucPrevZ || '';
            raised.classList.remove('is-raised');
            raised = null;
            element.__ucRaisedCard = null;
        };
        const raise = (card) => {
            if (raised === card) return;
            lower();
            if (!card || (options?.isCardActive && !options.isCardActive(card))) return;
            raised = card;
            card.__ucPrevZ = card.style.zIndex;
            card.style.zIndex = '60';
            card.classList.add('is-raised');
            // Expose the currently raised card so the drag picks THIS card (the one
            // the user sees enlarged), regardless of exact pointer-vs-element overlap.
            element.__ucRaisedCard = card;
        };
        // Pick the card visually under the cursor (see pickFanCard), but ONLY when
        // the cursor is actually over the cards' area — not the empty padding to the
        // left/right/top/bottom of the hand. Otherwise hovering blank space would
        // enlarge the edge card.
        const pickCard = (x, y) => {
            const cards = getZoneCards(resolved || {element}).filter((card) =>
                !card.classList.contains('is-dragging')
                && !card.classList.contains('drag-ghost')
                && (!options?.isCardActive || options.isCardActive(card)));
            if (!cards.length) return null;
            const {card, bounds} = pickFanCard(cards, x);
            if (x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom) return null;
            return card;
        };
        // pickCard reads every card's rect; run it at most once per frame and not
        // at all mid-drag (touch move events arrive faster than frames on mobile,
        // and during a drag each pick would force a layout flush after the drag
        // transform write — the main source of drag stutter on mobile Gecko).
        let hoverPoint = null;
        let hoverFrame = null;
        element.addEventListener('pointermove', (event) => {
            if (activeDragSession) return;
            if (options?.isActive && !options.isActive()) {
                lower();
                return;
            }
            hoverPoint = {
                x: Number(event.clientX ?? event.pageX ?? 0),
                y: Number(event.clientY ?? event.pageY ?? 0)
            };
            if (hoverFrame != null) return;
            hoverFrame = requestAnimationFrame(() => {
                hoverFrame = null;
                if (activeDragSession) return;
                if (options?.isActive && !options.isActive()) return;
                const target = pickCard(hoverPoint.x, hoverPoint.y);
                if (target) raise(target);
                else lower();
            });
        });
        // Touch has no hover: without this, drag-start would use a STALE raised card
        // from a previous interaction and play the wrong card. Re-pick at touch point.
        element.addEventListener('pointerdown', (event) => {
            if (options?.isActive && !options.isActive()) return;
            const x = Number(event.clientX ?? event.pageX ?? 0);
            const y = Number(event.clientY ?? event.pageY ?? 0);
            raise(pickCard(x, y));
        });
        element.addEventListener('pointerleave', lower);
    }

    // Container-level hand drag. One draggable on the hand element; on start it picks
    // the RAISED card (the enlarged one the user is pointing at) — falling back to the
    // card nearest the cursor X — instead of relying on which element the pointer
    // happens to be over. This guarantees the intended (enlarged) card is dragged, as
    // the real element, never a copy, even when the lift makes the cursor overlap a
    // card behind it.
    function enableHandCardDrag(zoneOrEl, options) {
        const resolved = getZone(zoneOrEl);
        const element = resolved ? resolved.element : zoneOrEl;
        if (!element || !interactApi) return null;
        if (element.__ucHandDrag) {
            try { element.__ucHandDrag.unset(); } catch (err) { /* ignore */ }
        }
        const nearestCard = (x) => {
            const cards = getZoneCards(resolved || {element})
                .filter((card) => !card.classList.contains('drag-ghost'));
            return cards.length ? pickFanCard(cards, x).card : null;
        };
        const interactable = interactApi(element)
            .styleCursor(false)
            .draggable({
                inertia: false,
                autoScroll: false,
                listeners: {
                    start(event) {
                        if (options?.isActive && !options.isActive()) return;
                        const pointer = eventToPoint(event);
                        const cardEl = element.__ucRaisedCard || nearestCard(pointer.x);
                        if (!cardEl || (options?.isCardActive && !options.isCardActive(cardEl))) return;
                        const session = startDragCard({
                            sourceEl: cardEl,
                            originZone: options?.originZone || resolved,
                            pointer,
                            className: options?.className || 'drag-ghost',
                            boundsElement: options?.boundsElement,
                            boundsRect: options?.boundsRect
                        });
                        element.__ucActiveDrag = session || null;
                        if (session) options?.onStart?.(session, cardEl, event);
                    },
                    move(event) {
                        const session = element.__ucActiveDrag;
                        if (!session) return;
                        const pointer = eventToPoint(event);
                        updateDragCard(session, pointer.x, pointer.y);
                        options?.onMove?.(session, event);
                    },
                    end(event) {
                        const session = element.__ucActiveDrag;
                        element.__ucActiveDrag = null;
                        if (!session) return;
                        endDragSession(session);
                        options?.onEnd?.(session, event);
                    }
                }
            });
        element.__ucHandDrag = interactable;
        return interactable;
    }

    function markCardReturning(hand, cardKeyValue) {
        if (!hand || !cardKeyValue) return;
        const el = hand.querySelector(`[data-card-key="${CSS.escape(cardKeyValue)}"]`);
        if (!el) return;
        // The card returns to its slot via its CSS-var slot transform + CSS transition.
        // Never write an inline transform here — a leftover one would override the slot
        // position and strand the card. Just clear drag state and any stale inline style.
        el.classList.remove('is-dragging');
        el.style.transform = '';
        el.style.opacity = '';
    }

    function revealCardFace(cardEl, cardData) {
        if (!cardEl || !cardData) return;
        cardEl.dataset.cardFace = 'true';
        cardEl.dataset.cardCode = cardData.card || '';
        cardEl.dataset.cardKey = cardKey(cardData);
        let front = cardEl.querySelector('.card-front');
        if (!front) {
            front = createCardSide('card-front', 'Card', cardUrl(cardData));
            cardEl.replaceChildren(front);
        } else {
            applyCardImage(front, cardUrl(cardData));
        }
    }

    function flipCardReveal(cardEl, cardData) {
        if (!cardEl) return Promise.resolve();
        const inner = cardEl.querySelector('.card-inner');
        if (!inner) {
            revealCardFace(cardEl, cardData);
            return Promise.resolve();
        }
        const useNativeFlip = useNativeCardAnimations && typeof inner.animate === 'function';
        if (prefersReducedMotion() || (!gsap && !useNativeFlip)) {
            revealCardFace(cardEl, cardData);
            // showBack() may have left an inline rotateY(0) on .card-inner; without
            // a flip tween to overwrite it, it would keep the back on top forever.
            inner.style.transform = '';
            return Promise.resolve();
        }
        const dur = 0.20;
        const front = cardEl.querySelector('.card-front');
        const frontKey = cardData ? cardKey(cardData) : '';
        if (front && cardData && front.dataset.preloadedCardKey !== frontKey) {
            front.dataset.preloadedCardKey = frontKey;
            applyCardImage(front, cardUrl(cardData));
        }
        if (useNativeFlip) {
            return playWebAnimationFallback(inner, {
                transform: ['rotateY(0deg)', 'rotateY(90deg)'],
                duration: dur * 1000
            }).then(() => {
                revealCardFace(cardEl, cardData);
                return front?.decode ? front.decode().catch(() => undefined) : undefined;
            }).then(() => playWebAnimationFallback(inner, {
                transform: ['rotateY(90deg)', 'rotateY(180deg)'],
                duration: dur * 1000
            })).then(() => {
                inner.style.transform = '';
            });
        }
        gsap.set(inner, {rotateY: 0});
        return new Promise((resolve) => {
            gsap.to(inner, {
                rotateY: 90,
                duration: dur,
                ease: 'power2.in',
                onComplete() {
                    revealCardFace(cardEl, cardData);
                    const decoded = front?.decode ? front.decode().catch(() => undefined) : Promise.resolve();
                    Promise.resolve(decoded).then(() => {
                        // Continue rotation to 180° so .card-front (CSS rotateY(180deg))
                        // composites to 360°=0° → facing viewer; .card-back composites to 180° → hidden.
                        gsap.fromTo(inner,
                            {rotateY: 90},
                            {rotateY: 180, duration: dur, ease: 'power2.out', onComplete: resolve}
                        );
                    });
                }
            });
        });
    }

    function animateTrickCollect(trickCards, winnerSeatEl) {
        if (!trickCards || !trickCards.length) return Promise.resolve();
        const reducedMotion = prefersReducedMotion();
        const pickupDuration = reducedMotion ? 0.01 : 0.12;
        const collectDuration = reducedMotion ? 0.05 : 0.34;
        const stagger = reducedMotion ? 0 : 0.07;
        const orderedCards = [...trickCards].sort((left, right) =>
            left.getBoundingClientRect().left - right.getBoundingClientRect().left
        );
        const viewportLeft = -Math.max(window.innerWidth * 0.08, 120);

        if (!gsap || useNativeCardAnimations) {
            return Promise.all(orderedCards.map((card, i) => {
                const rect = card.getBoundingClientRect();
                const dx = viewportLeft - rect.right;
                const dy = (i % 2 ? -1 : 1) * (10 + i * 2);
                const base = getComputedStyle(card).transform;
                const baseTransform = base === 'none' ? '' : base;
                return playAnimation(card, {
                    transform: [
                        baseTransform || 'translate3d(0, 0, 0)',
                        `translate3d(${dx}px, ${dy}px, 0) ${baseTransform}`
                    ],
                    opacity: [1, 0],
                    duration: (pickupDuration + collectDuration) * 1000,
                    delay: i * stagger * 1000,
                    ease: 'inOut(2)'
                });
            }));
        }

        const promises = orderedCards.map((card, i) => {
            const fromRect = card.getBoundingClientRect();
            // Exit completely past the left edge. Each card gets a slight vertical
            // offset so the stack feels collected rather than simply fading in place.
            const dx = viewportLeft - fromRect.right;
            const dy = (i % 2 ? -1 : 1) * (10 + i * 2);
            return new Promise((resolve) => {
                const timeline = gsap.timeline({delay: i * stagger, onComplete: resolve});
                timeline.to(card, {
                    x: dx * 0.08,
                    y: dy * 0.08,
                    scale: 1.03,
                    duration: pickupDuration,
                    ease: 'power1.out'
                });
                timeline.to(card, {
                    x: dx,
                    y: dy,
                    scale: 0.72,
                    opacity: 0,
                    duration: collectDuration,
                    ease: 'power2.inOut'
                });
            });
        });
        return Promise.all(promises).then(() => {
            // Reset GSAP inline transforms so renderTrick cleanup works cleanly
            trickCards.forEach((card) => {
                if (gsap) gsap.set(card, {clearProps: 'x,y,scale,opacity'});
            });
        });
    }

    function createDealFlipCard(incomingCard, cardType) {
        const card = document.createElement('div');
        const back = document.createElement('div');
        back.className = 'deal-card-face deal-card-back';
        back.appendChild(renderCardImage({cardType, alt: t('game.cardBack.alt')}));

        const front = document.createElement('div');
        front.className = 'deal-card-face deal-card-front';
        front.appendChild(renderCardImage({card: incomingCard, cardType, alt: t('game.card.alt')}));

        card.appendChild(back);
        card.appendChild(front);
        return card;
    }

    window.UltracardsGameUi = {
        applyHandFan,
        applyCardImage,
        animateHandChange: animateZoneChange,
        animateZoneChange,
        cardBackUrl,
        cardKey,
        cardUrl,
        createCard,
        animateCardTransfer,
        animateCardBetweenZones,
        zoneSlotRect,
        animateTrickCollect,
        dealCardsIntoHand,
        createDealFlipCard,
        clearReservedSlot,
        disableCardDrag,
        enableCardDrag,
        enableHandCardDrag,
        enableHandHoverRaise,
        enableDropZone,
        finishDragCard,
        flyIntoSlot,
        flyOverlayTo,
        crossHandTransfer,
        flipCardReveal,
        hydrateCardImages,
        italianCardUrl,
        layoutHand: layoutZone,
        layoutZone,
        markCardReturning,
        pokerCardUrl,
        animateElement: playAnimation,
        registerHand: registerZone,
        registerZone,
        renderCardImage,
        renderDeckTower,
        reserveSlot,
        revealCardFace,
        startDragCard,
        updateDragCard,
        syncBackCards
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => hydrateCardImages(document), {once: true});
    } else {
        hydrateCardImages(document);
    }

    document.addEventListener('contextmenu', (event) => {
        if (event.target instanceof Element && event.target.closest('.card-wrap')) event.preventDefault();
    });
})();
