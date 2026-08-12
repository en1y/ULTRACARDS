(() => {
  const output = document.querySelector('[data-points-balance]');
  if (!output) return;
  fetch('/api/points/balance', {credentials: 'same-origin'})
      .then(response => response.ok ? response.json() : Promise.reject())
      .then(balance => { output.textContent = pointsCompactAmount(balance); })
      .catch(() => { output.textContent = '—'; });
})();
