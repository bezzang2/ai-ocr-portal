package com.agilesoda.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.TestPropertySource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DB나 엔진 호출 없이 실제 컨트롤러 매핑으로 OpenAPI 전체를 생성해 검증한다. */
@SpringBootTest(classes = SwaggerDocumentationTest.DocumentationApplication.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
        "logging.config=classpath:log4j2-test.xml", "aiocr.device-mock.enabled=true", "spring.profiles.active=swagger-test",
        "aiocr.file.path=/tmp/swagger-portal-fixtures", "sso.server-url=http://localhost/aiportal",
        "sso.nls-url=http://localhost", "sso.nls-port=1", "sso.uurl=/aiportal"
})
class SwaggerDocumentationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired private WebApplicationContext context;
    @Autowired private RequestMappingHandlerMapping mappings;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void documentsEveryRestHandlerAndExcludesHtmlViews() throws Exception {
        JsonNode document = document("/api-docs/springdoc-openapi");
        JsonNode paths = document.path("paths");
        var filter = context.getBean(SwaggerConfig.class).restApiMethodFilter();
        // 새 REST API를 만들 때 그룹 노출과 한국어 요약이 함께 유지되어야 한다.
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.agilesoda")) return;
            boolean api = filter.isMethodToInclude(handler.getMethod());
            mapping.getPatternValues().forEach(path -> {
                if (api) {
                    assertThat(paths.has(path)).as("문서에 포함할 API: %s", path).isTrue();
                    assertThat(handler.getMethodAnnotation(Operation.class))
                            .as("API 설명: %s", path).isNotNull();
                } else {
                    assertThat(paths.has(path)).as("화면 경로는 제외: %s", path).isFalse();
                }
            });
        });
        assertThat(paths.has("/module/ocr/result/lefrm/{wkId}/{lefrmId}/viewer-meta")).isTrue();
        assertThat(paths.has("/module/ocr/result/lefrm/{wkId}/{lefrmId}/role")).isTrue();
        assertThat(paths.has("/module/apply/file/{fileId}/download")).isTrue();
        assertThat(paths.has("/device-mock/send")).isTrue();
        assertThat(document.path("security").isMissingNode()).isTrue();
        Files.createDirectories(Path.of("build/swagger"));
        Files.writeString(Path.of("build/swagger/portal-openapi.json"), document.toPrettyString());
    }

    @Test
    void defaultDocumentAlsoExcludesViewsAndUsesCurrentContext() throws Exception {
        JsonNode document = document("/api-docs");
        assertThat(document.path("paths").has("/module/ocr/result")).isFalse();
        assertThat(document.path("paths").has("/module/ocr/result/trans/{transId}/raw")).isTrue();
        assertThat(document.path("servers").toString()).doesNotContain("smartit.kjbank.com");
    }

    @Test
    void describesMultipartAndBinaryResponses() throws Exception {
        JsonNode paths = document("/api-docs/springdoc-openapi").path("paths");
        JsonNode apply = paths.path("/api/v1/wkflow/apply").path("post")
                .path("requestBody").path("content").path("multipart/form-data").path("schema").path("properties");
        assertThat(apply.has("approval_list")).isTrue();
        assertThat(apply.has("wkflow_list")).isTrue();
        assertThat(apply.has("prgs_st_cd")).isTrue();
        assertThat(apply.path("attach_file").path("items").path("format").asText()).isEqualTo("binary");
        assertThat(paths.path("/module/ocr/result/lefrm/{wkId}/{lefrmId}/pages/{pageNumber}/image")
                .path("get").path("responses").path("200").path("content").has("image/png")).isTrue();
        assertThat(paths.path("/module/admin/auditLog/execelDownLoad").path("post")
                .path("responses").path("200").path("content")
                .has("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
    }

    @Test
    void servesSwaggerEntryAndConfiguration() throws Exception {
        mvc.perform(get("/swagger-portal.html")).andExpect(status().is3xxRedirection());
        JsonNode config = document("/api-docs/swagger-config");
        assertThat(config.path("urls").toString()).contains("/api-docs/springdoc-openapi");
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    /** 운영 설정처럼 mock을 끄면 해당 API가 명세에서도 빠져야 한다. */
    @Nested
    @TestPropertySource(properties = "aiocr.device-mock.enabled=false")
    class DeviceMockDisabled {
        @Autowired private WebApplicationContext disabledContext;

        @Test
        void omitsDisabledDeviceMockEndpoints() throws Exception {
            MockMvc disabledMvc = MockMvcBuilders.webAppContextSetup(disabledContext).build();
            JsonNode paths = JSON.readTree(disabledMvc.perform(get("/api-docs/springdoc-openapi"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("paths");
            paths.fieldNames().forEachRemaining(path -> assertThat(path).doesNotStartWith("/device-mock"));
            assertThat(paths.has("/module/ocr/result/lefrm/{wkId}/{lefrmId}/review")).isTrue();
        }
    }

    private JsonNode document(String path) throws Exception {
        return JSON.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    /** 외부 연계와 업무 로직을 실행하지 않고 컨트롤러의 매핑·타입 정보만 등록한다. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SwaggerConfig.class)
    static class DocumentationApplication {
        /** 조건부 등록을 실제 환경과 같은 방식으로 평가한 뒤 컨트롤러를 mock으로 제공한다. */
        @Bean
        static BeanDefinitionRegistryPostProcessor documentationControllers(Environment environment) {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    var scanner = new ClassPathScanningCandidateComponentProvider(false);
                    scanner.setEnvironment(environment);
                    scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
                    scanner.findCandidateComponents("com.agilesoda").forEach(candidate -> {
                        Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), getClass().getClassLoader());
                        var definition = new RootBeanDefinition(type);
                        definition.setInstanceSupplier(() -> Mockito.mock(type));
                        registry.registerBeanDefinition(type.getName(), definition);
                    });
                }
            };
        }
    }
}
