# OctaPoint – Credit-as-a-Service (CaaS) Platform

**OctaPoint** là nền tảng cung cấp dịch vụ tín dụng và điểm thưởng dành cho doanh nghiệp (Credit-as-a-Service), được xây dựng trên nền tảng Blockchain Sui.

Dự án hướng đến việc xây dựng hệ thống quản lý tín dụng, điểm thưởng và giao dịch dành cho doanh nghiệp thông qua kiến trúc kết hợp giữa Backend, Frontend, Database và Smart Contract.

Tài liệu này hướng dẫn cách cài đặt, cấu hình và khởi chạy dự án trên môi trường Local.

---

## 📑 MỤC LỤC

1. [Giới thiệu dự án](#-1-giới-thiệu-dự-án)
2. [Công nghệ sử dụng](#-2-công-nghệ-sử-dụng)
3. [Yêu cầu hệ thống](#-3-yêu-cầu-hệ-thống)
4. [Clone source code](#-4-clone-source-code)
5. [Cấu hình môi trường](#-5-cấu-hình-môi-trường)
6. [Khởi chạy Database và Backend](#-6-khởi-chạy-database-và-backend)
7. [Khởi chạy Frontend](#-7-khởi-chạy-frontend)
8. [Sui CLI và Smart Contract](#-8-sui-cli-và-smart-contract)
9. [Kiểm thử dự án](#-9-kiểm-thử-dự-án)
10. [Cấu trúc thư mục](#-10-cấu-trúc-thư-mục)
11. [Xử lý lỗi thường gặp](#-11-xử-lý-lỗi-thường-gặp)
12. [Dừng hệ thống](#-12-dừng-hệ-thống)

---

## 🚀 1. Giới thiệu dự án

OctaPoint là nền tảng Credit-as-a-Service (CaaS) hỗ trợ doanh nghiệp quản lý tín dụng và điểm thưởng thông qua hệ thống tích hợp Blockchain Sui.

Các thành phần chính:

- **Merchant Portal:** Giao diện dành cho doanh nghiệp.
- **Admin Portal:** Giao diện quản trị hệ thống.
- **Core Backend:** Xử lý nghiệp vụ và cung cấp API.
- **Signer Service:** Hỗ trợ ký giao dịch Blockchain.
- **Smart Contract:** Xử lý logic trên Blockchain Sui.
- **Database:** Lưu trữ dữ liệu hệ thống.

## 🛠️ 2. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Frontend | Next.js, TypeScript |
| Backend | Java Spring Boot |
| Signer | TypeScript |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| Blockchain | Sui |
| Smart Contract | Sui Move |
| Container | Docker, Docker Compose |
| Version Control | Git, GitHub |

## 💻 3. Yêu cầu hệ thống

Trước khi cài đặt, hãy đảm bảo máy tính đã có các công cụ sau:

| Công cụ | Phiên bản yêu cầu |
|---|---|
| Node.js | 18.x trở lên |
| npm | Đi kèm Node.js |
| Java JDK | 21 |
| Docker Desktop | Phiên bản mới nhất |
| Git | Phiên bản mới nhất |
| Sui CLI | Tùy chọn |

### Kiểm tra phiên bản

Mở Terminal và chạy:

```bash
node -v
npm -v
java -version
docker --version
docker compose version
git --version
```

## 📥 4. Clone source code

Mở Terminal và chạy lệnh:

```bash
git clone https://github.com/NhomJava1/LAP_TRINH_JAVA.git
```

Di chuyển vào thư mục dự án:

```bash
cd LAP_TRINH_JAVA
```

Kiểm tra cấu trúc thư mục:

```bash
ls -la
```

## ⚙️ 5. Cấu hình môi trường

Dự án sử dụng file `.env` để quản lý các thông số cấu hình môi trường.

### Bước 1: Tạo file .env

Tại thư mục gốc dự án, chạy:

```bash
cp .env.example .env
```

### Bước 2: Cấu hình thông tin

Mở file `.env` bằng VS Code:

```bash
code .env
```

Các biến môi trường chính:

```env
POSTGRES_DB=octapoint_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-local-password

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/octapoint_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your-local-password

REDIS_HOST=redis
REDIS_PORT=6379

API_KEY_MASTER_KEY=your-generated-master-key
API_KEY_ADMIN_TOKEN=your-generated-admin-token

SPRING_PROFILES_ACTIVE=prod
```

**Lưu ý bảo mật:**

- Thay các giá trị mẫu bằng mật khẩu và token riêng.
- `API_KEY_MASTER_KEY` cần khóa ngẫu nhiên 32 byte, có thể sử dụng giá trị Base64.
- Không chia sẻ file `.env` thật lên GitHub.
- Không sử dụng các giá trị mẫu cho môi trường production.

Tạo khóa ngẫu nhiên Base64 trên macOS:

```bash
openssl rand -base64 32
```

## 🐳 6. Khởi chạy Database và Backend

Dự án sử dụng Docker Compose để quản lý PostgreSQL, Redis và Backend.

### Bước 1: Khởi động Docker Desktop

Mở Docker Desktop và chờ đến khi Docker hoạt động bình thường.

### Bước 2: Khởi chạy các dịch vụ

Tại thư mục gốc dự án, chạy:

```bash
docker compose up -d --build postgres redis core
```

Lệnh này sẽ:

- Build Docker Image cho Backend.
- Khởi động PostgreSQL.
- Khởi động Redis.
- Khởi động Core Backend.
- Tự động thiết lập các phụ thuộc được khai báo trong Docker Compose.

### Bước 3: Kiểm tra trạng thái

```bash
docker compose ps
```

Các dịch vụ cần hoạt động:

| Service | Port |
|---|---|
| postgres | 5432 |
| redis | 6379 |
| core | 8080 |

### Bước 4: Kiểm tra log Backend

```bash
docker compose logs -f core
```

Nếu Backend khởi động thành công, Terminal sẽ hiển thị thông báo tương tự:

```text
Started CoreApplication...
```

Backend API hoạt động tại:

http://localhost:8080

Để xem log Database:

```bash
docker compose logs -f postgres
```

Để xem log Redis:

```bash
docker compose logs -f redis
```

## 🌐 7. Khởi chạy Frontend

Frontend được chạy độc lập bằng Node.js và npm.

### Bước 1: Mở Terminal mới

Giữ nguyên các dịch vụ Docker đang chạy.

Di chuyển vào thư mục Frontend:

```bash
cd frontend
```

### Bước 2: Cài đặt thư viện

```bash
npm install
```

### Bước 3: Khởi chạy ứng dụng

```bash
npm run dev
```

Khi Terminal hiển thị:

```text
Ready in ...
```

Frontend đã khởi chạy thành công.

Truy cập giao diện:

http://localhost:3000

**Lưu ý:** Nếu Frontend yêu cầu biến môi trường riêng, hãy kiểm tra file cấu hình mẫu hoặc tài liệu bên trong thư mục `frontend`.

## ⛓️ 8. Sui CLI và Smart Contract

Dự án sử dụng Sui Move để xây dựng Smart Contract.

### Bước 1: Khởi chạy môi trường Sui CLI

Tại thư mục gốc dự án, chạy:

```bash
docker compose run --rm sui-cli
```

Lệnh này mở shell Linux bên trong container đã được cấu hình Sui CLI.

### Bước 2: Kiểm tra Sui CLI

Trong shell container, chạy:

```bash
sui --version
```

### Bước 3: Di chuyển đến thư mục Smart Contract

```bash
cd /workspace/contracts/octapoint_move
```

### Bước 4: Kiểm tra cấu trúc Smart Contract

```bash
ls -la
```

Nếu thư mục có file `Move.toml`, có thể kiểm tra và build package bằng:

```bash
sui move build
```

**Lưu ý:** Việc build Smart Contract không đồng nghĩa với việc đã triển khai (deploy) lên Blockchain. Quá trình publish cần cấu hình địa chỉ mạng, ví và các thông số tương ứng.

## 🧪 9. Kiểm thử dự án

### 9.1. Kiểm thử Backend

Có thể chạy kiểm thử bằng Maven Wrapper.

Mở Terminal mới:

```bash
cd backend
```

Cấp quyền thực thi (chỉ cần thực hiện trên macOS/Linux):

```bash
chmod +x mvnw
```

Chạy kiểm thử:

```bash
./mvnw test
```

### 9.2. Kiểm tra Build Frontend

Di chuyển vào thư mục Frontend:

```bash
cd frontend
```

Cài đặt thư viện nếu chưa có:

```bash
npm install
```

Chạy kiểm tra Build:

```bash
npm run build
```

### 9.3. Kiểm tra Docker

Tại thư mục gốc dự án:

```bash
docker compose ps
```

Kiểm tra log toàn bộ dịch vụ:

```bash
docker compose logs
```

## 📂 10. Cấu trúc thư mục

Cấu trúc thư mục thực tế trên nhánh `main`:

```text
LAP_TRINH_JAVA/
│
├── .github/
│   └── GitHub workflows và cấu hình dự án
│
├── backend/
│   ├── Dockerfile
│   └── Java Spring Boot Application
│
├── contracts/
│   └── octapoint_move/
│       └── Sui Move Smart Contracts
│
├── docker/
│   └── sui/
│       └── Dockerfile
│
├── docs/
│   └── Tài liệu dự án
│
├── frontend/
│   └── Next.js Application
│
├── signer/
│   └── TypeScript Signer Service
│
├── .env.example
│
├── .gitignore
│
├── README.md
│
└── docker-compose.yml
```

### Mô tả các thư mục và file

| Thư mục / File | Chức năng |
|---|---|
| `.github/` | Chứa cấu hình và workflow GitHub |
| `backend/` | Mã nguồn Java Spring Boot |
| `contracts/octapoint_move/` | Smart Contract viết bằng Sui Move |
| `docker/sui/` | Dockerfile cấu hình môi trường Sui CLI |
| `docs/` | Tài liệu phân tích và thiết kế hệ thống |
| `frontend/` | Giao diện Next.js |
| `signer/` | Dịch vụ TypeScript hỗ trợ ký giao dịch |
| `.env.example` | File mẫu biến môi trường |
| `.gitignore` | Các file và thư mục không đưa lên Git |
| `README.md` | Tài liệu hướng dẫn dự án |
| `docker-compose.yml` | Cấu hình và quản lý các Docker services |

## ⚠️ 11. Xử lý lỗi thường gặp

### 11.1. Lỗi Port already in use

**Nguyên nhân:** Cổng đang được sử dụng bởi ứng dụng khác.

Các cổng thường gặp:

- 3000: Frontend.
- 5432: PostgreSQL.
- 6379: Redis.
- 8080: Backend.

Kiểm tra tiến trình đang sử dụng cổng:

```bash
lsof -i :8080
```

Thay `8080` bằng cổng cần kiểm tra.

### 11.2. Docker chưa khởi động

**Nguyên nhân:** Docker Desktop chưa được mở hoặc chưa hoàn tất quá trình khởi động.

**Cách khắc phục:**

1. Mở Docker Desktop.
2. Chờ Docker hoạt động.
3. Chạy lại:

```bash
docker compose up -d --build postgres redis core
```

### 11.3. Lỗi thiếu file .env

Nếu xuất hiện thông báo yêu cầu cấu hình biến môi trường:

```text
Set POSTGRES_PASSWORD in .env
```

Hãy kiểm tra xem đã tạo file `.env` từ `.env.example` chưa:

```bash
cp .env.example .env
```

Sau đó cập nhật các giá trị cần thiết.

### 11.4. Lỗi Maven Wrapper Permission denied

**Nguyên nhân:** File `mvnw` chưa có quyền thực thi.

**Cách khắc phục:**

```bash
cd backend
chmod +x mvnw
```

### 11.5. Backend không kết nối được Database

**Nguyên nhân:**

- PostgreSQL chưa chạy.
- Sai thông tin đăng nhập.
- Sai cấu hình kết nối Database.

Kiểm tra trạng thái:

```bash
docker compose ps
```

Kiểm tra log:

```bash
docker compose logs postgres
docker compose logs core
```

**Lưu ý:** Khi Backend chạy trong Docker Compose, hostname Database là `postgres` và Redis là `redis`. Nếu chạy Backend trực tiếp trên macOS bằng Maven, cần sử dụng hostname `localhost` và cấu hình kết nối phù hợp với môi trường local.

### 11.6. Frontend không khởi chạy

Kiểm tra phiên bản Node.js:

```bash
node -v
npm -v
```

Cài đặt lại thư viện:

```bash
cd frontend
npm install
```

Sau đó chạy:

```bash
npm run dev
```

## 🛑 12. Dừng hệ thống

Khi không còn sử dụng dự án, thực hiện các bước sau.

### Dừng Frontend

Tại Terminal đang chạy Frontend, nhấn:

```text
Ctrl + C
```

### Dừng Docker services

Tại thư mục gốc dự án:

```bash
docker compose down
```

Lệnh này dừng và xóa các container nhưng giữ lại volume dữ liệu.

**Nếu muốn xóa toàn bộ dữ liệu Database:**

```bash
docker compose down -v
```

**Cảnh báo:** Lệnh trên có thể xóa dữ liệu PostgreSQL đã lưu trong Docker volume. Chỉ sử dụng khi thực sự muốn làm sạch dữ liệu.

---

## 👨‍💻 THÔNG TIN DỰ ÁN

| Thuộc tính | Thông tin |
|---|---|
| Project Name | OctaPoint |
| Project Type | Credit-as-a-Service Platform |
| Backend | Java Spring Boot |
| Frontend | Next.js |
| Database | PostgreSQL |
| Cache | Redis |
| Blockchain | Sui Network |
| Smart Contract | Sui Move |
| Containerization | Docker Compose |
| Repository | LAP_TRINH_JAVA |

---

**OctaPoint – Credit-as-a-Service Platform**

*Built with Java, Next.js, PostgreSQL, Redis and Sui Blockchain.*