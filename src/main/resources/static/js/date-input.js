/* date input 플레이스홀더 톤 제어.
   <input type="date">는 placeholder 속성이 없어 값이 비어있을 때도 .input의 고정 color가
   그대로 적용된다. 값 유무에 따라 .has-value를 토글해 css/components/input.css의
   .input[type="date"] / .input[type="date"].has-value 색 분기가 동작하게 한다. */
(function () {
  function sync(el) {
    el.classList.toggle("has-value", !!el.value);
  }

  function init() {
    document.querySelectorAll('input[type="date"]').forEach(function (el) {
      sync(el);
      el.addEventListener("input", function () { sync(el); });
      el.addEventListener("change", function () { sync(el); });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
