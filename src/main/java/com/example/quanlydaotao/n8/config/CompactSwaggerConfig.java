package com.example.quanlydaotao.n8.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class CompactSwaggerConfig {

    private static final String BASE = "/api/n8";

    private static final String CLASS =
            BASE + "/classes/{classId}";

    private static final Map<String, Set<PathItem.HttpMethod>> VISIBLE =
            Map.ofEntries(
                    Map.entry(
                            BASE + "/manage",
                            Set.of(PathItem.HttpMethod.GET)
                    ),

                    Map.entry(
                            CLASS + "/students",
                            Set.of(PathItem.HttpMethod.POST)
                    ),

                    Map.entry(
                            CLASS + "/students/{registrationId}",
                            Set.of(PathItem.HttpMethod.DELETE)
                    ),

                    Map.entry(
                            CLASS + "/components/view",
                            Set.of(PathItem.HttpMethod.GET)
                    ),

                    Map.entry(
                            CLASS + "/components",
                            Set.of(PathItem.HttpMethod.POST)
                    ),

                    Map.entry(
                            CLASS + "/components/{componentId}",
                            Set.of(
                                    PathItem.HttpMethod.PUT,
                                    PathItem.HttpMethod.DELETE
                            )
                    ),

                    Map.entry(
                            CLASS + "/grades/view",
                            Set.of(PathItem.HttpMethod.GET)
                    ),

                    Map.entry(
                            CLASS + "/grades",
                            Set.of(PathItem.HttpMethod.POST)
                    ),

                    Map.entry(
                            CLASS + "/grades/{gradeId}",
                            Set.of(
                                    PathItem.HttpMethod.PUT,
                                    PathItem.HttpMethod.DELETE
                            )
                    ),

                    Map.entry(
                            CLASS + "/summaries/view",
                            Set.of(PathItem.HttpMethod.GET)
                    ),

                    Map.entry(
                            CLASS + "/summaries/action",
                            Set.of(PathItem.HttpMethod.POST)
                    ),

                    Map.entry(
                            CLASS + "/summaries/{summaryId}",
                            Set.of(PathItem.HttpMethod.DELETE)
                    ),

                    Map.entry(
                            BASE + "/scales/view",
                            Set.of(PathItem.HttpMethod.GET)
                    ),

                    Map.entry(
                            BASE + "/scales",
                            Set.of(PathItem.HttpMethod.POST)
                    ),

                    Map.entry(
                            BASE + "/scales/{scaleId}",
                            Set.of(
                                    PathItem.HttpMethod.PUT,
                                    PathItem.HttpMethod.DELETE
                            )
                    ),

                    Map.entry(
                            BASE + "/students/{studentId}/transcript/view",
                            Set.of(PathItem.HttpMethod.GET)
                    )
            );

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public GlobalOpenApiCustomizer compactN8Documentation() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            for (String path :
                    new ArrayList<>(openApi.getPaths().keySet())) {

                if (!path.startsWith(BASE + "/")) {
                    continue;
                }

                Set<PathItem.HttpMethod> allowed =
                        VISIBLE.get(path);

                if (allowed == null) {
                    openApi.getPaths().remove(path);
                    continue;
                }

                PathItem item = openApi.getPaths().get(path);

                if (!allowed.contains(PathItem.HttpMethod.GET)) {
                    item.setGet(null);
                }

                if (!allowed.contains(PathItem.HttpMethod.POST)) {
                    item.setPost(null);
                }

                if (!allowed.contains(PathItem.HttpMethod.PUT)) {
                    item.setPut(null);
                }

                if (!allowed.contains(PathItem.HttpMethod.DELETE)) {
                    item.setDelete(null);
                }

                if (!allowed.contains(PathItem.HttpMethod.PATCH)) {
                    item.setPatch(null);
                }

                if (!allowed.contains(PathItem.HttpMethod.HEAD)) {
                    item.setHead(null);
                }

                if (!allowed.contains(PathItem.HttpMethod.OPTIONS)) {
                    item.setOptions(null);
                }

                if (!allowed.contains(PathItem.HttpMethod.TRACE)) {
                    item.setTrace(null);
                }

                item.readOperations().forEach(
                        operation -> operation.setTags(
                                List.of(tag(path))
                        )
                );
            }

            List<String> names = List.of(
                    "01. Lớp học phần và sinh viên",
                    "02. Thành phần điểm",
                    "03. Điểm thành phần sinh viên",
                    "04. Tổng kết học phần",
                    "05. Thang điểm quy đổi",
                    "06. Bảng điểm sinh viên"
            );

            List<Tag> tags = new ArrayList<>();

            if (openApi.getTags() != null) {
                for (Tag existing : openApi.getTags()) {
                    if (!names.contains(existing.getName())) {
                        tags.add(existing);
                    }
                }
            }

            for (String name : names) {
                tags.add(new Tag().name(name));
            }

            openApi.setTags(tags);
        };
    }

    private static String tag(String path) {
        if (path.contains("/components")) {
            return "02. Thành phần điểm";
        }

        if (path.contains("/grades")) {
            return "03. Điểm thành phần sinh viên";
        }

        if (path.contains("/summaries")) {
            return "04. Tổng kết học phần";
        }

        if (path.startsWith(BASE + "/scales")) {
            return "05. Thang điểm quy đổi";
        }

        if (path.contains("/transcript")) {
            return "06. Bảng điểm sinh viên";
        }

        return "01. Lớp học phần và sinh viên";
    }
}