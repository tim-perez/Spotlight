package com.spotlight.logic;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.spotlight.model.Player;
import java.util.ArrayList;
import java.util.List;

public class GameSetupViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Player>> players = new MutableLiveData<>(new ArrayList<>());
    private final QuestionRepository questionRepository;
    private int selectedColor = 0;

    public GameSetupViewModel(@NonNull Application application) {
        super(application);
        questionRepository = new QuestionRepository(application);
    }

    public LiveData<List<Player>> getPlayers() {
        return players;
    }

    public List<String> getCategories() {
        return questionRepository.getCategories();
    }

    public void addPlayer(String name) {
        List<Player> currentPlayers = new ArrayList<>(players.getValue());
        Player newPlayer = new Player(name);
        newPlayer.setAvatarColor(selectedColor);
        currentPlayers.add(newPlayer);
        players.setValue(currentPlayers);
    }

    public void setSelectedColor(int color) {
        this.selectedColor = color;
    }

    public int getSelectedColor() {
        return selectedColor;
    }

    public void removePlayer(int position) {
        List<Player> currentPlayers = new ArrayList<>(players.getValue());
        if (position >= 0 && position < currentPlayers.size()) {
            currentPlayers.remove(position);
            players.setValue(currentPlayers);
        }
    }

    public void updatePlayer(int position, String newName, int newColor) {
        List<Player> currentPlayers = new ArrayList<>(players.getValue());
        if (position >= 0 && position < currentPlayers.size()) {
            Player player = currentPlayers.get(position);
            player.setName(newName);
            player.setAvatarColor(newColor);
            players.setValue(currentPlayers);
        }
    }
}
