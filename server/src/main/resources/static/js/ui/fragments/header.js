(() => {
  if (window.__ucHeaderInitialized) {
    return;
  }
  window.__ucHeaderInitialized = true;

  const createNoopPhase = window.ucHeader?.createNoopPhase || (() => ({ init: () => {} }));

  const createThemePhase = () => ({
    init: () => {
      window.syncThemeUi?.();
    }
  });

  const createPhase = (factory) => {
    if (typeof factory !== 'function') {
      return createNoopPhase();
    }

    return factory();
  };

  const initHeader = () => {
    const phases = [
      createThemePhase(),
      createPhase(window.ucHeader?.createHeaderUiPhase),
      createPhase(window.ucHeader?.createHeaderSearchPhase),
      createPhase(window.ucHeader?.createProfileMenuPhase),
      createPhase(window.ucHeader?.createNotificationsPhase),
      createPhase(window.ucHeader?.createFriendsPhase),
      createPhase(window.ucHeader?.createAuthModalPhase)
    ];

    phases.forEach((phase) => phase.init());
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initHeader, { once: true });
  } else {
    initHeader();
  }
})();
