(() => {
      if (window.__ucHeaderInitialized) {
        return;
      }
      window.__ucHeaderInitialized = true;

      const guardXss = (value) => {
        if (typeof value !== 'string') {
          return { ok: false, message: 'Invalid input' };
        }
        if (value.trim() === '') {
          return { ok: false, message: 'Value must not be blank' };
        }
        if (/[<>"'`]/.test(value)) {
          return { ok: false, message: 'Invalid characters detected' };
        }
        return { ok: true };
      };

      // Phase 1: header shell behavior
      const createHeaderUiPhase = () => {
        let lastScrollY = window.scrollY;
        const handleScroll = () => {
          const header = document.querySelector('.uc-header');
          if (!header) {
            return;
          }

          const currentScrollY = window.scrollY;
          if (currentScrollY > lastScrollY && currentScrollY > 40) {
            header.classList.add('hidden');
          } else {
            header.classList.remove('hidden');
          }
          lastScrollY = currentScrollY;
        };

        const init = () => {
          window.addEventListener('scroll', handleScroll, { passive: true });
        };

        return { init };
      };

      // Phase 2: authenticated profile interactions
      const createProfileMenuPhase = () => {
        const menu = document.querySelector('[data-profile-menu]');
        if (!menu) {
          return { init: () => {} };
        }

        const trigger = menu.querySelector('[data-profile-menu-trigger]');
        const panel = menu.querySelector('.profile-dropdown');
        const logoutButton = menu.querySelector('[data-action="logout"]');

        const openMenu = () => {
          menu.classList.add('open');
          trigger?.setAttribute('aria-expanded', 'true');
          panel?.setAttribute('aria-hidden', 'false');
        };

        const closeMenu = () => {
          menu.classList.remove('open');
          trigger?.setAttribute('aria-expanded', 'false');
          panel?.setAttribute('aria-hidden', 'true');
        };

        const bindToggle = () => {
          trigger?.addEventListener('click', (event) => {
            event.stopPropagation();
            if (menu.classList.contains('open')) {
              closeMenu();
            } else {
              openMenu();
            }
          });
        };

        const bindDismiss = () => {
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
        };

        const bindLogout = () => {
          logoutButton?.addEventListener('click', async () => {
            closeMenu();
            if (!window.confirm('This will log you out and clear your session. Are you sure?')) {
              return;
            }

            try {
              await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
            } catch (error) {
            }

            window.location.href = '/';
          });
        };

        const init = () => {
          bindToggle();
          bindDismiss();
          bindLogout();
        };

        return { init };
      };
      // Phase 3: login and sign-up modal flow
      const createAuthModalPhase = () => {
        const overlay = document.querySelector('#login-modal');
        if (!overlay) {
          return { init: () => {} };
        }

        const stepElements = Array.from(overlay.querySelectorAll('[data-step]'));
        const closeButtons = overlay.querySelectorAll('[data-close]');
        const cancelButtons = overlay.querySelectorAll('[data-cancel]');
        const openButton = document.querySelector('[data-action="open-login"]');
        const emailInput = overlay.querySelector('#email-input');
        const emailError = overlay.querySelector('#email-error');
        const codeError = overlay.querySelector('#code-error');
        const usernameInput = overlay.querySelector('#username-input');
        const usernameError = overlay.querySelector('#username-error');
        const emailForm = overlay.querySelector('#email-step-form');
        const codeForm = overlay.querySelector('#code-step-form');
        const usernameForm = overlay.querySelector('#username-step-form');
        const codeBoxes = Array.from(overlay.querySelectorAll('.code-box'));
        let pendingEmail = null;

        const showStep = (id) => {
          stepElements.forEach((stepElement) => {
            stepElement.style.display = stepElement.getAttribute('data-step') === id ? 'block' : 'none';
          });
        };

        const clearErrorMessages = () => {
          if (emailError) {
            emailError.textContent = '';
          }
          if (codeError) {
            codeError.textContent = '';
          }
          if (usernameError) {
            usernameError.textContent = '';
          }
        };

        const resetModalState = () => {
          if (emailInput) {
            emailInput.value = '';
          }
          if (usernameInput) {
            usernameInput.value = '';
          }
          codeBoxes.forEach((box) => {
            box.value = '';
          });
          clearErrorMessages();
          pendingEmail = null;
        };

        const openModal = () => {
          resetModalState();
          overlay.classList.add('active');
          showStep('email');
        };

        const closeModal = () => {
          overlay.classList.remove('active');
          resetModalState();
        };

        const parseErrorResponse = async (response, fallbackMessage) => {
          let message = fallbackMessage;
          try {
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
              const payload = await response.json();
              const messages = [];
              if (Array.isArray(payload?.globalErrors)) {
                messages.push(...payload.globalErrors);
              }
              if (payload?.errors) {
                messages.push(...Object.values(payload.errors).filter(Boolean));
              }
              if (Array.isArray(payload?.messages)) {
                messages.push(...payload.messages);
              }
              message = messages.filter((item, index, array) => array.indexOf(item) === index).join('\n') || payload?.message || fallbackMessage;
            } else {
              message = await response.text() || fallbackMessage;
            }
          } catch (error) {
          }

          return message;
        };

        const validateEmail = () => {
          const email = (emailInput?.value || '').trim();
          const guardedEmail = guardXss(email);
          if (!guardedEmail.ok) {
            if (emailError) {
              emailError.textContent = guardedEmail.message;
            }
            return null;
          }

          if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            if (emailError) {
              emailError.textContent = 'Please enter a valid email';
            }
            return null;
          }

          return email;
        };

        const getCodeValue = () => codeBoxes.map((box) => box.value).join('');

        const validateCode = () => {
          const code = getCodeValue();
          if (code.length !== 6 || /\D/.test(code)) {
            if (codeError) {
              codeError.textContent = 'Enter the 6 digit code';
            }
            return null;
          }

          if (!pendingEmail) {
            if (codeError) {
              codeError.textContent = 'Missing email. Go back and enter your email.';
            }
            showStep('email');
            return null;
          }

          return code;
        };

        const validateUsername = () => {
          const username = (usernameInput?.value || '').trim();
          const guardedUsername = guardXss(username);
          if (!guardedUsername.ok) {
            if (usernameError) {
              usernameError.textContent = guardedUsername.message;
            }
            return null;
          }

          if (username.length < 3) {
            if (usernameError) {
              usernameError.textContent = 'Username must be at least 3 characters';
            }
            return null;
          }

          return username;
        };

        const bindModalControls = () => {
          openButton?.addEventListener('click', openModal);
          closeButtons.forEach((button) => {
            button.addEventListener('click', closeModal);
          });
          cancelButtons.forEach((button) => {
            button.addEventListener('click', (event) => {
              event.preventDefault();
              closeModal();
            });
          });
        };

        const bindEmailSubmission = () => {
          emailForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (emailError) {
              emailError.textContent = '';
            }

            const email = validateEmail();
            if (!email) {
              return;
            }

            try {
              const response = await fetch('/api/auth/email/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ email })
              });

              if (!response.ok) {
                if (emailError) {
                  emailError.textContent = await parseErrorResponse(response, 'Could not send code.');
                }
                pendingEmail = null;
                showStep('email');
                return;
              }

              pendingEmail = email;
              showStep('code');
              codeBoxes[0]?.focus();
            } catch (error) {
              if (emailError) {
                emailError.textContent = 'Network error. Try again.';
              }
            }
          });
        };

        const bindCodeInputs = () => {
          codeBoxes.forEach((box, index) => {
            box.addEventListener('input', () => {
              box.value = box.value.replace(/\D/g, '').slice(0, 1);
              if (box.value && index < codeBoxes.length - 1) {
                codeBoxes[index + 1].focus();
              }
            });

            box.addEventListener('keydown', (event) => {
              const previous = codeBoxes[index - 1];
              const next = codeBoxes[index + 1];

              if (event.key === 'ArrowLeft' && previous) {
                previous.focus();
                return;
              }

              if (event.key === 'ArrowRight' && next) {
                next.focus();
                return;
              }

              if (event.key === 'Backspace') {
                event.preventDefault();
                if (box.value) {
                  box.value = '';
                } else if (previous) {
                  previous.value = '';
                  previous.focus();
                }
              }

              if (event.key === 'Delete') {
                event.preventDefault();
                if (box.value) {
                  box.value = '';
                } else if (next) {
                  next.value = '';
                }
              }
            });

            box.addEventListener('paste', (event) => {
              const pasted = (event.clipboardData || window.clipboardData).getData('text');
              if (!pasted) {
                return;
              }

              event.preventDefault();
              const digits = pasted.replace(/\D/g, '').slice(0, codeBoxes.length);
              digits.split('').forEach((digit, digitIndex) => {
                codeBoxes[digitIndex].value = digit;
              });
              codeBoxes[Math.max(digits.length - 1, 0)]?.focus();
            });
          });
        };

        const bindCodeSubmission = () => {
          codeForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (codeError) {
              codeError.textContent = '';
            }

            const code = validateCode();
            if (!code) {
              return;
            }

            try {
              const response = await fetch('/api/auth/email/verify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ code, email: pendingEmail })
              });

              const payload = await response.json().catch(() => null);
              if (!response.ok) {
                if (codeError) {
                  codeError.textContent = payload?.message || 'Verification failed. Try again.';
                }
                return;
              }

              const username_response = await fetch('/api/auth/username', {
                method: 'GET',
                credentials: 'include'
              });

              if (!username_response.ok){
                if (codeError) {
                  codeError.textContent = payload?.message || "Couldn't get the username. Try again.";
                }
                return;
              }
              const username = await username_response.json();
              const existingUsername = (username?.username || '').trim();

              if (!existingUsername) {
                showStep('username');
                usernameInput?.focus();
                return;
              }

              window.location.reload();
            } catch (error) {
              if (codeError) {
                codeError.textContent = 'Verification failed. Try again.';
              }
            }
          });
        };

        const bindUsernameSubmission = () => {
          usernameForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (usernameError) {
              usernameError.textContent = '';
            }

            const username = validateUsername();
            if (!username) {
              return;
            }

            try {
              const response = await fetch('/api/auth/username', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ username })
              });

              if (!response.ok) {
                if (usernameError) {
                  usernameError.textContent = 'Could not set username. Try again.';
                }
                return;
              }

              window.location.reload();
            } catch (error) {
              if (usernameError) {
                usernameError.textContent = 'Could not set username. Try again.';
              }
            }
          });
        };

        const init = () => {
          bindModalControls();
          bindEmailSubmission();
          bindCodeInputs();
          bindCodeSubmission();
          bindUsernameSubmission();
        };

        return { init };
      };

      // Phase 0: theme state sync with shared theme.js helpers
      const createThemePhase = () => ({
        init: () => {
          window.syncThemeUi?.();
        }
      });

      const initHeader = () => {
        const phases = [
          createThemePhase(),
          createHeaderUiPhase(),
          createProfileMenuPhase(),
          createAuthModalPhase()
        ];

        phases.forEach((phase) => phase.init());
      };

      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initHeader, { once: true });
      } else {
        initHeader();
      }
    })();
