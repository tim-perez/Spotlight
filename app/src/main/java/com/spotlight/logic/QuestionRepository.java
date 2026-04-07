package com.spotlight.logic;

import android.content.Context;
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

public class QuestionRepository {
    private List<Question> allQuestions;
    private List<Question> currentPool;
    private Context context;

    public QuestionRepository(Context context) {
        this.context = context;
        allQuestions = new ArrayList<>();
        loadQuestions();
        currentPool = new ArrayList<>(allQuestions);
        Collections.shuffle(currentPool);
    }

    private void loadQuestions() {
        String json = null;
        try {
            InputStream is = context.getAssets().open("questions.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);

            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                allQuestions.add(new Question(obj.getString("text"), obj.getString("category")));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            // Fallback if file is missing or corrupt
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
        if (category.equals("All")) {
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
                // Return a hardcoded fallback if everything else fails
                return new Question("Fallback: If you could have any superpower, what would it be?", "General");
            }
            currentPool = new ArrayList<>(allQuestions);
            Collections.shuffle(currentPool);
        }
        return currentPool.remove(0);
    }
}
