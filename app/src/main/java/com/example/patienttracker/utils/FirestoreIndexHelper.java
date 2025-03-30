package com.example.patienttracker.utils;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * Helper class to handle Firestore index-related errors and assist users in creating required indexes
 */
public class FirestoreIndexHelper {

    private static final String TAG = "FirestoreIndexHelper";
    private static final String INDEX_URL_PREFIX = "https://console.firebase.google.com";

    /**
     * Checks if an exception is related to missing Firestore indexes
     * @param exception The exception to check
     * @return true if the exception is related to missing Firestore indexes
     */
    public static boolean isIndexError(Exception exception) {
        if (exception instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) exception;
            return firestoreException.getCode() == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    && exception.getMessage() != null
                    && exception.getMessage().contains("requires an index");
        }
        return false;
    }

    /**
     * Extracts the index creation URL from an exception
     * @param exception The exception containing the index URL
     * @return The extracted URL or null if not found
     */
    public static String extractIndexUrl(Exception exception) {
        if (exception == null || exception.getMessage() == null) {
            return null;
        }

        String message = exception.getMessage();
        int indexStart = message.indexOf(INDEX_URL_PREFIX);
        if (indexStart == -1) {
            return null;
        }

        // Find the end of the URL (space, newline, or end of string)
        int indexEnd = message.indexOf(' ', indexStart);
        if (indexEnd == -1) {
            indexEnd = message.indexOf('\n', indexStart);
        }
        if (indexEnd == -1) {
            indexEnd = message.length();
        }

        return message.substring(indexStart, indexEnd);
    }

    /**
     * Shows a dialog to help the user create the required Firestore index
     * @param context The context
     * @param exception The exception containing the index URL
     */
    public static void showIndexHelperDialog(Context context, Exception exception) {
        if (context == null || exception == null) {
            return;
        }

        try {
            String indexUrl = extractIndexUrl(exception);
            if (indexUrl == null) {
                android.util.Log.e(TAG, "Could not extract index URL from exception: " + exception.getMessage());
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Firestore Index Required");
            builder.setMessage("This query requires a Firestore index to be created. You have two options:\n\n"
                    + "1. Open the link in a browser and create the index (requires Firebase Console access)\n\n"
                    + "2. Copy the link and send it to your Firebase administrator");

            builder.setPositiveButton("Open Link", (dialog, which) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(indexUrl));
                context.startActivity(browserIntent);
            });

            builder.setNeutralButton("Copy Link", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Firestore Index URL", indexUrl);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

            builder.show();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error showing index helper dialog", e);
        }
    }
}