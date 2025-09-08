(() => {
  const q = (sel, root = document) => root.querySelector(sel);
  const qa = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  function escapeHTML(str) {
    return str.replace(/[&<>"']/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  function stripTags(str) {
    return str.replace(/<[^>]*>/g, '');
  }

  document.addEventListener('DOMContentLoaded', () => {
    const form = q('#profile-form');
    const emailInput = q('#email');
    const usernameInput = q('#username');
    const emailError = q('#email-error');
    const usernameError = q('#username-error');
    const usernamePattern = /^[A-Za-z0-9_.\-]{1,64}$/;
    const initialEmail = (emailInput?.value || '').trim();
    // Tabs scrollspy
    const tabs = qa('.profile-tabs a');
    const sections = tabs.map(a => q(a.getAttribute('href'))).filter(Boolean);
    const tabById = Object.fromEntries(tabs.map(a => [a.getAttribute('href'), a]));

    const onIntersect = (entries) => {
      entries.forEach(entry => {
        const id = `#${entry.target.id}`;
        const tab = tabById[id];
        if (!tab) return;
        if (entry.isIntersecting) {
          tabs.forEach(t => t.classList.remove('active'));
          tab.classList.add('active');
        }
      });
    };

    if ('IntersectionObserver' in window) {
      const obs = new IntersectionObserver(onIntersect, { rootMargin: '-40% 0px -50% 0px', threshold: 0.01 });
      sections.forEach(s => s && obs.observe(s));
    }

    // Form validation
    // When submitting, if email changed, require verification via header modal
    form?.addEventListener('submit', async (e) => {
      // Only intercept when email changed and verification API is available
      const currentEmail = (emailInput?.value || '').trim();
      const verifiedFor = form?.dataset.verifiedEmail || '';
      const needsVerification = currentEmail !== initialEmail && currentEmail !== verifiedFor;
      const canVerify = !!(window.UCAuth && typeof window.UCAuth.verifyEmail === 'function');
      if (needsVerification && canVerify) {
        if (!validate()) { e.preventDefault(); return; }
        e.preventDefault();
        window.UCAuth.verifyEmail(currentEmail, (verifiedEmail) => {
          // Mark verified and submit programmatically (no submit event fired)
          form.dataset.verifiedEmail = verifiedEmail;
          form.submit();
        }, (message) => {
          // Show errors under the profile email field and keep user on profile
          if (emailError) emailError.textContent = message || 'Could not send code.';
        });
      }
      // If cannot verify, fall through and let normal submit proceed
    }, { capture: true });

    function clearErrors() {
      if (emailError) emailError.textContent = '';
      if (usernameError) usernameError.textContent = '';
      emailInput?.setCustomValidity('');
      usernameInput?.setCustomValidity('');
    }

    function validate() {
      clearErrors();
      let ok = true;

      // Sanitize on read
      const emailRaw = (emailInput?.value || '').trim();
      const userRaw = (usernameInput?.value || '').trim();

      // Basic XSS protection: strip tags; also reject angle brackets
      const email = stripTags(emailRaw);
      const username = stripTags(userRaw);

      if (emailInput && email !== emailInput.value) emailInput.value = email;
      if (usernameInput && username !== usernameInput.value) usernameInput.value = username;

      // Email checks
      if (!email) {
        emailInput?.setCustomValidity('Email is required');
        if (emailError) emailError.textContent = 'Email is required.';
        ok = false;
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
        emailInput?.setCustomValidity('Enter a valid email');
        if (emailError) emailError.textContent = 'Enter a valid email address.';
        ok = false;
      }

      // Username checks
      if (!username) {
        usernameInput?.setCustomValidity('Username is required');
        if (usernameError) usernameError.textContent = 'Username is required.';
        ok = false;
      } else if (!usernamePattern.test(username)) {
        usernameInput?.setCustomValidity('Invalid username');
        if (usernameError) usernameError.textContent = 'Letters, digits, dot, underscore, dash (1–64).';
        ok = false;
      }

      return ok;
    }

    form?.addEventListener('submit', (e) => {
      if (!validate()) {
        e.preventDefault();
      }
    });

    // Cancel button: reset form and clear errors
    const cancelBtn = q('#cancel-edit');
    cancelBtn?.addEventListener('click', () => {
      form?.reset();
      clearErrors();
    });

    // Live sanitization to avoid accidental tags
    [emailInput, usernameInput].forEach(inp => inp?.addEventListener('input', () => {
      const v = inp.value;
      const cleaned = stripTags(v).replace(/[<>]/g, '');
      if (v !== cleaned) inp.value = cleaned;
      clearErrors();
    }));
  });
})();
