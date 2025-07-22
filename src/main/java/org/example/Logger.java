package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final String LOG_FILE = "bot.log";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void info(String msg) {
        log("INFO", msg);
    }

    public static void error(String msg) {
        log("ERROR", msg);
    }

    public static void error(String msg, Throwable t) {
        log("ERROR", msg + "\n" + getStackTrace(t));
    }

    private static void log(String level, String msg) {
        String line = String.format("[%s] [%s] %s", sdf.format(new Date()), level, msg);
        System.out.println(line);
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.println(line);
        } catch (IOException e) {
            System.err.println("[Logger] Failed to write log: " + e.getMessage());
        }
    }

    private static String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("    at ").append(el).append("\n");
        }
        return sb.toString();
    }
}
