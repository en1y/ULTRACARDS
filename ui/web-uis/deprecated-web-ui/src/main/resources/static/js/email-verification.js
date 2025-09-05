document.addEventListener('DOMContentLoaded', function () {
    const emailForm = document.getElementById('email-form');
    const authForm = document.getElementById('auth-form');
    const emailInput = document.getElementById('email-input');
    const submitEmailBtn = document.getElementById('submit-email');

    submitEmailBtn.addEventListener('click', function () {
        if (emailInput.checkValidity()) {
            submitEmailBtn.disabled = true;
            submitEmailBtn.textContent = 'Sending...';

            fetch('/auth/send-code?email=' + encodeURIComponent(emailInput.value), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                }
            })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        emailForm.style.display = 'none';
                        authForm.style.display = 'block';
                        document.querySelector('.code-input').focus();

                        const infoDiv = document.createElement('div');
                        infoDiv.className = 'alert alert-success';
                        infoDiv.innerHTML = '<p>' + data.message + '</p>';
                        authForm.insertBefore(infoDiv, authForm.querySelector('.text-center'));
                    } else {
                        const errorDiv = document.createElement('div');
                        errorDiv.className = 'alert alert-error';
                        errorDiv.innerHTML = '<p>' + data.message + '</p>';
                        emailForm.insertBefore(errorDiv, emailForm.querySelector('.text-center'));

                        submitEmailBtn.disabled = false;
                        submitEmailBtn.textContent = 'Continue';
                    }
                })
                .catch(error => {
                    const errorDiv = document.createElement('div');
                    errorDiv.className = 'alert alert-error';
                    errorDiv.innerHTML = '<p>Error sending verification code: ' + error.message + '</p>';
                    emailForm.insertBefore(errorDiv, emailForm.querySelector('.text-center'));

                    submitEmailBtn.disabled = false;
                    submitEmailBtn.textContent = 'Continue';
                });
        } else {
            emailInput.reportValidity();
        }
    });
});