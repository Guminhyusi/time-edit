/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.domain.watermark

import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.i18n.AppStrings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class WatermarkStyleId(
    val displayName: String,
    val baseId: String,
    val supportsTitle: Boolean = true,
    val supportsSubtitle: Boolean = false,
    val isFullWidth: Boolean = false,
) {
    TIME_LOCATION("Giờ & Địa điểm", "34", supportsTitle = false, supportsSubtitle = false),
    CLOCK_IN("Điểm danh", "21", supportsTitle = true, supportsSubtitle = false),
    CUSTOM_TEXT("Văn bản", "10", supportsTitle = true, supportsSubtitle = false),
    COMPLETED("Hoàn thành", "80", supportsTitle = true, supportsSubtitle = true),
    CONSTRUCTION_RECORD("Hồ sơ kỹ thuật", "120", supportsTitle = true, supportsSubtitle = true),
    WORK_REPORT("Nhật ký việc", "20", supportsTitle = true, supportsSubtitle = false),
    SHEET("Bảng biểu", "100", supportsTitle = true, supportsSubtitle = false),
    CLEANED("Đã dọn dẹp", "90", supportsTitle = true, supportsSubtitle = false),
    LOCATION_BAR("Tọa độ & La bàn", "60", supportsTitle = false, supportsSubtitle = false, isFullWidth = true),
    SECURITY_GUARD("Tuần tra", "43", supportsTitle = true, supportsSubtitle = true);

    fun localizedName(language: AppLanguage): String = when (this) {
        TIME_LOCATION -> when (language) {
            AppLanguage.VI -> "Giờ & Địa điểm"
            AppLanguage.EN -> "Time & Location"
            AppLanguage.ZH_CN -> "时间地点"
        }
        CLOCK_IN -> when (language) {
            AppLanguage.VI -> "Điểm danh"
            AppLanguage.EN -> "Clock-In"
            AppLanguage.ZH_CN -> "考勤打卡"
        }
        CUSTOM_TEXT -> when (language) {
            AppLanguage.VI -> "Văn bản"
            AppLanguage.EN -> "Custom Text"
            AppLanguage.ZH_CN -> "自定义文本"
        }
        COMPLETED -> when (language) {
            AppLanguage.VI -> "Hoàn thành"
            AppLanguage.EN -> "Completed"
            AppLanguage.ZH_CN -> "完成记录"
        }
        CONSTRUCTION_RECORD -> when (language) {
            AppLanguage.VI -> "Hồ sơ kỹ thuật"
            AppLanguage.EN -> "Engineering"
            AppLanguage.ZH_CN -> "工程记录"
        }
        WORK_REPORT -> when (language) {
            AppLanguage.VI -> "Nhật ký việc"
            AppLanguage.EN -> "Work Record"
            AppLanguage.ZH_CN -> "工作记录"
        }
        SHEET -> when (language) {
            AppLanguage.VI -> "Bảng biểu"
            AppLanguage.EN -> "Sheet"
            AppLanguage.ZH_CN -> "表格清单"
        }
        CLEANED -> when (language) {
            AppLanguage.VI -> "Đã dọn dẹp"
            AppLanguage.EN -> "Cleaned"
            AppLanguage.ZH_CN -> "保洁完成"
        }
        LOCATION_BAR -> when (language) {
            AppLanguage.VI -> "Tọa độ & La bàn"
            AppLanguage.EN -> "Location Bar"
            AppLanguage.ZH_CN -> "经纬方位"
        }
        SECURITY_GUARD -> when (language) {
            AppLanguage.VI -> "Tuần tra"
            AppLanguage.EN -> "Patrol"
            AppLanguage.ZH_CN -> "安全巡逻"
        }
    }
}

@Serializable
enum class DateFormatStyle(val label: String, val pattern: String) {
    VN_LONG("25 Tháng 11,2026", "dd 'Tháng' M,yyyy"),
    EN_LONG("25 November 2026", "dd MMMM yyyy"),
    ZH_LONG("2026年11月25日", "yyyy'年'MM'月'dd'日'"),
    DMY_SLASH("25/11/2026", "dd/MM/yyyy"),
    YMD_SLASH("2026/11/25", "yyyy/MM/dd"),
    YMD_DASH("2026-11-25", "yyyy-MM-dd"),
    MDY_TEXT("Nov 25, 2026", "MMM dd, yyyy");

    fun isRegionalLong(): Boolean = this == VN_LONG || this == EN_LONG || this == ZH_LONG

    companion object {
        fun regionalLong(language: AppLanguage): DateFormatStyle = when (language) {
            AppLanguage.VI -> VN_LONG
            AppLanguage.EN -> EN_LONG
            AppLanguage.ZH_CN -> ZH_LONG
        }
    }
}

@Serializable
enum class WatermarkCorner {
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    FREE,
}

@Serializable
enum class TimeLayoutVariant {
    CLASSIC_LARGE,
    COMPACT_BAR;

    fun next(): TimeLayoutVariant = if (this == CLASSIC_LARGE) COMPACT_BAR else CLASSIC_LARGE
}

