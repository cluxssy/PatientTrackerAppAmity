package com.example.patienttracker.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import com.google.firebase.firestore.SetOptions;
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
    private TextView textProfilePhone, textProfileAddress, textProfileDob;
    private TextView textProfileGender, textProfileFathersName, textProfileBloodType;
    private TextView textProfileWeight, textProfileHeight;
    private EditText editName, editPhone, editEmail;
    private EditText editAddress, editGender, editDateOfBirth, editFathersName, editBloodType, editWeight, editHeight;
    private Button buttonEdit, buttonSave, buttonCancel, buttonLogout, buttonChangePassword;
    private CircleImageView profileImage;
    private Button buttonChangePhoto;
    private LinearLayout layoutPhysical;
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

        // Initialize the detail card TextViews
        textProfilePhone = view.findViewById(R.id.text_profile_phone);
        textProfileAddress = view.findViewById(R.id.text_profile_address);
        textProfileDob = view.findViewById(R.id.text_profile_dob);
        textProfileGender = view.findViewById(R.id.text_profile_gender);
        textProfileFathersName = view.findViewById(R.id.text_profile_fathers_name);
        textProfileBloodType = view.findViewById(R.id.text_profile_blood_type);
        textProfileWeight = view.findViewById(R.id.text_profile_weight);
        textProfileHeight = view.findViewById(R.id.text_profile_height);

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

        // Initialize new profile field components
        editAddress = view.findViewById(R.id.edit_profile_address);
        editGender = view.findViewById(R.id.edit_profile_gender);
        editDateOfBirth = view.findViewById(R.id.edit_profile_date_of_birth);
        editFathersName = view.findViewById(R.id.edit_profile_fathers_name);
        editBloodType = view.findViewById(R.id.edit_profile_blood_type);
        editWeight = view.findViewById(R.id.edit_profile_weight);
        editHeight = view.findViewById(R.id.edit_profile_height);
        layoutPhysical = view.findViewById(R.id.layout_profile_physical);

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
        // Use getFullName() for consistency, fallback to getName() for backward compatibility
        String displayName = currentUser.getFullName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = currentUser.getName();
        }

        textName.setText(displayName);
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
        editName.setText(displayName);
        editEmail.setText(currentUser.getEmail());
        editPhone.setText(currentUser.getPhone());

        // Set additional profile fields
        editAddress.setText(currentUser.getAddress() != null ? currentUser.getAddress() : "");
        editGender.setText(currentUser.getGender() != null ? currentUser.getGender() : "");
        editDateOfBirth.setText(currentUser.getDateOfBirth() != null ? currentUser.getDateOfBirth() : "");
        editFathersName.setText(currentUser.getFathersName() != null ? currentUser.getFathersName() : "");
        editBloodType.setText(currentUser.getBloodType() != null ? currentUser.getBloodType() : "");

        // Set physical measurements
        if (currentUser.getWeight() > 0) {
            editWeight.setText(String.valueOf(currentUser.getWeight()));
        } else {
            editWeight.setText("");
        }

        if (currentUser.getHeight() > 0) {
            editHeight.setText(String.valueOf(currentUser.getHeight()));
        } else {
            editHeight.setText("");
        }

        // Load profile image if available
        if (currentUser.getPhotoUrl() != null && !currentUser.getPhotoUrl().isEmpty()) {
            Picasso.get().load(currentUser.getPhotoUrl())
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(profileImage);
        }

        // Populate the detailed info card
        textProfilePhone.setText(currentUser.getPhone() != null && !currentUser.getPhone().isEmpty()
                ? currentUser.getPhone() : "Not provided");

        textProfileAddress.setText(currentUser.getAddress() != null && !currentUser.getAddress().isEmpty()
                ? currentUser.getAddress() : "Not provided");

        textProfileDob.setText(currentUser.getDateOfBirth() != null && !currentUser.getDateOfBirth().isEmpty()
                ? currentUser.getDateOfBirth() : "Not provided");

        textProfileGender.setText(currentUser.getGender() != null && !currentUser.getGender().isEmpty()
                ? currentUser.getGender() : "Not provided");

        textProfileFathersName.setText(currentUser.getFathersName() != null && !currentUser.getFathersName().isEmpty()
                ? currentUser.getFathersName() : "Not provided");

        textProfileBloodType.setText(currentUser.getBloodType() != null && !currentUser.getBloodType().isEmpty()
                ? currentUser.getBloodType() : "Not provided");

        // Format weight with units if available
        if (currentUser.getWeight() > 0) {
            textProfileWeight.setText(String.format("%.1f kg", currentUser.getWeight()));
        } else {
            textProfileWeight.setText("Not provided");
        }

        // Format height with units if available
        if (currentUser.getHeight() > 0) {
            textProfileHeight.setText(String.format("%.1f cm", currentUser.getHeight()));
        } else {
            textProfileHeight.setText("Not provided");
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

            // Show additional fields in edit mode
            editAddress.setVisibility(View.VISIBLE);
            editGender.setVisibility(View.VISIBLE);
            editDateOfBirth.setVisibility(View.VISIBLE);
            editFathersName.setVisibility(View.VISIBLE);
            editBloodType.setVisibility(View.VISIBLE);
            layoutPhysical.setVisibility(View.VISIBLE);

            // Set click listeners for special fields
            editGender.setFocusable(false);
            editGender.setClickable(true);
            editGender.setOnClickListener(v -> showGenderSelectionDialog());

            editDateOfBirth.setFocusable(false);
            editDateOfBirth.setClickable(true);
            editDateOfBirth.setOnClickListener(v -> showDatePickerDialog());

            editBloodType.setFocusable(false);
            editBloodType.setClickable(true);
            editBloodType.setOnClickListener(v -> showBloodTypeSelectionDialog());

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

            // Hide additional fields in view mode
            editAddress.setVisibility(View.GONE);
            editGender.setVisibility(View.GONE);
            editDateOfBirth.setVisibility(View.GONE);
            editFathersName.setVisibility(View.GONE);
            editBloodType.setVisibility(View.GONE);
            layoutPhysical.setVisibility(View.GONE);

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
            // Use getFullName() for consistency, fallback to getName() for backward compatibility
            String displayName = currentUser.getFullName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = currentUser.getName();
            }

            editName.setText(displayName);
            editEmail.setText(currentUser.getEmail());
            editPhone.setText(currentUser.getPhone());

            // Reset additional profile fields
            editAddress.setText(currentUser.getAddress() != null ? currentUser.getAddress() : "");
            editGender.setText(currentUser.getGender() != null ? currentUser.getGender() : "");
            editDateOfBirth.setText(currentUser.getDateOfBirth() != null ? currentUser.getDateOfBirth() : "");
            editFathersName.setText(currentUser.getFathersName() != null ? currentUser.getFathersName() : "");
            editBloodType.setText(currentUser.getBloodType() != null ? currentUser.getBloodType() : "");

            // Reset physical measurements
            if (currentUser.getWeight() > 0) {
                editWeight.setText(String.valueOf(currentUser.getWeight()));
            } else {
                editWeight.setText("");
            }

            if (currentUser.getHeight() > 0) {
                editHeight.setText(String.valueOf(currentUser.getHeight()));
            } else {
                editHeight.setText("");
            }

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

            // Get additional profile fields
            String newAddress = editAddress.getText().toString().trim();
            String newGender = editGender.getText().toString().trim();
            String newDateOfBirth = editDateOfBirth.getText().toString().trim();
            String newFathersName = editFathersName.getText().toString().trim();
            String newBloodType = editBloodType.getText().toString().trim();

            // Get physical measurements
            float newWeight = 0f;
            float newHeight = 0f;

            try {
                if (!editWeight.getText().toString().isEmpty()) {
                    newWeight = Float.parseFloat(editWeight.getText().toString());
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid weight format", Toast.LENGTH_SHORT).show();
            }

            try {
                if (!editHeight.getText().toString().isEmpty()) {
                    newHeight = Float.parseFloat(editHeight.getText().toString());
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid height format", Toast.LENGTH_SHORT).show();
            }

            // Validate inputs
            if (newName.isEmpty()) {
                Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(getContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate date of birth if provided
            if (!newDateOfBirth.isEmpty()) {
                if (!isValidDateFormat(newDateOfBirth)) {
                    Toast.makeText(getContext(), "Date of birth should be in MM/DD/YYYY format", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Update current user with new values
            currentUser.setAddress(newAddress);
            currentUser.setGender(newGender);
            currentUser.setDateOfBirth(newDateOfBirth);
            currentUser.setFathersName(newFathersName);
            currentUser.setBloodType(newBloodType);
            currentUser.setWeight(newWeight);
            currentUser.setHeight(newHeight);

            // Calculate and set age if date of birth provided
            if (!newDateOfBirth.isEmpty()) {
                int calculatedAge = currentUser.calculateAge();
                currentUser.setAge(calculatedAge);
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
                    ", Address=" + newAddress +
                    ", Gender=" + newGender +
                    ", DateOfBirth=" + newDateOfBirth +
                    ", BloodType=" + newBloodType +
                    ", Weight=" + newWeight +
                    ", Height=" + newHeight +
                    ", PhotoUrl=" + (selectedImageUri != null ? "New image selected" : (currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl() : "null")));
        }
    }

    /**
     * Validates if a string is in MM/DD/YYYY format
     */
    private boolean isValidDateFormat(String dateStr) {
        try {
            String[] parts = dateStr.split("/");
            if (parts.length != 3) return false;

            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);

            return month >= 1 && month <= 12 &&
                    day >= 1 && day <= 31 &&
                    year >= 1900 && year <= 2100;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Show date picker dialog for selecting date of birth
     */
    private void showDatePickerDialog() {
        if (getContext() == null) return;

        // Parse existing date if available
        int year = Calendar.getInstance().get(Calendar.YEAR);
        int month = Calendar.getInstance().get(Calendar.MONTH);
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        // Try to parse existing date
        String currentDate = editDateOfBirth.getText().toString();
        if (!currentDate.isEmpty() && isValidDateFormat(currentDate)) {
            try {
                String[] parts = currentDate.split("/");
                month = Integer.parseInt(parts[0]) - 1; // Month is 0-indexed in Calendar
                day = Integer.parseInt(parts[1]);
                year = Integer.parseInt(parts[2]);
            } catch (Exception ignored) {
                // Use default date if parsing fails
            }
        }

        // Create and show DatePickerDialog
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                getContext(),
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        // Format date as MM/DD/YYYY
                        String formattedDate = String.format(Locale.US, "%02d/%02d/%04d",
                                (monthOfYear + 1), dayOfMonth, year);
                        editDateOfBirth.setText(formattedDate);
                    }
                }, year, month, day);

        // Set max date to today
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

        // Show the dialog
        datePickerDialog.show();
    }

    /**
     * Show gender selection dialog
     */
    private void showGenderSelectionDialog() {
        if (getContext() == null) return;

        // Define gender options
        final String[] genders = {"Male", "Female", "Other", "Prefer not to say"};

        // Find current selection if any
        int selectedIndex = -1;
        String currentGender = editGender.getText().toString();
        for (int i = 0; i < genders.length; i++) {
            if (genders[i].equalsIgnoreCase(currentGender)) {
                selectedIndex = i;
                break;
            }
        }

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Gender");
        builder.setSingleChoiceItems(genders, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Set the selected gender
                editGender.setText(genders[which]);
                dialog.dismiss();
            }
        });

        // Add cancel button
        builder.setNegativeButton("Cancel", null);

        // Show dialog
        builder.show();
    }

    /**
     * Show blood type selection dialog
     */
    private void showBloodTypeSelectionDialog() {
        if (getContext() == null) return;

        // Define blood type options
        final String[] bloodTypes = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown"};

        // Find current selection if any
        int selectedIndex = -1;
        String currentBloodType = editBloodType.getText().toString();
        for (int i = 0; i < bloodTypes.length; i++) {
            if (bloodTypes[i].equalsIgnoreCase(currentBloodType)) {
                selectedIndex = i;
                break;
            }
        }

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Blood Type");
        builder.setSingleChoiceItems(bloodTypes, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Set the selected blood type
                editBloodType.setText(bloodTypes[which]);
                dialog.dismiss();
            }
        });

        // Add cancel button
        builder.setNegativeButton("Cancel", null);

        // Show dialog
        builder.show();
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
        final String encryptedPhone;
        if (newPhone != null && !newPhone.isEmpty()) {
            encryptedPhone = EncryptionUtil.encryptData(newPhone);
        } else {
            encryptedPhone = null;
        }

        // Update UI first
        textName.setText(newName);
        textEmail.setText(newEmail);

        // Update detail card TextViews
        textProfilePhone.setText(newPhone != null && !newPhone.isEmpty() ? newPhone : "Not provided");

        String address = currentUser.getAddress();
        textProfileAddress.setText(address != null && !address.isEmpty() ? address : "Not provided");

        String dob = currentUser.getDateOfBirth();
        textProfileDob.setText(dob != null && !dob.isEmpty() ? dob : "Not provided");

        String gender = currentUser.getGender();
        textProfileGender.setText(gender != null && !gender.isEmpty() ? gender : "Not provided");

        String fathersName = currentUser.getFathersName();
        textProfileFathersName.setText(fathersName != null && !fathersName.isEmpty() ? fathersName : "Not provided");

        String bloodType = currentUser.getBloodType();
        textProfileBloodType.setText(bloodType != null && !bloodType.isEmpty() ? bloodType : "Not provided");

        // Format weight with units if available
        if (currentUser.getWeight() > 0) {
            textProfileWeight.setText(String.format("%.1f kg", currentUser.getWeight()));
        } else {
            textProfileWeight.setText("Not provided");
        }

        // Format height with units if available
        if (currentUser.getHeight() > 0) {
            textProfileHeight.setText(String.format("%.1f cm", currentUser.getHeight()));
        } else {
            textProfileHeight.setText("Not provided");
        }

        // Update user object
        currentUser.setFullName(newName); // Use setFullName to ensure consistency
        currentUser.setEmail(newEmail);
        currentUser.setPhone(newPhone); // Store unencrypted in memory
        if (photoUrl != null) {
            currentUser.setPhotoUrl(photoUrl);
        }

        // Note: Other fields like address, gender, dateOfBirth, etc. were already updated in saveProfileChanges()

        // Log for debugging
        Logger.getLogger("ProfileFragment").info("Updating user profile for ID: " + currentUser.getId());

        // Create final references to the parameters for use in lambda expressions
        final String finalNewName = newName;
        final String finalNewEmail = newEmail;
        final String finalPhotoUrl = photoUrl;

        // Use the new method to update both Firestore and Realtime Database
        FirebaseUtil.saveUserToAllDatabases(currentUser)
                .addOnSuccessListener(aVoid -> {
                    // Show success message
                    Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    Logger.getLogger("ProfileFragment").info("User profile updated successfully in all databases");

                    // Also update the user profile in Firebase Auth
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(finalNewName)
                                .setPhotoUri(finalPhotoUrl != null ? Uri.parse(finalPhotoUrl) : null)
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
                    Logger.getLogger("ProfileFragment").severe("Error updating user profile: " + e.getMessage());

                    // Fall back to legacy method if the new one fails
                    updateProfileLegacyMethod(finalNewName, finalNewEmail, encryptedPhone, finalPhotoUrl);
                });

        // Also update in Firestore for consistency with login process
        // Create a separate map for Firestore to match field names with User model properties
        Map<String, Object> firestoreUpdates = new HashMap<>();
        firestoreUpdates.put("uid", currentUser.getId());  // Use uid which is the @DocumentId field in User model
        firestoreUpdates.put("fullName", finalNewName);
        firestoreUpdates.put("email", finalNewEmail);
        firestoreUpdates.put("phone", encryptedPhone);
        firestoreUpdates.put("address", currentUser.getAddress());
        firestoreUpdates.put("gender", currentUser.getGender());
        firestoreUpdates.put("dateOfBirth", currentUser.getDateOfBirth());
        firestoreUpdates.put("fathersName", currentUser.getFathersName());
        firestoreUpdates.put("bloodType", currentUser.getBloodType());
        firestoreUpdates.put("weight", currentUser.getWeight());
        firestoreUpdates.put("height", currentUser.getHeight());
        firestoreUpdates.put("age", currentUser.getAge());
        firestoreUpdates.put("role", currentUser.getRole());
        firestoreUpdates.put("status", currentUser.getStatus());

        if (finalPhotoUrl != null) {
            firestoreUpdates.put("photoUrl", finalPhotoUrl);
        }

        // Add timestamp for last update
        firestoreUpdates.put("lastUpdated", com.google.firebase.Timestamp.now());

        // Log what we're sending to Firestore for debugging purposes
        Log.d("ProfileFragment", "Firestore update data: " + firestoreUpdates.toString());

        FirebaseUtil.getUserDocument(currentUser.getId())
                .set(firestoreUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d("ProfileFragment", "Firestore updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileFragment", "Error updating Firestore", e);
                    // Log the user object for debugging
                    Log.e("ProfileFragment", "User object: " + currentUser.toString());
                });
    }

    /**
     * Legacy method for updating profile in case the new method fails
     * This method only updates the Firebase Realtime Database
     */
    private void updateProfileLegacyMethod(String newName, String newEmail, String encryptedPhone, String photoUrl) {
        // Build updates map
        DatabaseReference userRef = usersRef.child(currentUser.getId());

        // Create a map for the updates to do a single transaction
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", newName); // Use fullName for consistency
        updates.put("name", newName);     // Also store as name for backward compatibility
        updates.put("email", newEmail);
        updates.put("phone", encryptedPhone); // Store encrypted in database

        // Add the additional profile fields to the updates
        updates.put("address", currentUser.getAddress());
        updates.put("gender", currentUser.getGender());
        updates.put("dateOfBirth", currentUser.getDateOfBirth());
        updates.put("fathersName", currentUser.getFathersName());
        updates.put("bloodType", currentUser.getBloodType());
        updates.put("weight", currentUser.getWeight());
        updates.put("height", currentUser.getHeight());
        updates.put("age", currentUser.getAge());

        if (photoUrl != null) {
            updates.put("profileImageUrl", photoUrl); // Store as profileImageUrl for consistency
            updates.put("photoUrl", photoUrl); // Also store as photoUrl for compatibility
        }

        // Update all fields in a single transaction
        userRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // Show success message
                    Toast.makeText(getContext(), "Profile updated in Realtime Database", Toast.LENGTH_SHORT).show();
                    Logger.getLogger("ProfileFragment").info("Firebase Realtime Database updated with legacy method");
                })
                .addOnFailureListener(e -> {
                    // Show error message
                    Toast.makeText(getContext(), "Failed to update profile in all databases: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    Logger.getLogger("ProfileFragment").severe("Error updating Firebase database with legacy method: " + e.getMessage());
                });
    }
}