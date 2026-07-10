(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createLanguagePhase = () => {
    const select = document.querySelector('[data-language-select]');
    if (!select) {
      return { init: () => {} };
    }

    const changeLanguage = () => {
      document.cookie = `uc-lang=${select.value};path=/;max-age=31536000;samesite=lax`;
      window.location.reload();
    };

    return {
      init() {
        select.addEventListener('change', changeLanguage);
      }
    };
  };
})();
