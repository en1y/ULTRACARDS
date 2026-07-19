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
    const adminModeButton = menu.querySelector('[data-action="admin-mode-toggle"]');
    const adminModeDialog = document.querySelector('[data-admin-mode-dialog]');
    const adminModeForm = document.querySelector('[data-admin-mode-form]');
    const adminModeTitle = document.querySelector('[data-admin-mode-title]');
    const adminModeCopy = document.querySelector('[data-admin-mode-copy]');
    const adminModeConfirm = document.querySelector('[data-admin-mode-confirm]');
    const adminModeLockDialog = document.querySelector('[data-admin-mode-lock-dialog]');
    const adminModeEnabledKey = 'uc-admin-hacks-enabled';
    const adminModeLockKey = 'uc-admin-hacks-locked-until';
    const adminModeLockDuration = 60 * 60 * 1000;
    let adminModeUnlockTimeout;

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

    const adminModeEnabled = () => localStorage.getItem(adminModeEnabledKey) === 'true';
    const adminModeLockedUntil = () => Number(localStorage.getItem(adminModeLockKey)) || 0;
    const syncAdminModeButton = () => {
      if (!adminModeButton) return;
      const locked = adminModeEnabled() && adminModeLockedUntil() > Date.now();
      adminModeButton.setAttribute('aria-pressed', String(adminModeEnabled()));
      adminModeButton.setAttribute('aria-disabled', String(locked));
      adminModeButton.title = locked ? t('header.adminHacks.locked') : '';
      clearTimeout(adminModeUnlockTimeout);
      if (locked) adminModeUnlockTimeout = window.setTimeout(syncAdminModeButton, adminModeLockedUntil() - Date.now());
    };

    const confirmAdminMode = enabled => new Promise(resolve => {
      if (!adminModeDialog || !adminModeForm || !adminModeTitle || !adminModeCopy || !adminModeConfirm) return resolve(false);
      adminModeTitle.textContent = t(enabled ? 'header.adminHacks.disableTitle' : 'header.adminHacks.enableTitle');
      adminModeCopy.textContent = t(enabled ? 'header.adminHacks.disableCopy' : 'header.adminHacks.enableCopy');
      adminModeConfirm.textContent = t(enabled ? 'header.adminHacks.disableConfirm' : 'header.adminHacks.enableConfirm');
      const finish = value => {
        adminModeForm.removeEventListener('submit', submit);
        adminModeForm.removeEventListener('keydown', submitOnEnter);
        adminModeDialog.removeEventListener('close', close);
        resolve(value);
      };
      const submit = event => finish(event.submitter?.value === 'confirm');
      const submitOnEnter = event => {
        if (event.key !== 'Enter' || event.shiftKey || event.target.matches('textarea')) return;
        event.preventDefault();
        adminModeConfirm.click();
      };
      const close = () => finish(false);
      adminModeForm.addEventListener('submit', submit);
      adminModeForm.addEventListener('keydown', submitOnEnter);
      adminModeDialog.addEventListener('close', close, { once: true });
      adminModeDialog.showModal();
    });

    const showAdminModeLock = () => adminModeLockDialog?.showModal();

    return {
      init() {
        syncAdminModeButton();
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
          if (event.key === 'Escape' && menu.classList.contains('open')) {
            closeMenu();
            trigger?.focus();
          }
        });

        document.addEventListener('uc:notifications-open', closeMenu);
        adminModeButton?.addEventListener('click', async () => {
          if (adminModeButton.getAttribute('aria-disabled') === 'true') {
            showAdminModeLock();
            return;
          }
          const enabled = adminModeButton.getAttribute('aria-pressed') === 'true';
          if (!await confirmAdminMode(enabled)) return;
          try {
            const response = await fetch('/api/admin-mode/toggle', { method: 'POST', credentials: 'include' });
            if (!response.ok) return;
            localStorage.setItem(adminModeEnabledKey, String(!enabled));
            if (enabled) {
              localStorage.removeItem(adminModeLockKey);
            } else {
              localStorage.setItem(adminModeLockKey, String(Date.now() + adminModeLockDuration));
            }
            syncAdminModeButton();
            if (!enabled) showAdminModeLock();
          } catch (error) {
          }
        });
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
