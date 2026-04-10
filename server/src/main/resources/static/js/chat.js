(() => {
    function escapeHtml(value) {
        return String(value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }

    function formatTimestamp(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return new Intl.DateTimeFormat(undefined, {
            hour: '2-digit',
            minute: '2-digit'
        }).format(date);
    }

    function resolveOwnMessage(message, currentUserId, currentUsername) {
        const senderId = message?.sender?.id != null ? String(message.sender.id) : '';
        const senderName = message?.sender?.name != null ? String(message.sender.name) : '';
        if (currentUserId && senderId) {
            return currentUserId === senderId;
        }
        return !!currentUsername && currentUsername === senderName;
    }

    function messageKey(message) {
        return [
            message?.sender?.id != null ? String(message.sender.id) : '',
            message?.sender?.name != null ? String(message.sender.name) : '',
            message?.timestamp != null ? String(message.timestamp) : '',
            message?.message != null ? String(message.message) : ''
        ].join('|');
    }

    function create(config) {
        const state = {
            chat: config.initialChat || {messages: [], isOpen: true},
            currentUserId: config.currentUserId != null ? String(config.currentUserId) : '',
            currentUsername: config.currentUsername != null ? String(config.currentUsername) : '',
            sending: false,
            initialAutoScroll: true,
            animateMessageKey: null
        };

        const dom = {
            messages: document.getElementById(config.messagesId),
            status: document.getElementById(config.statusId),
            form: document.getElementById(config.formId),
            input: document.getElementById(config.inputId),
            send: document.getElementById(config.sendId)
        };

        if (!dom.messages || !dom.form || !dom.input || !dom.send) {
            return null;
        }

        function isNearBottom() {
            const remaining = dom.messages.scrollHeight - dom.messages.scrollTop - dom.messages.clientHeight;
            return remaining < 48;
        }

        function syncComposer() {
            const disabled = !state.chat?.isOpen || state.sending;
            dom.input.disabled = disabled;
            dom.input.placeholder = state.chat?.isOpen ? 'Send a message' : 'Chat is closed';
            dom.send.disabled = disabled;
        }

        function showMessageTooLongPopup() {
            dom.input.setCustomValidity('Chat messages can be at most 200 characters long.');
            dom.input.reportValidity();
        }

        function clearMessageValidity() {
            dom.input.setCustomValidity('');
        }

        function scrollToBottom() {
            const applyScroll = () => {
                dom.messages.scrollTo({
                    top: dom.messages.scrollHeight,
                    left: 0,
                    behavior: 'smooth'
                });
            };

            applyScroll();
            window.requestAnimationFrame(() => {
                applyScroll();
                window.requestAnimationFrame(applyScroll);
            });
            window.setTimeout(applyScroll, 0);
            window.setTimeout(applyScroll, 120);
        }

        function scheduleInitialAutoScroll() {
            if (!state.initialAutoScroll) {
                return;
            }
            scrollToBottomInstant();
            window.setTimeout(() => {
                if (state.initialAutoScroll) {
                    scrollToBottomInstant();
                }
            }, 260);
        }

        function scrollToBottomInstant() {
            const applyScroll = () => {
                dom.messages.scrollTo({
                    top: dom.messages.scrollHeight,
                    left: 0,
                    behavior: 'auto'
                });
            };

            applyScroll();
            window.requestAnimationFrame(() => {
                applyScroll();
                window.requestAnimationFrame(applyScroll);
            });
            window.setTimeout(applyScroll, 0);
            window.setTimeout(applyScroll, 120);
        }

        function render(shouldStickToBottom = false) {
            const messages = Array.isArray(state.chat?.messages) ? state.chat.messages : [];
            const wasNearBottom = shouldStickToBottom || isNearBottom();
            if (messages.length) {
                dom.messages.innerHTML = messages.map((message) => {
                    const senderName = message?.sender?.name || 'Player';
                    const ownClass = resolveOwnMessage(message, state.currentUserId, state.currentUsername) ? ' is-own' : '';
                    const animationClass = state.animateMessageKey === messageKey(message) ? ' is-new' : '';
                    const timestamp = message?.timestamp ? String(message.timestamp) : '';
                    const timestampLabel = timestamp ? formatTimestamp(timestamp) : '';
                    const metaClass = config.metaClass;
                    const bubbleClass = config.bubbleClass;
                    const timeClass = config.timeClass;

                    return `
                        <div class="${config.messageClass}${ownClass}${animationClass}">
                            <div class="${metaClass}">
                                <span>${escapeHtml(senderName)}</span>
                                ${timestamp ? `<span class="${timeClass}" data-timestamp="${escapeHtml(timestamp)}">${escapeHtml(timestampLabel)}</span>` : ''}
                            </div>
                            <div class="${bubbleClass}">${escapeHtml(message?.message || '')}</div>
                        </div>
                    `;
                }).join('');
            } else {
                dom.messages.innerHTML = `<div class="${config.emptyClass}">${escapeHtml(config.emptyText || 'Send a message! I dare you!')}</div>`;
            }

            if (dom.status) {
                dom.status.textContent = state.chat?.isOpen ? 'Live' : 'Closed';
            }
            syncComposer();
            if (wasNearBottom) {
                if (state.initialAutoScroll) {
                    scrollToBottomInstant();
                } else {
                    scrollToBottom();
                }
            }
            state.animateMessageKey = null;
        }

        async function sendMessage() {
            if (state.sending || !state.chat?.isOpen) {
                return;
            }
            const message = dom.input.value.trim();
            if (!message) {
                return;
            }
            if (message.length > 200) {
                showMessageTooLongPopup();
                return;
            }

            let shouldRefocus = false;
            state.sending = true;
            syncComposer();
            try {
                const response = await fetch('/api/chat', {
                    method: 'POST',
                    credentials: 'include',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({message})
                });
                if (!response.ok) {
                    throw new Error(`chat failed: ${response.status}`);
                }
                dom.input.value = '';
                shouldRefocus = true;
            } finally {
                state.sending = false;
                syncComposer();
                if (shouldRefocus) {
                    dom.input.focus();
                }
            }
        }

        dom.form.addEventListener('submit', async (event) => {
            event.preventDefault();
            await sendMessage();
        });

        dom.input.addEventListener('input', () => {
            clearMessageValidity();
        });

        dom.input.addEventListener('paste', (event) => {
            const pastedText = event.clipboardData?.getData('text') || '';
            const selectionStart = dom.input.selectionStart ?? dom.input.value.length;
            const selectionEnd = dom.input.selectionEnd ?? selectionStart;
            const nextLength = dom.input.value.length - (selectionEnd - selectionStart) + pastedText.length;
            if (nextLength > 200) {
                event.preventDefault();
                showMessageTooLongPopup();
            }
        });

        dom.input.addEventListener('keydown', async (event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                await sendMessage();
            }
        });

        render(true);

        window.addEventListener('load', scheduleInitialAutoScroll, {once: true});

        const resizeObserver = new ResizeObserver(() => {
            scheduleInitialAutoScroll();
        });
        resizeObserver.observe(dom.messages);

        const mutationObserver = new MutationObserver(() => {
            scheduleInitialAutoScroll();
        });
        mutationObserver.observe(dom.messages, {
            childList: true,
            subtree: true,
            characterData: true
        });

        window.setTimeout(() => {
            state.initialAutoScroll = false;
            resizeObserver.disconnect();
            mutationObserver.disconnect();
        }, 1000);

        return {
            render,
            setIdentity(identity) {
                state.currentUserId = identity?.id != null ? String(identity.id) : state.currentUserId;
                state.currentUsername = identity?.username != null ? String(identity.username) : state.currentUsername;
                render();
            },
            addMessage(message) {
                state.chat.messages = [...(state.chat.messages || []), message];
                state.animateMessageKey = messageKey(message);
                render(true);
            },
            setChat(chat) {
                state.chat = chat || {messages: [], isOpen: true};
                render(true);
            }
        };
    }

    window.UltracardsChat = {create};
})();
