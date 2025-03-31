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
import com.example.patienttracker.adapters.ApprovalAdapter;
import com.example.patienttracker.interfaces.ApprovalActionListener;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalsFragment extends Fragment implements ApprovalActionListener {

    private static final String TAG = "ApprovalsFragment";

    private RecyclerView recyclerView;
    private ApprovalAdapter adapter;
    private List<User> pendingDoctorsList;
    private TextView tvNoApprovals;
    private ProgressBar progressBar;

    private FirebaseFirestore db;

    // To track and remove listeners
    private ListenerRegistration pendingDoctorsListener;

    public ApprovalsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_approvals, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        db = FirebaseUtil.getFirestore();

        // Initialize views
        recyclerView = view.findViewById(R.id.rv_approvals);
        tvNoApprovals = view.findViewById(R.id.tv_no_approvals);
        progressBar = view.findViewById(R.id.progress_bar);

        // Initialize RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        pendingDoctorsList = new ArrayList<>();
        adapter = new ApprovalAdapter(getContext(), pendingDoctorsList, this);
        recyclerView.setAdapter(adapter);

        // Load pending doctor approvals
        loadPendingDoctors();
    }

    private void loadPendingDoctors() {
        showLoading(true);

        // Query for doctors with pending status
        Query query = FirebaseUtil.getPendingDoctorsQuery();

        // Store the listener registration so we can remove it later
        pendingDoctorsListener = query.addSnapshotListener((QuerySnapshot snapshots, FirebaseFirestoreException e) -> {
            if (e != null) {
                Log.e(TAG, "Listen failed.", e);
                showLoading(false);

                // Check if this is an index error and show a helpful dialog if it is
                if (com.example.patienttracker.utils.FirestoreIndexHelper.isIndexError(e)) {
                    Log.i(TAG, "Firestore index required. Using simplified query instead.");
                    // Show UI for the error without blocking functionality
                    tvNoApprovals.setText("Error: Firestore index required. Administrator can fix this.");
                    tvNoApprovals.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);

                    // Optionally show a dialog to help create the index (if in development)
                    // com.example.patienttracker.utils.FirestoreIndexHelper.showIndexHelperDialog(getContext(), e);

                    // For now, let's just show a simple error
                    showError("Index error. Contact administrator or update code.");
                } else {
                    showError("Error loading approvals: " + e.getMessage());
                }
                return;
            }

            pendingDoctorsList.clear();

            for (DocumentSnapshot doc : snapshots) {
                try {
                    // Don't use toObject to avoid @DocumentId issues
                    User doctor = createUserFromDocument(doc);
                    if (doctor != null) {
                        pendingDoctorsList.add(doctor);
                    }
                } catch (Exception parseException) {
                    Log.e(TAG, "Error parsing doctor data: " + parseException.getMessage(), parseException);
                }
            }

            adapter.notifyDataSetChanged();
            showLoading(false);

            // Show "No approvals" message if list is empty
            if (pendingDoctorsList.isEmpty()) {
                tvNoApprovals.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvNoApprovals.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onApprove(User doctor) {
        if (doctor != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", User.STATUS_ACTIVE);

            // Update status to active
            db.collection("users").document(doctor.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        showToast("Doctor " + doctor.getFullName() + " has been approved");
                        // Send notification to doctor
                        sendApprovalNotification(doctor, true);
                    })
                    .addOnFailureListener(e -> {
                        showError("Error approving doctor: " + e.getMessage());
                    });
        }
    }

    @Override
    public void onReject(User doctor) {
        if (doctor != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", User.STATUS_REJECTED);

            // Update status to rejected
            db.collection("users").document(doctor.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        showToast("Doctor " + doctor.getFullName() + " has been rejected");
                        // Send notification to doctor
                        sendApprovalNotification(doctor, false);
                    })
                    .addOnFailureListener(e -> {
                        showError("Error rejecting doctor: " + e.getMessage());
                    });
        }
    }

    private void sendApprovalNotification(User doctor, boolean isApproved) {
        // Get current admin name
        FirebaseUtil.getCurrentUserDocument().get()
                .addOnSuccessListener(documentSnapshot -> {
                    try {
                        // Don't use toObject to avoid @DocumentId issues
                        User currentAdmin = createUserFromDocument(documentSnapshot);
                        if (currentAdmin != null) {

                            String adminName = currentAdmin.getFullName();
                            String title = isApproved ? "Account Approved" : "Account Rejected";
                            String message = isApproved ?
                                    "Your doctor account has been approved by " + adminName + ". You can now login and use the system." :
                                    "Your doctor account has been rejected by " + adminName + ". Please contact support for more information.";

                            // Create notification document
                            List<String> recipientIds = new ArrayList<>();
                            recipientIds.add(doctor.getUid());

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
                        }
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

    @Override
    public void onPause() {
        // Remove the listener when the fragment is paused
        if (pendingDoctorsListener != null) {
            pendingDoctorsListener.remove();
            pendingDoctorsListener = null;
            Log.d(TAG, "Firestore listener removed on pause");
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reattach the listener when the fragment is resumed if it was removed
        if (pendingDoctorsListener == null) {
            loadPendingDoctors();
            Log.d(TAG, "Firestore listener reattached on resume");
        }
    }

    @Override
    public void onDestroyView() {
        // Remove the snapshot listener to prevent memory leaks and permission errors
        if (pendingDoctorsListener != null) {
            pendingDoctorsListener.remove();
            pendingDoctorsListener = null;
            Log.d(TAG, "Firestore listener removed on destroy");
        }
        super.onDestroyView();
    }

    /**
     * Create a User object manually from a DocumentSnapshot to avoid @DocumentId issues
     */
    private User createUserFromDocument(DocumentSnapshot documentSnapshot) {
        try {
            Map<String, Object> userData = documentSnapshot.getData();
            if (userData == null) {
                Log.e(TAG, "createUserFromDocument: userData is null");
                return null;
            }

            User user = new User();
            user.setUid(documentSnapshot.getId()); // Set the document ID

            // Manually map fields from document to User object
            if (userData.containsKey("email")) {
                user.setEmail((String) userData.get("email"));
            }
            if (userData.containsKey("fullName")) {
                user.setFullName((String) userData.get("fullName"));
            }
            if (userData.containsKey("role")) {
                Object roleObj = userData.get("role");
                if (roleObj instanceof Long) {
                    user.setRole(((Long) roleObj).intValue());
                } else if (roleObj instanceof Integer) {
                    user.setRole((Integer) roleObj);
                }
            }
            if (userData.containsKey("status")) {
                Object statusObj = userData.get("status");
                if (statusObj instanceof Long) {
                    user.setStatus(((Long) statusObj).intValue());
                } else if (statusObj instanceof Integer) {
                    user.setStatus((Integer) statusObj);
                }
            }
            if (userData.containsKey("specialization")) {
                user.setSpecialization((String) userData.get("specialization"));
            }
            if (userData.containsKey("photoUrl")) {
                user.setPhotoUrl((String) userData.get("photoUrl"));
            }
            if (userData.containsKey("phone")) {
                user.setPhone((String) userData.get("phone"));
            }

            return user;
        } catch (Exception e) {
            Log.e(TAG, "Error creating user from document: " + e.getMessage(), e);
            return null;
        }
    }
}