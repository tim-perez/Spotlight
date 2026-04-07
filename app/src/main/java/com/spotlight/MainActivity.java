package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.airbnb.lottie.LottieAnimationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CardView buttonPassAndPlay = findViewById(R.id.buttonPassAndPlay);
        CardView buttonMultiplayer = findViewById(R.id.buttonMultiplayer);
        TextView textViewTitle = findViewById(R.id.textViewTitle);
        LottieAnimationView animationViewBackground = findViewById(R.id.animationViewBackground);

        // Simple entrance animation for buttons
        buttonPassAndPlay.setAlpha(0f);
        buttonMultiplayer.setAlpha(0f);
        textViewTitle.setAlpha(0f);

        textViewTitle.animate().alpha(1f).setDuration(1000).start();
        buttonPassAndPlay.animate().alpha(1f).setDuration(1000).setStartDelay(300).start();
        buttonMultiplayer.animate().alpha(1f).setDuration(1000).setStartDelay(600).start();

        buttonPassAndPlay.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GameSetupActivity.class);
            startActivity(intent);
        });

        buttonMultiplayer.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MultiplayerMenuActivity.class);
            startActivity(intent);
        });

        // Ensure background animation is handled safely
        try {
            if (animationViewBackground != null) {
                animationViewBackground.setFailureListener(result -> {
                    animationViewBackground.setVisibility(View.GONE);
                });
            }
        } catch (Exception e) {
            if (animationViewBackground != null) {
                animationViewBackground.setVisibility(View.GONE);
            }
        }
    }
}
