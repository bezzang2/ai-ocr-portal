# Pretendard 폰트 파일 안내

`css/tokens/primitive.css`의 `@font-face`가 이 폴더의 `PretendardVariable.woff2`를 참조합니다.

- 공식 배포처: https://github.com/orioncactus/pretendard (dist/web/variable/PretendardVariable.woff2)
- 은행 인트라넷 환경은 보안정책상 외부 CDN(jsdelivr 등) 접근이 차단되는 경우가 많으므로,
  운영 반영 전 이 폴더에 파일을 내려받아 자체 호스팅하는 것을 권장합니다.
- 가변 폰트를 지원하지 않는 구형 브라우저 대응이 필요하면 웨이트별 정적 파일
  (Regular/Medium/SemiBold/Bold)도 함께 받아 `@font-face`를 웨이트별로 분리하세요.
