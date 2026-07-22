(() => {
    // Card codes (e.g. "C1") currently selected in the hand for declaring.
    const selectedCodes = new Set();
    let currentApi = null;
    let declarationMade = false;
    let pendingDeclarationSignature = null;
    let successPopupTimer = null;
    // Fallback for when localStorage is unavailable; otherwise the skip flag
    // lives in storage only, so clearing the key re-opens the phase (the admin
    // sandbox relies on that).
    let memorySkipFlag = false;

    const OF_A_KIND = {1: 'ACES', 2: 'TWOS', 3: 'THREES'};

    function cardValue(code) {
        return Number(String(code || '').slice(1));
    }

    function cardSuit(code) {
        return String(code || '').charAt(0);
    }

    function skipStorageKey() {
        return 'treseta-declare-skip:'
            + (document.getElementById('game-container')?.dataset.gameId || '');
    }

    function hasSkipped() {
        try {
            return localStorage.getItem(skipStorageKey()) === '1';
        } catch (error) {
            return memorySkipFlag;
        }
    }

    function markSkipped() {
        try {
            localStorage.setItem(skipStorageKey(), '1');
        } catch (error) {
            memorySkipFlag = true;
        }
    }

    function isDeclarePhase(api) {
        const game = api.getGame();
        return !!game && Array.isArray(game.canDeclareUserIds)
            && game.canDeclareUserIds.some((id) => String(id) === String(api.currentUserId))
            && String(game.playersTurn?.id) === String(api.currentUserId)
            && !hasSkipped();
    }

    function myDeclarations(api) {
        const declared = api.getGame()?.declarations;
        if (!Array.isArray(declared)) return [];
        return declared.filter((d) => String(d.player?.id) === String(api.currentUserId));
    }

    function wasDeclared(declarations, type, suits) {
        return declarations.some((declaration) => declaration.type === type
            && Array.isArray(declaration.suits)
            && ((declaration.suits.length === suits.size
                    && declaration.suits.every((suit) => suits.has(cardSuit(suit))))
                || (declaration.suits.length === 4 && suits.size === 3)));
    }

    // The declaration the current selection forms, or null if it forms none
    // (or was already declared).
    function selectionDeclaration(api) {
        const cards = api.getHand().filter((card) => selectedCodes.has(card.card));
        if (cards.length < 3 || cards.length > 4 || cards.length !== selectedCodes.size) return null;
        const values = new Set(cards.map((card) => cardValue(card.card)));
        const suits = new Set(cards.map((card) => cardSuit(card.card)));
        const mine = myDeclarations(api);
        if (values.size === 1 && suits.size === cards.length) {
            const type = OF_A_KIND[values.values().next().value];
            if (type && !wasDeclared(mine, type, suits)) return {type, cards};
        }
        if (suits.size === 1 && cards.length === 3 && [1, 2, 3].every((value) => values.has(value))) {
            const suit = suits.values().next().value;
            if (!wasDeclared(mine, 'NAPOLITANA', suits))
                return {type: 'NAPOLITANA', cards};
        }
        return null;
    }

    function typeLabel(type) {
        return window.t ? window.t('treseta.declare.type.' + type) : type;
    }

    function declaredCards(declaration) {
        const suits = Array.isArray(declaration.suits) ? declaration.suits : [];
        if (declaration.type === 'NAPOLITANA') {
            const suit = cardSuit(suits[0]);
            return [1, 2, 3].map((value) => ({cardType: 'ITALIAN', card: suit + value}));
        }
        const value = {ACES: 1, TWOS: 2, THREES: 3}[declaration.type];
        return suits.map((suit) => ({cardType: 'ITALIAN', card: cardSuit(suit) + value}));
    }

    function declarationSignature(declaration) {
        return declaration.type + ':' + declaredCards(declaration)
            .map((card) => card.card)
            .sort()
            .join(',');
    }

    function selectedDeclarationSignature(declaration) {
        return declaration.type + ':' + declaration.cards
            .map((card) => card.card)
            .sort()
            .join(',');
    }

    function showDeclarationSuccess(api) {
        let popup = document.getElementById('treseta-declaration-success');
        if (!popup) {
            popup = document.createElement('div');
            popup.id = 'treseta-declaration-success';
            popup.className = 'treseta-declaration-success';
            (api.container.querySelector('.table-surface') || api.container).appendChild(popup);
        }
        popup.textContent = window.t ? window.t('treseta.declare.success') : 'Declaration added';
        popup.hidden = false;
        popup.classList.remove('is-visible');
        void popup.offsetWidth;
        popup.classList.add('is-visible');
        clearTimeout(successPopupTimer);
        successPopupTimer = setTimeout(() => {
            popup.classList.remove('is-visible');
            setTimeout(() => {
                if (!popup.classList.contains('is-visible')) popup.hidden = true;
            }, 180);
        }, 2600);
    }

    // One-time delegated click handler: selecting cards never conflicts with
    // playing, which uses drag or double-click.
    function ensureHandClickHandler() {
        const hand = document.getElementById('hand-cards');
        if (!hand || hand.dataset.tresetaDeclareBound) return;
        hand.dataset.tresetaDeclareBound = '1';
        hand.addEventListener('click', (event) => {
            if (!currentApi || !isDeclarePhase(currentApi)) return;
            const card = event.target.closest('.hand-card');
            const code = card?.dataset.cardCode;
            if (!code || ![1, 2, 3].includes(cardValue(code))) return;
            // A completed deal can leave an inline 3-D transform on the card's
            // flip layer. Declaration selection only enlarges a face-up hand card.
            card.dataset.cardFace = 'true';
            card.querySelector('.card-inner')?.style.removeProperty('transform');
            if (selectedCodes.has(code)) selectedCodes.delete(code);
            else selectedCodes.add(code);
            renderDeclarationUi(currentApi);
        });
    }

    function syncSelectedCardStyles(active) {
        document.querySelectorAll('#hand-cards .hand-card').forEach((el) => {
            el.classList.toggle('treseta-declare-selected',
                active && selectedCodes.has(el.dataset.cardCode));
        });
    }

    function renderPanel(api) {
        let panel = document.getElementById('treseta-declarations');
        if (!isDeclarePhase(api)) {
            panel?.remove();
            return;
        }
        if (!panel) {
            panel = document.createElement('div');
            panel.id = 'treseta-declarations';
            panel.className = 'treseta-declarations';
            (api.container.querySelector('.table-surface') || api.container).appendChild(panel);
        }
        panel.replaceChildren();

        const title = document.createElement('div');
        title.className = 'treseta-declare-title';
        title.textContent = window.t ? window.t('treseta.declare.title') : 'Declarations';
        panel.appendChild(title);

        const hint = document.createElement('div');
        hint.className = 'treseta-declare-hint';
        hint.textContent = window.t ? window.t('treseta.declare.hint') : 'Select cards to declare';
        panel.appendChild(hint);

        const actions = document.createElement('div');
        actions.className = 'treseta-declare-actions';

        const declaration = selectionDeclaration(api);
        const declareButton = document.createElement('button');
        declareButton.type = 'button';
        declareButton.className = 'treseta-declare-button treseta-declare-confirm';
        declareButton.disabled = !declaration;
        declareButton.textContent = window.t ? window.t('treseta.declare.confirm') : 'Declare';
        if (declaration) {
            declareButton.addEventListener('click', () => {
                declareButton.disabled = true;
                declarationMade = true;
                pendingDeclarationSignature = selectedDeclarationSignature(declaration);
                api.send('/app/game/declare', {
                    cards: declaration.cards.map((card) => ({cardType: card.cardType, card: card.card}))
                });
                selectedCodes.clear();
                renderDeclarationUi(api);
            });
        }
        actions.appendChild(declareButton);

        const endButton = document.createElement('button');
        endButton.type = 'button';
        endButton.className = 'treseta-declare-button treseta-declare-skip';
        const hasDeclaration = declarationMade || myDeclarations(api).length > 0;
        endButton.textContent = window.t
            ? window.t(hasDeclaration ? 'treseta.declare.end' : 'treseta.declare.skip')
            : (hasDeclaration ? 'End' : 'Skip');
        endButton.addEventListener('click', () => {
            markSkipped();
            selectedCodes.clear();
            api.refreshHand?.();
            renderDeclarationUi(api);
        });
        actions.appendChild(endButton);

        panel.appendChild(actions);
    }

    // On phones an in-place zoom cannot fit the screen, so a tap opens a
    // centred overlay with the player's declared sets at full size instead.
    function openDeclaredOverlay(playerName, declarations) {
        document.getElementById('treseta-declared-overlay')?.remove();
        const overlay = document.createElement('div');
        overlay.id = 'treseta-declared-overlay';
        overlay.className = 'treseta-declared-overlay';
        overlay.addEventListener('click', () => overlay.remove());
        const panel = document.createElement('div');
        panel.className = 'treseta-declared-overlay-panel';
        const title = document.createElement('div');
        title.className = 'treseta-declared-overlay-title';
        title.textContent = playerName || '';
        panel.appendChild(title);
        declarations.forEach((declaration) => {
            const row = document.createElement('div');
            row.className = 'treseta-declared-overlay-row';
            const label = document.createElement('div');
            label.className = 'treseta-declared-overlay-label';
            label.textContent = typeLabel(declaration.type);
            row.appendChild(label);
            const cards = document.createElement('div');
            cards.className = 'treseta-declared-overlay-cards';
            declaredCards(declaration).forEach((card) => {
                const el = window.UltracardsGameUi?.renderCardImage({
                    card,
                    className: 'treseta-declared-overlay-card',
                    alt: card.card
                });
                if (el) cards.appendChild(el);
            });
            row.appendChild(cards);
            panel.appendChild(row);
        });
        overlay.appendChild(panel);
        document.body.appendChild(overlay);
    }

    // A player's declared cards, rendered face-up as a badge under a seat.
    // Hover (desktop) enlarges a card in place; on phones a tap opens the
    // overlay instead. Shared with the game-history replay.
    function attachDeclaredBadge(seat, playerName, declarations) {
        let badge = seat.querySelector('.treseta-declared-cards');
        if (!declarations.length) {
            badge?.remove();
            return;
        }
        const signature = declarations.map((d) => d.type + ':' + (d.suits || []).join(',')).join('|');
        if (badge && badge.dataset.signature === signature) return;
        if (!badge) {
            badge = document.createElement('div');
            badge.className = 'treseta-declared-cards';
            badge.addEventListener('click', (event) => {
                const cardEl = event.target.closest('.treseta-declared-card');
                if (!cardEl) return;
                if (window.matchMedia('(max-width: 900px)').matches) {
                    openDeclaredOverlay(badge.dataset.playerName, JSON.parse(badge.dataset.declarations));
                    return;
                }
                const wasZoomed = cardEl.classList.contains('is-zoomed');
                badge.querySelectorAll('.is-zoomed').forEach((el) => el.classList.remove('is-zoomed'));
                if (!wasZoomed) cardEl.classList.add('is-zoomed');
            });
            seat.appendChild(badge);
        }
        badge.dataset.signature = signature;
        badge.dataset.playerName = playerName || '';
        badge.dataset.declarations = JSON.stringify(declarations.map((d) => ({type: d.type, suits: d.suits})));
        badge.replaceChildren();
        declarations.forEach((declaration) => {
            const set = document.createElement('div');
            set.className = 'treseta-declared-set';
            set.title = typeLabel(declaration.type);
            declaredCards(declaration).forEach((card) => {
                const el = window.UltracardsGameUi?.renderCardImage({
                    card,
                    className: 'treseta-declared-card',
                    alt: card.card
                });
                if (el) set.appendChild(el);
            });
            badge.appendChild(set);
        });
    }

    // Opponents' declared cards at their seats (the player sees their own in
    // the declare panel and scoring).
    function renderSeatDeclarations(api) {
        const declared = api.getGame()?.declarations;
        const byPlayer = new Map();
        (Array.isArray(declared) ? declared : []).forEach((declaration) => {
            const id = declaration.player?.id;
            if (id == null) return;
            const key = String(id);
            if (!byPlayer.has(key)) byPlayer.set(key, []);
            byPlayer.get(key).push(declaration);
        });
        document.querySelectorAll('.player-seat[data-player-id]').forEach((seat) => {
            const isSelf = String(seat.dataset.playerId) === String(api.currentUserId);
            const list = isSelf ? [] : (byPlayer.get(String(seat.dataset.playerId)) || []);
            const name = seat.dataset.playerName || seat.querySelector('.seat-name')?.textContent || '';
            attachDeclaredBadge(seat, name, list);
        });
    }

    // The game-history replay reuses the exact same badge + overlay rendering.
    window.UltracardsTresetaDeclarations = {attachDeclaredBadge, declaredCards, typeLabel};

    function renderDeclarationUi(api) {
        currentApi = api;
        const declarations = myDeclarations(api);
        if (!declarations.length) declarationMade = false;
        declarationMade ||= declarations.length > 0;
        if (pendingDeclarationSignature
            && declarations.some((declaration) => declarationSignature(declaration) === pendingDeclarationSignature)) {
            pendingDeclarationSignature = null;
            showDeclarationSuccess(api);
        }
        ensureHandClickHandler();
        const active = isDeclarePhase(api);
        if (!active) selectedCodes.clear();
        else {
            // Drop selections for cards no longer in hand (e.g. auto-played).
            const inHand = new Set(api.getHand().map((card) => card.card));
            selectedCodes.forEach((code) => {
                if (!inHand.has(code)) selectedCodes.delete(code);
            });
        }
        syncSelectedCardStyles(active);
        renderPanel(api);
        renderSeatDeclarations(api);
    }

    window.UltracardsGameRuntime?.register({
        name: 'treseta',
        cardType: 'ITALIAN',
        features: {deck: true, trump: false, teams: true, teammateHand: false, previousRound: true},
        cardStrength: {3: 0, 2: 1, 1: 2, 13: 3, 12: 4, 11: 5, 7: 6, 6: 7, 5: 8, 4: 9},
        opponentHandRotation: false,
        opponentCardSpread: 0,
        opponentCardLift: 0,
        historyCardSpread: 0,
        historyCardLift: 0,
        displayPoints(points) {
            return Math.floor((Number(points) || 0) / 3 * 10) / 10;
        },
        canPlayCard(card, game, hand) {
            const leadSuit = game?.playedCards?.[0]?.card?.charAt(0);
            if (!leadSuit) return true;
            return !hand.some((entry) => entry?.card?.charAt(0) === leadSuit)
                || card?.card?.charAt(0) === leadSuit;
        },
        isDeclarationPhase(game, currentUserId) {
            return Array.isArray(game?.canDeclareUserIds)
                && game.canDeclareUserIds.some((id) => String(id) === String(currentUserId))
                && String(game.playersTurn?.id) === String(currentUserId)
                && !hasSkipped();
        },
        canDeclareCard(card) {
            return [1, 2, 3].includes(cardValue(card?.card));
        },
        onStateChanged: renderDeclarationUi
    });
})();
