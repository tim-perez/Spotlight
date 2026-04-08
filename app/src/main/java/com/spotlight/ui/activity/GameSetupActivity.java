package com.spotlight.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spotlight.R;
import com.spotlight.databinding.ActivityGameSetupBinding;
import com.spotlight.logic.GameSetupViewModel;
import com.spotlight.model.Player;
import com.spotlight.ui.adapter.PlayerAdapter;
import com.spotlight.util.AvatarUtils;

import java.util.ArrayList;
import java.util.List;

public class GameSetupActivity extends AppCompatActivity {

    private ActivityGameSetupBinding binding;
    private GameSetupViewModel viewModel;
    private PlayerAdapter adapter;
    private View[] colorViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGameSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(GameSetupViewModel.class);

        initViews();
        setupObservers();
    }

    private void initViews() {
        colorViews = new View[]{
                binding.colorBlue,
                binding.colorGreen,
                binding.colorOrange,
                binding.colorPurple,
                binding.colorRed,
                binding.colorTeal
        };
        AvatarUtils.setupColorSelection(this, colorViews, color -> viewModel.setSelectedColor(color));
        AvatarUtils.resetColorSelection(colorViews);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, viewModel.getCategories());
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCategory.setAdapter(categoryAdapter);

        binding.buttonBack.setOnClickListener(v -> finish());

        adapter = new PlayerAdapter(new ArrayList<>());
        binding.recyclerViewPlayers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewPlayers.setAdapter(adapter);

        binding.buttonAddPlayer.setOnClickListener(v -> {
            String name = binding.editTextPlayerName.getText().toString().trim();
            if (!name.isEmpty()) {
                viewModel.addPlayer(name);
                binding.editTextPlayerName.setText("");
                AvatarUtils.resetColorSelection(colorViews);
            } else {
                Toast.makeText(this, R.string.error_enter_name, Toast.LENGTH_SHORT).show();
            }
        });

        binding.buttonStartGame.setOnClickListener(v -> {
            List<Player> players = viewModel.getPlayers().getValue();
            if (players != null && players.size() >= 3) {
                Intent intent = new Intent(this, GameActivity.class);
                intent.putExtra("players", (ArrayList<Player>) players);
                intent.putExtra("isMultiplayer", false);
                intent.putExtra("category", binding.spinnerCategory.getSelectedItem().toString());
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.error_min_players, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupObservers() {
        viewModel.getPlayers().observe(this, players -> {
            adapter.setPlayers(players);
        });
    }
}
