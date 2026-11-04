package com.agilesoda.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.filters.GlobalOpenApiMethodFilter;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.ResponseBody;

/** 실제 포털 REST API를 문서화하며 HTML 화면 이동은 API 목록에서 제외한다. */
@Configuration
public class SwaggerConfig {

    /** 접속한 서버의 context-path를 그대로 사용하는 포털 API 안내를 반환한다. */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("AI-OCR 포털 API")
                .version("v1")
                .description("업무·서식 신청, 판독결과 검토, 서식간 검증, 관리자 및 단말 mock API. "
                        + "로그인과 권한이 필요한 작업은 포털의 기존 SSO 로그인 세션을 사용합니다. "
                        + "Swagger는 권한을 부여하지 않으며, 실행한 변경 요청은 실제 업무에 반영됩니다."));
    }

    /** 기존 그룹 주소를 유지하면서 업무·관리자·단말 mock API를 함께 노출한다. */
    @Bean
    public GroupedOpenApi api() {
        return GroupedOpenApi.builder()
                .group("springdoc-openapi")
                .packagesToScan("com.agilesoda")
                .pathsToMatch("/api/**", "/module/**", "/device-mock/**")
                .build();
    }

    /** 본문 응답과 파일 다운로드만 허용하여 기본 명세와 그룹 명세에서 화면 URL을 제외한다. */
    @Bean
    public GlobalOpenApiMethodFilter restApiMethodFilter() {
        return method -> AnnotatedElementUtils.hasAnnotation(method, ResponseBody.class)
                || AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), ResponseBody.class)
                || HttpEntity.class.isAssignableFrom(method.getReturnType());
    }
}
