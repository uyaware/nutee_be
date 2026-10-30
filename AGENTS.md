# Nguyên tắc làm việc với backend NUTEE

## 1. Nguyên tắc chung

- Ưu tiên code đơn giản, rõ nghĩa, dễ đọc, dễ debug và dễ maintain.
- Áp dụng KISS, DRY, SOLID theo nhu cầu thực tế; không overengineering.
- Ưu tiên tính dễ hiểu hơn việc giảm số dòng code.
- Chỉ triển khai đúng yêu cầu; không tự thêm tính năng hoặc mở rộng phạm vi.
- Nếu yêu cầu chưa rõ hoặc có nhiều cách hiểu ảnh hưởng đến nghiệp vụ, hỏi người dùng trước.
- Trước khi sửa, đọc code liên quan và tuân theo convention hiện có.
- Ưu tiên thay đổi nhỏ nhất đáp ứng yêu cầu, không refactor module không liên quan.
- Không tự thêm dependency, framework, cache, queue hoặc infrastructure khi chưa cần.

## 2. Kiến trúc và tổ chức code

- Sử dụng kiến trúc modular monolith, tổ chức package theo feature/domain thay vì gom toàn bộ controller, service, repository vào các package chung.
- Trong mỗi feature, phân chia trách nhiệm rõ ràng:
  - Controller: HTTP request/response, validation đầu vào.
  - Service: business logic, transaction và phối hợp các thành phần.
  - Repository: truy cập dữ liệu.
  - Entity: ánh xạ database và quản lý trạng thái domain phù hợp.
  - DTO: dữ liệu request/response của API.
- Không đặt business logic trong controller hoặc repository.
- Không để service trở thành lớp quá lớn với nhiều trách nhiệm không liên quan.
- Tái sử dụng code khi có logic thực sự trùng lặp; không tạo abstraction chỉ vì hai đoạn code trông giống nhau.
- Không tạo interface cho mọi service, base class, factory, helper hoặc lớp trung gian không mang lại giá trị cụ thể.
- Không dùng design pattern chỉ để tăng số lớp; ưu tiên luồng xử lý trực tiếp, dễ theo dõi.
- Giữ dependency giữa các module rõ ràng, tránh circular dependency.

## 3. Clean Code và Naming

- Đặt tên class, method, variable theo ý nghĩa nghiệp vụ, dùng tiếng Anh nhất quán.
- Method nên thực hiện một nhiệm vụ rõ ràng; tách method khi giúp code dễ hiểu hơn, không tách máy móc.
- Ưu tiên early return và guard clause để giảm nested if.
- Tránh boolean flag khó hiểu, magic number, biểu thức lồng sâu và side effect bất ngờ.
- Dùng enum hoặc constant cho các tập giá trị nghiệp vụ cố định.
- Không tạo abstraction hoặc generic phức tạp khi code thông thường đã đủ rõ.
- Không viết comment mô tả lại code; chỉ giải thích nghiệp vụ, lý do thiết kế hoặc quyết định không hiển nhiên.
- Viết Javadoc tiếng Việt cho public class, interface và method có nghiệp vụ hoặc điều kiện xử lý không hiển nhiên.
- Không bắt buộc Javadoc cho constructor, getter/setter, private helper đơn giản hoặc method có tên đã đủ rõ nghĩa.

## 4. REST API và DTO

- Giữ API prefix `/api/v1` và các hợp đồng API đang có.
- Sử dụng DTO riêng cho request/response; không trả JPA Entity trực tiếp.
- Validate dữ liệu đầu vào bằng Jakarta Bean Validation.
- Dùng HTTP status code phù hợp và nhất quán.
- Xử lý exception tập trung bằng `@RestControllerAdvice`.
- Phân biệt lỗi validation, lỗi nghiệp vụ, không tìm thấy tài nguyên và lỗi hệ thống.
- Không bắt exception chỉ để trả về null, false hoặc response thành công giả.
- Không để lộ stack trace, thông tin nội bộ hoặc dữ liệu nhạy cảm trong API response.
- Không tự thay đổi API contract hoặc tạo endpoint mới ngoài yêu cầu.

## 5. Spring Data JPA

- Bắt buộc dùng Spring Data JPA cho truy cập dữ liệu nghiệp vụ.
- Entity ánh xạ schema hiện có; repository kế thừa `JpaRepository`.
- Ưu tiên method có sẵn của `JpaRepository` và derived query cho truy vấn đơn giản.
- Dùng `Sort`, `Pageable`, projection và `EntityGraph` khi phù hợp.
- Dùng `Specification` cho bộ lọc động có nhiều tổ hợp.
- Chỉ dùng `@Query` khi truy vấn phức tạp hoặc cách viết đó rõ ràng hơn; ghi lý do khi cần.
- Không ép derived query thành tên quá dài hoặc lọc/phân trang trong bộ nhớ thay vì database.
- Không dùng `JdbcTemplate`, `NamedParameterJdbcTemplate` hoặc JDBC trực tiếp trong `src/main/java` để thay cho JPA.
- Chỉ dùng native query khi JPQL/Criteria không phù hợp và có lý do rõ ràng.
- Quan hệ JPA mặc định tải `LAZY`; chủ động xử lý N+1 bằng `EntityGraph`, fetch join hoặc batch fetching.
- Không fetch collection cùng truy vấn phân trang nếu có nguy cơ phân trang trong bộ nhớ.
- Không tự động cascade hoặc orphan removal khi chưa xác định rõ vòng đời dữ liệu.
- Không sử dụng entity trực tiếp làm API DTO.

