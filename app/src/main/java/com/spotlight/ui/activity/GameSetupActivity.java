package com.spotlight.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spotlight.R;
import com.spotlight.databinding.ActivityGameSetupBinding;
import com.spotlight.databinding.DialogEditPlayerBinding;
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

        adapter = new PlayerAdapter(new ArrayList<>(), new PlayerAdapter.OnPlayerActionListener() {
            @Override
            public void onEditPlayer(Player player, int position) {
                showEditPlayerDialog(player, position);
            }

            @Override
            public void onDeletePlayer(int position) {
                viewModel.removePlayer(position);
            }
        });
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

    private void showEditPlayerDialog(Player player, int position) {
        DialogEditPlayerBinding dialogBinding = DialogEditPlayerBinding.inflate(getLayoutInflater());
        dialogBinding.editTextPlayerName.setText(player.getName());

        View[] dialogColorViews = new View[]{
                dialogBinding.colorBlue,
                dialogBinding.colorGreen,
                dialogBinding.colorOrange,
                dialogBinding.colorPurple,
                dialogBinding.colorRed,
                dialogBinding.colorTeal
        };

        final int[] selectedColor = {player.getAvatarColor()};
        AvatarUtils.setupColorSelection(this, dialogColorViews, color -> selectedColor[0] = color);
        // Pre-select current color
        for (View v : dialogColorViews) {
            String tag = (String) v.getTag();
            int colorResId = 0;
            if ("blue".equals(tag)) colorResId = R.color.avatar_blue;
            else if ("green".equals(tag)) colorResId = R.color.avatar_green;
            else if ("orange".equals(tag)) colorResId = R.color.avatar_orange;
            else if ("purple".equals(tag)) colorResId = R.color.avatar_purple;
            else if ("red".equals(tag)) colorResId = R.color.avatar_red;
            else if ("teal".equals(tag)) colorResId = R.color.avatar_teal;
            else if ("pink".equals(tag)) colorResId = R.color.avatar_pink;

            if (colorResId != 0 && getColor(colorResId) == player.getAvatarColor()) {
                v.setScaleX(1.2f);
                v.setScaleY(1.2f);
                v.setAlpha(1.0f);
            } else {
                v.setAlpha(0.6f);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Edit Player")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = dialogBinding.editTextPlayerName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        viewModel.updatePlayer(position, newName, selectedColor[0]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
