package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.google.gson.*;
import java.util.*;

public class Survey {
    private final int id;
    private final List<Question> questions;
    private final Map<Long, Map<String, String>> userVotes = new HashMap<>();
    private boolean active = true;
    private final long createdAt;
    private final String language;

    public Survey(int id, List<Question> questions, String language) {
        this.id = id;
        this.questions = questions;
        this.language = language;
        this.createdAt = System.currentTimeMillis();
    }

    public synchronized boolean vote(Long userId, String question, String option) {
        if (!active) return false;
        String qKey = sanitizeKey(question);
        Question q = getQuestionByKey(qKey);
        if (q == null) return false;

        // Find the original option that corresponds to the sanitized callback value
        String matchedOriginal = null;
        for (String origOpt : q.getOptions()) {
            if (sanitizeOption(origOpt).equals(option)) {
                matchedOriginal = origOpt;
                break;
            }
        }
        if (matchedOriginal == null) return false; // option not found

        Map<String, String> votes = userVotes.computeIfAbsent(userId, k -> new HashMap<>());
        if (votes.containsKey(qKey)) return false; // already voted
        votes.put(qKey, matchedOriginal);
        return true;
    }

    public synchronized boolean hasVoted(Long userId, String question) {
        String qKey = sanitizeKey(question);
        return userVotes.containsKey(userId) && userVotes.get(userId).containsKey(qKey);
    }

    public synchronized String getUserVote(Long userId, String question) {
        String qKey = sanitizeKey(question);
        return userVotes.containsKey(userId) ? userVotes.get(userId).get(qKey) : null;
    }

