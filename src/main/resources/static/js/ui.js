(() => {
    "use strict";

    const ui = {
        rows: [],
        page: 1,
        size: 8,
        pending: null
    };

    const pages = {
        overview: [
            "Trang tổng quan",
            "Danh sách lớp học phần và thông tin đánh giá đang quản lý."
        ],

        gradebook: [
            "Đánh giá & bảng điểm",
            "Nhập điểm thành phần, tính tổng kết và chốt điểm theo lớp học phần."
        ],

        components: [
            "Thành phần đánh giá",
            "Quản lý chuyên cần, giữa kỳ, cuối kỳ và trọng số của từng lớp."
        ],

        scales: [
            "Thang điểm quy đổi",
            "Quy đổi điểm hệ 10 sang điểm chữ, hệ 4 và kết quả học tập."
        ],

        transcript: [
            "Bảng điểm sinh viên",
            "Tra cứu và xuất kết quả các học phần đã chốt của sinh viên."
        ]
    };

    // =====================================================
    // ĐIỀU HƯỚNG
    // =====================================================

    function navigate(name, changeHash = true) {
        if (!pages[name]) {
            name = "gradebook";
        }

        document.querySelectorAll("[data-page]")
            .forEach(section => {
                section.hidden = section.dataset.page !== name;
            });

        document.querySelectorAll(".sidebar [data-nav]")
            .forEach(link => {
                const active = link.dataset.nav === name;

                link.classList.toggle("active", active);

                if (active) {
                    link.setAttribute("aria-current", "page");
                } else {
                    link.removeAttribute("aria-current");
                }
            });

        $("classContext").hidden =
            !["gradebook", "components"].includes(name);

        $("pageTitle").textContent = pages[name][0];
        $("pageDescription").textContent = pages[name][1];
        $("breadcrumbTitle").textContent = pages[name][0];

        document.title = pages[name][0] + " · Nhóm 8";

        if (changeHash && location.hash !== "#" + name) {
            location.hash = name;
        }

        document.body.classList.remove("sidebar-open");
        syncMenu();
    }

    function syncMenu() {
        const mobile = matchMedia(
            "(max-width: 850px)"
        ).matches;

        const expanded = mobile
            ? document.body.classList.contains("sidebar-open")
            : !document.body.classList.contains("sidebar-collapsed");

        $("menuToggle").setAttribute(
            "aria-expanded",
            String(expanded)
        );
    }

    // =====================================================
    // THỐNG KÊ THEO LỚP
    // =====================================================

    function syncBoard() {
        const board = state.board;

        $("statStudents").textContent =
            board ? board.students.length : "—";

        $("statComponents").textContent =
            board ? board.components.length : "—";

        const finalized = board
            ? board.summaries.filter(
                summary => yes(summary.is_finalized)
            ).length
            : 0;

        $("statFinalized").textContent =
            board ? finalized : "—";

        $("statClassLabel").textContent =
            board ? board.info.class_code : "Chưa chọn lớp";

        $("finalizedHint").textContent = board
            ? `${finalized}/${board.students.length} sinh viên`
            : "Chưa chọn lớp";

        const weight = board
            ? board.components.reduce(
                (sum, component) =>
                    sum + Number(component.weight_percentage),
                0
            )
            : 0;

        $("statWeight").textContent = board
            ? `${Number(weight.toFixed(2))}%`
            : "—";

        $("weightHint").textContent = !board
            ? "Yêu cầu đủ 100%"
            : Math.abs(weight - 100) < 0.001
                ? "Đủ điều kiện về trọng số"
                : "Cần cấu hình đủ 100%";

        $("gradeEmpty").hidden = !!board;
        $("componentEmpty").hidden = !!board;
        $("componentWorkspace").hidden = !board;
    }

    // =====================================================
    // DANH MỤC LỚP: TÌM KIẾM, LỌC, PHÂN TRANG
    // =====================================================

    function filteredRows() {
        const keyword = $("catalogSearch")
            .value.trim()
            .toLocaleLowerCase("vi");

        const semester = $("semesterFilter").value;

        return ui.rows.filter(row => {
            const matchesSemester =
                !semester || row.semester_name === semester;

            const matchesKeyword =
                `${row.class_code} ${row.course_name}`
                    .toLocaleLowerCase("vi")
                    .includes(keyword);

            return matchesSemester && matchesKeyword;
        });
    }

    function renderCatalog() {
        const rows = filteredRows();

        const totalPages = Math.max(
            1,
            Math.ceil(rows.length / ui.size)
        );

        ui.page = Math.max(
            1,
            Math.min(ui.page, totalPages)
        );

        const start = (ui.page - 1) * ui.size;
        const selected = rows.slice(start, start + ui.size);

        $("catalogTable").innerHTML = table(
            [
                "STT",
                "Mã lớp học phần",
                "Tên học phần",
                "Học kỳ",
                "Tín chỉ",
                "Sinh viên",
                "Thao tác"
            ],

            selected.map((row, index) => `
                <tr>
                    <td>${start + index + 1}</td>

                    <td>
                        <strong>${h(row.class_code)}</strong>
                    </td>

                    <td>${h(row.course_name)}</td>
                    <td>${h(row.semester_name)}</td>
                    <td>${h(row.credits)}</td>
                    <td>${h(row.student_count)}</td>

                    <td>
                        <button
                            class="small"
                            type="button"
                            data-pick-class="${h(row.id)}"
                        >
                            Nhập điểm →
                        </button>
                    </td>
                </tr>
            `)
        );

        $("catalogCount").textContent = rows.length
            ? `Hiển thị ${start + 1}–${start + selected.length} trong ${rows.length} lớp`
            : "Không có lớp phù hợp.";

        $("catalogPage").textContent =
            `${ui.page}/${totalPages}`;

        $("catalogPrev").disabled = ui.page <= 1;
        $("catalogNext").disabled = ui.page >= totalPages;
    }

    async function refreshCatalog() {
        if (ui.pending) {
            return ui.pending;
        }

        ui.pending = (async () => {
            try {
                const rows = await api("/classes");

                if (!Array.isArray(rows)) {
                    throw new Error(
                        "API lớp không trả về danh sách."
                    );
                }

                ui.rows = rows;

                $("statClasses").textContent = rows.length;

                const oldSemester =
                    $("semesterFilter").value;

                const semesters = [
                    ...new Set(
                        rows
                            .map(row => row.semester_name)
                            .filter(Boolean)
                    )
                ];

                $("semesterFilter").innerHTML =
                    '<option value="">Tất cả học kỳ</option>' +
                    semesters.map(semester => `
                        <option value="${h(semester)}">
                            ${h(semester)}
                        </option>
                    `).join("");

                if (semesters.includes(oldSemester)) {
                    $("semesterFilter").value = oldSemester;
                }

                $("apiStatus").textContent = "API sẵn sàng";
                $("apiStatus").classList.remove("failed");

                renderCatalog();
            } catch (error) {
                $("apiStatus").textContent =
                    "Lỗi tải dữ liệu";

                $("apiStatus").classList.add("failed");

                $("catalogTable").innerHTML = `
                    <p class="notice error">
                        ${h(error.message)}
                    </p>
                `;

                message(error.message, true);
            }
        })();

        try {
            await ui.pending;
        } finally {
            ui.pending = null;
        }
    }

    // =====================================================
    // NÚT ĐIỀU HƯỚNG VÀ CHỌN LỚP
    // =====================================================

    document.addEventListener("click", event => {
        const nav = event.target.closest("[data-nav]");

        if (nav) {
            event.preventDefault();
            navigate(nav.dataset.nav);
        }

        const pick = event.target.closest(
            "[data-pick-class]"
        );

        if (!pick) return;

        if (state.busy) {
            message("Đang tải dữ liệu, vui lòng chờ.");
            return;
        }

        const option = [
            ...$("classSelect").options
        ].find(item =>
            key(item.value) === key(pick.dataset.pickClass)
        );

        if (!option) {
            message(
                "Bấm Tải lại để cập nhật danh sách lớp.",
                true
            );
            return;
        }

        const previous = state.classId;

        $("classSelect").value = option.value;

        $("classSelect").dispatchEvent(
            new Event("change", {
                bubbles: true
            })
        );

        if (
            key(state.classId) === key(option.value) ||
            previous === state.classId
        ) {
            navigate("gradebook");
        }
    });

    $("menuToggle").addEventListener("click", () => {
        const mobile = matchMedia(
            "(max-width: 850px)"
        ).matches;

        document.body.classList.toggle(
            mobile ? "sidebar-open" : "sidebar-collapsed"
        );

        syncMenu();
    });

    $("sidebarBackdrop").addEventListener("click", () => {
        document.body.classList.remove("sidebar-open");
        syncMenu();
    });

    window.addEventListener("resize", syncMenu);

    window.addEventListener("hashchange", () => {
        navigate(location.hash.slice(1), false);
    });

    // =====================================================
    // TÌM KIẾM VÀ PHÂN TRANG
    // =====================================================

    $("catalogSearch").addEventListener("input", () => {
        ui.page = 1;
        renderCatalog();
    });

    $("semesterFilter").addEventListener("change", () => {
        ui.page = 1;
        renderCatalog();
    });

    $("catalogPrev").addEventListener("click", () => {
        ui.page--;
        renderCatalog();
    });

    $("catalogNext").addEventListener("click", () => {
        ui.page++;
        renderCatalog();
    });

    $("catalogRefresh").addEventListener(
        "click",
        refreshCatalog
    );

    // =====================================================
    // CỬA SỔ THÊM SINH VIÊN
    // =====================================================

    $("openEnrollment").addEventListener("click", () => {
        if (state.busy || !state.board) {
            message(
                "Chọn lớp và chờ tải bảng điểm trước.",
                true
            );
            return;
        }

        $("enrollmentNotice").hidden = true;
        $("enrollmentDialog").showModal();

        if (!$("studentSelect").options.length) {
            run(() =>
                searchStudents(
                    "studentKeyword",
                    "studentSelect"
                )
            );
        }
    });

    [
        "closeEnrollment",
        "cancelEnrollment"
    ].forEach(id => {
        $(id).addEventListener("click", () => {
            $("enrollmentDialog").close();
        });
    });

    // =====================================================
    // ĐỒNG BỘ VỚI APP.JS ĐANG HOẠT ĐỘNG
    // =====================================================

    new MutationObserver(syncBoard).observe(
        $("boardTable"),
        {childList: true}
    );

    new MutationObserver(() => {
        syncBoard();
        refreshCatalog();
    }).observe(
        $("classSelect"),
        {childList: true}
    );

    $("classSelect").addEventListener("change", () => {
        if (!state.classId) {
            syncBoard();
            return;
        }

        $("componentEmpty").hidden = true;
    });

    new MutationObserver(() => {
        if (ui.rows.length) {
            renderCatalog();
        }

        if (!$("enrollmentDialog").open) {
            return;
        }

        const notice = $("enrollmentNotice");

        notice.textContent = $("message").textContent;

        notice.className =
            "notice compact" +
            (
                $("message").classList.contains("error")
                    ? " error"
                    : ""
            );

        notice.hidden = false;

        if (
            notice.textContent ===
            "Đã thêm sinh viên vào lớp."
        ) {
            $("enrollmentDialog").close();
            refreshCatalog();
        }
    }).observe(
        $("message"),
        {
            childList: true,
            characterData: true,
            subtree: true
        }
    );

    // Xóa bảng điểm đang xem khi đổi sinh viên,
    // tránh xuất nhầm kết quả của người trước.
    $("transcriptStudent").addEventListener("change", () => {
        state.transcript = [];
        $("transcriptTitle").textContent = "";
        renderTranscript();
    });

    // Cho phép nhấn Enter để tìm kiếm.
    [
        ["studentKeyword", "searchStudents"],
        ["transcriptKeyword", "searchTranscriptStudent"]
    ].forEach(([input, button]) => {
        $(input).addEventListener("keydown", event => {
            if (event.key === "Enter") {
                event.preventDefault();
                $(button).click();
            }
        });
    });

    // =====================================================
    // KHỞI TẠO GIAO DIỆN
    // =====================================================

    $("todayLabel").textContent =
        new Intl.DateTimeFormat("vi-VN", {
            weekday: "long",
            day: "2-digit",
            month: "2-digit",
            year: "numeric"
        }).format(new Date());

    navigate(
        location.hash.slice(1) || "gradebook",
        false
    );

    syncBoard();
    refreshCatalog();
})();