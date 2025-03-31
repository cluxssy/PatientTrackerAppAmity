package com.example.patienttracker.activities;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patienttracker.R;
import com.example.patienttracker.fragments.MedicalRecordsFragment;
import com.example.patienttracker.fragments.ProfileFragment;
import com.example.patienttracker.models.Appointment;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.BadgeHelper;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.firebase.firestore.Query;

/**
 * Main activity for patients.
 * Provides a bottom navigation menu to access appointments,
 * medical records, profile, and notifications.
 */
public class PatientDashboardActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private static final String TAG = "PatientDashboardActivity";

    private BottomNavigationView bottomNavigationView;
    private User currentUser;
    private FirebaseFirestore db;
    private ListenerRegistration appointmentsListener; // Store the listener to detach it later
    private ConstraintLayout mainLayout; // Using ConstraintLayout instead of FrameLayout
    private FrameLayout progressOverlay; // Progress indicator overlay

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_dashboard);

        try {
            // Get current user data from intent
            String userId = getIntent().getStringExtra("USER_ID");
            String userEmail = getIntent().getStringExtra("USER_EMAIL");
            String userName = getIntent().getStringExtra("USER_NAME");
            int userRole = getIntent().getIntExtra("USER_ROLE", -1);
            int userStatus = getIntent().getIntExtra("USER_STATUS", -1);

            // Create initial user object from intent data
            if (userId != null && userEmail != null && userName != null && userRole == User.ROLE_PATIENT) {
                currentUser = new User(userId, userEmail, userName, userRole);
                currentUser.setStatus(userStatus);

                // Fetch full user data from Firestore for comprehensive profile information
                FirebaseFirestore.getInstance().collection("users").document(userId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
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
                            Log.e(TAG, "Error fetching full user data: " + e.getMessage(), e);
                        });
            } else {
                // If user data is invalid or not a patient, go back to login
                FirebaseUtil.signOut();
                navigateToLogin();
                return;
            }

            // Set up bottom navigation
            bottomNavigationView = findViewById(R.id.bottom_navigation);
            bottomNavigationView.setOnItemSelectedListener(this);

            // Find the main container - Using ConstraintLayout, not FrameLayout
            mainLayout = findViewById(R.id.frame_container);

            // Initialize progress overlay
            progressOverlay = findViewById(R.id.progress_overlay);

            // Set title in action bar
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.app_name) + " - " + getString(R.string.role_patient));
            }

            // Set default selection to profile (now the first tab)
            bottomNavigationView.setSelectedItemId(R.id.nav_profile);

            // Update notification badge
            updateNotificationBadge();
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing dashboard: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void displayWelcomeView() {
        // Clear previous views
        mainLayout.removeAllViews();

        // Create and add a text view with welcome message
        TextView welcomeText = new TextView(this);
        welcomeText.setText("Welcome, " + currentUser.getName() + "!\n\nYour appointments and medical information will appear here.");
        welcomeText.setTextSize(18);
        welcomeText.setPadding(50, 50, 50, 50);
        welcomeText.setTextColor(getResources().getColor(R.color.black));

        // Use ConstraintLayout.LayoutParams
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );

        // Center in parent
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;

        welcomeText.setLayoutParams(params);
        welcomeText.setGravity(Gravity.CENTER);

        // Add to layout
        mainLayout.addView(welcomeText);
    }

    private void displayAppointmentsView() {
        // Clear previous views
        mainLayout.removeAllViews();

        // Create a scroll view to ensure content fits on smaller screens
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
        ));

        // Create a vertical layout for appointments
        LinearLayout appointmentsLayout = new LinearLayout(this);
        appointmentsLayout.setOrientation(LinearLayout.VERTICAL);
        appointmentsLayout.setPadding(32, 32, 32, 32);

        // Set layout parameters to match parent for proper scrolling
        appointmentsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Add header with title and icon
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 32);

        // Title text with shadow for depth
        TextView titleText = new TextView(this);
        titleText.setText("Your Appointments");
        titleText.setTextSize(24);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setTextColor(getResources().getColor(R.color.colorPrimary));
        // Add shadow to text
        titleText.setShadowLayer(2, 1, 1, Color.parseColor("#33000000"));

        // Add title to header
        headerLayout.addView(titleText);
        appointmentsLayout.addView(headerLayout);

        // Add loading indicator
        LinearLayout loadingLayout = new LinearLayout(this);
        loadingLayout.setOrientation(LinearLayout.VERTICAL);
        loadingLayout.setGravity(Gravity.CENTER);
        loadingLayout.setPadding(0, 100, 0, 0);

        TextView loadingText = new TextView(this);
        loadingText.setText("Loading your appointments...");
        loadingText.setTextSize(18);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, 0, 0, 32);
        loadingLayout.addView(loadingText);

        appointmentsLayout.addView(loadingLayout);

        // Important: ScrollView can only have one direct child
        // Add the appointments layout to the scroll view, then to the main layout
        scrollView.addView(appointmentsLayout);
        mainLayout.addView(scrollView);

        // Query appointments from Firestore
        String currentUserId = currentUser.getId();

        // Initialize Firestore instance
        db = FirebaseFirestore.getInstance();

        // Setup error handling before setting the snapshot listener
        try {
            // Setup real-time listener for appointments
            // Note: There are inconsistencies in the field names, some use "date" and others use "dateTime"
            // This query will listen for changes to appointments regardless of which field is updated
            appointmentsListener = db.collection("appointments")
                    .whereEqualTo("patientId", currentUserId)
                    // Don't add ordering filters to make sure all changes are caught
                    .addSnapshotListener((queryDocumentSnapshots, exception) -> {
                        // Handle potential errors
                        if (exception != null) {
                            android.util.Log.e("PatientDashboard", "Error loading appointments: " + exception.getMessage(), exception);

                            // Remove loading layout
                            appointmentsLayout.removeView(loadingLayout);

                            // Show error message
                            TextView errorText = new TextView(this);

                            // Check if this is an index error
                            if (com.example.patienttracker.utils.FirestoreIndexHelper.isIndexError(exception)) {
                                // Show the index helper dialog
                                com.example.patienttracker.utils.FirestoreIndexHelper.showIndexHelperDialog(
                                        PatientDashboardActivity.this, exception);
                            } else {
                                // Show generic error message
                                errorText.setText("Error loading appointments: " + exception.getMessage());
                                errorText.setTextColor(Color.RED);
                                errorText.setTextSize(16);
                                errorText.setGravity(Gravity.CENTER);
                                errorText.setPadding(32, 100, 32, 0);
                                appointmentsLayout.addView(errorText);
                            }
                            return;
                        }

                        if (queryDocumentSnapshots == null) {
                            return;
                        }
                        // Remove loading layout
                        appointmentsLayout.removeView(loadingLayout);

                        // Clear all previous appointment cards and labels
                        // Keep only the header at index 0
                        while (appointmentsLayout.getChildCount() > 1) {
                            appointmentsLayout.removeViewAt(1);
                        }

                        if (queryDocumentSnapshots.isEmpty()) {
                            // Show empty state
                            LinearLayout emptyStateLayout = new LinearLayout(this);
                            emptyStateLayout.setOrientation(LinearLayout.VERTICAL);
                            emptyStateLayout.setGravity(Gravity.CENTER);
                            emptyStateLayout.setPadding(0, 100, 0, 0);

                            TextView noAppointmentsText = new TextView(this);
                            noAppointmentsText.setText("You have no upcoming appointments");
                            noAppointmentsText.setTextSize(18);
                            noAppointmentsText.setTypeface(null, Typeface.BOLD);
                            noAppointmentsText.setTextColor(getResources().getColor(R.color.black));
                            noAppointmentsText.setGravity(Gravity.CENTER);

                            TextView subText = new TextView(this);
                            subText.setText("Book an appointment to get started");
                            subText.setTextSize(16);
                            subText.setTextColor(Color.GRAY);
                            subText.setGravity(Gravity.CENTER);
                            subText.setPadding(0, 16, 0, 50);

                            emptyStateLayout.addView(noAppointmentsText);
                            emptyStateLayout.addView(subText);
                            appointmentsLayout.addView(emptyStateLayout);
                        } else {
                            // Add a label for upcoming appointments
                            TextView upcomingLabel = new TextView(this);
                            upcomingLabel.setText("UPCOMING");
                            upcomingLabel.setTextSize(14);
                            upcomingLabel.setTypeface(null, Typeface.BOLD);
                            upcomingLabel.setTextColor(Color.GRAY);
                            upcomingLabel.setPadding(8, 0, 0, 16);
                            appointmentsLayout.addView(upcomingLabel);

                            // Get appointments
                            List<Appointment> appointmentsList = new ArrayList<>();
                            for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                                // Don't use toObject to avoid potential @DocumentId issues
                                Appointment appointment = createAppointmentFromDocument(document);
                                if (appointment != null) {
                                    // Normalize the data format to handle legacy fields
                                    appointment.normalizeDateTime();

                                    String status = appointment.getStatus() != null ? appointment.getStatus().toLowerCase() : "";

                                    // Skip cancelled appointments
                                    if (!status.equals("cancelled") && !status.equals("canceled")) {
                                        appointment.setId(document.getId());
                                        appointmentsList.add(appointment);
                                    }
                                }
                            }

                            // Check if we have any appointments after filtering
                            if (appointmentsList.isEmpty()) {
                                // Show empty state
                                LinearLayout emptyStateLayout = new LinearLayout(this);
                                emptyStateLayout.setOrientation(LinearLayout.VERTICAL);
                                emptyStateLayout.setGravity(Gravity.CENTER);
                                emptyStateLayout.setPadding(0, 100, 0, 0);

                                TextView noAppointmentsText = new TextView(this);
                                noAppointmentsText.setText("You have no active appointments");
                                noAppointmentsText.setTextSize(18);
                                noAppointmentsText.setTypeface(null, Typeface.BOLD);
                                noAppointmentsText.setTextColor(getResources().getColor(R.color.black));
                                noAppointmentsText.setGravity(Gravity.CENTER);

                                TextView subText = new TextView(this);
                                subText.setText("Book an appointment to get started");
                                subText.setTextSize(16);
                                subText.setTextColor(Color.GRAY);
                                subText.setGravity(Gravity.CENTER);
                                subText.setPadding(0, 16, 0, 50);

                                emptyStateLayout.addView(noAppointmentsText);
                                emptyStateLayout.addView(subText);
                                appointmentsLayout.addView(emptyStateLayout);
                            }

                            // Create cards for each appointment
                            for (Appointment appointment : appointmentsList) {
                                // Create a card for each appointment with rounded corners and shadow
                                CardView card = new CardView(this);
                                card.setRadius(16);
                                card.setContentPadding(0, 0, 0, 0);
                                card.setUseCompatPadding(true);
                                card.setCardBackgroundColor(Color.WHITE);
                                card.setCardElevation(6);

                                // Main content layout
                                LinearLayout cardLayout = new LinearLayout(this);
                                cardLayout.setOrientation(LinearLayout.VERTICAL);

                                // Top colored strip to indicate appointment type
                                View colorStrip = new View(this);
                                colorStrip.setBackgroundColor(getAppointmentColor(appointment.getAppointmentType()));
                                LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        12
                                );
                                colorStrip.setLayoutParams(stripParams);
                                cardLayout.addView(colorStrip);

                                // Content container with padding
                                LinearLayout contentLayout = new LinearLayout(this);
                                contentLayout.setOrientation(LinearLayout.VERTICAL);
                                contentLayout.setPadding(24, 20, 24, 24);

                                // Date and time in highlighted format
                                LinearLayout dateTimeLayout = new LinearLayout(this);
                                dateTimeLayout.setOrientation(LinearLayout.HORIZONTAL);

                                // Format date and time from Timestamp
                                String dateStr = "";
                                String timeStr = "";

                                // Check for dateTime field first (newer format)
                                if (appointment.getDateTime() != null) {
                                    Date date = appointment.getDateTime().toDate();
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
                                    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.US);
                                    dateStr = dateFormat.format(date);
                                    timeStr = timeFormat.format(date);
                                }
                                // Fall back to date field if available (older format)
                                else if (appointment.getDate() != null) {
                                    Date date = appointment.getDate().toDate();
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
                                    // May not have time information in the older format
                                    dateStr = dateFormat.format(date);
                                    timeStr = appointment.getTime() != null ? appointment.getTime() : "Time not specified";
                                }

                                // Format the date more prominently
                                TextView dateText = new TextView(this);
                                dateText.setText(dateStr);
                                dateText.setTextSize(16);
                                dateText.setTextColor(getResources().getColor(R.color.colorPrimary));
                                dateText.setTypeface(null, Typeface.BOLD);

                                TextView timeText = new TextView(this);
                                timeText.setText(" • " + timeStr);
                                timeText.setTextSize(16);
                                timeText.setTextColor(getResources().getColor(R.color.colorPrimary));

                                dateTimeLayout.addView(dateText);
                                dateTimeLayout.addView(timeText);
                                contentLayout.addView(dateTimeLayout);

                                // Doctor name
                                TextView doctorText = new TextView(this);
                                doctorText.setText(appointment.getDoctorName());
                                doctorText.setTextSize(18);
                                doctorText.setTypeface(null, Typeface.BOLD);
                                doctorText.setTextColor(getResources().getColor(R.color.black));
                                doctorText.setPadding(0, 12, 0, 4);
                                contentLayout.addView(doctorText);

                                // Appointment type
                                TextView typeText = new TextView(this);
                                typeText.setText(appointment.getAppointmentType());
                                typeText.setTextSize(16);
                                typeText.setTextColor(Color.GRAY);
                                contentLayout.addView(typeText);

                                // Status indicator if present
                                if (appointment.getStatus() != null && !appointment.getStatus().equals("SCHEDULED")) {
                                    TextView statusText = new TextView(this);
                                    statusText.setText("Status: " + appointment.getStatus());
                                    statusText.setTextSize(14);
                                    statusText.setPadding(0, 8, 0, 0);

                                    if (appointment.getStatus().equals("CANCELLED")) {
                                        statusText.setTextColor(Color.RED);
                                    } else if (appointment.getStatus().equals("COMPLETED")) {
                                        statusText.setTextColor(Color.parseColor("#4CAF50")); // Green
                                    }

                                    contentLayout.addView(statusText);
                                }

                                // Add notes if present
                                if (appointment.getNotes() != null && !appointment.getNotes().isEmpty()) {
                                    TextView notesLabel = new TextView(this);
                                    notesLabel.setText("NOTES");
                                    notesLabel.setTextSize(12);
                                    notesLabel.setTypeface(null, Typeface.BOLD);
                                    notesLabel.setTextColor(Color.GRAY);
                                    notesLabel.setPadding(0, 16, 0, 4);
                                    contentLayout.addView(notesLabel);

                                    TextView notesText = new TextView(this);
                                    notesText.setText(appointment.getNotes());
                                    notesText.setTextSize(14);
                                    notesText.setTextColor(getResources().getColor(R.color.black));
                                    contentLayout.addView(notesText);
                                }

                                // Add space before buttons
                                Space space = new Space(this);
                                space.setMinimumHeight(16);
                                contentLayout.addView(space);

                                // Only show action buttons for scheduled appointments
                                if (appointment.getStatus() == null || appointment.getStatus().equals("SCHEDULED")) {
                                    // Button container for better layout
                                    LinearLayout buttonContainer = new LinearLayout(this);
                                    buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
                                    buttonContainer.setGravity(Gravity.END);

                                    // Reschedule button
                                    Button rescheduleButton = new Button(this);
                                    rescheduleButton.setText("Reschedule");
                                    rescheduleButton.setBackgroundColor(Color.TRANSPARENT);
                                    rescheduleButton.setTextColor(getResources().getColor(R.color.colorPrimary));
                                    rescheduleButton.setAllCaps(false);

                                    final String appointmentId = appointment.getId();
                                    rescheduleButton.setOnClickListener(v -> {
                                        Toast.makeText(PatientDashboardActivity.this,
                                                "Reschedule request initiated", Toast.LENGTH_SHORT).show();
                                        // In a real app, this would show a rescheduling dialog
                                    });

                                    // Cancel button with styled appearance
                                    Button cancelButton = new Button(this);
                                    cancelButton.setText("Cancel");
                                    cancelButton.setBackgroundColor(Color.TRANSPARENT);
                                    cancelButton.setTextColor(getResources().getColor(R.color.colorAccent));
                                    cancelButton.setAllCaps(false);
                                    cancelButton.setOnClickListener(v -> {
                                        // Create confirmation dialog
                                        new AlertDialog.Builder(PatientDashboardActivity.this)
                                                .setTitle("Cancel Appointment")
                                                .setMessage("Are you sure you want to cancel your appointment with " +
                                                        appointment.getDoctorName() + "?")
                                                .setPositiveButton("Yes", (dialog, which) -> {
                                                    cancelAppointment(appointmentId);
                                                })
                                                .setNegativeButton("No", null)
                                                .show();
                                    });

                                    buttonContainer.addView(rescheduleButton);
                                    buttonContainer.addView(cancelButton);
                                    contentLayout.addView(buttonContainer);
                                }

                                cardLayout.addView(contentLayout);
                                card.addView(cardLayout);

                                // Add the card to the main layout
                                appointmentsLayout.addView(card);
                            }
                        }

                        // Add space before button
                        Space space = new Space(this);
                        space.setMinimumHeight(24);
                        appointmentsLayout.addView(space);

                        // Add a styled "Book New Appointment" button
                        Button bookButton = new Button(this);
                        bookButton.setText("Book New Appointment");
                        bookButton.setTextColor(Color.WHITE);
                        bookButton.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                        bookButton.setAllCaps(false);
                        bookButton.setTextSize(16);

                        // Rounded corners for the button
                        GradientDrawable gradientDrawable = new GradientDrawable();
                        gradientDrawable.setColor(getResources().getColor(R.color.colorPrimary));
                        gradientDrawable.setCornerRadius(30);
                        bookButton.setBackground(gradientDrawable);

                        // Add padding to button
                        bookButton.setPadding(32, 24, 32, 24);

                        // Add margin to button
                        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        buttonParams.setMargins(16, 16, 16, 16);
                        bookButton.setLayoutParams(buttonParams);

                        // Add click listener
                        bookButton.setOnClickListener(v -> showNewBookAppointmentDialog());

                        appointmentsLayout.addView(bookButton);
                    });
        } catch (Exception e) {
            // Remove loading layout
            appointmentsLayout.removeView(loadingLayout);

            // Show error message
            TextView errorText = new TextView(this);

            // Check if this is an index error
            if (com.example.patienttracker.utils.FirestoreIndexHelper.isIndexError(e)) {
                // Show the index helper dialog
                com.example.patienttracker.utils.FirestoreIndexHelper.showIndexHelperDialog(
                        PatientDashboardActivity.this, e);

                errorText.setText("Missing Firestore index. Please create the required index.");
            } else {
                errorText.setText("Error loading appointments: " + e.getMessage());
            }

            errorText.setTextSize(16);
            errorText.setTextColor(Color.RED);
            errorText.setGravity(Gravity.CENTER);
            errorText.setPadding(32, 100, 32, 32);
            appointmentsLayout.addView(errorText);

            // Add "Book New Appointment" button even in error state
            Space space = new Space(this);
            space.setMinimumHeight(24);
            appointmentsLayout.addView(space);

            Button bookButton = new Button(this);
            bookButton.setText("Book New Appointment");
            bookButton.setTextColor(Color.WHITE);
            bookButton.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
            bookButton.setAllCaps(false);
            bookButton.setTextSize(16);

            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(getResources().getColor(R.color.colorPrimary));
            gradientDrawable.setCornerRadius(30);
            bookButton.setBackground(gradientDrawable);

            bookButton.setPadding(32, 24, 32, 24);

            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            buttonParams.setMargins(16, 16, 16, 16);
            bookButton.setLayoutParams(buttonParams);

            bookButton.setOnClickListener(v -> showNewBookAppointmentDialog());

            appointmentsLayout.addView(bookButton);
        }

        // Note: We already added the "Book New Appointment" button in both the
        // success and error case handlers above. No need to add it again here.

        // Note: We already added the appointments layout to the scroll view at line 199
        // and added the scroll view to the main layout at line 200.
        // No need to do it again as ScrollView can only have one direct child.
    }

    // Removed duplicate method showNewBookAppointmentDialog - the implementation is at line ~1017

    /**
     * Cancels an appointment by ID
     */
    private void cancelAppointment(String appointmentId) {
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage("Cancelling appointment...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        // Update Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("appointments").document(appointmentId)
                .update("status", "CANCELLED")
                .addOnSuccessListener(aVoid -> {
                    loadingDialog.dismiss();
                    Toast.makeText(PatientDashboardActivity.this,
                            "Appointment successfully cancelled", Toast.LENGTH_SHORT).show();

                    // Refresh the appointments view
                    displayAppointmentsView();

                    // Create a notification for the doctor
                    db.collection("appointments").document(appointmentId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                // Don't use toObject to avoid potential @DocumentId issues
                                Appointment appointment = createAppointmentFromDocument(documentSnapshot);
                                if (appointment != null && appointment.getDoctorId() != null) {
                                    // Normalize date/time format
                                    appointment.normalizeDateTime();
                                    // Create a notification for the doctor
                                    Map<String, Object> notification = new HashMap<>();
                                    notification.put("title", "Appointment Cancelled");
                                    notification.put("message", "Patient " + currentUser.getName() +
                                            " has cancelled their " + appointment.getAppointmentType() + " appointment.");
                                    notification.put("senderName", "Patient Tracker System");
                                    notification.put("timestamp", Timestamp.now());
                                    notification.put("recipientUids", Arrays.asList(appointment.getDoctorId()));

                                    // Save notification
                                    db.collection("notifications")
                                            .add(notification)
                                            .addOnFailureListener(e -> {
                                                // Non-critical error, don't display to user
                                                android.util.Log.e("PatientDashboard", "Failed to send notification: " + e.getMessage());
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    Toast.makeText(PatientDashboardActivity.this,
                            "Failed to cancel appointment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void createAppointment(User doctor, String appointmentType, Date appointmentDate, String notes) {
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage("Booking appointment...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        try {
            // Create Firestore timestamp from Date
            Timestamp appointmentTimestamp = new Timestamp(appointmentDate);

            // Create appointment object
            Appointment appointment = new Appointment(
                    currentUser.getId(),
                    currentUser.getName(),
                    doctor.getId(),
                    doctor.getName(),
                    appointmentTimestamp,
                    appointmentType,
                    notes
            );

            // Save to Firestore
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("appointments")
                    .add(appointment)
                    .addOnSuccessListener(documentReference -> {
                        loadingDialog.dismiss();

                        // Create a notification for the doctor
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("title", "New Appointment Request");
                        notification.put("message", "You have a new appointment request from " +
                                currentUser.getName() + " for " + appointmentType);
                        notification.put("senderName", "Patient Tracker System");
                        notification.put("timestamp", Timestamp.now());
                        notification.put("recipientUids", Arrays.asList(doctor.getId()));

                        // Save notification
                        db.collection("notifications")
                                .add(notification)
                                .addOnSuccessListener(notificationRef -> {
                                    // Refresh appointments view
                                    displayAppointmentsView();

                                    // Show success message
                                    Toast.makeText(PatientDashboardActivity.this,
                                            "Appointment booked successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(PatientDashboardActivity.this,
                                            "Appointment booked but failed to notify doctor: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        Toast.makeText(PatientDashboardActivity.this,
                                "Failed to book appointment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            loadingDialog.dismiss();
            Toast.makeText(this, "Error creating appointment: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Helper method to get appropriate color for appointment type
    private int getAppointmentColor(String appointmentType) {
        switch (appointmentType) {
            case "General Checkup":
                return Color.parseColor("#4CAF50"); // Green
            case "Dental Cleaning":
                return Color.parseColor("#2196F3"); // Blue
            case "Vision Test":
                return Color.parseColor("#9C27B0"); // Purple
            case "Specialist Consultation":
                return Color.parseColor("#FF9800"); // Orange
            default:
                return getResources().getColor(R.color.colorPrimary);
        }
    }

    /**
     * Shows dialog to book a new appointment
     */
    private void showNewBookAppointmentDialog() {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Book an Appointment");

        // Create a scrollable linear layout for the dialog
        ScrollView scrollView = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        scrollView.addView(layout);

        // Doctor selection spinner
        TextView doctorLabel = new TextView(this);
        doctorLabel.setText("Select a Doctor:");
        doctorLabel.setTextSize(16);
        doctorLabel.setPadding(0, 20, 0, 10);
        layout.addView(doctorLabel);

        Spinner doctorSpinner = new Spinner(this);
        layout.addView(doctorSpinner);

        // Load doctors from Firestore (in real implementation)
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<User> doctors = new ArrayList<>();

        // Show loading indicator in spinner
        ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Loading doctors..."}
        );
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        doctorSpinner.setAdapter(loadingAdapter);

        // Query Firestore for doctors
        db.collection("users")
                .whereEqualTo("role", User.ROLE_DOCTOR)
                .whereEqualTo("status", User.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    doctors.clear();

                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        // Don't use toObject to avoid @DocumentId issues
                        User doctor = createUserFromDocument(document);
                        if (doctor != null) {
                            doctors.add(doctor);
                        }
                    }

                    if (doctors.isEmpty()) {
                        // No doctors found
                        ArrayAdapter<String> noDoctorAdapter = new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                new String[]{"No doctors available"}
                        );
                        noDoctorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        doctorSpinner.setAdapter(noDoctorAdapter);
                    } else {
                        // Create array of doctor names for spinner
                        List<String> doctorNames = new ArrayList<>();
                        for (User doctor : doctors) {
                            doctorNames.add(doctor.getName());
                        }

                        // Set up spinner adapter
                        ArrayAdapter<String> doctorAdapter = new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                doctorNames
                        );
                        doctorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        doctorSpinner.setAdapter(doctorAdapter);
                    }
                })
                .addOnFailureListener(e -> {
                    // Error loading doctors
                    ArrayAdapter<String> errorAdapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            new String[]{"Error loading doctors"}
                    );
                    errorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    doctorSpinner.setAdapter(errorAdapter);

                    Toast.makeText(this, "Error loading doctors: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });

        // Add space
        Space space1 = new Space(this);
        space1.setMinimumHeight(20);
        layout.addView(space1);

        // Create spinner for appointment type
        TextView typeLabel = new TextView(this);
        typeLabel.setText("Appointment Type:");
        typeLabel.setTextColor(getResources().getColor(R.color.black));
        layout.addView(typeLabel);

        Spinner typeSpinner = new Spinner(this);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"General Checkup", "Dental Cleaning", "Vision Test", "Specialist Consultation"}
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        layout.addView(typeSpinner);

        // Add space
        Space space2 = new Space(this);
        space2.setMinimumHeight(20);
        layout.addView(space2);

        // Create date picker
        TextView dateLabel = new TextView(this);
        dateLabel.setText("Select Date:");
        dateLabel.setTextColor(getResources().getColor(R.color.black));
        layout.addView(dateLabel);

        EditText dateText = new EditText(this);
        dateText.setHint("MM/DD/YYYY");
        dateText.setInputType(InputType.TYPE_CLASS_DATETIME);
        layout.addView(dateText);

        // Add space
        Space space3 = new Space(this);
        space3.setMinimumHeight(20);
        layout.addView(space3);

        // Create time picker
        TextView timeLabel = new TextView(this);
        timeLabel.setText("Select Time:");
        timeLabel.setTextColor(getResources().getColor(R.color.black));
        layout.addView(timeLabel);

        Spinner timeSpinner = new Spinner(this);
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"9:00 AM", "10:00 AM", "11:00 AM", "1:00 PM", "2:00 PM", "3:00 PM", "4:00 PM"}
        );
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(timeAdapter);
        layout.addView(timeSpinner);

        // Notes field
        TextView notesLabel = new TextView(this);
        notesLabel.setText("Notes (optional):");
        notesLabel.setTextColor(getResources().getColor(R.color.black));
        notesLabel.setPadding(0, 20, 0, 10);
        layout.addView(notesLabel);

        EditText notesText = new EditText(this);
        notesText.setHint("Any additional information for the doctor");
        notesText.setLines(3);
        notesText.setGravity(Gravity.TOP);
        layout.addView(notesText);

        builder.setView(scrollView);

        // Add book button
        builder.setPositiveButton("Book Appointment", (dialog, which) -> {
            try {
                // Get selected doctor
                int doctorPosition = doctorSpinner.getSelectedItemPosition();
                if (doctors.isEmpty() || doctorPosition < 0 || doctorPosition >= doctors.size()) {
                    Toast.makeText(this, "Please select a doctor", Toast.LENGTH_SHORT).show();
                    return;
                }
                User selectedDoctor = doctors.get(doctorPosition);

                // Get appointment type
                String appointmentType = typeSpinner.getSelectedItem().toString();

                // Parse date and time
                String dateString = dateText.getText().toString();
                String timeString = timeSpinner.getSelectedItem().toString();

                if (dateString.isEmpty()) {
                    Toast.makeText(this, "Please enter a date", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Simple date parsing - in a real app, use a DatePickerDialog
                SimpleDateFormat inputFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US);
                Date appointmentDate = inputFormat.parse(dateString + " " + timeString);

                // Get notes
                String notes = notesText.getText().toString();

                // Create appointment
                createAppointment(selectedDoctor, appointmentType, appointmentDate, notes);

                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Add cancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        // Show dialog
        builder.create().show();
    }

    private void displayMedicalRecordsView() {
        // Clear previous views
        mainLayout.removeAllViews();

        // Create a MedicalRecordsFragment to display actual medical records from Firebase
        MedicalRecordsFragment medicalRecordsFragment = new MedicalRecordsFragment();

        // Pass current user to the fragment
        Bundle args = new Bundle();
        args.putSerializable("USER", currentUser);
        medicalRecordsFragment.setArguments(args);

        // Use FragmentManager to add the MedicalRecordsFragment to the layout
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.frame_container, medicalRecordsFragment)
                .commit();

        // Set title in ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Medical Records");
        }
    }

    private void showMedicalRecordDetailsDialog(String[] record) {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(record[0] + " Details");

        // Create a linear layout for the dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Add detailed information
        TextView doctorLabel = new TextView(this);
        doctorLabel.setText("Doctor: " + record[1]);
        doctorLabel.setTextSize(16);
        doctorLabel.setTypeface(null, Typeface.BOLD);
        doctorLabel.setTextColor(getResources().getColor(R.color.black));
        layout.addView(doctorLabel);

        TextView dateLabel = new TextView(this);
        dateLabel.setText("Date: " + record[2]);
        dateLabel.setTextSize(16);
        dateLabel.setTextColor(getResources().getColor(R.color.black));
        layout.addView(dateLabel);

        TextView resultsLabel = new TextView(this);
        resultsLabel.setText("Results:");
        resultsLabel.setTextSize(16);
        resultsLabel.setTypeface(null, Typeface.BOLD);
        resultsLabel.setPadding(0, 20, 0, 0);
        resultsLabel.setTextColor(getResources().getColor(R.color.black));
        layout.addView(resultsLabel);

        TextView resultsText = new TextView(this);
        resultsText.setText(record[3] + "\n\nAdditional notes from the doctor:\n" +
                "Patient reported feeling well. Recommend follow-up visit in 6 months and " +
                "continuing current medication regimen. Advised to maintain regular exercise " +
                "and healthy diet.");
        resultsText.setTextSize(16);
        resultsText.setTextColor(getResources().getColor(R.color.black));
        layout.addView(resultsText);

        builder.setView(layout);

        // Add button
        builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        // Show dialog
        builder.create().show();
    }

    private void showRequestRecordsDialog() {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Request Medical Records");

        // Create a linear layout for the dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Add instructions
        TextView instructionsText = new TextView(this);
        instructionsText.setText("Select the type of medical records you would like to request:");
        instructionsText.setTextSize(16);
        instructionsText.setTextColor(getResources().getColor(R.color.black));
        layout.addView(instructionsText);

        // Add checkboxes
        String[] recordTypes = {
                "Complete Medical History",
                "Lab Results",
                "X-Rays and Imaging",
                "Prescription History",
                "Immunization Records"
        };

        for (String type : recordTypes) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(type);
            checkBox.setTextSize(16);
            checkBox.setTextColor(getResources().getColor(R.color.black));
            layout.addView(checkBox);
        }

        // Add space
        Space space = new Space(this);
        space.setMinimumHeight(20);
        layout.addView(space);

        // Add notes field
        TextView notesLabel = new TextView(this);
        notesLabel.setText("Additional Notes:");
        notesLabel.setTextSize(16);
        notesLabel.setTextColor(getResources().getColor(R.color.black));
        layout.addView(notesLabel);

        EditText notesText = new EditText(this);
        notesText.setHint("Any specific details about your request...");
        notesText.setMinLines(3);
        notesText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        layout.addView(notesText);

        builder.setView(layout);

        // Add buttons
        builder.setPositiveButton("Submit Request", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(PatientDashboardActivity.this,
                        "Your medical records request has been submitted. You will be notified when they are available.",
                        Toast.LENGTH_LONG).show();
                dialog.dismiss();
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        // Show dialog
        builder.create().show();
    }

    private void displayProfileView() {
        // Clear previous views
        mainLayout.removeAllViews();

        // Show loading indicator
        showProgressIndicator(true);

        // Fetch the complete user profile from Firestore before creating the fragment
        FirebaseFirestore.getInstance().collection("users")
                .document(currentUser.getId())
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

                    // Create new ProfileFragment instance
                    ProfileFragment profileFragment = new ProfileFragment();

                    // Pass the complete user to the fragment
                    Bundle args = new Bundle();
                    args.putSerializable("USER", fullUserProfile);
                    profileFragment.setArguments(args);

                    // Add the fragment to the layout
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frame_container, profileFragment)
                            .commit();
                })
                .addOnFailureListener(e -> {
                    // Hide loading indicator
                    showProgressIndicator(false);

                    Log.e(TAG, "Error fetching user profile: " + e.getMessage(), e);
                    Toast.makeText(PatientDashboardActivity.this,
                            "Failed to load profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    // Create the fragment with the basic user info we have
                    ProfileFragment profileFragment = new ProfileFragment();
                    Bundle args = new Bundle();
                    args.putSerializable("USER", currentUser);
                    profileFragment.setArguments(args);

                    // Add the fragment to the layout
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frame_container, profileFragment)
                            .commit();
                });
    }

    private void displayNotificationsView() {
        // Clear previous views
        mainLayout.removeAllViews();

        // Create a linear layout for the notifications view
        LinearLayout notificationsLayout = new LinearLayout(this);
        notificationsLayout.setOrientation(LinearLayout.VERTICAL);
        notificationsLayout.setPadding(16, 16, 16, 16);

        // Add a title
        TextView titleText = new TextView(this);
        titleText.setText("My Notifications");
        titleText.setTextSize(22);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 20);
        notificationsLayout.addView(titleText);

        // Add Mark All as Read button (initially hidden)
        Button markAllReadButton = new Button(this);
        markAllReadButton.setText("Mark All as Read");
        markAllReadButton.setOnClickListener(v -> markAllNotificationsAsRead());
        markAllReadButton.setVisibility(View.GONE); // Initially hidden until we have notifications
        notificationsLayout.addView(markAllReadButton);

        // Add spacing
        View spacer = new View(this);
        spacer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                20)); // 20dp height
        notificationsLayout.addView(spacer);

        // Add loading text initially
        TextView loadingText = new TextView(this);
        loadingText.setText("Loading notifications...");
        loadingText.setTextSize(16);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, 100, 0, 0);
        notificationsLayout.addView(loadingText);

        // Create RecyclerView for notifications
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        notificationsLayout.addView(recyclerView);

        // Add the notifications layout to the main layout
        mainLayout.addView(notificationsLayout);

        // Load notifications from Firestore
        loadNotificationsFromFirestore(recyclerView, loadingText, markAllReadButton);
    }

    /**
     * Mark all notifications as read
     */
    private void markAllNotificationsAsRead() {
        // Get current user ID
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a batch operation for efficient updates
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.WriteBatch batch = db.batch();

        // Query for all unread notifications for this user
        db.collection("notifications")
                .whereArrayContains("recipientUids", currentUserId)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No unread notifications", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Add each notification to the batch update
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        batch.update(document.getReference(), "isRead", true);
                    }

                    // Commit the batch
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show();
                                // Refresh the notifications view
                                displayNotificationsView();
                                // Update badge count (should be zero now)
                                updateNotificationBadge();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadNotificationsFromFirestore(RecyclerView recyclerView, TextView loadingText, Button markAllReadButton) {
        // Get current user ID
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            loadingText.setText("Error: User not authenticated");
            recyclerView.setVisibility(View.GONE);
            markAllReadButton.setVisibility(View.GONE);
            return;
        }

        // Create the list to hold notifications
        List<Map<String, Object>> notificationsList = new ArrayList<>();

        // Query Firestore for notifications where recipient array contains this user's ID
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("notifications")
                .whereArrayContains("recipientUids", currentUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING) // Add document ID ordering to match the required index
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        // No notifications found
                        loadingText.setText("No notifications at this time");
                        recyclerView.setVisibility(View.GONE);
                        markAllReadButton.setVisibility(View.GONE);
                    } else {
                        // Process notifications
                        boolean hasUnreadNotifications = false;
                        for (DocumentSnapshot document : queryDocumentSnapshots) {
                            Map<String, Object> notification = document.getData();
                            notification.put("id", document.getId());
                            notificationsList.add(notification);

                            // Check if this notification is unread
                            if (notification.containsKey("isRead")) {
                                Boolean isRead = (Boolean) notification.get("isRead");
                                if (isRead != null && !isRead) {
                                    hasUnreadNotifications = true;
                                }
                            } else {
                                // Default to unread if not specified
                                hasUnreadNotifications = true;
                            }
                        }

                        // Display notifications in the RecyclerView
                        displayNotifications(recyclerView, notificationsList);
                        loadingText.setVisibility(View.GONE);

                        // Show mark all as read button if we have unread notifications
                        markAllReadButton.setVisibility(hasUnreadNotifications ? View.VISIBLE : View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    // Check if this is an index error
                    if (com.example.patienttracker.utils.FirestoreIndexHelper.isIndexError(e)) {
                        // Show the index helper dialog
                        com.example.patienttracker.utils.FirestoreIndexHelper.showIndexHelperDialog(
                                PatientDashboardActivity.this, e);

                        loadingText.setText("Missing Firestore index. Please create the required index.");
                    } else {
                        // Some other error
                        loadingText.setText("Error loading notifications: " + e.getMessage());
                    }
                    recyclerView.setVisibility(View.GONE);
                    markAllReadButton.setVisibility(View.GONE);
                });
    }

    private void displayNotifications(RecyclerView recyclerView, List<Map<String, Object>> notificationsList) {
        recyclerView.setAdapter(new RecyclerView.Adapter<NotificationViewHolder>() {
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

                // Get notification data
                String title = notification.containsKey("title") ? notification.get("title").toString() : "Notification";
                String message = notification.containsKey("message") ? notification.get("message").toString() : "";
                String senderName = notification.containsKey("senderName") ? notification.get("senderName").toString() : "Admin";
                boolean isRead = notification.containsKey("isRead") ? (boolean) notification.get("isRead") : false;

                // Get timestamp if available
                String timeText = "";
                if (notification.containsKey("timestamp")) {
                    Object timestamp = notification.get("timestamp");
                    if (timestamp instanceof com.google.firebase.Timestamp) {
                        com.google.firebase.Timestamp firebaseTimestamp = (com.google.firebase.Timestamp) timestamp;
                        Date date = firebaseTimestamp.toDate();
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                        timeText = sdf.format(date);
                    }
                }

                // Clear previous views
                ((LinearLayout) holder.cardView.getChildAt(0)).removeAllViews();
                LinearLayout layout = (LinearLayout) holder.cardView.getChildAt(0);

                // Change background color based on read status
                if (!isRead) {
                    holder.cardView.setCardBackgroundColor(
                            getResources().getColor(android.R.color.holo_blue_light, getTheme()));
                } else {
                    holder.cardView.setCardBackgroundColor(
                            getResources().getColor(android.R.color.white, getTheme()));
                }

                // Create and add title TextView
                TextView titleView = new TextView(holder.cardView.getContext());
                titleView.setText(title);
                titleView.setTypeface(null, Typeface.BOLD);
                titleView.setTextSize(18);
                titleView.setTextColor(getResources().getColor(R.color.text_primary));
                layout.addView(titleView);

                // Create and add message TextView
                TextView messageView = new TextView(holder.cardView.getContext());
                messageView.setText(message);
                messageView.setTextSize(14);
                messageView.setPadding(0, 10, 0, 10);
                messageView.setTextColor(getResources().getColor(R.color.text_secondary));
                layout.addView(messageView);

                // Create and add sender and time TextView
                TextView infoView = new TextView(holder.cardView.getContext());
                String infoText = "From: " + senderName;
                if (!timeText.isEmpty()) {
                    infoText += " • " + timeText;
                }
                infoView.setText(infoText);
                infoView.setTextSize(12);
                infoView.setTypeface(null, Typeface.ITALIC);
                infoView.setTextColor(getResources().getColor(R.color.text_secondary));
                layout.addView(infoView);

                // Add mark as read button if not read
                if (!isRead) {
                    Button markReadBtn = new Button(holder.cardView.getContext());
                    markReadBtn.setText("Mark as Read");
                    markReadBtn.setTextSize(14);

                    markReadBtn.setOnClickListener(v -> {
                        // Get the notification ID
                        if (notification.containsKey("id")) {
                            String notificationId = notification.get("id").toString();

                            // Update the notification in Firestore
                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                            db.collection("notifications").document(notificationId)
                                    .update("isRead", true)
                                    .addOnSuccessListener(aVoid -> {
                                        // Update locally
                                        notification.put("isRead", true);
                                        holder.cardView.setCardBackgroundColor(
                                                getResources().getColor(android.R.color.white, getTheme()));
                                        layout.removeView(markReadBtn);

                                        // Show toast
                                        Toast.makeText(PatientDashboardActivity.this,
                                                "Notification marked as read", Toast.LENGTH_SHORT).show();

                                        // Update badge count
                                        updateNotificationBadge();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(PatientDashboardActivity.this,
                                                "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    });

                    layout.addView(markReadBtn);
                }
            }

            @Override
            public int getItemCount() {
                return notificationsList.size();
            }
        });

        recyclerView.setVisibility(View.VISIBLE);
    }

    private static class NotificationViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
        }
    }

    /**
     * Update the notification badge with the count of unread notifications
     */
    private void updateNotificationBadge() {
        // Get current user ID
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null || bottomNavigationView == null) {
            return;
        }

        // Query Firestore for unread notifications
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("notifications")
                .whereArrayContains("recipientUids", currentUserId)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int unreadCount = queryDocumentSnapshots.size();
                    // Update the badge
                    BadgeHelper.showBadge(bottomNavigationView, R.id.nav_notifications, unreadCount);
                })
                .addOnFailureListener(e -> {
                    Log.e("PatientDashboard", "Error counting notifications: " + e.getMessage(), e);
                });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.nav_appointments) {
            displayAppointmentsView();
            return true;
        } else if (itemId == R.id.nav_medical_records) {
            displayMedicalRecordsView();
            return true;
        } else if (itemId == R.id.nav_profile) {
            displayProfileView();
            return true;
        } else if (itemId == R.id.nav_notifications) {
            // Remove the badge when navigating to notifications
            BadgeHelper.removeBadge(bottomNavigationView, R.id.nav_notifications);
            displayNotificationsView();
            return true;
        }

        return false;
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
     * Show or hide the progress indicator overlay
     *
     * @param show True to show the progress indicator, false to hide it
     */
    private void showProgressIndicator(boolean show) {
        if (progressOverlay != null) {
            progressOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Detach the Firestore listener to prevent memory leaks
        if (appointmentsListener != null) {
            appointmentsListener.remove();
            appointmentsListener = null;
            Log.d("PatientDashboard", "Firestore listener detached");
        }
    }

    /**
     * Handle back button press
     */
    @Override
    public void onBackPressed() {
        // If we're not on the appointments tab, go to appointments
        // (maintaining original functionality even with reordered tabs)
        if (bottomNavigationView.getSelectedItemId() != R.id.nav_appointments) {
            bottomNavigationView.setSelectedItemId(R.id.nav_appointments);
        } else {
            // Otherwise, exit the app
            super.onBackPressed();
        }
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
            } else if (userData.containsKey("name")) {
                user.setFullName((String) userData.get("name"));
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
            } else if (userData.containsKey("profileImageUrl")) {
                user.setPhotoUrl((String) userData.get("profileImageUrl"));
            }
            if (userData.containsKey("phone")) {
                // Decrypt phone number from database
                String encryptedPhone = (String) userData.get("phone");
                String decryptedPhone = com.example.patienttracker.utils.EncryptionUtil.decryptData(encryptedPhone);
                user.setPhone(decryptedPhone);
            }

            // Support both doctor and patient specific fields
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

            // Add physical information
            if (userData.containsKey("address")) {
                user.setAddress((String) userData.get("address"));
            }
            if (userData.containsKey("gender")) {
                user.setGender((String) userData.get("gender"));
            }
            if (userData.containsKey("dateOfBirth")) {
                user.setDateOfBirth((String) userData.get("dateOfBirth"));
            }
            if (userData.containsKey("fathersName")) {
                user.setFathersName((String) userData.get("fathersName"));
            }
            if (userData.containsKey("bloodType")) {
                user.setBloodType((String) userData.get("bloodType"));
            }

            // Handle numeric measurements with different possible types
            if (userData.containsKey("weight")) {
                Object weightObj = userData.get("weight");
                if (weightObj instanceof Double) {
                    user.setWeight((Double) weightObj);
                } else if (weightObj instanceof Long) {
                    user.setWeight(((Long) weightObj).doubleValue());
                } else if (weightObj instanceof Integer) {
                    user.setWeight(((Integer) weightObj).doubleValue());
                } else if (weightObj instanceof String) {
                    try {
                        user.setWeight(Double.parseDouble((String) weightObj));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Could not parse weight from string: " + weightObj);
                    }
                }
            }

            if (userData.containsKey("height")) {
                Object heightObj = userData.get("height");
                if (heightObj instanceof Double) {
                    user.setHeight((Double) heightObj);
                } else if (heightObj instanceof Long) {
                    user.setHeight(((Long) heightObj).doubleValue());
                } else if (heightObj instanceof Integer) {
                    user.setHeight(((Integer) heightObj).doubleValue());
                } else if (heightObj instanceof String) {
                    try {
                        user.setHeight(Double.parseDouble((String) heightObj));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Could not parse height from string: " + heightObj);
                    }
                }
            }

            // Print user data for debugging
            Log.d(TAG, "Created user from document: " + user.toString());

            return user;
        } catch (Exception e) {
            Log.e(TAG, "Error creating user from document: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Helper method to manually create an Appointment from a Firestore DocumentSnapshot
     * to avoid issues with @DocumentId and other Firestore deserialization problems
     */
    private Appointment createAppointmentFromDocument(DocumentSnapshot documentSnapshot) {
        try {
            Map<String, Object> data = documentSnapshot.getData();
            if (data == null) {
                Log.e(TAG, "createAppointmentFromDocument: data is null");
                return null;
            }

            Appointment appointment = new Appointment();
            appointment.setId(documentSnapshot.getId()); // Set document ID

            // Map patient info
            if (data.containsKey("patientId")) {
                appointment.setPatientId((String) data.get("patientId"));
            }
            if (data.containsKey("patientName")) {
                appointment.setPatientName((String) data.get("patientName"));
            }

            // Map doctor info
            if (data.containsKey("doctorId")) {
                appointment.setDoctorId((String) data.get("doctorId"));
            }
            if (data.containsKey("doctorName")) {
                appointment.setDoctorName((String) data.get("doctorName"));
            }

            // Map date/time fields
            if (data.containsKey("dateTime")) {
                Object dateTimeObj = data.get("dateTime");
                if (dateTimeObj instanceof Timestamp) {
                    appointment.setDateTime((Timestamp) dateTimeObj);
                } else if (dateTimeObj instanceof Map) {
                    // Handle server timestamp
                    Map<String, Object> timestampMap = (Map<String, Object>) dateTimeObj;
                    if (timestampMap.containsKey("seconds") && timestampMap.containsKey("nanoseconds")) {
                        long seconds = ((Number) timestampMap.get("seconds")).longValue();
                        int nanoseconds = ((Number) timestampMap.get("nanoseconds")).intValue();
                        appointment.setDateTime(new Timestamp(seconds, nanoseconds));
                    }
                } else if (dateTimeObj != null) {
                    Log.w("PatientDashboardActivity", "DateTime is not a Timestamp or Map: " + dateTimeObj.getClass().getName());
                }
            }

            if (data.containsKey("date")) {
                Object dateObj = data.get("date");
                if (dateObj instanceof Timestamp) {
                    appointment.setDate((Timestamp) dateObj);
                } else if (dateObj instanceof Map) {
                    // Handle server timestamp
                    Map<String, Object> timestampMap = (Map<String, Object>) dateObj;
                    if (timestampMap.containsKey("seconds") && timestampMap.containsKey("nanoseconds")) {
                        long seconds = ((Number) timestampMap.get("seconds")).longValue();
                        int nanoseconds = ((Number) timestampMap.get("nanoseconds")).intValue();
                        appointment.setDate(new Timestamp(seconds, nanoseconds));
                    }
                } else if (dateObj != null) {
                    Log.w("PatientDashboardActivity", "Date is not a Timestamp or Map: " + dateObj.getClass().getName());
                }
            }
            if (data.containsKey("time")) {
                appointment.setTime((String) data.get("time"));
            }

            // Map appointment details
            if (data.containsKey("appointmentType")) {
                appointment.setAppointmentType((String) data.get("appointmentType"));
            }
            if (data.containsKey("notes")) {
                appointment.setNotes((String) data.get("notes"));
            }
            if (data.containsKey("reason")) {
                appointment.setReason((String) data.get("reason"));
            }
            if (data.containsKey("status")) {
                appointment.setStatus((String) data.get("status"));
            }
            if (data.containsKey("createdAt")) {
                Object createdAtObj = data.get("createdAt");
                if (createdAtObj instanceof Timestamp) {
                    appointment.setCreatedAt((Timestamp) createdAtObj);
                } else if (createdAtObj instanceof Map) {
                    // Handle server timestamp
                    Map<String, Object> timestampMap = (Map<String, Object>) createdAtObj;
                    if (timestampMap.containsKey("seconds") && timestampMap.containsKey("nanoseconds")) {
                        long seconds = ((Number) timestampMap.get("seconds")).longValue();
                        int nanoseconds = ((Number) timestampMap.get("nanoseconds")).intValue();
                        appointment.setCreatedAt(new Timestamp(seconds, nanoseconds));
                    }
                } else if (createdAtObj != null) {
                    Log.w("PatientDashboardActivity", "CreatedAt is not a Timestamp or Map: " + createdAtObj.getClass().getName());
                }
            }

            return appointment;
        } catch (Exception e) {
            Log.e(TAG, "Error creating appointment from document: " + e.getMessage(), e);
            return null;
        }
    }
}