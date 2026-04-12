package com.spotlight.logic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CreateRoomViewModel extends ViewModel {

    private final GameRepository repository;
    private final QuestionRepository questionRepository;
    private final MutableLiveData<GameRoom> roomData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRoomCreated = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> categories = new MutableLiveData<>();

    private String playerId;
    private String roomCode;

    public CreateRoomViewModel(GameRepository repository, QuestionRepository questionRepository) {
        this.repository = repository;
        this.questionRepository = questionRepository;

        this.questionRepository.loadQuestionsAsync(() -> {
            categories.postValue(this.questionRepository.getCategories());
        });
    }

    public LiveData<List<String>> getCategoriesLiveData() {
        return categories;
    }

    public void createRoom(String hostName, int avatarColor) {
        this.playerId = UUID.randomUUID().toString();
        this.roomCode = generateRoomCode();
        
        Player host = new Player(playerId, hostName);
        host.setAvatarColor(avatarColor);
        host.setJoinTimestamp(System.currentTimeMillis());
        
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
        GameRoom room = roomData.getValue();
        if (room == null) return;

        questionRepository.filterByCategory(category);
        String initialQuestion = questionRepository.getRandomQuestion().getText();

        // Start with the player who joined last
        List<Player> playersList = new ArrayList<>(room.getPlayers().values());
        Collections.sort(playersList, (p1, p2) -> Long.compare(p2.getJoinTimestamp(), p1.getJoinTimestamp()));
        String spotlightId = !playersList.isEmpty() ? playersList.get(0).getId() : room.getHostId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "IN_PROGRESS");
        updates.put("category", category);
        updates.put("spotlightPlayerId", spotlightId);
        updates.put("currentQuestion", initialQuestion);

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

    public void leaveRoom() {
        if (roomCode != null && playerId != null) {
            repository.removePlayer(playerId);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanup();
    }
}
