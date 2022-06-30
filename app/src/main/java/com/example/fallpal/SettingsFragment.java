package com.example.fallpal;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsFragment extends Fragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        Button submitButton = (Button) view.findViewById(R.id.submit_button);
        EditText userName = (EditText) view.findViewById(R.id.user_name);
        EditText contactName1 = (EditText) view.findViewById(R.id.contact_name_1);
        EditText contactNumber1 = (EditText) view.findViewById(R.id.contact_number_1);
        EditText contactName2 = (EditText) view.findViewById(R.id.contact_name_2);
        EditText contactNumber2 = (EditText) view.findViewById(R.id.contact_number_2);


        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle args = new Bundle();
                args.putString("user_name", userName.getText().toString());
                args.putString("contact_name_1", contactName1.getText().toString());
                args.putString("contact_number_1", contactNumber1.getText().toString());
                args.putString("contact_name_2", contactName2.getText().toString());
                args.putString("contact_number_2", contactNumber2.getText().toString());
                args.putString("coming_from_fragment", "settings");
                SharedPreferences sharedPref = getContext().getSharedPreferences("user_settings", 0);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("user_name", userName.getText().toString());
                editor.putString("contact_name_1", contactName1.getText().toString());
                editor.putString("contact_number_1", contactNumber1.getText().toString());
                editor.putString("contact_name_2", contactName2.getText().toString());
                editor.putString("contact_number_2", contactNumber2.getText().toString());
                editor.apply();
                Fragment fragment = new StatusFragment();
                fragment.setArguments(args);
                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "status").commit();
            }
        });


        return view;
    }
}