@Serializable
enum class LogoPlacement(val label: String, val gravityCode: Int) {
    TOP_LEFT("Trên trái", 0),
    TOP_RIGHT("Trên phải", 1),
    ABOVE_WATERMARK("Trên con dấu", 2),
    BOTTOM_RIGHT("Dưới phải", 3),
    BOTTOM_LEFT("Dưới trái", 4);
}

@Serializable
enum class BrandBadgePreset(val label: String) {
    VN_AUTHENTIC("Timemark 100% Chân thực"),
    ORIGINAL_COLOR("Timemark Camera"),
    ORIGINAL_VN("Timemark Máy ảnh"),
    ORIGINAL_EN("Timemark White"),
    ORIGINAL_ZH("Timemark 中文版"),
    CUSTOM_IMAGE("Ảnh riêng"),
    TIME_EDIT_OSS("Chữ tùy chỉnh"),
    OFF("Tắt");

    fun displayName(strings: AppStrings): String = when (this) {
        VN_AUTHENTIC -> strings.wmBrandPresetAuthentic
        ORIGINAL_COLOR -> strings.wmBrandPresetOriginalColor
        ORIGINAL_VN -> strings.wmBrandPresetOriginalVn
        ORIGINAL_EN -> strings.wmBrandPresetOriginalEn
        ORIGINAL_ZH -> strings.wmBrandPresetOriginalZh
        CUSTOM_IMAGE -> strings.wmBrandPresetCustomImage
        TIME_EDIT_OSS -> strings.wmBrandPresetTimeEditOss
        OFF -> strings.wmBrandPresetOff
    }
}

@Serializable
enum class CoordinateFormatStyle(val label: String) {
    DECIMAL_NSEW("10.7749°N, 106.6924°E"),
    DM_NSEW("10°46.49'N, 106°41.55'E"),
    DECIMAL_SIGNED("10.77490, 106.69242"),
}

@Serializable
data class WatermarkPosition(
    val xFraction: Float = 0.022f,
    val yFraction: Float = 0.78f,
    val corner: WatermarkCorner = WatermarkCorner.BOTTOM_LEFT,
)

@Serializable
data class WatermarkField(
    val id: String,
    val label: String,
    val value: String = "",
    val enabled: Boolean = true,
    val isCustom: Boolean = false,
)

@Serializable
data class WatermarkTheme(
    val accentColorHex: String = "#FFC233",
    val textColorHex: String = "#FFFFFF",
    val backgroundAlpha: Float = 0.0f,
    val scale: Float = 1.0f,
)

@Serializable
data class WatermarkTemplate(
    val id: String,
    val name: String,
    val style: WatermarkStyleId,
    val title: String = "",
    val showTitle: Boolean = false,
    val subtitle: String = "",
    val showSubtitle: Boolean = false,
    val styledNote: String = "",
    val showStyledNote: Boolean = false,
    val showPhotoCode: Boolean = false,
    val showVerifiedBadge: Boolean = false,
    val use24Hour: Boolean = true,
    val showSeconds: Boolean = false,
    val dateFormat: DateFormatStyle = DateFormatStyle.VN_LONG,
    val showTime: Boolean = true,
    val timeLayoutVariant: TimeLayoutVariant = TimeLayoutVariant.CLASSIC_LARGE,
    val showAddress: Boolean = true,
    val address: String = "",
    val smallPosition: String = "",
    val largePosition: String = "",
    val useLiveLocation: Boolean = true,
    val pinnedLatitude: Double? = null,
    val pinnedLongitude: Double? = null,
    val pinnedAltitude: Double? = null,
    val showCoordinates: Boolean = false,
    val coordinates: String = "",
    val coordinateFormat: CoordinateFormatStyle = CoordinateFormatStyle.DECIMAL_NSEW,
    val showWeather: Boolean = false,
    val weather: String = "",
    val showAltitude: Boolean = false,
    val altitude: String = "",
    val showCompass: Boolean = false,
    val compass: String = "",
    val showNote: Boolean = false,
    val note: String = "",
    val customFields: List<WatermarkField> = emptyList(),
    val logoUri: String? = null,
    val logoScale: Float = 0.23f,
    val logoAlpha: Float = 1.0f,
    val logoPlacement: LogoPlacement = LogoPlacement.TOP_LEFT,
    val logoPosition: WatermarkPosition? = null,
    val brandBadgePreset: BrandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
    val customBrandImageUri: String? = null,
    val brandBadgeScale: Float = 1.0f,
    val customBrandPrefix: String = "Time",
    val customBrandSuffix: String = "Edit",
    val customBrandSubtitle: String = "Camera",
    val brandPosition: WatermarkPosition? = null,
    val theme: WatermarkTheme = WatermarkTheme(),
    val position: WatermarkPosition = WatermarkPosition(),
) {
    val usesLocationFeature: Boolean
        get() = showAddress || showCoordinates

    val hasCustomElementPositions: Boolean
        get() {
            val defaultPos = DefaultWatermarkTemplates.defaultTemplateForId(id, style).position
            val mainMoved = position.corner != defaultPos.corner ||
                (position.corner == WatermarkCorner.FREE &&
                    (kotlin.math.abs(position.xFraction - defaultPos.xFraction) > 0.005f ||
                        kotlin.math.abs(position.yFraction - defaultPos.yFraction) > 0.005f))
            return mainMoved || brandPosition != null || logoPosition != null
        }
}

