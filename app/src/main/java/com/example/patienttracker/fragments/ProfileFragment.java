package com.example.patienttracker.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import java.util.logging.Level;
import java.util.logging.Logger;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.patienttracker.R;
import com.example.patienttracker.activities.LoginActivity;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.EncryptionUtil;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import com.squareup.picasso.Picasso;
import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Fragment to display and manage user profile.
 * All users can view and update their profile information.
 */
public class ProfileFragment extends Fragment {

    private User currentUser;
    private TextView textName, textEmail, textRole;
    private EditText editName, editPhone, editEmail;
    private Button buttonEdit, buttonSave, buttonCancel, buttonLogout, buttonChangePassword;
    private CircleImageView profileImage;
    private Button buttonChangePhoto;
    private boolean isEditing = false;
    private DatabaseReference usersRef;
    private StorageReference storageRef;
    private Uri selectedImageUri;

    // Activity result launcher for picking images
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public ProfileFragment() {
        // Required empty constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize activity result launcher for image picking
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            // Update profile image preview
                            Picasso.get().load(selectedImageUri).placeholder(R.drawable.ic_profile).into(profileImage);
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Get current user (will be passed from parent activity)
        if (getArguments() != null) {
            currentUser = (User) getArguments().getSerializable("USER");
        }

        // Initialize Firebase references
        usersRef = FirebaseUtil.getDatabaseReference().child("users");
        // Use the new dedicated method for getting profile images storage reference
        storageRef = FirebaseUtil.getProfileImagesReference();

        // Initialize UI components
        textName = view.findViewById(R.id.text_profile_name);
        textEmail = view.findViewById(R.id.text_profile_email);
        textRole = view.findViewById(R.id.text_profile_role);

        editName = view.findViewById(R.id.edit_profile_name);
        editPhone = view.findViewById(R.id.edit_profile_phone);
        editEmail = view.findViewById(R.id.edit_profile_email);

        buttonEdit = view.findViewById(R.id.button_edit_profile);
        buttonSave = view.findViewById(R.id.button_save_profile);
        buttonCancel = view.findViewById(R.id.button_cancel_edit);

        // Initialize new UI components
        profileImage = view.findViewById(R.id.image_profile);
        buttonLogout = view.findViewById(R.id.button_logout);
        buttonChangePassword = view.findViewById(R.id.button_change_password);

        // Add button for changing photo
        buttonChangePhoto = view.findViewById(R.id.button_change_photo);

        // Set initial button visibility
        buttonEdit.setVisibility(View.VISIBLE);
        buttonSave.setVisibility(View.GONE);
        buttonCancel.setVisibility(View.GONE);

        // Set up click listeners
        buttonEdit.setOnClickListener(v -> {
            // Enter edit mode
            isEditing = true;
            updateUIState();
        });

        buttonSave.setOnClickListener(v -> {
            // Save changes
            saveProfileChanges();
            isEditing = false;
            updateUIState();
        });

        buttonCancel.setOnClickListener(v -> {
            // Cancel edit mode
            isEditing = false;
            updateUIState();
            resetFormFields();
        });

        // Add click listener for logout button
        buttonLogout.setOnClickListener(v -> {
            logoutUser();
        });

        // Add click listener for change password button
        buttonChangePassword.setOnClickListener(v -> {
            showChangePasswordDialog();
        });

        // Add click listener for change photo button
        buttonChangePhoto.setOnClickListener(v -> {
            openImagePicker();
        });

        // Add click listener for profile image
        profileImage.setOnClickListener(v -> {
            openImagePicker();
        });

        // Load profile data
        if (currentUser != null) {
            loadProfileData();
        } else {
            Toast.makeText(getContext(), "User information not available", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    /**
     * Open image picker for selecting profile photo
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    /**
     * Logout user and navigate to login screen
     */
    private void logoutUser() {
        // Use FirebaseUtil.signOut() which has proper error handling
        FirebaseUtil.signOut();
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * Load user profile data
     */
    private void loadProfileData() {
        textName.setText(currentUser.getName());
        textEmail.setText(currentUser.getEmail());

        // Set role text
        String role = "Unknown";
        if (currentUser.isAdmin()) {
            role = "Administrator";
        } else if (currentUser.isDoctor()) {
            role = "Doctor";
        } else if (currentUser.isPatient()) {
            role = "Patient";
        }
        textRole.setText(role);

        // Set edit text fields
        editName.setText(currentUser.getName());
        editEmail.setText(currentUser.getEmail());
        editPhone.setText(currentUser.getPhone());

        // Load profile image if available
        if (currentUser.getPhotoUrl() != null && !currentUser.getPhotoUrl().isEmpty()) {
            Picasso.get().load(currentUser.getPhotoUrl())
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(profileImage);
        }

        // Update UI state
        updateUIState();
    }

    /**
     * Update UI state based on edit mode
     */
    private void updateUIState() {
        if (isEditing) {
            // Edit mode
            textName.setVisibility(View.GONE);
            textEmail.setVisibility(View.GONE);
            editName.setVisibility(View.VISIBLE);
            editEmail.setVisibility(View.VISIBLE);
            editPhone.setVisibility(View.VISIBLE);
            buttonChangePhoto.setVisibility(View.VISIBLE);

            buttonEdit.setVisibility(View.GONE);
            buttonSave.setVisibility(View.VISIBLE);
            buttonCancel.setVisibility(View.VISIBLE);
        } else {
            // View mode
            textName.setVisibility(View.VISIBLE);
            textEmail.setVisibility(View.VISIBLE);
            editName.setVisibility(View.GONE);
            editEmail.setVisibility(View.GONE);
            editPhone.setVisibility(View.GONE);
            buttonChangePhoto.setVisibility(View.GONE);

            buttonEdit.setVisibility(View.VISIBLE);
            buttonSave.setVisibility(View.GONE);
            buttonCancel.setVisibility(View.GONE);
        }
    }

    /**
     * Reset form fields to current user data
     */
    private void resetFormFields() {
        if (currentUser != null) {
            editName.setText(currentUser.getName());
            editEmail.setText(currentUser.getEmail());
            editPhone.setText(currentUser.getPhone());

            // Reset selected image if any
            selectedImageUri = null;
            if (currentUser.getPhotoUrl() != null && !currentUser.getPhotoUrl().isEmpty()) {
                Picasso.get().load(currentUser.getPhotoUrl())
                        .placeholder(R.drawable.ic_profile)
                        .into(profileImage);
            } else {
                profileImage.setImageResource(R.drawable.ic_profile);
            }
        }
    }

    /**
     * Save profile changes to Firebase
     */
    private void saveProfileChanges() {
        if (currentUser != null) {
            String newName = editName.getText().toString().trim();
            String newEmail = editEmail.getText().toString().trim();
            String newPhone = editPhone.getText().toString().trim();

            // Validate inputs
            if (newName.isEmpty()) {
                Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(getContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show progress indication
            Toast.makeText(getContext(), "Updating profile...", Toast.LENGTH_SHORT).show();

            // Check if email has changed
            boolean emailChanged = !newEmail.equals(currentUser.getEmail());

            // If email changed, update it first in Firebase Authentication
            if (emailChanged) {
                updateEmail(newEmail, newName, newPhone);
            } else {
                // Upload image first if selected
                if (selectedImageUri != null) {
                    uploadProfileImage(newName, newEmail, newPhone);
                } else {
                    // Just update user info without changing image
                    updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
                }
            }

            // Log the update attempt for debugging
            Logger.getLogger("ProfileFragment").info("Saving profile changes: " +
                    "Name=" + newName +
                    ", Email=" + newEmail +
                    ", Phone=" + newPhone +
                    ", PhotoUrl=" + (selectedImageUri != null ? "New image selected" : (currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl() : "null")));
        }
    }

    /**
     * Upload profile image to Firebase Storage with enhanced error handling and diagnostics
     */
    private void uploadProfileImage(String newName, String newEmail, String newPhone) {
        if (selectedImageUri == null || getContext() == null) {
            Log.w("ProfileFragment", "Cannot upload: selectedImageUri is null or context is null");
            // Fall back to updating user info without image
            updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
            return;
        }

        // Add Firebase initialization check
        if (FirebaseUtil.getFirebaseStorage() == null) {
            Log.e("ProfileFragment", "Firebase Storage not initialized");
            Toast.makeText(getContext(), "Firebase Storage not properly initialized", Toast.LENGTH_LONG).show();
            updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
            return;
        }

        Log.d("ProfileFragment", "Starting image upload process");
        Log.d("ProfileFragment", "User ID: " + currentUser.getId());
        Log.d("ProfileFragment", "Selected image URI: " + selectedImageUri.toString());

        // Check if current user is authenticated
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e("ProfileFragment", "User not authenticated");
            Toast.makeText(getContext(), "You must be logged in to upload profile images", Toast.LENGTH_LONG).show();
            updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
            return;
        }

        // First try a direct upload to test Firebase Storage
        try {
            // Show a progress dialog
            Toast.makeText(getContext(), "Uploading profile image...", Toast.LENGTH_SHORT).show();

            // Try direct storage reference first to diagnose issues
            StorageReference directRef = FirebaseStorage.getInstance().getReference()
                    .child("test_uploads/" + System.currentTimeMillis() + ".jpg");

            Log.d("ProfileFragment", "Testing direct upload to: " + directRef.getPath());

            directRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d("ProfileFragment", "Direct upload test SUCCESS");

                        // Now try the actual profile upload
                        FirebaseUtil.uploadProfileImage(currentUser.getId(), selectedImageUri, uri -> {
                            if (uri != null) {
                                // Success - we got back a download URL
                                String imageUrl = uri.toString();
                                Log.d("ProfileFragment", "Profile upload successful, image URL: " + imageUrl);
                                // Update user info with new image URL
                                updateUserInfo(newName, newEmail, newPhone, imageUrl);
                            } else {
                                // Failed to upload or get URL
                                Log.e("ProfileFragment", "Failed to upload profile image or get download URL");
                                Toast.makeText(getContext(), "Failed to upload profile image", Toast.LENGTH_LONG).show();
                                // Still update other user info
                                updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e("ProfileFragment", "Direct upload test FAILED: " + e.getMessage(), e);
                        // Show detailed error message
                        String errorMsg = "Firebase Storage error: " + e.getMessage();
                        Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();

                        // Check if this is a permissions issue
                        if (e.getMessage() != null && e.getMessage().contains("not authorized")) {
                            Log.e("ProfileFragment", "This appears to be a Firebase Storage Rules issue");
                            Toast.makeText(getContext(), "Storage permission denied - check Firebase rules", Toast.LENGTH_LONG).show();
                        }

                        // Still update other user info
                        updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
                    });
        } catch (Exception e) {
            Log.e("ProfileFragment", "Exception during direct upload test: " + e.getMessage(), e);
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
        }
    }

    /**
     * Update email in Firebase Authentication
     */
    private void updateEmail(String newEmail, String newName, String newPhone) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Update email in Firebase Authentication
            user.updateEmail(newEmail)
                    .addOnSuccessListener(aVoid -> {
                        // After email is updated, update the database
                        if (selectedImageUri != null) {
                            uploadProfileImage(newName, newEmail, newPhone);
                        } else {
                            updateUserInfo(newName, newEmail, newPhone, currentUser.getPhotoUrl());
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Show error message
                        Toast.makeText(getContext(), "Failed to update email: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();

                        // If this is a credentials error, need to reauthenticate
                        if (e.getMessage() != null && e.getMessage().contains("requires recent authentication")) {
                            Toast.makeText(getContext(), "Please log out and log in again to change your email",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    /**
     * Show password change dialog
     */
    private void showChangePasswordDialog() {
        if (getContext() == null) return;

        // Create AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Change Password");

        // Set up the layout
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        // Current password
        EditText currentPassword = new EditText(getContext());
        currentPassword.setHint("Current Password");
        currentPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(currentPassword);

        // Space
        Space space = new Space(getContext());
        space.setMinimumHeight(20);
        layout.addView(space);

        // New password
        EditText newPassword = new EditText(getContext());
        newPassword.setHint("New Password");
        newPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newPassword);

        // Space
        Space space2 = new Space(getContext());
        space2.setMinimumHeight(20);
        layout.addView(space2);

        // Confirm new password
        EditText confirmPassword = new EditText(getContext());
        confirmPassword.setHint("Confirm New Password");
        confirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmPassword);

        builder.setView(layout);

        // Set up the buttons
        builder.setPositiveButton("Change", (dialog, which) -> {
            String currentPwd = currentPassword.getText().toString().trim();
            String newPwd = newPassword.getText().toString().trim();
            String confirmPwd = confirmPassword.getText().toString().trim();

            // Validate inputs
            if (currentPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
                Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPwd.length() < 6) {
                Toast.makeText(getContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPwd.equals(confirmPwd)) {
                Toast.makeText(getContext(), "New passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            // Change password
            changePassword(currentPwd, newPwd);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * Change user password
     */
    private void changePassword(String currentPassword, String newPassword) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            // Re-authenticate user
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

            user.reauthenticate(credential)
                    .addOnSuccessListener(aVoid -> {
                        // User re-authenticated, now update password
                        user.updatePassword(newPassword)
                                .addOnSuccessListener(aVoid2 -> {
                                    Toast.makeText(getContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(getContext(), "Failed to update password: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Authentication failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        }
    }

    /**
     * Update user information in Firebase
     */
    private void updateUserInfo(String newName, String newEmail, String newPhone, String photoUrl) {
        // Encrypt phone if not empty
        String encryptedPhone = null;
        if (newPhone != null && !newPhone.isEmpty()) {
            encryptedPhone = EncryptionUtil.encryptData(newPhone);
        }

        // Update UI first
        textName.setText(newName);
        textEmail.setText(newEmail);

        // Update user object
        currentUser.setName(newName);
        currentUser.setEmail(newEmail);
        currentUser.setPhone(newPhone); // Store unencrypted in memory
        if (photoUrl != null) {
            currentUser.setPhotoUrl(photoUrl);
        }

        // Build updates map
        DatabaseReference userRef = usersRef.child(currentUser.getId());

        // Create a map for the updates to do a single transaction
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("email", newEmail);
        updates.put("phone", encryptedPhone); // Store encrypted in database
        if (photoUrl != null) {
            updates.put("profileImageUrl", photoUrl); // Store as profileImageUrl for consistency
            updates.put("photoUrl", photoUrl); // Also store as photoUrl for compatibility
        }

        // Update all fields in a single transaction
        userRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // Show success message
                    Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    Logger.getLogger("ProfileFragment").info("Firebase database updated successfully");

                    // Update user profile in Firebase Auth as well
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(newName)
                                .setPhotoUri(photoUrl != null ? Uri.parse(photoUrl) : null)
                                .build();

                        user.updateProfile(profileUpdates)
                                .addOnSuccessListener(aVoid2 -> {
                                    Logger.getLogger("ProfileFragment").info("Firebase Auth profile updated successfully");
                                })
                                .addOnFailureListener(e -> {
                                    Logger.getLogger("ProfileFragment").severe("Error updating Firebase Auth profile: " + e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    // Show error message
                    Toast.makeText(getContext(), "Failed to update profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Logger.getLogger("ProfileFragment").severe("Error updating Firebase database: " + e.getMessage());
                });

        // Also update in Firestore for consistency with login process
        FirebaseUtil.getUserDocument(currentUser.getId())
                .set(currentUser)
                .addOnSuccessListener(aVoid -> {
                    Log.d("ProfileFragment", "Firestore updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileFragment", "Error updating Firestore", e);
                });
    }
}