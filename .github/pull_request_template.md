## 📌 Tiêu đề PR
<!-- Định dạng bắt buộc: SCRUM-<số>: <Mô tả ngắn gọn công việc> -->
<!-- Ví dụ: SCRUM-12: Cập nhật danh sách actor và use case v1 -->

## 🎯 Mục đích & Bối cảnh
<!-- Giải thích lý do thực hiện PR này: giải quyết vấn đề gì, thêm tính năng nào? -->
- **Jira Ticket:** [SCRUM-](https://chamchamcham628.atlassian.net/jira/software/projects/SCRUM/boards/1?filter=&groupBy=none&selectedIssue=SCRUM-14)
- **Yêu cầu liên quan:** FR-xx / NFR-xx / D-xx

## 📝 Loại thay đổi
- [ ] 🚀 Tính năng mới (Feature)
- [ ] 🐛 Sửa lỗi (Bug fix)
- [ ] 📚 Tài liệu (Documentation)
- [ ] ♻️ Tái cấu trúc mã nguồn (Refactor)
- [ ] ⚙️ Hạ tầng / CI/CD / Docker (DevOps/Chore)
- [ ] 🧪 Bổ sung / cập nhật kiểm thử (Testing)

## 📋 Tóm tắt các thay đổi
<!-- Liệt kê ngắn gọn các file hoặc logic đã thay đổi -->
- Thay đổi 1: ...
- Thay đổi 2: ...

## 🔍 Cách xác minh & Kết quả kiểm thử
<!-- Nêu rõ các bước để reviewer có thể kiểm tra hoặc đính kèm log/screenshot chứng minh -->
- [ ] Đã chạy kiểm thử cục bộ: `mvn test` / `npm test`
- [ ] Kết quả kiểm thử / Bằng chứng (Paste log hoặc screenshot vào đây nếu có):

## ✅ Checklist tự kiểm tra trước khi tạo PR
<!-- Đánh dấu [x] vào các mục đã hoàn thành -->
- [ ] Tiêu đề PR tuân thủ đúng cú pháp `SCRUM-<số>: <việc đã làm>`.
- [ ] Target branch là `develop` (TUYỆT ĐỐI không tạo PR trực tiếp vào `main`).
- [ ] Mã nguồn đã được format sạch sẽ, không có import thừa hoặc code thừa (dead code).
- [ ] Không có secret, private key, API secret hay file `.env` thật nào bị commit.
- [ ] Đã cập nhật tài liệu liên quan nếu có thay đổi về kiến trúc, API hoặc schema.
- [ ] CI pipeline đã chạy và đạt trạng thái XANH.
