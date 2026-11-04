/* 직원 검색 선택 입력칸. 이름을 입력하면 직원 목록을 바로 보여 주고, 클릭이나 ↑↓·Enter 로 고른다.
   전역 상태 없이 인스턴스마다 동작하므로 한 화면에 여러 개를 둘 수 있다.

   var picker = StaffPicker.create({
     searchUrl: contextPath + 'api/v1/common/emp-list',   // 응답 {status, data:[{EMPNO, EMP_KOR_FLNM, HRA_PSTN_NM, BR_NM}]}
     label: '담당자 검색',                                   // 입력칸 접근성 이름
     onSelect: function (staff) { ... }                     // { id, name, position, dept }
   });
   container.appendChild(picker.element); picker.focus(); picker.reset(); */
(function () {
  'use strict';
  var sequence = 0;

  function create(options) {
    var opts = Object.assign({ limit: 20, debounce: 250, gubun: 1, placeholder: '이름 검색', label: '직원 검색' }, options);
    var id = 'staff-picker-' + (++sequence);
    var timer = null;
    var abort = null;
    var version = 0;
    var results = [];
    var activeIndex = -1;

    var root = document.createElement('div');
    root.className = 'staff-picker';
    var input = document.createElement('input');
    input.type = 'search';
    input.className = 'input staff-picker__input';
    input.placeholder = opts.placeholder;
    input.autocomplete = 'off';
    input.setAttribute('role', 'combobox');
    input.setAttribute('aria-label', opts.label);
    input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-expanded', 'false');
    input.setAttribute('aria-controls', id + '-list');
    var list = document.createElement('ul');
    list.className = 'staff-picker__list';
    list.id = id + '-list';
    list.setAttribute('role', 'listbox');
    list.setAttribute('aria-label', opts.label + ' 결과');
    list.hidden = true;
    var status = document.createElement('p');
    status.className = 'staff-picker__status';
    status.setAttribute('role', 'status');
    root.append(input, list, status);

    function setStatus(text) { status.textContent = text || ''; status.hidden = !text; }

    function setActive(index) {
      var options = list.querySelectorAll('[role="option"]');
      activeIndex = options.length ? (index + options.length) % options.length : -1;
      options.forEach(function (option, i) { option.setAttribute('aria-selected', String(i === activeIndex)); });
      if (activeIndex >= 0) {
        input.setAttribute('aria-activedescendant', options[activeIndex].id);
        options[activeIndex].scrollIntoView({ block: 'nearest' });
      } else {
        input.removeAttribute('aria-activedescendant');
      }
    }

    function render() {
      list.replaceChildren();
      results.forEach(function (staff, index) {
        var option = document.createElement('li');
        option.id = id + '-option-' + index;
        option.className = 'staff-picker__option';
        option.setAttribute('role', 'option');
        option.setAttribute('aria-selected', 'false');
        var name = document.createElement('span');
        name.className = 'staff-picker__name';
        name.textContent = staff.name;
        var meta = document.createElement('span');
        meta.className = 'staff-picker__meta';
        meta.textContent = [staff.position, staff.dept].filter(Boolean).join(' · ');
        option.append(name, meta);
        option.addEventListener('mousedown', function (event) { event.preventDefault(); });
        option.addEventListener('click', function () { choose(index); });
        list.appendChild(option);
      });
      list.hidden = results.length === 0;
      input.setAttribute('aria-expanded', String(!list.hidden));
      setActive(results.length ? 0 : -1);
    }

    function search(keyword) {
      if (abort) abort.abort();
      var current = ++version;
      if (!keyword) { results = []; render(); setStatus(''); return; }
      abort = new AbortController();
      setStatus('검색 중…');
      var url = opts.searchUrl + '?gubun=' + encodeURIComponent(opts.gubun) + '&keyword=' + encodeURIComponent(keyword)
          + '&limit=' + encodeURIComponent(opts.limit);
      fetch(url, { signal: abort.signal, cache: 'no-store' })
        .then(function (response) {
          if (!response.ok) throw new Error('직원 검색 실패');
          return response.json();
        })
        .then(function (body) {
          if (current !== version) return;
          var rows = body && Array.isArray(body.data) ? body.data : [];
          results = rows.map(function (row) {
            return { id: row.EMPNO, name: row.EMP_KOR_FLNM, position: row.HRA_PSTN_NM, dept: row.BR_NM };
          });
          render();
          setStatus(results.length ? '' : '검색 결과가 없습니다.');
        })
        .catch(function (error) {
          if (error.name === 'AbortError' || current !== version) return;
          results = []; render();
          setStatus('직원을 검색하지 못했습니다. 다시 시도해 주세요.');
        });
    }

    function choose(index) {
      var staff = results[index];
      if (!staff) return;
      if (typeof opts.onSelect === 'function') opts.onSelect(staff);
    }

    input.addEventListener('input', function () {
      clearTimeout(timer);
      var keyword = input.value.trim();
      timer = setTimeout(function () { search(keyword); }, opts.debounce);
    });
    input.addEventListener('keydown', function (event) {
      if (event.isComposing) return;
      if (event.key === 'ArrowDown') { event.preventDefault(); setActive(activeIndex + 1); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); setActive(activeIndex - 1); }
      else if (event.key === 'Enter') { event.preventDefault(); choose(activeIndex); }
    });

    return {
      element: root,
      focus: function () { input.focus(); },
      reset: function () {
        clearTimeout(timer);
        if (abort) abort.abort();
        version++;
        input.value = '';
        results = [];
        render();
        setStatus('');
      }
    };
  }

  window.StaffPicker = { create: create };
}());
