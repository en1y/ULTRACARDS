(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createProfileMenuPhase = () => {
    const menu = document.querySelector('[data-profile-menu]');
    if (!menu) {
      return { init: () => {} };
    }

    const trigger = menu.querySelector('[data-profile-menu-trigger]');
    const panel = menu.querySelector('.profile-dropdown');
    const logoutButton = menu.querySelector('[data-action="logout"]');

    const openMenu = () => {
      document.dispatchEvent(new CustomEvent('uc:profile-menu-open'));
      menu.classList.add('open');
      trigger?.setAttribute('aria-expanded', 'true');
      panel?.setAttribute('aria-hidden', 'false');
    };

    const closeMenu = () => {
      menu.classList.remove('open');
      trigger?.setAttribute('aria-expanded', 'false');
      panel?.setAttribute('aria-hidden', 'true');
    };

    return {
      init() {
        trigger?.addEventListener('click', (event) => {
          event.stopPropagation();
          if (menu.classList.contains('open')) {
            closeMenu();
          } else {
            openMenu();
          }
        });

        document.addEventListener('click', (event) => {
          if (!menu.contains(event.target)) {
            closeMenu();
          }
        });

        document.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            closeMenu();
          }
        });

        document.addEventListener('uc:notifications-open', closeMenu);
        document.addEventListener('uc:language-open', closeMenu);

        logoutButton?.addEventListener('click', async () => {
          closeMenu();
          if (!window.confirm(t('header.logout.confirm'))) {
            return;
          }

          try {
            await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
          } catch (error) {
          }

          window.location.href = '/';
        });
      }
    };
  };
})();
