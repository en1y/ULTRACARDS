(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createNoopPhase = () => ({ init: () => {} });

  window.ucHeader.guardXss = (value) => {
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
})();
