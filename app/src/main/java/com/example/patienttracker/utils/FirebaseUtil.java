package com.example.patienttracker.utils;

import android.net.Uri;
import android.util.Log;

import com.example.patienttracker.models.Notification;
import com.example.patienttracker.models.User;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.Timestamp;

/**
 * Utility class for Firebase services
 */
public class FirebaseUtil {

    private static final String TAG = "FirebaseUtil";
    private static final String USERS_COLLECTION = "users";
    private static final String NOTIFICATIONS_COLLECTION = "notifications";

    private static FirebaseAuth auth;
    private static FirebaseFirestore firestore;
    private static FirebaseStorage storage;
    private static FirebaseDatabase database;

    // Firebase Authentication
    public static FirebaseAuth getAuth() {
        try {
            if (auth == null) {
                auth = FirebaseAuth.getInstance();
            }
            return auth;
        } catch (Exception e) {
            Log.e(TAG, "Error getting FirebaseAuth: " + e.getMessage(), e);
            return FirebaseAuth.getInstance(); // Try again with a fresh instance
        }
    }

    // Firebase Firestore
    public static FirebaseFirestore getFirestore() {
        try {
            if (firestore == null) {
                firestore = FirebaseFirestore.getInstance();
            }
            return firestore;
        } catch (Exception e) {
            Log.e(TAG, "Error getting FirebaseFirestore: " + e.getMessage(), e);
            return FirebaseFirestore.getInstance(); // Try again with a fresh instance
        }
    }

    // Shorthand for getFirestore() so both patterns can be used
    public static FirebaseFirestore db() {
        return getFirestore();
    }

    // Firebase Storage
    public static FirebaseStorage getStorage() {
        try {
            if (storage == null) {
                storage = FirebaseStorage.getInstance();
            }
            return storage;
        } catch (Exception e) {
            Log.e(TAG, "Error getting FirebaseStorage: " + e.getMessage(), e);
            return FirebaseStorage.getInstance(); // Try again with a fresh instance
        }
    }

    // Alias for getStorage() to maintain backward compatibility
    public static FirebaseStorage getFirebaseStorage() {
        return getStorage();
    }

    // Get current user from Firebase Auth
    public static FirebaseUser getCurrentUser() {
        try {
            return getAuth().getCurrentUser();
        } catch (Exception e) {
            Log.e(TAG, "Error getting current user: " + e.getMessage(), e);
            return null;
        }
    }

    // Get current user ID
    public static String getCurrentUserId() {
        try {
            FirebaseUser user = getCurrentUser();
            return (user != null) ? user.getUid() : null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current user ID: " + e.getMessage(), e);
            return null;
        }
    }

