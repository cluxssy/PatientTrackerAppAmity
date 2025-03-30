package com.example.patienttracker.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.patienttracker.R;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsFragment extends Fragment {

    private static final String TAG = "NotificationsFragment";

    // Target types for notifications
    private static final int TARGET_ALL_USERS = 0;
    private static final int TARGET_ALL_PATIENTS = 1;
    private static final int TARGET_ALL_DOCTORS = 2;
    private static final int TARGET_SPECIFIC_USER = 3;

    private FirebaseFirestore db;
    private Spinner spinnerRecipient;
    private EditText etTitle, etMessage, etUserEmail;
    private TextInputLayout tilUserEmail;
    private Button btnSend;

    private User currentAdmin;
    private int selectedTargetType = TARGET_ALL_USERS;

    public NotificationsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        db = FirebaseUtil.getFirestore();

        // Initialize views
        spinnerRecipient = view.findViewById(R.id.spinner_recipient);
        etTitle = view.findViewById(R.id.et_notification_title);
        etMessage = view.findViewById(R.id.et_notification_message);
        etUserEmail = view.findViewById(R.id.et_specific_user);
        tilUserEmail = view.findViewById(R.id.til_specific_user);
        btnSend = view.findViewById(R.id.btn_send_notification);

        // Get current admin from arguments
        Bundle args = getArguments();
        if (args != null) {
            currentAdmin = (User) args.getSerializable("CURRENT_USER");
            if (currentAdmin != null) {
                Log.d(TAG, "Current admin: " + currentAdmin.getFullName());
            } else {
                Log.e(TAG, "Current admin is null");
            }
        }

        // DEBUG: Print all users in the database with their emails
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    StringBuilder userList = new StringBuilder("ALL USERS IN DATABASE:\n");
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String uid = doc.getId();
                        String email = doc.getString("email");
                        String fullName = doc.getString("fullName");
                        Long roleLong = doc.getLong("role");
                        int role = (roleLong != null) ? roleLong.intValue() : -1;

                        userList.append("UID: ").append(uid)
                                .append(", Email: ").append(email)
                                .append(", Name: ").append(fullName)
                                .append(", Role: ").append(role)
                                .append("\n");
                    }
                    Log.d(TAG, userList.toString());

                    // Also show a toast with count of users for debugging
                    showToast("Found " + queryDocumentSnapshots.size() +
                            " users in database. See logcat for details");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading users: " + e.getMessage(), e);
                });

        // Set up the recipient spinner
        setupRecipientSpinner();

        // Set up send button
        btnSend.setOnClickListener(v -> validateAndSendNotification());
    }

    private void setupRecipientSpinner() {
        // Create adapter for spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.notification_recipients,
                android.R.layout.simple_spinner_item
        );

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        spinnerRecipient.setAdapter(adapter);

        // Set the listener
        spinnerRecipient.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Update the selected target type
                selectedTargetType = position;

                // Show/hide the specific user email field based on selection
                tilUserEmail.setVisibility(position == TARGET_SPECIFIC_USER ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedTargetType = TARGET_ALL_USERS;
                tilUserEmail.setVisibility(View.GONE);
            }
        });
    }

    private void validateAndSendNotification() {
        // Get values from fields
        String title = etTitle.getText().toString().trim();
        String message = etMessage.getText().toString().trim();
        String userEmail = etUserEmail.getText().toString().trim();

        // Validate fields
        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title is required");
            etTitle.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(message)) {
            etMessage.setError("Message is required");
            etMessage.requestFocus();
            return;
        }

        if (selectedTargetType == TARGET_SPECIFIC_USER && TextUtils.isEmpty(userEmail)) {
            etUserEmail.setError("User email is required");
            etUserEmail.requestFocus();
            return;
        }

        // Disable the send button to prevent double submissions
        btnSend.setEnabled(false);

        // Send the notification
        if (selectedTargetType == TARGET_SPECIFIC_USER) {
            sendNotificationToSpecificUser(title, message, userEmail);
        } else {
            sendNotificationToGroup(title, message, selectedTargetType);
        }
    }

    private void sendNotificationToSpecificUser(String title, String message, String userEmail) {
        Log.d(TAG, "Searching for user with email: " + userEmail);

        // Show debug messages
        showToast("Searching for user: " + userEmail);

        // Email address might be case-sensitive, so convert to lowercase for comparison
        String normalizedSearchTerm = userEmail.trim().toLowerCase();

        // Find all users first and then filter manually (to avoid Firestore index issues)
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean userFound = false;
                    StringBuilder debugInfo = new StringBuilder("Available users:\n");

                    Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " users in database");

                    if (queryDocumentSnapshots.isEmpty()) {
                        showError("No users found in database");
                        btnSend.setEnabled(true);
                        return;
                    }

                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        String userId = document.getId();

                        // Fields may be swapped in the database, so check both "email" and "fullName" fields
                        String emailField = document.getString("email");
                        String nameField = document.getString("fullName");

                        // Add debug info about this user
                        debugInfo.append("User ID: ").append(userId)
                                .append(", Email field: ").append(emailField)
                                .append(", Name field: ").append(nameField)
                                .append("\n");

                        // Compare with the email field
                        if (checkForMatch(emailField, normalizedSearchTerm)) {
                            userFound = true;
                            processUserMatch(userId, emailField, title, message);
                            break;
                        }

                        // Compare with the name field (since they might be swapped)
                        if (checkForMatch(nameField, normalizedSearchTerm)) {
                            userFound = true;
                            processUserMatch(userId, nameField, title, message);
                            break;
                        }
                    }

                    if (!userFound) {
                        // Log detailed user info for debugging
                        Log.e(TAG, "No matching user found. " + debugInfo.toString());

                        showError("No user found with email: " + userEmail);
                        btnSend.setEnabled(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting users collection: " + e.getMessage(), e);
                    showError("Error searching for user: " + e.getMessage());
                    btnSend.setEnabled(true);
                });
    }

    // Helper method to check if a string matches our search term
    private boolean checkForMatch(String field, String normalizedSearchTerm) {
        if (field == null) return false;

        // Normalize the field value
        String normalizedField = field.trim().toLowerCase();

        // Log comparison for debugging
        Log.d(TAG, "Comparing: '" + normalizedSearchTerm + "' with '" + normalizedField + "'");

        // Check for exact match (case-insensitive)
        return normalizedField.equals(normalizedSearchTerm) ||
                // Also check if the search term is contained within
                normalizedField.contains(normalizedSearchTerm) ||
                normalizedSearchTerm.contains(normalizedField);
    }

    // Helper method to process a matching user
    private void processUserMatch(String userId, String fieldValue, String title, String message) {
        // Log user found
        Log.d(TAG, "FOUND USER: " + fieldValue + ", userId: " + userId);
        showToast("User found: " + fieldValue);

        // Create notification data
        Map<String, Object> notificationData = createNotificationData(title, message, TARGET_SPECIFIC_USER);

        // Add recipient UID
        List<String> recipientUids = new ArrayList<>();
        recipientUids.add(userId);
        notificationData.put("recipientUids", recipientUids);

        // Save the notification
        saveNotification(notificationData);

        // Log for debugging
        Log.d(TAG, "Sending notification to user: " + userId);
    }

    private void sendNotificationToGroup(String title, String message, int targetType) {
        // Create notification data
        Map<String, Object> notificationData = createNotificationData(title, message, targetType);

        // If sending to all users, add a marker for ALL_USERS
        if (targetType == TARGET_ALL_USERS) {
            // Add a marker for ALL_USERS
            notificationData.put("forAllUsers", true);

            // Also get all users to add to recipientUids for better compatibility
            db.collection("users")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<String> recipientUids = new ArrayList<>();

                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            recipientUids.add(doc.getId());
                        }

                        // Add recipient UIDs to notification data
                        notificationData.put("recipientUids", recipientUids);

                        // Save the notification
                        saveNotification(notificationData);

                        // Log for debugging
                        Log.d(TAG, "Sending notification to ALL users (" + recipientUids.size() + " recipients)");
                    })
                    .addOnFailureListener(e -> {
                        // Handle index errors gracefully
                        if (e.getMessage() != null && e.getMessage().contains("requires an index")) {
                            showError("Firestore index required. Please contact administrator.");
                            Log.e(TAG, "Firestore index error: " + e.getMessage());

                            // Save it anyway without specific recipients
                            saveNotification(notificationData);
                        } else {
                            showError("Error getting recipients: " + e.getMessage());
                            Log.e(TAG, "Error getting recipients: " + e.getMessage(), e);
                        }
                        btnSend.setEnabled(true);
                    });
            return;
        }

        // Otherwise, we need to get the UIDs of the target group (patients or doctors)
        int role = (targetType == TARGET_ALL_PATIENTS) ? User.ROLE_PATIENT : User.ROLE_DOCTOR;

        db.collection("users")
                .whereEqualTo("role", role)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> recipientUids = new ArrayList<>();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        recipientUids.add(doc.getId());
                    }

                    if (recipientUids.isEmpty()) {
                        btnSend.setEnabled(true);
                        String roleType = (targetType == TARGET_ALL_PATIENTS) ? "patients" : "doctors";
                        showError("No " + roleType + " found in the system");
                        return;
                    }

                    // Add recipient UIDs to notification data
                    notificationData.put("recipientUids", recipientUids);

                    // Save the notification
                    saveNotification(notificationData);

                    // Log for debugging
                    String roleType = (targetType == TARGET_ALL_PATIENTS) ? "PATIENTS" : "DOCTORS";
                    Log.d(TAG, "Sending notification to ALL " + roleType + " (" + recipientUids.size() + " recipients)");
                })
                .addOnFailureListener(e -> {
                    // Handle index errors gracefully
                    if (e.getMessage() != null && e.getMessage().contains("requires an index")) {
                        showError("Firestore index required. Please contact administrator.");
                        Log.e(TAG, "Firestore index error: " + e.getMessage());
                    } else {
                        showError("Error getting recipients: " + e.getMessage());
                        Log.e(TAG, "Error getting recipients: " + e.getMessage(), e);
                    }
                    btnSend.setEnabled(true);
                });
    }

    private Map<String, Object> createNotificationData(String title, String message, int targetType) {
        Map<String, Object> notificationData = new HashMap<>();

        // Basic notification data
        notificationData.put("title", title);
        notificationData.put("message", message);
        notificationData.put("timestamp", new Timestamp(new Date()));
        notificationData.put("isRead", false);
        notificationData.put("targetType", targetType);

        // Sender information
        if (currentAdmin != null) {
            notificationData.put("senderUid", currentAdmin.getUid());
            notificationData.put("senderName", currentAdmin.getFullName());
        }

        return notificationData;
    }

    private void saveNotification(Map<String, Object> notificationData) {
        // Log the notification data for debugging
        Log.d(TAG, "Saving notification with data: " + notificationData.toString());

        db.collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    // Log success
                    Log.d(TAG, "Notification saved with ID: " + documentReference.getId());

                    // Clear the form fields
                    etTitle.setText("");
                    etMessage.setText("");
                    etUserEmail.setText("");

                    // Show success message
                    showToast(getString(R.string.notification_sent));

                    // Re-enable the send button
                    btnSend.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    // Log error
                    Log.e(TAG, "Error saving notification: " + e.getMessage(), e);

                    // Handle index errors gracefully
                    if (e.getMessage() != null && e.getMessage().contains("requires an index")) {
                        showError("Firestore index required. Please contact administrator.");
                    } else {
                        showError(getString(R.string.error_sending_notification) + ": " + e.getMessage());
                    }
                    btnSend.setEnabled(true);
                });
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}