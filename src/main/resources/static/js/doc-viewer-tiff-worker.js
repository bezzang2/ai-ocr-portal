/* TIFF 원본은 파일 변환 없이 worker에서 디코딩한다. 원본 바이트는 worker에만 보관한다. */
importScripts('../vendor/tiff/pako.min.js', '../vendor/tiff/UTIF.js');
var source;
var pages;
var previous;
self.onmessage = function (event) {
  var request = event.data;
  try {
    if (request.type === 'load') {
      source = request.buffer;
      pages = UTIF.decode(source).filter(function (ifd) { return ifd.t256 && ifd.t257 && !(ifd.t254 && (ifd.t254[0] & 1)); });
      if (!pages.length || pages.length > 500) throw new Error('페이지 수를 확인할 수 없습니다.');
      pages.forEach(function (p) {
        var width = p.t256[0], height = p.t257[0];
        if (!Number.isFinite(width * height) || width <= 0 || height <= 0 || width * height > 24000000) {
          throw new Error('표시 가능한 TIFF 페이지 크기를 초과했습니다.');
        }
      });
      self.postMessage({ id: request.id, pages: pages.map(function (p) {
        return { width: p.t256[0], height: p.t257[0] };
      }) });
    } else if (request.type === 'page') {
      var page = pages && pages[request.page - 1];
      if (!page) throw new Error('페이지를 찾을 수 없습니다.');
      if (previous && previous !== page) delete previous.data;
      UTIF.decodeImage(source, page);
      var rgba = UTIF.toRGBA8(page);
      previous = page;
      self.postMessage({ id: request.id, width: page.width, height: page.height,
        orientation: page.t274 ? page.t274[0] : 1, rgba: rgba.buffer }, [rgba.buffer]);
    }
  } catch (error) {
    self.postMessage({ id: request.id, error: 'TIFF 페이지를 표시할 수 없습니다. 파일 형식과 크기를 확인해 주세요.' });
  }
};
