/* 저장 정의(DEF_JSON)와 편집 상태를 서로 바꾸고 규칙 문장을 만든다. 규칙 관리와 서식간 검증 결과 상세가 함께 쓴다. 문장은 저장하지 않는다. */
(function () {
  'use strict';
  /* ctx.forms(): 서식 목록 [{formId, formName, keys}]. ctx.keysOf(formId): 직접 입력 항목까지 더한 항목 목록(없으면 서식의 keys) */
  function create(ctx) {
    var OPS = {
      compare: [['EQ', '같다'], ['NE', '다르다'], ['CONTAINS', '포함한다']],
      fixed: [['EQ', '같다'], ['NE', '다르다'], ['CONTAINS', '포함한다']],
      list: [['IN_LIST', '있다'], ['NOT_IN_LIST', '없다']],
      number: [['EQ', '같다'], ['GT', '크다'], ['LT', '작다'], ['GE', '이상'], ['LE', '이하']],
      dateCompare: [['GT', '이후이다'], ['GE', '같거나 이후이다'], ['LT', '이전이다'], ['LE', '같거나 이전이다'], ['EQ', '같은 날이다']],
      baseDate: [['GE', '같거나 이후이다'], ['GT', '이후이다'], ['LE', '같거나 이전이다'], ['LT', '이전이다'], ['EQ', '같은 날이다']],
      presence: [['NOT_EMPTY', '값이 있다'], ['EMPTY', '값이 없다']],
      aggregate: [['EQ', '같다'], ['GT', '크다'], ['LT', '작다'], ['GE', '이상'], ['LE', '이하']]
    };
    /* 조건 칸의 비교 방법. '목록 중 하나'는 값을 쉼표로 나눠 받는다 */
    var CONDITION_OPS = [['EQ', '인'], ['NE', '이(가) 아닌'], ['CONTAINS', '을(를) 포함하는'], ['IN', '중 하나인(쉼표로 구분)']];
    var AGGREGATE_FNS = [['SUM', '합계'], ['COUNT', '개수'], ['MAX', '최댓값'], ['MIN', '최솟값']];
    var DATE_UNITS = [['months', '개월'], ['days', '일'], ['years', '년']];
    var SORT_ORDERS = [['DESC', '큰 순'], ['ASC', '작은 순']];
    var SORT_TYPES = [['NUMBER', '숫자'], ['DATE', '날짜']];
    function labelOf(list, code) { return (list.filter(function (x) { return x[0] === code; })[0] || ['', ''])[1]; }
    var CALC_OPS = [['MUL', '×'], ['DIV', '÷'], ['ADD', '+'], ['SUB', '−']];
    var PICK_OPS = [['EQ', '같은'], ['NE', '다른'], ['CONTAINS', '포함하는']];
    var RIGHT_ITEM_TYPES = ['compare', 'list', 'number', 'dateCompare'];
    function form(formId) { return ctx.forms().filter(function (f) { return f.formId === Number(formId); })[0]; }
    function formName(formId) { var f = form(formId); return f ? f.formName : '(연결 해제된 서식)'; }
    function keysOf(formId) { if (ctx.keysOf) return ctx.keysOf(formId); var f = form(formId); return f && f.keys || []; }
    function firstKey(formId, preferred) {
      var keys = keysOf(formId);
      return keys.indexOf(preferred) >= 0 ? preferred : keys[0] || '';
    }
    function newStep(formId) { return { op: 'MUL', kind: 'number', value: 1, form: formId, key: firstKey(formId) }; }
    function newSort(formId) { return { by: firstKey(formId), order: 'DESC', as: 'NUMBER', limit: 1 }; }
    function newPick(formId) {
      var other = ctx.forms()[0] ? ctx.forms()[0].formId : null;
      return { key: firstKey(formId), op: 'EQ', kind: 'text', value: '', form: other, valueKey: firstKey(other) };
    }
    function newCheck(type) {
      var a = ctx.forms()[0], b = ctx.forms()[1] || a, left = a ? a.formId : null, right = b ? b.formId : null;
      return {
        type: type, leftForm: left, leftKey: firstKey(left), rightForm: right, rightKey: firstKey(right),
        op: type === 'list' ? 'IN_LIST' : type === 'dateCompare' || type === 'baseDate' ? 'GE' : type === 'presence' ? 'NOT_EMPTY' : 'EQ', fixed: '', months: 3, min: 1, max: 1,
        // 집계 비교의 조건은 왼쪽(집계 대상) 서류에 걸린다. 다른 유형은 오른쪽(대조할) 서류에 걸린다.
        offset: 0, dateUnit: 'months', leftSort: null, rightSort: null, sliceOn: false, sliceStart: 7, sliceLength: 1, patternMode: 'regex', pattern: '', valueList: '',
        fn: 'SUM', aggregateAs: 'NUMBER', scope: 'DOCUMENT', targetKind: 'number', targetValue: 100, rightFn: 'SUM',
        calc: [newStep(right)], round: 'NONE', tolerance: 0, tolerancePct: 0, extras: [], leftExtras: [], pairs: [], leftPick: null, rightPick: null, leftRole: null, rightRole: null,
        useDocCount: false, docMin: 1, docMax: 1,
        condition: false, conditionKey: firstKey(type === 'aggregate' ? left : right), conditionOp: 'EQ', conditionValue: '',
        match: type === 'list' || type === 'presence' || type === 'pattern' ? 'ALL' : 'ANY', normalize: ['IGNORE_SPACE', 'IGNORE_HYPHEN']
      };
    }
    // 편집 캐시는 객체에 붙이지 않는다. 같은 비교가 새 객체로 바뀌어도 계보만 이어 저장 JSON과 분리한다.
    var checkTypeStates = new WeakMap();
    var CHECK_COMMON_FIELDS = ['id', 'leftForm', 'leftKey', 'rightForm', 'rightKey', 'match', 'normalize'];
    var CHECK_SIDE_FIELDS = ['leftPick', 'leftRole', 'rightPick', 'rightRole', 'leftSort', 'rightSort', 'leftExtras', 'extras', 'pairs', 'condition', 'conditionKey', 'conditionOp', 'conditionValue', 'useDocCount', 'docMin', 'docMax'];
    var ITEM_SIDE_FIELDS = ['leftPick', 'leftRole'];
    var FAMILY_SIDE_FIELDS = CHECK_SIDE_FIELDS;
    var CHECK_TYPE_FIELDS = {
      compare: FAMILY_SIDE_FIELDS,
      list: FAMILY_SIDE_FIELDS,
      number: FAMILY_SIDE_FIELDS.filter(function (key) { return key !== 'pairs'; }).concat(['calc', 'round', 'tolerance', 'tolerancePct']),
      dateCompare: FAMILY_SIDE_FIELDS.filter(function (key) { return key !== 'pairs'; }),
      fixed: ITEM_SIDE_FIELDS.concat(['fixed']),
      date: ITEM_SIDE_FIELDS.concat(['months', 'dateUnit']),
      count: ['min', 'max'],
      presence: ITEM_SIDE_FIELDS,
      baseDate: ITEM_SIDE_FIELDS.concat(['offset', 'dateUnit']),
      pattern: ITEM_SIDE_FIELDS.concat(['sliceOn', 'sliceStart', 'sliceLength', 'patternMode', 'pattern', 'valueList']),
      aggregate: ITEM_SIDE_FIELDS.concat(['leftSort', 'condition', 'conditionKey', 'conditionOp', 'conditionValue', 'fn', 'aggregateAs', 'scope', 'targetKind', 'targetValue', 'rightFn', 'tolerance', 'tolerancePct', 'offset', 'dateUnit'])
    };
    /* 배열과 하위 객체도 복사해 다른 유형의 미완성 입력이나 계산 순서가 캐시를 바꾸지 않게 한다. */
    function copyCheckValue(value) {
      if (Array.isArray(value)) return value.map(copyCheckValue);
      if (value && typeof value === 'object') {
        var copy = {};
        Object.keys(value).forEach(function (key) { copy[key] = copyCheckValue(value[key]); });
        return copy;
      }
      return value;
    }
    /**
     * 비교 유형을 바꾸고 같은 비교에서 앞서 입력한 유형별 설정을 복원한다.
     * @param {Object} check newCheck 또는 fromDef가 만든 편집 중 비교. 원본은 변경하지 않는다.
     * @param {string} type 새 비교 유형. 같은 유형이면 원본 객체를 그대로 반환한다.
     * @returns {Object} 최신 서식·항목·일치 방식과 새 유형의 설정을 가진 비교 객체. 캐시는 저장하지 않는다.
     * @throws {Error} 지원하지 않는 비교 유형이면 발생한다.
     */
    function retypeCheck(check, type) {
      if (check.type === type) return check;
      if (!Object.prototype.hasOwnProperty.call(CHECK_TYPE_FIELDS, type)) throw new Error('지원하지 않는 비교 유형입니다: ' + type);
      var states = checkTypeStates.get(check) || {};
      states[check.type] = copyCheckValue(check);
      var next = newCheck(type), saved = states[type], fields = CHECK_TYPE_FIELDS[type];
      if (saved) {
        // 해당 유형에서 쓰는 값만 복원한다. 숨은 조건·정렬·고르기가 다른 유형의 검증을 막지 않게 한다.
        fields.concat(['op']).forEach(function (key) { next[key] = copyCheckValue(saved[key]); });
      } else {
        var previousFields = CHECK_TYPE_FIELDS[check.type] || [];
        CHECK_SIDE_FIELDS.forEach(function (key) {
          // 집계 조건은 왼쪽, 항목 비교 조건은 오른쪽에 걸리므로 서로 옮기지 않는다.
          var conditionSideChanged = key.indexOf('condition') === 0 && (check.type === 'aggregate' || type === 'aggregate');
          if (!conditionSideChanged && fields.indexOf(key) >= 0 && previousFields.indexOf(key) >= 0) next[key] = copyCheckValue(check[key]);
        });
        if (OPS[type] && OPS[type].some(function (op) { return op[0] === check.op; })) next.op = check.op;
      }
      CHECK_COMMON_FIELDS.forEach(function (key) {
        if (Object.prototype.hasOwnProperty.call(check, key)) next[key] = copyCheckValue(check[key]);
      });
      if (saved) ['left', 'right'].forEach(function (side) {
        var formId = next[side + 'Form'];
        if (saved[side + 'Form'] === formId) return;
        // 다른 유형에서 서식을 바꿨다면 화면의 서식 변경과 같이 이전 역할을 해제하고 종속 항목을 보정한다.
        // 집계의 행 조건은 왼쪽, 일반 비교의 행 조건은 오른쪽 서식에 속한다.
        next[side + 'Role'] = null;
        if (next[side + 'Pick']) next[side + 'Pick'].key = firstKey(formId, next[side + 'Pick'].key);
        if (next[side + 'Sort']) next[side + 'Sort'].by = firstKey(formId, next[side + 'Sort'].by);
        if (side === (type === 'aggregate' ? 'left' : 'right')) next.conditionKey = firstKey(formId, next.conditionKey);
      });
      checkTypeStates.set(next, states);
      return next;
    }
    var WHEN_TYPES = [['none', '항상'], ['hasDoc', '서류가 있을 때'], ['noDoc', '서류가 없을 때'], ['value', '항목 값이 맞을 때']];
    var WHEN_OPS = [['EQ', '이다'], ['NE', '이(가) 아니다'], ['CONTAINS', '을(를) 포함한다'], ['NOT_EMPTY', '값이 있다'], ['EMPTY', '값이 없다']];
    function newWhen(type) {
      var formId = ctx.forms()[0] ? ctx.forms()[0].formId : null;
      return { type: type, form: formId, key: firstKey(formId), op: 'EQ', value: '' };
    }
    /* 경우마다 적용 조건(when + andWhen, 모두 맞아야 함)과 통과 방법(methods)이 있다. 방법마다 비교 목록이 있고, 방법이 하나면 일반 규칙이다 */
    function newBranch(whenType) { return { label: '', when: newWhen(whenType), andWhen: [], methods: [[newCheck('compare')]], activeMethod: 0 }; }
    function newOtherwise() { return { on: false, methods: [[newCheck('compare')]], activeMethod: 0 }; }
    /* 경우 수 위치(branches.length)는 '그 외'다 */
    function activeCase(d) { return d.activeBranch < d.branches.length ? d.branches[d.activeBranch] : d.otherwise; }
    /* 편집 중인 비교 목록은 d.checks 다. 경우나 방법을 옮길 때 그 목록을 가리키게 한다 */
    function selectMethod(d, n) {
      var c = activeCase(d);
      c.activeMethod = n;
      d.checks = c.methods[n];
    }
    function selectBranch(d, n) {
      d.activeBranch = n;
      selectMethod(d, 0);
    }
    function allChecks(d) {
      var list = [];
      function add(c) { c.methods.forEach(function (m) { list = list.concat(m); }); }
      d.branches.forEach(add);
      if (d.otherwise.on) add(d.otherwise);
      return list;
    }
    function checkById(d, id) { return allChecks(d).filter(function (c) { return c.id === id; })[0]; }
    var PAIR_TYPES = ['compare', 'list'];
    function pairKeys(c, side) { return [c[side + 'Key']].concat(PAIR_TYPES.indexOf(c.type) >= 0 ? c.pairs.map(function (p) { return p[side]; }) : []); }
    function negativeOp(c) { return c.op === 'NE' || c.op === 'NOT_IN_LIST'; }
    function fromDef(rule) {
      var def = JSON.parse(rule.defJson);
      function sourceOf(alias) { return def.sources.filter(function (s) { return s.alias === alias; })[0]; }
      function formOf(alias) { return sourceOf(alias).formId; }
      function roleOf(alias) {
        var s = sourceOf(alias);
        return s.role ? { name: s.role } : null;
      }
      function pickOf(alias) {
        var w = (sourceOf(alias).documentWhere || [])[0];
        if (!w) return null;
        var pick = { key: w.left.keys[0], op: w.op, kind: w.right.kind === 'item' ? 'item' : 'text', value: '', form: ctx.forms()[0] && ctx.forms()[0].formId, valueKey: '' };
        if (pick.kind === 'item') { pick.form = formOf(w.right.source); pick.valueKey = w.right.keys[0]; } else { pick.value = w.right.value; }
        return pick;
      }
      function itemsOf(operand) { return operand.kind === 'union' ? operand.items : [operand]; }
      function sortOf(operand) {
        return operand.sort ? { by: operand.sort.by, order: operand.sort.order || 'DESC', as: operand.sort.as || 'NUMBER', limit: operand.limit } : null;
      }
      /* 저장 형식은 where 하나당 op 와 값이다. 편집 상태는 IN 목록을 쉼표로 이어 한 칸에 보여 준다 */
      function readCondition(c, operand, formId) {
        var w = (operand.where || [])[0];
        if (!w) { c.conditionKey = firstKey(formId); return; }
        c.condition = true;
        c.conditionKey = w.key;
        c.conditionOp = w.op || 'EQ';
        c.conditionValue = w.op === 'IN' ? (w.values || []).join(', ') : w.value.value;
      }
      function offsetOf(c, offset) {
        offset = offset || {};
        c.dateUnit = offset.days ? 'days' : offset.years ? 'years' : 'months';
        c.offset = offset.days || offset.years || offset.months || 0;
      }
      /* 고르기·역할은 비교할 항목 쪽 서류 설정이다. 집계·자릿수 안쪽 항목에도 같은 방식으로 읽는다 */
      function readLeft(c, item) {
        c.leftForm = formOf(item.source);
        c.leftKey = item.keys[0];
        c.leftPick = pickOf(item.source);
        c.leftRole = roleOf(item.source);
        c.leftSort = sortOf(item);
      }
      function checksOf(list) {
      var checks = [];
      list.forEach(function (raw) {
        var c = newCheck(raw.uiType);
        c.id = raw.id;
        if (raw.rightDocCount) { c.useDocCount = true; c.docMin = raw.rightDocCount.min; c.docMax = raw.rightDocCount.max; }
        c.match = raw.match || 'ANY';
        c.normalize = raw.normalize || [];
        c.op = raw.op;
        if (raw.uiType === 'presence') { readLeft(c, raw.left); checks.push(c); return; }
        if (raw.uiType === 'baseDate') { readLeft(c, raw.left); offsetOf(c, raw.right.offset); checks.push(c); return; }
        if (raw.uiType === 'pattern') {
          c.sliceOn = raw.left.kind === 'slice';
          if (c.sliceOn) { c.sliceStart = raw.left.start; c.sliceLength = raw.left.length; }
          readLeft(c, c.sliceOn ? raw.left.of : raw.left);
          c.patternMode = raw.op === 'MATCHES' ? 'regex' : 'list';
          if (raw.op === 'MATCHES') c.pattern = raw.right.value;
          else c.valueList = raw.right.items.map(function (x) { return x.value; }).join(', ');
          checks.push(c);
          return;
        }
        if (raw.uiType === 'aggregate') {
          c.fn = raw.left.fn;
          c.aggregateAs = raw.left.as || 'NUMBER';
          c.scope = raw.left.scope || 'DOCUMENT';
          readLeft(c, raw.left.of);
          readCondition(c, raw.left.of, c.leftForm);
          c.tolerance = raw.tolerance || 0;
          c.tolerancePct = raw.tolerancePct || 0;
          c.targetKind = raw.right.kind === 'aggregate' ? 'aggregate' : raw.right.kind === 'item' ? 'item' : raw.right.kind === 'baseDate' ? 'baseDate' : 'number';
          if (raw.right.kind === 'aggregate') { c.rightFn = raw.right.fn; c.rightForm = formOf(raw.right.of.source); c.rightKey = raw.right.of.keys[0]; }
          else if (raw.right.kind === 'item') { c.rightForm = formOf(raw.right.source); c.rightKey = raw.right.keys[0]; }
          else if (raw.right.kind === 'baseDate') { offsetOf(c, raw.right.offset); }
          else c.targetValue = raw.right.value;
          checks.push(c);
          return;
        }
        var lefts = itemsOf(raw.left);
        c.leftForm = formOf(lefts[0].source);
        c.leftKey = lefts[0].keys ? lefts[0].keys[0] : '';
        if (lefts[0].kind === 'item') { c.leftPick = pickOf(lefts[0].source); c.leftRole = roleOf(lefts[0].source); c.leftSort = sortOf(lefts[0]); }
        c.leftExtras = lefts.slice(1).map(function (x) { return { form: formOf(x.source), key: x.keys[0] }; });
        if (raw.uiType === 'count') { c.min = raw.low.value; c.max = raw.high.value; }
        else if (raw.uiType === 'date') {
          // 옛 규칙은 months 만 있다. 일·년 단위도 같은 칸(c.months)에 양수로 보여 준다.
          offsetOf(c, raw.low.offset);
          c.months = -c.offset;
        }
        else if (raw.uiType === 'fixed') c.fixed = raw.right.value;
        else {
          var right = raw.right;
          if (right.kind === 'calc') {
            c.round = right.round || 'NONE';
            c.tolerance = raw.tolerance || 0;
            c.tolerancePct = raw.tolerancePct || 0;
            c.calc = [];
            while (right.kind === 'calc') {
              c.calc.unshift(right.right.kind === 'item'
                ? { op: right.op, kind: 'item', value: 1, form: formOf(right.right.source), key: right.right.keys[0] }
                : { op: right.op, kind: 'number', value: right.right.value, form: c.rightForm, key: firstKey(c.rightForm) });
              right = right.left;
            }
          }
          if (right.kind === 'union') {
            c.extras = right.items.slice(1).map(function (x) { return { form: formOf(x.source), key: x.keys[0] }; });
            right = right.items[0];
          }
          c.rightSort = sortOf(right);
          c.rightPick = pickOf(right.source);
          c.rightRole = roleOf(right.source);
          c.rightForm = formOf(right.source);
          c.rightKey = right.keys[0];
          c.pairs = right.keys.slice(1).map(function (key, n) { return { left: lefts[0].keys[n + 1], right: key }; });
          readCondition(c, right, c.rightForm);
        }
        checks.push(c);
      });
      return checks;
      }
      function whenOf(raw) {
        var w = newWhen('none');
        if (!raw) return w;
        w.form = formOf(raw.left.source);
        if (raw.left.kind === 'docCount') { w.type = raw.op === 'LE' ? 'noDoc' : 'hasDoc'; return w; }
        w.type = 'value'; w.key = raw.left.keys[0]; w.op = raw.op; w.value = raw.right ? raw.right.value : '';
        return w;
      }
      /* 조건은 비교 하나 또는 {all:[비교...]} 로 저장한다 */
      function whensOf(raw) { return raw && raw.all ? raw.all.map(whenOf) : [whenOf(raw)]; }
      /* 묶음은 경우의 비교 전체일 때만 저장되므로 첫 원소만 본다 */
      function methodsOf(list) { return list[0].any ? list[0].any.map(function (m) { return checksOf(m.all); }) : [checksOf(list)]; }
      function branchOf(x) {
        var whens = whensOf(x.when);
        return { label: x.label || '', when: whens[0], andWhen: whens.slice(1), methods: methodsOf(x.checks), activeMethod: 0 };
      }
      var other = def.otherwise && def.otherwise.checks;
      var d = {
        ruleId: rule.ruleId, name: rule.ruleNm, useYn: rule.useYn,
        branches: def.cases.map(branchOf),
        otherwise: other ? { on: true, methods: methodsOf(other), activeMethod: 0 } : newOtherwise()
      };
      selectBranch(d, 0);
      return d;
    }
    /* 쉼표로 적은 값 목록. 빈 칸은 뺀다 */
    function splitList(text) {
      return String(text).split(',').map(function (x) { return x.trim(); }).filter(Boolean);
    }

    function toDef(draft) {
      var sources = [], seen = {};
      // 서류 고르기나 역할이 다르면 같은 서식도 다른 별칭으로 쓴다.
      function source(formId, pick, role) {
        var where = pick ? [{
          left: { kind: 'item', source: '$self', keys: [pick.key] }, op: pick.op,
          right: pick.kind === 'item' ? item(pick.form, pick.valueKey) : { kind: 'text', value: pick.value },
          normalize: ['IGNORE_SPACE', 'IGNORE_HYPHEN']
        }] : null;
        var id = formId + JSON.stringify(where) + JSON.stringify(role || null);
        if (!seen[id]) {
          var entry = { alias: 's' + formId + (where || role ? '_' + sources.length : ''), formId: Number(formId) };
          seen[id] = entry.alias;
          if (where) entry.documentWhere = where;
          if (role) entry.role = role.name;
          sources.push(entry);
        }
        return seen[id];
      }
      function item(formId, key, where, pick, role, sort) {
        var op = { kind: 'item', source: source(formId, pick, role), keys: Array.isArray(key) ? key : [key] };
        if (where) op.where = where;
        if (sort) { op.sort = { by: sort.by, order: sort.order, as: sort.as }; op.limit = sort.limit; }
        return op;
      }
      /* 조건 칸 하나를 저장 형식으로 바꾼다. IN 은 쉼표로 나눈 목록이다 */
      function whereOf(c) {
        if (!c.condition) return undefined;
        if (c.conditionOp === 'IN') return [{ key: c.conditionKey, op: 'IN', values: splitList(c.conditionValue) }];
        return [{ key: c.conditionKey, op: c.conditionOp, value: { kind: 'text', value: c.conditionValue } }];
      }
      /* 기준일에서 옮기는 값. 음수는 전, 양수는 후다 */
      function offsetDef(c) {
        var offset = {};
        if (c.offset) offset[c.dateUnit] = c.offset;
        return offset;
      }
      var number = 0;
      function buildChecks(list) {
      var checks = [];
      list.forEach(function (c) {
        var id = c.id = 'c' + (++number);
        var usesCount = c.useDocCount && RIGHT_ITEM_TYPES.indexOf(c.type) >= 0 && !negativeOp(c);
        var common = { uiType: c.type, match: c.match, rightDocCount: usesCount ? { min: c.docMin, max: c.docMax } : null, normalize: c.normalize };
        if (c.type === 'count') {
          checks.push(Object.assign({}, common, { id: id, left: { kind: 'docCount', source: source(c.leftForm) }, op: 'BETWEEN', low: { kind: 'number', value: c.min }, high: { kind: 'number', value: c.max }, compareAs: 'NUMBER', match: 'ANY' }));
        } else if (c.type === 'presence') {
          checks.push(Object.assign({}, common, { id: id, left: item(c.leftForm, c.leftKey, null, c.leftPick, c.leftRole), op: c.op, compareAs: 'TEXT' }));
        } else if (c.type === 'baseDate') {
          checks.push(Object.assign({}, common, { id: id, left: item(c.leftForm, c.leftKey, null, c.leftPick, c.leftRole), op: c.op, right: { kind: 'baseDate', offset: offsetDef(c) }, compareAs: 'DATE' }));
        } else if (c.type === 'pattern') {
          var target = item(c.leftForm, c.leftKey, null, c.leftPick, c.leftRole);
          var patternLeft = c.sliceOn ? { kind: 'slice', of: target, start: c.sliceStart, length: c.sliceLength } : target;
          checks.push(Object.assign({}, common, c.patternMode === 'regex'
            ? { id: id, left: patternLeft, op: 'MATCHES', right: { kind: 'text', value: c.pattern }, compareAs: 'TEXT' }
            : { id: id, left: patternLeft, op: 'IN_LIST', right: { kind: 'union', items: splitList(c.valueList).map(function (x) { return { kind: 'text', value: x }; }) }, compareAs: 'TEXT' }));
        } else if (c.type === 'aggregate') {
          var as = c.fn === 'MAX' || c.fn === 'MIN' ? c.aggregateAs : 'NUMBER';
          var aggregateLeft = { kind: 'aggregate', fn: c.fn, as: as, scope: c.scope, of: item(c.leftForm, c.leftKey, whereOf(c), c.leftPick, c.leftRole, c.leftSort) };
          var aggregateRight = c.targetKind === 'item' ? item(c.rightForm, c.rightKey)
            : c.targetKind === 'aggregate' ? { kind: 'aggregate', fn: c.rightFn, as: as, scope: c.scope, of: item(c.rightForm, c.rightKey) }
            : c.targetKind === 'baseDate' ? { kind: 'baseDate', offset: offsetDef(c) } : { kind: 'number', value: c.targetValue };
          checks.push(Object.assign({}, common, { id: id, left: aggregateLeft, op: c.op, right: aggregateRight, compareAs: as, tolerance: c.tolerance, tolerancePct: c.tolerancePct }));
        } else if (c.type === 'date') {
          var low = {};
          low[c.dateUnit] = -c.months;
          checks.push(Object.assign({}, common, { id: id, left: item(c.leftForm, c.leftKey, null, c.leftPick, c.leftRole), op: 'BETWEEN', low: { kind: 'baseDate', offset: low }, high: { kind: 'baseDate' }, compareAs: 'DATE' }));
        } else if (c.type === 'fixed') {
          checks.push(Object.assign({}, common, { id: id, left: item(c.leftForm, c.leftKey, null, c.leftPick, c.leftRole), op: c.op, right: { kind: 'text', value: c.fixed }, compareAs: 'TEXT' }));
        } else {
          var right = item(c.rightForm, pairKeys(c, 'right'), whereOf(c), c.rightPick, c.rightRole, c.rightSort);
          if (c.extras.length) right = { kind: 'union', items: [right].concat(c.extras.map(function (x) { return item(x.form, x.key); })) };
          if (c.type === 'number') {
            c.calc.forEach(function (step) {
              right = { kind: 'calc', op: step.op, left: right, right: step.kind === 'item' ? item(step.form, step.key) : { kind: 'number', value: step.value } };
            });
            right.round = c.round;
          }
          var left = item(c.leftForm, pairKeys(c, 'left'), null, c.leftPick, c.leftRole, c.leftSort);
          if (c.leftExtras.length) left = { kind: 'union', items: [left].concat(c.leftExtras.map(function (x) { return item(x.form, x.key); })) };
          var check = Object.assign({}, common, { id: id, left: left, op: c.op, right: right, compareAs: c.type === 'number' ? 'NUMBER' : c.type === 'dateCompare' ? 'DATE' : 'TEXT' });
          if (c.type === 'number') { check.tolerance = c.tolerance; check.tolerancePct = c.tolerancePct; }
          checks.push(check);
        }
      });
      return checks;
      }
      function whenDef(w) {
        if (w.type === 'hasDoc') return { left: { kind: 'docCount', source: source(w.form) }, op: 'GE', right: { kind: 'number', value: 1 }, compareAs: 'NUMBER' };
        if (w.type === 'noDoc') return { left: { kind: 'docCount', source: source(w.form) }, op: 'LE', right: { kind: 'number', value: 0 }, compareAs: 'NUMBER' };
        if (w.type === 'value' && (w.op === 'EMPTY' || w.op === 'NOT_EMPTY')) return { left: item(w.form, w.key), op: w.op, compareAs: 'TEXT', match: 'ANY' };
        if (w.type === 'value') return { left: item(w.form, w.key), op: w.op, right: { kind: 'text', value: w.value }, compareAs: 'TEXT', match: 'ANY', normalize: ['IGNORE_SPACE', 'IGNORE_HYPHEN'] };
        return null;
      }
      /* 조건이 하나면 비교 그대로, 둘 이상이면 {all:[...]} 로 저장해 기존 규칙의 저장 내용을 바꾸지 않는다 */
      function whensDef(b) {
        var list = [b.when].concat(b.andWhen).map(whenDef).filter(Boolean);
        return list.length > 1 ? { all: list } : list[0] || null;
      }
      var groupNumber = 0;
      /* 방법이 하나면 일반 비교 목록이다. 둘 이상이면 경우의 비교 전체가 묶음 하나다 */
      function buildCase(c) {
        if (c.methods.length === 1) return buildChecks(c.methods[0]);
        var group = ++groupNumber;
        return [{ id: 'g' + group, uiType: 'anyGroup', any: c.methods.map(function (m, n) { return { id: 'g' + group + 'm' + (n + 1), all: buildChecks(m) }; }) }];
      }
      var cases = draft.branches.map(function (b) { return { label: b.label.trim() || null, when: whensDef(b), checks: buildCase(b) }; });
      var otherwise = draft.otherwise.on ? { label: '그 외', checks: buildCase(draft.otherwise) } : { result: 'NOT_APPLICABLE' };
      return { schemaVersion: 1, sources: sources, cases: cases, otherwise: otherwise };
    }

    /* 마지막 글자의 받침으로 조사를 고른다. 한글이 아니면 숫자·영문도 함께 읽히도록 두 형태를 적는다 */
    function josa(word, withFinal, withoutFinal) {
      /* 조사는 따옴표 앞 글자의 받침으로 고른다: "한빛"과 */
      var bare = String(word).replace(/"+$/, '');
      var code = bare.charCodeAt(bare.length - 1) - 0xAC00;
      if (code < 0 || code > 11171) return word + withFinal + '(' + withoutFinal + ')';
      return word + (code % 28 ? withFinal : withoutFinal);
    }
    function roleText(role) { return role && role.name.trim() ? '[' + role.name.trim() + ']' : ''; }
    function pickText(pick, formId) {
      if (!pick) return formName(formId);
      var target = pick.kind === 'item' ? formName(pick.form) + '의 ' + pick.valueKey : '"' + pick.value + '"';
      var label = PICK_OPS.filter(function (o) { return o[0] === pick.op; })[0][1];
      var joined = pick.op === 'CONTAINS' ? josa(target, '을', '를') : josa(target, '과', '와');
      return formName(formId) + '(' + pick.key + ' 값이 ' + joined + ' ' + label + ' 서류)';
    }
    /* 계산은 왼쪽부터 차례로 한다. 곱하기·나누기가 더하기·빼기 뒤에 오면 괄호로 순서를 보여 준다 */
    function calcText(base, c) {
      var text = base, low = false;
      c.calc.forEach(function (step) {
        var sign = CALC_OPS.filter(function (o) { return o[0] === step.op; })[0][1];
        var isLow = step.op === 'ADD' || step.op === 'SUB';
        if (low && !isLow) text = '(' + text + ')';
        text += ' ' + sign + ' ' + (step.kind === 'item' ? formName(step.form) + '의 ' + step.key : step.value);
        low = isLow;
      });
      return text;
    }
    function sentence(c) {
      var text = baseSentence(c);
      return c.useDocCount && RIGHT_ITEM_TYPES.indexOf(c.type) >= 0 && !negativeOp(c) ? text + ' (' + c.docMin + '~' + c.docMax + '건의 서류에서 찾아야 함)' : text;
    }
    function sortText(sort) {
      if (!sort) return '';
      return ' (' + (sort.by || '(항목)') + ' ' + labelOf(SORT_ORDERS, sort.order) + ' 상위 ' + sort.limit + '건)';
    }
    function conditionText(c) {
      var value = c.conditionValue || '(조건 값)', key = josa(c.conditionKey, '이', '가');
      if (c.conditionOp === 'NE') return key + ' ' + value + '이(가) 아닌';
      if (c.conditionOp === 'CONTAINS') return key + ' ' + value + '을(를) 포함하는';
      if (c.conditionOp === 'IN') return key + ' ' + value + ' 중 하나인';
      return key + ' ' + value + '인';
    }
    function offsetText(c) {
      if (!c.offset) return '기준일';
      return '기준일 ' + Math.abs(c.offset) + labelOf(DATE_UNITS, c.dateUnit) + (c.offset < 0 ? ' 전' : ' 후');
    }
    function aggregateText(c) {
      var cond = c.condition ? ' 중 ' + conditionText(c) + ' 값의 ' : '의 ';
      var left = pickText(c.leftPick, c.leftForm) + roleText(c.leftRole) + cond + (c.leftKey || '(항목)') + sortText(c.leftSort) + ' ' + labelOf(AGGREGATE_FNS, c.fn) + (c.scope === 'ALL' ? '(전체 서류)' : '');
      var target = c.targetKind === 'item' ? formName(c.rightForm) + '의 ' + (c.rightKey || '(항목)')
        : c.targetKind === 'aggregate' ? formName(c.rightForm) + '의 ' + (c.rightKey || '(항목)') + ' ' + labelOf(AGGREGATE_FNS, c.rightFn)
        : c.targetKind === 'baseDate' ? offsetText(c) : String(c.targetValue);
      return josa(left, '이', '가') + ' ' + (c.op === 'EQ' ? josa(target, '과', '와') + ' 같다.' : target + ' 기준으로 ' + labelOf(OPS.aggregate, c.op) + '.');
    }
    function baseSentence(c) {
      var left = pickText(c.leftPick, c.leftForm) + roleText(c.leftRole) + '의 ' + (pairKeys(c, 'left').join('·') || '(항목)') + sortText(c.leftSort) + c.leftExtras.map(function (x) { return ' + ' + formName(x.form) + '의 ' + x.key; }).join('');
      if (c.type === 'count') return formName(c.leftForm) + ' 제출 건수가 ' + c.min + '건 이상 ' + c.max + '건 이하이다.';
      if (c.type === 'date') return josa(left, '이', '가') + ' 접수일 기준 ' + c.months + labelOf(DATE_UNITS, c.dateUnit) + ' 이내이다.';
      if (c.type === 'presence') return left + ' ' + labelOf(OPS.presence, c.op) + '.';
      if (c.type === 'baseDate') return josa(left, '이', '가') + ' ' + offsetText(c) + ' 기준으로 ' + labelOf(OPS.baseDate, c.op) + '.';
      if (c.type === 'aggregate') return aggregateText(c);
      if (c.type === 'pattern') {
        var part = c.sliceOn ? left + '의 ' + c.sliceStart + '번째부터 ' + c.sliceLength + '자리' : left;
        return c.patternMode === 'regex' ? josa(part, '이', '가') + ' 형식 ' + (c.pattern || '(정규식)') + '에 맞다.' : josa(part, '이', '가') + ' ' + (c.valueList || '(값 목록)') + ' 중 하나이다.';
      }
      var opLabel = (OPS[c.type].filter(function (o) { return o[0] === c.op; })[0] || ['', ''])[1];
      if (c.type === 'fixed') return josa(left, '이', '가') + ' ' + josa('"' + c.fixed + '"', '과', '와') + ' ' + opLabel + '.';
      var cond = c.condition ? ' 중 ' + conditionText(c) + ' 값의 ' : '의 ';
      var right = pickText(c.rightPick, c.rightForm) + roleText(c.rightRole) + cond + (pairKeys(c, 'right').join('·') || '(항목)') + sortText(c.rightSort) + c.extras.map(function (x) { return ' + ' + formName(x.form) + '의 ' + x.key; }).join('');
      if (c.type === 'dateCompare') return josa(left, '이', '가') + ' ' + right + ' 기준으로 ' + opLabel + '.';
      if (c.type === 'list') return josa(left, '이', '가') + ' ' + right + ' 목록에 ' + opLabel + '.';
      if (c.type === 'number') {
        var target = calcText(right, c);
        return josa(left, '이', '가') + ' ' + (c.op === 'EQ' ? josa(target, '과', '와') + ' 같다.' : target + ' 기준으로 ' + opLabel + '.');
      }
      return josa(left, '이', '가') + ' ' + (c.op === 'CONTAINS' ? josa(right, '을', '를') + ' 포함한다.' : josa(right, '과', '와') + ' ' + opLabel + '.');
    }
    function whenText(w) {
      if (w.type === 'hasDoc') return josa(formName(w.form), '이', '가') + ' 있으면';
      if (w.type === 'noDoc') return josa(formName(w.form), '이', '가') + ' 없으면';
      if (w.op === 'NOT_EMPTY' || w.op === 'EMPTY') return formName(w.form) + '의 ' + (w.key || '(항목)') + (w.op === 'EMPTY' ? ' 값이 없으면' : ' 값이 있으면');
      var target = formName(w.form) + '의 ' + josa(w.key || '(항목)', '이', '가') + ' "' + (w.value || '(값)') + '"';
      return w.op === 'NE' ? josa(target, '이', '가') + ' 아니면' : w.op === 'CONTAINS' ? josa(target, '을', '를') + ' 포함하면' : josa(target, '이면', '면');
    }
    function whensText(b) { return [b.when].concat(b.andWhen).map(whenText).join(' 그리고 '); }
    function bodyText(c) {
      function text(checks) { return checks.map(sentence).join(' 그리고 '); }
      if (c.methods.length === 1) return text(c.methods[0]);
      return '다음 중 하나라도 맞으면 일치한다. ' + c.methods.map(function (m, n) { return '방법 ' + (n + 1) + ': ' + text(m); }).join(' ');
    }
    function draftSentence(d) {
      if (d.branches.length === 1 && !d.otherwise.on) {
        var only = d.branches[0];
        if (only.when.type === 'none') return bodyText(only);
        if (only.when.type === 'hasDoc' && !only.andWhen.length) return josa(formName(only.when.form), '이', '가') + ' 있을 때만 확인한다. ' + bodyText(only);
        return whensText(only) + ' 확인한다. ' + bodyText(only);
      }
      return d.branches.map(function (b, i) { return (i ? '아니고 ' : '만약 ') + whensText(b) + ' ' + bodyText(b); }).join(' ')
        + ' 그 외에는 ' + (d.otherwise.on ? bodyText(d.otherwise) : '해당없음.');
    }

    /* 읽지 못한 정의 하나 때문에 목록 전체가 멈추지 않게 그 규칙의 문장만 비운다 */
    function ruleSentence(rule) {
      try {
        return draftSentence(fromDef(rule));
      } catch (e) {
        console.warn('규칙 문장을 만들지 못했습니다. ruleId=' + rule.ruleId, e);
        return '';
      }
    }
    return {
      CONDITION_OPS: CONDITION_OPS, AGGREGATE_FNS: AGGREGATE_FNS, DATE_UNITS: DATE_UNITS, SORT_ORDERS: SORT_ORDERS, SORT_TYPES: SORT_TYPES, newSort: newSort,
      OPS: OPS, CALC_OPS: CALC_OPS, PICK_OPS: PICK_OPS, RIGHT_ITEM_TYPES: RIGHT_ITEM_TYPES, PAIR_TYPES: PAIR_TYPES,
      WHEN_TYPES: WHEN_TYPES, WHEN_OPS: WHEN_OPS, form: form, formName: formName, firstKey: firstKey,
      newStep: newStep, newPick: newPick, newCheck: newCheck, retypeCheck: retypeCheck, newWhen: newWhen, newBranch: newBranch, newOtherwise: newOtherwise,
      activeCase: activeCase, selectMethod: selectMethod, selectBranch: selectBranch, allChecks: allChecks, checkById: checkById, pairKeys: pairKeys, negativeOp: negativeOp,
      fromDef: fromDef, toDef: toDef, splitList: splitList, josa: josa, sentence: sentence, draftSentence: draftSentence, ruleSentence: ruleSentence
    };
  }
  window.CrossRuleSentence = { create: create };
})();
