/* 자주 쓰는 검증의 시작점. 현재 업무의 서식만 제안하고 저장하지 않은 새 편집 상태를 만든다. */
(function () {
  'use strict';

  // 이름이 명확히 같은 경우만 제안한다. 일부 글자가 포함된 서류를 임의로 고르지 않는다.
  var identity = ['신분증', '주민등록증', '운전면허증'];
  var registry = ['등기', '등기사항증명서', '등기부', '등기부등본', '법인등기부', '법인등기부등본', '등기사항전부증명서', '법인등기사항전부증명서'];
  var NAME_KEYS = ['성명', '이름'];
  var OFFICER = ['임원.', '임원명부.', '임원에관한사항.'];
  /** 임원 표의 항목 이름 후보. 등기부 종류마다 표 이름이 달라 접두어를 바꿔 붙인다. */
  function officerKeys(field) { return OFFICER.map(function (prefix) { return prefix + field; }); }

  /* 필드 정의 도우미. names는 이름 기반 자동 제안에 쓰는 후보이며 화면에는 노출하지 않는다. */
  function formField(name, label, side, names) { return { name: name, label: label, kind: 'form', side: side, names: names }; }
  function keyField(name, label, side, names) { return { name: name, label: label, kind: 'key', side: side, names: names }; }
  function textField(name, label, defaultValue) { return { name: name, label: label, kind: 'text', defaultValue: defaultValue }; }
  function numberField(name, label, defaultValue, extra) { return Object.assign({ name: name, label: label, kind: 'number', defaultValue: defaultValue, step: 'any' }, extra); }
  /** 1 이상의 정수만 받는 칸(기간·나이 등). createDraft가 integer·min으로 검사한다. */
  function integerField(name, label, defaultValue, min) { return numberField(name, label, defaultValue, { min: min, step: 1, integer: true }); }

  /*
   * 템플릿 정의. 화면(builder)은 id·group·title·description·example·fields만 읽는다.
   * group은 카드 묶음 제목이다. 같은 group은 배열에서 이어 붙여 둔다(화면은 group이 바뀔 때 제목을 새로 단다).
   * fields의 side는 항목 칸이 어느 서류 칸을 따르는지(같은 side의 form 칸 + 'Form')를 뜻한다.
   * methods(v, make)는 통과 방법별 비교 목록을 만든다. make(type, props)는 v 중 비교가 가진 속성을 채운 새 비교를 반환한다.
   * when(v)이 있으면 경우의 적용 조건을 정하고, name(v)이 있으면 초안 이름을 정한다.
   */
  var templates = [
    /* 항목 비교: 서로 다른 서류의 값이 같은지 본다. */
    {
      id: 'compare', group: '값 비교', title: '성명 비교', description: '두 서류의 항목 값이 같은지 확인합니다.',
      example: '신분증 성명과 위임장 수임자 성명이 같은지 확인',
      fields: [
        formField('leftForm', '비교할 서류', 'left', identity), keyField('leftKey', '비교할 항목', 'left', NAME_KEYS),
        formField('rightForm', '비교 대상 서류', 'right', ['위임장']), keyField('rightKey', '비교 대상 항목', 'right', ['수임자성명', '수임인성명', '수임자.성명', '수임인.성명'])
      ],
      methods: function (v, make) { return [[make('compare', { op: 'EQ', match: 'ANY' })]]; }
    },
    {
      id: 'representative', group: '값 비교', title: '대표 행 선택', description: '대표자인 행을 골라 성명을 비교합니다.',
      example: '신분증 성명과 등기의 대표자 행 성명이 같은지 확인',
      fields: [
        formField('leftForm', '비교할 서류', 'left', identity), keyField('leftKey', '성명 항목', 'left', NAME_KEYS),
        formField('rightForm', '대표자가 적힌 서류', 'right', registry), keyField('rightKey', '비교 대상 성명 항목', 'right', officerKeys('성명')),
        keyField('conditionKey', '직위 항목', 'right', officerKeys('직위')), textField('conditionValue', '대표자를 뜻하는 값', '대표자')
      ],
      methods: function (v, make) { return [[make('compare', { op: 'EQ', match: 'ANY', condition: true, conditionOp: 'EQ' })]]; }
    },
    {
      id: 'pair', group: '값 비교', title: '성명·생년월일 함께', description: '같은 사람의 성명과 생년월일이 둘 다 같은 행을 찾습니다.',
      example: '신분증 성명·생년월일이 등기의 대표자 행 성명·생년월일과 함께 같은지 확인',
      fields: [
        formField('leftForm', '비교할 서류', 'left', identity), keyField('leftKey', '성명 항목', 'left', NAME_KEYS), keyField('leftKey2', '생년월일 항목', 'left', ['생년월일', '생일']),
        formField('rightForm', '비교 대상 서류', 'right', registry), keyField('rightKey', '비교 대상 성명 항목', 'right', officerKeys('성명')),
        keyField('rightKey2', '비교 대상 생년월일 항목', 'right', officerKeys('생년월일')),
        keyField('conditionKey', '직위 항목', 'right', officerKeys('직위')), textField('conditionValue', '대표자를 뜻하는 값', '대표자')
      ],
      // pairs는 성명·생년월일이 같은 행에서 함께 맞아야 통과하게 묶는다(다른 사람의 값을 섞어 맞추지 않는다).
      methods: function (v, make) {
        return [[make('compare', { op: 'EQ', match: 'ANY', condition: true, conditionOp: 'EQ', pairs: [{ left: v.leftKey2, right: v.rightKey2 }] })]];
      }
    },
    {
      id: 'family', group: '값 비교', title: '가족 목록에 모두 있음', description: '신청서의 가족 성명이 모두 본인·배우자·가족사항 안에 있는지 확인합니다.',
      example: '신청서의 가족 성명이 가족관계증명서의 본인·배우자·가족사항 성명 중에 모두 있는지 확인',
      fields: [
        formField('leftForm', '가족을 적은 서류', 'left', ['신청서']), keyField('leftKey', '가족 성명 항목', 'left', ['가족성명', '가족.성명', '동반가족.성명']),
        formField('rightForm', '비교 대상 서류', 'right', ['가족관계증명서']), keyField('rightKey', '본인 성명 항목', 'right', ['본인.성명', '본인성명']),
        keyField('spouseKey', '배우자 성명 항목', 'right', ['배우자.성명', '배우자성명']), keyField('familyKey', '가족사항 성명 항목', 'right', ['가족사항.성명', '가족.성명'])
      ],
      // 비교 대상 항목 더하기(extras)로 본인·배우자·가족사항 성명을 하나의 목록으로 합치고, 모두 맞아야 함(ALL)으로 한 명도 빠지지 않게 한다.
      // 새 규칙은 통합된 항목 비교(compare·같다)로 만든다. 'list' 유형은 기존 규칙 호환용 저장 코드라 새로 만들지 않는다.
      methods: function (v, make) {
        return [[make('compare', { op: 'EQ', match: 'ALL', extras: [{ form: v.rightForm, key: v.spouseKey }, { form: v.rightForm, key: v.familyKey }] })]];
      }
    },
    {
      id: 'parent', group: '값 비교', title: '부 또는 모와 같음', description: '두 방법 중 하나라도 통과하면 맞는 것으로 봅니다.',
      example: '신분증 성명이 가족관계증명서의 부 성명 또는 모 성명과 같은지 확인',
      fields: [
        formField('leftForm', '비교할 서류', 'left', identity), keyField('leftKey', '비교할 항목', 'left', NAME_KEYS),
        formField('rightForm', '비교 대상 서류', 'right', ['가족관계증명서']), keyField('fatherKey', '부 성명 항목', 'right', ['부.성명', '부성명', '부']),
        keyField('motherKey', '모 성명 항목', 'right', ['모.성명', '모성명', '모'])
      ],
      // 통과 방법을 둘로 나누면 하나만 통과해도 규칙이 통과한다(부 방법 OR 모 방법).
      methods: function (v, make) {
        return [[make('compare', { op: 'EQ', match: 'ANY', rightKey: v.fatherKey })], [make('compare', { op: 'EQ', match: 'ANY', rightKey: v.motherKey })]];
      }
    },
    {
      id: 'top-holder', group: '값 비교', title: '최대주주', description: '지분이 가장 큰 행을 골라 성명을 비교합니다.',
      example: '신분증 성명과 주주명부에서 소유주식수가 가장 많은 주주 성명이 같은지 확인',
      fields: [
        formField('leftForm', '비교할 서류', 'left', identity), keyField('leftKey', '비교할 항목', 'left', NAME_KEYS),
        formField('rightForm', '주주가 적힌 서류', 'right', ['주주명부']), keyField('rightKey', '비교 대상 성명 항목', 'right', ['주주명', '주주.성명', '주주명부.성명', '성명']),
        keyField('sortKey', '순위를 정할 항목', 'right', ['소유주식수', '보유주식수', '주식수', '주주.소유주식수'])
      ],
      // 순위로 행 고르기: 숫자 큰 순 1개(limit 1)만 비교 대상으로 남긴다.
      methods: function (v, make) {
        return [[make('compare', { op: 'EQ', match: 'ANY', rightSort: { by: v.sortKey, order: 'DESC', as: 'NUMBER', limit: 1 } })]];
      }
    },
    /* 값 확인: 한 서류 항목만 보고 값이 있는지, 형식이 맞는지 확인한다. */
    {
      id: 'presence', group: '값 확인', title: '값 있음', description: '항목에 값이 적혀 있는지 확인합니다.',
      example: '위임장 수임자 성명에 값이 있는지 확인',
      fields: [formField('leftForm', '확인할 서류', 'left', ['위임장']), keyField('leftKey', '확인할 항목', 'left', ['수임자성명', '수임인성명', '수임자.성명', '수임인.성명'])],
      methods: function (v, make) { return [[make('presence', { op: 'NOT_EMPTY' })]]; }
    },
    {
      id: 'pattern', group: '값 확인', title: '형식 확인', description: '항목 값이 정한 형식(정규식)에 맞는지 확인합니다.',
      example: '사업자등록증의 사업자등록번호가 000-00-00000 형식인지 확인',
      fields: [
        formField('leftForm', '확인할 서류', 'left', ['사업자등록증']), keyField('leftKey', '확인할 항목', 'left', ['사업자등록번호', '등록번호']),
        // ^\d{3}-\d{2}-\d{5}$ : 처음부터 끝까지 숫자 3자리-숫자 2자리-숫자 5자리(사업자등록번호 표기).
        textField('pattern', '맞아야 하는 형식(정규식)', '^\\d{3}-\\d{2}-\\d{5}$')
      ],
      methods: function (v, make) { return [[make('pattern', { patternMode: 'regex' })]]; }
    },
    {
      id: 'count', group: '조건·건수', title: '서류 건수', description: '제출된 서류가 정한 건수 안에 드는지 확인합니다.',
      example: '신분증이 1건 이상 2건 이하로 제출됐는지 확인',
      fields: [formField('leftForm', '건수를 셀 서류', 'left', identity), integerField('min', '최소 건수', 1, 0), integerField('max', '최대 건수', 2, 1)],
      validate: function (v) { return v.min > v.max ? '최소 건수는 최대 건수보다 클 수 없습니다.' : ''; },
      methods: function (v, make) { return [[make('count', {})]]; }
    },
    /* 조건부 검증: 서류가 있을 때만 규칙을 적용한다. */
    {
      id: 'when-doc', group: '조건·건수', title: '서류가 있을 때만 비교', description: '비교 대상 서류가 제출된 경우에만 값을 비교합니다.',
      example: '위임장이 제출된 경우에만 신분증 성명과 위임장 수임자 성명을 비교',
      fields: [
        formField('leftForm', '비교할 서류', 'left', identity), keyField('leftKey', '비교할 항목', 'left', NAME_KEYS),
        formField('rightForm', '있을 때만 비교할 서류(비교 대상)', 'right', ['위임장']), keyField('rightKey', '비교 대상 항목', 'right', ['수임자성명', '수임인성명', '수임자.성명', '수임인.성명'])
      ],
      when: function (v) { return { type: 'hasDoc', form: v.rightForm }; },
      methods: function (v, make) { return [[make('compare', { op: 'EQ', match: 'ANY' })]]; }
    },
    /* 숫자 비교: 계산한 값이나 합계를 비교한다. */
    {
      id: 'amount', group: '숫자', title: '금액 계산 비교', description: '비교 대상 값에 곱셈을 한 뒤 반올림해 비교합니다.',
      example: '보증서 보증한도가 약정서 적용한도 × 1.2(반올림)와 같은지 확인',
      fields: [
        formField('leftForm', '비교할 서류', 'left', ['보증서']), keyField('leftKey', '비교할 항목', 'left', ['보증한도']),
        formField('rightForm', '비교 대상 서류', 'right', ['약정서', '대출약정서']), keyField('rightKey', '비교 대상 항목', 'right', ['적용한도']),
        numberField('factor', '곱할 수', 1.2)
      ],
      methods: function (v, make) {
        return [[make('number', { op: 'EQ', match: 'ANY', rightKind: 'item', round: 'ROUND', calc: [{ op: 'MUL', kind: 'number', value: v.factor, form: v.rightForm, key: v.rightKey }] })]];
      }
    },
    {
      id: 'sum', group: '숫자', title: '지분율 합계', description: '서류 한 건 안의 항목 합계가 정한 수와 같은지 확인합니다.',
      example: '주주명부의 지분율 합계가 100인지 확인',
      fields: [formField('leftForm', '합계를 구할 서류', 'left', ['주주명부']), keyField('leftKey', '합계를 구할 항목', 'left', ['지분율', '주주.지분율', '주주명부.지분율']), numberField('targetValue', '비교할 수', 100)],
      // 저장 정의의 DOCUMENT는 제출 서류 한 건마다 합계를 구한다는 뜻이다.
      methods: function (v, make) { return [[make('aggregate', { op: 'EQ', match: 'ALL', fn: 'SUM', targetKind: 'number', scope: 'DOCUMENT' })]]; }
    },
    /* 날짜 비교: 접수일(스캔일)을 기준으로 날짜를 확인한다. */
    {
      id: 'recent-date', group: '날짜', title: '최근 3개월', description: '접수일 기준으로 정한 기간 안에 발급됐는지 확인합니다.',
      example: '등기 발급일이 접수일 이전 3개월 이내인지 확인',
      fields: [formField('leftForm', '발급일이 적힌 서류', 'left', registry), keyField('leftKey', '발급일 항목', 'left', ['발급일', '발급일자', '발행일', '발행일자']), integerField('months', '기간(개월)', 3, 1)],
      name: function (v) { return '최근 ' + v.months + '개월 확인'; },
      methods: function (v, make) { return [[make('date', { op: 'EQ', match: 'ALL', dateUnit: 'months' })]]; }
    },
    {
      id: 'adult', group: '날짜', title: '만 14세 이상', description: '생년월일이 접수일 기준 정한 나이 이전인지 확인합니다.',
      example: '신분증 생년월일이 접수일의 14년 전과 같거나 이전인지 확인',
      fields: [formField('leftForm', '생년월일이 적힌 서류', 'left', identity), keyField('leftKey', '생년월일 항목', 'left', ['생년월일', '생일']), integerField('years', '나이(세)', 14, 1)],
      name: function (v) { return '만 ' + v.years + '세 이상 확인'; },
      // 접수일에서 years년을 뺀 날짜(offset 음수 = 전) 이전에 태어났으면 그 나이 이상이다.
      methods: function (v, make) { return [[make('baseDate', { op: 'LE', dateUnit: 'years', offset: -v.years })]]; }
    },
    {
      id: 'not-expired', group: '날짜', title: '유효기간 지나지 않음', description: '만료일이 접수일과 같거나 이후인지 확인합니다.',
      example: '신분증 만료일이 접수일과 같거나 이후인지 확인',
      fields: [formField('leftForm', '만료일이 적힌 서류', 'left', identity.concat(['여권'])), keyField('leftKey', '만료일 항목', 'left', ['만료일', '유효기간만료일', '유효기간', '만료일자'])],
      // offset 0은 접수일 당일을 뜻한다.
      methods: function (v, make) { return [[make('baseDate', { op: 'GE', offset: 0 })]]; }
    }
  ];

  /** 템플릿 ID에 해당하는 정의를 반환한다. 알 수 없는 ID는 한국어 오류로 알린다. */
  function templateOf(id) {
    var found = templates.filter(function (template) { return template.id === id; })[0];
    if (!found) throw new Error('알 수 없는 시작 예시입니다.');
    return found;
  }

  /** 표시 이름의 공백과 항목 경로 구분자만 제거해 같은 뜻의 별칭을 비교한다. */
  function normalized(value) { return String(value || '').replace(/[\s._-]/g, '').toLowerCase(); }

  /** 후보가 정확히 하나일 때만 반환한다. 중복 후보는 사용자가 직접 선택하게 한다. */
  function uniqueMatch(items, names, labelOf) {
    var expected = (names || []).map(normalized);
    var matched = items.filter(function (item) { return expected.indexOf(normalized(labelOf(item))) >= 0; });
    return matched.length === 1 ? matched[0] : null;
  }

  /**
   * 현재 업무의 서식·항목 이름으로 입력값을 제안한다. 목록과 원본 객체는 바꾸지 않는다.
   * @param {string} id 템플릿 ID.
   * @param {Array} forms 현재 업무의 [{formId, formName, keys}] 목록.
   * @returns {Object} 필드별 제안값. 없거나 모호한 서식·항목은 빈 문자열이다.
   * @throws {Error} 지원하지 않는 템플릿이면 발생한다.
   */
  function suggest(id, forms) {
    var template = templateOf(id), mapping = {}, matchedForms = {};
    forms = Array.isArray(forms) ? forms : [];
    template.fields.forEach(function (field) {
      if (field.kind === 'form') {
        var found = uniqueMatch(forms, field.names, function (form) { return form.formName; });
        matchedForms[field.side] = found;
        mapping[field.name] = found ? found.formId : '';
      } else if (field.kind === 'key') {
        var form = matchedForms[field.side];
        // 서식을 확정하지 못했으면 다른 서식의 같은 이름 항목을 대신 넣지 않는다.
        mapping[field.name] = form ? uniqueMatch(form.keys || [], field.names, function (key) { return key; }) || '' : '';
      } else mapping[field.name] = field.defaultValue !== undefined ? field.defaultValue : '';
    });
    return mapping;
  }

  /**
   * 필수 입력을 확인하고 저장하지 않은 새 규칙을 만든다. 호출마다 하위 객체도 새로 만든다.
   * @param {string} id 템플릿 ID.
   * @param {Object} mapping 사용자가 확인한 필드별 입력값. 항목 키는 직접 입력도 허용한다.
   * @param {Object} def 현재 업무를 참조하는 CrossRuleSentence.create 반환값.
   * @returns {Object} 기본 편집기와 같은 새 draft. checks는 첫 방법의 비교 목록을 가리킨다.
   * @throws {Error} 필수 입력 누락, 현재 업무에 없는 서식, 유효하지 않은 숫자·범위이면 발생한다.
   */
  function createDraft(id, mapping, def) {
    var template = templateOf(id), values = {};
    mapping = mapping || {};
    template.fields.forEach(function (field) {
      var value = mapping[field.name];
      if (value === undefined || value === null || String(value).trim() === '') throw new Error(field.label + '을(를) 입력해 주세요.');
      if (field.kind === 'form') {
        value = Number(value);
        if (!Number.isInteger(value) || !def.form(value)) throw new Error(field.label + '에서 현재 업무의 서식을 선택해 주세요.');
      } else if (field.kind === 'number') {
        value = Number(value);
        if (!Number.isFinite(value)) throw new Error(field.label + '에 유효한 수를 입력해 주세요.');
        if (field.integer && (!Number.isInteger(value) || value < field.min)) throw new Error(field.label + '은(는) ' + field.min + ' 이상의 정수로 입력해 주세요.');
      } else value = String(value).trim();
      values[field.name] = value;
    });

    var message = template.validate && template.validate(values);
    if (message) throw new Error(message);

    /* 비교가 원래 가진 속성(서식·항목·기간 등)만 입력값으로 채운다. 템플릿 전용 입력(생년월일 항목·곱할 수 등)은 methods가 직접 쓴다. */
    function make(type, props) {
      var check = def.newCheck(type);
      Object.keys(values).forEach(function (name) { if (name in check) check[name] = values[name]; });
      return Object.assign(check, props);
    }
    var branch = def.newBranch('none');
    if (template.when) branch.when = Object.assign(branch.when, template.when(values));
    branch.methods = template.methods(values, make);
    // 기간·나이를 바꿔 시작하면 이름도 같은 값을 가리키게 한다.
    var name = template.name ? template.name(values) : template.title + ' 확인';
    var draft = { ruleId: null, name: name, useYn: 'Y', branches: [branch], otherwise: def.newOtherwise() };
    def.selectBranch(draft, 0);
    return draft;
  }

  window.CrossRuleStarters = { templates: templates, suggest: suggest, createDraft: createDraft };
})();
