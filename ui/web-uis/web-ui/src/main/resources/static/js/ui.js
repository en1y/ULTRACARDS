// Theme toggle and persistence
(function () {
  const storageKey = 'uc-theme';
  const root = document.documentElement;
  const toggleBtn = () => document.querySelector('[data-action="toggle-theme"]');

  function getPreferredTheme() {
    const saved = localStorage.getItem(storageKey);
    if (saved === 'light' || saved === 'dark') return saved;
    return 'light';
  }

  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    localStorage.setItem(storageKey, theme);
    const btn = toggleBtn();
    if (btn) btn.setAttribute('aria-pressed', theme === 'dark');
    try {
      const src = theme === 'dark' ? '/pics/profile_icon_light.svg' : '/pics/profile_icon_dark.svg';
      document.querySelectorAll('[data-avatar]').forEach((img) => {
        if (img.getAttribute('src') !== src) img.setAttribute('src', src);
      });
    } catch {}
  }

  document.addEventListener('DOMContentLoaded', () => {
    applyTheme(getPreferredTheme());
    const btn = toggleBtn();
    if (btn) {
      btn.addEventListener('click', () => {
        const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        applyTheme(next);
      });
    }
  });
})();

// Header hide/show on scroll
(function () {
  let lastY = window.scrollY;
  const header = () => document.querySelector('.uc-header');
  window.addEventListener('scroll', () => {
    const h = header();
    if (!h) return;
    const current = window.scrollY;
    if (current > lastY && current > 40) {
      h.classList.add('hidden');
    } else {
      h.classList.remove('hidden');
    }
    lastY = current;
  }, { passive: true });
})();

