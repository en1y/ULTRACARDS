(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createHeaderUiPhase = () => {
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

    return {
      init() {
        window.addEventListener('scroll', handleScroll, { passive: true });
      }
    };
  };
})();
