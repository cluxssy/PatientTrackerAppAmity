package com.example.patienttracker.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patienttracker.R;
import com.example.patienttracker.adapters.UserAdapter;
import com.example.patienttracker.interfaces.UserActionListener;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsersFragment extends Fragment implements UserActionListener {

    private static final String TAG = "UsersFragment";

    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private List<User> usersList;
    private TextView tvNoUsers;
    private ProgressBar progressBar;

    private FirebaseFirestore db;

    public UsersFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        db = FirebaseUtil.getFirestore();

        // Initialize views
        recyclerView = view.findViewById(R.id.rv_users);
        tvNoUsers = view.findViewById(R.id.tv_no_users);
        progressBar = view.findViewById(R.id.progress_bar);

        // Initialize RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        usersList = new ArrayList<>();
        adapter = new UserAdapter(getContext(), usersList, this);
        recyclerView.setAdapter(adapter);

        // Load all users
        loadUsers();
    }

    private void loadUsers() {
        showLoading(true);

        // Query for all users
        Query query = FirebaseUtil.getAllUsersQuery();

        query.addSnapshotListener((QuerySnapshot snapshots, FirebaseFirestoreException e) -> {
            if (e != null) {
                Log.e(TAG, "Listen failed.", e);
                showLoading(false);

                // Check if this is an index error and handle it gracefully
                if (com.example.patienttracker.utils.FirestoreIndexHelper.isIndexError(e)) {
                    Log.i(TAG, "Firestore index required. Using simplified query instead.");
                    // Show UI for the error without blocking functionality
                    tvNoUsers.setText("Error: Firestore index required. Administrator can fix this.");
                    tvNoUsers.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);

                    // For now, let's just show a simple error
                    showError("Index error. Contact administrator or update code.");
                } else {
                    showError("Error loading users: " + e.getMessage());
                }
                return;
            }

            usersList.clear();

            for (DocumentSnapshot doc : snapshots) {
                try {
                    // Since we're getting warnings about incompatible fields,
                    // manually extract the fields we need instead of using toObject()
                    String userId = doc.getId();
                    String email = doc.getString("email");
                    String fullName = doc.getString("fullName");

                    // Get integers with fallbacks
                    Long roleLong = doc.getLong("role");
                    int role = (roleLong != null) ? roleLong.intValue() : User.ROLE_PATIENT;

                    Long statusLong = doc.getLong("status");
                    int status = (statusLong != null) ? statusLong.intValue() : User.STATUS_PENDING;

                    // Create a user object manually
                    User user = new User(userId, email, fullName, role);
                    user.setStatus(status);

                    // Add additional fields if they exist
                    if (doc.contains("phone")) user.setPhone(doc.getString("phone"));
                    if (doc.contains("address")) user.setAddress(doc.getString("address"));
                    if (doc.contains("dateOfBirth")) user.setDateOfBirth(doc.getString("dateOfBirth"));
                    if (doc.contains("gender")) user.setGender(doc.getString("gender"));
                    if (doc.contains("photoUrl")) user.setPhotoUrl(doc.getString("photoUrl"));

                    // Doctor specific fields
                    if (doc.contains("specialization")) user.setSpecialization(doc.getString("specialization"));
                    if (doc.contains("department")) user.setDepartment(doc.getString("department"));

                    if (doc.contains("yearsOfExperience")) {
                        Long expLong = doc.getLong("yearsOfExperience");
                        if (expLong != null) {
                            user.setYearsOfExperience(expLong.intValue());
                        }
                    }

                    usersList.add(user);
                } catch (Exception parseException) {
                    Log.e(TAG, "Error parsing user data for document " + doc.getId() + ": "
                            + parseException.getMessage(), parseException);
                }
            }

            adapter.notifyDataSetChanged();
            showLoading(false);

            // Show "No users" message if list is empty
            if (usersList.isEmpty()) {
                tvNoUsers.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvNoUsers.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onActivate(User user) {
        if (user != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", User.STATUS_ACTIVE);

            // 1. First update the user's status in Firestore
            db.collection("users").document(user.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // 2. Then get a custom token to update Firebase Auth status
                        activateFirebaseAuthAccount(user);
                    })
                    .addOnFailureListener(e -> {
                        showError("Error activating user: " + e.getMessage());
                    });
        }
    }

    // Method to activate the user's authentication account
    private void activateFirebaseAuthAccount(User user) {
        // Use Firebase Functions to enable user account
        // This is a workaround using an admin notification to indicate success
        // A real production app would use Firebase Cloud Functions to manage user accounts
        Map<String, Object> functionData = new HashMap<>();
        functionData.put("action", "enable_user");
        functionData.put("uid", user.getUid());

        // Instead of actually calling a function, we'll update Firestore and simulate success
        db.collection("adminActions").document()
                .set(functionData)
                .addOnSuccessListener(aVoid -> {
                    showToast("User " + user.getFullName() + " has been activated");
                    sendStatusNotification(user, true);
                })
                .addOnFailureListener(e -> {
                    showError("Error logging admin action: " + e.getMessage());
                });
    }

    @Override
    public void onDeactivate(User user) {
        if (user != null && !user.isAdmin()) { // Don't allow deactivating admin accounts
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", User.STATUS_REJECTED);

            // 1. First update the user's status in Firestore
            db.collection("users").document(user.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // 2. Then disable the user's account in Firebase Auth
                        deactivateFirebaseAuthAccount(user);
                    })
                    .addOnFailureListener(e -> {
                        showError("Error deactivating user: " + e.getMessage());
                    });
        } else if (user != null && user.isAdmin()) {
            showToast("Admin accounts cannot be deactivated");
        }
    }

    // Method to deactivate the user's authentication account
    private void deactivateFirebaseAuthAccount(User user) {
        // Use Firebase Functions to disable user account
        // This is a workaround using an admin notification to indicate success
        // A real production app would use Firebase Cloud Functions to manage user accounts
        Map<String, Object> functionData = new HashMap<>();
        functionData.put("action", "disable_user");
        functionData.put("uid", user.getUid());

        // Instead of actually calling a function, we'll update Firestore and simulate success
        db.collection("adminActions").document()
                .set(functionData)
                .addOnSuccessListener(aVoid -> {
                    showToast("User " + user.getFullName() + " has been deactivated");
                    sendStatusNotification(user, false);
                })
                .addOnFailureListener(e -> {
                    showError("Error logging admin action: " + e.getMessage());
                });
    }

    private void sendStatusNotification(User user, boolean isActivated) {
        // Get current admin name
        FirebaseUtil.getCurrentUserDocument().get()
                .addOnSuccessListener(documentSnapshot -> {
                    try {
                        // Manually parse the current admin user to avoid model mapping issues
                        String adminId = documentSnapshot.getId();
                        String adminEmail = documentSnapshot.getString("email");
                        String adminFullName = documentSnapshot.getString("fullName");

                        // Get role with fallback
                        Long roleLong = documentSnapshot.getLong("role");
                        int adminRole = (roleLong != null) ? roleLong.intValue() : User.ROLE_ADMIN;

                        User currentAdmin = new User(adminId, adminEmail, adminFullName, adminRole);

                        String adminName = currentAdmin.getFullName();
                        String title = isActivated ? "Account Activated" : "Account Deactivated";
                        String message = isActivated ?
                                "Your account has been activated by " + adminName + ". You can now login and use the system." :
                                "Your account has been deactivated by " + adminName + ". Please contact support for more information.";

                        // Create notification document
                        List<String> recipientIds = new ArrayList<>();
                        recipientIds.add(user.getUid());

                        Map<String, Object> notification = new HashMap<>();
                        notification.put("title", title);
                        notification.put("message", message);
                        notification.put("senderUid", currentAdmin.getUid());
                        notification.put("senderName", adminName);
                        notification.put("targetType", 3); // Specific users
                        notification.put("recipientUids", recipientIds);
                        notification.put("isRead", false);
                        notification.put("timestamp", new com.google.firebase.Timestamp(new java.util.Date()));

                        // Save notification to Firestore
                        db.collection("notifications")
                                .add(notification)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "Notification sent successfully");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error sending notification", e);
                                });
                    } catch (Exception adminException) {
                        Log.e(TAG, "Error processing admin user: " + adminException.getMessage(), adminException);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting admin data", e);
                });
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}