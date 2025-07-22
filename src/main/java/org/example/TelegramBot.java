package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.InputStream;
import java.io.IOException;

public class TelegramBot extends TelegramLongPollingBot {
    private final CommunityManager communityManager = new CommunityManager();
    private Timer surveyTimer = null;
    private final Map<Long, String> userState = new HashMap<>();
    private static final AtomicInteger surveyIdCounter = new AtomicInteger((int)(System.currentTimeMillis() / 1000));
    private final Map<Long, String> userLanguages = new HashMap<>();

    private static final String BOT_TOKEN = loadToken();

    private static String loadToken() {
        String env = System.getenv("BOT_TOKEN");
        if (env != null && !env.isBlank() && !"TOKEN_PLACEHOLDER".equals(env)) {
            return env.trim();
        }
        try (InputStream in = TelegramBot.class.getClassLoader().getResourceAsStream("bot.properties")) {
            if (in != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                String token = p.getProperty("BOT_TOKEN");
                if (token != null && !token.isBlank()) {
                    return token.trim();
                }
            }
        } catch (IOException ignored) { }
        return "TOKEN_PLACEHOLDER";
    }

    private static volatile TelegramBot INSTANCE;

    public static synchronized TelegramBot getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TelegramBot();
        }
        return INSTANCE;
    }

    private TelegramBot() {
        try {
            DeleteWebhook del = new DeleteWebhook();
            del.setDropPendingUpdates(true);
            execute(del);
            System.out.println("[TelegramBot] Existing webhook (if any) deleted.");
        } catch (Exception ex) {
            System.err.println("[TelegramBot] Couldn't delete webhook (may be already absent): " + ex.getMessage());
        }
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            if ("TOKEN_PLACEHOLDER".equals(BOT_TOKEN)) {
                System.err.println("[TelegramBot] WARNING: BOT_TOKEN environment variable not set. Using placeholder token – the bot will NOT work!");
            }
        } catch (Exception e) {
            System.err.println("[TelegramBot] Failed to register bot: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() { return "Yossi2025Bot"; }
    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        Long userId = null;
        String state = "START";
        Survey survey = communityManager.getActiveSurvey();
        if (update.hasCallbackQuery()) {
            userId = update.getCallbackQuery().getFrom().getId();
            state = userState.getOrDefault(userId, "START");
            String data = update.getCallbackQuery().getData();
            if ("WAITING_FOR_SURVEY_TYPE".equals(state)) {
                if ("SURVEY_MANUAL".equals(data)) {
                    sendMessage(userId, "שלח את השאלה של הסקר (טקסט בלבד):", "Send the survey question (text only):");
                    userState.put(userId, "WAITING_FOR_QUESTION");
                } else if ("SURVEY_GPT".equals(data)) {
                    sendMessage(userId, "שלח נושא כללי לסקר (מילה/משפט):", "Send a general topic for the survey (word/sentence):");
                    userState.put(userId, "WAITING_FOR_GPT_TOPIC");
                }
                return;
            } else {

                String[] parts = data.split("_");
                if (parts.length >= 3) {
                    String qKey = parts[parts.length - 1];
                    String surveyIdStr = parts[parts.length - 2];
                    String option = String.join("_", java.util.Arrays.copyOfRange(parts, 0, parts.length - 2));
                    int surveyId;
                    try {
                        surveyId = Integer.parseInt(surveyIdStr);
                    } catch (NumberFormatException ex) {
                        sendMessage(userId, "שגיאת מזהה סקר.", "Invalid survey id.");
                        return;
                    }

                    System.out.println("[DEBUG] Callback from user="+userId+" option="+option+" surveyIdParsed="+surveyId+" qKey="+qKey+" activeSurvey="+(survey==null?"null":survey.getId())+" active="+(survey!=null && survey.isActive()) );
                    if ("showresults".equalsIgnoreCase(option)) {
                        boolean heb = userLanguages.getOrDefault(userId, "he").equals("he");
                        String results = survey.calculateForQuestion(qKey, heb);
                        sendMessage(userId, results, results);
                        return;
                    }

                    if (survey == null || survey.getId() != surveyId || !survey.isActive()) {
                        sendMessage(userId, "אין סקר פעיל או מזהה לא תואם.", "No active survey or mismatched ID.");
                        return;
                    }
                    Survey.Question q = survey.getQuestionByKey(qKey);
                    if (q == null) {
                        sendMessage(userId, "שגיאת סנכרון שאלה.", "Question sync error.");
                        return;
                    }
                    if (survey.hasVoted(userId, q.getQuestion())) {
                        sendMessage(userId, "כבר הצבעת לשאלה זו.", "You already voted for this question.");
                        return;
                    }
                    boolean voted = survey.vote(userId, q.getQuestion(), option);
                    if (voted) {
                        sendMessage(userId, "הצבעתך נקלטה: " + option, "Your vote was recorded: " + option);
                        communityManager.incrementUserVote(userId);
                    } else {
                        sendMessage(userId, "שגיאה בהצבעה. נסה שוב.", "Voting error. Try again.");
                    }
                    checkSurveyCompletion();
                    return;
                }
                sendMessage(userId, "הצבעה לא תקינה.", "Invalid vote.");
                return;
            }
        }
        if (!update.hasMessage() || !update.getMessage().hasText()) return;
        userId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        state = userState.getOrDefault(userId, "START");

        if (text.equalsIgnoreCase("/sub") || text.equalsIgnoreCase("היי") || text.equalsIgnoreCase("start")) {
            boolean added = communityManager.addMember(userId);
            if (added) {
                sendMessageToAll("משתמש חדש הצטרף: " + userId + " | חברים כעת: " + communityManager.getMemberCount(),
                        "New user joined: " + userId + " | Members now: " + communityManager.getMemberCount());
            } else {
                sendMessage(userId, "אתה כבר חבר בקהילה.", "You are already a member of the community.");
            }
            userState.put(userId, "READY");
            return;
        }

        if (text.equalsIgnoreCase("/survey")) {
            if (communityManager.getMemberCount() < 3) {
                sendMessage(userId, "צריך לפחות 3 חברים בקהילה כדי להתחיל סקר.", "At least 3 members are required in the community to start a survey.");
                return;
            }
            Survey activeSurvey = communityManager.getActiveSurvey();
            if (activeSurvey != null && activeSurvey.isActive()) {
                sendMessage(userId, "כבר קיים סקר פעיל בקהילה.", "A survey is already active in the community.");
                return;
            }
            if (activeSurvey != null && !activeSurvey.isActive()) {
                communityManager.endSurvey();
            }
            String lang = userLanguages.getOrDefault(userId, "he");
            String menuText = lang.equals("en") ? "How would you like to create the survey?" : "איך תרצה ליצור את הסקר?";
            SendMessage menu = new SendMessage(String.valueOf(userId), menuText);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton manualBtn = new InlineKeyboardButton();
            manualBtn.setText("יצירה ידנית");
            manualBtn.setCallbackData("SURVEY_MANUAL");
            row.add(manualBtn);
            InlineKeyboardButton gptBtn = new InlineKeyboardButton();
            gptBtn.setText("יצירת סקר GPT");
            gptBtn.setCallbackData("SURVEY_GPT");
            row.add(gptBtn);
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(row);
            markup.setKeyboard(rows);
            menu.setReplyMarkup(markup);
            try { execute(menu); } catch (Exception ignore) {}
            userState.put(userId, "WAITING_FOR_SURVEY_TYPE");
            return;
        }

        if (state.equals("WAITING_FOR_QUESTION")) {
            userState.put(userId, "WAITING_FOR_OPTIONS");
            userState.put(-1L, text);
            sendMessage(userId, "הזן תשובות אפשריות מופרדות בפסיק (לפחות 2, עד 4):", "Enter possible answers separated by commas (at least 2, up to 4):");
            return;
        }

        if (state.equals("WAITING_FOR_OPTIONS")) {
            String question = userState.get(-1L);
            List<String> options = Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (options.size() < 2 || options.size() > 4) {
                sendMessage(userId, "יש להזין 2 עד 4 תשובות.", "You must enter 2 to 4 answers.");
                return;
            }
            createAndSendSurvey(question, options);
            userState.put(userId, "READY");
            return;
        }

        if (state.equals("WAITING_FOR_GPT_TOPIC")) {
            String topic = text.trim();
            if (topic.isEmpty()) {
                sendMessage(userId, "נא להזין נושא לסקר.", "Please enter a topic for the survey.");
                return;
            }
            String userIdStr = userId.toString();
            try {
                GPTSurveyResult result = GPTSurveyHelper.generateSurvey(userIdStr, topic);
                if (result.question == null || result.options == null || result.options.size() < 2) {
                    sendMessage(userId, "שגיאה ביצירת סקר אוטומטי. נסה נושא אחר.", "Error creating automatic survey. Try a different topic.");
                    userState.put(userId, "READY");
                    return;
                }
                createAndSendSurvey(result.question, result.options);
                sendMessage(userId, "הסקר מגנרטור GPT נשלח לקבוצה!", "The GPT survey generator was sent to the group!");
            } catch (Exception ex) {
                sendMessage(userId, "שגיאה ביצירת סקר אוטומטי: " + ex.getMessage(), "Error creating automatic survey: " + ex.getMessage());
            }
            userState.put(userId, "READY");
            return;
        }

        sendMessage(userId, "לא זוהתה פקודה. שלח /sub להצטרפות או /survey ליצירת סקר.", "Unknown command. Send /sub to join or /survey to create a survey.");
    }

    private void checkSurveyCompletion() {
        Survey survey = communityManager.getActiveSurvey();
        if (survey == null || !survey.isActive()) return;
        int members = communityManager.getMemberCount();
        boolean allVoted = true;
        for (Survey.Question q : survey.getQuestions()) {
            for (Long member : communityManager.getMembers()) {
                if (!survey.hasVoted(member, q.getQuestion())) {
                    allVoted = false;
                    break;
                }
            }
            if (!allVoted) break;
        }
        if (allVoted) {
            closeSurveyAndSendResults();
        }
    }

    private void startSurveyTimer() {
        if (surveyTimer != null) surveyTimer.cancel();
        surveyTimer = new Timer();
        surveyTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Survey survey = communityManager.getActiveSurvey();
                if (survey != null && survey.isActive()) {
                    closeSurveyAndSendResults();
                }
            }
        }, 5 * 60 * 1000);
    }

    private void closeSurveyAndSendResults() {
        Survey survey = communityManager.getActiveSurvey();
        if (survey == null) return;
        survey.close();
        String results = survey.calculate();
        sendMessageToAll("תוצאות הסקר:\n" + results, "Survey results:\n" + results);
        communityManager.endSurvey();
        if (surveyTimer != null) surveyTimer.cancel();
    }

    private void sendMessageToAll(String textHe, String textEn) {
        for (Long member : communityManager.getMembers()) {
            sendMessage(member, textHe, textEn);
        }
    }

    private void sendMessage(Long chatId, String textHe, String textEn) {
        try {
            execute(new SendMessage(String.valueOf(chatId), userLanguages.getOrDefault(chatId, "he").equals("en") ? textEn : textHe));
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException e) {
            // 403 – user blocked the bot
            if (e.getErrorCode() == 403) {
                System.err.println("[TelegramBot] User " + chatId + " blocked the bot. Removing from community.");
                communityManager.removeMember(chatId);
            } else {
                System.err.println("Failed to send message: " + e.getApiResponse());
            }
        } catch (TelegramApiException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    public synchronized boolean createAndSendSurvey(String question, List<String> options) {
        System.out.println("[DEBUG] createAndSendSurvey called");
        System.out.println("[DEBUG] Member count: " + communityManager.getMemberCount());
        Survey survey = communityManager.getActiveSurvey();
        System.out.println("[DEBUG] Existing active survey: " + (survey != null ? survey.isActive() : "none"));
        if (communityManager.getMemberCount() < 3) {
            sendMessageToAll("צריך לפחות 3 חברים בקהילה כדי לשלוח סקר.", "At least 3 members are required in the community to send a survey.");
            return false;
        }
        if (survey != null && survey.isActive()) {

            closeSurveyAndSendResults();
        }
        int surveyId = surveyIdCounter.incrementAndGet();
        List<Survey.Question> questions = new ArrayList<>();
        questions.add(new Survey.Question(question, options));
        Survey newSurvey = new Survey(surveyId, questions, "he");
        try {
            communityManager.startSurvey(newSurvey);
        } catch (Exception e) {
            System.err.println("[createAndSendSurvey] Failed to start survey: " + e.getMessage());
            sendMessageToAll("שגיאה בהפעלת הסקר. פנה למנהל.", "Error starting survey. Contact the administrator.");
            return false;
        }
        System.out.println("[DEBUG] Sending survey to members: " + communityManager.getMembers());
        for (Long member : communityManager.getMembers()) {
            try {
                newSurvey.send(member, this);
            } catch (Exception e) {
                System.err.println("[createAndSendSurvey] Failed to send survey to user " + member + ": " + e.getMessage());
                sendMessage(member, "שגיאה בשליחת הסקר. פנה למנהל.", "Error sending survey. Contact the administrator.");
            }
        }
        sendMessageToAll("הסקר נשלח! יש לכם 5 דקות לענות.", "The survey has been sent! You have 5 minutes to answer.");
        startSurveyTimer();
        return true;
    }

    public CommunityManager getCommunityManager() {
        return communityManager;
    }


    public static void main(String[] args) {
        TelegramBot.getInstance();
        System.out.println("TelegramBot started. Press Ctrl+C to exit.");
    }
}
