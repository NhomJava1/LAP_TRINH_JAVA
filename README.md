# LAP_TRINH_JAVA

## Quy ước làm việc nhóm

| Mục | Quy ước | Ví dụ |
|---|---|---|
| Nhánh chính | `main` (không push trực tiếp) | |
| Nhánh phát triển | `develop` (không push trực tiếp) | |
| Nhánh làm việc | `feature/SCRUM-<số>-<mô-tả-ngắn>` | `feature/SCRUM-6-env-setup` |
| Commit | `SCRUM-<số>: <việc đã làm>` | `SCRUM-6: add env screenshot` |
| Pull Request | Tiêu đề `SCRUM-<số>: ...`, nhóm trưởng review rồi merge vào `develop` | |

## Quy trình mỗi lần làm việc
1. `git checkout develop` rồi `git pull`
2. `git checkout -b feature/SCRUM-<số>-<mô-tả>`
3. Code, rồi `git add .` và `git commit -m "SCRUM-<số>: ..."`
4. `git push -u origin feature/SCRUM-<số>-<mô-tả>`
5. Tạo Pull Request vào `develop`