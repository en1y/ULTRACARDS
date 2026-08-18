(() => {
  window.ucHeader = window.ucHeader || {};

  let provider = null;
  let opener = null;
  let refocusElement = null;

  window.ucHeader.registerUserProfilePopup = (nextProvider) => {
    provider = nextProvider;
  };

  window.ucHeader.isUserProfilePopupOpen = () => !!provider?.isOpen?.();

  window.ucHeader.switchUserProfilePopupTab = (direction) => !!provider?.switchTab?.(direction);

  window.ucHeader.openUserProfilePopup = (user, options = {}) => {
    if (!user?.id || !provider?.open) {
      return;
    }

    opener = options.source || 'external';
    refocusElement = options.refocusElement || null;
    provider.open(user);
  };

  window.ucHeader.closeUserProfilePopup = () => {
    if (!provider?.isOpen?.()) {
      return false;
    }

    provider.close?.();
    if (opener === 'search' && refocusElement instanceof HTMLElement) {
      refocusElement.focus({ preventScroll: true });
    }
    opener = null;
    refocusElement = null;
    return true;
  };

  document.addEventListener('uc:open-user-profile', (event) => {
    const id = event.detail?.id;
    if (!id) {
      return;
    }

    window.ucHeader.openUserProfilePopup({
      id,
      username: event.detail?.username || 'User'
    }, {
      source: event.detail?.source || 'external',
      refocusElement: event.detail?.refocusElement || null
    });
  });

  document.addEventListener('dblclick', (event) => {
    const avatar = event.target instanceof Element
      ? event.target.closest('.player-seat .seat-avatar')
      : null;
    const seat = avatar?.closest('.player-seat');
    if (!seat?.dataset.playerId || seat.classList.contains('is-self') || seat.dataset.isSelf === '1') {
      return;
    }

    window.ucHeader.openUserProfilePopup({
      id: seat.dataset.playerId,
      username: seat.dataset.playerName || seat.querySelector('.seat-name')?.textContent?.trim() || 'User'
    }, {
      source: 'game',
      refocusElement: avatar
    });
  });
})();
