(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createHeaderUiPhase = () => {
    let lastScrollY = window.scrollY;
    let scrolledInDirection = 0;

    // Cached DOM/metrics: the scroll handler must NEVER query the DOM or read
    // layout (offsetHeight forces a reflow per scroll event = janky scrolling).
    let headerEl = null;
    let headerHeight = 64;
    let headerHidden = false;

    // The mobile header is two rows tall (~112px) while CSS assumes 64px;
    // measure once (and on resize) and publish it for overlays (friends drawer).
    const refreshHeaderMetrics = () => {
      headerEl = headerEl || document.querySelector('.uc-header');
      if (!headerEl) {
        return;
      }
      headerHeight = headerEl.offsetHeight || 64;
      document.documentElement.style.setProperty('--uc-header-h', `${headerHeight}px`);
    };

    const setHeaderHidden = (hidden) => {
      if (!headerEl || headerHidden === hidden) {
        return;   // no redundant class writes → no needless style recalc
      }
      headerHidden = hidden;
      headerEl.classList.toggle('hidden', hidden);
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
        refreshHeaderMetrics();
        window.addEventListener('scroll', handleScroll, { passive: true });
        window.addEventListener('wheel', handleWheel, { passive: true });
        window.addEventListener('resize', refreshHeaderMetrics);
      }
    };
  };
})();
