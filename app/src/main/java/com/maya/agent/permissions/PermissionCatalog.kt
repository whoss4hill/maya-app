package com.maya.agent.permissions

import android.Manifest
import android.os.Build

/**
 * The 12 onboarding permission groups from the approved designs.
 * Each carries its one-line Hinglish reason. The owner can skip any;
 * asking up front never loosens the use gates (camera/mic/location stay
 * owner-summoned only).
 */
data class PermissionItem(
    val id: String,
    val title: String,
    val reason: String,
    val manifestPerms: List<String>,
    /** Special access needs a Settings screen instead of a runtime dialog. */
    val special: Special? = null,
) {
    enum class Special { NOTIFICATION_LISTENER, ACCESSIBILITY, OVERLAY, BATTERY }
}

object PermissionCatalog {

    val page1: List<PermissionItem> = listOf(
        PermissionItem(
            "mic", "Microphone",
            "Tum bolo, main sun saku. Wake word ko kabhi bhi pause kar sakte ho.",
            listOf(Manifest.permission.RECORD_AUDIO),
        ),
        PermissionItem(
            "camera", "Camera",
            "Tum photo ya video maango, tabhi camera khulega.",
            listOf(Manifest.permission.CAMERA),
        ),
        PermissionItem(
            "location", "Location",
            "Jab raasta ya nearby jagah poochho, tabhi location use hogi.",
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        ),
        PermissionItem(
            "media", "Photos, media aur files",
            "Tumhare kehne par files dhoondhne aur save karne ke liye.",
            if (Build.VERSION.SDK_INT >= 33)
                listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            else
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
        ),
        PermissionItem(
            "contacts", "Contacts",
            "Jis insaan ko tum bulao, uska contact dhoondhne ke liye.",
            listOf(Manifest.permission.READ_CONTACTS),
        ),
        PermissionItem(
            "calendar", "Calendar",
            "Tumhare kehne par reminders aur events ke liye.",
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        ),
    )

    val page2: List<PermissionItem> = listOf(
        PermissionItem(
            "phone", "Phone aur call log",
            "Calls karne, caller batane aur call history ke liye.",
            listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG),
        ),
        PermissionItem(
            "sms", "SMS",
            "Tumhare kehne par messages bhejne aur padhne ke liye.",
            listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS),
        ),
        PermissionItem(
            "notifications", "Notifications padhna",
            "Aayi hui notifications sunane aur reply karne ke liye.",
            emptyList(), special = PermissionItem.Special.NOTIFICATION_LISTENER,
        ),
        PermissionItem(
            "accessibility", "Phone control (Accessibility)",
            "Tumhare liye apps kholne, tap karne aur screens padhne ke liye. Sirf tumhare kehne par.",
            emptyList(), special = PermissionItem.Special.ACCESSIBILITY,
        ),
        PermissionItem(
            "overlay", "Floating orb",
            "Doosri apps ke upar chhota Maya orb dikhane ke liye.",
            emptyList(), special = PermissionItem.Special.OVERLAY,
        ),
        PermissionItem(
            "battery", "Background chalna",
            "Battery optimization se chhut, taaki wake word hamesha kaam kare.",
            emptyList(), special = PermissionItem.Special.BATTERY,
        ),
    )

    val all = page1 + page2
}
