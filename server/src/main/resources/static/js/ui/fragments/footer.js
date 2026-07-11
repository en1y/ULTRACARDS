document.addEventListener('DOMContentLoaded', () => {
      const randomFooter = document.getElementById('random-footer');
      const footers = [t('footer.joke1'), t('footer.joke2'), t('footer.joke3'), t('footer.joke4'), t('footer.joke5')];
      randomFooter.textContent = footers[Math.floor(Math.random() * footers.length)];
    })
