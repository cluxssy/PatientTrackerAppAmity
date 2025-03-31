package com.example.patienttracker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.patienttracker.R;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Activity for user registration.
 * This activity allows users to create a new account with email, password,
 * name, and role selection (patient, doctor, admin).
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    // UI components
    private TextInputLayout nameLayout;
    private TextInputEditText nameEditText;
    private TextInputLayout emailLayout;
    private TextInputEditText emailEditText;
    private TextInputLayout passwordLayout;
    private TextInputEditText passwordEditText;
    private RadioGroup roleRadioGroup;
    private Button registerButton;
    private TextView loginTextView;
    private ProgressBar progressBar;

    // Firebase Auth
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase Auth
        auth = FirebaseUtil.getFirebaseAuth();

        // Initialize UI components
        initializeUI();

        // Set up click listeners
        setupClickListeners();
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        nameLayout = findViewById(R.id.til_name);
        nameEditText = findViewById(R.id.et_name);
        emailLayout = findViewById(R.id.til_email);
        emailEditText = findViewById(R.id.et_email);
        passwordLayout = findViewById(R.id.til_password);
        passwordEditText = findViewById(R.id.et_password);
        roleRadioGroup = findViewById(R.id.rg_role);
        registerButton = findViewById(R.id.btn_register);
        loginTextView = findViewById(R.id.tv_login);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * Set up click listeners for buttons and text views
     */
    private void setupClickListeners() {
        // Register button click listener
        registerButton.setOnClickListener(v -> register());

        // Login text view click listener
        loginTextView.setOnClickListener(v -> navigateToLogin());
    }

    /**
     * Attempt to register a new user with the provided information
     */
    private void register() {
        // Get input values
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Get selected role
        int selectedRoleId = roleRadioGroup.getCheckedRadioButtonId();
        if (selectedRoleId == -1) {
            Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRoleButton = findViewById(selectedRoleId);
        int roleConstant = getUserRole(selectedRoleButton.getText().toString());
        Log.d(TAG, "Selected role: " + selectedRoleButton.getText() + ", Role constant: " + roleConstant);

        // Validate input
        if (!validateInput(name, email, password)) {
            return;
        }

        // Show progress bar
        progressBar.setVisibility(View.VISIBLE);

        // Disable register button
        registerButton.setEnabled(false);

        // Create user with email and password
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Registration successful, create user in Firestore
                        FirebaseUser firebaseUser = auth.getCurrentUser();
                        if (firebaseUser != null) {
                            createUserInFirestore(firebaseUser.getUid(), email, name, roleConstant);
                        }
                    } else {
                        // Registration failed, show error message
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());

                        // Hide progress bar
                        progressBar.setVisibility(View.GONE);

                        // Enable register button
                        registerButton.setEnabled(true);

                        // Get the exception message to handle specific error cases
                        String errorMessage = getString(R.string.registration_failed);
                        if (task.getException() != null) {
                            String exceptionMessage = task.getException().getMessage();
                            Log.d(TAG, "Error message: " + exceptionMessage);

                            // Check for provider disabled error
                            if (exceptionMessage != null && exceptionMessage.contains("sign-in provider is disabled")) {
                                errorMessage = "Email sign-in is currently disabled. Please verify your Firebase authentication settings or contact support.";
                            }
                        }

                        // Show error message
                        Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Create a user document in Firestore
     */
    private void createUserInFirestore(String uid, String email, String fullName, int role) {
        // Create User object
        User user = new User(uid, email, fullName, role);

        // Save user to Firestore
        FirebaseUtil.saveUser(user)
                .addOnCompleteListener(task -> {
                    // Hide progress bar
                    progressBar.setVisibility(View.GONE);

                    // Enable register button
                    registerButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        // User created successfully
                        Log.d(TAG, "User document added to Firestore");

                        // Show success message
                        Toast.makeText(RegisterActivity.this, getString(R.string.registration_success),
                                Toast.LENGTH_SHORT).show();

                        // Sign out the user so they can log in
                        FirebaseUtil.signOut();

                        // Navigate to login
                        navigateToLogin();
                    } else {
                        // Failed to create user document
                        Log.w(TAG, "Error adding user document", task.getException());

                        // Show error message
                        Toast.makeText(RegisterActivity.this, getString(R.string.registration_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Get the user role as an integer constant based on the selected role text
     */
    private int getUserRole(String roleText) {
        if (roleText.equals(getString(R.string.role_patient))) {
            return User.ROLE_PATIENT;
        } else if (roleText.equals(getString(R.string.role_doctor))) {
            return User.ROLE_DOCTOR;
        } else if (roleText.equals(getString(R.string.role_admin))) {
            return User.ROLE_ADMIN;
        } else {
            return User.ROLE_PATIENT; // Default to patient
        }
    }

    /**
     * This method is kept for backward compatibility
     * @deprecated Use getUserRole directly
     */
    @Deprecated
    private int mapRoleToConstant(String role) {
        try {
            // Try to parse the role string as an integer
            return Integer.parseInt(role);
        } catch (NumberFormatException e) {
            // If parsing fails, fall back to the original logic
            Log.d(TAG, "Error parsing role string: " + role + ", falling back to default mapping");
            if (role.equals(getString(R.string.role_patient))) {
                return User.ROLE_PATIENT;
            } else if (role.equals(getString(R.string.role_doctor))) {
                return User.ROLE_DOCTOR;
            } else if (role.equals(getString(R.string.role_admin))) {
                return User.ROLE_ADMIN;
            } else {
                return User.ROLE_PATIENT; // Default to patient
            }
        }
    }

    /**
     * Validate the input fields
     */
    private boolean validateInput(String name, String email, String password) {
        boolean isValid = true;

        // Check if name is empty
        if (name.isEmpty()) {
            nameLayout.setError("Name is required");
            isValid = false;
        } else {
            nameLayout.setError(null);
        }

        // Check if email is empty
        if (email.isEmpty()) {
            emailLayout.setError("Email is required");
            isValid = false;
        } else {
            emailLayout.setError(null);
        }

        // Check if password is empty
        if (password.isEmpty()) {
            passwordLayout.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            passwordLayout.setError("Password must be at least 6 characters");
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }

        return isValid;
    }

    /**
     * Navigate to the login activity
     */
    private void navigateToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Finish this activity
    }
}