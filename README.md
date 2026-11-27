# Hiểu Đơn (MedRead VN) — App Android Đọc Hiểu Đơn Thuốc & Phiếu Xét Nghiệm AI

> **"Chụp là hiểu – Không còn hoang mang trước chữ bác sĩ và bảng chỉ số y khoa."**

Ứng dụng Android Native (Kotlin + Jetpack Compose Material 3), thiết kế giao diện sáng, chữ to, độ tương phản cao thân thiện với người trung niên và gia đình.

## Tính năng MVP 1.0 đã triển khai

1. **Quét & Giải mã Phiếu Xét Nghiệm (Đèn giao thông thông minh)**
   - Chụp ảnh trực tiếp **nhiều trang liên tiếp**, chọn nhiều ảnh từ thư viện, hoặc **tải file PDF kết quả từ bệnh viện** (gửi native PDF tới **Gemini 2.5 Flash**).
   - **Tóm tắt tổng quan** 3–4 gạch đầu dòng dễ hiểu.
   - **Bảng chỉ số đèn giao thông**: 🔴 Đỏ (Bất thường) & 🟡 Vàng (Cận ngưỡng) tự đẩy lên đầu trang kèm giải thích đời thường + lời khuyên ăn uống; 🟢 Xanh (Bình thường) tự thu gọn gọn gàng, bấm vào mới mở chi tiết.
   - **Gợi ý 2–3 câu hỏi thông minh cho bác sĩ** (có nút sao chép nhanh).

2. **Quét & Giải mã Đơn Thuốc (Chữ in & Chữ viết tay)**
   - Áp dụng nguyên tắc an toàn bắt buộc: chữ mờ không đoán bừa, ghi rõ `[Chữ mờ, cần xác nhận lại với nhà thuốc/bác sĩ]`.
   - Bóc tách tên thuốc, hoạt chất, công dụng ngắn gọn, cảnh báo an toàn (buồn ngủ, tránh rượu bia...).
   - **Lịch uống trực quan Sáng – Trưa – Chiều – Tối** kèm nhãn **Trước ăn / Sau ăn**.
   - **Nút 1 chạm "Tạo lịch nhắc uống toàn bộ thuốc"** đẩy thẳng sang báo thức hệ thống (`AlarmManager` + thông báo có nút *Đã uống ✓* / *Nhắc sau 15p* + tự khôi phục sau reboot).

3. **Trợ lý Hỏi – Đáp ngữ cảnh (Chat & Micro tiếng Việt)**
   - Nằm ngay bên dưới mỗi phiếu kết quả vừa quét: hỏi sâu về thuốc uống chung với sữa, người đau dạ dày, dị ứng thuốc... bằng văn bản hoặc bấm nút **Micro** nói trực tiếp (`vi-VN`).

4. **Hồ sơ Gia đình (Sổ khám) & Biểu đồ xu hướng chỉ số**
   - Tách riêng hồ sơ **Tôi, Mẹ, Bố, Con** (hỗ trợ thêm thành viên mới) với thanh đổi hồ sơ nhanh ngay trên Trang chủ.
   - 3 tab con trong **Sổ khám**: `Tất cả phiếu` | `Biểu đồ chỉ số` (tự vẽ đồ thị đường & so sánh % tăng/giảm qua nhiều lần khám) | `Thông tin cơ bản` (nhóm máu, tiền sử dị ứng, bệnh nền, mã & ảnh thẻ BHYT).

5. **Đồng bộ Firebase + Sao lưu JSON + Cập nhật OTA qua GitHub Releases**
   - Hoạt động **Offline-first**, xuất/khôi phục file sao lưu JSON cục bộ.
   - Tích hợp **Firebase Auth + Cloud Firestore + Storage** (tự kích hoạt khi có `app/google-services.json`).
   - **Cập nhật OTA qua GitHub Releases** + workflow tự động build/release tại `.github/workflows/android-ota-release.yml`.
