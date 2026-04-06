package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class CreateRoomActivity extends AppCompatActivity {

    private List<Player> players = new ArrayList<>();
    private PlayerAdapter adapter;
    private String roomCode;
    private String playerId;
    private DatabaseReference roomRef;
    private ValueEventListener roomListener;

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
        View buttonBack = findViewById(R.id.buttonBack);

        buttonBack.setOnClickListener(v -> finish());

        adapter = new PlayerAdapter(players);
        recyclerViewRoomPlayers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewRoomPlayers.setAdapter(adapter);

        buttonGenerateRoom.setOnClickListener(v -> {
            String name = editTextHostName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show();
                return;
            }

            playerId = UUID.randomUUID().toString();
            roomCode = generateRoomCode();
            
            createRoomInFirebase(name);

            textViewRoomCodeDisplay.setText("Room Code: " + roomCode);
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
            // Update room status to IN_PROGRESS
            roomRef.child("status").setValue("IN_PROGRESS");
            
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("players", (ArrayList<Player>) players);
            intent.putExtra("isMultiplayer", true);
            intent.putExtra("roomCode", roomCode);
            intent.putExtra("playerId", playerId);
            startActivity(intent);
        });
    }

    private void createRoomInFirebase(String hostName) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        roomRef = database.getReference("rooms").child(roomCode);

        Player host = new Player(playerId, hostName);
        GameRoom room = new GameRoom(roomCode, playerId);
        room.getPlayers().put(playerId, host);

        roomRef.setValue(room);
        adapter.setHostId(playerId);

        // Listen for players joining
        roomListener = roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameRoom updatedRoom = snapshot.getValue(GameRoom.class);
                if (updatedRoom != null && updatedRoom.getPlayers() != null) {
                    players.clear();
                    players.addAll(updatedRoom.getPlayers().values());
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CreateRoomActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomRef != null && roomListener != null) {
            roomRef.removeEventListener(roomListener);
        }
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
