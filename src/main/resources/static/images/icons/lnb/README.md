# LNB 아이콘

get_design_context(fileKey `BPDT1QEnlvJirJPbFo0qLl`, nodeId `481:6740`)로 Figma 원본 벡터를
직접 받아 반영했다(대시보드·통합검색·신청관리(checkmark)·관리자·커뮤니티·chevron 6종).
이 폴더의 svg는 실제 화면(HTML)에 쓰이는 인라인 `<svg>`와 동일한 path 데이터를 담은
참고/백업용 개별 파일이다 — 실제 페이지는 `fill="currentColor"`가 CSS `color: var(--icon-*)`
로 상속돼 default(`--icon-primary-strong` #00286c)/active(`--icon-inverse` #fff) 상태 전환이
되어야 하므로 `<img src="...svg">`가 아니라 각 페이지에 인라인으로 직접 삽입돼 있다.

- `ic-16-chevron-down.svg` 1개만 있다 — 펼침(▲) 상태는 별도 파일 대신 CSS
  `transform: rotate(180deg)`로 만든다(원본 Figma도 arrow-down/arrow-up이 같은 모양을
  색만 바꿔 쓰는 두 개 인스턴스였다).
- `ic-16-search.svg`는 레이어 3장(메인 렌즈+손잡이, 작은 점 2개)이 겹쳐서 하나의 아이콘을
  이루는 합성 아이콘이다 — Figma의 `inset-[...]` 퍼센트 값을 16×16 기준 px로 환산해
  `<g transform="translate(x,y)">`로 각 레이어를 정확한 위치에 배치했다.
