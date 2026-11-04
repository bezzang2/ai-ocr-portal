/* 판독결과: 전처리 페이지와 bbox는 같은 좌표계로 표시한다. 원본은 별도로 열람한다. */
(function () {
  'use strict';

  /* 페이지를 휠로 내리다 커서 아래로 올라온 뷰어가 휠을 가로채지 않게, 마우스를 움직여 올라간 뷰어만 확대·축소한다.
     화면이 움직일 때 브라우저가 같은 좌표로 보내는 이동 이벤트는 무시한다 */
  var pointerViewport = null, pointerAt = { x: 0, y: 0 };
  document.addEventListener('pointermove', function (event) {
    if (Math.abs(event.clientX - pointerAt.x) <= 3 && Math.abs(event.clientY - pointerAt.y) <= 3) return;
    pointerAt = { x: event.clientX, y: event.clientY };
    pointerViewport = event.target.closest ? event.target.closest('.doc-viewer__viewport') : null;
  }, { passive: true });
  var scriptUrl = document.currentScript.src;
  var pdfLibrary;
  var MAX_BYTES = 64 * 1024 * 1024;
  var MAX_PIXELS = 24000000;

  function actionUrl(url, action) { return url.replace(/\/view(?=\?|$)/, '/' + action); }
  function safeUrl(url) {
    var parsed = new URL(url, location.href);
    if (parsed.origin !== location.origin || !/^https?:$/.test(parsed.protocol)) throw new Error('파일 주소가 올바르지 않습니다.');
    return parsed.href;
  }
  async function readFile(url, signal) {
    var response = await fetch(safeUrl(url), { signal: signal, cache: 'no-store' });
    if (!response.ok) throw new Error('파일을 불러올 수 없습니다.');
    if (Number(response.headers.get('content-length')) > MAX_BYTES) throw new Error('원본 보기의 파일 크기 제한(64MB)을 초과했습니다.');
    var reader = response.body.getReader(), chunks = [], total = 0;
    try {
      while (true) {
        var part = await reader.read();
        if (part.done) break;
        total += part.value.length;
        if (total > MAX_BYTES) throw new Error('원본 보기의 파일 크기 제한(64MB)을 초과했습니다.');
        chunks.push(part.value);
      }
    } finally { await reader.cancel().catch(function () {}); }
    var bytes = new Uint8Array(total), offset = 0;
    chunks.forEach(function (chunk) { bytes.set(chunk, offset); offset += chunk.length; });
    return bytes;
  }
  function boxesOf(raw) {
    try {
      var value = typeof raw === 'string' ? JSON.parse(raw) : raw;
      return Array.isArray(value) ? value.filter(function (entry) {
        return entry && Number.isInteger(entry.page) && entry.page > 0 && Array.isArray(entry.token_bbox);
      }).map(function (entry) {
        return { page: entry.page, token_bbox: entry.token_bbox.filter(function (box) {
          return Array.isArray(box) && box.length === 4 && box.every(Number.isFinite) && box[2] > 0 && box[3] > 0;
        }) };
      }).filter(function (entry) { return entry.token_bbox.length; }) : [];
    } catch (_) { return []; }
  }
  function checkImageSize(bytes) {
    var data = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength), width, height;
    if (bytes.length >= 24 && data.getUint32(0) === 0x89504e47 && data.getUint32(4) === 0x0d0a1a0a) {
      width = data.getUint32(16); height = data.getUint32(20);
    } else if (bytes.length > 4 && data.getUint16(0) === 0xffd8) {
      var offset = 2;
      while (offset + 3 < bytes.length && bytes[offset] === 0xff) {
        while (bytes[offset] === 0xff) offset++;
        var marker = bytes[offset++];
        if (marker === 0xda || marker === 0xd9) break;
        if (marker === 1 || marker >= 0xd0 && marker <= 0xd8) continue;
        if (offset + 2 > bytes.length) break;
        var length = data.getUint16(offset);
        if (length < 2 || offset + length > bytes.length) break;
        if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc && length >= 7) {
          height = data.getUint16(offset + 3); width = data.getUint16(offset + 5); break;
        }
        offset += length;
      }
    }
    if (!width || !height || width * height > MAX_PIXELS) throw new Error('이미지 형식 또는 표시 가능한 크기를 확인해 주세요.');
  }

  function Viewer(root) {
    this.root = root;
    this.viewport = root.querySelector('[data-viewer-viewport]');
    this.space = root.querySelector('[data-viewer-space]');
    this.stage = root.querySelector('[data-viewer-stage]');
    this.image = root.querySelector('[data-viewer-image]');
    this.canvas = root.querySelector('[data-viewer-canvas]');
    this.overlay = root.querySelector('[data-viewer-overlay]');
    this.status = root.querySelector('[data-viewer-status]');
    this.note = root.querySelector('[data-viewer-note]');
    this.pageInput = root.querySelector('[data-viewer-page]');
    this.minimap = root.querySelector('[data-viewer-minimap]');
    this.minimapImage = root.querySelector('[data-viewer-minimap-image]');
    this.minimapOverlay = root.querySelector('[data-viewer-minimap-overlay]');
    this.minimapBoxes = root.querySelector('[data-viewer-minimap-boxes]');
    this.minimapWindow = root.querySelector('[data-viewer-minimap-window]');
    this.mode = 'processed';
    this.page = 1;
    this.pageCount = 0;
    this.zoom = 1;
    this.width = 0;
    this.height = 0;
    this.version = 0;
    this.renderVersion = 0;
    this.selection = [];
    this.focusMode = 'locate';
    this.focusRequest = 0;
    this.rotation = 0;
    this.rotations = new Map();
    this.pendingWorker = new Map();
    this.workerId = 0;
    this.bind();
    this.updateControls();
  }
  Viewer.prototype.bind = function () {
    var self = this;
    this.root.addEventListener('click', function (event) {
      var button = event.target.closest('[data-viewer-action]');
      if (!button || button.disabled) return;
      var action = button.dataset.viewerAction;
      if (action === 'in') self.setZoom(self.zoom + .25);
      if (action === 'out') self.setZoom(self.zoom - .25);
      if (action === 'fit') self.fit();
      if (action === 'width') self.fit('width');
      if (action === 'reset') self.setZoom(1, true);
      if (action === 'rotate-left') self.setRotation(self.rotation - 90);
      if (action === 'rotate-right') self.setRotation(self.rotation + 90);
      if (action === 'focus-locate') self.setFocusMode('locate');
      if (action === 'focus-zoom') self.setFocusMode('zoom');
      if (action === 'clear-focus') self.clearFocus();
      if (action === 'previous') self.go(self.page - 1);
      if (action === 'next') self.go(self.page + 1);
      if (action === 'processed' || action === 'original') self.setMode(action);
    });
    function jump() {
      var number = Number(self.pageInput.value);
      var index = self.mode === 'processed' && self.meta ? self.meta.pages.findIndex(function (entry) {
        return entry.pageNumber === number;
      }) + 1 : number;
      self.go(index);
    }
    this.pageInput.addEventListener('change', jump);
    this.pageInput.addEventListener('keydown', function (event) {
      if (event.key === 'Enter') { event.preventDefault(); jump(); }
    });
    this.viewport.addEventListener('wheel', function (event) {
      if (!self.width || !Number.isFinite(event.deltaY) || event.deltaY === 0) return;
      /* 세로로 늘어놓은 크게 보기에서는 휠이 창 스크롤이고 Ctrl + 휠만 확대·축소다 */
      if (self.root.closest('[data-viewer-ctrl-zoom]') ? !event.ctrlKey : pointerViewport !== self.viewport) return;
      event.preventDefault();
      var bounds = self.viewport.getBoundingClientRect();
      self.setZoom(self.zoom + (event.deltaY < 0 ? .25 : -.25), false,
        { x: event.clientX - bounds.left - self.viewport.clientLeft, y: event.clientY - bounds.top - self.viewport.clientTop });
    }, { passive: false });
    var drag;
    this.viewport.addEventListener('pointerdown', function (event) {
      if (event.button !== 0 || !self.width || event.target.closest('button, input, [data-viewer-minimap]')) return;
      drag = { x: event.clientX, y: event.clientY, left: self.viewport.scrollLeft, top: self.viewport.scrollTop };
      self.viewport.setPointerCapture(event.pointerId);
      self.viewport.classList.add('is-dragging');
      event.preventDefault();
    });
    this.viewport.addEventListener('pointermove', function (event) {
      if (!drag) return;
      self.viewport.scrollLeft = drag.left - (event.clientX - drag.x);
      self.viewport.scrollTop = drag.top - (event.clientY - drag.y);
    });
    ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(function (name) {
      self.viewport.addEventListener(name, function () { drag = null; self.viewport.classList.remove('is-dragging'); });
    });
    this.viewport.addEventListener('keydown', function (event) {
      if (event.key === '+' || event.key === '=') { event.preventDefault(); self.setZoom(self.zoom + .25); }
      if (event.key === '-') { event.preventDefault(); self.setZoom(self.zoom - .25); }
      if (event.key === '0') { event.preventDefault(); self.fit(); }
      if (event.key === 'Escape') self.clearFocus();
    });
    this.viewport.addEventListener('scroll', function () {
      if (self.minimapFrame) return;
      self.minimapFrame = requestAnimationFrame(function () { self.minimapFrame = null; self.updateMinimap(); });
    }, { passive: true });
    if (this.minimap) {
      var minimapDrag = false;
      function panMinimap(event) {
        var rect = self.minimap.getBoundingClientRect(), size = self.orientedSize();
        if (!self.width || !rect.width || !rect.height) return;
        self.centerOn(Math.max(0, Math.min(size.width, (event.clientX - rect.left) / rect.width * size.width)),
          Math.max(0, Math.min(size.height, (event.clientY - rect.top) / rect.height * size.height)));
      }
      this.minimap.addEventListener('pointerdown', function (event) {
        if (event.button !== 0 || !self.width) return;
        event.stopPropagation(); event.preventDefault();
        minimapDrag = true;
        self.minimap.setPointerCapture(event.pointerId);
        panMinimap(event);
      });
      this.minimap.addEventListener('pointermove', function (event) { if (minimapDrag) panMinimap(event); });
      ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(function (name) {
        self.minimap.addEventListener(name, function () { minimapDrag = false; });
      });
      this.minimap.addEventListener('keydown', function (event) {
        if (event.key === 'Home') { event.preventDefault(); event.stopPropagation(); self.fit(); return; }
        var dx = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0;
        var dy = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
        if (!dx && !dy) return;
        event.preventDefault(); event.stopPropagation();
        self.viewport.scrollLeft += dx * self.viewport.clientWidth / 4;
        self.viewport.scrollTop += dy * self.viewport.clientHeight / 4;
        self.updateMinimap();
      });
    }
    this.resizeObserver = new ResizeObserver(function () {
      var width = self.viewport.clientWidth, height = self.viewport.clientHeight;
      // Ignore subpixel notifications that do not change the usable viewport.
      if (width === self.viewportWidth && height === self.viewportHeight) return;
      self.viewportWidth = width;
      self.viewportHeight = height;
      if (!self.width || self.resizeFrame) return;
      // ResizeObserver delivery must not synchronously resize its own observed box.
      self.resizeFrame = requestAnimationFrame(function () {
        self.resizeFrame = null;
        if (!self.width) return;
        if (self.fitted) self.fit(self.fitMode); else self.layout();
      });
    });
    this.resizeObserver.observe(this.viewport);
  };
  Viewer.prototype.setStatus = function (text, busy) {
    this.status.textContent = text;
    this.status.hidden = !text;
    this.root.setAttribute('aria-busy', busy ? 'true' : 'false');
  };
  Viewer.prototype.updateControls = function () {
    var self = this, ready = this.width > 0;
    this.root.querySelectorAll('[data-viewer-action]').forEach(function (button) {
      var action = button.dataset.viewerAction;
      button.disabled = action === 'original' ? !(self.meta && self.meta.original) :
        action === 'processed' ? !self.meta : !ready;
      if (action === 'in') button.disabled = !ready || self.zoom >= 4;
      if (action === 'out') button.disabled = !ready || self.zoom <= .1;
      if (action === 'previous') button.disabled = !ready || self.page <= 1;
      if (action === 'next') button.disabled = !ready || self.page >= self.pageCount;
      if (action === 'clear-focus') button.disabled = !ready || !self.selection.length;
      if (action === 'processed' || action === 'original') button.setAttribute('aria-pressed', String(self.mode === action));
      if (action === 'fit' || action === 'width') button.setAttribute('aria-pressed', String(!!self.fitted && self.fitMode === (action === 'fit' ? 'page' : 'width')));
      if (action === 'focus-locate' || action === 'focus-zoom') button.setAttribute('aria-pressed', String(self.focusMode === action.slice(6)));
    });
    var pages = this.mode === 'processed' && this.meta ? this.meta.pages : null;
    this.pageInput.value = this.pageCount ? pages ? pages[this.page - 1].pageNumber : this.page : '';
    this.pageInput.max = pages && pages.length ? pages[pages.length - 1].pageNumber : this.pageCount;
    this.pageInput.disabled = !this.pageCount;
    var sparse = pages && pages.length && pages.some(function (entry, index) { return entry.pageNumber !== index + 1; });
    this.root.querySelector('[data-viewer-pages]').textContent = sparse ? '· ' + this.pageCount + '장' : '/ ' + this.pageCount;
    this.root.querySelector('[data-viewer-zoom]').textContent = Math.round(this.zoom * 100) + '%';
    var rotationLabel = this.root.querySelector('[data-viewer-rotation]');
    if (rotationLabel) rotationLabel.textContent = this.rotation + '°';
  };
  Viewer.prototype.release = function () {
    this.renderVersion++;
    this.focusSnapshot = null;
    this.thumbnailSource = null;
    if (this.minimap) this.minimap.hidden = true;
    if (this.abort) this.abort.abort();
    if (this.imageAbort) this.imageAbort.abort();
    if (this.pdfRender) this.pdfRender.cancel();
    this.pdfRender = null;
    if (this.pdfTask) this.pdfTask.destroy().catch(function () {});
    this.pdfTask = null;
    this.pdf = null;
    if (this.worker) this.worker.terminate();
    this.worker = null;
    this.pendingWorker.forEach(function (pending) { clearTimeout(pending.timer); pending.reject(new Error('cancelled')); });
    this.pendingWorker.clear();
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = null;
    this.image.removeAttribute('src');
    this.image.hidden = true;
    this.canvas.hidden = true;
    this.canvas.width = this.canvas.height = 0;
    this.overlay.replaceChildren();
    this.width = this.height = 0;
    this.stage.hidden = true;
    this.space.style.width = this.space.style.height = '0px';
  };
  Viewer.prototype.open = async function (item) {
    var version = ++this.version;
    this.release();
    this.meta = null;
    this.mode = 'processed';
    this.page = 1;
    this.pageCount = 0;
    this.selection = [];
    this.focusRequest++;
    this.rotations.clear();
    this.rotation = 0;
    this.updateControls();
    this.note.textContent = '';
    this.setStatus('판독 이미지를 불러오는 중입니다.', true);
    this.abort = new AbortController();
    try {
      var response = await fetch(safeUrl(actionUrl(item.getAttribute('data-image-src'), 'viewer-meta')), {
        signal: this.abort.signal, cache: 'no-store'
      });
      if (!response.ok) throw new Error('판독 이미지를 불러올 수 없습니다. 잠시 후 다시 선택해 주세요.');
      var meta = await response.json();
      if (version !== this.version) return;
      this.meta = meta;
      this.meta.pages = Array.isArray(meta.pages) ? meta.pages : [];
      this.pageCount = this.meta.pages.length;
      this.updateControls();
      if (!this.pageCount) {
        this.setStatus(meta.message || '판독 이미지가 없습니다. 원본 보기를 이용해 주세요.', false);
        return;
      }
      await this.renderPage(true);
    } catch (error) {
      if (version === this.version && error.name !== 'AbortError') this.setStatus(error.message, false);
    }
  };
  Viewer.prototype.workerCall = function (type, extra, transfer) {
    var self = this, id = ++this.workerId;
    return new Promise(function (resolve, reject) {
      var timer = setTimeout(function () {
        if (self.worker) self.worker.terminate();
        self.worker = null;
        self.pendingWorker.forEach(function (pending) { clearTimeout(pending.timer); pending.reject(new Error('TIFF 처리 시간이 초과했습니다.')); });
        self.pendingWorker.clear();
      }, 30000);
      self.pendingWorker.set(id, { resolve: resolve, reject: reject, timer: timer });
      self.worker.postMessage(Object.assign({ id: id, type: type }, extra), transfer || []);
    });
  };
  Viewer.prototype.loadOriginal = async function () {
    var version = this.version;
    var original = this.meta.original;
    var mime = (original.mimeType || '').toLowerCase();
    this.abort = new AbortController();
    if (mime === 'application/pdf') {
      var vendor = new URL('../vendor/pdfjs/', scriptUrl).href;
      if (!pdfLibrary) pdfLibrary = import(vendor + 'build/pdf.min.mjs');
      var pdfjs = await pdfLibrary;
      if (version !== this.version) return;
      pdfjs.GlobalWorkerOptions.workerSrc = vendor + 'build/pdf.worker.min.mjs';
      var data = await readFile(original.url, this.abort.signal);
      if (version !== this.version) return;
      var loading = pdfjs.getDocument({ data: data, cMapUrl: vendor + 'cmaps/', cMapPacked: true,
        standardFontDataUrl: vendor + 'standard_fonts/', wasmUrl: vendor + 'wasm/',
        isEvalSupported: false, maxImageSize: MAX_PIXELS });
      this.pdfTask = loading;
      var pdf = await loading.promise;
      if (version !== this.version) { await loading.destroy(); return; }
      this.pdf = pdf;
      this.pageCount = pdf.numPages;
    } else if (/image\/tiff?/.test(mime) || /\.tiff?$/i.test(original.fileName || '')) {
      var bytes = await readFile(original.url, this.abort.signal);
      if (version !== this.version) return;
      var self = this;
      this.worker = new Worker(new URL('doc-viewer-tiff-worker.js', scriptUrl));
      this.worker.onmessage = function (event) {
        var pending = self.pendingWorker.get(event.data.id);
        if (!pending) return;
        self.pendingWorker.delete(event.data.id);
        clearTimeout(pending.timer);
        if (event.data.error) pending.reject(new Error(event.data.error)); else pending.resolve(event.data);
      };
      this.worker.onerror = function () {
        if (self.worker) self.worker.terminate();
        self.worker = null;
        self.pendingWorker.forEach(function (pending) { clearTimeout(pending.timer); pending.reject(new Error('TIFF 뷰어를 시작할 수 없습니다.')); });
        self.pendingWorker.clear();
      };
      var loaded = await this.workerCall('load', { buffer: bytes.buffer }, [bytes.buffer]);
      this.pageCount = loaded.pages.length;
    } else if (mime === 'image/jpeg' || mime === 'image/png' || /\.(jpe?g|png)$/i.test(original.fileName || '')) {
      this.pageCount = 1;
    } else { throw new Error('지원하는 원본 형식은 PDF, JPG, PNG, TIFF입니다.'); }
  };
  Viewer.prototype.setMode = async function (mode) {
    if (!this.meta || mode === this.mode || mode === 'original' && !this.meta.original) return;
    var version = ++this.version;
    this.release();
    this.mode = mode;
    this.page = 1;
    this.pageCount = 0;
    this.updateControls();
    this.setStatus('이미지를 불러오는 중입니다.', true);
    this.note.textContent = mode === 'original' ? '업로드 원본에서는 인식 영역을 표시하지 않습니다.' : '';
    try {
      if (mode === 'original') await this.loadOriginal();
      else this.pageCount = this.meta.pages.length;
      if (version !== this.version) return;
      this.updateControls();
      if (!this.pageCount) { this.setStatus('판독 이미지가 없습니다.', false); return; }
      await this.renderPage(true);
    } catch (error) {
      if (version === this.version && error.name !== 'AbortError') this.setStatus(error.message, false);
    }
  };
  Viewer.prototype.go = async function (page) {
    if (!Number.isInteger(page) || page < 1 || page > this.pageCount) { this.updateControls(); return; }
    this.page = page;
    this.focusSnapshot = null;
    this.updateControls();
    await this.renderPage(true);
  };
  Viewer.prototype.renderPage = async function (fit) {
    var render = ++this.renderVersion, version = this.version;
    if (this.pdfRender) this.pdfRender.cancel();
    this.pdfRender = null;
    this.overlay.replaceChildren();
    this.stage.hidden = true;
    if (this.minimap) this.minimap.hidden = true;
    this.setStatus('페이지를 불러오는 중입니다.', true);
    try {
      var width, height;
      if (this.mode === 'original' && this.pdf) {
        var page = await this.pdf.getPage(this.page);
        if (render !== this.renderVersion || version !== this.version) return;
        var viewport = page.getViewport({ scale: 1 });
        width = viewport.width; height = viewport.height;
        var scale = Math.min(2, Math.sqrt(12000000 / (width * height)));
        // 별도 canvas에서 렌더링해 연속 페이지 전환의 결과가 섞이지 않게 한다.
        var target = document.createElement('canvas');
        var rendered = page.getViewport({ scale: scale });
        target.width = Math.ceil(rendered.width); target.height = Math.ceil(rendered.height);
        var task = page.render({ canvasContext: target.getContext('2d'), viewport: rendered });
        this.pdfRender = task;
        await task.promise;
        if (this.pdfRender === task) this.pdfRender = null;
        if (render !== this.renderVersion || version !== this.version) return;
        this.canvas.width = target.width; this.canvas.height = target.height;
        this.canvas.getContext('2d').drawImage(target, 0, 0);
        target.width = target.height = 0;
        this.canvas.hidden = false; this.image.hidden = true;
        this.thumbnailSource = this.canvas;
      } else if (this.mode === 'original' && this.worker) {
        var data = await this.workerCall('page', { page: this.page });
        if (render !== this.renderVersion || version !== this.version) return;
        var raw = document.createElement('canvas');
        raw.width = data.width; raw.height = data.height;
        raw.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(data.rgba), data.width, data.height), 0, 0);
        var rotated = data.orientation >= 5 && data.orientation <= 8;
        width = rotated ? data.height : data.width; height = rotated ? data.width : data.height;
        this.canvas.width = width; this.canvas.height = height;
        var transforms = { 2: [-1,0,0,1,width,0], 3: [-1,0,0,-1,width,height], 4: [1,0,0,-1,0,height],
          5: [0,1,1,0,0,0], 6: [0,1,-1,0,width,0], 7: [0,-1,-1,0,width,height], 8: [0,-1,1,0,0,height] };
        var context = this.canvas.getContext('2d');
        context.setTransform.apply(context, transforms[data.orientation] || [1,0,0,1,0,0]);
        context.drawImage(raw, 0, 0);
        context.resetTransform();
        raw.width = raw.height = 0;
        this.canvas.hidden = false; this.image.hidden = true;
        this.thumbnailSource = this.canvas;
      } else {
        var entry = this.mode === 'processed' ? this.meta.pages[this.page - 1] : this.meta.original;
        if (!entry || !(entry.imageUrl || entry.url)) throw new Error('페이지 이미지를 찾을 수 없습니다.');
        if (this.imageAbort) this.imageAbort.abort();
        this.imageAbort = new AbortController();
        var bytes = await readFile(entry.imageUrl || entry.url, this.imageAbort.signal);
        checkImageSize(bytes);
        var image = new Image();
        var url = URL.createObjectURL(new Blob([bytes]));
        try {
          image.src = url;
          await image.decode();
          if (render !== this.renderVersion || version !== this.version) { URL.revokeObjectURL(url); return; }
          width = image.naturalWidth; height = image.naturalHeight;
          if (width * height > MAX_PIXELS) throw new Error('표시 가능한 이미지 크기를 초과했습니다.');
          if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
          this.objectUrl = url;
          this.image.src = url;
          this.image.hidden = false; this.canvas.hidden = true;
          this.thumbnailSource = image;
        } catch (error) { URL.revokeObjectURL(url); throw error; }
      }
      if (render !== this.renderVersion || version !== this.version) return;
      this.width = width; this.height = height;
      this.rotation = this.rotations.get(this.rotationKey()) || 0;
      this.stage.hidden = false;
      this.setStatus('', false);
      this.overlay.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
      if (fit) this.fit(); else this.layout();
      this.drawMinimapImage();
      this.drawHighlights();
      if (this.pendingFocus === this.focusRequest) { this.focusSelection(); this.pendingFocus = null; }
      this.updateControls();
    } catch (error) {
      if (render === this.renderVersion && version === this.version && error.name !== 'AbortError') {
        this.width = this.height = 0;
        this.setStatus('페이지를 표시할 수 없습니다. ' + (error.message || ''), false);
        this.updateControls();
      }
    }
  };
  Viewer.prototype.rotationKey = function () { return this.mode + ':' + this.page; };
  Viewer.prototype.orientedSize = function () {
    return this.rotation % 180 ? { width: this.height, height: this.width } : { width: this.width, height: this.height };
  };
  Viewer.prototype.rotationMatrix = function () {
    if (this.rotation === 90) return [0, 1, -1, 0, this.height, 0];
    if (this.rotation === 180) return [-1, 0, 0, -1, this.width, this.height];
    if (this.rotation === 270) return [0, -1, 1, 0, 0, this.width];
    return [1, 0, 0, 1, 0, 0];
  };
  Viewer.prototype.orientedPoint = function (x, y, inverse) {
    var m = this.rotationMatrix();
    if (inverse) return { x: m[0] * (x - m[4]) + m[1] * (y - m[5]), y: m[2] * (x - m[4]) + m[3] * (y - m[5]) };
    return { x: m[0] * x + m[2] * y + m[4], y: m[1] * x + m[3] * y + m[5] };
  };
  Viewer.prototype.orientedBox = function (box) {
    var a = this.orientedPoint(box.x, box.y), b = this.orientedPoint(box.x + box.width, box.y + box.height);
    return { x: Math.min(a.x, b.x), y: Math.min(a.y, b.y), width: Math.abs(b.x - a.x), height: Math.abs(b.y - a.y) };
  };
  Viewer.prototype.setRotation = function (angle) {
    if (!this.width || !Number.isFinite(angle) || angle % 90 !== 0) return;
    var rotation = ((angle % 360) + 360) % 360;
    if (rotation === this.rotation) return;
    var center = this.orientedPoint((this.viewport.scrollLeft + this.viewport.clientWidth / 2 - parseFloat(this.stage.style.left)) / this.zoom,
      (this.viewport.scrollTop + this.viewport.clientHeight / 2 - parseFloat(this.stage.style.top)) / this.zoom, true);
    this.focusSnapshot = null;
    this.rotation = rotation;
    this.rotations.set(this.rotationKey(), rotation);
    if (this.fitted) this.fit(this.fitMode);
    else {
      this.layout();
      var point = this.orientedPoint(center.x, center.y);
      this.centerOn(point.x, point.y);
    }
    this.drawMinimapImage();
    this.drawHighlights();
  };
  Viewer.prototype.fit = function (mode) {
    if (!this.width) return;
    this.focusSnapshot = null;
    this.fitted = true;
    this.fitMode = mode === 'width' ? 'width' : 'page';
    var size = this.orientedSize();
    // Leave room for fractional CSS pixels as clientWidth/clientHeight are rounded.
    this.zoom = Math.min(1, (this.viewport.clientWidth - 34) / size.width);
    if (this.fitMode === 'page') this.zoom = Math.min(this.zoom, (this.viewport.clientHeight - 34) / size.height);
    this.zoom = Math.max(.01, this.zoom);
    this.layout();
    this.viewport.scrollLeft = this.viewport.scrollTop = 0;
    this.updateMinimap();
  };
  Viewer.prototype.setZoom = function (zoom, reset, anchor) {
    if (!this.width) return;
    var before = this.zoom;
    anchor = anchor || { x: this.viewport.clientWidth / 2, y: this.viewport.clientHeight / 2 };
    var x = (this.viewport.scrollLeft + anchor.x - parseFloat(this.stage.style.left)) / before;
    var y = (this.viewport.scrollTop + anchor.y - parseFloat(this.stage.style.top)) / before;
    this.fitted = false;
    this.zoom = Math.min(4, Math.max(.1, zoom));
    this.layout();
    this.viewport.scrollLeft = reset ? 0 : parseFloat(this.stage.style.left) + x * this.zoom - anchor.x;
    this.viewport.scrollTop = reset ? 0 : parseFloat(this.stage.style.top) + y * this.zoom - anchor.y;
    this.updateMinimap();
  };
  Viewer.prototype.centerOn = function (x, y) {
    this.viewport.scrollLeft = parseFloat(this.stage.style.left) + x * this.zoom - this.viewport.clientWidth / 2;
    this.viewport.scrollTop = parseFloat(this.stage.style.top) + y * this.zoom - this.viewport.clientHeight / 2;
    this.updateMinimap();
  };
  Viewer.prototype.layout = function () {
    var size = this.orientedSize(), w = size.width * this.zoom, h = size.height * this.zoom;
    var left = Math.max(16, (this.viewport.clientWidth - w) / 2);
    var top = Math.max(16, (this.viewport.clientHeight - h) / 2);
    // CSS min-size fills the viewport without feeding rounded viewport sizes back into it.
    this.space.style.width = Math.ceil(w + 32) + 'px';
    this.space.style.height = Math.ceil(h + 32) + 'px';
    this.stage.style.width = this.width + 'px'; this.stage.style.height = this.height + 'px';
    this.stage.style.left = left + 'px'; this.stage.style.top = top + 'px';
    this.stage.style.transformOrigin = '0 0';
    var zoom = this.zoom;
    this.stage.style.transform = 'matrix(' + this.rotationMatrix().map(function (value) { return value * zoom; }).join(',') + ')';
    this.updateControls();
    this.updateMinimap();
  };
  Viewer.prototype.setFocusMode = function (mode) {
    if ((mode !== 'locate' && mode !== 'zoom') || mode === this.focusMode) return;
    var selection = this.selection;
    this.clearFocus();
    this.focusMode = mode;
    this.selection = selection;
    this.drawHighlights();
    this.focusSelection();
    this.updateControls();
  };
  Viewer.prototype.clearFocus = function () {
    this.focusRequest++;
    this.pendingFocus = null;
    var saved = this.focusSnapshot;
    this.focusSnapshot = null;
    this.selection = [];
    if (saved && saved.version === this.version && saved.key === this.rotationKey() && saved.rotation === this.rotation && this.width) {
      if (saved.fitted && (saved.viewportWidth !== this.viewport.clientWidth || saved.viewportHeight !== this.viewport.clientHeight)) this.fit(saved.fitMode);
      else {
        this.zoom = saved.zoom; this.fitted = saved.fitted; this.fitMode = saved.fitMode;
        this.layout();
        this.viewport.scrollLeft = saved.left; this.viewport.scrollTop = saved.top;
      }
    }
    this.drawHighlights();
    this.updateControls();
    this.updateMinimap();
    if (this.mode === 'processed') this.note.textContent = '';
  };
  Viewer.prototype.highlight = async function (raw) {
    var selection = boxesOf(raw), request = ++this.focusRequest, meta = this.meta;
    this.pendingFocus = request;
    this.selection = selection;
    if (!meta) return;
    if (!selection.length) { this.clearFocus(); this.note.textContent = '이 항목에는 인식 영역 정보가 없습니다.'; return; }
    if (this.mode !== 'processed') await this.setMode('processed');
    if (request !== this.focusRequest || meta !== this.meta) return;
    var target = meta.pages.findIndex(function (page) {
      return selection.some(function (entry) { return entry.page === page.pageNumber; });
    }) + 1;
    if (target && target !== this.page) await this.go(target);
    if (request !== this.focusRequest || meta !== this.meta || this.mode !== 'processed' || target && target !== this.page) return;
    this.drawHighlights();
    this.note.textContent = target ? '' : '해당 항목의 판독 페이지를 사용할 수 없습니다.';
    this.focusSelection();
    if (!this.stage.hidden) this.pendingFocus = null;
    this.updateControls();
  };
  Viewer.prototype.focusSelection = function () {
    if (!this.width || this.stage.hidden || this.mode !== 'processed') return;
    var self = this, boxes = Array.from(this.overlay.querySelectorAll('rect')).map(function (rect) {
      return self.orientedBox({ x: Number(rect.getAttribute('x')), y: Number(rect.getAttribute('y')),
        width: Number(rect.getAttribute('width')), height: Number(rect.getAttribute('height')) });
    });
    if (!boxes.length) return;
    var left = Math.min.apply(null, boxes.map(function (box) { return box.x; }));
    var top = Math.min.apply(null, boxes.map(function (box) { return box.y; }));
    var right = Math.max.apply(null, boxes.map(function (box) { return box.x + box.width; }));
    var bottom = Math.max.apply(null, boxes.map(function (box) { return box.y + box.height; }));
    if (!this.focusSnapshot) this.focusSnapshot = { version: this.version, key: this.rotationKey(), rotation: this.rotation,
      zoom: this.zoom, fitted: this.fitted, fitMode: this.fitMode, left: this.viewport.scrollLeft, top: this.viewport.scrollTop,
      viewportWidth: this.viewport.clientWidth, viewportHeight: this.viewport.clientHeight };
    if (this.focusMode === 'zoom') {
      var baseline = this.focusSnapshot.zoom;
      var zoom = Math.min(this.viewport.clientWidth / (Math.max(1, right - left) * 1.5),
        this.viewport.clientHeight / (Math.max(1, bottom - top) * 1.8));
      this.setZoom(Math.max(baseline, Math.min(4, baseline * 4, zoom)));
      this.centerOn((left + right) / 2, (top + bottom) / 2);
      return;
    }
    // Locate only moves an offscreen selection; repeated selection never changes zoom.
    left = parseFloat(this.stage.style.left) + left * this.zoom;
    top = parseFloat(this.stage.style.top) + top * this.zoom;
    right = parseFloat(this.stage.style.left) + right * this.zoom;
    bottom = parseFloat(this.stage.style.top) + bottom * this.zoom;
    var x = this.viewport.scrollLeft, y = this.viewport.scrollTop;
    if (left < x || right > x + this.viewport.clientWidth) this.viewport.scrollLeft = right - left > this.viewport.clientWidth ? left - 16 : left < x ? left - 16 : right - this.viewport.clientWidth + 16;
    if (top < y || bottom > y + this.viewport.clientHeight) this.viewport.scrollTop = bottom - top > this.viewport.clientHeight ? top - 16 : top < y ? top - 16 : bottom - this.viewport.clientHeight + 16;
    this.updateMinimap();
  };
  Viewer.prototype.drawMinimapImage = function () {
    if (!this.minimapImage || !this.thumbnailSource || !this.width) return;
    var size = this.orientedSize(), scale = Math.min(160 / size.width, 132 / size.height, 1);
    var width = Math.max(1, Math.round(size.width * scale)), height = Math.max(1, Math.round(size.height * scale));
    this.minimap.style.width = width + 'px'; this.minimap.style.height = height + 'px';
    this.minimapImage.width = width; this.minimapImage.height = height;
    var context = this.minimapImage.getContext('2d'), m = this.rotationMatrix();
    context.setTransform.apply(context, m.map(function (value) { return value * scale; }));
    context.drawImage(this.thumbnailSource, 0, 0, this.width, this.height);
    context.resetTransform();
    if (this.minimapOverlay) this.minimapOverlay.setAttribute('viewBox', '0 0 ' + size.width + ' ' + size.height);
    this.updateMinimap();
  };
  Viewer.prototype.updateMinimap = function () {
    if (!this.minimap || !this.minimapWindow) return;
    if (!this.width || this.stage.hidden) { this.minimap.hidden = true; return; }
    var size = this.orientedSize(), left = parseFloat(this.stage.style.left), top = parseFloat(this.stage.style.top);
    var x = this.viewport.scrollLeft, y = this.viewport.scrollTop;
    this.minimap.hidden = left >= x - 1 && top >= y - 1 && left + size.width * this.zoom <= x + this.viewport.clientWidth + 1 && top + size.height * this.zoom <= y + this.viewport.clientHeight + 1;
    var x0 = Math.max(0, Math.min(size.width, (x - left) / this.zoom));
    var y0 = Math.max(0, Math.min(size.height, (y - top) / this.zoom));
    var x1 = Math.max(x0, Math.min(size.width, (x + this.viewport.clientWidth - left) / this.zoom));
    var y1 = Math.max(y0, Math.min(size.height, (y + this.viewport.clientHeight - top) / this.zoom));
    this.minimapWindow.setAttribute('x', x0); this.minimapWindow.setAttribute('y', y0);
    this.minimapWindow.setAttribute('width', x1 - x0); this.minimapWindow.setAttribute('height', y1 - y0);
  };
  Viewer.prototype.drawHighlights = function () {
    this.overlay.replaceChildren();
    if (this.minimapBoxes) this.minimapBoxes.replaceChildren();
    if (this.mode !== 'processed' || !this.width) return;
    var self = this;
    var pageNumber = this.meta.pages[this.page - 1].pageNumber;
    this.selection.filter(function (entry) { return entry.page === pageNumber; }).forEach(function (entry) {
      entry.token_bbox.forEach(function (box) {
        if (!Array.isArray(box) || box.length !== 4 || !box.every(Number.isFinite) || box[2] <= 0 || box[3] <= 0) return;
        var x = Math.max(0, box[0]), y = Math.max(0, box[1]);
        var w = Math.min(self.width, box[0] + box[2]) - x, h = Math.min(self.height, box[1] + box[3]) - y;
        if (w <= 0 || h <= 0) return;
        var rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        rect.setAttribute('x', x); rect.setAttribute('y', y); rect.setAttribute('width', w); rect.setAttribute('height', h);
        rect.setAttribute('vector-effect', 'non-scaling-stroke');
        self.overlay.appendChild(rect);
        if (self.minimapBoxes) {
          var box = self.orientedBox({ x: x, y: y, width: w, height: h });
          var miniature = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
          Object.keys(box).forEach(function (key) { miniature.setAttribute(key, box[key]); });
          miniature.setAttribute('vector-effect', 'non-scaling-stroke');
          self.minimapBoxes.appendChild(miniature);
        }
      });
    });
  };
  function init() {
    document.querySelectorAll('[data-document-viewer]').forEach(function (root) {
      if (!root.documentViewer) root.documentViewer = new Viewer(root);
    });
  }
  // 화면이 나중에 만든 뷰어(서식간 검증 결과의 항목별 뷰어 등)도 같은 방식으로 붙인다.
  window.attachDocumentViewer = function (root) {
    if (!root.documentViewer) root.documentViewer = new Viewer(root);
    return root.documentViewer;
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
