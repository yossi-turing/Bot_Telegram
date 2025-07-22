package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;


public class SurveyCreatorFrame extends JFrame {

    private JTextField questionField;

    private DefaultListModel<String> optionsModel;

    private JList<String> optionsList;

    private JTextField newOptionField;

    private JButton addOptionButton;

    private JButton createSurveyButton;

    private JButton gptButton;

    private JLabel statusLabel;

    private TelegramBot telegramBot;

    private JButton removeMemberButton;

    private JList<String> membersList;

    private DefaultListModel<String> membersModel;

    private CommunityManager communityManager;

    private JLabel membersCountLabel;

    public SurveyCreatorFrame(TelegramBot telegramBot) {
        if (telegramBot == null || telegramBot.getCommunityManager() == null) {
            JOptionPane.showMessageDialog(this, "שגיאה: הבוט או מנהל הקהילה לא מאותחלים!", "שגיאה קריטית", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        this.telegramBot = telegramBot;
        this.communityManager = telegramBot.getCommunityManager();
        setTitle("יצירת סקר חדש");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));
        getContentPane().setBackground(new Color(245, 248, 255));


        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBackground(new Color(245, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;


        JLabel titleLabel = new JLabel("\uD83D\uDCCA יצירת סקר חדש");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 22));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        inputPanel.add(titleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        inputPanel.add(new JLabel("שאלה:"), gbc);
        gbc.gridx = 1;
        questionField = new JTextField();
        inputPanel.add(questionField, gbc);

        gbc.gridx = 0; gbc.gridy++;
        inputPanel.add(new JLabel("תשובות אפשריות (2-4):"), gbc);
        gbc.gridx = 1;
        optionsModel = new DefaultListModel<>();
        optionsList = new JList<>(optionsModel);
        optionsList.setFont(new Font("Arial", Font.PLAIN, 14));
        optionsList.setVisibleRowCount(4);
        optionsList.setFixedCellHeight(28);
        JScrollPane optionsScroll = new JScrollPane(optionsList);
        optionsScroll.setPreferredSize(new Dimension(200, 90));
        inputPanel.add(optionsScroll, gbc);

        gbc.gridx = 0; gbc.gridy++;
        gbc.gridwidth = 2;
        JPanel optionAddPanel = new JPanel(new BorderLayout(5, 0));
        optionAddPanel.setBackground(new Color(245, 248, 255));
        newOptionField = new JTextField();
        addOptionButton = new JButton("הוסף תשובה");
        addOptionButton.setBackground(new Color(120, 180, 255));
        addOptionButton.setForeground(Color.WHITE);
        addOptionButton.setFocusPainted(false);
        optionAddPanel.add(newOptionField, BorderLayout.CENTER);
        optionAddPanel.add(addOptionButton, BorderLayout.EAST);
        inputPanel.add(optionAddPanel, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy++;
        createSurveyButton = new JButton("\uD83D\uDCE8 שלח סקר לבוט");
        createSurveyButton.setBackground(new Color(46, 204, 113));
        createSurveyButton.setForeground(Color.WHITE);
        createSurveyButton.setFont(new Font("Arial", Font.BOLD, 16));
        createSurveyButton.setFocusPainted(false);
        inputPanel.add(createSurveyButton, gbc);

        gbc.gridx = 1;
        gptButton = new JButton("\uD83E\uDD16 צור סקר אוטומטי מ־GPT");
        gptButton.setBackground(new Color(52, 152, 219));
        gptButton.setForeground(Color.WHITE);
        gptButton.setFont(new Font("Arial", Font.BOLD, 14));
        gptButton.setFocusPainted(false);
        inputPanel.add(gptButton, gbc);

        gbc.gridx = 0; gbc.gridy++;
        gbc.gridwidth = 2;
        JLabel membersLabel = new JLabel("\uD83D\uDC65 חברי צוות רשומים:");
        membersLabel.setFont(new Font("Arial", Font.BOLD, 16));
        inputPanel.add(membersLabel, gbc);

        gbc.gridy++;
        gbc.gridwidth = 2;
        membersCountLabel = new JLabel();
        membersCountLabel.setFont(new Font("Arial", Font.BOLD, 16));
        membersCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
        updateMembersCountLabel();
        inputPanel.add(membersCountLabel, gbc);

        gbc.gridy++;
        membersModel = new DefaultListModel<>();
        for (Long id : communityManager.getMembers()) membersModel.addElement(String.valueOf(id));
        membersList = new JList<>(membersModel);
        membersList.setFont(new Font("Arial", Font.PLAIN, 14));
        membersList.setVisibleRowCount(3);
        JScrollPane membersScroll = new JScrollPane(membersList);
        membersScroll.setPreferredSize(new Dimension(200, 60));
        inputPanel.add(membersScroll, gbc);

        gbc.gridy++;
        removeMemberButton = new JButton("\uD83D\uDDD1️ הסר חבר נבחר");
        removeMemberButton.setBackground(new Color(231, 76, 60));
        removeMemberButton.setForeground(Color.WHITE);
        removeMemberButton.setFont(new Font("Arial", Font.BOLD, 14));
        removeMemberButton.setFocusPainted(false);
        inputPanel.add(removeMemberButton, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy++;
        gbc.gridwidth = 2;
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(41, 128, 185));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        inputPanel.add(statusLabel, gbc);

        add(inputPanel, BorderLayout.CENTER);


        addOptionButton.addActionListener(e -> {
            String opt = newOptionField.getText().trim();
            if (opt.isEmpty()) {
                statusLabel.setText("יש להזין תשובה.");
                return;
            }
            if (optionsModel.contains(opt)) {
                statusLabel.setText("תשובה זו כבר קיימת.");
                return;
            }
            if (optionsModel.size() >= 4) {
                statusLabel.setText("מותר עד 4 תשובות בלבד.");
                return;
            }
            optionsModel.addElement(opt);
            newOptionField.setText("");
            statusLabel.setText("");
        });


        createSurveyButton.addActionListener(e -> {
            String question = questionField.getText().trim();
            List<String> options = new ArrayList<>();
            for (int i = 0; i < optionsModel.size(); i++) {
                options.add(optionsModel.get(i));
            }
            if (question.isEmpty()) {
                statusLabel.setText("יש להזין שאלה לסקר.");
                return;
            }
            if (options.size() < 2) {
                statusLabel.setText("יש להזין לפחות 2 תשובות.");
                return;
            }
            if (options.size() > 4) {
                statusLabel.setText("מותר עד 4 תשובות בלבד.");
                return;
            }
            if (communityManager.getMemberCount() == 0) {
                statusLabel.setText("אין חברים במערכת. לא ניתן לשלוח סקר.");
                return;
            }
            try {
                boolean sent = telegramBot.createAndSendSurvey(question, options);
                if (sent) {
                    statusLabel.setText("הסקר נשלח לבוט!");
                    questionField.setText("");
                    optionsModel.clear();
                } else {
                    statusLabel.setText("שליחת הסקר נכשלה (סקר פעיל או חוסר חברים).");
                }
            } catch (Exception ex) {
                statusLabel.setText("שגיאה בשליחת סקר: " + ex.getMessage());
            }
        });

        gptButton.addActionListener(e -> {
            String topic = JOptionPane.showInputDialog(this, "הזן נושא כללי לסקר:", "נושא לסקר GPT", JOptionPane.PLAIN_MESSAGE);
            if (topic == null || topic.trim().isEmpty()) return;
            String userId = "215916354";
            try {
                GPTSurveyResult result = GPTSurveyHelper.generateSurvey(userId, topic);
                if (result.question == null || result.options == null || result.options.size() < 2) {
                    statusLabel.setText("שגיאה ביצירת סקר אוטומטי. נסה נושא אחר.");
                    return;
                }
                questionField.setText(result.question);
                optionsModel.clear();
                for (String opt : result.options) optionsModel.addElement(opt);
                if (communityManager.getMemberCount() == 0) {
                    statusLabel.setText("אין חברים במערכת. לא ניתן לשלוח סקר.");
                    return;
                }
                boolean sent = telegramBot.createAndSendSurvey(result.question, result.options);
                if (sent) {
                    statusLabel.setText("הסקר מגנרטור GPT נשלח לבוט!");
                } else {
                    statusLabel.setText("שליחת הסקר נכשלה (סקר פעיל או חוסר חברים).");
                }
            } catch (Exception ex) {
                statusLabel.setText("שגיאה ביצירת סקר אוטומטי: " + ex.getMessage());
            }
        });

        removeMemberButton.addActionListener(e -> {
            String selected = membersList.getSelectedValue();
            if (selected == null) {
                statusLabel.setText("בחר חבר להסרה מהרשימה.");
                return;
            }
            try {
                Long id = Long.parseLong(selected);
                boolean removed = communityManager.removeMember(id);
                if (removed) {
                    membersModel.removeElement(selected);
                    statusLabel.setText("החבר הוסר בהצלחה.");
                    updateMembersCountLabel();
                } else {
                    statusLabel.setText("החבר לא נמצא.");
                }
            } catch (Exception ex) {
                statusLabel.setText("שגיאה בהסרה: " + ex.getMessage());
            }
        });
    }


    private void updateMembersCountLabel() {
        int count = communityManager.getMemberCount();
        membersCountLabel.setText("\uD83D\uDC65 סה" + (count == 1 ? ": חבר אחד" : ": " + count + " חברים"));
    }


    public static void main(String[] args) {
        TelegramBot bot = TelegramBot.getInstance();
        SwingUtilities.invokeLater(() -> new SurveyCreatorFrame(bot).setVisible(true));
    }
}