// Simple XSS guard: returns { ok, message }
function guardXSS(value) {
  if (typeof value !== 'string') return { ok: false, message: 'Invalid input' };
  if (value.trim() === '') return { ok: false, message: 'Value must not be blank' };
  const forbidden = /[<>"'`]/;
  if (forbidden.test(value)) return { ok: false, message: 'Invalid characters detected' };
  return { ok: true };
}

// Login modal logic (multi-step)
(function () {
  const overlay = () => document.querySelector('#login-modal');
  const closeButtons = () => overlay()?.querySelectorAll('[data-close]') || [];
  const stepEls = () => overlay()?.querySelectorAll('[data-step]') || [];
  const openBtn = () => document.querySelector('[data-action="open-login"]');
  // Keep the email captured in step 1 for use in verification
  let pendingEmail = null;

  function showStep(id) {
    stepEls().forEach((el) => {
      el.style.display = el.getAttribute('data-step') === id ? 'block' : 'none';
    });
  }

  function openModal() {
    const ov = overlay();
    if (!ov) return;
    ov.classList.add('active');
    // Reset all fields so nothing is memorised between openings
    try {
      const emailInput = document.querySelector('#email-input');
      if (emailInput) emailInput.value = '';
      const codeBoxes = Array.from(document.querySelectorAll('.code-box'));
      codeBoxes.forEach(b => b.value = '');
      const userInput = document.querySelector('#username-input');
      if (userInput) userInput.value = '';
      const emailError = document.querySelector('#email-error');
      const codeError = document.querySelector('#code-error');
      const userError = document.querySelector('#username-error');
      if (emailError) emailError.textContent = '';
      if (codeError) codeError.textContent = '';
      if (userError) userError.textContent = '';
      pendingEmail = null;
    } catch {}
    showStep('email');
  }

  function closeModal() {
    const ov = overlay();
    if (!ov) return;
    ov.classList.remove('active');
    // Also clear inputs on close so next open is clean
    try {
      const emailInput = document.querySelector('#email-input');
      if (emailInput) emailInput.value = '';
      const codeBoxes = Array.from(document.querySelectorAll('.code-box'));
      codeBoxes.forEach(b => b.value = '');
      const userInput = document.querySelector('#username-input');
      if (userInput) userInput.value = '';
      const emailError = document.querySelector('#email-error');
      const codeError = document.querySelector('#code-error');
      const userError = document.querySelector('#username-error');
      if (emailError) emailError.textContent = '';
      if (codeError) codeError.textContent = '';
      if (userError) userError.textContent = '';
      pendingEmail = null;
    } catch {}
  }

  document.addEventListener('DOMContentLoaded', () => {
    const btn = openBtn();
    if (btn) btn.addEventListener('click', openModal);
    // Only the X button (data-close in modal header) can close the modal.
    closeButtons().forEach((b) => b.addEventListener('click', closeModal));

    // Step 1: email submit
    const emailForm = document.querySelector('#email-step-form');
    const emailInput = document.querySelector('#email-input');
    const emailError = document.querySelector('#email-error');
    emailForm?.addEventListener('submit', async (e) => {
      e.preventDefault();
      emailError.textContent = '';
      const email = (emailInput?.value || '').trim();
      // Basic email check and xss guard
      const x = guardXSS(email);
      if (!x.ok) { emailError.textContent = x.message; return; }
      const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!re.test(email)) { emailError.textContent = 'Please enter a valid email'; return; }
      try {
        const res = await fetch('/api/auth/email/send', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email })
        });
        // Proceed regardless; backend should send the code
        pendingEmail = email;
        showStep('code');
        // Focus first code box
        document.querySelector('.code-box')?.focus();
      } catch (err) {
        emailError.textContent = 'Network error. Try again.';
      }
    });

    // Step 2: code input
    const codeBoxes = Array.from(document.querySelectorAll('.code-box'));
    function codeValue() { return codeBoxes.map(b => b.value).join(''); }
    codeBoxes.forEach((box, idx) => {
      box.addEventListener('input', (e) => {
        const v = box.value.replace(/\D/g, '');
        box.value = v.slice(0, 1);
        if (box.value && idx < codeBoxes.length - 1) codeBoxes[idx + 1].focus();
      });
      box.addEventListener('keydown', (e) => {
        const prev = () => codeBoxes[idx - 1];
        const next = () => codeBoxes[idx + 1];

        if (e.key === 'ArrowLeft' && idx > 0) { prev()?.focus(); return; }
        if (e.key === 'ArrowRight' && idx < codeBoxes.length - 1) { next()?.focus(); return; }

        // Backspace behavior
        if (e.key === 'Backspace') {
          e.preventDefault();
          if (box.value) {
            box.value = '';
          } else if (idx > 0) {
            // Delete left cell when current is empty
            const p = prev();
            if (p) { p.value = ''; p.focus(); }
          }
          return;
        }

        // Delete behavior
        if (e.key === 'Delete') {
          e.preventDefault();
          if (box.value) {
            box.value = '';
          } else if (idx < codeBoxes.length - 1) {
            // Delete right cell when current is empty
            const n = next();
            if (n) { n.value = ''; }
          }
          return;
        }
      });
      box.addEventListener('paste', (e) => {
        const paste = (e.clipboardData || window.clipboardData).getData('text');
        if (!paste) return;
        e.preventDefault();
        const digits = paste.replace(/\D/g, '').slice(0, codeBoxes.length);
        for (let i = 0; i < digits.length; i++) {
          codeBoxes[i].value = digits[i];
        }
        const nxt = codeBoxes[digits.length - 1] || codeBoxes[0];
        nxt?.focus();
      });
    });

    const codeForm = document.querySelector('#code-step-form');
    const codeError = document.querySelector('#code-error');
    codeForm?.addEventListener('submit', async (e) => {
      e.preventDefault();
      codeError.textContent = '';
      const code = codeValue();
      if (code.length !== 6 || /\D/.test(code)) { codeError.textContent = 'Enter the 6 digit code'; return; }
      if (!pendingEmail) { codeError.textContent = 'Missing email. Go back and enter your email.'; return; }
      try {
        const res = await fetch('/api/auth/email/verify', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code, email: pendingEmail })
        });
        let needsUsername = true;
        try {
          const data = await res.json();
          if (data && data.success === true) {
            needsUsername = !!data.needsUsername;
          } else {
            codeError.textContent = 'Verification failed. Try again.';
            return;
          }
        } catch {}
        if (needsUsername) {
          showStep('username');
          document.querySelector('#username-input')?.focus();
        } else {
          window.location.reload();
        }
      } catch (err) {
        codeError.textContent = 'Verification failed. Try again.';
      }
    });

    // Step 3: username
    const userForm = document.querySelector('#username-step-form');
    const userInput = document.querySelector('#username-input');
    const userError = document.querySelector('#username-error');
    userForm?.addEventListener('submit', async (e) => {
      e.preventDefault();
      userError.textContent = '';
      const username = (userInput?.value || '').trim();
      const x = guardXSS(username);
      if (!x.ok) { userError.textContent = x.message; return; }
      if (username.length < 3) { userError.textContent = 'Username must be at least 3 characters'; return; }
      try {
        const res = await fetch('/api/auth/username', {
          method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username })
        });
        window.location.reload();
      } catch (err) {
        userError.textContent = 'Could not set username. Try again.';
      }
    });
  });
})();
