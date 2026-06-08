(() => {
    const storageKey = 'uc-theme';
    const savedTheme = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = savedTheme || (systemDark ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', theme);
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

    function renderCardImage(options) {
        const img = document.createElement('img');
        const card = options?.card || null;
        const cardType = normalizeCardType(card?.cardType || options?.cardType);
        img.className = options?.className || 'game-card';
        img.alt = options?.alt || (card ? 'Card' : 'Card back');
        img.src = card ? cardUrl(card) : cardBackUrl(cardType);
        img.dataset.cardType = cardType;
        img.dataset.cardFace = card ? 'true' : 'false';
        if (card) {
            img.dataset.cardCode = card.card || '';
            img.dataset.cardKey = cardKey(card);
        }
        return img;
    }

    function hydrateCardImages(root) {
        const scope = root || document;
        scope.querySelectorAll('[data-card-code][data-card-face="true"]').forEach((img) => {
            const src = cardUrl({
                cardType: img.dataset.cardType || 'ITALIAN',
                card: img.dataset.cardCode
            });
            if (src) img.src = src;
        });
        scope.querySelectorAll('[data-card-face="false"]').forEach((img) => {
            img.src = cardBackUrl(img.dataset.cardType || 'ITALIAN');
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

    function playAnimation(target, parameters) {
        if (!target) return Promise.resolve();
        if (prefersReducedMotion()) {
            applyFinalAnimationState(target, parameters);
            return Promise.resolve();
        }
        const duration = Number(parameters?.duration) || MOTION.standardMs;
        const delay = Number(parameters?.delay) || 0;
        const animate = animeApi.animate;
        if (typeof animate === 'function') {
            try {
                const animation = animate(target, parameters);
                if (animation?.finished?.then) {
                    return animation.finished.catch(() => undefined);
                }
                if (animation?.then) {
                    return animation.then(() => undefined).catch(() => undefined);
                }
                return new Promise((resolve) => window.setTimeout(resolve, duration + delay + 24))
                    .then(() => {
                        applyFinalAnimationState(target, parameters);
                    });
            } catch (err) {
                console.warn('Anime.js animation failed, falling back to Web Animations API.', err);
            }
        }
        return playWebAnimationFallback(target, parameters);
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
        const sample = layoutItems.find((item) => !item.placeholder)?.el;
        const sampleRect = sample?.getBoundingClientRect();
        const style = getComputedStyle(element);
        const baseWidth = resolved.options.cardWidth
            || sampleRect?.width
            || Number.parseFloat(style.getPropertyValue('--hand-card-width'))
            || 100;
        const baseHeight = resolved.options.cardHeight || sampleRect?.height || baseWidth * 1.38;
        const available = Math.max(rect.width - baseWidth, baseWidth);
        const zoneType = resolved.options.type || resolved.options.zoneType || element.dataset.zoneType;
        const spacingScale = resolved.options.spacingScale ?? (zoneType === 'center' ? 0.72 : 0.52);
        const naturalSpacing = baseWidth * spacingScale;
        const spacing = total > 1 ? Math.min(naturalSpacing, available / (total - 1)) : 0;
        const maxTilt = resolved.options.maxTilt ?? (zoneType === 'center' ? 8 : 18);
        const yArc = resolved.options.yArc ?? (zoneType === 'center' ? 4 : 16);
        const baseOffsetY = Number(resolved.options.baseOffsetY) || 0;

        if (zoneType !== 'mini') {
            const minWidth = Math.ceil(baseWidth + (total > 1 ? spacing * (total - 1) : 0) + 28);
            const minHeight = Math.ceil(baseHeight + yArc + Math.abs(baseOffsetY) + 28);
            element.style.minWidth = `${minWidth}px`;
            element.style.minHeight = `${minHeight}px`;
        }

        layoutItems.forEach((item, index) => {
            const centered = index - ((total - 1) / 2);
            const normalized = total > 1 ? centered / ((total - 1) / 2) : 0;
            const x = centered * spacing;
            const y = Math.abs(normalized) * yArc + baseOffsetY;
            const rotation = normalized * maxTilt;
            const el = item.el;
            el.classList.add('game-hand-card');
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

    function animateZoneChange(zone, mutation, options) {
        const resolved = getZone(zone);
        if (!resolved) {
            mutation?.();
            return Promise.resolve();
        }
        const first = captureRects(resolved);
        mutation?.();
        const cards = options?.cards ? Array.from(options.cards) : null;
        if (cards) cards.forEach((card) => resolved.element.appendChild(card));
        layoutZone(resolved, cards || undefined, options?.layout);
        const last = captureRects(resolved);
        const animations = [];
        last.forEach((entry, el) => {
            const before = first.get(el);
            if (before) {
                animations.push(animateFromRect(el, before.rect, entry.rect, options?.duration));
                return;
            }
            if (!el.classList.contains('game-hand-placeholder')) {
                const transform = getComputedStyle(el).transform === 'none' ? '' : getComputedStyle(el).transform;
                animations.push(playAnimation(el, {
                    opacity: [0, 1],
                    transform: [`${transform} scale(.94)`, transform || 'scale(1)'],
                    duration: options?.duration ?? MOTION.quickMs,
                    ease: MOTION.ease
                }).then(() => {
                    el.style.opacity = '';
                    el.style.transform = '';
                }));
            }
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
        const first = new Map(existingCards.map((el) => [el, el.getBoundingClientRect()]));
        while (existingCards.length > count) existingCards.pop()?.remove();
        while (existingCards.length < count) {
            const img = renderCardImage({
                cardType: options?.cardType,
                className: options?.className || 'seat-card',
                alt: options?.alt || 'Card back'
            });
            cardsEl.appendChild(img);
            existingCards.push(img);
        }
        const total = existingCards.length;
        existingCards.forEach((img, index) => {
            const centeredIndex = index - ((total - 1) / 2);
            const rotate = centeredIndex * (options?.spread ?? 5);
            const lift = Math.abs(centeredIndex) * 1.5;
            img.style.transform = `translateY(${lift}px) rotate(${rotate}deg)`;
            img.style.zIndex = String(index + 1);
        });
        existingCards.forEach((img) => {
            const previous = first.get(img);
            const next = img.getBoundingClientRect();
            if (previous) {
                animateFromRect(img, previous, next, MOTION.quickMs);
                return;
            }
            playAnimation(img, {
                opacity: [0, 1],
                transform: [`${img.style.transform} scale(.92)`, img.style.transform],
                duration: MOTION.quickMs,
                ease: MOTION.ease
            });
        });
    }

    function renderDeckTower(deckTower, deckStack, cardsLeft, options) {
        if (!deckTower) return;
        deckTower.innerHTML = '';
        const deckTowerCount = Math.max((cardsLeft ?? 0) - (options?.featuredCard ? 1 : 0), 0);
        const exhausting = options?.exhausting === true;
        if (deckStack) deckStack.classList.toggle('is-empty', deckTowerCount <= 0 && !exhausting);
        if (deckTowerCount <= 0 && !exhausting) return;
        const count = exhausting ? 1 : Math.min(deckTowerCount, 18);
        for (let i = 0; i < count; i += 1) {
            const img = renderCardImage({
                cardType: options?.cardType,
                className: options?.className || '',
                alt: options?.alt || 'Deck'
            });
            const depthFromTop = count - i - 1;
            const xOffset = Number((depthFromTop * 0.65).toFixed(2));
            const yOffset = Number((-depthFromTop * 0.55).toFixed(2));
            const rotation = Number((((i % 3) - 1) * 0.65).toFixed(3));
            img.style.setProperty('--deck-offset-x', String(xOffset));
            img.style.setProperty('--deck-offset-y', String(yOffset));
            img.style.setProperty('--deck-rot', String(rotation));
            img.style.zIndex = String(i + 1);
            deckTower.appendChild(img);
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
        if (!clone || !fromRect || !toRect) return Promise.resolve();
        const duration = prefersReducedMotion() ? 80 : (options?.duration ?? MOTION.dealMs);
        const fromX = fromRect.left;
        const fromY = fromRect.top;
        const toX = toRect.left + (toRect.width - fromRect.width) / 2;
        const toY = toRect.top + (toRect.height - fromRect.height) / 2;
        const lift = Math.min(36, Math.max(12, Math.abs(toY - fromY) * 0.07));
        const midX = fromX + (toX - fromX) * 0.58;
        const midY = fromY + (toY - fromY) * 0.58 - lift;
        return playAnimation(clone, {
            transform: [
                `translate3d(${fromX}px, ${fromY}px, 0) rotate(${options?.fromRot || '0deg'}) scale(${options?.fromScale || 1})`,
                `translate3d(${midX}px, ${midY}px, 0) rotate(${options?.midRot || '-2deg'}) scale(${options?.midScale || 1.03})`,
                `translate3d(${toX}px, ${toY}px, 0) rotate(${options?.toRot || '0deg'}) scale(${options?.toScale || 1})`
            ],
            opacity: options?.fadeOut ? [1, 1, 0] : [1, 1, 1],
            duration,
            delay: Number(options?.delay) || 0,
            ease: options?.easing || MOTION.ease
        }).then(() => {
            clone.remove();
            options?.onDone?.();
        });
    }

    function animateCardTransfer(options) {
        const sourceEl = options?.cardEl || options?.sourceEl;
        const sourceRect = options?.sourceRect || sourceEl?.getBoundingClientRect();
        const targetRect = options?.targetRect || getReservedTargetRect(options?.toZone || options?.toHand, options?.toIndex);
        if (!sourceRect || !targetRect) return Promise.resolve();
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
            clone.style.width = `${sourceRect.width || targetRect.width || 96}px`;
            clone.style.height = sourceRect.height ? `${sourceRect.height}px` : 'auto';
            clone.style.transform = `translate3d(${sourceRect.left}px, ${sourceRect.top}px, 0)`;
            ensureOverlayLayer().appendChild(clone);
        }
        return animateOverlayToRect(clone, sourceRect, targetRect, options)
            .then(() => {
                if (options?.toZone || options?.toHand) clearReservedSlot(options.toZone || options.toHand);
            });
    }

    function startDragCard(options) {
        const sourceEl = options?.sourceEl;
        const pointer = options?.pointer || {x: 0, y: 0};
        const overlay = overlayCloneFromElement(sourceEl, {
            className: options?.className || 'drag-ghost'
        });
        if (!overlay) return null;
        const session = {
            clone: overlay.clone,
            originEl: sourceEl,
            originRect: overlay.rect,
            pointerOffsetX: overlay.rect.width / 2,
            pointerOffsetY: overlay.rect.height / 2,
            originZone: getZone(options?.originZone || options?.originHand || sourceEl?.parentElement),
            bounds: resolveDragBounds(options),
            accepted: false,
            overDropZone: false,
            lastPoint: pointer
        };
        sourceEl?.classList.add('is-dragging');
        layoutZone(session.originZone);
        updateDragCard(session, pointer.x, pointer.y);
        return session;
    }

    function updateDragCard(session, x, y) {
        if (!session?.clone) return;
        const rect = session.clone.getBoundingClientRect();
        const width = rect.width || session.originRect?.width || 96;
        const height = rect.height || session.originRect?.height || 134;
        const bounds = session.bounds || resolveDragBounds();
        const moveX = clamp(x - session.pointerOffsetX, bounds.left, bounds.right - width);
        const moveY = clamp(y - session.pointerOffsetY, bounds.top, bounds.bottom - height);
        session.lastPoint = {x, y};
        session.clone.style.transform = `translate3d(${moveX}px, ${moveY}px, 0) rotate(var(--drag-rot, 0deg)) scale(1.045)`;
    }

    function finishDragCard(session, options) {
        if (!session?.clone) return Promise.resolve();
        const targetRect = options?.targetRect || session.originRect;
        const currentRect = session.clone.getBoundingClientRect();
        const duration = options?.duration ?? (options?.accepted ? 170 : 240);
        return animateOverlayToRect(session.clone, currentRect, targetRect, {
            duration,
            easing: options?.accepted ? MOTION.ease : MOTION.snapEase,
            toScale: options?.accepted ? 0.94 : 1,
            toRot: options?.toRot || '0deg',
            fadeOut: options?.accepted === true
        }).then(() => {
            if (!options?.accepted) {
                session.originEl?.classList.remove('is-dragging');
                layoutZone(session.originZone);
            }
            options?.onDone?.();
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

    function markCardReturning(hand, cardKeyValue, options) {
        if (!hand || !cardKeyValue) return;
        const el = hand.querySelector(`[data-card-key="${CSS.escape(cardKeyValue)}"]`);
        if (!el) return;
        const className = options?.className || 'returning';
        const duration = Number(options?.duration) || 220;
        el.classList.remove('is-dragging');
        el.classList.add(className);
        playAnimation(el, {
            opacity: [0.72, 1],
            transform: ['translate3d(0, 12px, 0) scale(.94)', 'translate3d(0, 0, 0) scale(1)'],
            duration,
            ease: MOTION.snapEase
        }).then(() => el.classList.remove(className));
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
        animateHandChange: animateZoneChange,
        animateZoneChange,
        cardBackUrl,
        cardKey,
        cardUrl,
        animateCardTransfer,
        createDealFlipCard,
        clearReservedSlot,
        disableCardDrag,
        enableCardDrag,
        enableDropZone,
        finishDragCard,
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
