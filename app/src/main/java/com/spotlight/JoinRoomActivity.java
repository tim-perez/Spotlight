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

//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.UUID;

public class JoinRoomActivity extends AppCompatActivity {

//    private DatabaseReference roomRef;
    private String playerId;
    private String roomCode;

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
            roomCode = editTextRoomCode.getText().toString().trim().toUpperCase();

            if (name.isEmpty() || roomCode.isEmpty()) {
                Toast.makeText(this, "Enter name and room code", Toast.LENGTH_SHORT).show();
                return;
            }

            playerId = UUID.randomUUID().toString();
            joinRoom(name);
        });
    }

    private void joinRoom(String name) {
/*        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("rooms");
        roomsRef.child(roomCode).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null) {
                    if ("WAITING".equals(room.getStatus())) {
                        Player player = new Player(playerId, name);
                        roomsRef.child(roomCode).child("players").child(playerId).setValue(player);
                        
                        listenForGameStart();
                        Toast.makeText(JoinRoomActivity.this, "Joined Room. Waiting for host...", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(JoinRoomActivity.this, "Game already in progress", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(JoinRoomActivity.this, "Room not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(JoinRoomActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
            }
        });*/
    }

    private void listenForGameStart() {
/*        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomCode);
        roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null && "IN_PROGRESS".equals(room.getStatus())) {
                    roomRef.removeEventListener(this);
                    
                    Intent intent = new Intent(JoinRoomActivity.this, GameActivity.class);
                    intent.putExtra("players", new ArrayList<>(room.getPlayers().values()));
                    intent.putExtra("isMultiplayer", true);
                    intent.putExtra("roomCode", roomCode);
                    intent.putExtra("playerId", playerId);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });*/
    }
}
