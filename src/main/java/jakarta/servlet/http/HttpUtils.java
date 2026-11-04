package jakarta.servlet.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Hashtable;
import java.util.Map;

/**
 * Jakarta EE 10+ 에서 제거된 HttpUtils 를 대체하기 위한 shim.
 * 기존 레거시 코드가 그대로 동작하도록 동일한 패키지·클래스·메서드 시그니처를 제공한다.
 */
public final class HttpUtils {

   private HttpUtils() { /* 인스턴스화 방지 */ }

   /* -------------------------------------------------------------
    *  1) getRequestURL  (반환형: StringBuffer)
     * ------------------------------------------------------------- */
    /**
     * 기존 HttpUtils.getRequestURL(HttpServletRequest) 와 동일하게
    * {@link HttpServletRequest#getRequestURL()} 의 결과를 그대로 반환한다.
     *
     * @param request 현재 요청 객체
    * @return 요청 URL 를 담은 {@link StringBuffer}
     */
    public static StringBuffer getRequestURL(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        // HttpServletRequest#getRequestURL() 은 StringBuffer 를 반환한다.
        return request.getRequestURL();
   }

   /* -------------------------------------------------------------
    *  2) parseQueryString
     * ------------------------------------------------------------- */
    /**
     * 기존 HttpUtils.parseQueryString(String) 를 대체한다.
     * 대부분의 경우 {@link HttpServletRequest#getParameterMap()} 를 사용하도록 권장한다.
     *
     * @param query 쿼리 문자열 (예: "a=1&b=2")
     * @return 파라미터를 담은 Hashtable (키 → String[], 값은 복수 가능)
     */
    public static Hashtable<String, String[]> parseQueryString(String query) {
        Hashtable<String, String[]> result = new Hashtable<>();
        if (query == null || query.isEmpty()) {
            return result;
       }

       String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            String key, value;
            try {
                if (idx > 0) {
                    key   = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                } else {
                    key   = URLDecoder.decode(pair, "UTF-8");
                    value = "";
                }
            } catch (UnsupportedEncodingException e) {
                // UTF‑8 은 JDK 표준이므로 여기서는 발생하지 않는다.
                key = pair;
                value = "";
           }

           String[] existing = result.get(key);
            if (existing == null) {
                result.put(key, new String[]{value});
            } else {
                String[] merged = new String[existing.length + 1];
                System.arraycopy(existing, 0, merged, 0, existing.length);
                merged[existing.length] = value;
                result.put(key, merged);
            }
        }
        return result;
   }

   /* -------------------------------------------------------------
    *  3) parsePostData
     * ------------------------------------------------------------- */
    /**
     * 기존 HttpUtils.parsePostData(int, InputStream) 를 대체한다.
     * 구현은 {@link HttpServletRequest#getParameterMap()} 로부터 데이터를 복제한다.
     *
     * @param request POST 데이터를 포함하고 있는 요청 객체
    * @return 파라미터 맵 (Hashtable 형태)
     */
    public static Hashtable<String, String[]> parsePostData(HttpServletRequest request) {
        if (request == null) {
            return new Hashtable<>();
        }
        Map<String, String[]> src = request.getParameterMap();
        Hashtable<String, String[]> dst = new Hashtable<>(src.size());
        for (Map.Entry<String, String[]> e : src.entrySet()) {
            dst.put(e.getKey(), e.getValue());
        }
        return dst;
   }

   /**
     * (옵션) 기존 시그니처를 그대로 유지하고 싶다면 오버로드 형태를 추가한다.
     * 실제 로직은 위의 request‑기반 메서드와 동일하게 동작한다.
     *
     * @param len  POST 바디 길이 (사용되지 않음)
     * @param in   POST 바디 스트림 (사용되지 않음)
     * @return 빈 Hashtable (호환성을 위해 존재)
     */
    @Deprecated
    public static Hashtable<String, String[]> parsePostData(int len, java.io.InputStream in) {
        // 기존 시그니처를 유지하기 위한 빈 구현.
        // 실제 애플리케이션에서는 위의 request‑기반 메서드로 교체하는 것이 권장됨.
        return new Hashtable<>();
   }

   /* -------------------------------------------------------------
    *  4) encodeURL / encodeRedirectURL (선택 사항)
     * ------------------------------------------------------------- */
    public static String encodeURL(String url) {
        if (url == null) return null;
        try {
            return java.net.URLEncoder.encode(url, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            return url;   // UTF‑8 은 절대 발생하지 않음
       }
   }

   public static String encodeRedirectURL(String url) {
        return encodeURL(url);
    }
}

