(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createNoopPhase = () => ({ init: () => {} });

  window.ucHeader.guardXss = (value) => {
    if (typeof value !== 'string') {
      return { ok: false, message: t('validation.invalidInput') };
    }
    if (value.trim() === '') {
      return { ok: false, message: t('validation.blank') };
    }
    if (/[<>"'`]/.test(value)) {
      return { ok: false, message: t('validation.invalidCharacters') };
    }
    return { ok: true };
  };
})();
