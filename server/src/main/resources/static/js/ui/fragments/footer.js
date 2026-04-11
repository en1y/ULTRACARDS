document.addEventListener('DOMContentLoaded', () => {
      const randomFooter = document.getElementById('random-footer');
      const footers = ["I'm so ULTRACARDSing it rn", "Where am I?", "SSStraight", "God Damn The Cards", "YOU INSIGNIFICANT CARD!"];
      randomFooter.textContent = footers[Math.floor(Math.random() * footers.length)];
    })
