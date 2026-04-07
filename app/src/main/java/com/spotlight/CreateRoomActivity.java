package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import com.spotlight.logic.QuestionRepository;
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
    private Spinner spinnerCategory;
    private QuestionRepository questionRepository;

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
        spinnerCategory = findViewById(R.id.spinnerCategory);
        View buttonBack = findViewById(R.id.buttonBack);

        questionRepository = new QuestionRepository();
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, questionRepository.getCategories());
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

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
            if (players.size() < 3) {
                Toast.makeText(this, "Wait for at least 3 players to start", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String selectedCategory = spinnerCategory.getSelectedItem().toString();
            
            // Update room status and category
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("status", "IN_PROGRESS");
            updates.put("category", selectedCategory);
            roomRef.updateChildren(updates);
            
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("players", (ArrayList<Player>) players);
            intent.putExtra("isMultiplayer", true);
            intent.putExtra("roomCode", roomCode);
            intent.putExtra("playerId", playerId);
            intent.putExtra("hostId", playerId);
            intent.putExtra("category", selectedCategory);
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
