package com.example.fallpal;

class Constants {

    // values have to be globally unique
    static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    static final String NOTIFICATION_CHANNEL_2 = BuildConfig.APPLICATION_ID + ".Channel_2";
    static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";
    static final String INTENT_CLASS_EMERGENCY_ACTIVITY = BuildConfig.APPLICATION_ID + ".EmergencyActivity";

    // values have to be unique within each app
    static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    private Constants() {}
}
