package org.example;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class GPTSurveyHelper {
    private static final String ENDPOINT = "https://app.seker.live/fm1/send-message";
    private static final String SENDER_ID = "215916354";


    public static GPTSurveyResult generateSurvey(String userId, String topic) {
        try {
            System.out.println("[GPTSurveyHelper] userId: " + userId);
            System.out.println("[GPTSurveyHelper] topic: " + topic);

            String prompt = "צור שאלה לסקר בנושא: '" + topic + "' והצע 2-4 תשובות אפשריות. הפורמט: שאלה בשורה הראשונה, ואז כל תשובה בשורה חדשה וממוספרת.\n" +
                    "דוגמה:\nמה דעתך על קפה?\n1. אני אוהב\n2. אני לא אוהב\n3. לא אכפת לי\n" +
                    "כללים: כתוב בעברית בלבד, אל תוסיף הסברים, אל תציין שמדובר בסקר, רק את השאלה והאפשרויות,ותיצור שאלות יותר יצירתיות.";
            String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String url = ENDPOINT + "?id=" + SENDER_ID + "&text=" + encodedPrompt;
            String bodyString = httpGet(url);
            JSONObject json;
            try {
                json = new JSONObject(bodyString);
            } catch (Exception ex) {
                return errorResult("שגיאה בפענוח JSON מה-API: " + bodyString);
            }
            String raw = json.optString("extra", "");
            if (raw.isEmpty()) {
                return errorResult("ה-GPT לא החזיר תשובה.");
            }
            List<Survey.Question> questions = parseQuestions(raw, "he");
            if (questions.size() < 1) {
                return errorResult("תשובה לא תקינה מה-API: " + raw);
            }
            Survey.Question question = questions.get(0);
            String q = question.getQuestion();
            List<String> options = question.getOptions();
            if (q == null || q.isEmpty() || options == null || options.size() < 2) {
                return errorResult("ה-GPT לא החזיר שאלה או מספיק תשובות.");
            }
            return new GPTSurveyResult(q, options);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            e.printStackTrace();
            System.out.println("[GPTSurveyHelper] Exception: " + msg);
            return errorResult(msg);
        }
    }


    private static GPTSurveyResult errorResult(String msg) {
        List<String> err = new ArrayList<>();
        err.add(msg);
        return new GPTSurveyResult("שגיאה", err);
    }


    private static List<Survey.Question> parseQuestions(String content, String language) {
        List<Survey.Question> questions = new ArrayList<>();
        String[] blocks = content.split("\n\n|\n\r\n"); // חיתוך לבלוקים
        for (String block : blocks) {
            String[] lines = block.trim().split("\r?\n");
            if (lines.length < 2) continue;
            String q = lines[0].trim();
            List<String> opts = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String opt = lines[i].replaceAll("^\\s*[0-9]+[.\\)]?\\s*", "").trim();
                if (!opt.isEmpty()) opts.add(opt);
            }
            if (opts.size() >= 2 && opts.size() <= 4 && isValidLanguage(q, language) && opts.stream().allMatch(o -> isValidLanguage(o, language))) {
                questions.add(new Survey.Question(q, opts));
            }
        }
        return questions;
    }
    private static boolean isValidLanguage(String s, String lang) {
        if (lang.equals("he")) return s.matches(".*[\u0590-\u05FF].*");
        if (lang.equals("en")) return s.matches(".*[A-Za-z].*");
        return true;
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + sb);
                return sb.toString();
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
