package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.UUID;

public class JoinRoomActivity extends AppCompatActivity {

    private DatabaseReference roomRef;
    private String playerId;
    private String roomCode;

    private PlayerAdapter adapter;
    private List<Player> players = new ArrayList<>();

    private TextView textViewStatus;
    private int selectedColor;
    private View[] colorViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_room);

        EditText editTextPlayerName = findViewById(R.id.editTextPlayerName);
        EditText editTextRoomCode = findViewById(R.id.editTextRoomCode);
        Button buttonJoin = findViewById(R.id.buttonJoin);
        textViewStatus = findViewById(R.id.textViewStatus);
        View buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        RecyclerView recyclerViewPlayers = findViewById(R.id.recyclerViewJoinPlayers);
        adapter = new PlayerAdapter(players);
        recyclerViewPlayers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPlayers.setAdapter(adapter);

        setupColorSelection();

        buttonJoin.setOnClickListener(v -> {
            String name = editTextPlayerName.getText().toString().trim();
            String rawCode = editTextRoomCode.getText().toString().trim().toUpperCase();
            
            // Handle case where user might include "Room Code: " prefix
            if (rawCode.startsWith("ROOM CODE:")) {
                roomCode = rawCode.replace("ROOM CODE:", "").trim();
            } else if (rawCode.contains(":")) {
                roomCode = rawCode.substring(rawCode.indexOf(":") + 1).trim();
            } else {
                roomCode = rawCode;
            }

            if (name.isEmpty() || roomCode.isEmpty()) {
                Toast.makeText(this, R.string.error_enter_name_code, Toast.LENGTH_SHORT).show();
                return;
            }

            playerId = UUID.randomUUID().toString();
            joinRoom(name);
        });
    }

    private void setupColorSelection() {
        colorViews = new View[]{
                findViewById(R.id.colorBlue),
                findViewById(R.id.colorGreen),
                findViewById(R.id.colorOrange),
                findViewById(R.id.colorPurple),
                findViewById(R.id.colorRed),
                findViewById(R.id.colorTeal)
        };

        for (View v : colorViews) {
            v.setOnClickListener(view -> {
                for (View other : colorViews) {
                    other.setScaleX(1.0f);
                    other.setScaleY(1.0f);
                    other.setAlpha(0.6f);
                }
                view.setScaleX(1.2f);
                view.setScaleY(1.2f);
                view.setAlpha(1.0f);

                int colorResId = 0;
                String tag = (String) view.getTag();
                if ("blue".equals(tag)) colorResId = R.color.avatar_blue;
                else if ("green".equals(tag)) colorResId = R.color.avatar_green;
                else if ("orange".equals(tag)) colorResId = R.color.avatar_orange;
                else if ("purple".equals(tag)) colorResId = R.color.avatar_purple;
                else if ("red".equals(tag)) colorResId = R.color.avatar_red;
                else if ("teal".equals(tag)) colorResId = R.color.avatar_teal;

                selectedColor = getColor(colorResId);
            });
        }
        resetColorSelection();
    }

    private void resetColorSelection() {
        for (View v : colorViews) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
            v.setAlpha(0.6f);
        }
        colorViews[0].performClick();
    }

    private void joinRoom(String name) {
        textViewStatus.setVisibility(View.VISIBLE);
        textViewStatus.setText(getString(R.string.status_searching, roomCode));
        
        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("rooms");
        roomsRef.child(roomCode).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    textViewStatus.setText(getString(R.string.error_room_not_found, roomCode));
                    return;
                }
                
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null) {
                    if ("WAITING".equals(room.getStatus())) {
                        textViewStatus.setText(R.string.status_waiting_host);
                        Player player = new Player(playerId, name);
                        player.setAvatarColor(selectedColor);
                        roomsRef.child(roomCode).child("players").child(playerId).setValue(player);
                        
                        // UI Changes after joining
                        findViewById(R.id.editTextPlayerName).setVisibility(View.GONE);
                        findViewById(R.id.editTextRoomCode).setVisibility(View.GONE);
                        findViewById(R.id.layoutColorsJoin).setVisibility(View.GONE);
                        findViewById(R.id.buttonJoin).setVisibility(View.GONE);
                        findViewById(R.id.recyclerViewJoinPlayers).setVisibility(View.VISIBLE);
                        
                        listenForPlayersAndStart();
                    } else {
                        textViewStatus.setText(R.string.error_game_in_progress);
                    }
                } else {
                    textViewStatus.setText(R.string.error_reading_room);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                textViewStatus.setText(getString(R.string.error_database_format, error.getMessage()));
            }
        });
    }

    private void listenForPlayersAndStart() {
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomCode);
        roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null) {
                    if (!"WAITING".equals(room.getStatus())) {
                        roomRef.removeEventListener(this);
                        
                        Intent intent = new Intent(JoinRoomActivity.this, GameActivity.class);
                        intent.putExtra("players", new ArrayList<>(room.getPlayers().values()));
                        intent.putExtra("isMultiplayer", true);
                        intent.putExtra("roomCode", roomCode);
                        intent.putExtra("playerId", playerId);
                        intent.putExtra("hostId", room.getHostId());
                        startActivity(intent);
                        finish();
                    } else if (room.getPlayers() != null) {
                        // Update player list if we had a RecyclerView here
                        players.clear();
                        players.addAll(room.getPlayers().values());
                        adapter.setHostId(room.getHostId());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
