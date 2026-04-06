package com.spotlight;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AnswerChoiceAdapter extends RecyclerView.Adapter<AnswerChoiceAdapter.ChoiceViewHolder> {

    public interface OnChoiceSelectedListener {
        void onChoiceSelected(String choice);
    }

    private List<String> choices;
    private OnChoiceSelectedListener listener;
    private int selectedPosition = -1;

    public AnswerChoiceAdapter(List<String> choices, OnChoiceSelectedListener listener) {
        this.choices = choices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_choice, parent, false);
        return new ChoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChoiceViewHolder holder, int position) {
        String choice = choices.get(position);
        holder.textViewChoice.setText(choice);
        
        if (selectedPosition == position) {
            holder.itemView.setBackgroundColor(Color.LTGRAY);
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousSelected = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousSelected);
            notifyItemChanged(selectedPosition);
            listener.onChoiceSelected(choice);
        });
    }

    @Override
    public int getItemCount() {
        return choices.size();
    }

    static class ChoiceViewHolder extends RecyclerView.ViewHolder {
        TextView textViewChoice;

        public ChoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewChoice = itemView.findViewById(R.id.textViewChoice);
        }
    }
}
