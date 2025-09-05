document.addEventListener('DOMContentLoaded', function () {
    const inputs = document.querySelectorAll('.code-input');
    const hiddenInput = document.getElementById('verificationCode');

    inputs.forEach((input, index) => {
        input.addEventListener('input', () => {
            const value = input.value;
            if (value.length === 1 && index < inputs.length - 1) {
                inputs[index + 1].focus();
            }
            updateHiddenInput();
        });

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !input.value && index > 0) {
                inputs[index - 1].focus();
            }
        });

        input.addEventListener('paste', (e) => {
            e.preventDefault();
            const paste = (e.clipboardData || window.clipboardData).getData('text');
            if (/^[0-9]{6}$/.test(paste)) {
                paste.split('').forEach((char, i) => {
                    if (inputs[i]) {
                        inputs[i].value = char;
                    }
                });
                inputs[5].focus();
                updateHiddenInput();
            }
        });

        const usernameForm = document.getElementById('username-form');
        const usernameInput = document.getElementById('username');

        if (usernameForm && usernameForm.style.display !== 'none') {
            usernameInput.focus();
        }
    });

    function updateHiddenInput() {
        hiddenInput.value = Array.from(inputs).map(i => i.value).join('');
    }
});