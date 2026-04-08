package com.spotlight.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.R;
import com.spotlight.databinding.ItemResultBinding;
import com.spotlight.model.Player;

import java.util.List;

public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ResultViewHolder> {

    private List<Player> players;

    public ResultAdapter(List<Player> players) {
        this.players = players;
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemResultBinding binding = ItemResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        Player player = players.get(position);
        holder.binding.textViewResultPlayerName.setText(player.getName());
        holder.binding.textViewResultScore.setText(String.valueOf(player.getScore()));

        if (player.getAvatarColor() != 0) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(player.getAvatarColor());
            holder.binding.viewResultAvatarColor.setBackground(drawable);
        } else {
            holder.binding.viewResultAvatarColor.setBackgroundResource(android.R.color.darker_gray);
        }
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        final ItemResultBinding binding;

        public ResultViewHolder(@NonNull ItemResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
