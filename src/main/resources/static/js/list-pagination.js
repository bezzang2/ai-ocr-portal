/* 조회한 목록을 같은 규격으로 나누는 공용 페이지네이션. 서버의 조회 범위는 바꾸지 않는다. */
(function () {
  'use strict';
  var SIZES = [15, 30, 50];

  /** 양의 정수만 페이지 번호로 받는다. 주소의 잘못된 값은 첫 페이지로 돌린다. */
  function positive(value) {
    var number = Number(value);
    return Number.isSafeInteger(number) && number > 0 ? number : 1;
  }

  /**
   * 목록 하단 조작부를 만든다. onChange에는 보정된 page·pageSize와 slice용 start·end를 전달한다.
   * container는 이 목록 전용 DOM 요소다. 초기화와 setTotal·reset 때에도 onChange를 동기 호출한다.
   * 빈 목록은 조작부를 숨기며 페이지는 1로 보정한다.
   */
  function create(options) {
    var container = options.container;
    var pager = {
      page: positive(options.page),
      pageSize: SIZES.indexOf(Number(options.pageSize)) >= 0 ? Number(options.pageSize) : 15,
      start: 0, end: 0,
      /** 검색·삭제 후 총 건수를 반영한다. reset이면 첫 페이지로 이동한다. */
      setTotal: function (total, settings) {
        pager.totalCount = Math.max(0, Math.floor(Number(total) || 0));
        if (settings && settings.reset) pager.page = 1;
        render();
      },
      /** 표시 건수는 유지하고 첫 페이지로 이동한다. */
      reset: function () { pager.page = 1; render(); }
    };
    container.classList.add('list-pagination');

    function render(focusLabel) {
      var pages = Math.max(1, Math.ceil(pager.totalCount / pager.pageSize));
      pager.page = Math.min(pages, positive(pager.page));
      pager.start = (pager.page - 1) * pager.pageSize;
      pager.end = Math.min(pager.start + pager.pageSize, pager.totalCount);
      container.hidden = pager.totalCount === 0;
      container.replaceChildren();
      var count = document.createElement('p');
      count.className = 'list-footer__count';
      count.setAttribute('role', 'status');
      count.setAttribute('aria-live', 'polite');
      count.setAttribute('aria-atomic', 'true');
      count.textContent = (pager.totalCount ? pager.start + 1 : 0) + '–' + pager.end + ' / ' + pager.totalCount + '건';
      var nav = document.createElement('nav');
      nav.className = 'pagination';
      nav.setAttribute('aria-label', '목록 페이지');
      function button(label, text, target, disabled, current) {
        var item = document.createElement('button');
        item.type = 'button';
        item.className = 'pagination__item' + (current ? ' is-current' : '');
        item.setAttribute('aria-label', label);
        item.textContent = text;
        item.disabled = disabled;
        if (current) item.setAttribute('aria-current', 'page');
        item.addEventListener('click', function () {
          pager.page = target;
          render(label);
        });
        nav.appendChild(item);
      }
      button('처음', '«', 1, pager.page === 1);
      button('이전', '‹', pager.page - 1, pager.page === 1);
      // 숫자 버튼은 다섯 개씩 묶어 좁은 목록에서도 조작부 너비를 제한한다.
      var first = Math.floor((pager.page - 1) / 5) * 5 + 1;
      for (var i = first; i <= Math.min(first + 4, pages); i++) {
        button(i + '페이지', String(i), i, false, i === pager.page);
      }
      button('다음', '›', pager.page + 1, pager.page === pages);
      button('끝', '»', pages, pager.page === pages);
      var size = document.createElement('select');
      size.className = 'input input--w140 list-footer__page-size';
      size.setAttribute('aria-label', '페이지당 표시 건수');
      SIZES.forEach(function (value) {
        var item = document.createElement('option');
        item.value = String(value);
        item.textContent = value + '개씩 보기';
        item.selected = value === pager.pageSize;
        size.appendChild(item);
      });
      size.addEventListener('change', function () {
        pager.pageSize = Number(size.value);
        pager.page = 1;
        render('페이지당 표시 건수');
      });
      container.append(count, nav, size);
      options.onChange(pager);
      // 조작부를 다시 그린 뒤에도 키보드 사용자가 같은 조작을 이어갈 수 있게 한다.
      if (focusLabel) {
        var target = Array.from(container.querySelectorAll('[aria-label]')).find(function (node) {
          return node.getAttribute('aria-label') === focusLabel && !node.disabled;
        });
        (target || nav.querySelector('[aria-current="page"]')).focus({ preventScroll: true });
      }
    }
    pager.setTotal(options.totalCount);
    return pager;
  }
  window.ListPagination = { create: create };
}());
