package com.smart.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 64, 32, 32);
        layout.setBackgroundColor(Color.parseColor("#1a1a2e"));
        
        TextView title = new TextView(this);
        title.setText("Smart Keyboard");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);
        
        TextView info = new TextView(this);
        info.setText("Step 1: Enable Smart Keyboard in Settings\n\nStep 2: Select Smart Keyboard as your input method");
        info.setTextColor(Color.parseColor("#aaaaaa"));
        info.setTextSize(16);
        info.setPadding(0, 0, 0, 32);
        layout.addView(info);
        
        Button enableBtn = new Button(this);
        enableBtn.setText("Enable Keyboard");
        enableBtn.setTextColor(Color.WHITE);
        enableBtn.setBackgroundColor(Color.parseColor("#4a4a6a"));
        enableBtn.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });
        layout.addView(enableBtn);
        
        Button selectBtn = new Button(this);
        selectBtn.setText("Switch Keyboard");
        selectBtn.setTextColor(Color.WHITE);
        selectBtn.setBackgroundColor(Color.parseColor("#4a4a6a"));
        selectBtn.setPadding(0, 32, 0, 0);
        selectBtn.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        layout.addView(selectBtn);
        
        setContentView(layout);
    }
}
