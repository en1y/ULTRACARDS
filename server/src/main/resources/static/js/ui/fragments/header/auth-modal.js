(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createAuthModalPhase = () => {
    const overlay = document.querySelector('#login-modal');
    if (!overlay) {
      window.ucAuthModal = {
        requestRecentVerification: async () => false
      };
      return { init: () => {} };
    }

    const guardXss = window.ucHeader.guardXss;
    const stepElements = Array.from(overlay.querySelectorAll('[data-step]'));
    const closeButtons = overlay.querySelectorAll('[data-close]');
    const cancelButtons = overlay.querySelectorAll('[data-cancel]');
    const openButton = document.querySelector('[data-action="open-login"]');
    const title = overlay.querySelector('#login-title');
    const subtitle = overlay.querySelector('#login-subtitle');
    const emailInput = overlay.querySelector('#email-input');
    const emailError = overlay.querySelector('#email-error');
    const codeError = overlay.querySelector('#code-error');
    const usernameInput = overlay.querySelector('#username-input');
    const usernameError = overlay.querySelector('#username-error');
    const emailForm = overlay.querySelector('#email-step-form');
    const codeForm = overlay.querySelector('#code-step-form');
    const usernameForm = overlay.querySelector('#username-step-form');
    const resendButton = overlay.querySelector('[data-resend-code]');
    const codeBoxes = Array.from(overlay.querySelectorAll('.code-box'));

    const modes = {
      auth: {
        title: 'Welcome to ULTRACARDS',
        subtitle: ''
      },
      reverify: {
        title: 'Confirm Profile Changes',
        subtitle: 'A verification code has been sent to your email.'
      }
    };

    let pendingEmail = null;
    let currentMode = 'auth';
    let verifyContinuation = null;
    let verificationPromiseResolve = null;

    const showStep = (id) => {
      stepElements.forEach((stepElement) => {
        stepElement.style.display = stepElement.getAttribute('data-step') === id ? 'block' : 'none';
      });
    };

    const clearErrors = () => {
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

    const resetState = () => {
      if (emailInput) {
        emailInput.value = '';
      }
      if (usernameInput) {
        usernameInput.value = '';
      }
      codeBoxes.forEach((box) => {
        box.value = '';
      });
      clearErrors();
      pendingEmail = null;
      currentMode = 'auth';
      verifyContinuation = null;
      verificationPromiseResolve = null;
      if (resendButton) {
        resendButton.style.display = 'none';
      }
    };

    const applyModeCopy = () => {
      const config = modes[currentMode] || modes.auth;
      if (title) {
        title.textContent = config.title;
      }
      if (subtitle) {
        subtitle.textContent = config.subtitle;
      }
      if (resendButton) {
        resendButton.style.display = currentMode === 'reverify' ? '' : 'none';
      }
    };

    const closeModal = (verified = false) => {
      overlay.classList.remove('active');
      verificationPromiseResolve?.(verified);
      resetState();
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
          message = messages.filter((item, index, array) => array.indexOf(item) === index).join('\n')
            || payload?.message
            || fallbackMessage;
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

      if (currentMode === 'auth' && !pendingEmail) {
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

    const sendAuthenticatedVerificationEmail = async () => {
      const response = await fetch('/api/auth/email/send', {
        method: 'POST',
        credentials: 'include'
      });

      if (!response.ok) {
        throw new Error(await parseErrorResponse(response, 'Could not send verification code.'));
      }
    };

    const waitForAuthenticatedRefresh = async () => {
      for (let attempt = 0; attempt < 5; attempt += 1) {
        const response = await fetch('/api/profile/username', {
          method: 'GET',
          credentials: 'include',
          cache: 'no-store'
        }).catch(() => null);

        if (response?.ok) {
          return;
        }

        await new Promise((resolve) => window.setTimeout(resolve, 80));
      }

      throw new Error('Verification succeeded, but the refreshed session was not ready yet.');
    };

    const openModal = (mode = 'auth') => {
      resetState();
      currentMode = mode;
      applyModeCopy();
      overlay.classList.add('active');
      showStep(mode === 'reverify' ? 'code' : 'email');
    };

    const requestRecentVerification = async ({ onVerified } = {}) => {
      verifyContinuation = typeof onVerified === 'function' ? onVerified : null;
      openModal('reverify');

      try {
        await sendAuthenticatedVerificationEmail();
        codeBoxes[0]?.focus();
      } catch (error) {
        if (codeError) {
          codeError.textContent = error.message || 'Could not send verification code.';
        }
      }

      return new Promise((resolve) => {
        verificationPromiseResolve = resolve;
      });
    };

    const resendCode = async () => {
      clearErrors();

      try {
        if (currentMode === 'reverify') {
          await sendAuthenticatedVerificationEmail();
          if (codeError) {
            codeError.textContent = 'A new code was sent to your email.';
          }
          return;
        }

        const email = validateEmail();
        if (!email) {
          return;
        }

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
        if (currentMode === 'reverify') {
          if (codeError) {
            codeError.textContent = 'Network error. Try again.';
          }
        } else if (emailError) {
          emailError.textContent = 'Network error. Try again.';
        }
      }
    };

    window.ucAuthModal = {
      requestRecentVerification
    };

    return {
      init() {
        openButton?.addEventListener('click', () => openModal('auth'));

        closeButtons.forEach((button) => {
          button.addEventListener('click', () => closeModal(false));
        });

        cancelButtons.forEach((button) => {
          button.addEventListener('click', (event) => {
            event.preventDefault();
            closeModal(false);
          });
        });

        resendButton?.addEventListener('click', async (event) => {
          event.preventDefault();
          await resendCode();
        });

        emailForm?.addEventListener('submit', async (event) => {
          event.preventDefault();
          clearErrors();
          await resendCode();
        });

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
              body: JSON.stringify({
                code,
                email: currentMode === 'reverify' ? 'verified@ultracards.local' : pendingEmail
              })
            });

            const payload = await response.json().catch(() => null);
            if (!response.ok) {
              if (codeError) {
                codeError.textContent = payload?.message || 'Verification failed. Try again.';
              }
              return;
            }

            if (currentMode === 'reverify') {
              try {
                await waitForAuthenticatedRefresh();
                await verifyContinuation?.();
                closeModal(true);
              } catch (error) {
                if (codeError) {
                  codeError.textContent = error.message || 'Verification succeeded, but the action failed.';
                }
              }
              return;
            }

            const usernameResponse = await fetch('/api/profile/username', {
              method: 'GET',
              credentials: 'include'
            });

            if (!usernameResponse.ok) {
              if (codeError) {
                codeError.textContent = payload?.message || "Couldn't get the username. Try again.";
              }
              return;
            }

            const username = await usernameResponse.json();
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
            const response = await fetch('/api/profile/username', {
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
      }
    };
  };
})();
