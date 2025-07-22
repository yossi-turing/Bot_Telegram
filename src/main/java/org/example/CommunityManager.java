package org.example;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class CommunityManager {
    private static final String MEMBERS_FILE = "members.txt";
    private static final String SURVEY_HISTORY_FILE = "survey_history.json";
    private final Set<Long> members = Collections.synchronizedSet(new HashSet<>());
    private final List<Survey> surveyHistory = Collections.synchronizedList(new ArrayList<>());
    private final Map<Long, Integer> votesPerUser = Collections.synchronizedMap(new HashMap<>());
    private final Map<Long, Integer> surveysCreated = Collections.synchronizedMap(new HashMap<>());
    private volatile Survey activeSurvey = null;
    private final AtomicInteger surveyIdCounter = new AtomicInteger(1);

    public CommunityManager() {
        loadMembers();
        loadSurveyHistory();
    }

    public boolean addMember(Long userId) {
        boolean added = members.add(userId);
        if (added) saveMembers();
        return added;
    }

    public boolean removeMember(Long userId) {
        boolean removed = members.remove(userId);
        if (removed) saveMembers();
        return removed;
    }

    public Set<Long> getMembers() {
        return Collections.unmodifiableSet(new HashSet<>(members));
    }

    public int getMemberCount() {
        return members.size();
    }

    public synchronized boolean startSurvey(Survey survey) {
        if (activeSurvey != null && activeSurvey.isActive()) return false;
        if (getMemberCount() < 1) return false;
        activeSurvey = survey;
        return true;
    }

    public Survey getActiveSurvey() {
        return activeSurvey;
    }


    public boolean hasActiveSurvey() {
        return activeSurvey != null && activeSurvey.isActive();
    }

    public int generateSurveyId() {
        return surveyIdCounter.getAndIncrement();
    }

    public synchronized void endActiveSurvey() {
        endSurvey();
    }

    public void endSurvey() {
        if (activeSurvey != null) {
            activeSurvey.close();
            addSurveyToHistory(activeSurvey);
            activeSurvey = null;
        }
    }

    public void addSurveyToHistory(Survey s) {
        surveyHistory.add(s);
        saveSurveyHistory();
    }

    public List<Survey> getSurveyHistory() {
        return new ArrayList<>(surveyHistory);
    }

    public void incrementUserVote(Long userId) {
        votesPerUser.put(userId, votesPerUser.getOrDefault(userId, 0) + 1);
    }

    public void incrementUserCreated(Long userId) {
        surveysCreated.put(userId, surveysCreated.getOrDefault(userId, 0) + 1);
    }

    public int getUserVotes(Long userId) {
        return votesPerUser.getOrDefault(userId, 0);
    }

    public int getUserSurveys(Long userId) {
        return surveysCreated.getOrDefault(userId, 0);
    }

    private void saveMembers() {
        try (PrintWriter out = new PrintWriter(new FileWriter(MEMBERS_FILE))) {
            for (Long id : members) out.println(id);
        } catch (IOException e) {
            System.err.println("[CommunityManager] Failed to save members: " + e.getMessage());
        }
    }

    private void loadMembers() {
        File file = new File(MEMBERS_FILE);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    members.add(Long.parseLong(line.trim()));
                } catch (NumberFormatException ignore) {}
            }
        } catch (IOException e) {
            System.err.println("[CommunityManager] Failed to load members: " + e.getMessage());
        }
    }

    public void saveSurveyHistory() {
        try (PrintWriter out = new PrintWriter(new FileWriter(SURVEY_HISTORY_FILE, false))) {
            for (Survey s : surveyHistory) {
                out.println(s.toJson());
            }
        } catch (Exception e) {
            System.err.println("[CommunityManager] Failed to save survey history: " + e.getMessage());
        }
    }

    public void loadSurveyHistory() {
        File file = new File(SURVEY_HISTORY_FILE);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                Survey s = Survey.fromJson(line);
                if (s != null) surveyHistory.add(s);
            }
        } catch (Exception e) {
            System.err.println("[CommunityManager] Failed to load survey history: " + e.getMessage());
        }
    }
}
