(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createHeaderUiPhase = () => {
    let lastScrollY = window.scrollY;
    let scrolledInDirection = 0;

    const setHeaderHidden = (hidden) => {
      const header = document.querySelector('.uc-header');
      if (!header) {
        return;
      }
      header.classList.toggle('hidden', hidden);
    };

    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      const delta = currentScrollY - lastScrollY;
      lastScrollY = currentScrollY;

      // Hysteresis: accumulate travel per direction so 1px jitter and the
      // mobile URL-bar resize can't flap the header on every scroll event.
      if ((delta > 0) !== (scrolledInDirection > 0)) {
        scrolledInDirection = 0;
      }
      scrolledInDirection += delta;

      const headerHeight = document.querySelector('.uc-header')?.offsetHeight || 64;

      if (currentScrollY <= headerHeight) {
        setHeaderHidden(false);
        return;
      }
      if (scrolledInDirection > headerHeight) {
        setHeaderHidden(true);
      } else if (scrolledInDirection < -24) {
        setHeaderHidden(false);
      }
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
