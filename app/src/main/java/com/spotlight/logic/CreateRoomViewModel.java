package com.spotlight.logic;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;

import java.util.Random;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class CreateRoomViewModel extends AndroidViewModel {

    private final GameRepository repository;
    private final MutableLiveData<GameRoom> roomData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRoomCreated = new MutableLiveData<>(false);

    private String playerId;
    private String roomCode;

    public CreateRoomViewModel(@NonNull Application application) {
        super(application);
        repository = new GameRepository();
    }

    public void createRoom(String hostName, int avatarColor) {
        this.playerId = UUID.randomUUID().toString();
        this.roomCode = generateRoomCode();
        
        Player host = new Player(playerId, hostName);
        host.setAvatarColor(avatarColor);
        
        GameRoom room = new GameRoom(roomCode, playerId);
        
        repository.createRoom(room, host, new GameRepository.OnCreateResultListener() {
            @Override
            public void onSuccess(GameRoom createdRoom) {
                isRoomCreated.setValue(true);
                observeRoom();
            }

            @Override
            public void onFailure(String message) {
                errorMessage.setValue(message);
            }
        });
    }

    private void observeRoom() {
        repository.getRoomData(roomCode).observeForever(room -> {
            if (room != null) {
                roomData.setValue(room);
            }
        });
    }

    public void startGame(String category) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "IN_PROGRESS");
        updates.put("category", category);
        repository.updateRoom(updates);
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

    public LiveData<GameRoom> getRoomData() {
        return roomData;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsRoomCreated() {
        return isRoomCreated;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanup();
    }
}
