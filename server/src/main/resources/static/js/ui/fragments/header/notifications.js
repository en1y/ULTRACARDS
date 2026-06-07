(() => {
  window.ucHeader = window.ucHeader || {};

  let sharedClient = null;
  let sharedClientPromise = null;

  window.ucHeader.getSharedStompClient = () => {
    if (!window.Stomp) {
      return Promise.reject(new Error('STOMP is unavailable'));
    }

    if (sharedClient?.connected) {
      return Promise.resolve(sharedClient);
    }

    if (sharedClientPromise) {
      return sharedClientPromise;
    }

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/ws`;
    const client = Stomp.client(wsUrl);
    client.reconnect_delay = 3000;
    client.debug = null;

    sharedClientPromise = new Promise((resolve, reject) => {
      client.connect({}, () => {
        sharedClient = client;
        sharedClientPromise = null;
        resolve(client);
      }, (error) => {
        sharedClientPromise = null;
        reject(error);
      });
    });

    return sharedClientPromise;
  };

  window.ucHeader.createNotificationsPhase = () => {
    const root = document.querySelector('[data-notifications]');
    const toastStack = document.querySelector('[data-notification-toasts]');
    if (!root) {
      return { init: () => {} };
    }

    const trigger = root.querySelector('[data-notification-trigger]');
    const closeButton = root.querySelector('[data-notification-close]');
    const toggleButton = root.querySelector('[data-notification-toggle]');
    const tray = root.querySelector('[data-notification-tray]');
    const list = root.querySelector('[data-notification-list]');
    const badge = root.querySelector('[data-notification-badge]');
    const notificationsById = new Map();
    const toastEntriesByKey = new Map();
    const popupStorageKey = 'uc-notification-popups-enabled';
    let receivedSequence = 0;
    let reconnectTimer = null;
    let popupsEnabled = localStorage.getItem(popupStorageKey) !== 'false';

    const notificationId = (notification) => notification?.id ? String(notification.id) : '';
    const notificationType = (notification) => String(notification?.type || 'TEXT');
    const senderName = (notification) => notification?.sender?.name || 'ULTRACARDS';
    const senderId = (notification) => notification?.sender?.id != null ? String(notification.sender.id) : '';
    const isGroupedNotification = (notification) => Array.isArray(notification?.groupedNotifications);
    const groupItems = (notification) => isGroupedNotification(notification) ? notification.groupedNotifications : [notification];
    const displayNotificationId = (notification) => isGroupedNotification(notification)
        ? notification.groupId
        : notificationId(notification);
    const isMessageNotification = (notification) => notificationType(notification) === 'TEXT' && !!senderId(notification);
    const notificationTime = (notification) => {
      const time = Date.parse(notification?.createdAt || '');
      if (Number.isFinite(time)) {
        return time;
      }
      return Number(notification?.receivedAt || 0);
    };
    const notificationOrder = (notification) => Number(notification?.receivedSequence || 0);
    const compareNewestFirst = (left, right) => {
      const timeDifference = notificationTime(right) - notificationTime(left);
      if (timeDifference !== 0) {
        return timeDifference;
      }
      return notificationOrder(right) - notificationOrder(left);
    };
    const sortedGroupItems = (notification) => groupItems(notification).slice().sort(compareNewestFirst);
    const createToastMessageGroup = () => {
      const messages = Array.from(notificationsById.values())
          .filter(isMessageNotification)
          .sort(compareNewestFirst);
      if (!messages.length) {
        return null;
      }
      return {
        ...messages[0],
        groupId: 'message-toast-group',
        groupedNotifications: messages
      };
    };
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

    const setIcon = (button, icon) => {
      const image = button?.querySelector('[data-icon]');
      if (!image) {
        return;
      }
      image.dataset.icon = icon;
      window.syncThemeUi?.();
    };

    const syncPopupToggle = () => {
      if (!toggleButton) {
        return;
      }

      toggleButton.setAttribute('aria-pressed', String(popupsEnabled));
      toggleButton.setAttribute('aria-label', popupsEnabled ? 'Turn off notification popups' : 'Turn on notification popups');
      toggleButton.title = popupsEnabled ? 'Turn off notification popups' : 'Turn on notification popups';
      setIcon(toggleButton, popupsEnabled ? 'notifications_on' : 'notifications_off');
    };

    const setTrayOpen = (isOpen) => {
      if (isOpen) {
        document.dispatchEvent(new CustomEvent('uc:notifications-open'));
      }
      root.classList.toggle('open', isOpen);
      trigger?.setAttribute('aria-expanded', String(isOpen));
      tray?.setAttribute('aria-hidden', String(!isOpen));
    };

    const notificationTitle = (notification) => {
      const type = notificationType(notification);
      if (type === 'GAME_INVITE') {
        return 'Lobby invite';
      }
      if (type === 'FRIEND_INVITE') {
        return 'Friend request';
      }
      if (isGroupedNotification(notification)) {
        return 'Messages';
      }
      return 'Message';
    };

    const notificationClass = (notification) => {
      const type = notificationType(notification);
      if (type === 'GAME_INVITE') {
        return 'notification-kind-game';
      }
      if (type === 'FRIEND_INVITE') {
        return 'notification-kind-friend';
      }
      return 'notification-kind-text';
    };

    const notificationMessage = (notification) => {
      if (isGroupedNotification(notification)) {
        const sortedItems = sortedGroupItems(notification);
        const count = sortedItems.length;
        const latest = sortedItems[0]?.message || 'New message';
        return `${count} new messages. Latest: ${latest}`;
      }

      if (notification?.message) {
        return notification.message;
      }

      const type = notificationType(notification);
      if (type === 'GAME_INVITE') {
        return `${senderName(notification)} invited you to a lobby.`;
      }
      if (type === 'FRIEND_INVITE') {
        return `${senderName(notification)} sent you a friend request.`;
      }
      return 'You have a new notification.';
    };

    const setBadgeCount = () => {
      const count = getRenderableNotifications().length;
      if (!badge) {
        return;
      }
      badge.hidden = count === 0;
      badge.textContent = count > 9 ? '9+' : String(count);
      trigger?.querySelector('[data-icon]')?.setAttribute('data-icon', count === 0 ? 'notifications' : 'notifications_unread');
      window.syncThemeUi?.();
    };

    const setButtonLabel = (button, label) => {
      const text = button?.querySelector('span');
      if (text) {
        text.textContent = label;
      } else if (button) {
        button.textContent = label;
      }
    };

    const setActionError = (button, message) => {
      if (!button) {
        return;
      }

      const originalText = button.dataset.originalText || button.querySelector('span')?.textContent || button.textContent;
      button.dataset.originalText = originalText;
      setButtonLabel(button, message);
      window.setTimeout(() => {
        setButtonLabel(button, button.dataset.originalText || originalText);
      }, 1800);
    };

    const markRead = async (notification) => {
      for (const item of groupItems(notification)) {
        const id = notificationId(item);
        if (!id) {
          continue;
        }

        const response = await fetch(`/api/notifications/${encodeURIComponent(id)}/read`, {
          method: 'PATCH',
          credentials: 'include'
        });

        if (!response.ok) {
          throw new Error(`Unable to mark notification read: ${response.status}`);
        }
      }
    };

    const markReadIfPossible = async (notification) => {
      try {
        await markRead(notification);
      } catch (error) {
        console.error('Unable to mark notification read', error);
      }
    };

    const removeNotification = (notification) => {
      const domId = displayNotificationId(notification);
      for (const item of groupItems(notification)) {
        const id = notificationId(item);
        if (id) {
          notificationsById.delete(id);
        }
      }

      renderNotifications();
      if (isMessageNotification(notification) || isGroupedNotification(notification)) {
        refreshMessageToast();
      }
      document.querySelectorAll(`[data-notification-id="${CSS.escape(domId)}"]`).forEach((element) => {
        element.classList.add('is-removing');
        window.setTimeout(() => {
          element.remove();
        }, 180);
      });
    };

    const postEmpty = async (url) => {
      const response = await fetch(url, {
        method: 'POST',
        credentials: 'include'
      });

      if (!response.ok) {
        const text = (await response.text()).trim();
        throw new Error(text || `Request failed: ${response.status}`);
      }

      return response;
    };

    const joinLobby = async (notification) => {
      if (!notification?.lobbyId) {
        throw new Error('Missing lobby id');
      }

      const response = await fetch('/api/lobby/join', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify({ lobbyId: notification.lobbyId })
      });

      if (!response.ok) {
        const text = (await response.text()).trim();
        throw new Error(text || 'Unable to join this lobby.');
      }
    };

    const openNotificationChat = async (notification) => {
      const id = senderId(notification);
      if (!id) {
        throw new Error('Missing sender id');
      }

      setTrayOpen(false);
      if (typeof window.ucHeader?.openFriendChatByUserId === 'function') {
        await window.ucHeader.openFriendChatByUserId(id);
      } else {
        document.dispatchEvent(new CustomEvent('uc:open-friend-chat', {
          detail: { userId: id }
        }));
      }
      await markReadIfPossible(notification);
      removeNotification(notification);
    };

    const handleNotificationAction = async (notification, action, button) => {
      if (!notification) {
        return;
      }

      if (button) {
        button.disabled = true;
      }

      try {
        if (action === 'join-lobby') {
          await joinLobby(notification);
          await markReadIfPossible(notification);
          removeNotification(notification);
          window.location.href = '/lobbies';
          return;
        }

        if (action === 'open-chat') {
          await openNotificationChat(notification);
          return;
        }

        if (action === 'accept-friend' || action === 'reject-friend' || action === 'block-friend') {
          if (!notification.friendRequestId) {
            throw new Error('Missing friend request id');
          }

          const requestAction = action === 'accept-friend'
              ? 'accept'
              : action === 'reject-friend' ? 'decline' : 'block';
          await postEmpty(`/api/friends/requests/${encodeURIComponent(notification.friendRequestId)}/${requestAction}`);
          await markReadIfPossible(notification);
          removeNotification(notification);
          document.dispatchEvent(new CustomEvent('uc:friends-refresh'));
          return;
        }

        await markRead(notification);
        removeNotification(notification);
      } catch (error) {
        console.error('Notification action failed', error);
        setActionError(button, 'Failed');
      } finally {
        if (button && button.isConnected) {
          button.disabled = false;
        }
      }
    };

    const createButton = (label, action, notification, className = 'btn') => {
      const button = document.createElement('button');
      button.className = className;
      button.type = 'button';
      const iconByAction = {
        'join-lobby': 'login',
        'open-chat': 'chat',
        'reject-friend': 'close',
        'block-friend': 'block',
        'mark-read': 'notifications'
      };
      if (iconByAction[action]) {
        button.append(createIcon(iconByAction[action]), document.createElement('span'));
        setButtonLabel(button, label);
      } else {
        button.textContent = label;
      }
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        handleNotificationAction(notification, action, button);
      });
      return button;
    };

    const createActions = (notification) => {
      const actions = document.createElement('div');
      actions.className = 'notification-actions';

      const type = notificationType(notification);
      if (type === 'GAME_INVITE') {
        actions.append(createButton('Join lobby', 'join-lobby', notification, 'btn btn-accent'));
        return actions;
      }

      if (type === 'FRIEND_INVITE') {
        actions.append(
            createButton('Accept', 'accept-friend', notification, 'btn btn-accent'),
            createButton('Reject', 'reject-friend', notification),
            createButton('Block', 'block-friend', notification, 'btn danger')
        );
        return actions;
      }

      if (isMessageNotification(notification) || isGroupedNotification(notification)) {
        actions.append(
            createButton('Open chat', 'open-chat', notification, 'btn btn-accent'),
            createButton('Mark read', 'mark-read', notification)
        );
        return actions;
      }

      actions.append(createButton('Mark read', 'mark-read', notification));
      return actions;
    };

    const createNotificationCard = (notification, isToast) => {
      const id = displayNotificationId(notification);
      const card = document.createElement('article');
      card.className = `${isToast ? 'notification-toast' : 'notification-row'} ${notificationClass(notification)}`;
      card.dataset.notificationId = id;

      const body = document.createElement('div');
      body.className = 'notification-body';

      const meta = document.createElement('div');
      meta.className = 'notification-meta';
      meta.textContent = notificationTitle(notification);

      const message = document.createElement('p');
      message.className = 'notification-message';
      message.textContent = notificationMessage(notification);

      const sender = document.createElement('p');
      sender.className = 'notification-sender';
      sender.textContent = senderName(notification);

      body.append(meta, message, sender);

      const dismiss = document.createElement('button');
      dismiss.className = 'header-icon-button header-icon-button-small notification-dismiss';
      dismiss.type = 'button';
      dismiss.setAttribute('aria-label', 'Dismiss notification');
      dismiss.append(createIcon('close'));
      dismiss.addEventListener('click', (event) => {
        event.stopPropagation();
        handleNotificationAction(notification, 'mark-read', dismiss);
      });

      card.append(body, dismiss, createActions(notification));
      window.syncThemeUi?.();
      return card;
    };

    const updateNotificationCardContent = (card, notification) => {
      card.className = `${card.classList.contains('notification-toast') ? 'notification-toast' : 'notification-row'} ${notificationClass(notification)}`;
      card.dataset.notificationId = displayNotificationId(notification);

      const meta = card.querySelector('.notification-meta');
      const message = card.querySelector('.notification-message');
      const sender = card.querySelector('.notification-sender');
      if (meta) {
        meta.textContent = notificationTitle(notification);
      }
      if (message) {
        message.textContent = notificationMessage(notification);
      }
      if (sender) {
        sender.textContent = senderName(notification);
      }
    };

    const createMessageGroup = (items) => {
      const sortedItems = items.slice().sort(compareNewestFirst);
      return {
        ...sortedItems[0],
        groupId: `message-group-${senderId(sortedItems[0])}`,
        groupedNotifications: sortedItems
      };
    };

    const getRenderableNotifications = () => {
      const rendered = [];
      const messageGroups = new Map();

      for (const notification of notificationsById.values()) {
        if (!isMessageNotification(notification)) {
          rendered.push(notification);
          continue;
        }

        const key = senderId(notification);
        if (!messageGroups.has(key)) {
          messageGroups.set(key, []);
        }
        messageGroups.get(key).push(notification);
      }

      for (const group of messageGroups.values()) {
        const sortedGroup = group.slice().sort(compareNewestFirst);
        rendered.push(sortedGroup.length === 1 ? sortedGroup[0] : createMessageGroup(sortedGroup));
      }

      return rendered.sort(compareNewestFirst);
    };

    function renderNotifications() {
      const renderedNotifications = getRenderableNotifications();
      setBadgeCount();

      if (!list) {
        return;
      }

      list.replaceChildren();
      if (renderedNotifications.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'notification-empty';
        empty.textContent = 'No unread notifications.';
        list.append(empty);
        return;
      }

      for (const notification of renderedNotifications) {
        list.append(createNotificationCard(notification, false));
      }
    }

    const showToast = (notification) => {
      if (!toastStack) {
        return;
      }

      const toastKey = isMessageNotification(notification) || isGroupedNotification(notification)
          ? 'messages'
          : `notification-${displayNotificationId(notification)}`;
      const toast = createNotificationCard(notification, true);
      const existing = toastEntriesByKey.get(toastKey);

      if (existing?.element?.isConnected) {
        window.clearTimeout(existing.timer);
        updateNotificationCardContent(existing.element, notification);
        existing.element.classList.add('is-visible');
        const timer = scheduleToastRemoval(toastKey, existing.element);
        toastEntriesByKey.set(toastKey, { element: existing.element, timer });
        return;
      }

      toastStack.prepend(toast);
      const timer = scheduleToastRemoval(toastKey, toast);
      toastEntriesByKey.set(toastKey, { element: toast, timer });
      window.requestAnimationFrame(() => {
        toast.classList.add('is-visible');
      });
    };

    const scheduleToastRemoval = (toastKey, toast) => window.setTimeout(() => {
      toast.classList.remove('is-visible');
      window.setTimeout(() => {
        toast.remove();
        if (toastEntriesByKey.get(toastKey)?.element === toast) {
          toastEntriesByKey.delete(toastKey);
        }
      }, 220);
    }, 7000);

    function refreshMessageToast() {
      const entry = toastEntriesByKey.get('messages');
      if (!entry?.element?.isConnected) {
        return;
      }

      const grouped = createToastMessageGroup();
      if (grouped) {
        showToast(grouped);
        return;
      }

      window.clearTimeout(entry.timer);
      entry.element.classList.remove('is-visible');
      window.setTimeout(() => {
        entry.element.remove();
        toastEntriesByKey.delete('messages');
      }, 220);
    }

    const setPopupsEnabled = (enabled) => {
      popupsEnabled = enabled;
      localStorage.setItem(popupStorageKey, String(enabled));
      syncPopupToggle();
      if (!enabled) {
        for (const [key, entry] of toastEntriesByKey.entries()) {
          window.clearTimeout(entry.timer);
          entry.element.classList.remove('is-visible');
          window.setTimeout(() => {
            entry.element.remove();
            toastEntriesByKey.delete(key);
          }, 220);
        }
      }
    };

    const addNotification = (notification, showPopup = false) => {
      const id = notificationId(notification);
      if (!id || notification?.read) {
        return;
      }

      notificationsById.set(id, {
        ...notification,
        receivedAt: Date.now(),
        receivedSequence: receivedSequence += 1
      });
      renderNotifications();
      if (showPopup && popupsEnabled && !root.classList.contains('open')) {
        if (isMessageNotification(notification)) {
          const grouped = createToastMessageGroup();
          showToast(grouped || notification);
        } else {
          showToast(notification);
        }
      }
    };

    const loadUnreadNotifications = async () => {
      try {
        const response = await fetch('/api/notifications/unread', {
          method: 'GET',
          credentials: 'include',
          cache: 'no-store'
        });

        if (!response.ok) {
          throw new Error(`Notifications failed: ${response.status}`);
        }

        const notifications = await response.json();
        notificationsById.clear();
        if (Array.isArray(notifications)) {
          for (const notification of notifications) {
            addNotification(notification);
          }
        }
        renderNotifications();
      } catch (error) {
        console.error('Unable to load notifications', error);
      }
    };

    const connectNotifications = () => {
      window.clearTimeout(reconnectTimer);
      window.ucHeader.getSharedStompClient()
          .then((client) => {
            client.subscribe('/user/queue/notifications', (message) => {
              try {
                addNotification(JSON.parse(message.body), true);
              } catch (error) {
                console.error('Notification websocket parse error', error);
              }
            });
          })
          .catch((error) => {
            console.error('Notification websocket connection failed', error);
            reconnectTimer = window.setTimeout(connectNotifications, 3000);
          });
    };

    return {
      init() {
        syncPopupToggle();
        renderNotifications();
        loadUnreadNotifications();
        connectNotifications();

        trigger?.addEventListener('click', (event) => {
          event.stopPropagation();
          setTrayOpen(!root.classList.contains('open'));
        });

        closeButton?.addEventListener('click', (event) => {
          event.stopPropagation();
          setTrayOpen(false);
        });

        toggleButton?.addEventListener('click', (event) => {
          event.stopPropagation();
          setPopupsEnabled(!popupsEnabled);
        });

        document.addEventListener('click', (event) => {
          if (!root.contains(event.target)) {
            setTrayOpen(false);
          }
        });

        document.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            setTrayOpen(false);
          }
        });

        document.addEventListener('uc:notifications-refresh', loadUnreadNotifications);
        document.addEventListener('uc:profile-menu-open', () => setTrayOpen(false));
      }
    };
  };
})();
