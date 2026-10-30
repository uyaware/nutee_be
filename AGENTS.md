# Nguyên tắc làm việc với backend NUTEE

- Ưu tiên code đơn giản, rõ nghĩa và dễ sửa; áp dụng KISS, DRY, SOLID theo nhu cầu thực tế.
- Nếu yêu cầu chưa rõ, hỏi người dùng trước khi mở rộng phạm vi; không tự suy đoán thêm chức năng.
- Phạm vi sửa theo yêu cầu hiện tại của người dùng; frontend tổ chức theo module.
- Frontend gọi API thực tế qua `/api/v1`, không dùng service localStorage/sessionStorage
  hoặc đồng bộ người dùng API sang dữ liệu trình duyệt. Khi bỏ service phải giữ HTML/JSX,
  CSS, component, router và JS giao diện. Màn hình chưa có API giữ giao diện với dữ liệu rỗng;
  không thay cả trang bằng placeholder, không giả lập lưu thành công hoặc tự thêm endpoint.
  Mock HTTP chỉ dùng trong test.
- Giữ `/api/v1`, cookie HttpOnly cho access/refresh token và các chức năng đang có.
- Controller xử lý HTTP; service xử lý nghiệp vụ; repository truy cập dữ liệu.
- Bắt buộc dùng Spring Data JPA cho truy cập dữ liệu nghiệp vụ: entity ánh xạ schema hiện có,
  repository kế thừa JpaRepository.
- Ưu tiên method có sẵn của JpaRepository, sau đó là derived query từ tên method cho điều kiện
  đơn giản. Dùng Sort/Pageable và EntityGraph khi cần; map DTO trong service hoặc dùng projection
  phù hợp. Không viết @Query chỉ để thay findAll/findById hoặc các điều kiện derived query đã hỗ trợ.
- Dùng Specification cho bộ lọc động/nhiều tổ hợp. Chỉ dùng @Query cho truy vấn phức tạp,
  tổng hợp hoặc projection mà method có sẵn/derived query không diễn đạt rõ, và ghi lý do tại method.
  Không ép derived query thành tên quá dài hoặc chuyển lọc/phân trang từ database về bộ nhớ chỉ
  để tránh @Query. Luôn giữ các yêu cầu khóa dòng, thứ tự dữ liệu và tránh N+1 khi đơn giản hóa query.
- Không dùng JdbcTemplate, NamedParameterJdbcTemplate hoặc JDBC trực tiếp trong `src/main/java`
  để thay cho JPA. Native query qua Spring Data JPA chỉ dùng khi JPQL/Criteria không đáp ứng
  phù hợp và phải ghi rõ lý do. SQL trong Flyway, seed và kiểm tra database ở test vẫn được phép.
- Quan hệ JPA mặc định tải LAZY; dùng fetch join/EntityGraph hoặc truy vấn theo lô có chủ đích
  để tránh N+1. Không fetch collection cùng truy vấn phân trang và không trả entity trực tiếp qua API.
- Flyway quản lý schema; Hibernate chỉ `ddl-auto: validate`. Không đổi migration hoặc dữ liệu
  chỉ để thuận tiện cho entity mapping.
- Không tạo interface cho mọi service, lớp chuyển tiếp chỉ gọi một hàm, base class,
  factory hoặc DTO trùng dữ liệu nếu không mang lại giá trị cụ thể.
- Dùng Strategy cho đăng nhập bên thứ ba: mỗi provider tự xác minh danh tính;
  nghiệp vụ tìm/tạo/liên kết tài khoản dùng chung. Chỉ triển khai provider được yêu cầu.
- Hiện chỉ triển khai Google. Không tự thêm implementation, cấu hình hoặc mock Facebook/GitHub.
- Đặt tên theo cơ chế xác thực JWT: JwtAuthenticationFilter, JwtCookies, jwtAuth.
  Cookie chỉ là phương tiện lưu và gửi JWT, không phải một cơ chế xác thực riêng.
- Không dùng design pattern chỉ để tăng số lớp. Khi cần chọn giữa hai cách,
  chọn cách có ít khái niệm hơn và luồng xử lý dễ theo dõi hơn.
- Tránh boolean flag khó hiểu, biểu thức lồng sâu và xử lý lỗi bị nuốt im lặng.
- Dùng thư viện/framework đang có cho JWT, validation, giao dịch và bảo mật.
- Không bỏ xác minh token, hạn dùng, CSRF hoặc transaction để giảm số dòng.
- Mỗi class, interface, record, constructor và method viết tay trong `src/main/java`
  phải có Javadoc tiếng Việt ngắn, giải thích công dụng và điều kiện đáng chú ý.
  Không cần tự viết getter/setter chỉ để thêm comment cho code Lombok sinh.
- Chỉ giữ test cho nghiệp vụ, hợp đồng API và bảo mật quan trọng. Không thêm test
  lặp implementation, test getter/setter hoặc test lại hành vi mặc định của framework.
- Khi refactor, ưu tiên cập nhật test đang có; không sinh nhiều lớp test cho cùng một luồng.
- Không tự bật Google/email khi chưa có cấu hình. Không đưa bí mật từ `.env` vào Git.
- Không sửa migration đã áp dụng hoặc reset dữ liệu khi chỉ refactor code.
- Khi bàn giao: nêu thay đổi chính, cách mở rộng và kiểm tra đã chạy; trình bày ngắn gọn.
