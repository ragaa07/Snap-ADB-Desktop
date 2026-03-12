package com.ragaa.snapadb.feature.devicecontrols.model

enum class KeyEvent(val code: Int, val label: String) {
    HOME(3, "Home"),
    BACK(4, "Back"),
    CALL(5, "Call"),
    END_CALL(6, "End Call"),
    VOLUME_UP(24, "Volume Up"),
    VOLUME_DOWN(25, "Volume Down"),
    POWER(26, "Power"),
    CAMERA(27, "Camera"),
    MENU(82, "Menu"),
    SEARCH(84, "Search"),
    ENTER(66, "Enter"),
    DELETE(67, "Delete"),
    TAB(61, "Tab"),
    MEDIA_PLAY_PAUSE(85, "Play/Pause"),
    MEDIA_NEXT(87, "Next Track"),
    MEDIA_PREVIOUS(88, "Prev Track"),
    MUTE(91, "Mute"),
    APP_SWITCH(187, "App Switch"),
    BRIGHTNESS_DOWN(220, "Brightness Down"),
    BRIGHTNESS_UP(221, "Brightness Up"),
}
