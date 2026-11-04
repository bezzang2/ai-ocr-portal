/* 자주 쓰는 검증의 시작점. 현재 업무의 서식만 제안하고 저장하지 않은 새 편집 상태를 만든다. */
(function () {
  'use strict';

  var templates = [
    {
      id: 'compare', title: '성명 비교', description: '두 서류의 항목 값이 같은지 확인합니다.',
      example: '신분증 성명과 위임장 수임자 성명이 같은지 확인',
      fields: [
        { name: 'leftForm', label: '비교할 서류', kind: 'form', side: 'left' },
        { name: 'leftKey', label: '비교할 항목', kind: 'key', side: 'left' },
        { name: 'rightForm', label: '대조할 서류', kind: 'form', side: 'right' },
        { name: 'rightKey', label: '대조할 항목', kind: 'key', side: 'right' }
      ]
    },
    {
      id: 'representative', title: '대표 행 선택', description: '대표자인 행을 골라 성명을 비교합니다.',
      example: '신분증 성명과 등기의 대표자 행 성명이 같은지 확인',
      fields: [
        { name: 'leftForm', label: '비교할 서류', kind: 'form', side: 'left' },
        { name: 'leftKey', label: '성명 항목', kind: 'key', side: 'left' },
        { name: 'rightForm', label: '대표자가 적힌 서류', kind: 'form', side: 'right' },
        { name: 'rightKey', label: '대조할 성명 항목', kind: 'key', side: 'right' },
        { name: 'conditionKey', label: '직위 항목', kind: 'key', side: 'right' },
        { name: 'conditionValue', label: '대표자를 뜻하는 값', kind: 'text', defaultValue: '대표자' }
      ]
    },
    {
      id: 'recent-date', title: '최근 3개월', description: '접수일 기준으로 정한 기간 안에 발급됐는지 확인합니다.',
      example: '등기 발급일이 접수일 이전 3개월 이내인지 확인',
      fields: [
        { name: 'leftForm', label: '발급일이 적힌 서류', kind: 'form', side: 'left' },
        { name: 'leftKey', label: '발급일 항목', kind: 'key', side: 'left' },
        { name: 'months', label: '기간(개월)', kind: 'number', defaultValue: 3, min: 1, step: 1 }
      ]
    },
    {
      id: 'sum', title: '지분율 합계', description: '서류 한 건 안의 항목 합계가 정한 수와 같은지 확인합니다.',
      example: '주주명부의 지분율 합계가 100인지 확인',
      fields: [
        { name: 'leftForm', label: '합계를 구할 서류', kind: 'form', side: 'left' },
        { name: 'leftKey', label: '합계를 구할 항목', kind: 'key', side: 'left' },
        { name: 'targetValue', label: '비교할 수', kind: 'number', defaultValue: 100, step: 'any' }
      ]
    }
  ];

  // 이름이 명확히 같은 경우만 제안한다. 일부 글자가 포함된 서류를 임의로 고르지 않는다.
  var identity = ['신분증', '주민등록증', '운전면허증'];
  var registry = ['등기', '등기부', '등기부등본', '법인등기부', '법인등기부등본', '등기사항전부증명서', '법인등기사항전부증명서'];
  var aliases = {
    compare: { leftForm: identity, leftKey: ['성명', '이름'], rightForm: ['위임장'], rightKey: ['수임자성명', '수임인성명', '수임자.성명', '수임인.성명'] },
    representative: { leftForm: identity, leftKey: ['성명', '이름'], rightForm: registry, rightKey: ['임원.성명', '임원명부.성명', '임원에관한사항.성명'], conditionKey: ['임원.직위', '임원명부.직위', '임원에관한사항.직위'] },
    'recent-date': { leftForm: registry, leftKey: ['발급일', '발급일자', '발행일', '발행일자'] },
    sum: { leftForm: ['주주명부'], leftKey: ['지분율', '주주.지분율', '주주명부.지분율'] }
  };

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
    var template = templateOf(id), names = aliases[id], mapping = {}, matchedForms = {};
    forms = Array.isArray(forms) ? forms : [];
    template.fields.forEach(function (field) {
      if (field.kind === 'form') {
        var found = uniqueMatch(forms, names[field.name], function (form) { return form.formName; });
        matchedForms[field.side] = found;
        mapping[field.name] = found ? found.formId : '';
      } else if (field.kind === 'key') {
        var form = matchedForms[field.side];
        // 서식을 확정하지 못했으면 다른 서식의 같은 이름 항목을 대신 넣지 않는다.
        mapping[field.name] = form ? uniqueMatch(form.keys || [], names[field.name], function (key) { return key; }) || '' : '';
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
   * @throws {Error} 필수 입력 누락, 현재 업무에 없는 서식, 유효하지 않은 숫자이면 발생한다.
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
        if (field.name === 'months' && (!Number.isInteger(value) || value < 1)) throw new Error('기간은 1 이상의 정수로 입력해 주세요.');
      } else value = String(value).trim();
      values[field.name] = value;
    });

    var type = id === 'recent-date' ? 'date' : id === 'sum' ? 'aggregate' : 'compare';
    var check = def.newCheck(type);
    Object.keys(values).forEach(function (name) { check[name] = values[name]; });
    check.op = 'EQ';
    check.match = id === 'recent-date' || id === 'sum' ? 'ALL' : 'ANY';
    if (id === 'representative') {
      check.condition = true;
      check.conditionOp = 'EQ';
    } else if (id === 'recent-date') check.dateUnit = 'months';
    else if (id === 'sum') {
      check.fn = 'SUM';
      check.targetKind = 'number';
      // 저장 정의의 DOCUMENT는 제출 서류 한 건마다 합계를 구한다는 뜻이다.
      check.scope = 'DOCUMENT';
    }
    var branch = def.newBranch('none');
    branch.methods = [[check]];
    // 기간을 바꿔 시작하면 이름도 같은 기간을 가리키게 한다.
    var name = id === 'recent-date' ? '최근 ' + values.months + '개월 확인' : template.title + ' 확인';
    var draft = { ruleId: null, name: name, useYn: 'Y', branches: [branch], otherwise: def.newOtherwise() };
    def.selectBranch(draft, 0);
    return draft;
  }

  window.CrossRuleStarters = { templates: templates, suggest: suggest, createDraft: createDraft };
})();
