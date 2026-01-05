package com.softweb.chatwithpdf;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles Qwen2.5 LLM inference for offline Q&A
 */
public class QwenInference {
    
    private static final String TAG = "QwenInference";
    private static final String MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf";
    private static final String MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf";
    
    private LlmInference llmInference;
    private Context context;
    private boolean isInitialized = false;
    private ExecutorService executor;
    private Handler mainHandler;
    
    public interface InferenceCallback {
        void onResult(String response);
        void onError(String error);
        void onProgress(String status);
    }
    
    public interface ModelDownloadCallback {
        void onProgress(int percent);
        void onComplete(File modelFile);
        void onError(String error);
    }
    
    public QwenInference(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Check if model file exists
     */
    public boolean isModelDownloaded() {
        File modelFile = new File(context.getFilesDir(), MODEL_NAME);
        return modelFile.exists() && modelFile.length() > 0;
    }
    
    /**
     * Get model file path
     */
    public String getModelPath() {
        return new File(context.getFilesDir(), MODEL_NAME).getAbsolutePath();
    }
    
    /**
     * Download model file from HuggingFace
     */
    public void downloadModel(ModelDownloadCallback callback) {
        executor.execute(() -> {
            try {
                File modelFile = new File(context.getFilesDir(), MODEL_NAME);
                
                if (modelFile.exists()) {
                    mainHandler.post(() -> callback.onComplete(modelFile));
                    return;
                }
                
                mainHandler.post(() -> callback.onProgress(0));
                
                java.net.URL url = new java.net.URL(MODEL_URL);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                
                int fileLength = connection.getContentLength();
                
                try (java.io.InputStream input = connection.getInputStream();
                     java.io.FileOutputStream output = new java.io.FileOutputStream(modelFile)) {
                    
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int count;
                    int lastPercent = 0;
                    
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        output.write(buffer, 0, count);
                        
                        if (fileLength > 0) {
                            int percent = (int) (total * 100 / fileLength);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                mainHandler.post(() -> callback.onProgress(p));
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Model downloaded: " + modelFile.length() + " bytes");
                mainHandler.post(() -> callback.onComplete(modelFile));
                
            } catch (Exception e) {
                Log.e(TAG, "Error downloading model: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Download failed: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Initialize the LLM model
     */
    public void initialize(InferenceCallback callback) {
        if (isInitialized) {
            callback.onProgress("Model already loaded");
            return;
        }
        
        executor.execute(() -> {
            try {
                mainHandler.post(() -> callback.onProgress("Loading model..."));
                
                String modelPath = getModelPath();
                if (!new File(modelPath).exists()) {
                    mainHandler.post(() -> callback.onError("Model not downloaded"));
                    return;
                }
                
                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(512)
                        .setTopK(40)
                        .setTemperature(0.7f)
                        .build();
                
                llmInference = LlmInference.createFromOptions(context, options);
                isInitialized = true;
                
                mainHandler.post(() -> callback.onProgress("Model loaded successfully"));
                
            } catch (Exception e) {
                Log.e(TAG, "Error initializing LLM: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Failed to load model: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Generate response for a question about PDF content
     */
    public void askQuestion(String pdfContext, String question, InferenceCallback callback) {
        if (!isInitialized || llmInference == null) {
            callback.onError("Model not initialized");
            return;
        }
        
        executor.execute(() -> {
            try {
                // Limit context to avoid token overflow
                String limitedContext = pdfContext.length() > 2000 
                    ? pdfContext.substring(0, 2000) + "..." 
                    : pdfContext;
                
                String prompt = buildPrompt(limitedContext, question);
                
                mainHandler.post(() -> callback.onProgress("Generating answer..."));
                
                String response = llmInference.generateResponse(prompt);
                
                mainHandler.post(() -> callback.onResult(response));
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating response: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Build prompt for Qwen model
     */
    private String buildPrompt(String context, String question) {
        return "<|im_start|>system\n" +
               "You are a helpful assistant that answers questions based on the provided document content. " +
               "Answer concisely and accurately based only on the given context.\n" +
               "<|im_end|>\n" +
               "<|im_start|>user\n" +
               "Document content:\n" + context + "\n\n" +
               "Question: " + question + "\n" +
               "<|im_end|>\n" +
               "<|im_start|>assistant\n";
    }
    
    /**
     * Close and release resources
     */
    public void close() {
        if (llmInference != null) {
            llmInference.close();
            llmInference = null;
        }
        isInitialized = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    public boolean isReady() {
        return isInitialized && llmInference != null;
    }
}
