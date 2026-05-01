package com.spotlight.logic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.spotlight.model.Player;
import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@HiltViewModel
public class GameSetupViewModel extends ViewModel {

    private final MutableLiveData<List<Player>> players = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> categories = new MutableLiveData<>();
    private final QuestionRepository questionRepository;
    private int selectedColor = 0;

    @Inject
    public GameSetupViewModel(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;

        this.questionRepository.loadQuestionsAsync(() -> {
            categories.postValue(this.questionRepository.getCategories());
        });
    }

    public LiveData<List<Player>> getPlayers() {
        return players;
    }

    public LiveData<List<String>> getCategoriesLiveData() {
        return categories;
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
