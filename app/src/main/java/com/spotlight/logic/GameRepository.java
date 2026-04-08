package com.spotlight.logic;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameRepository {
    private final DatabaseReference roomsRef;
    private DatabaseReference currentRoomRef;
    private ValueEventListener roomListener;
    private final MutableLiveData<GameRoom> roomData = new MutableLiveData<>();

    public GameRepository() {
        roomsRef = FirebaseDatabase.getInstance().getReference("rooms");
    }

    public LiveData<GameRoom> getRoomData(String roomCode) {
        if (currentRoomRef != null && roomListener != null) {
            currentRoomRef.removeEventListener(roomListener);
        }

        currentRoomRef = roomsRef.child(roomCode);
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                roomData.setValue(snapshot.getValue(GameRoom.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        };
        currentRoomRef.addValueEventListener(roomListener);
        return roomData;
    }

    public interface OnRoomDataListener {
        void onDataChange(GameRoom room);
    }

    public void getRoomDataOnce(String roomCode, OnRoomDataListener listener) {
        roomsRef.child(roomCode).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onDataChange(snapshot.getValue(GameRoom.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void updateRoomStatus(String status) {
        if (currentRoomRef != null) {
            currentRoomRef.child("status").setValue(status);
        }
    }

    public void submitAnswer(String playerId, String answer) {
        if (currentRoomRef != null) {
            currentRoomRef.child("guesses").child(playerId).setValue(answer);
        }
    }

    public void submitVote(String playerId, String vote) {
        if (currentRoomRef != null) {
            currentRoomRef.child("votes").child(playerId).setValue(vote);
        }
    }

    public void updateRoom(Map<String, Object> updates) {
        if (currentRoomRef != null) {
            currentRoomRef.updateChildren(updates);
        }
    }

    public void removePlayer(String playerId) {
        if (currentRoomRef != null) {
            currentRoomRef.child("players").child(playerId).removeValue();
        }
    }

    public void joinRoom(String roomCode, Player player, OnJoinResultListener listener) {
        roomsRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                GameRoom room = task.getResult().getValue(GameRoom.class);
                if (room != null && "WAITING".equals(room.getStatus())) {
                    currentRoomRef = roomsRef.child(roomCode);
                    currentRoomRef.child("players").child(player.getId()).setValue(player)
                            .addOnSuccessListener(aVoid -> listener.onSuccess(room))
                            .addOnFailureListener(e -> listener.onFailure("Failed to join room"));
                } else {
                    listener.onFailure("Room already started");
                }
            } else {
                listener.onFailure("Room not found");
            }
        });
    }

    public interface OnJoinResultListener {
        void onSuccess(GameRoom room);
        void onFailure(String message);
    }

    public void createRoom(GameRoom room, Player host, OnCreateResultListener listener) {
        currentRoomRef = roomsRef.child(room.getRoomCode());
        currentRoomRef.setValue(room)
                .addOnSuccessListener(aVoid -> {
                    currentRoomRef.child("players").child(host.getId()).setValue(host)
                            .addOnSuccessListener(v -> listener.onSuccess(room))
                            .addOnFailureListener(e -> listener.onFailure("Failed to add host"));
                })
                .addOnFailureListener(e -> listener.onFailure("Failed to create room"));
    }

    public interface OnCreateResultListener {
        void onSuccess(GameRoom room);
        void onFailure(String message);
    }

    public void cleanup() {
        if (currentRoomRef != null && roomListener != null) {
            currentRoomRef.removeEventListener(roomListener);
        }
    }
}
