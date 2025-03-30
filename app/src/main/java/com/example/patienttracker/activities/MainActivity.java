package com.example.patienttracker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.patienttracker.R;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * The main entry point for the application.
 * This activity checks if the user is logged in and redirects to the appropriate dashboard
 * based on the user's role, or to the login screen if not logged in.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int FIREBASE_OPERATION_TIMEOUT_MS = 15000; // 15 seconds timeout

    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: MainActivity started");

        // Initialize timeout handler
        timeoutHandler = new Handler(Looper.getMainLooper());

        try {
            // Initialize Firebase Auth
            auth = FirebaseUtil.getFirebaseAuth();

            // Set up auth state listener with error handling
            authStateListener = firebaseAuth -> {
                try {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        // User is signed in
                        Log.d(TAG, "onAuthStateChanged: signed_in: " + user.getUid());
                        cancelTimeoutHandler(); // Cancel any running timeout since auth state changed
                        checkUserRoleAndRedirect(user);
                    } else {
                        // User is signed out
                        Log.d(TAG, "onAuthStateChanged: signed_out");
                        cancelTimeoutHandler(); // Cancel any running timeout since auth state changed
                        navigateToLogin();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in auth state listener: " + e.getMessage(), e);
                    cancelTimeoutHandler();
                    navigateToLogin();
                }
            };
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase Auth: " + e.getMessage(), e);
            // Handle initialization error by proceeding to login after a short delay
            new Handler(Looper.getMainLooper()).postDelayed(this::navigateToLogin, 1000);
            return;
        }

        // Set a timeout for the MainActivity
        setOperationTimeout();
    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            Log.d(TAG, "onStart: Adding auth state listener");
            auth.addAuthStateListener(authStateListener);
        } catch (Exception e) {
            Log.e(TAG, "Error adding auth state listener: " + e.getMessage(), e);
            navigateToLogin();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            Log.d(TAG, "onStop: Removing auth state listener");
            if (authStateListener != null) {
                auth.removeAuthStateListener(authStateListener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing auth state listener: " + e.getMessage(), e);
        }

        // Cancel timeout handler
        cancelTimeoutHandler();
    }

    /**
     * Set a timeout for Firebase operations
     */
    private void setOperationTimeout() {
        timeoutRunnable = () -> {
            Log.e(TAG, "Firebase operation timed out after " + FIREBASE_OPERATION_TIMEOUT_MS + "ms");

            // If we're still in this activity after timeout, go to login
            Toast.makeText(MainActivity.this,
                    "Taking too long to retrieve your account. Please try again.",
                    Toast.LENGTH_LONG).show();
            navigateToLogin();
        };

        timeoutHandler.postDelayed(timeoutRunnable, FIREBASE_OPERATION_TIMEOUT_MS);
    }

    /**
     * Cancel the timeout handler
     */
    private void cancelTimeoutHandler() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            Log.d(TAG, "cancelTimeoutHandler: Removing callbacks");
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    /**
     * Check the user's role and redirect to the appropriate dashboard
     */
    private void checkUserRoleAndRedirect(FirebaseUser firebaseUser) {
        Log.d(TAG, "checkUserRoleAndRedirect: Checking role for user " + firebaseUser.getUid());

        try {
            FirebaseUtil.getUserDocument(firebaseUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        try {
                            Log.d(TAG, "checkUserRoleAndRedirect: Document retrieved successfully");

                            if (documentSnapshot.exists()) {
                                User user = documentSnapshot.toObject(User.class);
                                if (user != null) {
                                    Log.d(TAG, "checkUserRoleAndRedirect: User role = " + user.getRole() +
                                            ", status = " + user.getStatus());
                                    navigateToDashboard(user);
                                } else {
                                    Log.e(TAG, "checkUserRoleAndRedirect: Failed to parse user data");
                                    showErrorAndNavigateToLogin("Failed to retrieve user information. Please try again.");
                                }
                            } else {
                                // User document doesn't exist in Firestore
                                Log.e(TAG, "checkUserRoleAndRedirect: User document does not exist for " + firebaseUser.getUid());
                                showErrorAndNavigateToLogin("User profile not found. Please log in again.");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing user document: " + e.getMessage(), e);
                            showErrorAndNavigateToLogin("Error processing user data: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "checkUserRoleAndRedirect: Error getting user document", e);
                        showErrorAndNavigateToLogin("Failed to retrieve user information: " + e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in checkUserRoleAndRedirect: " + e.getMessage(), e);
            showErrorAndNavigateToLogin("Failed to verify your account: " + e.getMessage());
        }
    }

    /**
     * Navigate to the appropriate dashboard based on user role
     */
    private void navigateToDashboard(User user) {
        Intent intent;

        try {
            // Check if doctor is approved
            if (user.isDoctor() && !user.isApproved()) {
                // Doctor is not approved yet
                Log.d(TAG, "navigateToDashboard: Doctor not approved yet");
                Toast.makeText(this, "Your account is pending approval. Please try again later.",
                        Toast.LENGTH_LONG).show();

                // Sign out the user
                FirebaseUtil.signOut();
                navigateToLogin();
                return;
            }

            // Redirect based on role
            if (user.isPatient()) {
                Log.d(TAG, "navigateToDashboard: Navigating to PatientDashboardActivity");
                intent = new Intent(MainActivity.this, PatientDashboardActivity.class);
            } else if (user.isDoctor()) {
                Log.d(TAG, "navigateToDashboard: Navigating to DoctorDashboardActivity");
                intent = new Intent(MainActivity.this, DoctorDashboardActivity.class);
            } else if (user.isAdmin()) {
                Log.d(TAG, "navigateToDashboard: Navigating to AdminDashboardActivity");
                intent = new Intent(MainActivity.this, AdminDashboardActivity.class);
            } else {
                // Unknown role, redirect to login
                Log.e(TAG, "navigateToDashboard: Unknown role: " + user.getRole());
                showErrorAndNavigateToLogin("Invalid user role. Please contact support.");
                return;
            }

            // Pass user data to next activity by individual properties
            // instead of trying to serialize the entire User object
            intent.putExtra("USER_ID", user.getUid());
            intent.putExtra("USER_EMAIL", user.getEmail());
            intent.putExtra("USER_NAME", user.getFullName());
            intent.putExtra("USER_ROLE", user.getRole());
            intent.putExtra("USER_STATUS", user.getStatus());

            // Include doctor-specific fields if relevant
            if (user.isDoctor()) {
                intent.putExtra("USER_SPECIALIZATION", user.getSpecialization());
                intent.putExtra("USER_DEPARTMENT", user.getDepartment());
                intent.putExtra("USER_EXPERIENCE", user.getYearsOfExperience());
            }

            // Start activity and finish this one
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error in navigateToDashboard: " + e.getMessage(), e);
            showErrorAndNavigateToLogin("Error loading dashboard: " + e.getMessage());
        }
    }

    /**
     * Show error message and navigate to the login screen
     */
    private void showErrorAndNavigateToLogin(String errorMessage) {
        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        navigateToLogin();
    }

    /**
     * Navigate to the login screen
     */
    private void navigateToLogin() {
        try {
            Log.d(TAG, "navigateToLogin: Redirecting to LoginActivity");
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to login: " + e.getMessage(), e);
            // If we can't navigate to login, restart the app
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
}