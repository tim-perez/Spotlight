package com.spotlight.logic;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.MutableData;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.model.Question;
import com.spotlight.model.RoomStatus;

import java.math.RoundingMode;
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
        currentRoomRef = roomsRef.child(roomCode);
        currentRoomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onDataChange(snapshot.getValue(GameRoom.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void updateRoomStatus(RoomStatus status) {
        if (currentRoomRef != null) {
            currentRoomRef.child("status").setValue(status.name());
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
            DatabaseReference playerRef = currentRoomRef.child("players").child(playerId);
            // Remove the player immediately
            playerRef.removeValue();
            // Cancel the server-side disconnect hook since they left cleanly
            playerRef.onDisconnect().cancel();
        }
    }

    public void joinRoom(String roomCode, Player player, OnJoinResultListener listener) {
        roomsRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                GameRoom room = task.getResult().getValue(GameRoom.class);
                if (room != null && "WAITING".equals(room.getStatus())) {
                    currentRoomRef = roomsRef.child(roomCode);
                    DatabaseReference playerRef = currentRoomRef.child("players").child(player.getId());

                    playerRef.setValue(player)
                            .addOnSuccessListener(aVoid -> {
                                // THE FIX: Automatically delete the Guesser if their app closes!
                                playerRef.onDisconnect().removeValue();
                                listener.onSuccess(room);
                            })
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
        Map<String, Object> hostData = new HashMap<>();
        hostData.put("id", host.getId());
        hostData.put("name", host.getName());
        hostData.put("score", host.getScore());
        hostData.put("joinTimestamp", host.getJoinTimestamp());
        hostData.put("avatarColor", host.getAvatarColor());

        Map<String, Object> playersMap = new HashMap<>();
        playersMap.put(host.getId(), hostData);

        Map<String, Object> safeRoomData = new HashMap<>();
        safeRoomData.put("roomCode", room.getRoomCode());
        safeRoomData.put("hostId", room.getHostId());
        safeRoomData.put("status", "WAITING");
        safeRoomData.put("players", playersMap);

        roomsRef.child(room.getRoomCode()).setValue(safeRoomData)
                .addOnSuccessListener(aVoid -> {
                    roomsRef.child(room.getRoomCode()).child("players").child(host.getId()).onDisconnect().removeValue();
                    listener.onSuccess(room);
                })
                .addOnFailureListener(e -> listener.onFailure("Firebase Error: " + e.getMessage()));
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

    public void finalizeRoundTransaction(String roomCode, Map<String, Integer> pointsAwarded, List<String> newLogs, boolean isGameFinished) {
        DatabaseReference currentRoomRef = roomsRef.child(roomCode);
        currentRoomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                GameRoom room = mutableData.getValue(GameRoom.class);
                if (room == null) return Transaction.success(mutableData);

                // --- THE BULLETPROOF LOCK ---
                if (room.getStatusEnum() == RoomStatus.RESULTS || room.getStatusEnum() == RoomStatus.FINISHED) {
                    return Transaction.success(mutableData);
                }

                Map<String, Player> players = room.getPlayers();
                if (players != null) {
                    for (Map.Entry<String, Integer> entry : pointsAwarded.entrySet()) {
                        Player p = players.get(entry.getKey());
                        if (p != null) {
                            p.addScore(entry.getValue());
                        }
                    }
                }

                List<String> logs = room.getLogs();
                if (logs == null) logs = new ArrayList<>();
                logs.addAll(newLogs);
                room.setLogs(logs);

                room.setStatusEnum(isGameFinished ? RoomStatus.FINISHED : RoomStatus.RESULTS);

                mutableData.setValue(room);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            }
        });
    }
}


