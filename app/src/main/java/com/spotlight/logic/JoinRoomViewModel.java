package com.spotlight.logic;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;

import java.util.UUID;

public class JoinRoomViewModel extends AndroidViewModel {

    private final GameRepository repository;
    private final MutableLiveData<GameRoom> roomData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> statusText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isJoined = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> gameStarted = new MutableLiveData<>(false);

    private String playerId;
    private String roomCode;

    public JoinRoomViewModel(@NonNull Application application) {
        super(application);
        repository = new GameRepository();
    }

    public void joinRoom(String roomCode, String playerName, int avatarColor) {
        this.roomCode = roomCode.toUpperCase();
        this.playerId = UUID.randomUUID().toString();
        
        Player player = new Player(playerId, playerName);
        player.setAvatarColor(avatarColor);

        statusText.setValue("Joining...");
        repository.joinRoom(this.roomCode, player, new GameRepository.OnJoinResultListener() {
            @Override
            public void onSuccess(GameRoom room) {
                isJoined.setValue(true);
                observeRoom();
            }

            @Override
            public void onFailure(String message) {
                errorMessage.setValue(message);
                statusText.setValue(null);
            }
        });
    }

    private void observeRoom() {
        repository.getRoomData(roomCode).observeForever(room -> {
            if (room != null) {
                roomData.setValue(room);
                if (room.getPlayers() != null) {
                    statusText.setValue("Waiting for host... (" + room.getPlayers().size() + " players)");
                }
                if ("IN_PROGRESS".equals(room.getStatus())) {
                    gameStarted.setValue(true);
                }
            }
        });
    }

    public LiveData<GameRoom> getRoomData() {
        return roomData;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getStatusText() {
        return statusText;
    }

    public LiveData<Boolean> getIsJoined() {
        return isJoined;
    }

    public LiveData<Boolean> getGameStarted() {
        return gameStarted;
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
