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
            "So Maya can hear you. You can pause the wake word any time.",
            listOf(Manifest.permission.RECORD_AUDIO),
        ),
        PermissionItem(
            "camera", "Camera",
            "The camera opens only when you ask for a photo or video.",
            listOf(Manifest.permission.CAMERA),
        ),
        PermissionItem(
            "location", "Location",
            "Location is used only when you ask for directions or nearby places.",
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        ),
        PermissionItem(
            "media", "Photos, media and files",
            "To find and save files when you ask.",
            if (Build.VERSION.SDK_INT >= 33)
                listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            else
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
        ),
        PermissionItem(
            "contacts", "Contacts",
            "To look up the contact of the person you want to call.",
            listOf(Manifest.permission.READ_CONTACTS),
        ),
        PermissionItem(
            "calendar", "Calendar",
            "For reminders and events when you ask.",
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        ),
    )

    val page2: List<PermissionItem> = listOf(
        PermissionItem(
            "phone", "Phone and call log",
            "To place calls, announce callers and use call history.",
            listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG),
        ),
        PermissionItem(
            "sms", "SMS",
            "To send and read messages when you ask.",
            listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS),
        ),
        PermissionItem(
            "notifications", "Read notifications",
            "To read incoming notifications aloud and reply.",
            emptyList(), special = PermissionItem.Special.NOTIFICATION_LISTENER,
        ),
        PermissionItem(
            "accessibility", "Phone control (Accessibility)",
            "To open apps, tap and read screens for you. Only when you ask.",
            emptyList(), special = PermissionItem.Special.ACCESSIBILITY,
        ),
        PermissionItem(
            "overlay", "Floating orb",
            "To show a small Maya orb over other apps.",
            emptyList(), special = PermissionItem.Special.OVERLAY,
        ),
        PermissionItem(
            "battery", "Run in background",
            "Exempt from battery optimization so the wake word keeps working.",
            emptyList(), special = PermissionItem.Special.BATTERY,
        ),
    )

    val all = page1 + page2
}
