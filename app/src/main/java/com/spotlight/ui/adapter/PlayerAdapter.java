package com.spotlight.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.R;
import com.spotlight.model.Player;

import java.util.List;

public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder> {

    private List<Player> playerList;
    private String hostId;

    public PlayerAdapter(List<Player> playerList) {
        this.playerList = playerList;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_player, parent, false);
        return new PlayerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerViewHolder holder, int position) {
        Player player = playerList.get(position);
        String displayName = player.getName();
        if (hostId != null && hostId.equals(player.getId())) {
            displayName += " " + holder.itemView.getContext().getString(R.string.host_suffix);
        }
        holder.textViewName.setText(displayName);

        if (player.getAvatarColor() != 0) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(player.getAvatarColor());
            holder.viewAvatar.setBackground(drawable);
        } else {
            holder.viewAvatar.setBackgroundResource(android.R.color.darker_gray);
        }
    }

    @Override
    public int getItemCount() {
        return playerList.size();
    }

    static class PlayerViewHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        View viewAvatar;

        public PlayerViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.textViewPlayerName);
            viewAvatar = itemView.findViewById(R.id.viewAvatarColor);
        }
    }
}
