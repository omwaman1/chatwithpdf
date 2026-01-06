package com.softweb.chatwithpdf;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles AI inference via PHP backend
 * The PHP script handles the actual API calls to Gemini
 */
public class ChatApiClient {
    
    private static final String TAG = "ChatApiClient";
    
    // Railway deployment URL
    private static final String API_ENDPOINT = "https://brave-hope-production-0204.up.railway.app/chat";
    
    private Context context;
    private ExecutorService executor;
    private Handler mainHandler;
    
    public interface ChatCallback {
        void onResult(String response);
        void onError(String error);
        void onProgress(String status);
    }
    
    public ChatApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Check if ready (always ready since we use server)
     */
    public boolean isReady() {
        return true;
    }
    
    /**
     * Send question to PHP backend
     */
    public void askQuestion(String pdfContext, String question, ChatCallback callback) {
        executor.execute(() -> {
            try {
                mainHandler.post(() -> callback.onProgress("Sending to server..."));
                
                // Limit context to avoid large requests
                String limitedContext = pdfContext.length() > 8000 
                    ? pdfContext.substring(0, 8000) + "..." 
                    : pdfContext;
                
                String response = callBackendApi(limitedContext, question);
                
                mainHandler.post(() -> callback.onResult(response));
                
            } catch (Exception e) {
                Log.e(TAG, "Error calling backend: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }
    
    private String callBackendApi(String context, String question) throws Exception {
        URL url = new URL(API_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(120000);
        
        Log.d(TAG, "Sending request to: " + API_ENDPOINT);
        
        // Build request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("context", context);
        requestBody.put("question", question);
        
        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        
        StringBuilder response = new StringBuilder();
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            JSONObject jsonResponse = new JSONObject(response.toString());
            
            if (jsonResponse.optBoolean("success", false)) {
                return jsonResponse.getString("answer");
            } else {
                throw new Exception(jsonResponse.optString("error", "Unknown error"));
            }
            
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            throw new Exception("Server error (" + responseCode + "): " + response.toString());
        }
    }
    
    /**
     * Close and release resources
     */
    public void close() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
