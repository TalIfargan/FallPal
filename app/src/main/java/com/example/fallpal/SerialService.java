package com.example.fallpal;

import static com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service implements SerialListener {

    class GPSThread extends Thread {

        // the run method contains code that the thread will execute
        @Override
        public void run() {
            GPSLocation = requestCurrentLocation();
        }
    }

    class SerialBinder extends Binder {
        SerialService getService() {
            return SerialService.this;
        }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private static class QueueItem {
        QueueType type;
        byte[] data;
        Exception e;

        QueueItem(QueueType type, byte[] data, Exception e) {
            this.type = type;
            this.data = data;
            this.e = e;
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final Queue<QueueItem> queue1, queue2;

    private SerialSocket socket;
    private SerialListener listener;
    private boolean connected;

    private final CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
    private FusedLocationProviderClient mFusedLocationClient;
    private double[] GPSLocation;
    Intent statusFragmentIntent;
    SharedPreferences sharedPref;

    private String userNameText = "";
    private String contactNumber1 = "";
    private String contactNumber2 = "";
    private boolean fallRecognized = false;
    private boolean sendMessages = true;
    private String location_url;
    private GPSThread gpsThread;
    private ScheduledExecutorService schd;
    private NotificationManager emergencyNM;
    private NotificationCompat.Builder emergencyNotificationBuilder;


    /**
     * Lifecylce
     */
    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new LinkedList<>();
        queue2 = new LinkedList<>();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("VisibleForTests")
    @Override
    public void onCreate() {
        super.onCreate();
        mFusedLocationClient = new FusedLocationProviderClient(getApplicationContext());
        gpsThread = new GPSThread();
        gpsThread.start();
        statusFragmentIntent = new Intent();
        statusFragmentIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        statusFragmentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        statusFragmentIntent.setAction("com.example.Broadcast");
        statusFragmentIntent.putExtra("extra", 1);
        sharedPref = getSharedPreferences("contacts_notified", 0);
        schd = Executors.newSingleThreadScheduledExecutor();
        createEmergencyNotificationChannel();
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Api
     */
    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        connected = false; // ignore data,errors while disconnecting
        cancelNotification();
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    public void attach(SerialListener listener) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        cancelNotification();
        // use synchronized() to prevent new items in queue2
        // new items will not be added to queue1 because mainLooper.post and attach() run in main thread
        synchronized (this) {
            this.listener = listener;
        }
        for (QueueItem item : queue1) {
            switch (item.type) {
                case Connect:
                    listener.onSerialConnect();
                    break;
                case ConnectError:
                    listener.onSerialConnectError(item.e);
                    break;
                case Read:
                    listener.onSerialRead(item.data);
                    break;
                case IoError:
                    listener.onSerialIoError(item.e);
                    break;
            }
        }
        for (QueueItem item : queue2) {
            switch (item.type) {
                case Connect:
                    listener.onSerialConnect();
                    break;
                case ConnectError:
                    listener.onSerialConnectError(item.e);
                    break;
                case Read:
                    listener.onSerialRead(item.data);
                    break;
                case IoError:
                    listener.onSerialIoError(item.e);
                    break;
            }
        }
        queue1.clear();
        queue2.clear();
    }

    public void detach() {
        if (connected)
            createNotification();
        // items already in event queue (posted before detach() to mainLooper) will end up in queue1
        // items occurring later, will be moved directly to queue2
        // detach() and mainLooper.post run in the main thread, so all items are caught
        listener = null;
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }
        Intent disconnectIntent = new Intent()
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(socket != null ? "Connected to " + socket.getName() : "Background Service")
                .setContentIntent(restartPendingIntent)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent));
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        Notification notification = builder.build();
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createEmergencyNotificationChannel() {
        emergencyNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "en";
        CharSequence channelName = "Emergency notifications";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel nc = new NotificationChannel(channelId, channelName, importance);
        nc.enableLights(true);
        nc.setLightColor(Color.RED);
        nc.enableVibration(true);
        nc.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
        emergencyNM.createNotificationChannel(nc);
        Intent emergencyIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_EMERGENCY_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent emergencyPendingIntent = PendingIntent.getActivity(this, 1, emergencyIntent, PendingIntent.FLAG_IMMUTABLE);

        emergencyNotificationBuilder = new NotificationCompat.Builder(getApplicationContext(), "en")
                .setSmallIcon(R.drawable.ic_baseline_priority_high_24)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle("Fall recognized!")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("If this is a false alarm enter the app and cancel the emergency alert. If not do nothing and the help is on the way!")).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 500, 1000})
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentIntent(emergencyPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }

