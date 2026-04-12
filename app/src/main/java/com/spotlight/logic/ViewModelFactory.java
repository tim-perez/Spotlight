package com.spotlight.logic;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ViewModelFactory implements ViewModelProvider.Factory {

    private final GameRepository gameRepository;
    private final QuestionRepository questionRepository;

    public ViewModelFactory(Context context) {
        this.gameRepository = new GameRepository();
        this.questionRepository = new QuestionRepository(context.getApplicationContext());
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CreateRoomViewModel.class)) {
            return (T) new CreateRoomViewModel(gameRepository, questionRepository);
        } else if (modelClass.isAssignableFrom(JoinRoomViewModel.class)) {
            return (T) new JoinRoomViewModel(gameRepository);
        } else if (modelClass.isAssignableFrom(GameSetupViewModel.class)) {
            return (T) new GameSetupViewModel(questionRepository);
        } else if (modelClass.isAssignableFrom(GameViewModel.class)) {
            return (T) new GameViewModel(gameRepository, questionRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}