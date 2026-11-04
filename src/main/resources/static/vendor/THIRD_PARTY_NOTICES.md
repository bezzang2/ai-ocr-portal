# 파일 뷰어 라이브러리

은행 내부 환경에서 CDN 연결 없이 실행하도록 필요한 배포 파일만 포함한다.
원본 npm 패키지는 설치 스크립트를 실행하지 않고 추출했다.

| 라이브러리 | 버전 | 출처 | 라이선스 |
| --- | --- | --- | --- |
| PDF.js pdfjs-dist | 6.4.299 | https://github.com/mozilla/pdf.js | Apache 2.0, pdfjs/LICENSE 및 fonts·wasm 내 개별 라이선스 |
| UTIF.js | 3.1.0 (로컬 수정) | https://github.com/photopea/UTIF.js | MIT, tiff/LICENSE |
| pako | 2.1.0 | https://github.com/nodeca/pako | MIT 및 Zlib, tiff/pako-LICENSE |

PDF 본체와 worker 버전은 일치시킨다. 폰트·CMap·WASM 보조 자원과 해당 라이선스를 함께 보존한다.
TIFF worker는 UTIF.js와 pako를 상대 경로로 로드한다.

UTIF.js는 npm 최신 배포본(3.1.0)에 없는 upstream master(2026-10-26 확인) 동작 하나를 이식했다.
Photometric=RGB JPEG 압축 TIFF에 Adobe 표식이 없고 성분 ID가 1,2,3이 아니면 YCbCr 변환 없이 디코딩한다.
수정 위치는 `UTIF.decode._decodeNewJPEG`와 `UTIF.decode._jpegRgbComponentIds`의 `kjbank 수정` 주석이다.
라이브러리를 갱신할 때 upstream 반영 여부를 확인하고 이 수정을 제거하거나 다시 적용한다.