//
//        emergencyNM = null;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL_2, "Emergency", NotificationManager.IMPORTANCE_HIGH);
//            nc.setShowBadge(false);
//            nc.enableLights(true);
//            nc.setLightColor(Color.RED);
//            nc.setVibrationPattern(new long[] {0});
//            nc.enableVibration(true);
////            nc.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
////            nc.setImportance(NotificationManager.IMPORTANCE_HIGH);
//            emergencyNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//            emergencyNM.createNotificationChannel(nc);
//        }
////        Intent disconnectIntent = new Intent()
////                .setAction(Constants.INTENT_ACTION_DISCONNECT);
//        Intent emergencyIntent = new Intent()
//                .setClassName(this, Constants.INTENT_CLASS_EMERGENCY_ACTIVITY)
//                .setAction(Intent.ACTION_MAIN)
//                .addCategory(Intent.CATEGORY_LAUNCHER);
//        PendingIntent emergencyPendingIntent = PendingIntent.getActivity(this, 1, emergencyIntent, PendingIntent.FLAG_IMMUTABLE);
//        emergencyNotificationBuilder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
//                .setSmallIcon(R.drawable.ic_baseline_priority_high_24)
//                .setColor(getResources().getColor(R.color.colorPrimary))
//                .setContentTitle("Fall recognized!")
//                .setStyle(new NotificationCompat.BigTextStyle()
//                        .bigText("If this is a false alarm enter the app and cancel the emergency alert. If not do nothing and the help is on the way!"))
//                .setContentIntent(emergencyPendingIntent)
////                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setVibrate(new long[]{0, 500, 400, 500})
//                .setDefaults(Notification.DEFAULT_LIGHTS)
//                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
//                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
////                .setOngoing(true)
////                .addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent));
//        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
//        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
////        Notification notification = builder.build();
////        Notification notification = builder.build();
////        if(nm != null){
////            nm.notify(1, builder.build());
////        }
////        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
//    }

    private void cancelNotification() {
        stopForeground(true);
    }

    /**
     * SerialListener
     */
    public void onSerialConnect() {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnect();
                        } else {
                            queue1.add(new QueueItem(QueueType.Connect, null, null));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Connect, null, null));
                }
            }
        }
    }

    public void onSerialConnectError(Exception e) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnectError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.ConnectError, null, e));
                            cancelNotification();
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.ConnectError, null, e));
                    cancelNotification();
                    disconnect();
                }
            }
        }
    }

    public void onSerialRead(byte[] data) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialRead(data);
                        } else {
                            queue1.add(new QueueItem(QueueType.Read, data, null));
                        }
                    });
                    fallRecognized = sharedPref.getBoolean("fall_recognized", true);
                    sendMessages = sharedPref.getBoolean("send_emergency_massages", true);
                    if (fallRecognized && sendMessages) {
                        mainLooper.removeCallbacksAndMessages(null);
                        emergencyNM.notify(0, emergencyNotificationBuilder.build());
                        schd.scheduleAtFixedRate(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    synchronized (this) {
                                        sendEmergencyMessages();
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }, 15, 15, TimeUnit.SECONDS);
                    }
                } else {
                    queue2.add(new QueueItem(QueueType.Read, data, null));
                }
            }
        }
    }

    public void onSerialIoError(Exception e) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialIoError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.IoError, null, e));
                            cancelNotification();
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.IoError, null, e));
                    cancelNotification();
                    disconnect();
                }
            }
        }
    }

    private void sendEmergencyMessages() throws InterruptedException {
        gpsThread.join();
        location_url = String.format(Locale.ENGLISH, "https://www.google.com/maps/search/?api=1&query=%f,%f", GPSLocation[0], GPSLocation[1]);
        boolean listeningStatus = sharedPref.getBoolean("send_emergency_massages", false);
        if (listeningStatus) {
            sendSMSMessage(location_url);
            MediaPlayer music = MediaPlayer.create(getApplicationContext(), R.raw.emergency_message);
            music.start();

            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("send_emergency_massages", false);
            editor.apply();
            sendBroadcast(statusFragmentIntent);
        }
    }


    private void updateUserSettings() {
        SharedPreferences sharedPref2 = getSharedPreferences("user_settings", 0);
        userNameText = sharedPref2.getString("user_name", "");
        contactNumber1 = sharedPref2.getString("contact_number_1", "");
        contactNumber2 = sharedPref2.getString("contact_number_2", "");
    }

    @SuppressLint("DefaultLocale")
    protected void sendSMSMessage(String location_url) {
        updateUserSettings();
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(contactNumber1, null, "Emergency! " + userNameText + " fell at location\n" + location_url + "\nPlease call for help!", null, null);
        if (!contactNumber2.equals("")) {
            SmsManager smsmanager = SmsManager.getDefault();
            smsmanager.sendTextMessage(contactNumber2, null, "Emergency! " + userNameText + " fell at location\n" + location_url + "\nPlease call for help!", null, null);
        }
    }

    @SuppressLint({"MissingPermission", "DefaultLocale"})
    private double[] requestCurrentLocation() {
        double[] GPSLocation = new double[]{0.0, 0.0};
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            GPSLocation[0] = location.getLongitude();
                            GPSLocation[1] = location.getLatitude();
                        }
                    }
                });
        mFusedLocationClient
                .getCurrentLocation(PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Location result = task.getResult();
                        GPSLocation[0] = result.getLatitude();
                        GPSLocation[1] = result.getLongitude();
                    }
                });
        return GPSLocation;
    }
}
