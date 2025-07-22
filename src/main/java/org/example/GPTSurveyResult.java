package org.example;
import java.util.List;


public class GPTSurveyResult {
    public final String question;
    public final List<String> options;

    public GPTSurveyResult(String question, List<String> options) {
        this.question = question;
        this.options = options;
    }
}
