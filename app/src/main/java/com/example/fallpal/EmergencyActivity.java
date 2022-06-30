package com.example.fallpal;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

public class EmergencyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);
        Button disable_button = (Button) findViewById(R.id.disable_button);
        disable_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                status(String.format("The emergency alert has been disabled!"));
                SharedPreferences sharedPref = getSharedPreferences("contacts_notified", 0);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("send_emergency_massages", false);
                editor.apply();
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                Bundle b = new Bundle();
                b.putBoolean("send_emergency_massages", false);
                intent.putExtras(b);
                startActivity(intent);
                finish();
            }
        });
    }
    private void status(String str) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, str, duration);
        toast.show();
    }
}