# Nguyên tắc làm việc với backend NUTEE

- Ưu tiên code đơn giản, rõ nghĩa và dễ sửa; áp dụng KISS, DRY, SOLID theo nhu cầu thực tế.
- Nếu yêu cầu chưa rõ, hỏi người dùng trước khi mở rộng phạm vi; không tự suy đoán thêm chức năng.
- Chỉ refactor backend. Frontend chỉ sửa khi cần khớp hợp đồng API đã thay đổi.
- Giữ `/api/v1`, cookie HttpOnly cho access/refresh token và các chức năng đang có.
- Controller xử lý HTTP; service xử lý nghiệp vụ; repository truy cập dữ liệu.
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
