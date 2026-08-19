/**
 * The advanced admin user lookup behind ui/fragments/admin/user-search.html.
 *
 * Several criteria apply at once and are ANDed by /reports/users. Each block is
 * independent, so one page can host more than one, and picking a result fires
 * `uc:admin-user-picked` on the block with { id, username, email } in the detail.
 */
(() => {
  const api = '/api/admin/v1';

  const tr = (key, fallback, ...args) => (typeof window.t === 'function' && (window.__I18N__ || {})[key]
    ? window.t(key, ...args)
    : fallback);

  // value: what /reports/users calls the parameter. Anything with `options` renders a
  // select; everything else is a plain input of the given type.
  const FIELDS = () => [
    { value: 'username', label: tr('admin.userSearch.username', 'Username'), type: 'search' },
    { value: 'email', label: tr('admin.userSearch.email', 'Email'), type: 'search' },
    { value: 'userId', label: tr('admin.userSearch.userId', 'User ID'), type: 'number' },
    { value: 'query', label: tr('admin.userSearch.anyField', 'Any field'), type: 'search' },
    {
      value: 'status',
      label: tr('admin.userSearch.status', 'Status'),
      options: ['ACTIVE', 'DISABLED', 'DELETED']
    },
    {
      value: 'role',
      label: tr('admin.userSearch.role', 'Role'),
      options: ['USER', 'MODERATOR', 'ADMIN']
    }
  ];

  const element = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (className) node.className = className;
    return node;
  };

  const formatDate = (value) => {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    const pad = (part) => String(part).padStart(2, '0');
    return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}`;
  };

  const criterionRow = (fields, preset) => {
    const row = element('div', null, 'admin-user-search-criterion');

    const select = document.createElement('select');
    select.className = 'admin-user-search-field';
    select.setAttribute('aria-label', tr('admin.userSearch.criterion', 'Search by'));
    fields.forEach((field) => {
      const option = element('option', field.label);
      option.value = field.value;
      select.append(option);
    });
    if (preset?.field) select.value = preset.field;

    const valueSlot = element('div', null, 'admin-user-search-value');

    const renderValue = () => {
      const field = fields.find((candidate) => candidate.value === select.value) || fields[0];
      let input;
      if (field.options) {
        input = document.createElement('select');
        const any = element('option', tr('admin.common.all', 'All'));
        any.value = '';
        input.append(any, ...field.options.map((value) => {
          const option = element('option', value);
          option.value = value;
          return option;
        }));
      } else {
        input = document.createElement('input');
        input.type = field.type || 'search';
        input.autocomplete = 'off';
        input.placeholder = field.label;
      }
      input.className = 'admin-user-search-input';
      input.setAttribute('aria-label', field.label);
      if (preset?.value != null) input.value = preset.value;
      valueSlot.replaceChildren(input);
    };

    // Swapping the field swaps the control, since a status is a choice and a name is text.
    select.addEventListener('change', () => { preset = null; renderValue(); });
    renderValue();

    const remove = element('button', '×', 'btn admin-user-search-remove');
    remove.type = 'button';
    remove.setAttribute('aria-label', tr('admin.userSearch.removeCriterion', 'Remove criterion'));
    remove.addEventListener('click', () => {
      const list = row.parentElement;
      row.remove();
      // Never leave the block with nothing to type into.
      if (list && !list.children.length) list.append(criterionRow(fields, null));
    });

    row.append(select, valueSlot, remove);
    return row;
  };

  const readCriteria = (root) => {
    const params = new URLSearchParams();
    let count = 0;
    root.querySelectorAll('.admin-user-search-criterion').forEach((row) => {
      const field = row.querySelector('.admin-user-search-field')?.value;
      const value = row.querySelector('.admin-user-search-input')?.value?.trim();
      if (!field || !value) return;
      // Repeating a field would send two identical params; the last one wins server-side,
      // so set() keeps the request honest about what is actually applied.
      params.set(field, value);
      count += 1;
    });
    if (root.querySelector('[data-user-search-exact]')?.checked) params.set('exact', 'true');
    return { params, count };
  };

  const renderResults = (root, page, allowEmpty) => {
    const results = root.querySelector('[data-user-search-results]');
    const pagination = root.querySelector('[data-user-search-pagination]');
    results.replaceChildren();
    const items = page?.items || [];
    if (!items.length) {
      results.append(element('p', tr('admin.userSearch.noMatches', 'No users match those criteria.'), 'admin-empty'));
      if (pagination) pagination.hidden = true;
      return;
    }

    items.forEach((user) => {
      const row = element('button', null, 'admin-user-search-result');
      row.type = 'button';
      const head = element('span', null, 'admin-user-search-result-head');
      const name = element('strong', user.username || `#${user.id}`);
      const status = element('span', user.status || '—', `admin-user-search-badge is-${String(user.status || '').toLowerCase()}`);
      head.append(name, status);
      const meta = element('small', `#${user.id} · ${user.email || '—'}`);
      const facts = element('small', null, 'admin-user-search-result-facts');
      const roles = [...(user.roles || [])].join(', ') || '—';
      facts.append(
        element('span', roles),
        element('span', tr('admin.userSearch.joined', `Joined ${formatDate(user.createdAt)}`, formatDate(user.createdAt))),
        element('span', tr('admin.userSearch.points', `${user.points ?? 0} P`, user.points ?? 0), 'points-value')
      );
      row.append(head, meta, facts);
      row.addEventListener('click', () => {
        root.querySelectorAll('.admin-user-search-result').forEach((other) =>
          other.classList.toggle('is-picked', other === row));
        root.dispatchEvent(new CustomEvent('uc:admin-user-picked', {
          bubbles: true,
          detail: { id: user.id, username: user.username, email: user.email,
            status: user.status, roles: user.roles, points: user.points }
        }));
      });
      results.append(row);
    });

    const totalPages = Math.max(Number(page.totalPages) || 0, 1);
    const currentPage = Math.max(Number(page.page) || 0, 0);
    root._userSearchState = { page: currentPage, allowEmpty };
    if (pagination) {
      pagination.hidden = totalPages <= 1;
      pagination.querySelector('[data-user-search-page]').textContent =
        tr('admin.userSearch.page', `Page ${currentPage + 1} of ${totalPages}`, currentPage + 1, totalPages);
      pagination.querySelector('[data-user-search-previous]').disabled = currentPage <= 0;
      pagination.querySelector('[data-user-search-next]').disabled = currentPage + 1 >= totalPages;
    }
  };

  const search = async (root, pageNumber = 0, allowEmpty = false) => {
    const status = root.querySelector('[data-user-search-status]');
    const { params, count } = readCriteria(root);
    if (!count && !allowEmpty) {
      status.textContent = tr('admin.userSearch.needCriterion', 'Fill in at least one criterion.');
      status.classList.add('is-error');
      return;
    }
    params.set('page', String(pageNumber));
    params.set('size', root.querySelector('[data-user-search-size]')?.value || '10');
    params.set('sort', root.querySelector('[data-user-search-sort]')?.value || 'userCreatedAt');
    params.set('direction', root.querySelector('[data-user-search-direction]')?.value || 'desc');
    status.classList.remove('is-error');
    status.textContent = tr('admin.userSearch.searching', 'Searching…');
    try {
      const response = await fetch(`${api}/reports/users?${params}`, { credentials: 'same-origin' });
      const text = await response.text();
      if (!response.ok) {
        let message = `Request failed (${response.status})`;
        try { message = JSON.parse(text).message || message; } catch { /* non-JSON error body */ }
        throw new Error(message);
      }
      const page = text ? JSON.parse(text) : { items: [] };
      renderResults(root, page, allowEmpty);
      status.textContent = tr('admin.userSearch.matches', 'Matches:') + ` ${page.totalElements ?? 0}`;
    } catch (error) {
      status.textContent = error.message;
      status.classList.add('is-error');
    }
  };

  const init = (root) => {
    if (root.dataset.userSearchReady) return;
    root.dataset.userSearchReady = 'true';
    const fields = FIELDS();
    const list = root.querySelector('[data-user-search-criteria]');
    list.append(criterionRow(fields, null));

    root.querySelector('[data-user-search-add]')?.addEventListener('click', () => {
      list.append(criterionRow(fields, null));
      list.lastElementChild.querySelector('.admin-user-search-input')?.focus();
    });
    root.querySelector('[data-user-search-submit]')?.addEventListener('click', () => search(root, 0, false));
    root.querySelector('[data-user-search-browse]')?.addEventListener('click', () => search(root, 0, true));
    root.querySelector('[data-user-search-previous]')?.addEventListener('click', () => {
      const state = root._userSearchState || { page: 0, allowEmpty: false };
      search(root, Math.max(0, state.page - 1), state.allowEmpty);
    });
    root.querySelector('[data-user-search-next]')?.addEventListener('click', () => {
      const state = root._userSearchState || { page: 0, allowEmpty: false };
      search(root, state.page + 1, state.allowEmpty);
    });
    root.querySelector('[data-user-search-clear]')?.addEventListener('click', () => {
      list.replaceChildren(criterionRow(fields, null));
      root.querySelector('[data-user-search-results]').replaceChildren();
      root.querySelector('[data-user-search-pagination]').hidden = true;
      root.querySelector('[data-user-search-exact]').checked = false;
      root.querySelector('[data-user-search-sort]').value = 'userCreatedAt';
      root.querySelector('[data-user-search-direction]').value = 'desc';
      root.querySelector('[data-user-search-size]').value = '10';
      root._userSearchState = null;
      const status = root.querySelector('[data-user-search-status]');
      status.textContent = '';
      status.classList.remove('is-error');
    });
    // Enter anywhere in the block searches, the way a form would.
    root.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      search(root, 0, false);
    });
  };

  const initAll = () => document.querySelectorAll('[data-user-search]').forEach(init);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initAll, { once: true });
  else initAll();

  window.UltracardsUserSearch = { init, initAll, search };
})();
