(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createLanguagePhase = () => {
    const menu = document.querySelector('[data-language-menu]');
    if (!menu) {
      return { init: () => {} };
    }

    const trigger = menu.querySelector('[data-language-trigger]');
    const panel = menu.querySelector('.language-dropdown');
    const currentLanguage = document.documentElement.lang || 'en';

    const openMenu = () => {
      document.dispatchEvent(new CustomEvent('uc:language-open'));
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
        menu.querySelectorAll('[data-language]').forEach((button) => {
          if (button.dataset.language === currentLanguage) {
            button.classList.add('active');
          }
          button.addEventListener('click', () => {
            document.cookie = `uc-lang=${button.dataset.language};path=/;max-age=31536000;samesite=lax`;
            window.location.reload();
          });
        });

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
        document.addEventListener('uc:profile-menu-open', closeMenu);
      }
    };
  };
})();
