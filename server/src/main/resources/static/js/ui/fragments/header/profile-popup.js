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
})();
