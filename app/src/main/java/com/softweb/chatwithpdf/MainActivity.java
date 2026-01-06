package com.softweb.chatwithpdf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
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
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB max
    private static final int MAX_TEXT_CHARS = 50000; // ~50K chars for model context
    
    // URLs
    private static final String PRIVACY_POLICY_URL = "https://omwaman1.github.io/chatwithpdf/privacy-policy.html";
    private static final String PLAY_STORE_URL = "https://play.google.com/store/search?q=pub:Softweb_technologies&c=apps";
    
    private Uri pdfUri;
    private String pdfText = "";

    private TextView fileNameTextView;
    private EditText questionEditText;
    private ProgressBar progressBar;
    private Button uploadButton;
    private Button askButton;
    private ImageButton menuButton;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;

    private ChatApiClient chatApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fileNameTextView = findViewById(R.id.fileNameTextView);
        questionEditText = findViewById(R.id.questionEditText);
        progressBar = findViewById(R.id.progressBar);
        uploadButton = findViewById(R.id.uploadButton);
        askButton = findViewById(R.id.askButton);
        menuButton = findViewById(R.id.menuButton);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setAdapter(chatAdapter);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize Chat API Client
        chatApiClient = new ChatApiClient(this);
        
        fileNameTextView.setText("Ready - Select a PDF");

        uploadButton.setOnClickListener(v -> requestStoragePermission());
        askButton.setOnClickListener(v -> askQuestion());
        menuButton.setOnClickListener(v -> openDrawer());
        
        // Set up navigation drawer item selection
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_about) {
                showAboutDialog();
            } else if (id == R.id.nav_privacy) {
                openPrivacyPolicy();
            } else if (id == R.id.nav_share) {
                shareApp();
            } else if (id == R.id.nav_more_apps) {
                openMoreApps();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }
    
    private void openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
    }
    
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    
    private void showAboutDialog() {
        String version = "2.0";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        
        new AlertDialog.Builder(this)
            .setTitle("About Chat with PDF")
            .setMessage("Version: " + version + "\n\n" +
                    "Chat with PDF lets you upload any PDF document and ask questions about its content using AI.\n\n" +
                    "Features:\n" +
                    "• Upload PDF files (max 10MB)\n" +
                    "• Ask questions in natural language\n" +
                    "• Get AI-powered answers\n\n" +
                    "Developed by Softweb Technologies")
            .setPositiveButton("OK", null)
            .setNeutralButton("Privacy Policy", (d, w) -> openPrivacyPolicy())
            .show();
    }
    
    private void openPrivacyPolicy() {
        Intent intent = new Intent(this, PrivacyPolicyActivity.class);
        startActivity(intent);
    }
    
    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Chat with PDF - AI Document Assistant");
        shareIntent.putExtra(Intent.EXTRA_TEXT, 
            "Check out Chat with PDF! Upload any PDF and ask questions about it using AI.\n\n" +
            "Download: https://play.google.com/store/apps/details?id=" + getPackageName());
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }
    
    private void openMoreApps() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Play Store", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
            
            // Validate file size
            long fileSize = getFileSize(pdfUri);
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                Toast.makeText(this, "❌ File too large! Max size is 10MB", Toast.LENGTH_LONG).show();
                return;
            }
            
            displayFileName(pdfUri);
            extractPdfText();
        }
    }
    
    private long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    return cursor.getLong(sizeIndex);
                }
            }
        }
        return 0;
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
                        // Show clean confirmation without messy preview
                        chatMessages.add(new ChatMessage("✅ PDF ready! Ask me anything about this document.", false));
                        chatAdapter.notifyDataSetChanged();
                        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
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

        progressBar.setVisibility(View.VISIBLE);
        questionEditText.setText("");

        chatMessages.add(new ChatMessage(question, true));
        chatAdapter.notifyDataSetChanged();
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);

        chatApiClient.askQuestion(pdfText, question, new ChatApiClient.ChatCallback() {
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
        if (chatApiClient != null) {
            chatApiClient.close();
        }
    }
}
