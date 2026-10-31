package com.nutee.backend.payment.adapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
/** Bật đối soát nền với kênh thật khi đã cấu hình; không đăng ký webhook bên ngoài tự động. */
@Configuration @EnableScheduling
public class PaymentConfiguration {}
