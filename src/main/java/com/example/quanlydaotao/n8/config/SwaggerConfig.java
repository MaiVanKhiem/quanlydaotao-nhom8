package com.example.quanlydaotao.n8.config;

import java.math.BigDecimal;
import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI nhom8OpenAPI() {
        Components models = new Components()
                .addSchemas("grade_components", component())
                .addSchemas("student_component_grades", grade())
                .addSchemas("student_summaries", summary())
                .addSchemas("grade_scales", scale());

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Nhóm 8 · Đánh giá & Bảng điểm")
                                .version("1.0.0")
                                .description(
                                        "Quản lý thành phần điểm, điểm sinh viên, "
                                                + "tổng kết và thang quy đổi."
                                )
                )
                .servers(
                        List.of(
                                new Server()
                                        .url("/")
                                        .description("Máy chủ hiện tại")
                        )
                )
                .components(models)
                .tags(
                        List.of(
                                tag(
                                        "01. Lớp học phần và sinh viên",
                                        "Chọn lớp, tìm sinh viên và quản lý đăng ký."
                                ),
                                tag(
                                        "02. Thành phần điểm",
                                        "Cấu hình trọng số và ngưỡng điểm."
                                ),
                                tag(
                                        "03. Điểm thành phần sinh viên",
                                        "Nhập điểm, ghi chú và đánh dấu thi lại."
                                ),
                                tag(
                                        "04. Tổng kết học phần",
                                        "Tính tổng kết, chốt, khóa và mở điểm."
                                ),
                                tag(
                                        "05. Thang điểm quy đổi",
                                        "Quy đổi hệ 10 sang điểm chữ và hệ 4."
                                ),
                                tag(
                                        "06. Bảng điểm sinh viên",
                                        "Tra cứu học phần đã chốt và xuất CSV."
                                )
                        )
                );
    }

    @Bean
    public OpenApiCustomizer nhom8ResponseSchemas() {
        return api -> {
            arrayResponse(
                    api,
                    "/api/n8/classes/{classId}/components",
                    "grade_components"
            );

            arrayResponse(
                    api,
                    "/api/n8/classes/{classId}/grades",
                    "student_component_grades"
            );

            arrayResponse(
                    api,
                    "/api/n8/classes/{classId}/summaries",
                    "student_summaries"
            );

            arrayResponse(
                    api,
                    "/api/n8/scales",
                    "grade_scales"
            );

            if (api.getPaths() == null) {
                return;
            }

            var path = api.getPaths()
                    .get("/api/n8/classes/{classId}/board");

            if (path != null
                    && path.getGet() != null
                    && path.getGet().getResponses() != null) {

                Schema<?> board = new ObjectSchema();

                board.addProperty("info", new ObjectSchema());

                board.addProperty(
                        "students",
                        new ArraySchema().items(new ObjectSchema())
                );

                board.addProperty(
                        "components",
                        array("grade_components")
                );

                board.addProperty(
                        "grades",
                        array("student_component_grades")
                );

                board.addProperty(
                        "summaries",
                        array("student_summaries")
                );

                path.getGet()
                        .getResponses()
                        .addApiResponse("200", response(board));
            }
        };
    }

    private ArraySchema array(String name) {
        return new ArraySchema().items(
                new Schema<>()
                        .$ref("#/components/schemas/" + name)
        );
    }

    private ApiResponse response(Schema<?> schema) {
        return new ApiResponse()
                .description("Thành công")
                .content(
                        new Content().addMediaType(
                                "application/json",
                                new MediaType().schema(schema)
                        )
                );
    }

    private void arrayResponse(
            OpenAPI api,
            String route,
            String model
    ) {
        if (api.getPaths() == null) {
            return;
        }

        var path = api.getPaths().get(route);

        if (path != null
                && path.getGet() != null
                && path.getGet().getResponses() != null) {

            path.getGet()
                    .getResponses()
                    .addApiResponse(
                            "200",
                            response(array(model))
                    );
        }
    }

    private Tag tag(String name, String description) {
        return new Tag()
                .name(name)
                .description(description);
    }

    private Schema<?> uuid(String description) {
        return new StringSchema()
                .format("uuid")
                .description(description);
    }

    private Schema<?> string(
            int max,
            String description
    ) {
        return new StringSchema()
                .maxLength(max)
                .description(description);
    }

    private Schema<?> decimal(
            int max,
            String description
    ) {
        return new NumberSchema()
                .minimum(BigDecimal.ZERO)
                .maximum(BigDecimal.valueOf(max))
                .multipleOf(new BigDecimal("0.01"))
                .description(description);
    }

    private Schema<?> optional(Schema<?> schema) {
        schema.setNullable(true);
        return schema;
    }

    private Schema<?> object(
            String description,
            String... required
    ) {
        Schema<?> schema = new ObjectSchema()
                .description(description);

        schema.setRequired(List.of(required));

        return schema;
    }

    private Schema<?> audit(Schema<?> schema) {
        schema.addProperty(
                "is_active",
                new BooleanSchema()
                        .description("Trạng thái hiệu lực")
        );

        schema.addProperty(
                "created_at",
                new StringSchema()
                        .format("date-time")
                        .description("Thời điểm tạo")
        );

        schema.addProperty(
                "updated_at",
                optional(
                        new StringSchema()
                                .format("date-time")
                                .description("Thời điểm cập nhật")
                )
        );

        schema.addProperty(
                "created_by",
                optional(uuid("Người tạo"))
        );

        schema.addProperty(
                "updated_by",
                optional(uuid("Người cập nhật"))
        );

        schema.addProperty(
                "deleted_at",
                optional(
                        new StringSchema()
                                .format("date-time")
                                .description("Thời điểm xóa mềm")
                )
        );

        schema.addProperty(
                "deleted_by",
                optional(uuid("Người xóa mềm"))
        );

        return schema;
    }

    private Schema<?> component() {
        Schema<?> schema = object(
                "Thành phần điểm của một lớp học phần.",
                "id",
                "course_class_id",
                "component_code",
                "component_name",
                "weight_percentage",
                "is_required",
                "is_active",
                "created_at"
        );

        schema.addProperty(
                "id",
                uuid("Khóa chính")
        );

        schema.addProperty(
                "course_class_id",
                uuid("Khóa ngoại tới courses_classes.id")
        );

        schema.addProperty(
                "component_code",
                string(20, "Mã: CC, GK, CK...")
        );

        schema.addProperty(
                "component_name",
                string(50, "Tên thành phần")
        );

        schema.addProperty(
                "weight_percentage",
                decimal(
                        100,
                        "Trọng số %; tổng các thành phần phải bằng 100"
                )
        );

        schema.addProperty(
                "min_score",
                optional(
                        decimal(10, "Điểm tối thiểu để đạt thành phần")
                )
        );

        schema.addProperty(
                "max_score",
                optional(
                        decimal(10, "Điểm tối đa; mặc định 10")
                )
        );

        schema.addProperty(
                "is_required",
                new BooleanSchema()
                        .description("Bắt buộc có điểm")
        );

        schema.addProperty(
                "input_order",
                optional(
                        new IntegerSchema()
                                .minimum(BigDecimal.ZERO)
                                .description("Thứ tự hiển thị")
                )
        );

        return audit(schema);
    }

    private Schema<?> grade() {
        Schema<?> schema = object(
                "Điểm thành phần của sinh viên thông qua lượt đăng ký.",
                "id",
                "registration_id",
                "component_id",
                "is_retake",
                "is_locked",
                "is_active",
                "created_at"
        );

        schema.addProperty("id", uuid("Khóa chính"));

        schema.addProperty(
                "registration_id",
                uuid("Khóa ngoại tới course_registrations.id")
        );

        schema.addProperty(
                "component_id",
                uuid("Khóa ngoại tới grade_components.id")
        );

        schema.addProperty(
                "score",
                optional(
                        decimal(10, "Điểm thực tế; NULL là chưa nhập")
                )
        );

        schema.addProperty(
                "is_retake",
                new BooleanSchema()
                        .description("Thi lại hoặc cải thiện")
        );

        schema.addProperty(
                "is_locked",
                new BooleanSchema()
                        .description("Đã khóa, không được sửa")
        );

        schema.addProperty(
                "graded_at",
                optional(
                        new StringSchema()
                                .format("date-time")
                                .description("Thời điểm chấm điểm")
                )
        );

        schema.addProperty(
                "graded_by",
                optional(
                        uuid("Người chấm; tham chiếu users.id")
                )
        );

        schema.addProperty(
                "note",
                optional(string(255, "Ghi chú"))
        );

        return audit(schema);
    }

    private Schema<?> summary() {
        Schema<?> schema = object(
                "Tổng kết của một lượt đăng ký học phần.",
                "id",
                "registration_id",
                "is_finalized",
                "is_active",
                "created_at"
        );

        schema.addProperty("id", uuid("Khóa chính"));

        schema.addProperty(
                "registration_id",
                uuid("Khóa ngoại tới course_registrations.id")
        );

        schema.addProperty(
                "total_score",
                optional(
                        decimal(10, "Tổng điểm hệ 10 sau khi nhân trọng số")
                )
        );

        schema.addProperty(
                "scale_id",
                optional(uuid("Khóa ngoại tới grade_scales.id"))
        );

        schema.addProperty(
                "letter_grade",
                optional(string(2, "Điểm chữ"))
        );

        schema.addProperty(
                "gpa_value",
                optional(decimal(4, "Điểm hệ 4"))
        );

        schema.addProperty(
                "result",
                optional(string(10, "PASS hoặc FAIL"))
        );

        schema.addProperty(
                "is_finalized",
                new BooleanSchema()
                        .description(
                                "Đã chốt và được đưa vào bảng điểm"
                        )
        );

        return audit(schema);
    }

    private Schema<?> scale() {
        Schema<?> schema = object(
                "Khoảng quy đổi điểm hệ 10 sang điểm chữ và GPA.",
                "id",
                "scale_code",
                "min_score",
                "max_score",
                "letter_grade",
                "gpa_value",
                "is_pass",
                "is_active",
                "created_at"
        );

        schema.addProperty("id", uuid("Khóa chính"));

        schema.addProperty(
                "scale_code",
                string(10, "Mã thang điểm")
        );

        schema.addProperty(
                "min_score",
                decimal(10, "Cận dưới, bao gồm giá trị này")
        );

        schema.addProperty(
                "max_score",
                decimal(10, "Cận trên, bao gồm giá trị này")
        );

        schema.addProperty(
                "letter_grade",
                string(2, "Điểm chữ tương ứng")
        );

        schema.addProperty(
                "gpa_value",
                decimal(4, "Điểm hệ 4")
        );

        schema.addProperty(
                "is_pass",
                new BooleanSchema()
                        .description("Có được tính là đạt hay không")
        );

        return audit(schema);
    }
}