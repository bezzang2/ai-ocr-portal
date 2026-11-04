# AI OCR 서류판독시스템 — HTML 퍼블리싱 구조

Figma `BPDT1QEnlvJirJPbFo0qLl` (node 232:744 레이아웃, node 481:5704/481:6522 스타일가이드)
실측 기반 초기 스캐폴드.

## 폴더 구조

```
aiocr-publishing/
├── html/
│   ├── common/              # LNB, GNB 등 JSP include 대상 조각
│   └── application/         # 도메인별 화면 (신청관리 등) — IA 2depth 리프당 1파일
├── css/
│   ├── reset.css            # 업계 표준 리셋 (브랜드 무관, 고정)
│   ├── tokens/
│   │   ├── primitive.css    # Layer 1 — 실측 원시값 (컬러/폰트/스페이싱 등)
│   │   ├── semantic.css     # Layer 2 — 용도별 별칭
│   │   └── component.css    # Layer 3 — 컴포넌트 치수 토큰
│   ├── base.css              # 전역 기본 스타일 + Figma 텍스트 스타일 클래스
│   ├── layout.css            # 앱 셸 그리드 + 콘텐츠 레이아웃 패턴
│   ├── components/           # 컴포넌트별 CSS (button/input/table/pagination/nav-search/lnb)
│   └── main.css              # 로컬 미리보기용 aggregator
├── js/lnb.js                  # LNB 아코디언 토글 + 현재 페이지 하이라이트
├── fonts/Pretendard/          # 웹폰트 자체 호스팅 위치
└── images/icons/               # 아이콘 전략 별도 안내 참고 (로고 포함)
```

새 화면/컴포넌트가 생기면 `css/components/`, `css/pages/`(화면 고유 예외 스타일),
`html/<domain>/` 아래에 같은 패턴으로 추가한다. 토큰 레이어(1→2→3) 순서를
거스르지 않는 것이 유일한 규칙이다.

## 목록/검색형 화면 — 재사용 가능한 공통 블록

`html/application/ocr-form-search.html`(Figma node 232:744 실측)이 이 패턴의 기준 구현이다.
새 "검색+목록" 화면을 만들 때는 아래 블록을 그대로 복사해 텍스트/데이터만 바꾼다 —
간격·버튼 크기·색상은 전부 `css/components/nav-search.css`, `button.css`, `input.css`에
공통 클래스로 빠져 있어 화면마다 다시 정의할 필요가 없다.

```html
<main class="content">                          <!-- gap 12px, padding 24px/40px -->
  <div class="page-header">                      <!-- 제목 + 우측 액션 버튼, height 30px -->
    <h1 class="page-header__title">...</h1>
    <button class="btn-outline-primary">...</button>
  </div>
  <div class="tab-panel">                        <!-- gap 24px -->
    <nav class="tabs">...</nav>                  <!-- .tabs__item.is-active로 선택 탭 표시 -->
    <div class="layout-list">                    <!-- gap 24px -->
      <form class="search-area">                 <!-- 검색조건 영역, bg Neutral/50, radius 8px -->
        <div class="search-area__row">           <!-- gap 36px -->
          <div class="search-area__item">        <!-- gap 16px(기본)/12px(--tight) -->
            <label class="search-area__label">...</label>
            <select class="input input--w120">...</select>   <!-- w80/120/160/200 폭 modifier -->
          </div>
          <!-- 입력 2개가 묶인 항목(검색어 유형+텍스트, 날짜 범위)은 .search-area__combo로 감싼다 -->
        </div>
        <div class="search-area__actions">       <!-- 새로고침(.btn-icon) + 조회(.btn-outline-primary) -->
      </form>
      <div class="list-section">                 <!-- gap 12px -->
        <h2 class="list-section__title">...</h2>
        <table class="table">...</table>         <!-- 상태값 셀은 <span class="status-pill"> -->
        <div class="list-footer">
          <span class="list-footer__count">...</span>
          <nav class="pagination">...</nav>       <!-- 아이콘은 images/icons/pagination/*.svg -->
        </div>
      </div>
    </div>
  </div>
</main>
```

컬럼 너비(`<th style="width:...">`)처럼 테이블 스키마에 종속된 값만 화면별로 인라인
유지한다 — 공용 컴포넌트 클래스와 화면 고유 데이터를 섞지 않는 게 원칙이다.

## 토큰 출처 원칙

