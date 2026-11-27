# Changelog

## [1.0.0] - 2026-11-27

Phiên bản phát hành mã nguồn mở đầu tiên của **Time Edit** (`GPL-3.0-or-later`) — ứng dụng camera và đóng dấu thời gian, vị trí linh hoạt cho cộng đồng.

### Tính năng chính
- **Tự do điều chỉnh thời gian & đồng bộ EXIF**: Hỗ trợ 3 chế độ `Thực tế`, `Giờ cố định`, `Lệch giờ` (± ngày/giờ/phút) và ghi đầy đủ thẻ EXIF (`DateTimeOriginal`, `DateTimeDigitized`, `DateTime`, GPS).
- **Chụp ảnh & Quay video đóng dấu trực tiếp**:
  - CameraX tích hợp lấy nét chạm, thu phóng mượt (`0.6x` / `1x` / `2x` + chụm tay), hẹn giờ, đèn flash, tỷ lệ `4:3` / `16:9` / `1:1` và lưới 3×3.
  - Chọn chất lượng ảnh PNG (`SD`, `HD`, `FHD`) với luồng xuất streaming tốc độ cao.
  - Tự động phát hiện khả năng phần cứng thiết bị cho chất lượng quay video (`720p 30fps`, `720p 60fps`, `1080p 30fps`, `1080p 60fps`).
- **Đóng dấu ảnh hàng loạt từ thư viện**: Hỗ trợ đóng dấu cùng một mốc thời gian cho cả lô hoặc chỉnh riêng từng ảnh, kèm nút popup tiến trình `x/n` xoay theo hướng cầm máy.
- **12 mẫu đóng dấu & tùy biến không giới hạn**:
  - 2 bố cục cho mẫu Thời gian (`Mẫu 1` số lớn & `Mẫu 2` thanh ngang gọn) cùng nút chuyển nhanh ngay trên màn hình chụp.
  - Tự do bật/tắt và chỉnh sửa địa chỉ, tọa độ GPS (3 định dạng), thời tiết, độ cao, la bàn, ghi chú, nhãn nổi bật, mã xác thực dọc, dòng tùy chỉnh, logo tổ chức và logo góc phải (mặc định **Timemark 100% Chân thực**, hỗ trợ đổi sang các mẫu gốc khác, ảnh riêng, chữ tùy chỉnh hoặc tắt).
  - Hỗ trợ kéo thả tự do **mọi thành phần đóng dấu** (khối nội dung chính, nhãn góc phải, logo) và nút popup **`Reset`** tự xoay theo hướng cầm máy (`0° / 90° / 180° / 270°`).
- **Địa chỉ & chọn vị trí miễn phí**: Kết hợp Android `Geocoder` đa kết quả và OSM Nominatim `zoom=18`, hỗ trợ tìm kiếm địa điểm và ghim trực tiếp trên bản đồ OpenStreetMap (`osmdroid`) không cần API key trả phí.
- **Giao diện Sáng / Tối & 3 ngôn ngữ**: Hỗ trợ chế độ Sáng (Liquid Glass pastel) và Tối tương phản cao, tự động đổi định dạng ngày tháng và giao diện theo **Tiếng Việt**, **English**, **简体中文**.
