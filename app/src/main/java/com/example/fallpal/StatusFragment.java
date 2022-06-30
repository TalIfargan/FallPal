package com.example.fallpal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class StatusFragment extends Fragment implements ServiceConnection, SerialListener {

    private class ServiceBroadcastReceiver extends BroadcastReceiver {

        public ServiceBroadcastReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String state = extras.getString("extra");
            setEmergencyMessagesSendingStatusText();
        }
    }

    private enum Connected {False, Pending, True}
    final static String newline_crlf = "\r\n";
    final static String emptyString ="";

    private String deviceAddress = "";
    private SerialService service;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private String precedingFragment = "";
    private String userNameText = "";
    private String contactName1 = "";
    private String contactNumber1 = "";
    private TextView bluetoothStatus;
    private TextView contactsStatus;
    private TextView userStatusSummary;
    private TextView userName;
    private TextView status_listening;
    private Button button_status_listening;
    private SharedPreferences sharedPref;
    private Bundle b;
    private ServiceBroadcastReceiver receiver;
    private boolean belowThree = false;
    private boolean aboveTwenty = false;
    private int counter = 0;


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        b = getArguments();
        if (b != null) {
            precedingFragment = getArguments().getString("coming_from_fragment", "");
            if (precedingFragment.equals("devices")) {
                deviceAddress = getArguments().getString("device", "");
            }
        }
        sharedPref = getContext().getSharedPreferences("contacts_notified", 0);
    }




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);
        Button emergencyContactsSetting = (Button) view.findViewById(R.id.button_emergency_contact_settings);
        Button bluetoothSetting = (Button) view.findViewById(R.id.button_bluetooth_settings);
        bluetoothStatus = (TextView) view.findViewById(R.id.bluetooth_status);
        contactsStatus = (TextView) view.findViewById(R.id.contacts_status);
        userStatusSummary = (TextView) view.findViewById(R.id.user_status_summary);
        userName = (TextView) view.findViewById(R.id.user_name);
        status_listening = (TextView) view.findViewById(R.id.status_listening);;
        button_status_listening = (Button) view.findViewById(R.id.button_status_listening);
        updateUserSettings();
        updateBluetoothStatus();
        setEmergencyMessagesSendingStatusText();

        userName.setText(userNameText);

        if (userNameText.equals("") || contactName1.equals("") || contactNumber1.equals("")) {
            contactsStatus.setText("Not set");
            contactsStatus.setTextColor(Color.RED);
        } else {
            contactsStatus.setText("Set");
            contactsStatus.setTextColor(Color.GREEN);
        }



        emergencyContactsSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment, new SettingsFragment(), "settings").commit();
            }
        });

        bluetoothSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment, new DevicesFragment(), "devices").commit();
            }
        });
        button_status_listening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setEmergencyMessagesSendingStatus(!sharedPref.getBoolean("contacts_notified_status", true));
                setEmergencyMessagesSendingStatusText();
            }
        });

        return view;
    }

    private void updateBluetoothStatus() {
        if (connected != Connected.True) {
            bluetoothStatus.setText("Not Connected");
            bluetoothStatus.setTextColor(Color.RED);
        } else {
            bluetoothStatus.setText("Connected");
            bluetoothStatus.setTextColor(Color.GREEN);
        }
        updateUserSummary();
    }

    private void updateUserSettings() {
        SharedPreferences sharedPref2 = getContext().getSharedPreferences("user_settings", 0);
        userNameText = sharedPref2.getString("user_name", "");
        contactName1 = sharedPref2.getString("contact_name_1", "");
        contactNumber1 = sharedPref2.getString("contact_number_1", "");
    }

    private void updateUserSummary() {
        if (userNameText.equals("") || contactName1.equals("") || contactNumber1.equals("") || connected != Connected.True) {
            userStatusSummary.setText("You need to set your name, at least one emergency contact details and connect to the bluetooth falling monitoring device!");
            userStatusSummary.setTextColor(Color.RED);
        } else {
            userStatusSummary.setText("You are all set, if anything happens your emergency contact will be alerted. You can close the app.");
            userStatusSummary.setTextColor(Color.GREEN);
        }
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null && precedingFragment.equals("devices")) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.example.Broadcast");
        receiver = new ServiceBroadcastReceiver();
        getContext().registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed() && precedingFragment.equals("devices")) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }


    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            updateBluetoothStatus();
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        updateBluetoothStatus();
        service.disconnect();
    }

    private void status(String str) {
        Context context = getContext();
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, str, duration);
        toast.show();
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        updateBluetoothStatus();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onSerialRead(byte[] data) {
        try {
            receive(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void receive(byte[] message) {
        String msg = new String(message);
        double norm = 0;
        if (msg.length() > 0) {
            msg = msg.replace(newline_crlf, emptyString);
            try{
                norm = Double.parseDouble(msg);
            } catch (Exception e){
                return;
            }
        }
        if (norm < 3 && !belowThree){
            belowThree = true;
        }
        if (belowThree && counter < 15){
            if (norm > 19){
                aboveTwenty = true;
            }
            else {
                counter++;
            }
        }
        if (counter == 15){
            belowThree = false;
            counter = 0;
        }
        if (belowThree && aboveTwenty) {
            belowThree = false;
            aboveTwenty = false;
            setFallRecognized();
        }
    }

    public void setEmergencyMessagesSendingStatus(boolean status){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("send_emergency_massages", status);
        editor.apply();
        setEmergencyMessagesSendingStatusText();
    }

    public void setFallRecognized(){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("fall_recognized", true);
        editor.apply();
    }

    public void setEmergencyMessagesSendingStatusText(){
        boolean sendEmergencyMassages = sharedPref.getBoolean("send_emergency_massages", true);
        if(sendEmergencyMassages){
            status_listening.setText("App will send emergency message if fall recognized");
            button_status_listening.setText("stop sending");
        }
        else{
            status_listening.setText("App will not send emergency message if fall recognized");
            button_status_listening.setText("start sending");
        }
    }
}

