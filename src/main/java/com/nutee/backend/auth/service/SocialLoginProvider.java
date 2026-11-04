package com.nutee.backend.auth.service;

/** Strategy xác minh danh tính; mỗi nhà cung cấp chỉ cần một implementation. */
public interface SocialLoginProvider {

    /** Trả về mã provider dùng để lưu liên kết tài khoản, ví dụ GOOGLE. */
    String name();

    /** Từ chối sử dụng provider khi chưa bật hoặc chưa có cấu hình hợp lệ. */
    void requireEnabled();

    /** Xác minh credential và nonce trước khi trả về danh tính đã được tin cậy. */
    SocialIdentity verify(String credential, String nonce);
}
