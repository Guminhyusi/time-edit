# Chính Sách Bảo Mật & Quyền Riêng Tư / Security Policy

## Cam kết quyền riêng tư

- **Không thu thập dữ liệu người dùng**: Time Edit không chứa SDK quảng cáo, analytics, crash tracker hay máy chủ trung gian.
- **Lưu trữ cục bộ 100%**: Mẫu dấu, thời gian tùy chỉnh và thiết lập được lưu trên thiết bị qua Android `DataStore Preferences`; ảnh và video được lưu trực tiếp vào `DCIM/TimeEdit` và `Movies/TimeEdit` thông qua `MediaStore`.
- **Kết nối mạng tối thiểu & minh bạch**: Ứng dụng chỉ gửi tọa độ (khi bạn bật tính năng vị trí/thời tiết hoặc mở hộp thoại tìm địa điểm) tới:
  - Android `Geocoder` hệ thống
  - OpenStreetMap Nominatim (`nominatim.openstreetmap.org`) & bản đồ nền OSM (`tile.openstreetmap.org`)
  - Open-Meteo (`api.open-meteo.com`) để lấy nhiệt độ/thời tiết hiện tại
  Không có thông tin tài khoản, danh bạ hay ảnh chụp nào được tải lên Internet.

## Báo cáo lỗ hổng bảo mật

Nếu bạn phát hiện vấn đề bảo mật hoặc lỗi xử lý dữ liệu EXIF/quyền hệ thống, vui lòng mở issue trên GitHub hoặc gửi mô tả kèm phiên bản ứng dụng và các bước tái hiện (tránh đính kèm khóa ký hoặc thông tin cá nhân).
