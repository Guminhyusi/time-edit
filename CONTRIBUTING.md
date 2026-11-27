# Đóng Góp Cho Dự Án / Contributing to Time Edit

Chào mừng mọi đóng góp từ cộng đồng giúp **Time Edit** ngày càng linh hoạt, ổn định và dễ sử dụng hơn!

## Yêu cầu môi trường

- **JDK 21**
- **Android SDK** (`compileSdk 36`, `targetSdk 35`, `minSdk 26`, Build Tools `35.0.1+`)

## Quy trình phát triển & kiểm tra

```bash
./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain
```

Trước khi gửi Pull Request:
1. Đảm bảo toàn bộ unit test (`testDebugUnitTest`) đều đạt và bản biên dịch không phát sinh cảnh báo Kotlin mới.
2. Cập nhật chuỗi giao diện cho cả 3 ngôn ngữ (`AppStringsProvider`: Tiếng Việt, English, 简体中文) nếu có thêm text UI.
3. Không đính kèm API key cá nhân, thông tin nhạy cảm hay tệp ký `.jks` / `keystore.properties`.
4. Sử dụng tiêu đề commit theo quy ước Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