object DefaultWatermarkTemplates {
    fun timeLocation(): WatermarkTemplate = WatermarkTemplate(
        id = "34",
        name = "Thời gian",
        style = WatermarkStyleId.TIME_LOCATION,
        showTitle = false,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.VN_LONG,
        showAddress = false,
        address = "",
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#FFC233",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.0f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.025f, 0.85f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun clockIn(): WatermarkTemplate = WatermarkTemplate(
        id = "21",
        name = "Điểm danh",
        style = WatermarkStyleId.CLOCK_IN,
        title = "Điểm danh",
        showTitle = true,
        showPhotoCode = false,
        showVerifiedBadge = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#FFC233",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.0f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.72f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun customText(): WatermarkTemplate = WatermarkTemplate(
        id = "10",
        name = "Văn bản tùy chỉnh",
        style = WatermarkStyleId.CUSTOM_TEXT,
        title = "Tiêu đề của bạn",
        showTitle = true,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        showCoordinates = true,
        coordinates = "",
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#FFC233",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.0f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.74f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun completed(): WatermarkTemplate = WatermarkTemplate(
        id = "80",
        name = "Hoàn thành",
        style = WatermarkStyleId.COMPLETED,
        title = "Hoàn thành",
        showTitle = false,
        subtitle = "Giao hàng",
        showSubtitle = false,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        customFields = listOf(
            WatermarkField(id = "staff", label = "Nhân viên", value = "", enabled = false),
            WatermarkField(id = "work_id", label = "Mã công việc", value = "", enabled = false),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#391A13",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.92f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.68f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun constructionRecord(): WatermarkTemplate = WatermarkTemplate(
        id = "120",
        name = "Hồ sơ kỹ thuật",
        style = WatermarkStyleId.CONSTRUCTION_RECORD,
        title = "Hồ sơ kỹ thuật",
        showTitle = true,
        subtitle = "",
        showSubtitle = false,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        showCoordinates = false,
        coordinates = "",
        customFields = listOf(
            WatermarkField(id = "developer", label = "Chủ đầu tư", value = "", enabled = false),
            WatermarkField(id = "desc", label = "Mô tả", value = "Thử kín nội bộ tầng 3", enabled = false),
            WatermarkField(id = "area", label = "Khu vực", value = "Khu B", enabled = false),
            WatermarkField(id = "operator", label = "Thi công", value = "", enabled = false),
            WatermarkField(id = "inspector", label = "Giám sát", value = "", enabled = false),
            WatermarkField(id = "inspection", label = "Nghiệm thu", value = "Thử kín nội bộ tầng 3", enabled = true),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#2A489E",
            textColorHex = "#000000",
            backgroundAlpha = 0.85f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.56f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun workReport(): WatermarkTemplate = WatermarkTemplate(
        id = "20",
        name = "Nhật ký công việc",
        style = WatermarkStyleId.WORK_REPORT,
        title = "Nhật ký công việc",
        showTitle = true,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        showWeather = false,
        showCoordinates = false,
        coordinates = "",
        customFields = listOf(
            WatermarkField(id = "project", label = "Nội dung", value = "Thử kín nội bộ tầng 3", enabled = true),
            WatermarkField(id = "area", label = "Khu vực", value = "Tầng 3 trục 1B", enabled = false),
            WatermarkField(id = "inspection", label = "Hạng mục", value = "", enabled = false),
            WatermarkField(id = "inspector", label = "Người kiểm tra", value = "", enabled = false),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#D98A2B",
            textColorHex = "#000000",
            backgroundAlpha = 0.85f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.62f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun project(): WatermarkTemplate = WatermarkTemplate(
        id = "110",
        name = "Dự án",
        style = WatermarkStyleId.SHEET,
        title = "CHỦ ĐẦU TƯ",
        showTitle = true,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        customFields = listOf(
            WatermarkField(id = "project", label = "Dự án", value = "Giai đoạn 1", enabled = true),
            WatermarkField(id = "content", label = "Công việc", value = "Điện nước", enabled = true),
            WatermarkField(id = "executor", label = "Thực hiện", value = "Đội A", enabled = true),
            WatermarkField(id = "area", label = "Khu vực", value = "", enabled = false),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#74DDA2",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.45f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.58f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun service(): WatermarkTemplate = WatermarkTemplate(
        id = "service01",
        name = "Dịch vụ",
        style = WatermarkStyleId.CUSTOM_TEXT,
        title = "Tên dịch vụ",
        showTitle = true,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        customFields = listOf(
            WatermarkField(id = "s1", label = "Hạng mục 1", value = "Chi tiết dịch vụ", enabled = true),
            WatermarkField(id = "s2", label = "Hạng mục 2", value = "Bảo trì định kỳ", enabled = true),
            WatermarkField(id = "tel", label = "Điện thoại", value = "0974 882 227", enabled = true),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#FFC233",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.0f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.64f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun locationBar(): WatermarkTemplate = WatermarkTemplate(
        id = "60",
        name = "Kinh độ / Vĩ độ",
        style = WatermarkStyleId.LOCATION_BAR,
        title = "Vị trí",
        showTitle = false,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        showCoordinates = true,
        coordinates = "",
        showCompass = true,
        compass = "",
        showWeather = true,
        weather = "",
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#FFC233",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.40f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.74f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun sheet(): WatermarkTemplate = WatermarkTemplate(
        id = "100",
        name = "Bảng biểu",
        style = WatermarkStyleId.SHEET,
        title = "Hồ sơ công việc",
        showTitle = true,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        customFields = listOf(
            WatermarkField(id = "note1", label = "Ghi chú", value = "Đã kiểm tra", enabled = false),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#74DDA2",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.45f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.64f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun cleaned(): WatermarkTemplate = WatermarkTemplate(
        id = "90",
        name = "Đã dọn dẹp",
        style = WatermarkStyleId.CLEANED,
        title = "Hoàn thành",
        showTitle = true,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        customFields = listOf(
            WatermarkField(id = "cleaner", label = "Nhân viên", value = "", enabled = false),
            WatermarkField(id = "workplace", label = "Khu vực", value = "", enabled = false),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#004B92",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.0f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.72f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun securityGuard(): WatermarkTemplate = WatermarkTemplate(
        id = "43",
        name = "Tuần tra bảo vệ",
        style = WatermarkStyleId.SECURITY_GUARD,
        title = "Tuần tra",
        showTitle = true,
        subtitle = "Kiểm tra an ninh",
        showSubtitle = false,
        showPhotoCode = false,
        showTime = true,
        showSeconds = false,
        dateFormat = DateFormatStyle.DMY_SLASH,
        showAddress = true,
        address = "",
        customFields = listOf(
            WatermarkField(id = "work", label = "Nội dung", value = "Cổng & Hàng rào", enabled = false),
            WatermarkField(id = "inspector", label = "Người kiểm tra", value = "", enabled = false),
        ),
        brandBadgePreset = BrandBadgePreset.VN_AUTHENTIC,
        theme = WatermarkTheme(
            accentColorHex = "#2B3B78",
            textColorHex = "#FFFFFF",
            backgroundAlpha = 0.92f,
            scale = 1.0f,
        ),
        position = WatermarkPosition(0.022f, 0.56f, WatermarkCorner.BOTTOM_LEFT),
    )

    fun defaultTemplateForId(
        id: String,
        style: WatermarkStyleId,
        language: AppLanguage = AppLanguage.VI,
    ): WatermarkTemplate =
        all(language).firstOrNull { it.id == id }
            ?: all(language).firstOrNull { it.style == style }
            ?: all(language).first()

    fun all(language: AppLanguage = AppLanguage.VI): List<WatermarkTemplate> = listOf(
        timeLocation(language),
        clockIn(language),
        customText(language),
        completed(language),
        constructionRecord(language),
        workReport(language),
        project(language),
        service(language),
        locationBar(language),
        sheet(language),
        cleaned(language),
        securityGuard(language),
    )

    fun all(): List<WatermarkTemplate> = all(AppLanguage.VI)

    fun defaultTemplateForId(id: String, style: WatermarkStyleId): WatermarkTemplate =
        defaultTemplateForId(id, style, AppLanguage.VI)

    fun timeLocation(language: AppLanguage): WatermarkTemplate = timeLocation().copy(
        name = when (language) {
            AppLanguage.VI -> "Thời gian"
            AppLanguage.EN -> "Time Stamp"
            AppLanguage.ZH_CN -> "经典时间"
        },
        dateFormat = DateFormatStyle.regionalLong(language),
    )

    fun clockIn(language: AppLanguage): WatermarkTemplate = clockIn().copy(
        name = when (language) {
            AppLanguage.VI -> "Điểm danh"
            AppLanguage.EN -> "Clock-In"
            AppLanguage.ZH_CN -> "考勤打卡"
        },
        title = when (language) {
            AppLanguage.VI -> "Điểm danh"
            AppLanguage.EN -> "Clock-In"
            AppLanguage.ZH_CN -> "打卡记录"
        },
    )

    fun customText(language: AppLanguage): WatermarkTemplate = customText().copy(
        name = when (language) {
            AppLanguage.VI -> "Văn bản tùy chỉnh"
            AppLanguage.EN -> "Custom Text"
            AppLanguage.ZH_CN -> "自定义标题"
        },
        title = when (language) {
            AppLanguage.VI -> "Tiêu đề của bạn"
            AppLanguage.EN -> "Your Title"
            AppLanguage.ZH_CN -> "自定义标题"
        },
    )

    fun completed(language: AppLanguage): WatermarkTemplate = completed().copy(
        name = when (language) {
            AppLanguage.VI -> "Hoàn thành"
            AppLanguage.EN -> "Completed"
            AppLanguage.ZH_CN -> "任务完成"
        },
        title = when (language) {
            AppLanguage.VI -> "Hoàn thành"
            AppLanguage.EN -> "Completed"
            AppLanguage.ZH_CN -> "已完成"
        },
        subtitle = when (language) {
            AppLanguage.VI -> "Giao hàng"
            AppLanguage.EN -> "Delivery"
            AppLanguage.ZH_CN -> "配送完成"
        },
        customFields = listOf(
            WatermarkField(
                id = "staff",
                label = when (language) { AppLanguage.VI -> "Nhân viên"; AppLanguage.EN -> "Staff"; AppLanguage.ZH_CN -> "人员" },
                value = "",
                enabled = false,
            ),
            WatermarkField(
                id = "work_id",
                label = when (language) { AppLanguage.VI -> "Mã công việc"; AppLanguage.EN -> "Work ID"; AppLanguage.ZH_CN -> "工单号" },
                value = "",
                enabled = false,
            ),
        ),
    )

    fun constructionRecord(language: AppLanguage): WatermarkTemplate = constructionRecord().copy(
        name = when (language) {
            AppLanguage.VI -> "Hồ sơ kỹ thuật"
            AppLanguage.EN -> "Engineering Record"
            AppLanguage.ZH_CN -> "工程记录"
        },
        title = when (language) {
            AppLanguage.VI -> "Hồ sơ kỹ thuật"
            AppLanguage.EN -> "Engineering Record"
            AppLanguage.ZH_CN -> "工程记录"
        },
        customFields = listOf(
            WatermarkField("developer", when (language) { AppLanguage.VI -> "Chủ đầu tư"; AppLanguage.EN -> "Client"; AppLanguage.ZH_CN -> "建设单位" }, "", false),
            WatermarkField("desc", when (language) { AppLanguage.VI -> "Mô tả"; AppLanguage.EN -> "Details"; AppLanguage.ZH_CN -> "工程描述" }, when (language) { AppLanguage.VI -> "Thử kín nội bộ tầng 3"; AppLanguage.EN -> "Level 3 internal inspection"; AppLanguage.ZH_CN -> "三层内部验收检查" }, false),
            WatermarkField("area", when (language) { AppLanguage.VI -> "Khu vực"; AppLanguage.EN -> "Area"; AppLanguage.ZH_CN -> "施工区域" }, when (language) { AppLanguage.VI -> "Khu B"; AppLanguage.EN -> "Zone B"; AppLanguage.ZH_CN -> "B区" }, false),
            WatermarkField("operator", when (language) { AppLanguage.VI -> "Thi công"; AppLanguage.EN -> "Contractor"; AppLanguage.ZH_CN -> "施工方" }, "", false),
            WatermarkField("inspector", when (language) { AppLanguage.VI -> "Giám sát"; AppLanguage.EN -> "Inspector"; AppLanguage.ZH_CN -> "监理人" }, "", false),
            WatermarkField("inspection", when (language) { AppLanguage.VI -> "Nghiệm thu"; AppLanguage.EN -> "Inspection"; AppLanguage.ZH_CN -> "验收内容" }, when (language) { AppLanguage.VI -> "Thử kín nội bộ tầng 3"; AppLanguage.EN -> "Level 3 internal inspection"; AppLanguage.ZH_CN -> "三层内部验收检查" }, true),
        ),
    )

    fun workReport(language: AppLanguage): WatermarkTemplate = workReport().copy(
        name = when (language) {
            AppLanguage.VI -> "Nhật ký công việc"
            AppLanguage.EN -> "Work Record"
            AppLanguage.ZH_CN -> "工作记录"
        },
        title = when (language) {
            AppLanguage.VI -> "Nhật ký công việc"
            AppLanguage.EN -> "Work Record"
            AppLanguage.ZH_CN -> "工作记录"
        },
        customFields = listOf(
            WatermarkField("project", when (language) { AppLanguage.VI -> "Nội dung"; AppLanguage.EN -> "Task"; AppLanguage.ZH_CN -> "工作内容" }, when (language) { AppLanguage.VI -> "Thử kín nội bộ tầng 3"; AppLanguage.EN -> "Site check & log"; AppLanguage.ZH_CN -> "三层现场巡检记录" }, true),
            WatermarkField("area", when (language) { AppLanguage.VI -> "Khu vực"; AppLanguage.EN -> "Area"; AppLanguage.ZH_CN -> "区域" }, when (language) { AppLanguage.VI -> "Tầng 3 trục 1B"; AppLanguage.EN -> "Floor 3 - Bay B"; AppLanguage.ZH_CN -> "三层B区" }, false),
            WatermarkField("inspection", when (language) { AppLanguage.VI -> "Hạng mục"; AppLanguage.EN -> "Item"; AppLanguage.ZH_CN -> "项目" }, "", false),
            WatermarkField("inspector", when (language) { AppLanguage.VI -> "Người kiểm tra"; AppLanguage.EN -> "Inspector"; AppLanguage.ZH_CN -> "检查人" }, "", false),
        ),
    )

    fun project(language: AppLanguage): WatermarkTemplate = project().copy(
        name = when (language) {
            AppLanguage.VI -> "Dự án"
            AppLanguage.EN -> "Project"
            AppLanguage.ZH_CN -> "项目管理"
        },
        title = when (language) {
            AppLanguage.VI -> "CHỦ ĐẦU TƯ"
            AppLanguage.EN -> "PROJECT TEAM"
            AppLanguage.ZH_CN -> "工程项目"
        },
        customFields = listOf(
            WatermarkField("project", when (language) { AppLanguage.VI -> "Dự án"; AppLanguage.EN -> "Project"; AppLanguage.ZH_CN -> "项目名称" }, when (language) { AppLanguage.VI -> "Giai đoạn 1"; AppLanguage.EN -> "Phase 1"; AppLanguage.ZH_CN -> "一期工程" }, true),
            WatermarkField("content", when (language) { AppLanguage.VI -> "Công việc"; AppLanguage.EN -> "Work"; AppLanguage.ZH_CN -> "施工内容" }, when (language) { AppLanguage.VI -> "Điện nước"; AppLanguage.EN -> "MEP Works"; AppLanguage.ZH_CN -> "水电安装" }, true),
            WatermarkField("executor", when (language) { AppLanguage.VI -> "Thực hiện"; AppLanguage.EN -> "Team"; AppLanguage.ZH_CN -> "负责人" }, when (language) { AppLanguage.VI -> "Đội A"; AppLanguage.EN -> "Team A"; AppLanguage.ZH_CN -> "A组" }, true),
            WatermarkField("area", when (language) { AppLanguage.VI -> "Khu vực"; AppLanguage.EN -> "Zone"; AppLanguage.ZH_CN -> "区域" }, "", false),
        ),
    )

    fun service(language: AppLanguage): WatermarkTemplate = service().copy(
        name = when (language) {
            AppLanguage.VI -> "Dịch vụ"
            AppLanguage.EN -> "Service"
            AppLanguage.ZH_CN -> "售后服务"
        },
        title = when (language) {
            AppLanguage.VI -> "Tên dịch vụ"
            AppLanguage.EN -> "Service Name"
            AppLanguage.ZH_CN -> "服务项目"
        },
        customFields = listOf(
            WatermarkField("s1", when (language) { AppLanguage.VI -> "Hạng mục 1"; AppLanguage.EN -> "Service 1"; AppLanguage.ZH_CN -> "服务内容" }, when (language) { AppLanguage.VI -> "Chi tiết dịch vụ"; AppLanguage.EN -> "Service details"; AppLanguage.ZH_CN -> "常规维护记录" }, true),
            WatermarkField("s2", when (language) { AppLanguage.VI -> "Hạng mục 2"; AppLanguage.EN -> "Service 2"; AppLanguage.ZH_CN -> "检查项" }, when (language) { AppLanguage.VI -> "Bảo trì định kỳ"; AppLanguage.EN -> "Routine maintenance"; AppLanguage.ZH_CN -> "定期巡检保养" }, true),
            WatermarkField("tel", when (language) { AppLanguage.VI -> "Điện thoại"; AppLanguage.EN -> "Phone"; AppLanguage.ZH_CN -> "联系电话" }, "0974 882 227", true),
        ),
    )

    fun locationBar(language: AppLanguage): WatermarkTemplate = locationBar().copy(
        name = when (language) {
            AppLanguage.VI -> "Kinh độ / Vĩ độ"
            AppLanguage.EN -> "Coordinates"
            AppLanguage.ZH_CN -> "经纬天气"
        },
        title = when (language) {
            AppLanguage.VI -> "Vị trí"
            AppLanguage.EN -> "Location"
            AppLanguage.ZH_CN -> "位置信息"
        },
    )

    fun sheet(language: AppLanguage): WatermarkTemplate = sheet().copy(
        name = when (language) {
            AppLanguage.VI -> "Bảng biểu"
            AppLanguage.EN -> "Report Sheet"
            AppLanguage.ZH_CN -> "表格记录"
        },
        title = when (language) {
            AppLanguage.VI -> "Hồ sơ công việc"
            AppLanguage.EN -> "Work Log"
            AppLanguage.ZH_CN -> "工作台账"
        },
        customFields = listOf(
            WatermarkField("note1", when (language) { AppLanguage.VI -> "Ghi chú"; AppLanguage.EN -> "Note"; AppLanguage.ZH_CN -> "备注" }, when (language) { AppLanguage.VI -> "Đã kiểm tra"; AppLanguage.EN -> "Checked"; AppLanguage.ZH_CN -> "已核查" }, false),
        ),
    )

    fun cleaned(language: AppLanguage): WatermarkTemplate = cleaned().copy(
        name = when (language) {
            AppLanguage.VI -> "Đã dọn dẹp"
            AppLanguage.EN -> "Cleaned"
            AppLanguage.ZH_CN -> "保洁完成"
        },
        title = when (language) {
            AppLanguage.VI -> "Hoàn thành"
            AppLanguage.EN -> "Cleaned"
            AppLanguage.ZH_CN -> "已清洁"
        },
        customFields = listOf(
            WatermarkField("cleaner", when (language) { AppLanguage.VI -> "Nhân viên"; AppLanguage.EN -> "Staff"; AppLanguage.ZH_CN -> "保洁员" }, "", false),
            WatermarkField("workplace", when (language) { AppLanguage.VI -> "Khu vực"; AppLanguage.EN -> "Area"; AppLanguage.ZH_CN -> "区域" }, "", false),
        ),
    )

    fun securityGuard(language: AppLanguage): WatermarkTemplate = securityGuard().copy(
        name = when (language) {
            AppLanguage.VI -> "Tuần tra bảo vệ"
            AppLanguage.EN -> "Security Patrol"
            AppLanguage.ZH_CN -> "保安巡逻"
        },
        title = when (language) {
            AppLanguage.VI -> "Tuần tra"
            AppLanguage.EN -> "Patrol"
            AppLanguage.ZH_CN -> "安全巡逻"
        },
        subtitle = when (language) {
            AppLanguage.VI -> "Kiểm tra an ninh"
            AppLanguage.EN -> "Security Check"
            AppLanguage.ZH_CN -> "治安巡查"
        },
        customFields = listOf(
            WatermarkField("work", when (language) { AppLanguage.VI -> "Nội dung"; AppLanguage.EN -> "Scope"; AppLanguage.ZH_CN -> "巡检内容" }, when (language) { AppLanguage.VI -> "Cổng & Hàng rào"; AppLanguage.EN -> "Gate & Perimeter"; AppLanguage.ZH_CN -> "大门及周界" }, false),
            WatermarkField("inspector", when (language) { AppLanguage.VI -> "Người kiểm tra"; AppLanguage.EN -> "Officer"; AppLanguage.ZH_CN -> "巡逻人" }, "", false),
        ),
    )

    fun localizeTemplateForLanguage(
        current: WatermarkTemplate,
        targetLanguage: AppLanguage,
    ): WatermarkTemplate {
        val targetDefault = defaultTemplateForId(current.id, current.style, targetLanguage)
        val allDefaultsForTemplate = AppLanguage.entries.map { lang ->
            defaultTemplateForId(current.id, current.style, lang)
        }

        val knownNames = allDefaultsForTemplate.map { it.name }.toSet()
        val knownTitles = allDefaultsForTemplate.map { it.title }.toSet()
        val knownSubtitles = allDefaultsForTemplate.map { it.subtitle }.toSet()

        val nextName = if (current.name in knownNames) targetDefault.name else current.name
        val nextTitle = if (current.title in knownTitles) targetDefault.title else current.title
        val nextSubtitle = if (current.subtitle in knownSubtitles) targetDefault.subtitle else current.subtitle
        val nextDateFormat = if (current.dateFormat.isRegionalLong()) {
            DateFormatStyle.regionalLong(targetLanguage)
        } else {
            current.dateFormat
        }

        val nextFields = current.customFields.map { field ->
            val targetField = targetDefault.customFields.firstOrNull { it.id == field.id }
            if (targetField == null) {
                field
            } else {
                val knownLabels = allDefaultsForTemplate.mapNotNull { t ->
                    t.customFields.firstOrNull { it.id == field.id }?.label
                }.toSet()
                val knownValues = allDefaultsForTemplate.mapNotNull { t ->
                    t.customFields.firstOrNull { it.id == field.id }?.value
                }.toSet()
                field.copy(
                    label = if (field.label in knownLabels) targetField.label else field.label,
                    value = if (field.value in knownValues) targetField.value else field.value,
                )
            }
        }

        val nextBrandBadge = when (current.brandBadgePreset) {
            BrandBadgePreset.ORIGINAL_VN,
            BrandBadgePreset.ORIGINAL_EN,
            BrandBadgePreset.ORIGINAL_ZH,
            -> when (targetLanguage) {
                AppLanguage.VI -> BrandBadgePreset.ORIGINAL_VN
                AppLanguage.EN -> BrandBadgePreset.ORIGINAL_EN
                AppLanguage.ZH_CN -> BrandBadgePreset.ORIGINAL_ZH
            }
            else -> current.brandBadgePreset
        }

        return current.copy(
            name = nextName,
            title = nextTitle,
            subtitle = nextSubtitle,
            dateFormat = nextDateFormat,
            customFields = nextFields,
            brandBadgePreset = nextBrandBadge,
        )
    }
}

fun generatePhotoCode(epochMillis: Long, templateId: String): String {
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    var hash = epochMillis * 1103515245L + templateId.hashCode().toLong() * 2654435761L
    val sb = StringBuilder(14)
    repeat(14) {
        hash = (hash * 6364136223846793005L + 1442695040888963407L) ushr 1
        val idx = ((hash and 0x7FFFFFFFL) % alphabet.length).toInt()
        sb.append(alphabet[idx])
    }
    return sb.toString()
}

fun computeSnappedPosition(
    rawXFraction: Float,
    rawYFraction: Float,
    boxWidthFraction: Float,
    boxHeightFraction: Float,
    margin: Float = 0.022f,
    snapThreshold: Float = 0.06f,
): WatermarkPosition {
    val maxX = (1f - boxWidthFraction - margin).coerceAtLeast(margin)
    val maxY = (1f - boxHeightFraction - margin).coerceAtLeast(margin)
    val clampedX = rawXFraction.coerceIn(margin, maxX)
    val clampedY = rawYFraction.coerceIn(margin, maxY)

    val nearLeft = kotlin.math.abs(clampedX - margin) <= snapThreshold
    val nearRight = kotlin.math.abs(clampedX - maxX) <= snapThreshold
    val nearTop = kotlin.math.abs(clampedY - margin) <= snapThreshold
    val nearBottom = kotlin.math.abs(clampedY - maxY) <= snapThreshold

    return when {
        nearTop && nearLeft -> WatermarkPosition(margin, margin, WatermarkCorner.TOP_LEFT)
        nearTop && nearRight -> WatermarkPosition(maxX, margin, WatermarkCorner.TOP_RIGHT)
        nearBottom && nearLeft -> WatermarkPosition(margin, maxY, WatermarkCorner.BOTTOM_LEFT)
        nearBottom && nearRight -> WatermarkPosition(maxX, maxY, WatermarkCorner.BOTTOM_RIGHT)
        else -> WatermarkPosition(clampedX, clampedY, WatermarkCorner.FREE)
    }
}

fun clampFreePosition(
    rawXFraction: Float,
    rawYFraction: Float,
    boxWidthFraction: Float,
    boxHeightFraction: Float,
    margin: Float = 0.022f,
): WatermarkPosition {
    val maxX = (1f - boxWidthFraction - margin).coerceAtLeast(margin)
    val maxY = (1f - boxHeightFraction - margin).coerceAtLeast(margin)
    return WatermarkPosition(
        xFraction = rawXFraction.coerceIn(margin, maxX),
        yFraction = rawYFraction.coerceIn(margin, maxY),
        corner = WatermarkCorner.FREE,
    )
}

fun positionForCorner(
    corner: WatermarkCorner,
    boxWidthFraction: Float,
    boxHeightFraction: Float,
    margin: Float = 0.022f,
): WatermarkPosition {
    val maxX = (1f - boxWidthFraction - margin).coerceAtLeast(margin)
    val maxY = (1f - boxHeightFraction - margin).coerceAtLeast(margin)
    return when (corner) {
        WatermarkCorner.TOP_LEFT -> WatermarkPosition(margin, margin, WatermarkCorner.TOP_LEFT)
        WatermarkCorner.TOP_RIGHT -> WatermarkPosition(maxX, margin, WatermarkCorner.TOP_RIGHT)
        WatermarkCorner.BOTTOM_LEFT -> WatermarkPosition(margin, maxY, WatermarkCorner.BOTTOM_LEFT)
        WatermarkCorner.BOTTOM_RIGHT -> WatermarkPosition(maxX, maxY, WatermarkCorner.BOTTOM_RIGHT)
        WatermarkCorner.FREE -> WatermarkPosition(margin, maxY, WatermarkCorner.BOTTOM_LEFT)
    }
}

fun resolveWatermarkOrigin(
    photoWidth: Int,
    photoHeight: Int,
    boxWidth: Float,
    boxHeight: Float,
    outerMargin: Float,
    position: WatermarkPosition,
    isFullWidth: Boolean = false,
    outerMarginVertical: Float = outerMargin,
): Pair<Float, Float> {
    val maxX = (photoWidth - boxWidth - outerMargin).coerceAtLeast(outerMargin)
    val maxY = (photoHeight - boxHeight - outerMarginVertical).coerceAtLeast(outerMarginVertical)
    if (isFullWidth) {
        val y = when (position.corner) {
            WatermarkCorner.TOP_LEFT, WatermarkCorner.TOP_RIGHT -> outerMarginVertical
            WatermarkCorner.BOTTOM_LEFT, WatermarkCorner.BOTTOM_RIGHT -> maxY
            WatermarkCorner.FREE -> (position.yFraction * photoHeight).coerceIn(outerMarginVertical, maxY)
        }
        return outerMargin to y
    }
    return when (position.corner) {
        WatermarkCorner.TOP_LEFT -> outerMargin to outerMarginVertical
        WatermarkCorner.TOP_RIGHT -> maxX to outerMarginVertical
        WatermarkCorner.BOTTOM_LEFT -> outerMargin to maxY
        WatermarkCorner.BOTTOM_RIGHT -> maxX to maxY
        WatermarkCorner.FREE -> (position.xFraction * photoWidth).coerceIn(outerMargin, maxX) to
            (position.yFraction * photoHeight).coerceIn(outerMarginVertical, maxY)
    }
}

object WatermarkJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encodeTemplate(template: WatermarkTemplate): String =
        json.encodeToString(WatermarkTemplate.serializer(), template)

    fun decodeTemplate(raw: String): Result<WatermarkTemplate> = runCatching {
        json.decodeFromString(WatermarkTemplate.serializer(), raw)
    }

    fun encodePresetList(presets: List<WatermarkTemplate>): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(WatermarkTemplate.serializer()),
            presets,
        )

    fun decodePresetList(raw: String): Result<List<WatermarkTemplate>> = runCatching {
        json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(WatermarkTemplate.serializer()),
            raw,
        )
    }
}
