package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * 
 */
public class SekerLiveApiClient {

    private static final String BASE_URL = "https://app.seker.live/fm1/";

    public static String sendMessage(String id, String text) throws SekerLiveApiException {
        checkNotEmpty(id, "id");
        checkNotEmpty(text, "text");
        JSONObject req = new JSONObject();
        req.put("id", id);
        req.put("text", text);
        JSONObject resp = postJson("send-message", req);
        checkError(resp);
        if (resp.has("message") && !resp.isNull("message"))
            return resp.getString("message");
        return resp.toString();
    }


    public static void clearHistory(String id) throws SekerLiveApiException {
        checkNotEmpty(id, "id");
        JSONObject req = new JSONObject();
        req.put("id", id);
        JSONObject resp = postJson("clear-history", req);
        checkError(resp);
    }


    public static int checkBalance(String id) throws SekerLiveApiException {
        checkNotEmpty(id, "id");
        JSONObject req = new JSONObject();
        req.put("id", id);
        JSONObject resp = postJson("check-balance", req);
        checkError(resp);
        if (resp.has("balance") && !resp.isNull("balance"))
            return resp.getInt("balance");
        return -1;
    }

    private static JSONObject postJson(String path, JSONObject body) throws SekerLiveApiException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            String json = body.toString();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject resp;
            try {
                resp = new JSONObject(sb.toString());
            } catch (JSONException e) {
                throw new SekerLiveApiException(3005, "Invalid JSON from server: " + sb.toString());
            }
            if (code < 200 || code >= 300) {
                String msg = resp != null && resp.has("errorMessage") ? resp.getString("errorMessage") : sb.toString();
                int errCode = resp != null && resp.has("errorCode") ? resp.getInt("errorCode") : code;
                throw new SekerLiveApiException(errCode, msg);
            }
            return resp;
        } catch (IOException e) {
            throw new SekerLiveApiException(3005, "Network error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void checkError(JSONObject resp) throws SekerLiveApiException {
        if (resp == null) throw new SekerLiveApiException(3005, "No response from API");
        if (resp.has("errorCode")) {
            int code = resp.optInt("errorCode");
            String msg = resp.optString("errorMessage", "");
            throw new SekerLiveApiException(code, msg);
        }
    }

    private static void checkNotEmpty(String value, String field) throws SekerLiveApiException {
        if (value == null || value.trim().isEmpty())
            throw new SekerLiveApiException(3000, field + " is required");
    }


    public static class SekerLiveApiException extends Exception {
        public final int code;
        public SekerLiveApiException(int code, String msg) {
            super("SekerLive API Error " + code + ": " + msg);
            this.code = code;
        }
    }
}
