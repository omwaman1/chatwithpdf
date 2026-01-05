package com.softweb.chatwithpdf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1;
    private static final int STORAGE_PERMISSION_CODE = 2;
    
    private Uri pdfUri;
    private String pdfText = "";

    private TextView fileNameTextView;
    private EditText questionEditText;
    private ProgressBar progressBar;
    private Button uploadButton;
    private Button askButton;
    private TextView statusTextView;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;

    private QwenInference qwenInference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fileNameTextView = findViewById(R.id.fileNameTextView);
        questionEditText = findViewById(R.id.questionEditText);
        progressBar = findViewById(R.id.progressBar);
        uploadButton = findViewById(R.id.uploadButton);
        askButton = findViewById(R.id.askButton);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setAdapter(chatAdapter);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize Qwen LLM
        qwenInference = new QwenInference(this);
        
        // Check if model needs to be downloaded
        checkModelStatus();

        uploadButton.setOnClickListener(v -> requestStoragePermission());
        
        askButton.setOnClickListener(v -> askQuestion());
    }
    
    private void checkModelStatus() {
        if (!qwenInference.isModelDownloaded()) {
            showModelDownloadDialog();
        } else {
            initializeModel();
        }
    }
    
    private void showModelDownloadDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Download AI Model")
            .setMessage("The offline AI model (~300MB) needs to be downloaded for the app to work.\n\nThis is a one-time download.")
            .setPositiveButton("Download", (dialog, which) -> downloadModel())
            .setNegativeButton("Cancel", (dialog, which) -> {
                Toast.makeText(this, "Model required for Q&A functionality", Toast.LENGTH_LONG).show();
            })
            .setCancelable(false)
            .show();
    }
    
    private void downloadModel() {
        progressBar.setVisibility(View.VISIBLE);
        fileNameTextView.setText("Downloading AI model...");
        
        qwenInference.downloadModel(new QwenInference.ModelDownloadCallback() {
            @Override
            public void onProgress(int percent) {
                fileNameTextView.setText("Downloading: " + percent + "%");
            }
            
            @Override
            public void onComplete(File modelFile) {
                fileNameTextView.setText("Download complete!");
                progressBar.setVisibility(View.GONE);
                initializeModel();
            }
            
            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                fileNameTextView.setText("Download failed");
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void initializeModel() {
        progressBar.setVisibility(View.VISIBLE);
        fileNameTextView.setText("Loading AI model...");
        
        qwenInference.initialize(new QwenInference.InferenceCallback() {
            @Override
            public void onResult(String response) {
                // Not used for initialization
            }
            
            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                fileNameTextView.setText("Model load failed");
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onProgress(String status) {
                progressBar.setVisibility(View.GONE);
                fileNameTextView.setText("Ready - Select a PDF");
                Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, we don't need storage permissions for document picker
            selectPdf();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                selectPdf();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            boolean permissionGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    permissionGranted = false;
                    break;
                }
            }
            if (permissionGranted) {
                selectPdf();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void selectPdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, PICK_PDF_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            pdfUri = data.getData();
            displayFileName(pdfUri);
            extractPdfText();
        }
    }

    @SuppressLint("Range")
    private void displayFileName(Uri uri) {
        String displayName = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        } else if (uri.getScheme().equals("file")) {
            displayName = new File(uri.getPath()).getName();
        }
        fileNameTextView.setText(displayName);
    }

    private void extractPdfText() {
        progressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                pdfText = PdfTextExtractor.extractText(this, pdfUri);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (pdfText.isEmpty()) {
                        Toast.makeText(this, "Could not extract text from PDF", Toast.LENGTH_LONG).show();
                    } else {
                        // Show extracted text preview in chat
                        String preview = pdfText.length() > 200 
                            ? pdfText.substring(0, 200) + "..." 
                            : pdfText;
                        chatMessages.add(new ChatMessage("PDF loaded (" + pdfText.length() + " characters)\n\nPreview: " + preview, false));
                        chatAdapter.notifyDataSetChanged();
                        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                        
                        Toast.makeText(this, "PDF text extracted. Ready for questions!", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void askQuestion() {
        String question = questionEditText.getText().toString().trim();

        if (question.isEmpty()) {
            Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (pdfText.isEmpty()) {
            Toast.makeText(this, "Please upload a PDF first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!qwenInference.isReady()) {
            Toast.makeText(this, "AI model not ready. Please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        questionEditText.setText("");

        // Add the question to the chat
        chatMessages.add(new ChatMessage(question, true));
        chatAdapter.notifyDataSetChanged();
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);

        // Ask the local LLM
        qwenInference.askQuestion(pdfText, question, new QwenInference.InferenceCallback() {
            @Override
            public void onResult(String response) {
                progressBar.setVisibility(View.GONE);
                chatMessages.add(new ChatMessage(response, false));
                chatAdapter.notifyDataSetChanged();
                chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onProgress(String status) {
                // Could show status in UI if needed
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (qwenInference != null) {
            qwenInference.close();
        }
    }
}
