package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.List;

public class JoinRoomActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_room);

        EditText editTextPlayerName = findViewById(R.id.editTextPlayerName);
        EditText editTextRoomCode = findViewById(R.id.editTextRoomCode);
        Button buttonJoin = findViewById(R.id.buttonJoin);
        TextView textViewStatus = findViewById(R.id.textViewStatus);
        View buttonBack = findViewById(R.id.buttonBack);

        buttonBack.setOnClickListener(v -> finish());

        buttonJoin.setOnClickListener(v -> {
            String name = editTextPlayerName.getText().toString().trim();
            String code = editTextRoomCode.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Enter name and room code", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mock joining logic
            buttonJoin.setVisibility(View.GONE);
            editTextPlayerName.setEnabled(false);
            editTextRoomCode.setEnabled(false);
            textViewStatus.setVisibility(View.VISIBLE);

            // In a real app, we would wait for a socket signal from the host
            // For now, let's simulate the game starting after a short delay
            textViewStatus.postDelayed(() -> {
                List<Player> mockPlayers = new ArrayList<>();
                mockPlayers.add(new Player("Host (You)")); // In real app, this would be the actual host
                mockPlayers.add(new Player(name));
                
                Intent intent = new Intent(this, GameActivity.class);
                intent.putExtra("players", (ArrayList<Player>) mockPlayers);
                intent.putExtra("isMultiplayer", true);
                startActivity(intent);
                finish();
            }, 3000);
        });
    }
}
