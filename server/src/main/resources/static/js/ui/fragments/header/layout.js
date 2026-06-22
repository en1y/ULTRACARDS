(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createHeaderUiPhase = () => {
    let lastScrollY = window.scrollY;

    const setHeaderHidden = (hidden) => {
      const header = document.querySelector('.uc-header');
      if (!header) {
        return;
      }
      header.classList.toggle('hidden', hidden);
    };

    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      if (currentScrollY > lastScrollY && currentScrollY > 40) {
        setHeaderHidden(true);
      } else if (currentScrollY < lastScrollY || currentScrollY <= 8) {
        setHeaderHidden(false);
      }
      lastScrollY = currentScrollY;
    };

    const handleWheel = (event) => {
      if (!document.body.classList.contains('game-page')) {
        return;
      }
      if (event.deltaY > 0) {
        setHeaderHidden(true);
        return;
      }
      if (event.deltaY < 0) {
        setHeaderHidden(false);
      }
    };

    return {
      init() {
        window.addEventListener('scroll', handleScroll, { passive: true });
        window.addEventListener('wheel', handleWheel, { passive: true });
      }
    };
  };
})();
