"use strict";

const $ = id => document.getElementById(id);

const state = {
    classId: "",
    board: null,
    scales: [],
    transcript: [],
    dirty: false,
    busy: false
};

const key = value => String(value ?? "").toLowerCase();

const yes = value =>
    value === true || value === 1 || value === "1";

const h = value =>
    String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    }[character]));

const fmt = value =>
    value == null
        ? "—"
        : Number(value).toLocaleString("vi-VN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });

function message(text, error = false) {
    $("message").textContent = text;
    $("message").className = error ? "error" : "";
    $("message").hidden = false;
}

async function api(path, method = "GET", body) {
    const response = await fetch("/api/n8" + path, {
        method,
        cache: "no-store",
        headers: body === undefined
            ? {}
            : {"Content-Type": "application/json"},
        body: body === undefined
            ? undefined
            : JSON.stringify(body)
    });

    const raw = await response.text();
    let data;

    try {
        data = raw ? JSON.parse(raw) : {};
    } catch {
        throw new Error(
            "API không trả về JSON. Kiểm tra cửa sổ chạy Java."
        );
    }

    if (!response.ok) {
        throw new Error(
            data.message || `Lỗi API ${response.status}`
        );
    }

    return data;
}

async function run(task) {
    if (state.busy) return;

    state.busy = true;

    const controls = [
        ...document.querySelectorAll(
            "button, #classSelect, #filterStudent"
        )
    ];

    const previousDisabled = controls.map(
        element => element.disabled
    );

    controls.forEach(element => {
        element.disabled = true;
    });

    try {
        await task();
    } catch (error) {
        message(error.message || "Có lỗi xảy ra.", true);
    } finally {
        state.busy = false;

        controls.forEach((element, index) => {
            element.disabled = previousDisabled[index];
        });
    }
}

const bind = (id, task) =>
    $(id).addEventListener("click", () => run(task));

function path(suffix) {
    if (!state.classId) {
        throw new Error("Hãy chọn lớp học phần.");
    }

    return `/classes/${encodeURIComponent(state.classId)}${suffix}`;
}

function table(headers, rows) {
    return `
        <table>
            <thead>
                <tr>
                    ${headers.map(item => `<th>${item}</th>`).join("")}
                </tr>
            </thead>
            <tbody>
                ${
                    rows.length
                        ? rows.join("")
                        : `
                            <tr>
                                <td colspan="${headers.length}">
                                    Chưa có dữ liệu.
                                </td>
                            </tr>
                        `
                }
            </tbody>
        </table>
    `;
}

function discard() {
    return !state.dirty ||
        confirm("Có điểm chưa lưu. Bỏ các thay đổi chưa lưu?");
}

function resetForm(id) {
    $(id).reset();
    $(id).elements.namedItem("id").value = "";
}

function fillForm(id, values) {
    resetForm(id);

    for (const [name, value] of Object.entries(values)) {
        const input = $(id).elements.namedItem(name);

        if (!input) continue;

        if (input.type === "checkbox") {
            input.checked = yes(value);
        } else {
            input.value = value ?? "";
        }
    }

    $(id).scrollIntoView({
        behavior: "smooth",
        block: "center"
    });
}

function readForm(form, numericFields, checkboxFields) {
    const data = Object.fromEntries(new FormData(form));

    data.id = data.id || null;

    numericFields.forEach(name => {
        data[name] = data[name] === ""
            ? null
            : Number(data[name]);
    });

    checkboxFields.forEach(name => {
        data[name] = form.elements.namedItem(name).checked;
    });

    return data;
}

// ========================================================
// TẢI DANH SÁCH LỚP
// ========================================================

async function loadClasses() {
    const rows = await api("/classes");

    if (!Array.isArray(rows)) {
        throw new Error("API lớp không trả về danh sách.");
    }

    $("classSelect").innerHTML =
        '<option value="">— Chọn lớp học phần —</option>' +
        rows.map(row => `
            <option value="${h(row.id)}">
                ${h(row.class_code)} ·
                ${h(row.course_name)} ·
                ${h(row.semester_name)}
            </option>
        `).join("");

    const current = rows.find(
        row => key(row.id) === key(state.classId)
    );

    state.classId = current ? String(current.id) : "";
    $("classSelect").value = state.classId;

    if (!state.classId) {
        state.board = null;
        state.dirty = false;

        $("boardSection").hidden = true;

        $("classInfo").textContent =
            `Đã tải ${rows.length} lớp. Chọn lớp để bắt đầu.`;
    }

    return rows.length;
}

async function loadBoard() {
    if (!state.classId) return;

    state.board = await api(path("/board"));
    state.dirty = false;

    renderBoard();
    renderComponents();
}

// ========================================================
// HIỂN THỊ BẢNG NHẬP ĐIỂM
// ========================================================

function renderBoard() {
    const board = state.board;

    if (!board) return;

    $("boardSection").hidden = false;

    $("classInfo").textContent =
        `${board.info.class_code} · ` +
        `${board.info.course_name} · ` +
        `${board.info.semester_name}`;

    const totalWeight = board.components.reduce(
        (total, component) =>
            total + Number(component.weight_percentage),
        0
    );

    const finalizedCount = board.summaries.filter(
        summary => yes(summary.is_finalized)
    ).length;

    $("statistics").textContent =
        `${board.students.length} sinh viên · ` +
        `${board.components.length} thành phần · ` +
        `Trọng số ${totalWeight.toFixed(2)}% · ` +
        `${finalizedCount} đã chốt`;

    const summaries = new Map(
        board.summaries.map(summary => [
            key(summary.registration_id),
            summary
        ])
    );

    const grades = new Map(
        board.grades.map(grade => [
            key(grade.registration_id) +
                ":" +
                key(grade.component_id),
            grade
        ])
    );

    const filter = $("filterStudent")
        .value.trim()
        .toLocaleLowerCase("vi");

    const students = board.students.filter(student =>
        `${student.code} ${student.full_name}`
            .toLocaleLowerCase("vi")
            .includes(filter)
    );

    const headers = [
        "STT",
        "Mã SV",
        "Họ tên",
        ...board.components.map(component => `
            ${h(component.component_name)}
            <br>
            <small>
                ${h(component.weight_percentage)}%
                · tối đa ${h(component.max_score ?? 10)}
            </small>
        `),
        "Hệ 10",
        "Chữ",
        "Hệ 4",
        "Kết quả",
        "Trạng thái",
        "Thao tác"
    ];

    const rows = students.map((student, index) => {
        const summary = summaries.get(
            key(student.registration_id)
        );

        const finalized = yes(summary?.is_finalized);

        const cells = board.components.map(component => {
            const grade = grades.get(
                key(student.registration_id) +
                    ":" +
                    key(component.id)
            );

            const locked =
                finalized || yes(grade?.is_locked);

            return `
                <td class="grade-cell"
                    data-registration="${h(student.registration_id)}"
                    data-component="${h(component.id)}">

                    <input
                        class="score"
                        type="number"
                        min="0"
                        max="${h(component.max_score ?? 10)}"
                        step="0.01"
                        value="${h(grade?.score ?? "")}"
                        aria-label="${h(component.component_name)}"
                        ${locked ? "disabled" : ""}
                    >

                    <label>
                        <input
                            class="retake"
                            type="checkbox"
                            ${yes(grade?.is_retake) ? "checked" : ""}
                            ${locked ? "disabled" : ""}
                        >
                        Thi lại / cải thiện
                    </label>

                    <input
                        class="note"
                        maxlength="255"
                        placeholder="Ghi chú..."
                        value="${h(grade?.note ?? "")}"
                        ${locked ? "disabled" : ""}
                    >
                </td>
            `;
        }).join("");

        const result = !summary
            ? "—"
            : summary.result === "PASS"
                ? '<span class="badge pass">Đạt</span>'
                : '<span class="badge fail">Không đạt</span>';

        return `
            <tr>
                <td>${index + 1}</td>
                <td>${h(student.code)}</td>
                <td class="student-name">
                    ${h(student.full_name)}
                </td>

                ${cells}

                <td>${fmt(summary?.total_score)}</td>
                <td>${h(summary?.letter_grade || "—")}</td>
                <td>${fmt(summary?.gpa_value)}</td>
                <td>${result}</td>

                <td>
                    ${
                        finalized
                            ? "Đã chốt"
                            : summary
                                ? "Đã tính"
                                : "Chưa tính"
                    }
                </td>

                <td>
                    ${
                        finalized
                            ? ""
                            : `
                                <button
                                    type="button"
                                    class="small danger"
                                    data-remove="${h(student.registration_id)}">
                                    Bỏ khỏi lớp
                                </button>
                            `
                    }
                </td>
            </tr>
        `;
    });

    $("boardTable").innerHTML = table(headers, rows);
}

// ========================================================
// THÀNH PHẦN ĐIỂM
// ========================================================

function renderComponents() {
    $("componentTable").innerHTML = table(
        [
            "Mã",
            "Tên",
            "Trọng số",
            "Ngưỡng đạt",
            "Tối đa",
            "Bắt buộc",
            "Thao tác"
        ],
        state.board.components.map(component => `
            <tr>
                <td>${h(component.component_code)}</td>
                <td>${h(component.component_name)}</td>
                <td>${fmt(component.weight_percentage)}%</td>
                <td>${fmt(component.min_score)}</td>
                <td>${fmt(component.max_score)}</td>
                <td>${yes(component.is_required) ? "Có" : "Không"}</td>

                <td>
                    <button
                        type="button"
                        class="small secondary"
                        data-edit="${h(component.id)}">
                        Sửa
                    </button>

                    <button
                        type="button"
                        class="small danger"
                        data-delete="${h(component.id)}">
                        Xóa
                    </button>
                </td>
            </tr>
        `)
    );
}

// ========================================================
// THANG ĐIỂM
// ========================================================

async function loadScales() {
    state.scales = await api("/scales");

    $("scaleTable").innerHTML = table(
        ["Mã", "Từ", "Đến", "Chữ", "Hệ 4", "Kết quả", "Thao tác"],

        state.scales.map(scale => `
            <tr>
                <td>${h(scale.scale_code)}</td>
                <td>${fmt(scale.min_score)}</td>
                <td>${fmt(scale.max_score)}</td>
                <td>${h(scale.letter_grade)}</td>
                <td>${fmt(scale.gpa_value)}</td>
                <td>${yes(scale.is_pass) ? "Đạt" : "Không đạt"}</td>

                <td>
                    <button
                        type="button"
                        class="small secondary"
                        data-edit="${h(scale.id)}">
                        Sửa
                    </button>

                    <button
                        type="button"
                        class="small danger"
                        data-delete="${h(scale.id)}">
                        Xóa
                    </button>
                </td>
            </tr>
        `)
    );
}

// ========================================================
// TÌM SINH VIÊN
// ========================================================

async function searchStudents(inputId, selectId) {
    const keyword = $(inputId).value.trim();

    const rows = await api(
        "/students?keyword=" + encodeURIComponent(keyword)
    );

    const select = $(selectId);

    select.innerHTML =
        (
            select.multiple
                ? ""
                : '<option value="">— Chọn sinh viên —</option>'
        ) +
        rows.map(student => `
            <option value="${h(student.id)}">
                ${h(student.code)} · ${h(student.full_name)}
            </option>
        `).join("");
}

// ========================================================
// LƯU ĐIỂM
// ========================================================

async function saveGrades() {
    const grades = [
        ...document.querySelectorAll(
            "#boardTable .grade-cell"
        )
    ]
        .filter(cell =>
            !cell.querySelector(".score").disabled
        )
        .map(cell => {
            const score = cell.querySelector(".score");

            if (!score.checkValidity()) {
                score.reportValidity();

                throw new Error(
                    "Có điểm không hợp lệ. Kiểm tra ô vừa nhập."
                );
            }

            return {
                registration_id: cell.dataset.registration,
                component_id: cell.dataset.component,
                score: score.value === ""
                    ? null
                    : Number(score.value),
                is_retake: cell.querySelector(".retake").checked,
                note: cell.querySelector(".note").value
            };
        });

    const result = await api(
        path("/grades"),
        "POST",
        {grades}
    );

    state.dirty = false;
    return result;
}

// ========================================================
// XUẤT CSV
// ========================================================

function csv(filename, rows) {
    const quote = value => {
        let text = String(value ?? "");

        if (/^[=+\-@\t\r]/.test(text)) {
            text = "'" + text;
        }

        return '"' + text.replaceAll('"', '""') + '"';
    };

    const data =
        "\uFEFF" +
        rows.map(row =>
            row.map(quote).join(",")
        ).join("\r\n");

    const url = URL.createObjectURL(
        new Blob([data], {
            type: "text/csv;charset=utf-8"
        })
    );

    const anchor = document.createElement("a");

    anchor.href = url;
    anchor.download = filename;

    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ========================================================
// BẢNG ĐIỂM SINH VIÊN
// ========================================================

function renderTranscript() {
    $("transcriptTable").innerHTML = table(
        [
            "Học kỳ",
            "Mã môn",
            "Tên môn",
            "Lớp",
            "Tín chỉ",
            "Hệ 10",
            "Chữ",
            "Hệ 4",
            "Kết quả"
        ],

        state.transcript.map(row => `
            <tr>
                <td>${h(row.semester_name)}</td>
                <td>${h(row.course_code)}</td>
                <td>${h(row.course_name)}</td>
                <td>${h(row.class_code)}</td>
                <td>${h(row.credits)}</td>
                <td>${fmt(row.total_score)}</td>
                <td>${h(row.letter_grade)}</td>
                <td>${fmt(row.gpa_value)}</td>
                <td>${row.result === "PASS" ? "Đạt" : "Không đạt"}</td>
            </tr>
        `)
    );
}

// ========================================================
// SỰ KIỆN CHỌN LỚP VÀ NHẬP ĐIỂM
// ========================================================

$("classSelect").addEventListener("change", () => {
    if (!discard()) {
        $("classSelect").value = state.classId;
        return;
    }

    state.classId = $("classSelect").value;
    state.dirty = false;

    resetForm("componentForm");

    if (!state.classId) {
        $("boardSection").hidden = true;
        return;
    }

    run(loadBoard);
});

$("boardTable").addEventListener("input", event => {
    if (event.target.matches("input")) {
        state.dirty = true;
        message("Có điểm chưa lưu.");
    }
});

$("filterStudent").addEventListener("input", () => {
    if (state.dirty) {
        message("Lưu điểm trước khi lọc sinh viên.", true);
        return;
    }

    renderBoard();
});

bind("reload", async () => {
    if (!discard()) return;

    const count = await loadClasses();

    await loadScales();
    await loadBoard();

    message(`Đã tải lại ${count} lớp học phần.`);
});

bind("saveGrades", async () => {
    const result = await saveGrades();

    await loadBoard();
    message(result.message);
});

for (const action of ["calculate", "finalize", "unlock"]) {
    bind(action, async () => {
        if (action !== "calculate") {
            const question = action === "finalize"
                ? "Chốt điểm toàn lớp?"
                : "Mở lại điểm toàn lớp?";

            if (!confirm(question)) return;
        }

        if (state.dirty) {
            await saveGrades();
        }

        const result = await api(
            path("/" + action),
            "POST"
        );

        await loadBoard();

        state.transcript = [];
        renderTranscript();

        message(result.message);
    });
}

// ========================================================
// THÊM VÀ BỎ SINH VIÊN KHỎI LỚP
// ========================================================

bind("searchStudents", () =>
    searchStudents("studentKeyword", "studentSelect")
);

bind("enrollStudents", async () => {
    if (!discard()) return;

    const ids = [
        ...$("studentSelect").selectedOptions
    ].map(option => option.value);

    if (!ids.length) {
        throw new Error("Hãy chọn ít nhất một sinh viên.");
    }

    const result = await api(
        path("/students"),
        "POST",
        {student_ids: ids}
    );

    await loadBoard();
    message(result.message);
});

$("boardTable").addEventListener("click", event => {
    const button = event.target.closest("[data-remove]");

    if (!button) return;

    run(async () => {
        if (!discard()) return;
        if (!confirm("Bỏ sinh viên khỏi lớp?")) return;

        const result = await api(
            path("/students/" + button.dataset.remove),
            "DELETE"
        );

        await loadBoard();
        message(result.message);
    });
});

// ========================================================
// THÊM, SỬA, XÓA THÀNH PHẦN
// ========================================================

$("componentForm").addEventListener("submit", event => {
    event.preventDefault();

    run(async () => {
        if (!discard()) return;

        const data = readForm(
            event.target,
            [
                "weight_percentage",
                "min_score",
                "max_score",
                "input_order"
            ],
            ["is_required"]
        );

        const result = await api(
            path("/components"),
            "POST",
            data
        );

        resetForm("componentForm");

        await loadBoard();
        message(result.message);
    });
});

bind("resetComponent", async () =>
    resetForm("componentForm")
);

$("componentTable").addEventListener("click", event => {
    const edit = event.target.closest("[data-edit]");
    const remove = event.target.closest("[data-delete]");

    if (edit) {
        const component = state.board.components.find(
            item => key(item.id) === key(edit.dataset.edit)
        );

        if (component) {
            fillForm("componentForm", component);
        }
    }

    if (remove) {
        run(async () => {
            if (!discard()) return;

            if (!confirm(
                "Xóa thành phần và các điểm thuộc thành phần?"
            )) return;

            const result = await api(
                path("/components/" + remove.dataset.delete),
                "DELETE"
            );

            resetForm("componentForm");

            await loadBoard();
            message(result.message);
        });
    }
});

// ========================================================
// THÊM, SỬA, XÓA THANG ĐIỂM
// ========================================================

$("scaleForm").addEventListener("submit", event => {
    event.preventDefault();

    run(async () => {
        if (!discard()) return;

        const data = readForm(
            event.target,
            ["min_score", "max_score", "gpa_value"],
            ["is_pass"]
        );

        const result = await api("/scales", "POST", data);

        resetForm("scaleForm");

        await loadScales();
        await loadBoard();

        message(result.message);
    });
});

bind("resetScale", async () =>
    resetForm("scaleForm")
);

$("scaleTable").addEventListener("click", event => {
    const edit = event.target.closest("[data-edit]");
    const remove = event.target.closest("[data-delete]");

    if (edit) {
        const scale = state.scales.find(
            item => key(item.id) === key(edit.dataset.edit)
        );

        if (scale) {
            fillForm("scaleForm", scale);
        }
    }

    if (remove) {
        run(async () => {
            if (!discard()) return;
            if (!confirm("Xóa thang điểm này?")) return;

            const result = await api(
                "/scales/" + remove.dataset.delete,
                "DELETE"
            );

            await loadScales();
            await loadBoard();

            message(result.message);
        });
    }
});

// ========================================================
// TRA CỨU BẢNG ĐIỂM
// ========================================================

bind("searchTranscriptStudent", () =>
    searchStudents("transcriptKeyword", "transcriptStudent")
);

bind("showTranscript", async () => {
    const select = $("transcriptStudent");

    if (!select.value) {
        throw new Error("Hãy chọn sinh viên.");
    }

    state.transcript = await api(
        `/students/${encodeURIComponent(select.value)}/transcript`
    );

    $("transcriptTitle").textContent =
        select.selectedOptions[0].textContent.trim();

    renderTranscript();

    message(
        state.transcript.length
            ? "Đã tải bảng điểm."
            : "Sinh viên chưa có học phần đã chốt."
    );
});

// ========================================================
// XUẤT BẢNG ĐIỂM LỚP
// ========================================================

bind("exportBoard", async () => {
    if (!state.board) {
        throw new Error("Hãy chọn lớp.");
    }

    if (state.dirty) {
        throw new Error("Lưu điểm trước khi xuất.");
    }

    const board = state.board;

    const summaries = new Map(
        board.summaries.map(summary => [
            key(summary.registration_id),
            summary
        ])
    );

    const grades = new Map(
        board.grades.map(grade => [
            key(grade.registration_id) +
                ":" +
                key(grade.component_id),
            grade
        ])
    );

    const headers = [
        "Mã SV",
        "Họ tên",
        ...board.components.map(
            component => component.component_name
        ),
        "Hệ 10",
        "Chữ",
        "Hệ 4",
        "Kết quả",
        "Đã chốt"
    ];

    const rows = board.students.map(student => {
        const summary = summaries.get(
            key(student.registration_id)
        );

        return [
            student.code,
            student.full_name,

            ...board.components.map(component =>
                grades.get(
                    key(student.registration_id) +
                        ":" +
                        key(component.id)
                )?.score ?? ""
            ),

            summary?.total_score ?? "",
            summary?.letter_grade ?? "",
            summary?.gpa_value ?? "",
            summary?.result ?? "",
            yes(summary?.is_finalized) ? "Có" : "Không"
        ];
    });

    csv("bang-diem-lop.csv", [headers, ...rows]);
});

// ========================================================
// XUẤT BẢNG ĐIỂM SINH VIÊN
// ========================================================

bind("exportTranscript", async () => {
    if (!state.transcript.length) {
        throw new Error("Chưa có bảng điểm để xuất.");
    }

    csv("bang-diem-sinh-vien.csv", [
        [
            "Mã SV",
            "Họ tên",
            "Học kỳ",
            "Mã môn",
            "Tên môn",
            "Lớp",
            "Tín chỉ",
            "Hệ 10",
            "Chữ",
            "Hệ 4",
            "Kết quả"
        ],

        ...state.transcript.map(row => [
            row.student_code,
            row.full_name,
            row.semester_name,
            row.course_code,
            row.course_name,
            row.class_code,
            row.credits,
            row.total_score,
            row.letter_grade,
            row.gpa_value,
            row.result
        ])
    ]);
});

// ========================================================
// IN VÀ CẢNH BÁO ĐIỂM CHƯA LƯU
// ========================================================

$("printPage").addEventListener("click", () => {
    if (state.dirty) {
        message("Lưu điểm trước khi in.", true);
        return;
    }

    window.print();
});

window.addEventListener("beforeunload", event => {
    if (state.dirty) {
        event.preventDefault();
        event.returnValue = "";
    }
});

// ========================================================
// TẢI DỮ LIỆU KHI MỞ TRANG
// ========================================================

run(async () => {
    const results = await Promise.allSettled([
        loadClasses(),
        loadScales(),
        searchStudents(
            "transcriptKeyword",
            "transcriptStudent"
        )
    ]);

    renderTranscript();

    const errors = results.filter(
        result => result.status === "rejected"
    );

    if (errors.length) {
        message(
            errors.map(
                result => result.reason.message
            ).join(" | "),
            true
        );
    } else {
        message(
            `Đã kết nối SQL và tải ${results[0].value} lớp học phần.`
        );
    }
});