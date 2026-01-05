package com.softweb.chatwithpdf;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;

/**
 * Extracts text content from PDF files using PDFBox
 */
public class PdfTextExtractor {
    
    private static final String TAG = "PdfTextExtractor";
    private static boolean isInitialized = false;
    
    /**
     * Initialize PDFBox resources (call once on app start)
     */
    public static void initialize(Context context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            isInitialized = true;
        }
    }
    
    /**
     * Extract all text from a PDF file
     * @param context Application context
     * @param pdfUri URI of the PDF file
     * @return Extracted text content
     */
    public static String extractText(Context context, Uri pdfUri) {
        initialize(context);
        
        StringBuilder textBuilder = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(pdfUri)) {
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for PDF");
                return "";
            }
            
            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Extract text from all pages
            String text = stripper.getText(document);
            textBuilder.append(text);
            
            document.close();
            
            Log.d(TAG, "Extracted " + textBuilder.length() + " characters from PDF");
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting text from PDF: " + e.getMessage(), e);
        }
        
        return textBuilder.toString();
    }
    
    /**
     * Extract text from specific page range
     */
    public static String extractText(Context context, Uri pdfUri, int startPage, int endPage) {
        initialize(context);
        
        StringBuilder textBuilder = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(pdfUri)) {
            if (inputStream == null) {
                return "";
            }
            
            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            
            String text = stripper.getText(document);
            textBuilder.append(text);
            
            document.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting text: " + e.getMessage(), e);
        }
        
        return textBuilder.toString();
    }
    
    /**
     * Get page count of a PDF
     */
    public static int getPageCount(Context context, Uri pdfUri) {
        initialize(context);
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(pdfUri)) {
            if (inputStream == null) {
                return 0;
            }
            
            PDDocument document = PDDocument.load(inputStream);
            int pageCount = document.getNumberOfPages();
            document.close();
            
            return pageCount;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting page count: " + e.getMessage(), e);
            return 0;
        }
    }
}
