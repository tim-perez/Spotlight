package com.spotlight.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spotlight.R;
import com.spotlight.databinding.ActivityJoinRoomBinding;
import com.spotlight.logic.JoinRoomViewModel;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.ui.adapter.PlayerAdapter;
import com.spotlight.util.AvatarUtils;

import java.util.ArrayList;
import java.util.List;

public class JoinRoomActivity extends AppCompatActivity {

    private ActivityJoinRoomBinding binding;
    private JoinRoomViewModel viewModel;
    private PlayerAdapter adapter;
    private int selectedColor;
    private View[] colorViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityJoinRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(JoinRoomViewModel.class);

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
                binding.colorTeal,
                binding.colorPink
        };
        AvatarUtils.setupColorSelection(this, colorViews, color -> selectedColor = color);
        AvatarUtils.resetColorSelection(colorViews);

        binding.buttonBack.setOnClickListener(v -> finish());

        adapter = new PlayerAdapter(new ArrayList<>(), null);
        binding.recyclerViewJoinPlayers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewJoinPlayers.setAdapter(adapter);

        binding.buttonJoin.setOnClickListener(v -> {
            String roomCode = binding.editTextRoomCode.getText().toString().trim();
            String name = binding.editTextPlayerName.getText().toString().trim();

            if (roomCode.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.joinRoom(roomCode, name, selectedColor);
        });
    }

    private void setupObservers() {
        viewModel.getIsJoined().observe(this, isJoined -> {
            if (isJoined) {
                binding.layoutJoinForm.setVisibility(View.GONE);
                binding.textViewRoomCode.setVisibility(View.VISIBLE);
                binding.textViewRoomCode.setText(getString(R.string.room_code_format, viewModel.getRoomCode()));
                binding.textViewStatus.setVisibility(View.VISIBLE);
                binding.recyclerViewJoinPlayers.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getStatusText().observe(this, status -> {
            if (status != null) {
                binding.textViewStatus.setText(status);
            }
        });

        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getRoomData().observe(this, room -> {
            if (room != null && room.getPlayers() != null) {
                List<Player> players = new ArrayList<>(room.getPlayers().values());
                adapter.setPlayers(players);
                adapter.setHostId(room.getHostId());
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.getGameStarted().observe(this, started -> {
            if (started) {
                GameRoom room = viewModel.getRoomData().getValue();
                if (room != null) {
                    Intent intent = new Intent(this, GameActivity.class);
                    intent.putExtra("players", new ArrayList<>(room.getPlayers().values()));
                    intent.putExtra("isMultiplayer", true);
                    intent.putExtra("roomCode", viewModel.getRoomCode());
                    intent.putExtra("playerId", viewModel.getPlayerId());
                    intent.putExtra("hostId", room.getHostId());
                    intent.putExtra("category", room.getCategory());
                    startActivity(intent);
                    finish();
                }
            }
        });
    }
}
