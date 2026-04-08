package com.spotlight.ui.activity;

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
import com.spotlight.R;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.ui.adapter.PlayerAdapter;
import com.spotlight.util.AvatarUtils;

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

        EditText editTextRoomCode = findViewById(R.id.editTextRoomCode);
        EditText editTextPlayerName = findViewById(R.id.editTextPlayerName);
        Button buttonJoin = findViewById(R.id.buttonJoin);
        textViewStatus = findViewById(R.id.textViewStatus);
        RecyclerView recyclerViewPlayers = findViewById(R.id.recyclerViewJoinPlayers);
        LinearLayout layoutJoinForm = findViewById(R.id.layoutJoinForm);
        View buttonBack = findViewById(R.id.buttonBack);

        colorViews = new View[]{
                findViewById(R.id.colorBlue),
                findViewById(R.id.colorGreen),
                findViewById(R.id.colorOrange),
                findViewById(R.id.colorPurple),
                findViewById(R.id.colorRed),
                findViewById(R.id.colorTeal),
                findViewById(R.id.colorPink)
        };
        AvatarUtils.setupColorSelection(this, colorViews, color -> selectedColor = color);
        AvatarUtils.resetColorSelection(colorViews);

        buttonBack.setOnClickListener(v -> finish());

        adapter = new PlayerAdapter(players);
        recyclerViewPlayers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPlayers.setAdapter(adapter);

        buttonJoin.setOnClickListener(v -> {
            roomCode = editTextRoomCode.getText().toString().trim().toUpperCase();
            String name = editTextPlayerName.getText().toString().trim();

            if (roomCode.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            playerId = UUID.randomUUID().toString();
            joinRoom(name);
            
            layoutJoinForm.setVisibility(View.GONE);
            textViewStatus.setVisibility(View.VISIBLE);
            textViewStatus.setText(R.string.status_joining);
        });
    }

    private void joinRoom(String name) {
        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("rooms");
        roomsRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                GameRoom room = task.getResult().getValue(GameRoom.class);
                if (room != null && "WAITING".equals(room.getStatus())) {
                    roomRef = roomsRef.child(roomCode);
                    Player newPlayer = new Player(playerId, name);
                    newPlayer.setAvatarColor(selectedColor);
                    
                    roomRef.child("players").child(playerId).setValue(newPlayer);
                    adapter.setHostId(room.getHostId());
                    findViewById(R.id.recyclerViewJoinPlayers).setVisibility(View.VISIBLE);
                    listenForPlayersAndStart();
                } else {
                    Toast.makeText(this, R.string.error_room_started, Toast.LENGTH_SHORT).show();
                    findViewById(R.id.layoutJoinForm).setVisibility(View.VISIBLE);
                    textViewStatus.setVisibility(View.GONE);
                }
            } else {
                Toast.makeText(this, R.string.error_room_not_found, Toast.LENGTH_SHORT).show();
                findViewById(R.id.layoutJoinForm).setVisibility(View.VISIBLE);
                textViewStatus.setVisibility(View.GONE);
            }
        });
    }

    private void listenForPlayersAndStart() {
        roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null) {
                    if (room.getPlayers() != null) {
                        players.clear();
                        players.addAll(room.getPlayers().values());
                        adapter.notifyDataSetChanged();
                        textViewStatus.setText(getString(R.string.status_waiting_host, players.size()));
                    }

                    if ("IN_PROGRESS".equals(room.getStatus())) {
                        roomRef.removeEventListener(this);
                        Intent intent = new Intent(JoinRoomActivity.this, GameActivity.class);
                        intent.putExtra("players", (ArrayList<Player>) players);
                        intent.putExtra("isMultiplayer", true);
                        intent.putExtra("roomCode", roomCode);
                        intent.putExtra("playerId", playerId);
                        intent.putExtra("hostId", room.getHostId());
                        intent.putExtra("category", room.getCategory());
                        startActivity(intent);
                        finish();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(JoinRoomActivity.this, R.string.error_database, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
