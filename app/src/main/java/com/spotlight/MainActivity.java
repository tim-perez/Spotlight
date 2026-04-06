package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button buttonPassAndPlay = findViewById(R.id.buttonPassAndPlay);
        Button buttonMultiplayer = findViewById(R.id.buttonMultiplayer);

        buttonPassAndPlay.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GameSetupActivity.class);
            startActivity(intent);
        });

        buttonMultiplayer.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MultiplayerMenuActivity.class);
            startActivity(intent);
        });
    }
}
