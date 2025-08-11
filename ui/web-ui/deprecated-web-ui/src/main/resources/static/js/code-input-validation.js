document.addEventListener('DOMContentLoaded', () => {
    const inputs = document.querySelectorAll('.code-input');

    inputs.forEach((input, index) => {
        input.addEventListener('input', (e) => {
            // Remove non-digits
            e.target.value = e.target.value.replace(/\D/g, '');

            // Move to next input if filled
            if (e.target.value.length === 1 && index < inputs.length - 1) {
                inputs[index + 1].focus();
            }

            // Combine digits into hidden input
            document.getElementById('verificationCode').value =
                Array.from(inputs).map(i => i.value).join('');
        });

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !e.target.value && index > 0) {
                inputs[index - 1].focus();
            }
        });
    });
});