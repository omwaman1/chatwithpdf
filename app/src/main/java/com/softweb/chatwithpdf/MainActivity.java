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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1;
    private static final int STORAGE_PERMISSION_CODE = 2;
    private Uri pdfUri;

    private TextView fileNameTextView;
    private EditText questionEditText;
    private ProgressBar progressBar;
    private Button uploadButton;
    private Button askButton;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;

    private RequestQueue requestQueue;

    private String serverUrl = "https://omwaman.pythonanywhere.com/";

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

        requestQueue = Volley.newRequestQueue(this);

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStoragePermission();
            }
        });

        askButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askQuestion();
            }
        });
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                selectPdf();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                }, STORAGE_PERMISSION_CODE);
            }
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
            uploadPdf();
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

    private void uploadPdf() {
        if (pdfUri == null) {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
            byte[] inputData = getBytes(inputStream);

            VolleyMultipartRequest volleyMultipartRequest = new VolleyMultipartRequest(Request.Method.POST, serverUrl,
                    new Response.Listener<NetworkResponse>() {
                        @Override
                        public void onResponse(NetworkResponse response) {
                            progressBar.setVisibility(View.GONE);
                            try {
                                JSONObject result = new JSONObject(new String(response.data));
                                String context = result.getString("context");
                                chatMessages.add(new ChatMessage("Context: " + context, false));
                                chatAdapter.notifyDataSetChanged();
                                chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            progressBar.setVisibility(View.GONE);
                            String errorMessage = "Unknown error occurred";  // Default error message

                            if (error instanceof NetworkError) {
                                errorMessage = "Network error";
                            } else if (error instanceof ServerError) {
                                errorMessage = "Server error";
                            } else if (error instanceof AuthFailureError) {
                                errorMessage = "Authentication failure";
                            } else if (error instanceof ParseError) {
                                errorMessage = "Parse error";
                            } else if (error instanceof TimeoutError) {
                                errorMessage = "Connection timeout";
                            }

                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }

                    });

            volleyMultipartRequest.addByteData("file", new VolleyMultipartRequest.DataPart("file.pdf", inputData, "application/pdf"));
            requestQueue.add(volleyMultipartRequest);

        } catch (IOException e) {
            e.printStackTrace();
            progressBar.setVisibility(View.GONE);
        }
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private void askQuestion() {
        String question = questionEditText.getText().toString();

        if (question.isEmpty()) {
            Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Add the question to the chat
        chatMessages.add(new ChatMessage(question, true));
        chatAdapter.notifyDataSetChanged();
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);

        JSONObject jsonObject = new JSONObject();
        try {
            // Assuming that context is stored after the PDF upload
            String context = chatMessages.size() > 0 ? chatMessages.get(0).getMessage().replace("Context: ", "") : "";
            jsonObject.put("context", context);
            jsonObject.put("question", question);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, serverUrl, jsonObject,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        progressBar.setVisibility(View.GONE);
                        try {
                            if (response.has("answer")) {
                                String answer = response.getString("answer");
                                chatMessages.add(new ChatMessage(answer, false));
                                chatAdapter.notifyDataSetChanged();
                                chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                            } else {
                                Toast.makeText(MainActivity.this, "No answer received", Toast.LENGTH_LONG).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        progressBar.setVisibility(View.GONE);
                        String errorMessage = "Unknown error occurred";  // Default error message

                        if (error instanceof NetworkError) {
                            errorMessage = "Network error";
                        } else if (error instanceof ServerError) {
                            errorMessage = "Server error";
                        } else if (error instanceof AuthFailureError) {
                            errorMessage = "Authentication failure";
                        } else if (error instanceof ParseError) {
                            errorMessage = "Parse error";
                        } else if (error instanceof TimeoutError) {
                            errorMessage = "Connection timeout";
                        }

                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }

                });

        requestQueue.add(jsonObjectRequest);
    }

}
