package com.example.patienttracker.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patienttracker.R;
import com.example.patienttracker.fragments.ProfileFragment;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.BadgeHelper;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;

/**
 * Main activity for doctors.
 * Provides a bottom navigation menu to access appointments,
 * patients, profile, and notifications.
 */
public class DoctorDashboardActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private static final String TAG = "DoctorDashboardActivity";

    private BottomNavigationView bottomNavigationView;
    private User currentUser;
    private ListenerRegistration notificationListener;
    private int unreadNotificationCount = 0;
    private View progressOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_dashboard);

        // Initialize progress overlay
        progressOverlay = findViewById(R.id.progress_overlay);
        if (progressOverlay == null) {
            // If progress overlay doesn't exist in the layout, log a warning
            Log.w(TAG, "Progress overlay view not found in layout");
        }

        try {
            // Get user data from intent as individual properties
            String userId = getIntent().getStringExtra("USER_ID");
            String userEmail = getIntent().getStringExtra("USER_EMAIL");
            String userName = getIntent().getStringExtra("USER_NAME");
            int userRole = getIntent().getIntExtra("USER_ROLE", -1);
            int userStatus = getIntent().getIntExtra("USER_STATUS", -1);

            // Create initial User object from individual properties
            if (userId != null && userEmail != null && userName != null && userRole == User.ROLE_DOCTOR) {
                currentUser = new User(userId, userEmail, userName, userRole);
                currentUser.setStatus(userStatus);

                // Also get doctor-specific fields from intent
                if (getIntent().hasExtra("USER_SPECIALIZATION")) {
                    currentUser.setSpecialization(getIntent().getStringExtra("USER_SPECIALIZATION"));
                }
                if (getIntent().hasExtra("USER_DEPARTMENT")) {
                    currentUser.setDepartment(getIntent().getStringExtra("USER_DEPARTMENT"));
                }
                if (getIntent().hasExtra("USER_EXPERIENCE")) {
                    currentUser.setYearsOfExperience(getIntent().getIntExtra("USER_EXPERIENCE", 0));
                }

                // Show progress indicator while fetching complete user data
                showProgressIndicator(true);

                // Fetch full user data from Firestore for comprehensive profile information
                FirebaseFirestore.getInstance().collection("users").document(userId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            showProgressIndicator(false);
                            if (documentSnapshot.exists()) {
                                Log.d(TAG, "Successfully fetched full user data from Firestore");
                                // Replace basic user with complete user data
                                User completeUser = createUserFromDocument(documentSnapshot);
                                if (completeUser != null) {
                                    currentUser = completeUser;
                                    Log.d(TAG, "Updated user with complete profile data: " + currentUser.toString());
                                }
                            } else {
                                Log.w(TAG, "User document doesn't exist for ID: " + userId);
                            }
                        })
                        .addOnFailureListener(e -> {
                            showProgressIndicator(false);
                            Log.e(TAG, "Error fetching full user data: " + e.getMessage(), e);
                        });
            } else {
                // Invalid user data or not a doctor, go back to login
                Toast.makeText(this, "Invalid user or insufficient permissions", Toast.LENGTH_LONG).show();
                FirebaseUtil.signOut();
                navigateToLogin();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            FirebaseUtil.signOut();
            navigateToLogin();
            return;
        }

        // Set up bottom navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this);

        // Set title in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name) + " - " + getString(R.string.role_doctor));
        }

        // Set selected item first, which will trigger fragment creation through listener
        bottomNavigationView.setSelectedItemId(R.id.nav_appointments);

        // Note: We don't need to manually create and load a fragment here anymore,
        // the navigation item selection listener will handle creating and loading the fragment

        // Initialize the notification badge counter
        if (currentUser != null) {
            setupNotificationCounter();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        String viewName = "";

        try {
            // Select fragment based on menu item
            int itemId = item.getItemId();
            if (itemId == R.id.nav_appointments) {
                viewName = "Appointments View";
            } else if (itemId == R.id.nav_patients) {
                viewName = "Patients View";
            } else if (itemId == R.id.nav_profile) {
                // Create a simple profile view instead of using ProfileFragment until we fix getId() errors
                viewName = "Profile View";
            } else if (itemId == R.id.nav_notifications) {
                viewName = "Notifications View";
                fragment = new NotificationsPlaceholderFragment();
            }

            // Create appropriate fragment based on navigation selection
            if (itemId == R.id.nav_appointments) {
                fragment = new AppointmentsPlaceholderFragment();
            } else if (itemId == R.id.nav_patients) {
                fragment = new PatientsPlaceholderFragment();
            } else if (itemId == R.id.nav_profile) {
                // Show loading indicator
                showProgressIndicator(true);

                // Fetch the complete user profile from Firestore before creating the fragment
                FirebaseFirestore.getInstance().collection("users")
                        .document(currentUser.getUid())
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            // Hide loading indicator
                            showProgressIndicator(false);

                            User fullUserProfile;
                            if (documentSnapshot.exists()) {
                                // Create a complete user object from the Firestore document
                                fullUserProfile = createUserFromDocument(documentSnapshot);

                                if (fullUserProfile == null) {
                                    // If document exists but mapping failed, use the current basic user object
                                    fullUserProfile = currentUser;
                                    Log.w(TAG, "Could not create user from document, using basic user info");
                                }
                            } else {
                                // If user document doesn't exist, use the current basic user object
                                fullUserProfile = currentUser;
                                Log.w(TAG, "User document not found in Firestore, using basic user info");
                            }

                            // Create the fragment with the full user profile
                            ProfileFragment profileFragment = new ProfileFragment();
                            Bundle args = new Bundle();
                            args.putSerializable("USER", fullUserProfile);
                            profileFragment.setArguments(args);

                            // Add the fragment to the layout
                            loadFragment(profileFragment);
                        })
                        .addOnFailureListener(e -> {
                            // Hide loading indicator
                            showProgressIndicator(false);

                            Log.e(TAG, "Error fetching user profile: " + e.getMessage(), e);
                            Toast.makeText(DoctorDashboardActivity.this,
                                    "Failed to load profile: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();

                            // Fall back to basic user info
                            ProfileFragment profileFragment = new ProfileFragment();
                            Bundle args = new Bundle();
                            args.putSerializable("USER", currentUser);
                            profileFragment.setArguments(args);

                            // Add the fragment to the layout
                            loadFragment(profileFragment);
                        });

                // Return false to prevent default fragment loading behavior
                // We'll load it asynchronously after fetching the full user profile
                return false;
            }
            // No need to create NotificationsPlaceholderFragment again as it's already created above

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(DoctorDashboardActivity.this,
                    "Error loading view: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    /**
     * Load a fragment into the frame layout
     */
    private void loadFragment(Fragment fragment) {
        if (fragment != null) {
            // Make sure we're using a named transaction and adding to back stack
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            // Using the fragment's class name as the transaction name ensures proper recreation
            String tag = fragment.getClass().getName();
            transaction.replace(R.id.frame_container, fragment, tag);
            // Add to backstack to help with proper state restoration
            transaction.addToBackStack(tag);
            transaction.commit();

            // If this is the notifications fragment, mark notifications as read
            if (fragment instanceof NotificationsPlaceholderFragment) {
                // Clear badge when viewing notifications
                BadgeHelper.removeBadge(bottomNavigationView, R.id.nav_notifications);
                unreadNotificationCount = 0;
            }
        }
    }

    /**
     * Setup the notification counter badge
     */
    private void setupNotificationCounter() {
        // Get the current user ID
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (userId == null) return;

        // Set up a real-time listener for unread notifications
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        notificationListener = db.collection("notifications")
                .whereArrayContains("recipientUids", userId)
                .whereEqualTo("isRead", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("DoctorDashboard", "Error listening for notifications: " + error.getMessage());
                        return;
                    }

                    if (value != null) {
                        unreadNotificationCount = value.size();
                        // Update the badge
                        BadgeHelper.showBadge(bottomNavigationView, R.id.nav_notifications, unreadNotificationCount);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove notification listener to prevent memory leaks
        if (notificationListener != null) {
            notificationListener.remove();
        }
    }

    /**
     * Navigate to login activity
     */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Handle back button press
     */
    @Override
    public void onBackPressed() {
        // If there are entries in the back stack, pop the back stack first
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        }
        // If we're not on the appointments tab, go to appointments
        else if (bottomNavigationView.getSelectedItemId() != R.id.nav_appointments) {
            bottomNavigationView.setSelectedItemId(R.id.nav_appointments);
        } else {
            // Otherwise, exit the app
            super.onBackPressed();
        }
    }

    /**
     * Updates the notification badge count on the bottom navigation bar
     * This method is called from the NotificationsPlaceholderFragment
     * @param count The number of unread notifications
     */
    public void updateNotificationBadge(int count) {
        if (count != unreadNotificationCount) {
            unreadNotificationCount = count;
            // Use BadgeHelper to update the badge on the notification icon
            BadgeHelper.showBadge(bottomNavigationView, R.id.nav_notifications, unreadNotificationCount);
        }
    }

    /**
     * Helper method to show reschedule dialog from adapter
     * This acts as a bridge between the adapter and the fragment
     */
    public void showRescheduleDialogFromAdapter(Map<String, Object> appointment, String appointmentId, String patientId) {
        // Find the appointments fragment and call its showRescheduleDialog method
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.frame_container);
        if (fragment != null) {
            // If we have a fragment container, check if it contains an appointment fragment
            if (fragment instanceof AppointmentsPlaceholderFragment) {
                ((AppointmentsPlaceholderFragment) fragment).showRescheduleDialog(appointment, appointmentId, patientId);
                return;
            }
        }

        // If we can't find the fragment directly, look for it in the current fragments
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : fragments) {
            if (f instanceof AppointmentsPlaceholderFragment) {
                ((AppointmentsPlaceholderFragment) f).showRescheduleDialog(appointment, appointmentId, patientId);
                return;
            }
        }

        // If we still can't find it, create a new instance of the fragment and show dialog
        AppointmentsPlaceholderFragment newFragment = new AppointmentsPlaceholderFragment();
        newFragment.showRescheduleDialog(appointment, appointmentId, patientId);
    }

    /**
     * Static Fragment class for Appointments view
     */
    public static class AppointmentsPlaceholderFragment extends Fragment {
        private RecyclerView recyclerView;
        private TextView emptyView;
        private List<Map<String, Object>> appointmentsList = new ArrayList<>();
        private FirebaseFirestore db;
        private String currentUserId;

        // Static import instead of class reference
        private static final String LOG_TAG = "AppointmentsFragment";

        // Method to send a notification about appointment changes
        private void sendAppointmentNotification(String patientId, String title, String message, String appointmentId) {
            if (patientId == null || patientId.isEmpty()) {
                Toast.makeText(getContext(), "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
                return;
            }

            android.util.Log.d(LOG_TAG, "Sending notification to patient ID: " + patientId);

            // Create a notification in Firestore with proper structure
            Map<String, Object> notification = new HashMap<>();
            notification.put("userId", patientId);
            notification.put("title", title);
            notification.put("message", message);
            notification.put("timestamp", com.google.firebase.Timestamp.now());
            notification.put("isRead", false);
            notification.put("type", "appointment");
            notification.put("appointmentId", appointmentId);

            // Additional fields to match structure of other notifications
            List<String> recipientUids = new ArrayList<>();
            recipientUids.add(patientId);
            notification.put("recipientUids", recipientUids);
            notification.put("targetType", 3); // Specific users (matches format in ApprovalsFragment)

            // Get current doctor info to add as sender
            String doctorId = currentUserId;
            db.collection("users").document(doctorId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        // Add sender information
                        String doctorName = "Doctor";

                        if (documentSnapshot.exists()) {
                            // Get doctor name, checking both fields since the field usage is inconsistent
                            if (documentSnapshot.contains("fullName")) {
                                doctorName = documentSnapshot.getString("fullName");
                            } else if (documentSnapshot.contains("name")) {
                                doctorName = documentSnapshot.getString("name");
                            }

                            notification.put("senderUid", doctorId);
                            notification.put("senderName", doctorName);

                            // Add to notifications collection
                            db.collection("notifications")
                                    .add(notification)
                                    .addOnSuccessListener(documentReference -> {
                                        android.util.Log.d(LOG_TAG, "Notification sent successfully with ID: " + documentReference.getId());
                                        Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        android.util.Log.e(LOG_TAG, "Failed to send notification: " + e.getMessage(), e);
                                        Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            android.util.Log.e(LOG_TAG, "Doctor document not found");

                            // If doctor document not found, still send notification but without sender info
                            db.collection("notifications")
                                    .add(notification)
                                    .addOnSuccessListener(documentReference -> {
                                        Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e(LOG_TAG, "Failed to get doctor info: " + e.getMessage(), e);

                        // If getting doctor info fails, still send notification without that info
                        db.collection("notifications")
                                .add(notification)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(notifError -> {
                                    Toast.makeText(getContext(), "Failed to send notification: " + notifError.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                });
                    });
        }

        // Method to show date/time picker dialog for rescheduling
        public void showRescheduleDialog(Map<String, Object> appointment, String appointmentId, String patientId) {
            // Get current date as default
            final Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            // Show date picker
            DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(),
                    (datePicker, selectedYear, selectedMonth, selectedDay) -> {
                        // When date is set, show time picker
                        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                                (timePicker, selectedHour, selectedMinute) -> {
                                    // When time is set, update the appointment
                                    Calendar newDateTime = Calendar.getInstance();
                                    newDateTime.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute);

                                    // Create a Firestore timestamp
                                    com.google.firebase.Timestamp newTimestamp =
                                            new com.google.firebase.Timestamp(new Date(newDateTime.getTimeInMillis()));

                                    // Update Firestore with new date/time
                                    db.collection("appointments").document(appointmentId)
                                            .update("date", newTimestamp)
                                            .addOnSuccessListener(aVoid -> {
                                                // Format the new date for display
                                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                                                String formattedDate = sdf.format(newDateTime.getTime());

                                                // Update local data
                                                appointment.put("date", newTimestamp);

                                                // Send notification to patient
                                                String message = "Your appointment has been rescheduled to " + formattedDate;
                                                sendAppointmentNotification(patientId, "Appointment Rescheduled", message, appointmentId);

                                                Toast.makeText(getContext(), "Appointment rescheduled successfully",
                                                        Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(getContext(), "Failed to reschedule: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                }, hour, minute, DateFormat.is24HourFormat(getContext()));
                        timePickerDialog.setTitle("Select Time");
                        timePickerDialog.show();
                    }, year, month, day);

            datePickerDialog.setTitle("Select Date");
            datePickerDialog.show();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Create the main layout
            View view = inflater.inflate(android.R.layout.simple_list_item_1, container, false);

            // Replace with our custom layout
            LinearLayout mainLayout = new LinearLayout(getContext());
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);

            // Add a title
            TextView titleText = new TextView(getContext());
            titleText.setText("My Appointments");
            titleText.setTextSize(22);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 0, 0, 30);
            mainLayout.addView(titleText);

            // Add empty state text view
            emptyView = new TextView(getContext());
            emptyView.setText("Loading appointments...");
            emptyView.setTextSize(16);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, 100, 0, 0);

            // Create recycler view for appointments
            recyclerView = new RecyclerView(getContext());
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setHasFixedSize(true);

            mainLayout.addView(recyclerView);
            mainLayout.addView(emptyView);

            // Initialize Firestore
            db = FirebaseFirestore.getInstance();

            // Get the current user ID from Firebase Auth
            currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

            // Load appointments
            if (currentUserId != null) {
                loadAppointments(currentUserId);
            } else {
                emptyView.setText("Error: User not authenticated");
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            }

            return mainLayout;
        }

        private void loadAppointments(String doctorId) {
            // Query Firestore for appointments where this doctor is assigned
            try {
                // Option 1: Using the query that requires a composite index (for production)
                // You need to create this index in the Firebase console
                // Fields indexed: doctorId (Ascending) and date (Descending)
                // If you see an error with a link to create the index, click that link
                /*
                db.collection("appointments")
                        .whereEqualTo("doctorId", doctorId)
                        .orderBy("date", Query.Direction.DESCENDING)
                        .get()
                */

                // Option 2: Temporary workaround until index is created
                // Get all appointments for this doctor without sorting
                db.collection("appointments")
                        .whereEqualTo("doctorId", doctorId)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            appointmentsList.clear();

                            if (queryDocumentSnapshots.isEmpty()) {
                                // No appointments found
                                emptyView.setText("No appointments found");
                                recyclerView.setVisibility(View.GONE);
                                emptyView.setVisibility(View.VISIBLE);
                            } else {
                                // Process appointments (excluding cancelled ones)
                                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                    Map<String, Object> appointment = document.getData();
                                    String status = appointment.get("status") != null ? appointment.get("status").toString().toLowerCase() : "";

                                    // Skip cancelled appointments
                                    if (!status.equals("cancelled") && !status.equals("canceled")) {
                                        appointment.put("id", document.getId());
                                        appointmentsList.add(appointment);
                                    }
                                }

                                // Sort appointments by date locally (descending order)
                                // Note: This is less efficient than server-side sorting but works without index
                                appointmentsList.sort((a, b) -> {
                                    // Extract date fields for comparison
                                    try {
                                        com.google.firebase.Timestamp timeA = (com.google.firebase.Timestamp) a.get("date");
                                        com.google.firebase.Timestamp timeB = (com.google.firebase.Timestamp) b.get("date");

                                        if (timeA != null && timeB != null) {
                                            // Sort in descending order (newest first)
                                            return timeB.compareTo(timeA);
                                        }
                                    } catch (Exception e) {
                                        // If date comparison fails, don't change order
                                    }
                                    return 0;
                                });

                                // Set up the adapter with the appointments
                                AppointmentsAdapter adapter = new AppointmentsAdapter(appointmentsList);
                                recyclerView.setAdapter(adapter);

                                if (appointmentsList.isEmpty()) {
                                    // If we filtered out all appointments, show empty state
                                    emptyView.setText("No active appointments found");
                                    recyclerView.setVisibility(View.GONE);
                                    emptyView.setVisibility(View.VISIBLE);
                                } else {
                                    recyclerView.setVisibility(View.VISIBLE);
                                    emptyView.setVisibility(View.GONE);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            // Handle errors
                            emptyView.setText("Error loading appointments: " + e.getMessage());
                            recyclerView.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                        });

            } catch (Exception ex) {
                emptyView.setText("Error executing query: " + ex.getMessage());
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            }
        }

        /**
         * Adapter for displaying appointments in a RecyclerView
         */
        private class AppointmentsAdapter extends RecyclerView.Adapter<AppointmentsAdapter.AppointmentViewHolder> {
            private List<Map<String, Object>> appointmentsList;

            public AppointmentsAdapter(List<Map<String, Object>> appointmentsList) {
                this.appointmentsList = appointmentsList;
            }

            @NonNull
            @Override
            public AppointmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                // Create a card for each appointment
                CardView cardView = new CardView(parent.getContext());
                cardView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                cardView.setRadius(16);
                cardView.setElevation(8);
                cardView.setUseCompatPadding(true);
                cardView.setContentPadding(20, 20, 20, 20);

                LinearLayout layout = new LinearLayout(parent.getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                cardView.addView(layout);

                return new AppointmentViewHolder(cardView);
            }

            @Override
            public void onBindViewHolder(@NonNull AppointmentViewHolder holder, int position) {
                Map<String, Object> appointment = appointmentsList.get(position);
                holder.bind(appointment);
            }

            @Override
            public int getItemCount() {
                return appointmentsList.size();
            }

            /**
             * ViewHolder for appointment items
             */
            class AppointmentViewHolder extends RecyclerView.ViewHolder {
                private LinearLayout layout;

                public AppointmentViewHolder(@NonNull View itemView) {
                    super(itemView);
                    layout = (LinearLayout) ((CardView) itemView).getChildAt(0);
                }

                /**
                 * Send a notification to a patient
                 */
                private void sendNotificationToPatient(String patientId, String title, String message, String appointmentId) {
                    if (patientId == null || patientId.isEmpty()) {
                        Toast.makeText(getContext(), "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    android.util.Log.d("AppointmentsFragment", "Sending notification to patient ID: " + patientId);

                    // Get current doctor ID
                    String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    // Create a notification in Firestore with proper structure
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("userId", patientId);
                    notification.put("title", title);
                    notification.put("message", message);
                    notification.put("timestamp", com.google.firebase.Timestamp.now());
                    notification.put("isRead", false);
                    notification.put("type", "appointment");
                    notification.put("appointmentId", appointmentId);

                    // Additional fields to match structure of other notifications
                    List<String> recipientUids = new ArrayList<>();
                    recipientUids.add(patientId);
                    notification.put("recipientUids", recipientUids);
                    notification.put("targetType", 3); // Specific users
                    notification.put("senderUid", doctorId);

                    // Get doctor name to add as sender
                    db.collection("users").document(doctorId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                // Add sender information
                                String doctorName = "Doctor";

                                if (documentSnapshot.exists()) {
                                    // Get doctor name, checking both fields since the field usage is inconsistent
                                    if (documentSnapshot.contains("fullName")) {
                                        doctorName = documentSnapshot.getString("fullName");
                                    } else if (documentSnapshot.contains("name")) {
                                        doctorName = documentSnapshot.getString("name");
                                    }

                                    notification.put("senderName", doctorName);

                                    // Add to notifications collection
                                    db.collection("notifications")
                                            .add(notification)
                                            .addOnSuccessListener(documentReference -> {
                                                android.util.Log.d("AppointmentsFragment", "Notification sent successfully");
                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                android.util.Log.e("AppointmentsFragment", "Failed to send notification: " + e.getMessage(), e);
                                                Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                } else {
                                    // If doctor document not found, still send notification but with generic sender info
                                    db.collection("notifications")
                                            .add(notification)
                                            .addOnSuccessListener(documentReference -> {
                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                }
                            })
                            .addOnFailureListener(e -> {
                                // If getting doctor info fails, still send notification with minimal info
                                db.collection("notifications")
                                        .add(notification)
                                        .addOnSuccessListener(documentReference -> {
                                            Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                        })
                                        .addOnFailureListener(notifError -> {
                                            Toast.makeText(getContext(), "Failed to send notification: " + notifError.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        });
                            });
                }

                public void bind(Map<String, Object> appointment) {
                    layout.removeAllViews();

                    // Attempt to get patient ID
                    String patientId = appointment.containsKey("patientId") ?
                            appointment.get("patientId").toString() : "Unknown Patient";

                    // Add a title with patient name
                    TextView patientText = new TextView(layout.getContext());

                    // Try to get patient data
                    if (!patientId.equals("Unknown Patient")) {
                        // Initially set a placeholder text
                        patientText.setText("Appointment with Patient");
                        patientText.setTextSize(18);
                        patientText.setTypeface(null, android.graphics.Typeface.BOLD);

                        // Look up the patient's name
                        db.collection("users").document(patientId)
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (documentSnapshot.exists()) {
                                        String patientName = documentSnapshot.getString("fullName");
                                        if (patientName != null && !patientName.isEmpty()) {
                                            patientText.setText("Appointment with " + patientName);
                                        }
                                    }
                                });
                    } else {
                        patientText.setText("Appointment with Unknown Patient");
                        patientText.setTextSize(18);
                        patientText.setTypeface(null, android.graphics.Typeface.BOLD);
                    }

                    layout.addView(patientText);

                    // Date and time
                    String dateStr = "Date not specified";
                    if (appointment.containsKey("date")) {
                        Object dateObj = appointment.get("date");
                        if (dateObj != null) {
                            if (dateObj instanceof com.google.firebase.Timestamp) {
                                com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) dateObj;
                                Date date = timestamp.toDate();
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
                                dateStr = sdf.format(date);
                            } else {
                                dateStr = dateObj.toString();
                            }
                        }
                    } else if (appointment.containsKey("dateTime")) {
                        // Try to get from dateTime field if date is not available
                        Object dateObj = appointment.get("dateTime");
                        if (dateObj != null) {
                            if (dateObj instanceof com.google.firebase.Timestamp) {
                                com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) dateObj;
                                Date date = timestamp.toDate();
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
                                dateStr = sdf.format(date);
                            } else {
                                dateStr = dateObj.toString();
                            }
                        }
                    }

                    TextView dateText = new TextView(layout.getContext());
                    dateText.setText("Date: " + dateStr);
                    dateText.setTextSize(16);
                    dateText.setPadding(0, 10, 0, 5);
                    layout.addView(dateText);

                    // Appointment Type
                    String appointmentType = appointment.containsKey("appointmentType") ?
                            appointment.get("appointmentType").toString() :
                            (appointment.containsKey("type") ? appointment.get("type").toString() : "Regular");

                    TextView typeText = new TextView(layout.getContext());
                    typeText.setText("Type: " + appointmentType);
                    typeText.setTextSize(16);
                    typeText.setPadding(0, 5, 0, 5);
                    layout.addView(typeText);

                    // Reason for visit
                    String reason = appointment.containsKey("reason") ?
                            appointment.get("reason").toString() : "No reason specified";

                    TextView reasonText = new TextView(layout.getContext());
                    reasonText.setText("Reason: " + reason);
                    reasonText.setTextSize(16);
                    reasonText.setPadding(0, 5, 0, 5);
                    layout.addView(reasonText);

                    // Notes if available
                    if (appointment.containsKey("notes") && appointment.get("notes") != null) {
                        String notes = appointment.get("notes").toString();
                        if (!notes.isEmpty()) {
                            TextView notesText = new TextView(layout.getContext());
                            notesText.setText("Notes: " + notes);
                            notesText.setTextSize(16);
                            notesText.setPadding(0, 5, 0, 5);
                            layout.addView(notesText);
                        }
                    }

                    // Status
                    String status = appointment.containsKey("status") ?
                            appointment.get("status").toString() : "pending";

                    TextView statusText = new TextView(layout.getContext());
                    statusText.setText("Status: " + status);
                    statusText.setTextSize(16);
                    statusText.setPadding(0, 5, 0, 5);
                    if ("confirmed".equalsIgnoreCase(status) || "SCHEDULED".equalsIgnoreCase(status)) {
                        statusText.setTextColor(layout.getContext().getResources().getColor(android.R.color.holo_green_dark));
                    } else if ("cancelled".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                        statusText.setTextColor(layout.getContext().getResources().getColor(android.R.color.holo_red_dark));
                    } else {
                        statusText.setTextColor(layout.getContext().getResources().getColor(android.R.color.holo_blue_dark));
                    }
                    layout.addView(statusText);

                    // Add update buttons if the appointment is pending
                    if ("pending".equalsIgnoreCase(status)) {
                        LinearLayout buttonLayout = new LinearLayout(layout.getContext());
                        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
                        buttonLayout.setGravity(Gravity.END);
                        buttonLayout.setPadding(0, 20, 0, 0);

                        // Confirm button
                        Button confirmButton = new Button(layout.getContext());
                        confirmButton.setText("Confirm");
                        confirmButton.setTextSize(14);
                        confirmButton.setBackgroundColor(layout.getContext().getResources()
                                .getColor(android.R.color.holo_green_light));
                        confirmButton.setTextColor(layout.getContext().getResources()
                                .getColor(android.R.color.white));

                        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        confirmParams.setMargins(0, 0, 10, 0);
                        confirmButton.setLayoutParams(confirmParams);

                        // Set an OnClickListener for the button
                        confirmButton.setOnClickListener(v -> {
                            // Update the appointment status in Firestore
                            String appointmentId = appointment.get("id").toString();
                            db.collection("appointments").document(appointmentId)
                                    .update("status", "confirmed")
                                    .addOnSuccessListener(aVoid -> {
                                        // Refresh the data
                                        appointment.put("status", "confirmed");
                                        notifyItemChanged(getAdapterPosition());
                                        Toast.makeText(getContext(), "Appointment confirmed", Toast.LENGTH_SHORT).show();

                                        // Send notification to patient
                                        // Use a direct approach to send notification instead of trying to cast fragments
                                        // This avoids the ClassCastException

                                        // Get patientId and create notification directly
                                        String patientIdStr = appointment.get("patientId").toString();
                                        String title = "Appointment Confirmed";
                                        String message = "Your appointment has been confirmed by the doctor.";

                                        // Create a notification in Firestore with proper structure
                                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                                        Map<String, Object> notification = new HashMap<>();
                                        notification.put("userId", patientIdStr);
                                        notification.put("title", title);
                                        notification.put("message", message);
                                        notification.put("timestamp", com.google.firebase.Timestamp.now());
                                        notification.put("isRead", false);
                                        notification.put("type", "appointment");
                                        notification.put("appointmentId", appointmentId);

                                        // Additional fields to match structure of other notifications
                                        List<String> recipientUids = new ArrayList<>();
                                        recipientUids.add(patientIdStr);
                                        notification.put("recipientUids", recipientUids);
                                        notification.put("targetType", 3); // Specific users

                                        // Get current doctor ID
                                        String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                        notification.put("senderUid", doctorId);

                                        // Get doctor name to add as sender
                                        firestore.collection("users").document(doctorId)
                                                .get()
                                                .addOnSuccessListener(docSnapshot -> {
                                                    // Add sender information
                                                    String doctorName = "Doctor";

                                                    if (docSnapshot.exists()) {
                                                        // Get doctor name, checking both fields
                                                        if (docSnapshot.contains("fullName")) {
                                                            doctorName = docSnapshot.getString("fullName");
                                                        } else if (docSnapshot.contains("name")) {
                                                            doctorName = docSnapshot.getString("name");
                                                        }

                                                        notification.put("senderName", doctorName);
                                                    }

                                                    // Add to notifications collection
                                                    firestore.collection("notifications")
                                                            .add(notification)
                                                            .addOnSuccessListener(docRef -> {
                                                                android.util.Log.d("AppointmentsAdapter", "Notification sent successfully");
                                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                                            })
                                                            .addOnFailureListener(err -> {
                                                                android.util.Log.e("AppointmentsAdapter", "Failed to send notification: " + err.getMessage(), err);
                                                                Toast.makeText(getContext(), "Failed to send notification: " + err.getMessage(),
                                                                        Toast.LENGTH_SHORT).show();
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    // If getting doctor info fails, still send notification with minimal info
                                                    firestore.collection("notifications")
                                                            .add(notification)
                                                            .addOnSuccessListener(docRef -> {
                                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                                            })
                                                            .addOnFailureListener(err -> {
                                                                Toast.makeText(getContext(), "Failed to send notification: " + err.getMessage(),
                                                                        Toast.LENGTH_SHORT).show();
                                                            });
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), "Error updating appointment: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        });

                        // Reject button
                        Button rejectButton = new Button(layout.getContext());
                        rejectButton.setText("Cancel");
                        rejectButton.setTextSize(14);
                        rejectButton.setBackgroundColor(layout.getContext().getResources()
                                .getColor(android.R.color.holo_red_light));
                        rejectButton.setTextColor(layout.getContext().getResources()
                                .getColor(android.R.color.white));

                        // Set an OnClickListener for the button
                        rejectButton.setOnClickListener(v -> {
                            // Update the appointment status in Firestore
                            String appointmentId = appointment.get("id").toString();
                            db.collection("appointments").document(appointmentId)
                                    .update("status", "cancelled")
                                    .addOnSuccessListener(aVoid -> {
                                        // Refresh the data
                                        appointment.put("status", "cancelled");
                                        notifyItemChanged(getAdapterPosition());
                                        Toast.makeText(getContext(), "Appointment cancelled", Toast.LENGTH_SHORT).show();

                                        // Send notification to patient
                                        // Use a direct approach to send notification instead of trying to cast fragments
                                        // This avoids the ClassCastException

                                        // Get patientId and create notification directly
                                        String patientIdStr = appointment.get("patientId").toString();
                                        String title = "Appointment Rejected";
                                        String message = "Your appointment request has been rejected by the doctor.";

                                        // Create a notification in Firestore with proper structure
                                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                                        Map<String, Object> notification = new HashMap<>();
                                        notification.put("userId", patientIdStr);
                                        notification.put("title", title);
                                        notification.put("message", message);
                                        notification.put("timestamp", com.google.firebase.Timestamp.now());
                                        notification.put("isRead", false);
                                        notification.put("type", "appointment");
                                        notification.put("appointmentId", appointmentId);

                                        // Additional fields to match structure of other notifications
                                        List<String> recipientUids = new ArrayList<>();
                                        recipientUids.add(patientIdStr);
                                        notification.put("recipientUids", recipientUids);
                                        notification.put("targetType", 3); // Specific users

                                        // Get current doctor ID
                                        String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                        notification.put("senderUid", doctorId);

                                        // Get doctor name to add as sender
                                        firestore.collection("users").document(doctorId)
                                                .get()
                                                .addOnSuccessListener(docSnapshot -> {
                                                    // Add sender information
                                                    String doctorName = "Doctor";

                                                    if (docSnapshot.exists()) {
                                                        // Get doctor name, checking both fields
                                                        if (docSnapshot.contains("fullName")) {
                                                            doctorName = docSnapshot.getString("fullName");
                                                        } else if (docSnapshot.contains("name")) {
                                                            doctorName = docSnapshot.getString("name");
                                                        }

                                                        notification.put("senderName", doctorName);
                                                    }

                                                    // Add to notifications collection
                                                    firestore.collection("notifications")
                                                            .add(notification)
                                                            .addOnSuccessListener(docRef -> {
                                                                android.util.Log.d("AppointmentsAdapter", "Notification sent successfully");
                                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                                            })
                                                            .addOnFailureListener(err -> {
                                                                android.util.Log.e("AppointmentsAdapter", "Failed to send notification: " + err.getMessage(), err);
                                                                Toast.makeText(getContext(), "Failed to send notification: " + err.getMessage(),
                                                                        Toast.LENGTH_SHORT).show();
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    // If getting doctor info fails, still send notification with minimal info
                                                    firestore.collection("notifications")
                                                            .add(notification)
                                                            .addOnSuccessListener(docRef -> {
                                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                                            })
                                                            .addOnFailureListener(err -> {
                                                                Toast.makeText(getContext(), "Failed to send notification: " + err.getMessage(),
                                                                        Toast.LENGTH_SHORT).show();
                                                            });
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), "Error updating appointment: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        });

                        buttonLayout.addView(confirmButton);
                        buttonLayout.addView(rejectButton);
                        layout.addView(buttonLayout);
                    }
                    // Add cancel and reschedule buttons if the appointment is confirmed or scheduled
                    else if ("confirmed".equalsIgnoreCase(status) || "SCHEDULED".equalsIgnoreCase(status)) {
                        LinearLayout buttonLayout = new LinearLayout(layout.getContext());
                        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
                        buttonLayout.setGravity(Gravity.END);
                        buttonLayout.setPadding(0, 20, 0, 0);

                        // Cancel button
                        Button cancelButton = new Button(layout.getContext());
                        cancelButton.setText("Cancel");
                        cancelButton.setTextSize(14);
                        cancelButton.setBackgroundColor(layout.getContext().getResources()
                                .getColor(android.R.color.holo_red_light));
                        cancelButton.setTextColor(layout.getContext().getResources()
                                .getColor(android.R.color.white));

                        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        cancelParams.setMargins(0, 0, 10, 0);
                        cancelButton.setLayoutParams(cancelParams);

                        // Set an OnClickListener for the cancel button
                        cancelButton.setOnClickListener(v -> {
                            // Show confirmation dialog
                            new AlertDialog.Builder(getContext())
                                    .setTitle("Cancel Appointment")
                                    .setMessage("Are you sure you want to cancel this appointment? A notification will be sent to the patient.")
                                    .setPositiveButton("Yes", (dialog, which) -> {
                                        // Update the appointment status in Firestore
                                        String appointmentId = appointment.get("id").toString();
                                        db.collection("appointments").document(appointmentId)
                                                .update("status", "CANCELLED")
                                                .addOnSuccessListener(aVoid -> {
                                                    // Refresh the data
                                                    appointment.put("status", "CANCELLED");
                                                    notifyItemChanged(getAdapterPosition());
                                                    Toast.makeText(getContext(), "Appointment cancelled", Toast.LENGTH_SHORT).show();

                                                    // Send notification to patient
                                                    // Use a direct approach to send notification instead of trying to cast fragments
                                                    // This avoids the ClassCastException

                                                    // Get patientId and create notification directly
                                                    String patientIdStr = appointment.get("patientId").toString();
                                                    String title = "Appointment Cancelled by Doctor";
                                                    String message = "Your appointment has been cancelled by the doctor.";

                                                    // Create a notification in Firestore with proper structure
                                                    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                                                    Map<String, Object> notification = new HashMap<>();
                                                    notification.put("userId", patientIdStr);
                                                    notification.put("title", title);
                                                    notification.put("message", message);
                                                    notification.put("timestamp", com.google.firebase.Timestamp.now());
                                                    notification.put("isRead", false);
                                                    notification.put("type", "appointment");
                                                    notification.put("appointmentId", appointmentId);

                                                    // Additional fields to match structure of other notifications
                                                    List<String> recipientUids = new ArrayList<>();
                                                    recipientUids.add(patientIdStr);
                                                    notification.put("recipientUids", recipientUids);
                                                    notification.put("targetType", 3); // Specific users

                                                    // Get current doctor ID
                                                    String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                                    notification.put("senderUid", doctorId);

                                                    // Get doctor name to add as sender
                                                    firestore.collection("users").document(doctorId)
                                                            .get()
                                                            .addOnSuccessListener(docSnapshot -> {
                                                                // Add sender information
                                                                String doctorName = "Doctor";

                                                                if (docSnapshot.exists()) {
                                                                    // Get doctor name, checking both fields
                                                                    if (docSnapshot.contains("fullName")) {
                                                                        doctorName = docSnapshot.getString("fullName");
                                                                    } else if (docSnapshot.contains("name")) {
                                                                        doctorName = docSnapshot.getString("name");
                                                                    }

                                                                    notification.put("senderName", doctorName);
                                                                }

                                                                // Add to notifications collection
                                                                firestore.collection("notifications")
                                                                        .add(notification)
                                                                        .addOnSuccessListener(docRef -> {
                                                                            android.util.Log.d("AppointmentsAdapter", "Notification sent successfully");
                                                                            Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                                                        })
                                                                        .addOnFailureListener(err -> {
                                                                            android.util.Log.e("AppointmentsAdapter", "Failed to send notification: " + err.getMessage(), err);
                                                                            Toast.makeText(getContext(), "Failed to send notification: " + err.getMessage(),
                                                                                    Toast.LENGTH_SHORT).show();
                                                                        });
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                // If getting doctor info fails, still send notification with minimal info
                                                                firestore.collection("notifications")
                                                                        .add(notification)
                                                                        .addOnSuccessListener(docRef -> {
                                                                            Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                                                        })
                                                                        .addOnFailureListener(err -> {
                                                                            Toast.makeText(getContext(), "Failed to send notification: " + err.getMessage(),
                                                                                    Toast.LENGTH_SHORT).show();
                                                                        });
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    Toast.makeText(getContext(), "Error cancelling appointment: " + e.getMessage(),
                                                            Toast.LENGTH_SHORT).show();
                                                });
                                    })
                                    .setNegativeButton("No", null)
                                    .show();
                        });

                        // Reschedule button
                        Button rescheduleButton = new Button(layout.getContext());
                        rescheduleButton.setText("Reschedule");
                        rescheduleButton.setTextSize(14);
                        rescheduleButton.setBackgroundColor(layout.getContext().getResources()
                                .getColor(android.R.color.holo_blue_light));
                        rescheduleButton.setTextColor(layout.getContext().getResources()
                                .getColor(android.R.color.white));

                        // Set an OnClickListener for the reschedule button
                        rescheduleButton.setOnClickListener(v -> {
                            String appointmentId = appointment.get("id").toString();

                            // Use the patientId that's already defined in the outer scope rather than declaring a new one
                            // Create a dialog in the current context instead of trying to access the fragment
                            // This is more reliable as it doesn't depend on fragment navigation structure
                            Context context = layout.getContext();
                            if (context instanceof DoctorDashboardActivity) {
                                // Call the activity's showRescheduleDialog method
                                ((DoctorDashboardActivity) context).showRescheduleDialogFromAdapter(
                                        appointment, appointmentId, patientId);
                            } else {
                                // Fallback to a simple toast message
                                Toast.makeText(context,
                                        "Please navigate to the Appointments tab to reschedule",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

                        buttonLayout.addView(cancelButton);
                        buttonLayout.addView(rescheduleButton);
                        layout.addView(buttonLayout);
                    }
                }
            }
        }
    }

    /**
     * Static Fragment class for Patients view
     */
    public static class PatientsPlaceholderFragment extends Fragment {
        private RecyclerView recyclerView;
        private TextView emptyView;
        private List<User> patientsList = new ArrayList<>();
        private FirebaseFirestore db;

        /**
         * Send a notification to a patient
         */
        public void sendNotificationToPatient(String patientId, String title, String message, String appointmentId) {
            if (patientId == null || patientId.isEmpty()) {
                Toast.makeText(getContext(), "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
                return;
            }

            android.util.Log.d("PatientsFragment", "Sending notification to patient ID: " + patientId);

            // Get current doctor ID
            String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Create a notification in Firestore with proper structure
            Map<String, Object> notification = new HashMap<>();
            notification.put("userId", patientId);
            notification.put("title", title);
            notification.put("message", message);
            notification.put("timestamp", com.google.firebase.Timestamp.now());
            notification.put("isRead", false);
            notification.put("type", "appointment");
            notification.put("appointmentId", appointmentId);

            // Additional fields to match structure of other notifications
            List<String> recipientUids = new ArrayList<>();
            recipientUids.add(patientId);
            notification.put("recipientUids", recipientUids);
            notification.put("targetType", 3); // Specific users
            notification.put("senderUid", doctorId);

            // Get doctor name to add as sender
            db.collection("users").document(doctorId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        // Add sender information
                        String doctorName = "Doctor";

                        if (documentSnapshot.exists()) {
                            // Get doctor name, checking both fields since the field usage is inconsistent
                            if (documentSnapshot.contains("fullName")) {
                                doctorName = documentSnapshot.getString("fullName");
                            } else if (documentSnapshot.contains("name")) {
                                doctorName = documentSnapshot.getString("name");
                            }

                            notification.put("senderName", doctorName);

                            // Add to notifications collection
                            db.collection("notifications")
                                    .add(notification)
                                    .addOnSuccessListener(documentReference -> {
                                        android.util.Log.d("PatientsFragment", "Notification sent successfully");
                                        Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        android.util.Log.e("PatientsFragment", "Failed to send notification: " + e.getMessage(), e);
                                        Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            // If doctor document not found, still send notification but with generic sender info
                            db.collection("notifications")
                                    .add(notification)
                                    .addOnSuccessListener(documentReference -> {
                                        Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        // If getting doctor info fails, still send notification with minimal info
                        db.collection("notifications")
                                .add(notification)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(notifError -> {
                                    Toast.makeText(getContext(), "Failed to send notification: " + notifError.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                });
                    });
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Create the main layout
            LinearLayout mainLayout = new LinearLayout(getContext());
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);

            // Add a title
            TextView titleText = new TextView(getContext());
            titleText.setText("My Patients");
            titleText.setTextSize(22);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 0, 0, 30);
            mainLayout.addView(titleText);

            // Add empty state text view
            emptyView = new TextView(getContext());
            emptyView.setText("Loading patients...");
            emptyView.setTextSize(16);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, 100, 0, 0);

            // Create recycler view for patients
            recyclerView = new RecyclerView(getContext());
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setHasFixedSize(true);

            mainLayout.addView(recyclerView);
            mainLayout.addView(emptyView);

            // Initialize Firestore
            db = FirebaseFirestore.getInstance();

            // Load patients
            loadPatients();

            return mainLayout;
        }

        private void loadPatients() {
            // Query Firestore for all users with role = PATIENT
            db.collection("users")
                    .whereEqualTo("role", User.ROLE_PATIENT)
                    .orderBy("fullName")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        patientsList.clear();

                        if (queryDocumentSnapshots.isEmpty()) {
                            // No patients found
                            emptyView.setText("No patients found in the system");
                            recyclerView.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                        } else {
                            // Process patients
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                try {
                                    // Don't use toObject to avoid @DocumentId issues
                                    User patient = createUserFromDocument(document);
                                    patientsList.add(patient);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            // Set up the adapter with the patients
                            PatientsAdapter adapter = new PatientsAdapter(patientsList);
                            recyclerView.setAdapter(adapter);

                            recyclerView.setVisibility(View.VISIBLE);
                            emptyView.setVisibility(View.GONE);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Handle errors
                        emptyView.setText("Error loading patients: " + e.getMessage());
                        recyclerView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    });
        }

        /**
         * Adapter for displaying patients in a RecyclerView
         */
        private class PatientsAdapter extends RecyclerView.Adapter<PatientsAdapter.PatientViewHolder> {
            private List<User> patientsList;

            public PatientsAdapter(List<User> patientsList) {
                this.patientsList = patientsList;
            }

            @NonNull
            @Override
            public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                // Create a card for each patient
                CardView cardView = new CardView(parent.getContext());
                cardView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                cardView.setRadius(16);
                cardView.setElevation(8);
                cardView.setUseCompatPadding(true);
                cardView.setContentPadding(20, 20, 20, 20);

                LinearLayout layout = new LinearLayout(parent.getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                cardView.addView(layout);

                return new PatientViewHolder(cardView);
            }

            @Override
            public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
                User patient = patientsList.get(position);
                holder.bind(patient);
            }

            @Override
            public int getItemCount() {
                return patientsList.size();
            }

            /**
             * ViewHolder for patient items
             */
            class PatientViewHolder extends RecyclerView.ViewHolder {
                private LinearLayout layout;

                public PatientViewHolder(@NonNull View itemView) {
                    super(itemView);
                    layout = (LinearLayout) ((CardView) itemView).getChildAt(0);
                }

                public void bind(User patient) {
                    layout.removeAllViews();

                    // Patient name
                    TextView nameText = new TextView(layout.getContext());
                    nameText.setText(patient.getFullName());
                    nameText.setTextSize(18);
                    nameText.setTypeface(null, android.graphics.Typeface.BOLD);
                    layout.addView(nameText);

                    // Email
                    if (patient.getEmail() != null && !patient.getEmail().isEmpty()) {
                        TextView emailText = new TextView(layout.getContext());
                        emailText.setText("Email: " + patient.getEmail());
                        emailText.setTextSize(16);
                        emailText.setPadding(0, 10, 0, 5);
                        layout.addView(emailText);
                    }

                    // Phone
                    if (patient.getPhone() != null && !patient.getPhone().isEmpty()) {
                        TextView phoneText = new TextView(layout.getContext());
                        phoneText.setText("Phone: " + patient.getPhone());
                        phoneText.setTextSize(16);
                        phoneText.setPadding(0, 5, 0, 5);
                        layout.addView(phoneText);
                    }

                    // Date of Birth
                    if (patient.getDateOfBirth() != null && !patient.getDateOfBirth().isEmpty()) {
                        TextView dobText = new TextView(layout.getContext());
                        dobText.setText("Date of Birth: " + patient.getDateOfBirth());
                        dobText.setTextSize(16);
                        dobText.setPadding(0, 5, 0, 5);
                        layout.addView(dobText);
                    }

                    // Gender
                    if (patient.getGender() != null && !patient.getGender().isEmpty()) {
                        TextView genderText = new TextView(layout.getContext());
                        genderText.setText("Gender: " + patient.getGender());
                        genderText.setTextSize(16);
                        genderText.setPadding(0, 5, 0, 5);
                        layout.addView(genderText);
                    }

                    // Add Schedule Appointment button
                    Button scheduleButton = new Button(layout.getContext());
                    scheduleButton.setText("Schedule Appointment");
                    scheduleButton.setTextSize(14);
                    scheduleButton.setBackgroundColor(layout.getContext().getResources()
                            .getColor(R.color.colorPrimary));
                    scheduleButton.setTextColor(layout.getContext().getResources()
                            .getColor(android.R.color.white));
                    LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    buttonParams.setMargins(0, 20, 0, 0);
                    scheduleButton.setLayoutParams(buttonParams);

                    // Set click listener for scheduling
                    scheduleButton.setOnClickListener(v -> {
                        // Show a confirmation dialog with date/time picker
                        new AlertDialog.Builder(getContext())
                                .setTitle("Schedule Appointment")
                                .setMessage("This will send an appointment request to " + patient.getFullName() +
                                        ". Would you like to proceed?")
                                .setPositiveButton("Yes", (dialog, which) -> {
                                    // Create a new appointment
                                    createAppointment(patient);
                                })
                                .setNegativeButton("No", null)
                                .show();
                    });

                    layout.addView(scheduleButton);

                    // Add View Medical Records button
                    Button recordsButton = new Button(layout.getContext());
                    recordsButton.setText("View Medical Records");
                    recordsButton.setTextSize(14);
                    recordsButton.setBackgroundColor(layout.getContext().getResources()
                            .getColor(R.color.colorAccent));
                    recordsButton.setTextColor(layout.getContext().getResources()
                            .getColor(android.R.color.white));
                    LinearLayout.LayoutParams recordsButtonParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    recordsButtonParams.setMargins(0, 10, 0, 0);
                    recordsButton.setLayoutParams(recordsButtonParams);

                    // Set click listener for medical records
                    recordsButton.setOnClickListener(v -> {
                        // Show a dialog with medical records
                        showMedicalRecords(patient);
                    });

                    layout.addView(recordsButton);
                }

                /**
                 * Create a new appointment for the patient
                 */
                private void createAppointment(User patient) {
                    // Get the current doctor
                    String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    // Get current date for date picker default values
                    final Calendar calendar = Calendar.getInstance();
                    int year = calendar.get(Calendar.YEAR);
                    int month = calendar.get(Calendar.MONTH);
                    int day = calendar.get(Calendar.DAY_OF_MONTH);
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                    int minute = calendar.get(Calendar.MINUTE);

                    // First show a date picker dialog
                    DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(),
                            (datePicker, selectedYear, selectedMonth, selectedDay) -> {

                                // After selecting date, show time picker
                                TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                                        (timePicker, selectedHour, selectedMinute) -> {

                                            // Once time is selected, create and save the appointment
                                            Calendar selectedDateTime = Calendar.getInstance();
                                            selectedDateTime.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute);

                                            // First fetch doctor name for the appointment
                                            db.collection("users").document(doctorId)
                                                    .get()
                                                    .addOnSuccessListener(doctorDoc -> {
                                                        String doctorName = "Doctor";

                                                        // Handle the inconsistent field names in the database
                                                        if (doctorDoc.contains("fullName")) {
                                                            doctorName = doctorDoc.getString("fullName");
                                                        } else if (doctorDoc.contains("name")) {
                                                            doctorName = doctorDoc.getString("name");
                                                        }

                                                        // Create a final copy of doctorName to use in lambda
                                                        final String finalDoctorName = doctorName;

                                                        // Create timestamp from selected date and time
                                                        com.google.firebase.Timestamp timestamp =
                                                                new com.google.firebase.Timestamp(new Date(selectedDateTime.getTimeInMillis()));

                                                        // Format time as string for older records
                                                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US);
                                                        String timeString = sdf.format(timestamp.toDate());

                                                        // Create an appointment document with all required fields
                                                        Map<String, Object> appointment = new HashMap<>();
                                                        appointment.put("patientId", patient.getUid());
                                                        appointment.put("patientName", patient.getFullName());
                                                        appointment.put("doctorId", doctorId);
                                                        appointment.put("doctorName", finalDoctorName);
                                                        appointment.put("dateTime", timestamp); // New format
                                                        appointment.put("date", timestamp);     // Legacy support
                                                        appointment.put("time", timeString);    // Legacy support
                                                        appointment.put("status", "SCHEDULED");
                                                        appointment.put("reason", "Regular checkup");
                                                        appointment.put("notes", "Regular checkup");
                                                        appointment.put("appointmentType", "General");
                                                        appointment.put("createdAt", com.google.firebase.Timestamp.now());

                                                        // Add to Firestore
                                                        db.collection("appointments")
                                                                .add(appointment)
                                                                .addOnSuccessListener(documentReference -> {
                                                                    // Success message
                                                                    Toast.makeText(getContext(), "Appointment scheduled with " + patient.getFullName() +
                                                                            " on " + getFormattedDate(timestamp), Toast.LENGTH_SHORT).show();

                                                                    // Send notification to patient
                                                                    String title = "New Appointment";
                                                                    String message = "Dr. " + finalDoctorName + " has scheduled an appointment with you on " +
                                                                            getFormattedDate(timestamp);

                                                                    // Send notification directly without trying to cast to PatientsPlaceholderFragment
                                                                    // This avoids the ClassCastException
                                                                    sendNotificationToPatient(patient.getUid(), title, message, documentReference.getId());
                                                                })
                                                                .addOnFailureListener(e -> {
                                                                    Toast.makeText(getContext(), "Error scheduling appointment: " + e.getMessage(),
                                                                            Toast.LENGTH_SHORT).show();
                                                                });
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        // Handle failure to get doctor data
                                                        Toast.makeText(getContext(), "Error getting doctor information: " + e.getMessage(),
                                                                Toast.LENGTH_SHORT).show();
                                                    });
                                        }, hour, minute, DateFormat.is24HourFormat(getContext()));

                                timePickerDialog.show();
                            }, year, month, day);

                    // Set min date to today to prevent scheduling in the past
                    datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
                    datePickerDialog.show();
                }

                /**
                 * Format timestamp to human-readable date and time
                 */
                private String getFormattedDate(com.google.firebase.Timestamp timestamp) {
                    if (timestamp == null) return "N/A";

                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat(
                            "MMM dd, yyyy 'at' h:mm a", java.util.Locale.US);
                    return dateFormat.format(timestamp.toDate());
                }

                /**
                 * Send a notification to a patient
                 */
                private void sendNotificationToPatient(String patientId, String title, String message, String appointmentId) {
                    if (patientId == null || patientId.isEmpty()) {
                        Toast.makeText(getContext(), "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    android.util.Log.d("PatientsFragment", "Sending notification to patient ID: " + patientId);

                    // Get current doctor ID
                    String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    // Create a notification in Firestore with proper structure
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("userId", patientId);
                    notification.put("title", title);
                    notification.put("message", message);
                    notification.put("timestamp", com.google.firebase.Timestamp.now());
                    notification.put("isRead", false);
                    notification.put("type", "appointment");
                    notification.put("appointmentId", appointmentId);

                    // Additional fields to match structure of other notifications
                    List<String> recipientUids = new ArrayList<>();
                    recipientUids.add(patientId);
                    notification.put("recipientUids", recipientUids);
                    notification.put("targetType", 3); // Specific users
                    notification.put("senderUid", doctorId);

                    // Get doctor name to add as sender
                    db.collection("users").document(doctorId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                // Add sender information
                                String doctorName = "Doctor";

                                if (documentSnapshot.exists()) {
                                    // Get doctor name, checking both fields since the field usage is inconsistent
                                    if (documentSnapshot.contains("fullName")) {
                                        doctorName = documentSnapshot.getString("fullName");
                                    } else if (documentSnapshot.contains("name")) {
                                        doctorName = documentSnapshot.getString("name");
                                    }

                                    notification.put("senderName", doctorName);

                                    // Add to notifications collection
                                    db.collection("notifications")
                                            .add(notification)
                                            .addOnSuccessListener(documentReference -> {
                                                android.util.Log.d("PatientsFragment", "Notification sent successfully");
                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                android.util.Log.e("PatientsFragment", "Failed to send notification: " + e.getMessage(), e);
                                                Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                } else {
                                    // If doctor document not found, still send notification but with generic sender info
                                    db.collection("notifications")
                                            .add(notification)
                                            .addOnSuccessListener(documentReference -> {
                                                Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(getContext(), "Failed to send notification: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                }
                            })
                            .addOnFailureListener(e -> {
                                // If getting doctor info fails, still send notification with minimal info
                                db.collection("notifications")
                                        .add(notification)
                                        .addOnSuccessListener(documentReference -> {
                                            Toast.makeText(getContext(), "Notification sent to patient", Toast.LENGTH_SHORT).show();
                                        })
                                        .addOnFailureListener(notifError -> {
                                            Toast.makeText(getContext(), "Failed to send notification: " + notifError.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        });
                            });
                }

                /**
                 * Show medical records for a patient
                 */
                private void showMedicalRecords(User patient) {
                    // Use MedicalRecordsFragment to display and manage medical records properly
                    try {
                        // Create a new MedicalRecordsFragment
                        com.example.patienttracker.fragments.MedicalRecordsFragment fragment =
                                new com.example.patienttracker.fragments.MedicalRecordsFragment();

                        // Create bundle to pass data to the fragment
                        Bundle args = new Bundle();

                        // Get the current doctor's information
                        String doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        FirebaseFirestore.getInstance().collection("users")
                                .document(doctorId)
                                .get()
                                .addOnSuccessListener(doctorDoc -> {
                                    if (doctorDoc.exists()) {
                                        // Create a User object for the doctor
                                        // Don't use toObject to avoid @DocumentId issues
                                        User doctorUser = createUserFromDocument(doctorDoc);
                                        if (doctorUser != null) {
                                            // Set arguments for the fragment
                                            args.putSerializable("USER", doctorUser);
                                            args.putString("PATIENT_ID", patient.getId());
                                            args.putString("PATIENT_NAME", patient.getFullName());
                                            args.putSerializable("PATIENT_USER", patient);

                                            fragment.setArguments(args);

                                            // Get the activity from context and launch the fragment
                                            if (getContext() instanceof AppCompatActivity) {
                                                AppCompatActivity activity = (AppCompatActivity) getContext();
                                                activity.getSupportFragmentManager().beginTransaction()
                                                        .replace(R.id.frame_container, fragment)
                                                        .addToBackStack("MedicalRecordsFragment")
                                                        .commit();
                                            } else {
                                                Toast.makeText(getContext(),
                                                        "Unable to display medical records: Context is not an activity",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(getContext(),
                                            "Error loading doctor information: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                });
                    } catch (Exception e) {
                        // Handle any exceptions
                        Toast.makeText(getContext(),
                                "Error showing medical records: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e("DoctorDashboard", "Error in showMedicalRecords: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Static Fragment class for Notifications view
     */
    public static class NotificationsPlaceholderFragment extends Fragment {
        private RecyclerView recyclerView;
        private TextView emptyView;
        private Button markAllReadButton;
        private List<Map<String, Object>> notificationsList = new ArrayList<>();
        private FirebaseFirestore db;
        private String currentUserId;
        private NotificationsAdapter adapter;
        // Track unread notifications count in the fragment
        private int localUnreadCount = 0;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Create the main layout
            LinearLayout mainLayout = new LinearLayout(getContext());
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);

            // Add a title
            TextView titleText = new TextView(getContext());
            titleText.setText("My Notifications");
            titleText.setTextSize(22);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 0, 0, 20);
            mainLayout.addView(titleText);

            // Add Mark All as Read button
            markAllReadButton = new Button(getContext());
            markAllReadButton.setText("Mark All as Read");
            markAllReadButton.setOnClickListener(v -> markAllNotificationsAsRead());
            markAllReadButton.setVisibility(View.GONE); // Initially hidden until we have notifications
            mainLayout.addView(markAllReadButton);

            // Add spacing
            View spacer = new View(getContext());
            spacer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    20)); // 20dp height
            mainLayout.addView(spacer);

            // Add empty state text view
            emptyView = new TextView(getContext());
            emptyView.setText("Loading notifications...");
            emptyView.setTextSize(16);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, 100, 0, 0);

            // Create recycler view for notifications
            recyclerView = new RecyclerView(getContext());
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setHasFixedSize(true);

            mainLayout.addView(recyclerView);
            mainLayout.addView(emptyView);

            // Initialize Firestore
            db = FirebaseFirestore.getInstance();

            // Get the current user ID from Firebase Auth
            currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

            // Load notifications
            if (currentUserId != null) {
                loadNotifications();
            } else {
                emptyView.setText("Error: User not authenticated");
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            }

            return mainLayout;
        }

        /**
         * Mark all notifications as read
         */
        private void markAllNotificationsAsRead() {
            if (notificationsList.isEmpty()) {
                return;
            }

            // Create a batch operation to update all notifications at once
            com.google.firebase.firestore.WriteBatch batch = db.batch();
            boolean hasRealNotifications = false;

            // Add each notification to the batch update
            for (Map<String, Object> notification : notificationsList) {
                // Skip notifications that are already read
                if (notification.containsKey("isRead") && (boolean) notification.get("isRead")) {
                    continue;
                }

                // Skip synthetic notifications (those created from appointments that don't exist in Firestore)
                if (notification.containsKey("id") && !notification.get("id").toString().startsWith("apt_")) {
                    String notificationId = notification.get("id").toString();
                    batch.update(db.collection("notifications").document(notificationId), "isRead", true);
                    hasRealNotifications = true;
                }

                // Update the local notification object
                notification.put("isRead", true);
            }

            // Only commit the batch if there are real notifications
            if (hasRealNotifications) {
                batch.commit()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "All notifications marked as read", Toast.LENGTH_SHORT).show();
                            // Refresh the adapter
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }

                            // Update badge count
                            localUnreadCount = 0;
                            updateBadgeCount();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                // Just update the UI for synthetic notifications
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                // Update badge count for synthetic notifications too
                localUnreadCount = 0;
                updateBadgeCount();

                Toast.makeText(getContext(), "All notifications marked as read", Toast.LENGTH_SHORT).show();

            }
        }

        /**
         * Updates the badge count in the main activity
         */
        private void updateBadgeCount() {
            // Get reference to the parent activity using Fragment's getActivity method
            android.app.Activity activity = getActivity();
            if (activity instanceof DoctorDashboardActivity) {
                DoctorDashboardActivity parentActivity = (DoctorDashboardActivity) activity;
                // Call method in parent activity to update badge
                parentActivity.updateNotificationBadge(localUnreadCount);
            }
        }

        private void loadNotifications() {
            // Query Firestore for notifications for this doctor using arrayContains
            // This matches any notification where the recipientUids array contains this user's ID
            db.collection("notifications")
                    .whereArrayContains("recipientUids", currentUserId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        notificationsList.clear();

                        if (queryDocumentSnapshots.isEmpty()) {
                            // No notifications found
                            emptyView.setText("No notifications at this time");
                            recyclerView.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                        } else {
                            // Process notifications
                            localUnreadCount = 0;
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                Map<String, Object> notification = document.getData();
                                notification.put("id", document.getId());

                                // Count unread notifications
                                boolean isRead = notification.containsKey("isRead") &&
                                        (boolean) notification.get("isRead");
                                if (!isRead) {
                                    localUnreadCount++;
                                }

                                notificationsList.add(notification);
                            }

                            // Update the badge count in the parent activity
                            updateBadgeCount();

                            // Set up the adapter with the notifications
                            adapter = new NotificationsAdapter(notificationsList);
                            recyclerView.setAdapter(adapter);

                            // Show the recycler view and the mark all as read button
                            recyclerView.setVisibility(View.VISIBLE);
                            emptyView.setVisibility(View.GONE);
                            markAllReadButton.setVisibility(View.VISIBLE);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Handle errors
                        emptyView.setText("Error loading notifications: " + e.getMessage());
                        recyclerView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    });

            // If there are no explicit notifications in Firestore,
            // let's also check for appointment changes that should generate notifications
            checkForAppointmentChanges();
        }

        private void checkForAppointmentChanges() {
            // Check for recent appointment changes (last 7 days)
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, -7);
            Date lastWeek = calendar.getTime();
            com.google.firebase.Timestamp lastWeekTimestamp = new com.google.firebase.Timestamp(lastWeek);

            db.collection("appointments")
                    .whereEqualTo("doctorId", currentUserId)
                    .whereGreaterThan("createdAt", lastWeekTimestamp)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            // If we have appointments but no notifications, create notifications from appointments
                            if (notificationsList.isEmpty()) {
                                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                    Map<String, Object> appointment = document.getData();

                                    // Create a notification for each recent appointment
                                    Map<String, Object> notification = new HashMap<>();
                                    notification.put("id", "apt_" + document.getId());
                                    notification.put("title", "New Appointment");

                                    String patientId = appointment.containsKey("patientId") ?
                                            appointment.get("patientId").toString() : "Unknown";

                                    notification.put("message", "You have a new appointment request.");
                                    notification.put("type", "appointment");
                                    notification.put("isRead", false);
                                    notification.put("timestamp", appointment.get("createdAt"));
                                    notification.put("data", appointment);

                                    notificationsList.add(notification);
                                }

                                // Update the UI if we found appointment notifications
                                if (!notificationsList.isEmpty()) {
                                    // Count unread synthetic notifications
                                    for (Map<String, Object> notification : notificationsList) {
                                        boolean isRead = notification.containsKey("isRead") &&
                                                (boolean) notification.get("isRead");
                                        if (!isRead) {
                                            localUnreadCount++;
                                        }
                                    }

                                    // Update UI
                                    adapter = new NotificationsAdapter(notificationsList);
                                    recyclerView.setAdapter(adapter);

                                    recyclerView.setVisibility(View.VISIBLE);
                                    emptyView.setVisibility(View.GONE);
                                    markAllReadButton.setVisibility(View.VISIBLE);

                                    // Update the badge count in the parent activity
                                    updateBadgeCount();
                                }
                            }
                        }
                    });
        }

        /**
         * Adapter for displaying notifications in a RecyclerView
         */
        private class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {
            private List<Map<String, Object>> notificationsList;

            public NotificationsAdapter(List<Map<String, Object>> notificationsList) {
                this.notificationsList = notificationsList;
            }

            @NonNull
            @Override
            public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                // Create a card for each notification
                CardView cardView = new CardView(parent.getContext());
                cardView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                cardView.setRadius(16);
                cardView.setElevation(8);
                cardView.setUseCompatPadding(true);
                cardView.setContentPadding(20, 20, 20, 20);

                LinearLayout layout = new LinearLayout(parent.getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                cardView.addView(layout);

                return new NotificationViewHolder(cardView);
            }

            @Override
            public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
                Map<String, Object> notification = notificationsList.get(position);
                holder.bind(notification);
            }

            @Override
            public int getItemCount() {
                return notificationsList.size();
            }

            /**
             * ViewHolder for notification items
             */
            class NotificationViewHolder extends RecyclerView.ViewHolder {
                private LinearLayout layout;

                public NotificationViewHolder(@NonNull View itemView) {
                    super(itemView);
                    layout = (LinearLayout) ((CardView) itemView).getChildAt(0);
                }

                public void bind(Map<String, Object> notification) {
                    layout.removeAllViews();

                    // Get details from notification
                    String title = notification.containsKey("title") ?
                            notification.get("title").toString() : "Notification";
                    String message = notification.containsKey("message") ?
                            notification.get("message").toString() : "";
                    boolean isRead = notification.containsKey("isRead") ?
                            (boolean) notification.get("isRead") : false;
                    String type = notification.containsKey("type") ?
                            notification.get("type").toString() : "general";

                    // Change background color based on read status
                    if (!isRead) {
                        ((CardView) itemView).setCardBackgroundColor(
                                layout.getContext().getResources().getColor(android.R.color.holo_blue_light, null));
                    }

                    // Title
                    TextView titleText = new TextView(layout.getContext());
                    titleText.setText(title);
                    titleText.setTextSize(18);
                    titleText.setTypeface(null, android.graphics.Typeface.BOLD);
                    layout.addView(titleText);

                    // Message
                    TextView messageText = new TextView(layout.getContext());
                    messageText.setText(message);
                    messageText.setTextSize(16);
                    messageText.setPadding(0, 10, 0, 10);
                    layout.addView(messageText);

                    // Date and time
                    String dateStr = "Date not specified";
                    if (notification.containsKey("timestamp")) {
                        Object timestampObj = notification.get("timestamp");
                        if (timestampObj != null) {
                            if (timestampObj instanceof com.google.firebase.Timestamp) {
                                com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) timestampObj;
                                Date date = timestamp.toDate();
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
                                dateStr = sdf.format(date);
                            } else {
                                dateStr = timestampObj.toString();
                            }
                        }
                    }

                    TextView dateText = new TextView(layout.getContext());
                    dateText.setText(dateStr);
                    dateText.setTextSize(14);
                    dateText.setPadding(0, 5, 0, 5);
                    dateText.setTextColor(layout.getContext().getResources().getColor(android.R.color.darker_gray, null));
                    layout.addView(dateText);

                    // Add mark as read button if not read
                    if (!isRead) {
                        Button markReadBtn = new Button(layout.getContext());
                        markReadBtn.setText("Mark as Read");
                        markReadBtn.setTextSize(14);

                        markReadBtn.setOnClickListener(v -> {
                            // Mark as read in Firestore if this is a real notification
                            if (notification.containsKey("id") && !notification.get("id").toString().startsWith("apt_")) {
                                String notificationId = notification.get("id").toString();
                                db.collection("notifications").document(notificationId)
                                        .update("isRead", true)
                                        .addOnSuccessListener(aVoid -> {
                                            // Update locally
                                            notification.put("isRead", true);
                                            ((CardView) itemView).setCardBackgroundColor(
                                                    layout.getContext().getResources().getColor(android.R.color.white, null));
                                            layout.removeView(markReadBtn);

                                            // Update the unread count and badge
                                            localUnreadCount = Math.max(0, localUnreadCount - 1);
                                            updateBadgeCount();
                                        });
                            } else {
                                // Just update locally for generated notifications
                                notification.put("isRead", true);
                                ((CardView) itemView).setCardBackgroundColor(
                                        layout.getContext().getResources().getColor(android.R.color.white, null));
                                layout.removeView(markReadBtn);

                                // Update the unread count and badge
                                localUnreadCount = Math.max(0, localUnreadCount - 1);
                                updateBadgeCount();
                            }
                        });

                        layout.addView(markReadBtn);
                    }
                }
            }
        }
    }

    /**
     * Create a User object manually from a DocumentSnapshot to avoid @DocumentId issues
     */
    private static User createUserFromDocument(DocumentSnapshot documentSnapshot) {
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

            // Add other fields as needed
            if (userData.containsKey("photoUrl")) {
                user.setPhotoUrl((String) userData.get("photoUrl"));
            }
            if (userData.containsKey("phone")) {
                // Decrypt phone number from database
                String encryptedPhone = (String) userData.get("phone");
                String decryptedPhone = com.example.patienttracker.utils.EncryptionUtil.decryptData(encryptedPhone);
                user.setPhone(decryptedPhone);
            }
            if (userData.containsKey("specialization")) {
                user.setSpecialization((String) userData.get("specialization"));
            }
            if (userData.containsKey("department")) {
                user.setDepartment((String) userData.get("department"));
            }
            if (userData.containsKey("yearsOfExperience")) {
                Object expObj = userData.get("yearsOfExperience");
                if (expObj instanceof Long) {
                    user.setYearsOfExperience(((Long) expObj).intValue());
                } else if (expObj instanceof Integer) {
                    user.setYearsOfExperience((Integer) expObj);
                }
            }

            return user;
        } catch (Exception e) {
            Log.e(TAG, "Error creating user from document: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Show or hide the progress indicator overlay
     *
     * @param show True to show the progress indicator, false to hide it
     */
    private void showProgressIndicator(boolean show) {
        if (progressOverlay != null) {
            progressOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // We've already defined the createUserFromDocument method earlier in this class, so this duplicate is removed
}