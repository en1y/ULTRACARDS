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

  function showStep(id) {
    stepEls().forEach((el) => {
      el.style.display = el.getAttribute('data-step') === id ? 'block' : 'none';
    });
  }

  function openModal() {
    const ov = overlay();
    if (!ov) return;
    ov.classList.add('active');
    showStep('email');
  }

  function closeModal() {
    const ov = overlay();
    if (!ov) return;
    ov.classList.remove('active');
  }

  document.addEventListener('DOMContentLoaded', () => {
    const btn = openBtn();
    if (btn) btn.addEventListener('click', openModal);
    closeButtons().forEach((b) => b.addEventListener('click', closeModal));
    overlay()?.addEventListener('click', (e) => {
      if (e.target === overlay()) closeModal();
    });

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
        const res = await fetch('/auth/email', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email })
        });
        // Proceed regardless; backend should send the code
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
        if (e.key === 'Backspace' && !box.value && idx > 0) {
          codeBoxes[idx - 1].focus();
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
        const next = codeBoxes[digits.length - 1] || codeBoxes[0];
        next?.focus();
      });
    });

    const codeForm = document.querySelector('#code-step-form');
    const codeError = document.querySelector('#code-error');
    codeForm?.addEventListener('submit', async (e) => {
      e.preventDefault();
      codeError.textContent = '';
      const code = codeValue();
      if (code.length !== 6 || /\D/.test(code)) { codeError.textContent = 'Enter the 6 digit code'; return; }
      try {
        const res = await fetch('/auth/verify-code', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code })
        });
        let needsUsername = true;
        try { const data = await res.json(); needsUsername = !!data?.needsUsername; } catch {}
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
        const res = await fetch('/auth/set-username', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username })
        });
        window.location.reload();
      } catch (err) {
        userError.textContent = 'Could not set username. Try again.';
      }
    });
  });
})();

