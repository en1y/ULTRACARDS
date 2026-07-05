(() => {
  window.ucHeader = window.ucHeader || {};
  const FRIENDS_REFRESH_INTERVAL_MS = 20000;

  window.ucHeader.createFriendsPhase = () => {
    const root = document.querySelector('[data-friends]');
    if (!root) {
      return { init: () => {} };
    }

    const trigger = root.querySelector('[data-friends-trigger]');
    const drawer = root.querySelector('[data-friends-drawer]');
    const closeButton = root.querySelector('[data-friends-close]');
    const groups = root.querySelector('[data-friends-groups]');
    const summary = root.querySelector('[data-friends-summary]');
    const status = root.querySelector('[data-friends-status]');
    const chatShell = root.querySelector('[data-friend-chat]');
    const chatTitle = root.querySelector('[data-friend-chat-title]');
    const chatClose = root.querySelector('[data-friend-chat-close]');
    const currentUsername = document.getElementById('username-header')?.textContent?.trim() || '';
    const createIcon = (icon) => {
      if (window.createUcIcon) {
        return window.createUcIcon(icon);
      }

      const img = document.createElement('img');
      img.className = 'uc-icon';
      img.dataset.icon = icon;
      img.src = `/pics/light/${icon}.svg`;
      img.alt = '';
      img.setAttribute('aria-hidden', 'true');
      return img;
    };

    const state = {
      friends: [],
      currentLobby: window.__INITIAL_LOBBY__ ?? null,
      loading: false,
      lobbyLoading: false,
      loaded: false,
      activeFriend: null,
      activeChatId: '',
      chat: null,
      chatSubscription: null,
      loadFriendsPromise: null,
      refreshTimer: null,
      invitingFriendIds: new Set()
    };

    const stopRefreshTimer = () => {
      if (!state.refreshTimer) {
        return;
      }

      window.clearInterval(state.refreshTimer);
      state.refreshTimer = null;
    };

    const startRefreshTimer = () => {
      stopRefreshTimer();
      state.refreshTimer = window.setInterval(() => {
        if (root.classList.contains('open')) {
          loadCurrentLobby({ silent: true });
          loadFriends({ silent: true });
        }
      }, FRIENDS_REFRESH_INTERVAL_MS);
    };

    const setDrawerOpen = (isOpen) => {
      root.classList.toggle('open', isOpen);
      document.body.classList.toggle('friends-drawer-open', isOpen);
      trigger?.setAttribute('aria-expanded', String(isOpen));
      trigger?.querySelector('[data-icon]')?.setAttribute('data-icon', isOpen ? 'opened_sandwich_menu' : 'sandwich_menu');
      window.syncThemeUi?.();
      drawer?.setAttribute('aria-hidden', String(!isOpen));
      if (isOpen) {
        loadCurrentLobby();
        loadFriends();
        startRefreshTimer();
      } else {
        stopRefreshTimer();
      }
    };

    const setStatus = (message, type = '') => {
      if (!status) {
        return;
      }
      status.textContent = message;
      status.hidden = !message;
      status.classList.toggle('error', type === 'error');
    };

    const setSummary = (message) => {
      if (!summary) {
        return;
      }
      summary.textContent = message;
    };

    const setButtonLabel = (button, label) => {
      const text = button?.querySelector('span');
      if (text) {
        text.textContent = label;
      } else if (button) {
        button.textContent = label;
      }
    };

    const friendUserId = (friend) => friend?.user?.id != null ? String(friend.user.id) : '';
    const friendName = (friend) => friend?.user?.name || 'Friend';
    const friendPresence = (friend) => String(friend?.presenceStatus || 'OFFLINE');
    const currentLobbyPlayers = () => Array.isArray(state.currentLobby?.players) ? state.currentLobby.players : [];
    const canInviteFriendToLobby = (friend) => {
      const id = friendUserId(friend);
      if (!state.currentLobby?.id || !id || friendPresence(friend) !== 'ONLINE') {
        return false;
      }

      return !currentLobbyPlayers().some((player) => String(player?.id) === id);
    };

    const presenceLabel = (presence) => {
      if (presence === 'IN_GAME') {
        return 'In game';
      }
      if (presence === 'IN_LOBBY') {
        return 'In lobby';
      }
      if (presence === 'ONLINE') {
        return 'Online';
      }
      return 'Offline';
    };

    const friendCountLabel = (count) => `${count} ${count === 1 ? 'friend' : 'friends'}`;

    const groupConfig = [
      {
        key: 'active',
        title: 'In game or lobby',
        accepts: (friend) => friendPresence(friend) === 'IN_GAME' || friendPresence(friend) === 'IN_LOBBY'
      },
      {
        key: 'online',
        title: 'Online',
        accepts: (friend) => friendPresence(friend) === 'ONLINE'
      },
      {
        key: 'offline',
        title: 'Offline',
        accepts: (friend) => friendPresence(friend) === 'OFFLINE'
      }
    ];

    const ensureChat = () => {
      if (state.chat) {
        return state.chat;
      }

      state.chat = window.UltracardsChat?.create({
        initialChat: { messages: [], isOpen: true },
        currentUsername,
        messagesId: 'friend-chat-messages',
        formId: 'friend-chat-form',
        inputId: 'friend-chat-input',
        sendId: 'friend-chat-send',
        messageClass: 'chat-message',
        metaClass: 'chat-meta',
        bubbleClass: 'chat-bubble',
        timeClass: 'chat-time',
        emptyClass: 'chat-empty',
        emptyText: 'No messages yet.',
        sendUrl: () => {
          const id = friendUserId(state.activeFriend);
          return id ? `/api/chat/friends/${encodeURIComponent(id)}` : '/api/chat';
        }
      });

      return state.chat;
    };

    const unsubscribeFriendChat = () => {
      if (!state.chatSubscription) {
        return;
      }

      try {
        state.chatSubscription.unsubscribe();
      } catch (error) {
        console.error('Unable to unsubscribe friend chat', error);
      }
      state.chatSubscription = null;
    };

    const subscribeFriendChat = (friend) => {
      unsubscribeFriendChat();
      const chatId = friend?.chatId ? String(friend.chatId) : '';
      if (!chatId || !window.ucHeader.getSharedStompClient) {
        return;
      }

      state.activeChatId = chatId;
      window.ucHeader.getSharedStompClient()
          .then((client) => {
            if (state.activeChatId !== chatId) {
              return;
            }

            state.chatSubscription = client.subscribe(`/topic/friends/chats/${chatId}`, (message) => {
              if (state.activeChatId !== chatId) {
                return;
              }

              try {
                const payload = JSON.parse(message.body);
                if (!payload?.message) {
                  return;
                }

                if (payload?.sender?.name && payload.sender.name === currentUsername) {
                  return;
                }

                state.chat?.addMessage(payload);
                markActiveChatRead();
              } catch (error) {
                console.error('Friend chat websocket parse error', error);
              }
            });
          })
          .catch((error) => {
            console.error('Friend chat websocket connection failed', error);
          });
    };

    const markActiveChatRead = () => {
      const id = friendUserId(state.activeFriend);
      if (!id) {
        return;
      }

      fetch(`/api/chat/friends/${encodeURIComponent(id)}/read-all`, {
        method: 'POST',
        credentials: 'include'
      })
          .then((response) => {
            if (response.ok) {
              document.dispatchEvent(new CustomEvent('uc:notifications-refresh'));
            }
          })
          .catch((error) => {
            console.error('Unable to mark friend chat read', error);
          });
    };

    const openFriendChat = async (friend) => {
      const id = friendUserId(friend);
      if (!id) {
        return;
      }

      state.activeFriend = friend;
      if (chatTitle) {
        chatTitle.textContent = friendName(friend);
      }
      if (chatShell) {
        chatShell.hidden = false;
        // The chat lives at the bottom of the scrollable drawer — bring its
        // header (with the close button) into view when it opens.
        requestAnimationFrame(() => chatShell.scrollIntoView({ block: 'start', behavior: 'smooth' }));
      }

      renderGroups();
      const chat = ensureChat();
      chat?.setChat({ messages: [], isOpen: true });
      setStatus(`Loading chat with ${friendName(friend)}...`);
      subscribeFriendChat(friend);

      try {
        const response = await fetch(`/api/chat/friends/${encodeURIComponent(id)}`, {
          method: 'GET',
          credentials: 'include',
          cache: 'no-store'
        });

        if (!response.ok) {
          throw new Error(`Friend chat failed: ${response.status}`);
        }

        const chatDto = await response.json();
        if (state.activeFriend && friendUserId(state.activeFriend) === id) {
          chat?.setChat(chatDto);
          setStatus('');
          markActiveChatRead();
        }
      } catch (error) {
        console.error('Unable to load friend chat', error);
        setStatus('Unable to load that chat.', 'error');
      }
    };

    const openFriendChatByUserId = async (userId) => {
      const id = userId != null ? String(userId) : '';
      if (!id) {
        return;
      }

      setDrawerOpen(true);
      if (!state.loaded && !state.loading) {
        await loadFriends();
      }

      let friend = state.friends.find((candidate) => friendUserId(candidate) === id);
      if (!friend) {
        await loadFriends();
        friend = state.friends.find((candidate) => friendUserId(candidate) === id);
      }

      if (!friend) {
        setStatus('Unable to find that chat.', 'error');
        return;
      }

      await openFriendChat(friend);
    };

    window.ucHeader.openFriendChatByUserId = openFriendChatByUserId;

    const closeFriendChat = () => {
      state.activeFriend = null;
      state.activeChatId = '';
      unsubscribeFriendChat();
      if (chatShell) {
        chatShell.hidden = true;
      }
      state.chat?.setChat({ messages: [], isOpen: true });
      renderGroups();
    };

    const inviteFriendToLobby = async (friend, button) => {
      const id = friendUserId(friend);
      if (!id || state.invitingFriendIds.has(id)) {
        return;
      }

      state.invitingFriendIds.add(id);
      if (button) {
        button.disabled = true;
        setButtonLabel(button, 'Inviting');
      }

      try {
        const response = await fetch(`/api/lobby/invite/${encodeURIComponent(id)}`, {
          method: 'POST',
          credentials: 'include'
        });

        if (!response.ok) {
          const message = (await response.text()).trim();
          throw new Error(message || `Invite failed: ${response.status}`);
        }

        setStatus(`Invited ${friendName(friend)} to the lobby.`);
      } catch (error) {
        console.error('Unable to invite friend to lobby', error);
        setStatus(error?.message || 'Unable to send lobby invite.', 'error');
      } finally {
        state.invitingFriendIds.delete(id);
        if (button && button.isConnected) {
          button.disabled = false;
          setButtonLabel(button, 'Invite');
        }
      }
    };

    const createFriendRow = (friend) => {
      const row = document.createElement('article');
      row.className = 'friend-row';
      const id = friendUserId(friend);
      if (state.activeFriend && friendUserId(state.activeFriend) === id) {
        row.classList.add('is-active');
      }

      const main = document.createElement('div');
      main.className = 'friend-main';

      const avatar = document.createElement('span');
      avatar.className = 'friend-avatar';
      avatar.textContent = friendName(friend).charAt(0).toUpperCase() || 'U';

      const copy = document.createElement('div');
      copy.className = 'friend-copy';

      const name = document.createElement('strong');
      name.textContent = friendName(friend);

      const meta = document.createElement('span');
      meta.textContent = `${friend.totalPlayedTogether || 0} games together`;

      copy.append(name, meta);
      main.append(avatar, copy);

      const actions = document.createElement('div');
      actions.className = 'friend-actions';

      const presence = document.createElement('span');
      presence.className = `friend-presence friend-presence-${friendPresence(friend).toLowerCase().replace('_', '-')}`;
      presence.setAttribute('aria-label', presenceLabel(friendPresence(friend)));
      presence.title = presenceLabel(friendPresence(friend));

      if (canInviteFriendToLobby(friend)) {
        const inviteButton = document.createElement('button');
        inviteButton.className = 'btn btn-accent friend-invite-button';
        inviteButton.type = 'button';
        inviteButton.append(createIcon('mail'), document.createElement('span'));
        setButtonLabel(inviteButton, 'Invite');
        inviteButton.setAttribute('aria-label', `Invite ${friendName(friend)} to lobby`);
        inviteButton.disabled = state.invitingFriendIds.has(id);
        inviteButton.addEventListener('click', (event) => {
          event.stopPropagation();
          inviteFriendToLobby(friend, inviteButton);
        });
        actions.append(inviteButton);
      }

      const chatButton = document.createElement('button');
      chatButton.className = 'header-icon-button header-icon-button-small friend-chat-button';
      chatButton.type = 'button';
      chatButton.setAttribute('aria-label', `Open chat with ${friendName(friend)}`);
      chatButton.append(createIcon('chat'));
      chatButton.addEventListener('click', () => {
        openFriendChat(friend);
      });

      const profileButton = document.createElement('button');
      profileButton.className = 'header-icon-button header-icon-button-small friend-profile-button';
      profileButton.type = 'button';
      profileButton.setAttribute('aria-label', `View stats for ${friendName(friend)}`);
      profileButton.append(createIcon('profile_icon'));
      profileButton.addEventListener('click', (event) => {
        event.stopPropagation();
        document.dispatchEvent(new CustomEvent('uc:open-user-profile', {
          detail: {
            id,
            username: friendName(friend)
          }
        }));
      });

      actions.append(presence, profileButton, chatButton);
      row.append(main, actions);
      return row;
    };

    const createGroup = (config, friends) => {
      const section = document.createElement('section');
      section.className = 'friends-group';

      const head = document.createElement('div');
      head.className = 'friends-group-head';

      const title = document.createElement('h3');
      title.textContent = config.title;

      const count = document.createElement('span');
      count.className = 'chip';
      count.textContent = String(friends.length);

      head.append(title, count);
      section.append(head);

      if (friends.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'friends-empty';
        empty.textContent = 'Empty';
        section.append(empty);
        return section;
      }

      const list = document.createElement('div');
      list.className = 'friends-list';
      for (const friend of friends) {
        list.append(createFriendRow(friend));
      }
      section.append(list);
      return section;
    };

    function renderGroups() {
      if (!groups) {
        return;
      }

      groups.replaceChildren();
      const total = state.friends.length;
      const online = state.friends.filter((friend) => friendPresence(friend) === 'ONLINE').length;
      const active = state.friends.filter((friend) => friendPresence(friend) === 'IN_GAME' || friendPresence(friend) === 'IN_LOBBY').length;
      setSummary(total ? `${friendCountLabel(total)} / ${online + active} active` : 'No friends yet');

      for (const config of groupConfig) {
        groups.append(createGroup(config, state.friends.filter(config.accepts)));
      }
      window.syncThemeUi?.();
    }

    async function loadCurrentLobby(options = {}) {
      if (state.lobbyLoading) {
        return;
      }

      state.lobbyLoading = true;
      try {
        const response = await fetch('/api/lobby/get', {
          method: 'GET',
          credentials: 'include',
          cache: 'no-store'
        });

        if (response.status === 404) {
          state.currentLobby = null;
          renderGroups();
          return;
        }

        if (!response.ok) {
          throw new Error(`Lobby failed: ${response.status}`);
        }

        state.currentLobby = await response.json();
        renderGroups();
      } catch (error) {
        console.error('Unable to load current lobby', error);
        if (!options.silent) {
          setStatus('Unable to check lobby invites.', 'error');
        }
      } finally {
        state.lobbyLoading = false;
      }
    }

    async function loadFriends(options = {}) {
      if (state.loading) {
        return state.loadFriendsPromise;
      }

      const silent = Boolean(options.silent);
      state.loading = true;
      state.loadFriendsPromise = (async () => {
        if (!silent) {
          setSummary(state.loaded ? 'Refreshing' : 'Loading');
          setStatus('');
        }

        try {
          const response = await fetch('/api/friends', {
            method: 'GET',
            credentials: 'include',
            cache: 'no-store'
          });

          if (!response.ok) {
            throw new Error(`Friends failed: ${response.status}`);
          }

          const friends = await response.json();
          state.friends = Array.isArray(friends) ? friends : [];
          state.loaded = true;
          renderGroups();
          setStatus('');
        } catch (error) {
          console.error('Unable to load friends', error);
          if (!silent) {
            setStatus('Unable to load friends.', 'error');
          }
        } finally {
          state.loading = false;
          state.loadFriendsPromise = null;
        }
      })();
      return state.loadFriendsPromise;
    }

    return {
      init() {
        renderGroups();

        trigger?.addEventListener('click', (event) => {
          event.stopPropagation();
          setDrawerOpen(!root.classList.contains('open'));
        });

        closeButton?.addEventListener('click', (event) => {
          event.stopPropagation();
          setDrawerOpen(false);
        });

        chatClose?.addEventListener('click', closeFriendChat);

        document.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            if (state.activeFriend) {
              closeFriendChat();
              return;
            }
            setDrawerOpen(false);
          }
        });

        document.addEventListener('uc:friends-refresh', () => {
          if (root.classList.contains('open')) {
            loadFriends();
          }
        });

        document.addEventListener('uc:open-friend-chat', (event) => {
          openFriendChatByUserId(event.detail?.userId);
        });
      }
    };
  };
})();
