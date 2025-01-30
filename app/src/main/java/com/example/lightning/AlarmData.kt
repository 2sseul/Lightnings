package com.example.lightning

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class AlarmData(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",  // ✅ Firebase ID 필드
    @get:PropertyName("hour") @set:PropertyName("hour") var hour: Int = 0,
    @get:PropertyName("minute") @set:PropertyName("minute") var minute: Int = 0,
    @get:PropertyName("amPm") @set:PropertyName("amPm") var amPm: String = "",
    @get:PropertyName("lightningEnabled") @set:PropertyName("lightningEnabled") var lightningEnabled: Boolean = false,
    @get:PropertyName("remindEnabled") @set:PropertyName("remindEnabled") var remindEnabled: Boolean = false,
    @get:PropertyName("detailsEnabled") @set:PropertyName("detailsEnabled") var detailsEnabled: Boolean = false,
    @get:PropertyName("detailsText") @set:PropertyName("detailsText") var detailsText: String = "",
    @get:PropertyName("isBookmarked") @set:PropertyName("isBookmarked") var isBookmarked: Boolean = false,  // ✅ 북마크 필드
    @get:PropertyName("isActive") @set:PropertyName("isActive") var isActive: Boolean = true
)
