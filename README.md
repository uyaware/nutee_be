# Chạy NUTEE Backend

Hướng dẫn dưới đây dùng Windows PowerShell và chạy backend tại `http://localhost:8080`.
Các lệnh được chạy trong thư mục `backend` của dự án.

## 1. Chuẩn bị

- Cài **JDK 21**. Kiểm tra bằng `java -version`.
- Cài và mở **Docker Desktop**, chờ Docker Engine khởi động. Kiểm tra bằng `docker info`.
- Có kết nối Internet trong lần chạy đầu để tải Maven, các thư viện và Docker image.
- Dự án đã có Maven Wrapper (`mvnw.cmd`), không bắt buộc cài Maven riêng.

Nếu đã có PostgreSQL trên máy và không muốn dùng Docker, xem phần [Dùng PostgreSQL cài sẵn](#dùng-postgresql-cài-sẵn).

## 2. Mở thư mục backend

Mở PowerShell và chuyển tới thư mục chứa `pom.xml`:

```powershell
cd D:\Web\NUTEE\backend
```

Đổi đường dẫn nếu bạn lưu dự án ở vị trí khác. Kiểm tra phiên bản Java:

```powershell
java -version
```

Cấu hình `JAVA_HOME` và `PATH` để `java -version` hiển thị JDK 21 trước khi chạy Maven.

## 3. Chuẩn bị PostgreSQL bằng Docker

```powershell
.\scripts\init-local.ps1
```

Lần đầu chạy, script tạo `.env` từ `.env.example`, sinh mật khẩu database và hai khóa JWT, sau đó khởi động PostgreSQL. Mặc định PostgreSQL được mở tại `localhost:5433`, database tên `nutee`.

Nếu `.env` đã tồn tại, script sử dụng lại file đó và database volume hiện có; các khóa JWT còn trống sẽ được sinh thêm. Xem các giá trị kết nối trong `.env` nếu bạn đã cấu hình trước đó. Không đăng `.env` lên GitHub.

Để chọn cổng PostgreSQL khác **khi tạo `.env` lần đầu**:

```powershell
.\scripts\init-local.ps1 -DatabasePort 5434
```

Nếu `.env` đã tồn tại, tham số này không ghi đè cấu hình cũ. Hãy sửa đồng thời `POSTGRES_PORT` và cổng trong `DB_URL` trong `.env` nếu cần đổi cổng.

## 4. Chạy backend

```powershell
.\mvnw.cmd spring-boot:run
```

Spring Boot tự đọc `.env` trong thư mục `backend` và dùng profile được cấu hình trong file đó (`local` theo mặc định). Chạy lệnh từ thư mục `backend` để tìm đúng file. Nếu đã cài Maven, cũng có thể dùng `mvn spring-boot:run`.

Đợi log cho biết ứng dụng đã khởi động. Lần đầu có thể mất vài phút vì Maven tải các thư viện. Flyway tự tạo và kiểm tra schema trong database; lần khởi động đầu nên dùng database mới, rỗng. Với profile `local`, catalog, khuyến mãi và voucher mẫu được thêm tự động. Đặt `CATALOG_SEED_ENABLED=false` hoặc `DISCOUNTS_SEED_ENABLED=false` trong `.env` để tắt từng nhóm dữ liệu mẫu.

Nhấn `Ctrl+C` trong cửa sổ PowerShell để dừng backend. Để dừng các container sau khi dùng xong:

```powershell
docker compose stop
```

Lệnh này giữ lại dữ liệu PostgreSQL trong Docker volume. Lần sau, mở Docker Desktop và bảo đảm PostgreSQL đã chạy, sau đó chạy lại lệnh Maven ở trên. Nếu đã dừng container, khởi động lại bằng `docker compose up -d`.

## 5. Kiểm tra ứng dụng

Khi backend đang chạy với profile `local`:

| Địa chỉ | Công dụng |
| --- | --- |
| [http://localhost:8080/api/v1/swagger-ui.html](http://localhost:8080/api/v1/swagger-ui.html) | Xem và thử các API bằng Swagger UI |
| [http://localhost:8080/api/v1/api-docs](http://localhost:8080/api/v1/api-docs) | OpenAPI ở định dạng JSON |
| [http://localhost:8080/api/v1/actuator/health](http://localhost:8080/api/v1/actuator/health) | Kiểm tra trạng thái backend |
| [http://localhost:8080/api/v1/actuator/health/readiness](http://localhost:8080/api/v1/actuator/health/readiness) | Kiểm tra khả năng sẵn sàng, bao gồm database |

Nếu đổi `SERVER_PORT` trong `.env`, thay `8080` trong các địa chỉ trên bằng cổng mới. Swagger mặc định được bật trong profile `local`.

## Dùng PostgreSQL cài sẵn

Nếu PostgreSQL đã chạy trực tiếp trên Windows, bạn không cần Docker để chạy backend:

1. Tạo database rỗng tên `nutee` bằng pgAdmin hoặc `psql`; đảm bảo tài khoản PostgreSQL có quyền truy cập database đó.
2. Nếu chưa có `.env`, sao chép file mẫu:

    ```powershell
    Copy-Item .env.example .env
    ```

3. Mở `.env` và điền thông tin phù hợp với PostgreSQL của bạn, ví dụ:

    ```dotenv
    DB_URL=jdbc:postgresql://localhost:5432/nutee
    DB_USERNAME=postgres
    DB_PASSWORD=mat_khau_postgresql_cua_ban
    SERVER_PORT=8080
    ```

4. Điền `JWT_ACCESS_SECRET` và `JWT_REFRESH_SECRET` trong `.env`. Hai giá trị phải khác nhau và mỗi giá trị là Base64 của ít nhất 32 byte ngẫu nhiên. Chạy đoạn sau hai lần để tạo hai giá trị, rồi chép từng giá trị vào đúng dòng trong `.env`:

    ```powershell
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
    ```

5. Chạy `.\mvnw.cmd spring-boot:run` và mở Swagger theo bước 5 ở trên.

Các dòng trong `.env` phải theo dạng `TEN_BIEN=gia_tri`, không đặt giá trị trong dấu nháy. Spring Boot đọc file theo định dạng Java properties khi khởi động; sau khi chỉnh sửa, dừng và chạy lại backend.

## Cấu hình chức năng tùy chọn

Các chức năng sau có thể để tắt nếu chỉ cần chạy backend và thử API cơ bản:

- **Google:** đặt `GOOGLE_AUTH_ENABLED=true` và `GOOGLE_CLIENT_ID` trong `.env` để bật đăng nhập Google.
- **payOS:** đặt `PAYOS_ENABLED=true`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY` và `PAYOS_CHECKSUM_KEY` theo thông tin merchant. `PAYMENT_FRONTEND_URL` là địa chỉ frontend nhận kết quả. Mặc định payOS tắt.
- **Email đặt lại mật khẩu:** bật Gmail API trong Google Cloud, tạo OAuth client và cấp quyền `https://www.googleapis.com/auth/gmail.send` cho hộp thư gửi với quyền truy cập ngoại tuyến để nhận refresh token. Cấu hình `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN`, `PASSWORD_RESET_URL`, sau đó đặt `PASSWORD_RESET_ENABLED=true`. Các thông tin này chỉ đặt trong biến môi trường của backend; hộp thư Gmail đã cấp quyền là người gửi. Xem [hướng dẫn OAuth của Google](https://developers.google.com/identity/protocols/oauth2/web-server) và [Gmail API](https://developers.google.com/workspace/gmail/api/guides/sending). Dự án OAuth ở trạng thái thử nghiệm có thể có refresh token hết hạn sau 7 ngày.
- **Frontend local:** `CORS_ALLOWED_ORIGINS` trong `.env.example` cho phép `http://localhost:3000` và `http://localhost:5173`. Cập nhật danh sách nếu frontend chạy ở địa chỉ khác.

## Xử lý lỗi khi chạy

- **Không tìm thấy JDK 21:** kiểm tra `JAVA_HOME`, `PATH` và `java -version`. Chỉ cài JRE là chưa đủ.
- **Docker chưa sẵn sàng:** mở Docker Desktop, chờ engine chạy và thử `docker info` trước khi chạy lại `init-local.ps1`.
- **Cổng 8080 đang được sử dụng:** chạy `netstat -ano | findstr :8080` để xem PID, sau đó dừng ứng dụng đang chiếm cổng hoặc đổi `SERVER_PORT` trong `.env` và khởi động lại.
- **Không kết nối được PostgreSQL:** kiểm tra PostgreSQL đã chạy, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và cổng trong `.env` khớp với database thực tế. Với Docker, xem trạng thái bằng `docker compose ps`.
- **Sai mật khẩu PostgreSQL trong Docker:** volume cũ giữ tài khoản và mật khẩu đã khởi tạo; sửa `POSTGRES_PASSWORD` trong `.env` không tự đổi mật khẩu database hiện có. Dùng lại thông tin đúng của database đó.
- **Lỗi JWT:** kiểm tra hai khóa trong `.env` là Base64 hợp lệ, giải mã được ít nhất 32 byte và khác nhau.
- **Lỗi Flyway:** kiểm tra bạn đang kết nối đúng database và các migration đã áp dụng khớp với dự án. Không tự xóa dữ liệu hoặc sửa lịch sử migration để xử lý lỗi.
