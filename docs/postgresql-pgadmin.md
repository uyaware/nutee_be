# Chạy PostgreSQL và kết nối pgAdmin trên Windows
> PostgreSQL cài trực tiếp trên Windows không cần Docker. Trong `backend/.env`,
> đặt `DB_URL=jdbc:postgresql://localhost:5432/nutee`, `DB_USERNAME` và
> `DB_PASSWORD` theo tài khoản PostgreSQL của bạn, rồi chạy
> `.\scripts\run-local.ps1` từ thư mục `backend`. Không chạy
> `init-local.ps1` cho cách này: script đó dành cho PostgreSQL trong Docker.
> Các mục phía dưới mô tả cấu hình Docker tùy chọn.


Hướng dẫn này dành cho cấu hình NUTEE ở máy hiện tại. PostgreSQL chạy trong Docker;
pgAdmin là chương trình để kết nối và xem database. Bạn có thể mở pgAdmin trên
Windows, không cần cài PostgreSQL server thứ hai để chạy dự án.

## 1. Khởi động PostgreSQL

1. Mở **Docker Desktop** và đợi Docker chạy xong. Nếu chưa có, xem hướng dẫn
   [cài Docker Desktop cho Windows](https://docs.docker.com/desktop/setup/install/windows-install/).
2. Mở PowerShell, vào thư mục backend:

   ```powershell
   cd D:\Web\NUTEE\backend
   ```

3. Khởi động database đã cấu hình trong `compose.yml` và `.env`:

   ```powershell
   docker compose up -d --wait postgres
   ```

   Dữ liệu nằm trong Docker volume `postgres_data`; khởi động lại container vẫn
   dùng dữ liệu trong volume. Lệnh trên không khởi động backend.

4. Kiểm tra container và PostgreSQL:

   ```powershell
   docker compose ps
   docker compose exec postgres pg_isready -U nutee -d nutee
   ```

   Mong đợi container `postgres` ở trạng thái đang chạy/healthy và `pg_isready`
   báo `accepting connections`. Nếu đã đổi `POSTGRES_USER` hoặc `POSTGRES_DB`
   trong `.env`, thay `nutee` bằng các giá trị tương ứng.

Nếu máy mới chưa có `backend\.env`, chạy một lần lệnh sau từ thư mục backend;
script tạo mật khẩu local và khởi động PostgreSQL. Với file `.env` đã có, script
tái sử dụng cấu hình thay vì tạo mật khẩu database mới:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\init-local.ps1
```

## 2. Lấy mật khẩu database

File `D:\Web\NUTEE\backend\.env` đã có cấu hình local. Mở file bằng Notepad:

```powershell
notepad .env
```

Tìm dòng `DB_PASSWORD=...` và dùng **giá trị sau dấu `=`** làm mật khẩu khi
kết nối pgAdmin. `POSTGRES_PASSWORD` trong cấu hình hiện tại có cùng giá trị.
Đây là mật khẩu database, khác với mật khẩu tài khoản/master password của pgAdmin.
Không gửi nội dung `.env` hoặc mật khẩu lên Git/chat.

Không ghi đè `.env` bằng `.env.example` khi đã có database. Mật khẩu trong
`.env` chỉ được PostgreSQL dùng khi volume được khởi tạo lần đầu; sửa file sau đó
không tự thay đổi mật khẩu của user đã tồn tại trong database.

## 3. Tạo kết nối trong pgAdmin 4

Mở **pgAdmin 4** trên Windows. Nếu chưa cài, tải từ
[trang tải pgAdmin](https://www.pgadmin.org/download/pgadmin-4-windows/).

1. Ở cây bên trái, nhấp chuột phải **Servers → Register → Server…**.
2. Tab **General**, nhập `NUTEE local` vào ô **Name**. Đây chỉ là tên hiển thị.
3. Tab **Connection**, nhập:

   | Ô trong pgAdmin | Giá trị trên máy hiện tại |
   | --- | --- |
   | Host name/address | `127.0.0.1` |
   | Port | `5433` |
   | Maintenance database | `nutee` |
   | Username | `nutee` |
   | Password | Giá trị `DB_PASSWORD` trong `backend\.env` |

4. Chọn **Save**. Tùy chọn **Save password** là tùy bạn; nếu pgAdmin hỏi master
   password, đó là mật khẩu bảo vệ thông tin kết nối được lưu của pgAdmin.
5. Mở cây **Servers → NUTEE local → Databases → nutee → Schemas → public → Tables**.
   Nhấp phải **Tables → Refresh** nếu danh sách chưa cập nhật.

Các trường kết nối theo [tài liệu Server Dialog của pgAdmin](https://www.pgadmin.org/docs/pgadmin4/latest/server_dialog.html).
Host port `5433` là cổng trên Windows; cổng `5432` trong `compose.yml` là cổng bên
trong container. Nếu chạy pgAdmin trong một container khác, địa chỉ kết nối sẽ
phụ thuộc mạng Docker; bảng trên dành cho pgAdmin chạy trực tiếp trên Windows.

Nếu đã đổi `.env`, dùng `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` tương ứng;
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cũng phải trỏ tới cùng database/tài khoản.

## 4. Khởi động backend để tạo/cập nhật bảng

Trong PowerShell tại `D:\Web\NUTEE\backend`, chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1
```

Script đọc `.env`, chọn JDK 21 và chạy backend profile `local`. Flyway chạy các
migration còn thiếu khi backend khởi động, sau đó Hibernate kiểm tra schema.
Baseline V1–V3 hiện tại dành cho database rỗng; database đã áp dụng chuỗi V1–V7 cũ
cần được reset có chủ đích hoặc có phương án chuyển dữ liệu riêng trước khi chạy.
Đợi ứng dụng khởi động xong, mở
<http://localhost:8080/api/v1/actuator/health> và kiểm tra trạng thái `UP`.

Nếu mở pgAdmin trước khi backend chạy lần đầu, database có thể chưa có các bảng
ứng dụng. Sau khi backend chạy xong, chọn **Refresh** tại `Tables`.

Trong pgAdmin, nhấp phải database `nutee` → **Query Tool**, chạy các câu chỉ đọc:

```sql
SELECT current_database(), current_user, version();

SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
```

Nếu báo không tìm thấy `flyway_schema_history`, backend chưa chạy migration thành
công trên database đang kết nối. Kiểm tra lại thông tin kết nối và log backend.

## 5. Dừng và xử lý lỗi thường gặp

- **Docker không kết nối được / engine chưa chạy:** mở Docker Desktop, đợi engine
  sẵn sàng rồi chạy lại `docker compose up -d --wait postgres`.
- **Connection refused:** chạy `docker compose ps`; kiểm tra cổng `5433` trong
  `.env` và ô Port của pgAdmin. Trên máy, có thể dùng
  `Test-NetConnection 127.0.0.1 -Port 5433`.
- **Password authentication failed:** kiểm tra username và `DB_PASSWORD` trong
  `.env`. Nếu `.env` từng bị thay, mật khẩu PostgreSQL lưu trong volume có thể
  khác mật khẩu trong file; đừng xóa volume hoặc tự reset để thử.
- **Database `nutee` does not exist:** kiểm tra lại `POSTGRES_DB` và database đang
  kết nối. Nếu database từng bị xóa khỏi volume đã tồn tại, cần xác định nguyên
  nhân trước khi tạo lại; script init không tự khôi phục dữ liệu đã xóa.
- **Flyway checksum/validation error:** không dùng `repair` hoặc xóa lịch sử; kiểm
  tra migration đã áp dụng và phiên bản source trước khi xử lý.

Để dừng backend, nhấn **Ctrl+C** ở cửa sổ chạy backend. Để dừng container nhưng
giữ volume database, chạy `docker compose stop postgres` từ thư mục backend.