이 프로젝트는 AgileSoDA 사내 디자인시스템과 무관한 별도 프로젝트다. 모든 토큰은
`get_variable_defs`(Figma **Variables**, 실제 컴포넌트에 바인딩된 값)와 Local Text
Style(`Type/*`)만 근거로 삼았고, 관례나 다른 프로젝트 값을 가져다 쓰지 않았다.
Variable로 확인되지 않은 값은 "레이아웃 좌표 실측" 또는 "미확인"으로 별도 표시했다.

## 이번 조사에서 확정된 것 (Figma Variable/Local Style 근거)

- **컬러**: Blue(Primary)/Red(Error)/Orange(Warning)/Green(Success)/Neutral 5팔레트
  50~900, Text/Border/Divider/Surface/Icon/Status(Error·Warning·Success·Info) 40개
  별칭 — 전부 `get_variable_defs` 실측.
- **타이포**: Pretendard, 13종 Local Text Style(`Type/Display-36B` ~ `Type/Body-12R`).
- **Spacing**: `spacing/sm`(6) · `spacing/lg`(12) · `spacing/xl`(16) · `spacing/4xl`(40)
  및 동일값의 중복 이름들(`Size/Space-*`, `Size/Pd-*`, `--sds-size-space-400`) —
  실제 버튼/테이블 인스턴스의 padding·gap에 바인딩된 상태로 확인.
- **Radius**: `radius/sm`·`Size/Radius-M`·`Corner/Extra-small`(전부 4px, 이름만 다른
  중복), `radius/2xl`(16), `Size/Radius-Round`(9999, pill) — 버튼에 실바인딩 확인.
- **컨트롤 높이**: `Size/Size-M` = `component-height/md` = 30px — 버튼에 실바인딩 확인.
- 레이아웃: LNB(200px, 전체 높이) + GNB(40px) + Content 구조, 목록/검색형 화면 1종.

### ⚠️ Figma 파일 자체의 이슈 (참고용, 코드에서 임의로 정리하지 않음)

- **Spacing/Radius/Size에 이름이 다른 중복 Variable이 다수 존재한다.** 예: 4px 값에
  `radius/sm` / `Size/Radius-M` / `Corner/Extra-small` 세 이름이 동시에 쓰인다. UI
  키트(이름 패턴상 shadcn 계열로 추정)를 가져다 쓰면서 자체 명명 체계와 키트 원본
  체계가 합쳐진 것으로 보인다. `tokens/primitive.css`에 전부 남겨두고 실제 바인딩된
  이름만 `tokens/component.css`에서 정식 소스로 채택했다 — 통합 여부는 디자인팀이
  결정할 사안이라 판단해 임의로 하나만 남기지 않았다.
- **아이콘 크기는 Variable이 아니라 인스턴스별 리터럴 값이다.** 심지어 이름에 "24"가
  붙은 아이콘(`ic/24/chevron-down` 등)도 실제로는 16px로 렌더링되는 경우가 확인됐다
  — 이름과 실치수 불일치. 별도 아이콘 사이즈 스케일을 만들지 않았다.

## 확정하지 못한 것 (지어내지 않고 표시만 해둠)

- **Shadow/Effect**: 파일 전체(화면·버튼 컴포넌트)에서 Effect Style/box-shadow
  Variable이 전혀 검출되지 않았다 — 완전 미정의. Dialog/Dropdown처럼 그림자가
  필요한 컴포넌트가 나오면 해당 프레임을 실측해서 채울 것.
- **Status-Neutral 색**: Figma는 Error/Warning/Success/Info 4종만 정의. Neutral 상태가
  필요하면 디자이너 확인 후 값을 채울 것.
- **Button/Input의 Hover·Disabled 색**: 컴포넌트 전용 프레임을 아직 조회하지 않았다.
  `css/components/button.css`, `input.css`에 임시값으로 표시해뒀다.
- **LNB/검색영역 좌우 패딩(24px)**: Variable로 바인딩돼 있지 않고 좌표 계산 추정치다.
  `css/components/nav-search.css`에 표시해뒀다.
- **다른 화면 레이아웃 패턴**: 지금까지 확인된 프레임은 "목록/검색형" 1종뿐이다. OCR
  서류판독 특성상 "문서뷰어+추출폼 분할형" 같은 화면이 반드시 나올 텐데, 그 프레임을
  실측하기 전까지는 `layout.css`의 `.layout-split` 자리만 비워뒀다 — 1개 화면만 보고
  범용 그리드를 확정 짓는 건 과도한 일반화라 판단했다.

