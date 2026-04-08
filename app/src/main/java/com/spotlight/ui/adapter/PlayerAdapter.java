package com.spotlight.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.R;
import com.spotlight.databinding.ItemPlayerBinding;
import com.spotlight.model.Player;

import java.util.List;

public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder> {

    public interface OnPlayerActionListener {
        void onEditPlayer(Player player, int position);
        void onDeletePlayer(int position);
    }

    private List<Player> playerList;
    private String hostId;
    private OnPlayerActionListener listener;

    public PlayerAdapter(List<Player> playerList, OnPlayerActionListener listener) {
        this.playerList = playerList;
        this.listener = listener;
    }

    public void setPlayers(List<Player> players) {
        this.playerList = players;
        notifyDataSetChanged();
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlayerBinding binding = ItemPlayerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PlayerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerViewHolder holder, int position) {
        Player player = playerList.get(position);
        String displayName = player.getName();
        if (hostId != null && hostId.equals(player.getId())) {
            displayName += " " + holder.binding.getRoot().getContext().getString(R.string.host_suffix);
        }
        holder.binding.textViewPlayerName.setText(displayName);

        if (player.getAvatarColor() != 0) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(player.getAvatarColor());
            holder.binding.viewAvatarColor.setBackground(drawable);
        } else {
            holder.binding.viewAvatarColor.setBackgroundResource(android.R.color.darker_gray);
        }

        if (listener != null) {
            holder.binding.buttonEditPlayer.setVisibility(View.VISIBLE);
            holder.binding.buttonDeletePlayer.setVisibility(View.VISIBLE);
            holder.binding.buttonEditPlayer.setOnClickListener(v -> listener.onEditPlayer(player, holder.getAdapterPosition()));
            holder.binding.buttonDeletePlayer.setOnClickListener(v -> listener.onDeletePlayer(holder.getAdapterPosition()));
        } else {
            holder.binding.buttonEditPlayer.setVisibility(View.GONE);
            holder.binding.buttonDeletePlayer.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return playerList.size();
    }

    static class PlayerViewHolder extends RecyclerView.ViewHolder {
        final ItemPlayerBinding binding;

        public PlayerViewHolder(@NonNull ItemPlayerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
