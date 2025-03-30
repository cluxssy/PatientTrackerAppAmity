package com.example.patienttracker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.patienttracker.R;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Activity for user login
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView registerTextView;
    private TextView forgotPasswordTextView;
    private ProgressBar progressIndicator;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize views
        emailInput = findViewById(R.id.et_email);
        passwordInput = findViewById(R.id.et_password);
        loginButton = findViewById(R.id.btn_login);
        registerTextView = findViewById(R.id.tv_register);
        forgotPasswordTextView = findViewById(R.id.tv_forgot_password);
        progressIndicator = findViewById(R.id.progressBar);

        try {
            // Initialize Firebase Auth
            auth = FirebaseUtil.getFirebaseAuth();

            // Set up click listeners
            loginButton.setOnClickListener(v -> attemptLogin());
            registerTextView.setOnClickListener(v -> navigateToRegister());
            forgotPasswordTextView.setOnClickListener(v -> handleForgotPassword());
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            // Disable login button if we can't initialize auth
            loginButton.setEnabled(false);
        }
    }

    /**
     * Attempt to log in with provided credentials
     */
    private void attemptLogin() {
        try {
            // Reset errors
            emailInput.setError(null);
            passwordInput.setError(null);

            // Get values
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            // Validate inputs
            if (TextUtils.isEmpty(email)) {
                emailInput.setError("Email is required");
                emailInput.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                passwordInput.setError("Password is required");
                passwordInput.requestFocus();
                return;
            }

            // Show progress indicator and disable login button
            showProgressIndicator(true);

            // Sign in with Firebase
            auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser firebaseUser = authResult.getUser();
                        if (firebaseUser != null) {
                            // First, check if the user account is active in Firestore
                            FirebaseUtil.isUserAccountActive(firebaseUser.getUid())
                                    .addOnSuccessListener(isActive -> {
                                        if (isActive) {
                                            // Only proceed to check role if account is active
                                            checkUserRoleAndRedirect(firebaseUser);
                                        } else {
                                            // Account is not active (status is pending or rejected)
                                            showProgressIndicator(false);
                                            Toast.makeText(LoginActivity.this,
                                                    "Your account has been deactivated or is pending approval. Please contact administrator.",
                                                    Toast.LENGTH_LONG).show();
                                            // Sign out the user
                                            FirebaseUtil.signOut();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        // Error checking account status, proceed with caution
                                        Log.e(TAG, "Error checking user account status", e);
                                        checkUserRoleAndRedirect(firebaseUser);
                                    });
                        } else {
                            showProgressIndicator(false);
                            Toast.makeText(LoginActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "signInWithEmail:failure", e);
                        showProgressIndicator(false);

                        // Handle specific Firebase Auth error codes
                        String errorMessage;
                        if (e.getMessage() != null && e.getMessage().contains("no user record")) {
                            errorMessage = "No account found with this email";
                        } else if (e.getMessage() != null && e.getMessage().contains("password is invalid")) {
                            errorMessage = "Invalid password";
                        } else if (e.getMessage() != null && e.getMessage().contains("network error")) {
                            errorMessage = "Network error. Please check your connection";
                        } else {
                            errorMessage = "Authentication failed: " + e.getMessage();
                        }

                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in attemptLogin: " + e.getMessage(), e);
            showProgressIndicator(false);
            Toast.makeText(this, "Error during login: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check user's role in Firestore and redirect to appropriate dashboard
     */
    private void checkUserRoleAndRedirect(FirebaseUser firebaseUser) {
        try {
            FirebaseUtil.getUserDocument(firebaseUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        try {
                            if (documentSnapshot.exists()) {
                                User user = documentSnapshot.toObject(User.class);
                                if (user != null) {
                                    navigateToDashboard(user);
                                } else {
                                    showProgressIndicator(false);
                                    Toast.makeText(LoginActivity.this, "Failed to retrieve user information",
                                            Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                showProgressIndicator(false);
                                Toast.makeText(LoginActivity.this, "User account not found",
                                        Toast.LENGTH_SHORT).show();
                                FirebaseUtil.signOut();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing user document: " + e.getMessage(), e);
                            showProgressIndicator(false);
                            Toast.makeText(LoginActivity.this, "Error processing user data", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting user document", e);
                        showProgressIndicator(false);
                        Toast.makeText(LoginActivity.this, "Error retrieving user information",
                                Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in checkUserRoleAndRedirect: " + e.getMessage(), e);
            showProgressIndicator(false);
            Toast.makeText(this, "Error retrieving user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Navigate to the appropriate dashboard based on user role
     */
    private void navigateToDashboard(User user) {
        Intent intent;

        try {
            // For testing purposes: Temporarily bypass the approval check for doctors
            // This will allow us to test the doctor dashboard even if the account is not approved
            // REMOVE THIS BYPASS IN PRODUCTION

            // Original code (commented out for testing):

            // Check if user account is active (not pending or rejected)
            if (!user.isActive()) {
                showProgressIndicator(false);
                String statusMessage;

                if (user.isPending()) {
                    statusMessage = "Your account is pending approval. Please try again later.";
                } else if (user.isRejected()) {
                    statusMessage = "Your account has been deactivated. Please contact administrator.";
                } else {
                    statusMessage = "Your account status is invalid. Please contact support.";
                }

                Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show();

                // Sign out the user
                FirebaseUtil.signOut();
                return;
            }


            // Navigate based on role
            if (user.isPatient()) {
                intent = new Intent(LoginActivity.this, PatientDashboardActivity.class);
            } else if (user.isDoctor()) {
                intent = new Intent(LoginActivity.this, DoctorDashboardActivity.class);
            } else if (user.isAdmin()) {
                intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
            } else {
                showProgressIndicator(false);
                Toast.makeText(this, "Invalid user role. Please contact support.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Pass individual user properties instead of serializing the User object
            // This avoids serialization issues with Timestamp fields
            intent.putExtra("USER_ID", user.getId());
            intent.putExtra("USER_EMAIL", user.getEmail());
            intent.putExtra("USER_NAME", user.getFullName());
            intent.putExtra("USER_ROLE", user.getRole());
            intent.putExtra("USER_STATUS", user.getStatus());

            Log.d(TAG, "navigateToDashboard: Navigating to " + intent.getComponent().getClassName());
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error in navigateToDashboard: " + e.getMessage(), e);
            showProgressIndicator(false);
            Toast.makeText(this, "Error navigating to dashboard: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Navigate to registration activity
     */
    private void navigateToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
    }

    /**
     * Handle forgot password
     */
    private void handleForgotPassword() {
        try {
            String email = emailInput.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                emailInput.setError("Please enter your email address");
                emailInput.requestFocus();
                return;
            }

            showProgressIndicator(true);

            auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(aVoid -> {
                        showProgressIndicator(false);
                        Toast.makeText(LoginActivity.this,
                                "Password reset email sent to " + email,
                                Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        showProgressIndicator(false);
                        Log.e(TAG, "Error sending password reset email", e);
                        Toast.makeText(LoginActivity.this,
                                "Failed to send password reset email: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in handleForgotPassword: " + e.getMessage(), e);
            showProgressIndicator(false);
            Toast.makeText(this, "Error processing request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show or hide progress indicator
     */
    private void showProgressIndicator(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!show);
        registerTextView.setEnabled(!show);
        forgotPasswordTextView.setEnabled(!show);
    }
}