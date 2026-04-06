package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CreateRoomActivity extends AppCompatActivity {

    private List<Player> players = new ArrayList<>();
    private PlayerAdapter adapter;
    private String roomCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        EditText editTextHostName = findViewById(R.id.editTextHostName);
        Button buttonGenerateRoom = findViewById(R.id.buttonGenerateRoom);
        LinearLayout layoutRoomInfo = findViewById(R.id.layoutRoomInfo);
        TextView textViewRoomCodeDisplay = findViewById(R.id.textViewRoomCodeDisplay);
        RecyclerView recyclerViewRoomPlayers = findViewById(R.id.recyclerViewRoomPlayers);
        Button buttonStartMultiplayer = findViewById(R.id.buttonStartMultiplayer);

        adapter = new PlayerAdapter(players);
        recyclerViewRoomPlayers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewRoomPlayers.setAdapter(adapter);

        buttonGenerateRoom.setOnClickListener(v -> {
            String name = editTextHostName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show();
                return;
            }

            roomCode = generateRoomCode();
            textViewRoomCodeDisplay.setText("Room Code: " + roomCode);
            
            players.add(new Player(name));
            adapter.notifyItemInserted(0);

            layoutRoomInfo.setVisibility(View.VISIBLE);
            buttonStartMultiplayer.setVisibility(View.VISIBLE);
            buttonGenerateRoom.setVisibility(View.GONE);
            editTextHostName.setEnabled(false);
        });

        buttonStartMultiplayer.setOnClickListener(v -> {
            if (players.size() < 2) {
                Toast.makeText(this, "Wait for more players", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("players", (ArrayList<Player>) players);
            intent.putExtra("isMultiplayer", true);
            startActivity(intent);
        });
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        while (sb.length() < 4) {
            int index = (int) (rnd.nextFloat() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
}