    // Get reference to current user document
    public static DocumentReference getCurrentUserDocument() {
        try {
            String uid = getCurrentUserId();
            if (uid != null) {
                return getFirestore().collection(USERS_COLLECTION).document(uid);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current user document: " + e.getMessage(), e);
            return null;
        }
    }

    // Get reference to user profile image in storage
    public static StorageReference getUserProfileImageRef(String userId) {
        try {
            if (userId == null) {
                Log.e(TAG, "getUserProfileImageRef: userId is null");
                return null;
            }
            return getStorage().getReference()
                    .child("profile_images")
                    .child(userId + ".jpg");
        } catch (Exception e) {
            Log.e(TAG, "Error getting user profile image ref: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Detach all Firestore listeners before sign out to prevent permission errors
     * This should be called before signing out or when permission errors occur
     */
    public static void detachFirestoreListeners() {
        // Each fragment or activity should remove its own listeners in onPause() or onDestroyView()
        // This is a fallback to reset the Firestore instance when needed
        Log.d(TAG, "Detaching Firestore listeners and resetting connection");

        if (firestore != null) {
            try {
                // Force clear cached instances to destroy any lingering listeners
                // Note: This is a somewhat aggressive approach and should be a last resort
                firestore = null;
                Log.d(TAG, "Firestore instance reset for clean reconnection");
            } catch (Exception e) {
                Log.e(TAG, "Error resetting Firestore: " + e.getMessage(), e);
            }
        }
    }

    // Sign out current user
    public static void signOut() {
        try {
            // Log before signing out for debugging
            Log.d(TAG, "Signing out user...");

            // Clean up any lingering Firestore listeners
            // (This is just a safeguard - fragments should handle their own listeners)
            detachFirestoreListeners();

            // Sign out the user
            getAuth().signOut();

            Log.d(TAG, "User signed out successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error signing out: " + e.getMessage(), e);
        }
    }

    // Query methods for admin fragments

    // Get all pending doctor approval requests
    public static Query getPendingDoctorsQuery() {
        try {
            // Option 1: Remove the orderBy to avoid requiring a compound index
            return getFirestore().collection(USERS_COLLECTION)
                    .whereEqualTo("role", User.ROLE_DOCTOR)
                    .whereEqualTo("status", User.STATUS_PENDING);

            // Option 2 (commented out): This requires a Firestore compound index to be created
            // The index URL is shown in the error message
            /*
            return getFirestore().collection(USERS_COLLECTION)
                    .whereEqualTo("role", User.ROLE_DOCTOR)
                    .whereEqualTo("status", User.STATUS_PENDING)
                    .orderBy("registeredAt", Query.Direction.DESCENDING);
            */
        } catch (Exception e) {
            Log.e(TAG, "Error getting pending doctors query: " + e.getMessage(), e);
            return getFirestore().collection(USERS_COLLECTION).limit(0); // Empty query
        }
    }

    // Get all users
    public static Query getAllUsersQuery() {
        try {
            return getFirestore().collection(USERS_COLLECTION)
                    .orderBy("registeredAt", Query.Direction.DESCENDING);
        } catch (Exception e) {
            Log.e(TAG, "Error getting all users query: " + e.getMessage(), e);
            return getFirestore().collection(USERS_COLLECTION).limit(0); // Empty query
        }
    }

    // Get users by role
    public static Query getUsersByRoleQuery(int role) {
        try {
            // This query also requires a Firestore index, but we'll keep it simple for now
            // Just filtering by role without sorting to avoid index requirement
            return getFirestore().collection(USERS_COLLECTION)
                    .whereEqualTo("role", role);

            // This version requires a compound index (commented out):
            /*
            return getFirestore().collection(USERS_COLLECTION)
                    .whereEqualTo("role", role)
                    .orderBy("registeredAt", Query.Direction.DESCENDING);
            */
        } catch (Exception e) {
            Log.e(TAG, "Error getting users by role query: " + e.getMessage(), e);
            return getFirestore().collection(USERS_COLLECTION).limit(0); // Empty query
        }
    }

    // Get notifications for current user
    public static Query getNotificationsForCurrentUserQuery() {
        try {
            String userId = getCurrentUserId();
            if (userId != null) {
                // This version won't require a compound index:
                return getFirestore().collection(NOTIFICATIONS_COLLECTION)
                        .whereArrayContains("recipientUids", userId);

                // This version requires a compound index (commented out):
                /*
                return getFirestore().collection(NOTIFICATIONS_COLLECTION)
                        .whereArrayContains("recipientUids", userId)
                        .orderBy("timestamp", Query.Direction.DESCENDING);
                */
            }
            return getFirestore().collection(NOTIFICATIONS_COLLECTION).limit(0); // Empty query
        } catch (Exception e) {
            Log.e(TAG, "Error getting notifications for current user query: " + e.getMessage(), e);
            return getFirestore().collection(NOTIFICATIONS_COLLECTION).limit(0); // Empty query
        }
    }

    // Get notifications by target type (role-based)
    public static Query getNotificationsByTargetTypeQuery(int targetType) {
        try {
            // This version won't require a compound index:
            return getFirestore().collection(NOTIFICATIONS_COLLECTION)
                    .whereEqualTo("targetType", targetType);

            // This version requires a compound index (commented out):
            /*
            return getFirestore().collection(NOTIFICATIONS_COLLECTION)
                    .whereEqualTo("targetType", targetType)
                    .orderBy("timestamp", Query.Direction.DESCENDING);
            */
        } catch (Exception e) {
            Log.e(TAG, "Error getting notifications by target type query: " + e.getMessage(), e);
            return getFirestore().collection(NOTIFICATIONS_COLLECTION).limit(0); // Empty query
        }
    }

    // Alias for getAuth() to maintain backward compatibility
    public static FirebaseAuth getFirebaseAuth() {
        return getAuth();
    }

    // Get a document reference for a specific user
    public static DocumentReference getUserDocument(String userId) {
        try {
            if (userId == null) {
                Log.e(TAG, "getUserDocument: userId is null");
                return null;
            }
            return getFirestore().collection(USERS_COLLECTION).document(userId);
        } catch (Exception e) {
            Log.e(TAG, "Error getting user document: " + e.getMessage(), e);
            return null;
        }
    }

    // Get a reference to the Firebase Realtime Database
    public static DatabaseReference getDatabaseReference() {
        try {
            if (database == null) {
                database = FirebaseDatabase.getInstance();
            }
            return database.getReference();
        } catch (Exception e) {
            Log.e(TAG, "Error getting database reference: " + e.getMessage(), e);
            try {
                return FirebaseDatabase.getInstance().getReference();
            } catch (Exception e2) {
                Log.e(TAG, "Fatal error getting database reference: " + e2.getMessage(), e2);
                return null;
            }
        }
    }

    // Save user to Firestore
    public static Task<Void> saveUser(User user) {
        try {
            if (user == null || user.getUid() == null) {
                Log.e(TAG, "saveUser: user or user.uid is null");
                throw new IllegalArgumentException("User or user ID cannot be null");
            }

            // Create a copy of the user with encrypted sensitive data
            Map<String, Object> userData = new HashMap<>();
            userData.put("uid", user.getUid());
            userData.put("email", user.getEmail());
            userData.put("fullName", user.getFullName());
            userData.put("role", user.getRole());
            userData.put("status", user.getStatus());

            // Encrypt phone number before saving to database
            if (user.getPhone() != null) {
                String encryptedPhone = com.example.patienttracker.utils.EncryptionUtil.encryptData(user.getPhone());
                userData.put("phone", encryptedPhone);
            }

            // Add other user fields
            if (user.getAddress() != null) userData.put("address", user.getAddress());
            if (user.getPhotoUrl() != null) userData.put("photoUrl", user.getPhotoUrl());
            if (user.getGender() != null) userData.put("gender", user.getGender());
            if (user.getDateOfBirth() != null) userData.put("dateOfBirth", user.getDateOfBirth());
            if (user.getFathersName() != null) userData.put("fathersName", user.getFathersName());
            if (user.getBloodType() != null) userData.put("bloodType", user.getBloodType());
            if (user.getWeight() > 0) userData.put("weight", user.getWeight());
            if (user.getHeight() > 0) userData.put("height", user.getHeight());

            // Add doctor-specific fields if present
            if (user.getSpecialization() != null) userData.put("specialization", user.getSpecialization());
            if (user.getDepartment() != null) userData.put("department", user.getDepartment());
            if (user.getYearsOfExperience() > 0) userData.put("yearsOfExperience", user.getYearsOfExperience());

            return getFirestore().collection(USERS_COLLECTION)
                    .document(user.getUid())
                    .set(userData);
        } catch (Exception e) {
            Log.e(TAG, "Error saving user: " + e.getMessage(), e);
            // Return a failed task using Tasks utility class
            return Tasks.forException(e);
        }
    }

    /**
     * Save user data to both Firestore and Realtime Database
     * This ensures data consistency across both databases
     * @param user The user object to save
     * @return A task that completes when both operations are done
     */
    public static Task<Void> saveUserToAllDatabases(User user) {
        try {
            if (user == null || user.getUid() == null) {
                Log.e(TAG, "saveUserToAllDatabases: user or user.uid is null");
                throw new IllegalArgumentException("User or user ID cannot be null");
            }

            // Save to Firestore first using our saveUser method which handles encryption
            Task<Void> firestoreTask = saveUser(user);

            // Save to Realtime Database as well
            DatabaseReference userRef = getDatabaseReference().child("users").child(user.getUid());
            Map<String, Object> updates = new HashMap<>();

            // Add all User fields to the updates map
            updates.put("uid", user.getUid());
            updates.put("email", user.getEmail());
            updates.put("fullName", user.getFullName());
            updates.put("name", user.getFullName()); // For backward compatibility
            updates.put("role", user.getRole());
            updates.put("status", user.getStatus());

            // Add profile-specific fields
            // Encrypt phone number before saving to database
            if (user.getPhone() != null) {
                String encryptedPhone = com.example.patienttracker.utils.EncryptionUtil.encryptData(user.getPhone());
                updates.put("phone", encryptedPhone);
            }
            if (user.getAddress() != null) updates.put("address", user.getAddress());
            if (user.getGender() != null) updates.put("gender", user.getGender());
            if (user.getDateOfBirth() != null) updates.put("dateOfBirth", user.getDateOfBirth());
            if (user.getFathersName() != null) updates.put("fathersName", user.getFathersName());
            if (user.getBloodType() != null) updates.put("bloodType", user.getBloodType());
            if (user.getWeight() > 0) updates.put("weight", user.getWeight());
            if (user.getHeight() > 0) updates.put("height", user.getHeight());
            if (user.getAge() > 0) updates.put("age", user.getAge());

            // Handle profile image URLs (use both field names for compatibility)
            if (user.getPhotoUrl() != null) {
                updates.put("photoUrl", user.getPhotoUrl());
                updates.put("profileImageUrl", user.getPhotoUrl());
            }

            // Perform the update
            Task<Void> realtimeDbTask = userRef.updateChildren(updates);

            // Return a task that completes when both operations complete
            return Tasks.whenAll(firestoreTask, realtimeDbTask);

        } catch (Exception e) {
            Log.e(TAG, "Error in saveUserToAllDatabases: " + e.getMessage(), e);
            // Return a failed task
            return Tasks.forException(e);
        }
    }

    // Get reference to profile images in storage
    public static StorageReference getProfileImagesReference() {
        try {
            return getStorage().getReference().child("profile_images");
        } catch (Exception e) {
            Log.e(TAG, "Error getting profile images reference: " + e.getMessage(), e);
            return null;
        }
    }

    // Upload a profile image for a user
    public static void uploadProfileImage(String userId, Uri imageUri, OnSuccessListener<Uri> onSuccessListener) {
        try {
            if (userId == null || imageUri == null) {
                Log.e(TAG, "uploadProfileImage: userId or imageUri is null");
                return;
            }

            StorageReference userProfileRef = getUserProfileImageRef(userId);
            if (userProfileRef == null) {
                Log.e(TAG, "uploadProfileImage: userProfileRef is null");
                return;
            }

            userProfileRef.putFile(imageUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful() && task.getException() != null) {
                            throw task.getException();
                        }
                        return userProfileRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(onSuccessListener)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error uploading profile image", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in uploadProfileImage: " + e.getMessage(), e);
        }
    }

    // Send a notification to a specific user
    public static void sendNotification(String userId, String title, String message, OnSuccessListener<Void> onSuccessListener) {
        try {
            if (userId == null || title == null || message == null) {
                Log.e(TAG, "sendNotification: userId, title, or message is null");
                return;
            }

            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setRecipientUids(Collections.singletonList(userId));
            notification.setCreatedAt(Timestamp.now());
            notification.setRead(false);

            getFirestore().collection(NOTIFICATIONS_COLLECTION)
                    .add(notification)
                    .addOnSuccessListener(documentReference -> {
                        if (onSuccessListener != null) {
                            onSuccessListener.onSuccess(null);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error sending notification", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in sendNotification: " + e.getMessage(), e);
        }
    }

    // Check if a user is active
    public static Task<Boolean> isUserAccountActive(String userId) {
        try {
            if (userId == null) {
                Log.e(TAG, "isUserAccountActive: userId is null");
                return Tasks.forException(new IllegalArgumentException("User ID cannot be null"));
            }

            return getFirestore().collection(USERS_COLLECTION)
                    .document(userId)
                    .get()
                    .continueWith(task -> {
                        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                            Log.e(TAG, "User document does not exist or task failed: " + userId);
                            // Default to false if we can't get the user document
                            return false;
                        }

                        // Get the status field from the document
                        Long status = task.getResult().getLong("status");
                        if (status == null) {
                            Log.e(TAG, "User status is null: " + userId);
                            return false;
                        }

                        // Check if status is ACTIVE
                        boolean isActive = status.intValue() == User.STATUS_ACTIVE;
                        Log.d(TAG, "User " + userId + " active status: " + isActive);
                        return isActive;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in isUserAccountActive: " + e.getMessage(), e);
            return Tasks.forException(e);
        }
    }
}