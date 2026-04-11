(() => {
            const storageKey = 'uc-theme';
            const savedTheme = localStorage.getItem(storageKey);
            const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            const theme = savedTheme || (systemDark ? 'dark' : 'light');
            document.documentElement.setAttribute('data-theme', theme);
        })();

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const PROFILE_STATUS_TIMEOUT_MS = 3200;
    let profileStatusTimer = null;

    function setProfileStatus(message, type) {
        const status = document.getElementById('profile-status');
        if (profileStatusTimer) {
            window.clearTimeout(profileStatusTimer);
        }
        status.textContent = message;
        status.className = `profile-status is-visible is-${type}`;
        profileStatusTimer = window.setTimeout(() => hideProfileStatus(), PROFILE_STATUS_TIMEOUT_MS);
    }

    function clearProfileStatus() {
        const status = document.getElementById('profile-status');
        if (profileStatusTimer) {
            window.clearTimeout(profileStatusTimer);
            profileStatusTimer = null;
        }
        status.textContent = '';
        status.className = 'profile-status';
    }

    function hideProfileStatus() {
        const status = document.getElementById('profile-status');
        if (!status.textContent) {
            clearProfileStatus();
            return;
        }

        if (profileStatusTimer) {
            window.clearTimeout(profileStatusTimer);
            profileStatusTimer = null;
        }

        status.classList.remove('is-visible');
        status.classList.add('is-hiding');

        window.setTimeout(() => {
            if (status.classList.contains('is-hiding')) {
                clearProfileStatus();
            }
        }, 280);
    }

    async function save() {
        const username = document.getElementById('username').value.trim();
        const email = document.getElementById('email').value.trim();

        clearProfileStatus();

        if (!EMAIL_PATTERN.test(email)) {
            setProfileStatus('Wrong email format.', 'error');
            return;
        }

        try {
            const response = await fetch('api/auth/profile', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username,
                    email
                })
            });

            if (!response.ok) {
                if (response.status === 400) {
                    setProfileStatus('Could not save profile. Check the email and username format.', 'error');
                    return;
                }

                setProfileStatus('Could not save profile. Please try again.', 'error');
                throw new Error(`Response status: ${response.status}`);
            }

            await update(await response.json());
            setProfileStatus('Profile saved successfully.', 'success');
        } catch (error) {
            if (!document.getElementById('profile-status').textContent) {
                setProfileStatus('Network error while saving profile.', 'error');
            }
            console.log(error.message);
        }
    }

    async function refresh() {
        try {
            const response = await fetch('api/auth/profile');

            if (!response.ok)
                throw new Error(`Response status: ${response.status}`);
            const result = await response.json();
            await update(result);
            clearProfileStatus();
        } catch (error) {
            console.error(error.message)
        }
    }

    async function update(data) {
        console.log(data)

        document.getElementById('username').value = data.username;
        document.getElementById('username-header').value = data.username;
        document.getElementById('email').value = data.email;
        document.getElementById('roles').innerText = data.roles.join(", ");
        document.getElementById('id').innerText = data.id;
        document.getElementById('games_played').innerText = data.gamesPlayed;
        document.getElementById('games_won').innerText = data.gamesWon;

        let per_game_type = document.getElementById('per-game-type');
        per_game_type.innerText = '';

        Object.entries(data.playedAndWonGames).forEach(([gameType, arr]) => {
            const game_card = document.createElement('div');
            game_card.className = 'profile-game-card';

            const title = document.createElement('span');
            title.textContent = gameType;

            const br1 = document.createElement('br');

            const playedGames = document.createElement('span');
            playedGames.textContent = `${arr[0]}`;

            const br2 = document.createElement('br');

            const wonGames = document.createElement('span');
            wonGames.textContent = `${arr[1]}`;

            game_card.appendChild(title);
            game_card.appendChild(br1);
            game_card.append("Games played: ");
            game_card.appendChild(playedGames);
            game_card.appendChild(br2);
            game_card.append("Games won: ");
            game_card.appendChild(wonGames);

            per_game_type.appendChild(game_card);
        })
    }

document.getElementById('refresh')?.addEventListener('click', refresh);
document.getElementById('save-profile')?.addEventListener('click', save);
