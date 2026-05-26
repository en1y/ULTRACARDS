(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createHeaderSearchPhase = () => {
    const form = document.querySelector('[data-header-search]');
    if (!form) {
      return { init: () => {} };
    }

    const input = form.querySelector('[data-header-search-input]');
    const resultsPanel = form.querySelector('[data-header-search-results]');
    if (!input || !resultsPanel) {
      return { init: () => {} };
    }

    const debounceDelay = 220;
    let debounceId = null;
    let abortController = null;
    let requestSequence = 0;
    let lastRenderedSignature = null;

    const setDropdownOpen = (isOpen) => {
      form.classList.toggle('open', isOpen);
      input.setAttribute('aria-expanded', String(isOpen));
      resultsPanel.setAttribute('aria-hidden', String(!isOpen));
    };

    const clearSearch = () => {
      if (debounceId) {
        window.clearTimeout(debounceId);
        debounceId = null;
      }
      abortController?.abort();
      abortController = null;
      requestSequence += 1;
      lastRenderedSignature = null;
      resultsPanel.replaceChildren();
      setDropdownOpen(false);
    };

    const toSearchUser = (item) => {
      if (item?.id === null || item?.id === undefined) {
        return null;
      }

      return {
        id: item.id,
        username: String(item.username || 'Unnamed user')
      };
    };

    const compareUsersById = (left, right) => {
      const leftId = Number(left.id);
      const rightId = Number(right.id);
      if (Number.isFinite(leftId) && Number.isFinite(rightId)) {
        return leftId - rightId;
      }

      return String(left.id).localeCompare(String(right.id));
    };

    const buildResultSignature = (state, users = []) => {
      if (state !== 'results') {
        return state;
      }

      return users
        .map((user) => `${user.id}\u0000${user.username}`)
        .join('\u0001');
    };

    const createStateElement = (className, message) => {
      const element = document.createElement('div');
      element.className = className;
      element.textContent = message;
      return element;
    };

    const createResultRow = (user) => {
      const row = document.createElement('div');
      row.className = 'header-search-row';
      row.setAttribute('role', 'listitem');

      const username = document.createElement('span');
      username.className = 'header-search-username';
      username.textContent = user.username;

      const id = document.createElement('span');
      id.className = 'header-search-id';
      id.textContent = `#${user.id}`;

      row.append(username, id);
      return row;
    };

    const renderResults = (state, users = []) => {
      const signature = buildResultSignature(state, users);
      if (signature === lastRenderedSignature) {
        setDropdownOpen(true);
        return;
      }

      lastRenderedSignature = signature;

      if (state === 'error') {
        resultsPanel.replaceChildren(createStateElement('header-search-state error', 'Search failed. Try again.'));
        setDropdownOpen(true);
        return;
      }

      if (users.length === 0) {
        resultsPanel.replaceChildren(createStateElement('header-search-state', 'No users found.'));
        setDropdownOpen(true);
        return;
      }

      const fragment = document.createDocumentFragment();
      for (const user of users) {
        fragment.append(createResultRow(user));
      }
      resultsPanel.replaceChildren(fragment);
      setDropdownOpen(true);
    };

    const fetchJson = async (url, signal) => {
      const response = await fetch(url, {
        method: 'GET',
        credentials: 'include',
        cache: 'no-store',
        signal
      });

      if (!response.ok) {
        throw new Error('Search request failed');
      }

      return response.json();
    };

    const searchUsers = async (query) => {
      abortController?.abort();
      abortController = new AbortController();
      const currentRequest = requestSequence + 1;
      requestSequence = currentRequest;

      const encodedQuery = encodeURIComponent(query);
      const bounds = 'lower=0&higher=10';
      const requests = [
        fetchJson(`/api/users/search/username/${encodedQuery}?${bounds}`, abortController.signal)
      ];

      if (/\d/.test(query)) {
        requests.push(fetchJson(`/api/users/search/id/${encodedQuery}?${bounds}`, abortController.signal));
      }

      try {
        const responses = await Promise.all(requests);
        if (currentRequest !== requestSequence || input.value.trim() !== query) {
          return;
        }

        const usersById = new Map();
        for (const response of responses) {
          if (!Array.isArray(response)) {
            continue;
          }

          for (const item of response) {
            const user = toSearchUser(item);
            if (user && !usersById.has(String(user.id))) {
              usersById.set(String(user.id), user);
            }
          }
        }

        const users = Array.from(usersById.values()).sort(compareUsersById);
        renderResults('results', users);
      } catch (error) {
        if (error.name === 'AbortError') {
          return;
        }

        if (currentRequest === requestSequence && input.value.trim() === query) {
          renderResults('error');
        }
      }
    };

    const queueSearch = () => {
      const query = input.value.trim();
      if (query === '') {
        clearSearch();
        return;
      }

      if (debounceId) {
        window.clearTimeout(debounceId);
      }

      debounceId = window.setTimeout(() => {
        debounceId = null;
        searchUsers(query);
      }, debounceDelay);
    };

    return {
      init() {
        input.addEventListener('input', queueSearch);

        form.addEventListener('submit', (event) => {
          event.preventDefault();
          const query = input.value.trim();
          if (query === '') {
            clearSearch();
            return;
          }

          if (debounceId) {
            window.clearTimeout(debounceId);
            debounceId = null;
          }
          searchUsers(query);
        });

        document.addEventListener('click', (event) => {
          if (!form.contains(event.target)) {
            setDropdownOpen(false);
          }
        });

        document.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            setDropdownOpen(false);
            input.blur();
          }
        });
      }
    };
  };
})();