## LNB 전체 실측 반영 (get_design_context 481:6740)

- **배경색 정정**: `.lnb` 배경은 흰색이 아니라 `Blue/50`(#e5f1ff)이었다 — 이전 버전 오류 수정.
- **크기/간격**: Logo `width 200 / padding 20px 24px / gap 4px`(24px·4px는 Variable
  미확인, 리터럴), 1depth `height 38px / gap 8px / padding-x 16px`(8·16은 confirmed
  Variable), 2depth `height 32px / radius 4px`, 서브메뉴 컨테이너 `padding 4px 0`(리터럴).
- **로고는 이미지로 대체**: 로고 워드마크("AI OCR" + "서류판독시스템")는 원래 Montserrat
  Bold 폰트로 렌더링했으나, `images/icons/logo-aiocr.svg`(93×45, white fill) 단일 이미지로
  교체했다 — 폰트 자체 호스팅(`fonts/Montserrat/`) 불필요해짐. `.lnb__logo-img`로 삽입.
  메뉴 라벨의 letter-spacing도 이 컴포넌트 실측값(-0.0476px,
  -0.0442px)이 이전에 뽑아둔 Typography 프레임 값(-0.34px)과 어긋나 — 두 출처가
  일치하지 않는다는 뜻이라 이 컴포넌트는 실제 렌더값을 그대로 썼다(`lnb.css` 주석 참고).
- **아이콘은 Figma 원본 벡터로 교체 완료**: 최초 작업 시점엔 Figma 애셋 서버가 네트워크
  정책에서 차단돼 대체 아이콘을 손으로 그려 넣었으나, 이후 세션에서 get_design_context
  (481:6740)로 원본 path 데이터를 정확히 추출해 전면 교체했다(대시보드·통합검색(3레이어
  합성)·신청관리(checkmark)·관리자·커뮤니티·chevron 6종). `fill`/`stroke`만 `currentColor`로
  바꿔 기존 default(`--icon-primary-strong`)/active(`--icon-inverse`) 상태 전환 구조는
  그대로 유지했다. 백업 파일은 `images/icons/lnb/README.md` 참고.
- **메뉴 구조는 `ia.png`(제공된 IA 정의서) 기준으로 전면 재구성**: 1depth 5개(대시보드·
  통합검색·신청관리·관리자·커뮤니티) 전부를 아코디언 그룹(`.nav-group`)으로 통일했다.
  대시보드·통합검색은 원래 2depth가 없었으나 "전 메뉴 아코디언" 요구사항에 맞춰 1depth와
  동일 이름의 2depth 1개씩을 추가했다. 신청관리(6)·관리자(7)·커뮤니티(3) 2depth 항목명은
  `ia.png` 텍스트를 그대로 옮겼다. Figma node 232:744 화면 자체의 라벨은 "OCR 신청검색"
  이었으나, 실제로는 IA상의 "OCR서식조회" 화면이라는 정정을 받아 화면명·타이틀·
  `html/application/ocr-form-search.html` 파일명 전부 "OCR서식조회" 기준으로 통일했다.
- **아코디언·화면이동**: 1depth 클릭 시 `js/lnb.js`가 `.nav-group`에 `is-open`을 토글해
  펼침/닫힘만 담당하고, 2depth(`.nav-subitem`)는 실제 `<a href>`라 JS 없이도 이동한다.
  각 리프 화면은 `html/application/`에 제목(`<h1>`)만 있는 빈 페이지로 생성했다(총 18개).
  페이지 로드 시 `js/lnb.js`가 현재 파일명과 일치하는 `nav-subitem`을 찾아 `is-selected`를
  붙이고 그 부모 그룹을 자동으로 펼친다.

## 운영 반영 시 참고

- Pretendard는 CDN import 대신 `fonts/Pretendard/`에 자체 호스팅할 것 (인트라넷 CDN 차단 대비).
- `main.css`의 `@import`는 로컬 미리보기 편의용이다. 실제 JSP 공통 헤더에는
  `reset → tokens/primitive → tokens/semantic → tokens/component → base → layout → components/*`
  순서로 `<link>` 태그를 개별 나열하는 것을 권장한다.
- 아이콘은 화면별 이미지 export 대신 아이콘 폰트/SVG 스프라이트로 표준화 권장 (`images/icons/README.md` 참고).
