package com.example.quanlydaotao.n8.config;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class N8OpenApiSchemaConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public GlobalOpenApiCustomizer n8RegisterMissingSchemas() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }

            Components components = openApi.getComponents();

            register(
                    components,
                    "grade_components",
                    gradeComponentSchema()
            );

            register(
                    components,
                    "student_component_grades",
                    studentComponentGradeSchema()
            );

            register(
                    components,
                    "student_summaries",
                    studentSummarySchema()
            );

            register(
                    components,
                    "grade_scales",
                    gradeScaleSchema()
            );
        };
    }

    private void register(
            Components components,
            String name,
            Schema<?> schema
    ) {
        if (components.getSchemas() == null
                || !components.getSchemas().containsKey(name)) {
            components.addSchemas(name, schema);
        }
    }

    private ObjectSchema gradeComponentSchema() {
        ObjectSchema schema = baseSchema("Thành phần điểm của lớp");

        schema.addProperty("course_class_id", uuid());
        schema.addProperty("component_code", new StringSchema());
        schema.addProperty("component_name", new StringSchema());
        schema.addProperty("weight_percentage", new NumberSchema());
        schema.addProperty(
                "min_score",
                new NumberSchema().nullable(true)
        );
        schema.addProperty(
                "max_score",
                new NumberSchema().nullable(true)
        );
        schema.addProperty("is_required", new BooleanSchema());
        schema.addProperty(
                "input_order",
                new IntegerSchema().nullable(true)
        );

        return schema;
    }

    private ObjectSchema studentComponentGradeSchema() {
        ObjectSchema schema = baseSchema("Điểm thành phần của sinh viên");

        schema.addProperty("registration_id", uuid());
        schema.addProperty("component_id", uuid());
        schema.addProperty("score", new NumberSchema().nullable(true));
        schema.addProperty("is_retake", new BooleanSchema());
        schema.addProperty("is_locked", new BooleanSchema());
        schema.addProperty("graded_at", nullableDateTime());
        schema.addProperty("graded_by", uuid().nullable(true));
        schema.addProperty("note", new StringSchema().nullable(true));

        return schema;
    }

    private ObjectSchema studentSummarySchema() {
        ObjectSchema schema = baseSchema("Tổng kết học phần");

        schema.addProperty("registration_id", uuid());
        schema.addProperty(
                "total_score",
                new NumberSchema().nullable(true)
        );
        schema.addProperty("scale_id", uuid().nullable(true));
        schema.addProperty(
                "letter_grade",
                new StringSchema().nullable(true)
        );
        schema.addProperty(
                "gpa_value",
                new NumberSchema().nullable(true)
        );
        schema.addProperty("result", new StringSchema().nullable(true));
        schema.addProperty("is_finalized", new BooleanSchema());

        return schema;
    }

    private ObjectSchema gradeScaleSchema() {
        ObjectSchema schema = baseSchema("Thang điểm quy đổi");

        schema.addProperty("scale_code", new StringSchema());
        schema.addProperty("min_score", new NumberSchema());
        schema.addProperty("max_score", new NumberSchema());
        schema.addProperty("letter_grade", new StringSchema());
        schema.addProperty("gpa_value", new NumberSchema());
        schema.addProperty("is_pass", new BooleanSchema());

        return schema;
    }

    private ObjectSchema baseSchema(String description) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription(description);

        schema.addProperty("id", uuid());
        schema.addProperty("is_active", new BooleanSchema());

        schema.addProperty(
                "created_at",
                new StringSchema().format("date-time")
        );
        schema.addProperty("updated_at", nullableDateTime());
        schema.addProperty("deleted_at", nullableDateTime());

        schema.addProperty("created_by", uuid().nullable(true));
        schema.addProperty("updated_by", uuid().nullable(true));
        schema.addProperty("deleted_by", uuid().nullable(true));

        return schema;
    }

    private Schema<?> uuid() {
        return new StringSchema().format("uuid");
    }

    private Schema<?> nullableDateTime() {
        return new StringSchema()
                .format("date-time")
                .nullable(true);
    }
}