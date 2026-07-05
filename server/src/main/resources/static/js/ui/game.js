(() => {
    const storageKey = 'uc-theme';
    const savedTheme = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = savedTheme || (systemDark ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', theme);

    // Reclaim space from the old localStorage card-image cache (now browser-cached).
    try {
        for (let i = localStorage.length - 1; i >= 0; i--) {
            const k = localStorage.key(i);
            if (k && k.startsWith('uc-card-img:')) localStorage.removeItem(k);
        }
    } catch (_) {}
})();

(function () {
    const MOTION = {
        quickMs: 160,
        standardMs: 300,
        dealMs: 520,
        ease: 'out(3)',
        snapEase: 'out(4)'
    };
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
        return normalizeCardType(cardType) === 'POKER'
            ? '/api/cards/poker/back'
            : '/api/cards/italian/back';
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

    function renderCardImage(options) {
        const card = options?.card || null;
        const cardType = normalizeCardType(card?.cardType || options?.cardType);

        const wrap = document.createElement('div');
        wrap.className = (options?.className || 'game-card') + ' card-wrap';
        wrap.dataset.cardType = cardType;
        wrap.dataset.cardFace = card ? 'true' : 'false';
        if (card) {
            wrap.dataset.cardCode = card.card || '';
            wrap.dataset.cardKey = cardKey(card);
        }

        const inner = document.createElement('div');
        inner.className = 'card-inner';

        const front = document.createElement('img');
        front.className = 'card-front';
        front.alt = options?.alt || (card ? 'Card' : '');
        applyCardImage(front, card ? cardUrl(card) : '');

        const back = document.createElement('img');
        back.className = 'card-back';
        back.alt = card ? '' : (options?.alt || 'Card back');
        applyCardImage(back, cardBackUrl(cardType));

        inner.appendChild(front);
        inner.appendChild(back);
        wrap.appendChild(inner);

        // Every card carries its own flip ("turn around") API, so callers never
        // need to spawn a second element to reveal a face.
        const resolveCardData = (c) => c
            || (wrap.dataset.cardCode ? {cardType: wrap.dataset.cardType, card: wrap.dataset.cardCode} : null);
        wrap.cardApi = {
            el: wrap,
            reveal(c) { revealCardFace(wrap, resolveCardData(c)); },
            flip(c) { return flipCardReveal(wrap, resolveCardData(c)); },
            showBack() {
                wrap.dataset.cardFace = 'false';
                if (inner) {
                    if (gsap) gsap.set(inner, {rotateY: 0});
                    else inner.style.transform = 'rotateY(0deg)';
                }
            },
            isFaceUp() { return wrap.dataset.cardFace === 'true'; }
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

    function playAnimation(target, parameters) {
        if (!target) return Promise.resolve();
        if (prefersReducedMotion()) {
            applyFinalAnimationState(target, parameters);
            return Promise.resolve();
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
            easing: 'cubic-bezier(.22,.9,.3,1)',
            fill: 'both'
        });
        return animation.finished.catch(() => undefined).then(() => {
            applyFinalAnimationState(target, parameters);
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
        const available = Math.max(rect.width - baseWidth, baseWidth);
        const zoneType = resolved.options.type || resolved.options.zoneType || element.dataset.zoneType;
        // Optional FIXED slot count: when set, cards occupy fixed slots (by index)
        // that never re-center as more cards are added — so e.g. a played card flies
        // straight to its side slot instead of centering first. Hand zones omit it.
        const slots = Math.max(Number(resolved.options.slotTotal) || 0, total);
        // Tighter, overlap-based spacing — flexible for any card count.
        const spacingScale = resolved.options.spacingScale ?? (zoneType === 'center' ? 0.45 : 0.4);
        const naturalSpacing = baseWidth * spacingScale;
        const spacing = slots > 1 ? Math.min(naturalSpacing, available / (slots - 1)) : 0;
        const maxTilt = resolved.options.maxTilt ?? (zoneType === 'center' ? 4 : 6);
        const yArc = resolved.options.yArc ?? (zoneType === 'center' ? 3 : 5);
        const baseOffsetY = Number(resolved.options.baseOffsetY) || 0;

        if (zoneType !== 'mini' && zoneType !== 'fan') {
            const minWidth = Math.ceil(baseWidth + (slots > 1 ? spacing * (slots - 1) : 0) + 28);
            const minHeight = Math.ceil(baseHeight + yArc + Math.abs(baseOffsetY) + 28);
            element.style.minWidth = `${minWidth}px`;
            element.style.minHeight = `${minHeight}px`;
        }

        layoutItems.forEach((item, index) => {
            const centered = index - ((slots - 1) / 2);
            const normalized = slots > 1 ? centered / ((slots - 1) / 2) : 0;
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

    function captureRects(zone) {
        const resolved = getZone(zone);
        if (!resolved) return new Map();
        const rects = new Map();
        Array.from(resolved.element.children).forEach((el) => {
            if (el.nodeType !== 1) return;
            rects.set(el, {
                rect: el.getBoundingClientRect(),
                transform: getComputedStyle(el).transform === 'none' ? '' : getComputedStyle(el).transform
            });
        });
        return rects;
    }

    function animateFromRect(el, firstRect, lastRect, duration) {
        if (!el || !firstRect || !lastRect) return Promise.resolve();
        const dx = firstRect.left - lastRect.left;
        const dy = firstRect.top - lastRect.top;
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return Promise.resolve();
        const baseTransform = getComputedStyle(el).transform === 'none' ? '' : getComputedStyle(el).transform;
        return playAnimation(el, {
            transform: [
                `translate3d(${dx}px, ${dy}px, 0) ${baseTransform}`,
                baseTransform || 'translate3d(0, 0, 0)'
            ],
            duration: duration ?? MOTION.standardMs,
            ease: MOTION.ease
        }).then(() => {
            el.style.transform = '';
        });
    }

    // Reflow a zone purely through the CSS-variable slot transform. Movement is
    // animated by the cards' own CSS `transition: transform`, so NO inline transforms
    // are ever written here — this makes it impossible for an interrupted animation to
    // strand a card at a stale position (the bug that kept one hand card separated).
    function animateZoneChange(zone, mutation, options) {
        const resolved = getZone(zone);
        if (!resolved) {
            mutation?.();
            return Promise.resolve();
        }
        mutation?.();
        const cards = options?.cards ? Array.from(options.cards) : null;
        if (cards) cards.forEach((card) => resolved.element.appendChild(card));
        layoutZone(resolved, cards || undefined, options?.layout);
        return Promise.resolve();
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
        const first = new Map(existingCards.map((el) => [el, el.getBoundingClientRect()]));
        while (existingCards.length > count) existingCards.pop()?.remove();
        while (existingCards.length < count) {
            const card = renderCardImage({
                cardType: options?.cardType,
                className: options?.className || 'seat-card',
                alt: options?.alt || 'Card back'
            });
            cardsEl.appendChild(card);
            existingCards.push(card);
        }
        const total = existingCards.length;
        existingCards.forEach((card, index) => {
            const centeredIndex = index - ((total - 1) / 2);
            const rotate = centeredIndex * (options?.spread ?? 5);
            const lift = Math.abs(centeredIndex) * 1.5;
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
        existingCards.forEach((card) => {
            const previous = first.get(card);
            const next = card.getBoundingClientRect();
            if (previous) {
                animateFromRect(card, previous, next, MOTION.quickMs);
                return;
            }
            const slotTransform = card.style.transform || 'translate3d(0,0,0)';
            // Cards fly and pop in at FULL size — a scaled-down start made the
            // back look smaller than the card it lands as.
            if (hasSource && next.width) {
                const dx = (source.left + (source.width || 0) / 2) - (next.left + next.width / 2);
                const dy = (source.top + (source.height || 0) / 2) - (next.top + next.height / 2);
                playAnimation(card, {
                    opacity: [0.35, 1],
                    transform: [
                        `translate3d(${dx}px, ${dy}px, 0) ${slotTransform}`,
                        slotTransform
                    ],
                    duration: options?.dealDuration ?? MOTION.dealMs,
                    delay: (dealtIndex++) * (options?.dealStagger ?? 70),
                    ease: MOTION.ease
                });
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

    function renderDeckTower(deckTower, deckStack, cardsLeft, options) {
        if (!deckTower) return;
        const deckTowerCount = Math.max((cardsLeft ?? 0) - (options?.featuredCard ? 1 : 0), 0);
        const exhausting = options?.exhausting === true;
        if (deckStack) deckStack.classList.toggle('is-empty', deckTowerCount <= 0 && !exhausting);
        if (deckTowerCount <= 0 && !exhausting) { deckTower.replaceChildren(); return; }

        const target = exhausting ? 1 : Math.min(deckTowerCount, 18);
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
                alt: options?.alt || 'Deck'
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

    function getReservedTargetRect(toZone, toIndex) {
        const resolved = getZone(toZone);
        if (!resolved) return null;
        const placeholder = reserveSlot(resolved, toIndex);
        return placeholder?.getBoundingClientRect() || resolved.element.getBoundingClientRect();
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
        // is interrupted or its promise never settles.
        const safety = setTimeout(cleanup, duration + delay + 400);
        const animation = gsap ? new Promise((resolve) => {
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
        });
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
                alt: options?.alt || 'Card'
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

    // Drag the REAL card element (no clone). The card is lifted into the fixed
    // overlay layer so it can move across the whole page without clipping and stops
    // participating in the hand fan; on cancel it is returned to its slot.
    function startDragCard(options) {
        const sourceEl = options?.sourceEl;
        if (!sourceEl) return null;
        const pointer = options?.pointer || {x: 0, y: 0};
        const rect = sourceEl.getBoundingClientRect();
        const originZone = getZone(options?.originZone || options?.originHand || sourceEl.parentElement);
        const session = {
            el: sourceEl,
            originEl: sourceEl,
            originRect: rect,
            originParent: sourceEl.parentElement,
            originNextSibling: sourceEl.nextElementSibling,
            // Grab the card at the point under the cursor so it doesn't jump.
            pointerOffsetX: clamp(pointer.x - rect.left, 0, rect.width) || rect.width / 2,
            pointerOffsetY: clamp(pointer.y - rect.top, 0, rect.height) || rect.height / 2,
            originZone,
            bounds: resolveDragBounds(options),
            accepted: false,
            overDropZone: false,
            lastPoint: pointer
        };
        sourceEl.style.width = `${rect.width}px`;
        sourceEl.style.height = `${rect.height}px`;
        sourceEl.classList.add(options?.className || 'drag-ghost');
        ensureOverlayLayer().appendChild(sourceEl);
        sourceEl.style.transform = `translate3d(${rect.left}px, ${rect.top}px, 0)`;
        // The card left the hand container, so the remaining cards re-fan to close the gap.
        if (originZone) layoutZone(originZone);
        updateDragCard(session, pointer.x, pointer.y);
        return session;
    }

    function updateDragCard(session, x, y) {
        if (!session?.el) return;
        const width = session.originRect?.width || 96;
        const height = session.originRect?.height || 134;
        const bounds = session.bounds || resolveDragBounds();
        const moveX = clamp(x - session.pointerOffsetX, bounds.left, bounds.right - width);
        const moveY = clamp(y - session.pointerOffsetY, bounds.top, bounds.bottom - height);
        session.lastPoint = {x, y};
        session.el.style.transform = `translate3d(${moveX}px, ${moveY}px, 0) rotate(var(--drag-rot, 0deg)) scale(1.04)`;
    }

    function restoreDraggedElement(session) {
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

    // Fly a card that is ALREADY placed in its slot, starting visually from `fromRect`,
    // by animating the composable --deal-* vars so it always lands EXACTLY on its slot
    // (composing with the fan transform). `faceDown:true` shows the back during flight
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
            el.classList.remove(flightClass);
            DEAL.forEach((p) => el.style.removeProperty(p));
            options?.onLand?.(el);
        };
        if (!gsap) { cleanup(); return Promise.resolve(); }
        gsap.set(el, {
            '--deal-x': `${dx}px`, '--deal-y': `${dy}px`,
            '--deal-rot': `${options?.spin ?? 8}deg`, '--deal-scale': options?.fromScale ?? 1.04
        });
        return new Promise((resolve) => {
            gsap.to(el, {
                '--deal-x': '0px', '--deal-y': '0px', '--deal-rot': '0deg', '--deal-scale': 1,
                duration: (options?.duration ?? 300) / 1000,
                delay: (options?.delay ?? 0) / 1000,
                ease: options?.ease || 'power3.out',
                onComplete() { cleanup(); resolve(); }
            });
        });
    }

    // Fly an OVERLAY-layer element (a re-parented real card or a clone) to a target
    // rect via a single inline-transform tween. Params: duration(ms), ease, fromScale,
    // toScale, fromRot, toRot, fade(boolean → fade to 0 on arrival), onLand(el).
    function flyOverlayTo(el, toRect, options) {
        if (!el || !toRect) return Promise.resolve();
        const fromRect = el.getBoundingClientRect();
        const duration = prefersReducedMotion() ? 80 : (options?.duration ?? MOTION.dealMs);
        const fromX = fromRect.left;
        const fromY = fromRect.top;
        // Scale is applied about the element's center, so center the destination on the
        // UNSCALED box (offsetWidth/Height), not the scaled getBoundingClientRect size.
        const nw = el.offsetWidth || fromRect.width;
        const nh = el.offsetHeight || fromRect.height;
        const toX = toRect.left + (toRect.width - nw) / 2;
        const toY = toRect.top + (toRect.height - nh) / 2;
        return playAnimation(el, {
            transform: [
                `translate3d(${fromX}px, ${fromY}px, 0) rotate(${options?.fromRot || '0deg'}) scale(${options?.fromScale ?? 1.04})`,
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
            if (!card) return;
            raised = card;
            card.__ucPrevZ = card.style.zIndex;
            card.style.zIndex = '60';
            card.classList.add('is-raised');
            // Expose the currently raised card so the drag picks THIS card (the one
            // the user sees enlarged), regardless of exact pointer-vs-element overlap.
            element.__ucRaisedCard = card;
        };
        // Pick the card nearest the cursor X, but ONLY when the cursor is actually over
        // the cards' area — not the empty padding to the left/right/top/bottom of the
        // hand. Otherwise hovering blank space would enlarge the edge card.
        const pickCard = (x, y) => {
            const cards = getZoneCards(resolved || {element}).filter((card) =>
                !card.classList.contains('is-dragging') && !card.classList.contains('drag-ghost'));
            if (!cards.length) return null;
            let minL = Infinity, maxR = -Infinity, minT = Infinity, maxB = -Infinity;
            let best = null, bestDist = Infinity;
            cards.forEach((card) => {
                const r = card.getBoundingClientRect();
                if (r.left < minL) minL = r.left;
                if (r.right > maxR) maxR = r.right;
                if (r.top < minT) minT = r.top;
                if (r.bottom > maxB) maxB = r.bottom;
                const dist = Math.abs(x - (r.left + r.width / 2));
                if (dist < bestDist) { bestDist = dist; best = card; }
            });
            if (x < minL || x > maxR || y < minT || y > maxB) return null;
            return best;
        };
        element.addEventListener('pointermove', (event) => {
            if (options?.isActive && !options.isActive()) {
                lower();
                return;
            }
            const x = Number(event.clientX ?? event.pageX ?? 0);
            const y = Number(event.clientY ?? event.pageY ?? 0);
            const target = pickCard(x, y);
            if (target) raise(target);
            else lower();
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
            let best = null;
            let bestDist = Infinity;
            getZoneCards(resolved || {element}).forEach((card) => {
                if (card.classList.contains('drag-ghost')) return;
                const r = card.getBoundingClientRect();
                const d = Math.abs(x - (r.left + r.width / 2));
                if (d < bestDist) { bestDist = d; best = card; }
            });
            return best;
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
                        if (!cardEl) return;
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
                        options?.onEnd?.(session, event);
                    }
                }
            });
        element.__ucHandDrag = interactable;
        return interactable;
    }

    function markCardReturning(hand, cardKeyValue, options) {
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
        const front = cardEl.querySelector('.card-front');
        if (front) applyCardImage(front, cardUrl(cardData));
    }

    function flipCardReveal(cardEl, cardData) {
        if (!cardEl) return Promise.resolve();
        const inner = cardEl.querySelector('.card-inner');
        if (!inner) {
            revealCardFace(cardEl, cardData);
            return Promise.resolve();
        }
        if (prefersReducedMotion()) {
            revealCardFace(cardEl, cardData);
            return Promise.resolve();
        }
        if (!gsap) {
            revealCardFace(cardEl, cardData);
            return Promise.resolve();
        }
        const dur = 0.20;
        gsap.set(inner, {rotateY: 0});
        return new Promise((resolve) => {
            gsap.to(inner, {
                rotateY: 90,
                duration: dur,
                ease: 'power2.in',
                onComplete() {
                    revealCardFace(cardEl, cardData);
                    // Continue rotation to 180° so .card-front (CSS rotateY(180deg))
                    // composites to 360°=0° → facing viewer; .card-back composites to 180° → hidden.
                    gsap.fromTo(inner,
                        {rotateY: 90},
                        {rotateY: 180, duration: dur, ease: 'power2.out', onComplete: resolve}
                    );
                }
            });
        });
    }

    function animateTrickCollect(trickCards, winnerSeatEl) {
        if (!trickCards || !trickCards.length) return Promise.resolve();
        const duration = prefersReducedMotion() ? 0.06 : 0.22;
        if (!gsap || !winnerSeatEl) {
            // fallback: just fade out
            return Promise.all(trickCards.map((card) =>
                playAnimation(card, {opacity: [1, 0], duration: duration * 1000, ease: 'inOut(2)'})
            ));
        }
        const targetRect = winnerSeatEl.getBoundingClientRect();
        const toX = targetRect.left + targetRect.width / 2;
        const toY = targetRect.top + targetRect.height / 2;
        const promises = trickCards.map((card, i) => {
            const fromRect = card.getBoundingClientRect();
            const dx = toX - (fromRect.left + fromRect.width / 2);
            const dy = toY - (fromRect.top + fromRect.height / 2);
            return new Promise((resolve) => {
                gsap.to(card, {
                    x: dx,
                    y: dy,
                    scale: 0.28,
                    opacity: 0,
                    duration,
                    delay: i * 0.038,
                    ease: 'power2.in',
                    onComplete: resolve
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
        back.appendChild(renderCardImage({cardType, alt: 'Card back'}));

        const front = document.createElement('div');
        front.className = 'deal-card-face deal-card-front';
        front.appendChild(renderCardImage({card: incomingCard, cardType, alt: 'Card'}));

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
})();
