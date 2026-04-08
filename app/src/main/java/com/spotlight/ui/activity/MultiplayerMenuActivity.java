package com.spotlight.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.spotlight.R;

public class MultiplayerMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_menu);

        Button buttonCreateRoom = findViewById(R.id.buttonCreateRoom);
        Button buttonJoinRoom = findViewById(R.id.buttonJoinRoom);
        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        buttonCreateRoom.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateRoomActivity.class);
            startActivity(intent);
        });

        buttonJoinRoom.setOnClickListener(v -> {
            Intent intent = new Intent(this, JoinRoomActivity.class);
            startActivity(intent);
        });
    }
}
