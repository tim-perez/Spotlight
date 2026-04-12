package com.spotlight.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.spotlight.R;
import com.takusemba.spotlight.OnSpotlightListener;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.Target;
import com.takusemba.spotlight.shape.Circle;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CardView buttonPassAndPlay = findViewById(R.id.buttonPassAndPlay);
        CardView buttonMultiplayer = findViewById(R.id.buttonMultiplayer);
        CardView buttonHowToPlay = findViewById(R.id.buttonHowToPlay);
        TextView textViewTitle = findViewById(R.id.textViewTitle);

        // Simple entrance animation for buttons
        buttonPassAndPlay.setAlpha(0f);
        buttonMultiplayer.setAlpha(0f);
        buttonHowToPlay.setAlpha(0f);
        textViewTitle.setAlpha(0f);

        textViewTitle.animate().alpha(1f).setDuration(1000).start();
        buttonPassAndPlay.animate().alpha(1f).setDuration(1000).setStartDelay(300).start();
        buttonMultiplayer.animate().alpha(1f).setDuration(1000).setStartDelay(600).start();
        buttonHowToPlay.animate().alpha(1f).setDuration(1000).setStartDelay(900).start();

        buttonPassAndPlay.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GameSetupActivity.class);
            startActivity(intent);
        });

        buttonMultiplayer.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MultiplayerMenuActivity.class);
            startActivity(intent);
        });

        buttonHowToPlay.setOnClickListener(v -> showHowToPlayDialog());

        android.content.SharedPreferences prefs = getSharedPreferences("SpotlightPrefs", MODE_PRIVATE);
        boolean hasSeenWalkthrough = prefs.getBoolean("hasSeenWalkthrough", false);

        if (!hasSeenWalkthrough) {
            buttonPassAndPlay.post(() -> {
                startWalkthrough();
                // Save that they have seen it, so it never auto-plays again
                prefs.edit().putBoolean("hasSeenWalkthrough", true).apply();
            });
        }
    }

    private void showHowToPlayDialog() {
        new AlertDialog.Builder(this, R.style.Theme_Spotlight_Dialog)
                .setTitle(R.string.how_to_play_title)
                .setMessage(R.string.how_to_play_content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void startWalkthrough() {
        CardView buttonPassAndPlay = findViewById(R.id.buttonPassAndPlay);
        CardView buttonMultiplayer = findViewById(R.id.buttonMultiplayer);
        CardView buttonHowToPlay = findViewById(R.id.buttonHowToPlay);

        View walkthroughView = getLayoutInflater().inflate(R.layout.layout_walkthrough, new FrameLayout(this), false);
        TextView title = walkthroughView.findViewById(R.id.walkthroughTitle);
        TextView desc = walkthroughView.findViewById(R.id.walkthroughDescription);

        List<Target> targets = new ArrayList<>();

        // Target 1: Pass and Play
        Target passAndPlayTarget = new Target.Builder()
                .setAnchor(buttonPassAndPlay)
                .setShape(new Circle(180f))
                .setOverlay(walkthroughView)
                .setOnTargetListener(new com.takusemba.spotlight.OnTargetListener() {
                    @Override
                    public void onStarted() {
                        title.setText(R.string.walkthrough_title_pass_and_play);
                        desc.setText(R.string.walkthrough_desc_pass_and_play);
                    }

                    @Override
                    public void onEnded() {}
                })
                .build();
        targets.add(passAndPlayTarget);

        // Target 2: Online Multiplayer
        Target multiplayerTarget = new Target.Builder()
                .setAnchor(buttonMultiplayer)
                .setShape(new Circle(180f))
                .setOverlay(walkthroughView)
                .setOnTargetListener(new com.takusemba.spotlight.OnTargetListener() {
                    @Override
                    public void onStarted() {
                        title.setText(R.string.walkthrough_title_multiplayer);
                        desc.setText(R.string.walkthrough_desc_multiplayer);
                    }

                    @Override
                    public void onEnded() {}
                })
                .build();
        targets.add(multiplayerTarget);

        // Target 3: How to Play
        Target howToPlayTarget = new Target.Builder()
                .setAnchor(buttonHowToPlay)
                .setShape(new Circle(180f))
                .setOverlay(walkthroughView)
                .setOnTargetListener(new com.takusemba.spotlight.OnTargetListener() {
                    @Override
                    public void onStarted() {
                        title.setText(R.string.how_to_play_title);
                        desc.setText(R.string.walkthrough_desc_how_to_play);
                    }

                    @Override
                    public void onEnded() {}
                })
                .build();
        targets.add(howToPlayTarget);

        Spotlight spotlight = new Spotlight.Builder(this)
                .setTargets(targets)
                .setBackgroundColorRes(R.color.spotlight_background)
                .setDuration(400L)
                .setAnimation(new DecelerateInterpolator(2f))
                .setOnSpotlightListener(new OnSpotlightListener() {
                    @Override
                    public void onStarted() {}

                    @Override
                    public void onEnded() {}
                })
                .build();

        spotlight.start();

        walkthroughView.setOnClickListener(v -> spotlight.next());
    }
}
