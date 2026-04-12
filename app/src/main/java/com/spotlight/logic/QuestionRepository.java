package com.spotlight.logic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.spotlight.model.Question;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuestionRepository {
    private List<Question> allQuestions = new ArrayList<>();
    private List<Question> currentPool = new ArrayList<>();
    private final Context context;

    public interface OnQuestionsLoadedListener {
        void onLoaded();
    }

    public QuestionRepository(Context context) {
        this.context = context;
    }

    public void loadQuestionsAsync(OnQuestionsLoadedListener listener) {
        if (!allQuestions.isEmpty()) {
            listener.onLoaded();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            parseJsonFile();

            new Handler(Looper.getMainLooper()).post(() -> {
                currentPool = new ArrayList<>(allQuestions);
                Collections.shuffle(currentPool);
                listener.onLoaded();
            });
        });
    }

    private void parseJsonFile() {
        try (InputStream is = context.getAssets().open("questions.json")) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            String json = new String(buffer, StandardCharsets.UTF_8);

            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                allQuestions.add(new Question(obj.getString("text"), obj.getString("category")));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            allQuestions.add(new Question("What is my favorite childhood memory?", "Personal"));
        }
    }
    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("All");
        for (Question q : allQuestions) {
            if (!categories.contains(q.getCategory())) {
                categories.add(q.getCategory());
            }
        }
        return categories;
    }

    public void filterByCategory(String category) {
        if ("All".equals(category)) {
            currentPool = new ArrayList<>(allQuestions);
        } else {
            currentPool = new ArrayList<>();
            for (Question q : allQuestions) {
                if (q.getCategory().equals(category)) {
                    currentPool.add(q);
                }
            }
        }
        Collections.shuffle(currentPool);
    }

    public Question getRandomQuestion() {
        if (currentPool.isEmpty()) {
            if (allQuestions.isEmpty()) {
                return new Question("Fallback: If you could have any superpower, what would it be?", "General");
            }
            currentPool = new ArrayList<>(allQuestions);
            Collections.shuffle(currentPool);
        }
        return currentPool.remove(0);
    }
}
