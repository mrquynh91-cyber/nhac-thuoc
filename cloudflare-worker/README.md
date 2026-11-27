# Cloudflare Worker Proxy cho Hiểu Đơn (Miễn phí 100.000 lượt/ngày)

Worker này đứng trung gian giữa app Android **Hiểu Đơn** và Google Gemini API để:
- **Mọi người tải app về dùng Gemini AI ngay lập tức** không cần nhập API Key.
- **Bảo mật 100%:** Gemini API Key nằm hoàn toàn trên server Cloudflare, không bao giờ nằm trong file `.apk`.
- **Hỗ trợ xoay vòng nhiều API Key:** dán 1 hoặc nhiều key cách nhau bằng dấu phẩy `,` vào Secret `GEMINI_API_KEYS` — khi 1 key hết hạn mức (429) hoặc quá tải (503), Worker tự chuyển sang key kế tiếp ngay lập tức.
- **Đổi key từ xa tức thì** trên Cloudflare Dashboard không cần cập nhật app.

## Cách triển khai trong 2 phút (Không cần cài đặt gì trên máy tính)

1. Mở https://dash.cloudflare.com/ → đăng ký/đăng nhập miễn phí.
2. Menu trái chọn **Compute (Workers) → Workers & Pages → Create → Create Worker**.
3. Đặt tên Worker là `hieudon-gemini-proxy` → bấm **Deploy** → sau đó bấm **Edit code**.
4. Xóa toàn bộ code mẫu, copy toàn bộ file `src/index.js` trong thư mục này dán vào → bấm **Deploy** (góc trên bên phải).
5. Quay ra trang Worker → tab **Settings → Variables and Secrets → + Add**:
   - **Secret 1:** Type = **Secret**, Name = `GEMINI_API_KEYS`, Value = dán 1 hoặc nhiều Gemini API Key (cách nhau bằng dấu `,`) → **Save**.
   - **Secret 2 (khuyên dùng để chặn người ngoài gọi ké):** Type = **Secret**, Name = `APP_CLIENT_TOKEN`, Value = một chuỗi mật khẩu bất kỳ (vd: `hd_vn_2026_secret_91`) → **Save**.
6. Sao chép đường dẫn Worker (dạng `https://hieudon-gemini-proxy.<ten-cua-ban>.workers.dev`) và thêm 2 Secrets vào GitHub Actions (`Settings → Secrets → Actions`):
   - `GEMINI_PROXY_URL` = `https://hieudon-gemini-proxy.<ten-cua-ban>.workers.dev`
   - `GEMINI_PROXY_TOKEN` = chuỗi `APP_CLIENT_TOKEN` bạn vừa đặt.