## 6. Database và Flyway

- PostgreSQL là database chính; Flyway là công cụ duy nhất quản lý schema.
- Hibernate sử dụng `ddl-auto: validate`, không tự cập nhật schema.
- Không sửa migration đã áp dụng.
- Không reset hoặc xóa dữ liệu chỉ để sửa lỗi mapping hay refactor code.
- Không tự ý thêm, xóa hoặc đổi tên bảng/cột/index/constraint ngoài phạm vi yêu cầu.
- Migration phải có thứ tự hợp lý và tương thích với dữ liệu hiện có.
- Khi thay đổi cấu trúc ảnh hưởng dữ liệu cũ, phải có phương án migration an toàn.
- Ưu tiên migration theo thay đổi nghiệp vụ; không tách file máy móc chỉ để phân loại schema, constraint và index.
- Chỉ tạo index có mục đích rõ ràng; tránh index trùng lặp với PK/UNIQUE.
- SQL trong Flyway, seed và database test được phép sử dụng.

## 7. Transaction và Concurrency

- Đặt `@Transactional` tại service cho nghiệp vụ cần tính nhất quán dữ liệu.
- Dùng `@Transactional(readOnly = true)` cho các luồng đọc phù hợp.
- Không giữ transaction khi gọi API bên ngoài nếu có thể tránh.
- Với checkout, tồn kho, voucher và thanh toán, phải xem xét race condition và request trùng.
- Dùng database constraint, locking, optimistic locking hoặc idempotency khi nghiệp vụ thực sự yêu cầu.
- Không dùng `synchronized` để thay thế concurrency control tại database.
- Không tự thêm distributed lock, queue hoặc cơ chế retry phức tạp nếu chưa có nhu cầu.
- Không nuốt lỗi transaction hoặc giả lập thành công khi thao tác dữ liệu thất bại.

## 8. Security

- Giữ cơ chế JWT hiện tại và cookie HttpOnly cho access/refresh token.
- Không bỏ xác minh token, thời hạn token, CSRF hoặc kiểm tra quyền để đơn giản hóa code.
- Dùng thư viện/framework hiện có cho JWT, validation và bảo mật.
- Không hardcode secret, password, API key hoặc token.
- Không commit thông tin bí mật từ `.env` hay file cấu hình local.
- Không log access token, refresh token, password hoặc dữ liệu nhạy cảm.
- Kiểm tra quyền truy cập tài nguyên theo người dùng sở hữu, không chỉ dựa vào việc đã đăng nhập.
- Với đăng nhập bên thứ ba, dùng Strategy khi cần nhiều provider; mỗi provider xác minh danh tính riêng, nghiệp vụ liên kết tài khoản dùng chung.
- Chỉ triển khai provider được yêu cầu; không tạo implementation giả cho provider chưa hỗ trợ.

## 9. Exception và Logging

- Xử lý exception tại tầng phù hợp, không dùng try-catch tràn lan.
- Sử dụng exception có ý nghĩa nghiệp vụ thay vì RuntimeException chung chung khi cần phân biệt lỗi.
- Không catch rồi bỏ qua exception.
- Ghi log có ngữ cảnh hữu ích như orderId, userId hoặc requestId khi phù hợp.
- Dùng logging framework hiện có; không dùng `System.out.println` để debug.
- Không log cùng một lỗi nhiều lần ở các tầng khác nhau nếu không bổ sung thông tin hữu ích.
- Không log dữ liệu nhạy cảm hoặc toàn bộ request body một cách mặc định.

## 10. Testing

- Ưu tiên test cho business logic, API contract, security và các trường hợp concurrency quan trọng.
- Không test getter/setter, implementation detail hoặc hành vi mặc định của framework.
- Khi refactor, ưu tiên cập nhật test đang có thay vì tạo test trùng lặp.
- Test phải độc lập, có thể chạy lại và không phụ thuộc thứ tự thực thi.
- Không mock những gì cần kiểm chứng bằng database thật, đặc biệt là constraint, transaction và locking.
- Không tự thêm nhiều test class cho cùng một luồng nếu không cần thiết.
- Không xóa hoặc làm yếu test chỉ để pipeline thành công.

## 11. Quy trình làm việc của AI

- Trước khi sửa, xác định đúng file, module và luồng nghiệp vụ liên quan.
- Ưu tiên tái sử dụng code và dependency đang có.
- Không thay đổi code ngoài phạm vi yêu cầu nếu không cần thiết.
- Không tự refactor toàn bộ module khi chỉ được yêu cầu sửa một chức năng.
- Không tự tạo API, DTO, service, repository hoặc abstraction chưa có use case thực tế.
- Nếu phát hiện vấn đề ngoài phạm vi, báo lại thay vì tự sửa.
- Khi có nhiều phương án hợp lý, ưu tiên phương án ít khái niệm hơn và dễ maintain hơn.
- Sau khi thay đổi, chạy các kiểm tra phù hợp với phạm vi sửa.
- Không tuyên bố test/build thành công nếu chưa thực sự chạy.
- Khi bàn giao, báo cáo ngắn gọn:
  - Thay đổi chính.
  - Quyết định kỹ thuật đáng chú ý.
  - Test/build đã chạy và kết quả.
  - Rủi ro hoặc công việc còn lại, nếu có.