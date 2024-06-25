package com.softweb.chatwithpdf;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VolleyMultipartRequest extends Request<NetworkResponse> {

    private static final String MULTIPART_FORM_DATA = "multipart/form-data;boundary=";
    private static final String BOUNDARY = "apiclient-" + System.currentTimeMillis();

    private final Response.Listener<NetworkResponse> listener;
    private final Response.ErrorListener errorListener;
    private final Map<String, DataPart> byteData;

    public VolleyMultipartRequest(int method, String url,
                                  Response.Listener<NetworkResponse> listener,
                                  Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.listener = listener;
        this.errorListener = errorListener;
        this.byteData = new HashMap<>();
    }

    public void addByteData(String key, DataPart dataPart) {
        byteData.put(key, dataPart);
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", MULTIPART_FORM_DATA + BOUNDARY);
        return headers;
    }

    @Override
    public String getBodyContentType() {
        return MULTIPART_FORM_DATA + BOUNDARY;
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            for (Map.Entry<String, DataPart> entry : byteData.entrySet()) {
                buildPart(bos, entry.getKey(), entry.getValue());
            }
            bos.write(("--" + BOUNDARY + "--\r\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bos.toByteArray();
    }

    private void buildPart(ByteArrayOutputStream bos, String name, DataPart dataPart) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(BOUNDARY).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(name).append("\"; filename=\"").append(dataPart.getFileName()).append("\"\r\n");
        sb.append("Content-Type: ").append(dataPart.getType()).append("\r\n\r\n");
        bos.write(sb.toString().getBytes());
        bos.write(dataPart.getContent());
        bos.write("\r\n".getBytes());
    }

    @Override
    protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
        try {
            return Response.success(
                    response,
                    HttpHeaderParser.parseCacheHeaders(response)
            );
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }

    @Override
    protected void deliverResponse(NetworkResponse response) {
        listener.onResponse(response);
    }

    @Override
    public void deliverError(VolleyError error) {
        errorListener.onErrorResponse(error);
    }

    public static class DataPart {
        private final String fileName;
        private final byte[] content;
        private final String type;

        public DataPart(String fileName, byte[] content, String type) {
            this.fileName = fileName;
            this.content = content;
            this.type = type;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getContent() {
            return content;
        }

        public String getType() {
            return type;
        }
    }
}
