(() => {
    const button = document.getElementById('mobile-chat-toggle');
    if (!button) {
        return;
    }
    const panel = document.querySelector(button.dataset.chatPanel);
    if (!panel) {
        return;
    }
    const badge = button.querySelector('.mobile-chat-badge');

    button.addEventListener('click', () => {
        const open = document.body.classList.toggle('mobile-chat-open');
        if (open && badge) {
            badge.hidden = true;
        }
    });

    document.addEventListener('click', (event) => {
        if (!document.body.classList.contains('mobile-chat-open')) {
            return;
        }
        if (panel.contains(event.target) || button.contains(event.target)) {
            return;
        }
        document.body.classList.remove('mobile-chat-open');
    });

    const messages = panel.querySelector('.chat-messages');
    if (messages && badge) {
        // Ignore the initial hydration burst so the badge only marks real news.
        const readyAt = Date.now() + 2000;
        new MutationObserver((mutations) => {
            if (Date.now() < readyAt) {
                return;
            }
            if (document.body.classList.contains('mobile-chat-open')) {
                return;
            }
            // NOTE: not offsetParent — that is always null for position:fixed
            // elements, which silently disabled the badge entirely.
            if (!button.offsetWidth) {
                return; // button hidden => desktop layout, chat is inline
            }
            if (mutations.some((mutation) => mutation.addedNodes.length)) {
                badge.hidden = false;
            }
        }).observe(messages, { childList: true });
    }
})();