    public void send(Long userId, TelegramLongPollingBot bot) {
        for (Question q : questions) {
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (String opt : q.getOptions()) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(opt);
                btn.setCallbackData(sanitizeCallbackData(opt, id, q.getQuestion()));
                row.add(btn);
                rows.add(row);
            }
            List<InlineKeyboardButton> resultsRow = new ArrayList<>();
            InlineKeyboardButton resultsBtn = new InlineKeyboardButton();
            resultsBtn.setText("הצג תוצאות");
            resultsBtn.setCallbackData("showresults_" + id + "_" + sanitizeKey(q.getQuestion()));
            resultsRow.add(resultsBtn);
            rows.add(resultsRow);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(rows);
            SendMessage msg = new SendMessage(String.valueOf(userId), q.getQuestion());
            msg.setReplyMarkup(markup);
            try {
                bot.execute(msg);
            } catch (TelegramApiException e) {
                System.err.println("Failed to send survey: " + e.getMessage());
            }
        }
    }

    private String sanitizeCallbackData(String option, int id, String q) {
        // Allow only letters, digits, and underscore to keep UTF-8 length predictable; remove spaces.
        String clean = option.replaceAll("[^\\p{L}\\p{N}_]", "");
        if (clean.isEmpty()) clean = "opt";

        String qKey = sanitizeKey(q);
        String idAndKey = "_" + id + "_" + qKey;

        // Ensure total UTF-8 byte length ≤ 64 (Telegram limit)
        int idKeyBytes = idAndKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int available = 64 - idKeyBytes;
        if (available < 1) available = 1;

        byte[] cleanBytes = clean.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (cleanBytes.length > available) {
            // Trim bytes safely (may cut multibyte char) – iterate chars
            StringBuilder sb = new StringBuilder();
            int bytesCount = 0;
            for (int i = 0; i < clean.length(); i++) {
                char ch = clean.charAt(i);
                byte[] b = String.valueOf(ch).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (bytesCount + b.length > available) break;
                sb.append(ch);
                bytesCount += b.length;
            }
            clean = sb.toString();
        }

        String data = clean + idAndKey;
        // Final safeguard
        if (data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64) {
            data = (clean.length() > 1 ? clean.substring(0, 1) : "x") + idAndKey;
        }
        return data;
    }

    private String sanitizeKey(String q) {
        return Integer.toHexString(q.hashCode());
    }

    /**
     * Sanitize an option string using the same rules applied when building callback data.
     * This ensures we can reliably match the callback value back to the original option
     * even if it contained spaces or other special characters that were stripped.
     */
    private static String sanitizeOption(String opt) {
        String clean = opt.replaceAll("[^\\p{L}\\p{N}_]", "");
        if (clean.isEmpty()) clean = "opt";
        return clean;
    }

    public synchronized String calculate() {
        StringBuilder sb = new StringBuilder();
        for (Question q : questions) {
            String qKey = sanitizeKey(q.getQuestion());
            sb.append("Results for: ").append(q.getQuestion()).append("\n");
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String opt : q.getOptions()) counts.put(opt, 0);
            for (Map<String, String> votes : userVotes.values()) {
                String opt = votes.get(qKey);
                if (opt != null && counts.containsKey(opt)) {
                    counts.put(opt, counts.get(opt) + 1);
                }
            }
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                double percentage = total == 0 ? 0 : (entry.getValue() * 100.0) / total;
                sb.append(entry.getKey()).append(": ")
                  .append(String.format("%.1f", percentage)).append("% (").append(entry.getValue()).append(")\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public synchronized String calculateForQuestion(String qKey, boolean hebrew) {
        for (Question q : questions) {
            if (!sanitizeKey(q.getQuestion()).equals(qKey)) continue;
            StringBuilder sb = new StringBuilder();
            sb.append(hebrew ? "תוצאות לשאלה: " : "Results for: ")
              .append(q.getQuestion()).append("\n");
            Map<String,Integer> counts = new LinkedHashMap<>();
            for (String opt : q.getOptions()) counts.put(opt, 0);
            for (Map<String,String> votes : userVotes.values()) {
                String opt = votes.get(qKey);
                if (opt!=null && counts.containsKey(opt)) {
                    counts.put(opt, counts.get(opt)+1);
                }
            }
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            for (Map.Entry<String,Integer> entry : counts.entrySet()) {
                double perc = total==0 ? 0 : entry.getValue()*100.0/total;
                sb.append(entry.getKey()).append(hebrew?": ":": ")
                  .append(String.format("%.1f",perc)).append("% (")
                  .append(entry.getValue()).append(")\n");
            }
            return sb.toString();
        }
        return hebrew?"שגיאה: שאלה לא נמצאה." : "Error: question not found.";
    }

    public void close() { this.active = false; }
    public boolean isActive() { return active; }
    public int getId() { return id; }
    public int getNumVoters() { return userVotes.size(); }

    public List<Question> getQuestions() {
        return questions;
    }

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        JsonArray qArr = new JsonArray();
        for (Question q : questions) {
            JsonObject qObj = new JsonObject();
            qObj.addProperty("question", q.getQuestion());
            JsonArray opts = new JsonArray();
            for (String opt : q.getOptions()) opts.add(opt);
            qObj.add("options", opts);
            qArr.add(qObj);
        }
        obj.add("questions", qArr);
        obj.addProperty("language", language);
        return obj.toString();
    }

    public static Survey fromJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            int id = obj.get("id").getAsInt();
            String lang = obj.has("language") ? obj.get("language").getAsString() : "he";
            JsonArray qArr = obj.getAsJsonArray("questions");
            List<Question> qs = new ArrayList<>();
            for (JsonElement el : qArr) {
                JsonObject qObj = el.getAsJsonObject();
                String q = qObj.get("question").getAsString();
                List<String> opts = new ArrayList<>();
                for (JsonElement optEl : qObj.getAsJsonArray("options")) opts.add(optEl.getAsString());
                qs.add(new Question(q, opts));
            }
            return new Survey(id, qs, lang);
        } catch (Exception e) {
            return null;
        }
    }

    public Question getQuestionByKey(String qKey) {
        for (Question q : questions) {
            if (sanitizeKey(q.getQuestion()).equals(qKey)) return q;
        }
        return null;
    }

    public static class Question {
        private final String question;
        private final List<String> options;
        public Question(String question, List<String> options) {
            this.question = question;
            this.options = options;
        }
        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
    }
}
