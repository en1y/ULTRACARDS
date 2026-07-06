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

    // While the chat is open, the panel and button are positioned straight from
    // the visualViewport geometry — they always sit fully inside the VISIBLE
    // area, whatever the on-screen keyboard, URL bar, scroll or zoom state is.
    let frame = null;
    const layout = () => {
        if (frame) {
            return;
        }
        frame = requestAnimationFrame(() => {
            frame = null;
            if (!document.body.classList.contains('mobile-chat-open')) {
                panel.style.top = '';
                panel.style.bottom = '';
                panel.style.height = '';
                button.style.top = '';
                return;
            }
            const vv = window.visualViewport;
            const viewTop = vv ? vv.offsetTop : 0;
            const viewHeight = vv ? vv.height : window.innerHeight;
            const gap = 10;
            const buttonSize = button.offsetHeight || 52;
            const buttonTop = viewTop + viewHeight - buttonSize - gap;
            panel.style.top = `${viewTop + gap}px`;
            panel.style.bottom = 'auto';
            panel.style.height = `${Math.max(buttonTop - (viewTop + gap) - gap, 200)}px`;
            button.style.top = `${buttonTop}px`;   // inline top beats the CSS bottom anchor
        });
    };

    const setOpen = (open) => {
        document.body.classList.toggle('mobile-chat-open', open);
        if (open && badge) {
            badge.hidden = true;
        }
        layout();
    };

    button.addEventListener('click', () => {
        setOpen(!document.body.classList.contains('mobile-chat-open'));
    });

    document.addEventListener('click', (event) => {
        if (!document.body.classList.contains('mobile-chat-open')) {
            return;
        }
        if (panel.contains(event.target) || button.contains(event.target)) {
            return;
        }
        setOpen(false);
    });

    if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', layout);
        window.visualViewport.addEventListener('scroll', layout);
    }
    window.addEventListener('resize', layout);
    document.addEventListener('focusin', layout);
    document.addEventListener('focusout', layout);

    // Tapping Send must NOT steal focus from the textarea: the blur would close
    // the keyboard and bounce the panel down and back up ("send jiggle").
    panel.querySelectorAll('form button').forEach((sendButton) => {
        sendButton.addEventListener('pointerdown', (event) => event.preventDefault());
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
            // elements, which would silently disable the badge entirely.
            if (!button.offsetWidth) {
                return; // button hidden => desktop layout, chat is inline
            }
            if (mutations.some((mutation) => mutation.addedNodes.length)) {
                badge.hidden = false;
            }
        }).observe(messages, { childList: true });
    }
})();
