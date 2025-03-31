package com.example.patienttracker.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patienttracker.R;
import com.example.patienttracker.adapters.MedicalRecordAdapter;
import com.example.patienttracker.models.MedicalRecord;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.DateTimeUtils;
import com.example.patienttracker.utils.PdfGenerator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fragment to display and manage medical records.
 * Patients can view their own records.
 * Doctors can view and add records for their patients.
 */
public class MedicalRecordsFragment extends Fragment implements MedicalRecordAdapter.OnRecordClickListener {

    private static final String TAG = "MedicalRecordsFragment";
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddRecord;
    private List<MedicalRecord> recordsList;
    private MedicalRecordAdapter recordAdapter;
    private User currentUser;
    private String patientId; // Used when a doctor is viewing a specific patient's records
    private String patientName; // Name of the patient being viewed
    private TextView emptyRecordsText;
    private FirebaseFirestore firestore;
    private ListenerRegistration medicalRecordsListener;
    private User patientUser; // The patient whose records are being viewed

    public MedicalRecordsFragment() {
        // Required empty constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_medical_records, container, false);

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance();

        // Get current user and/or patient ID
        if (getArguments() != null) {
            currentUser = (User) getArguments().getSerializable("USER");
            patientId = getArguments().getString("PATIENT_ID", null);
            patientName = getArguments().getString("PATIENT_NAME", null);
            patientUser = (User) getArguments().getSerializable("PATIENT_USER");
        }

        // If no patientId provided and user is a patient, use the current user's ID
        if (patientId == null && currentUser != null && currentUser.isPatient()) {
            patientId = currentUser.getId();
            patientName = currentUser.getFullName();
            patientUser = currentUser;
        }

        // Initialize Firestore for medical records
        // (We'll query the "medicalRecords" collection later)

        // Initialize UI components
        recyclerView = view.findViewById(R.id.recycler_medical_records);
        fabAddRecord = view.findViewById(R.id.fab_add_record);
        emptyRecordsText = view.findViewById(R.id.text_empty_records);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recordsList = new ArrayList<>();

        // Set up adapter
        recordAdapter = new MedicalRecordAdapter(getContext(), recordsList, this);
        recyclerView.setAdapter(recordAdapter);

        // Show/hide add button based on role (only doctors can add records)
        if (currentUser != null && currentUser.isDoctor()) {
            fabAddRecord.setVisibility(View.VISIBLE);
            fabAddRecord.setOnClickListener(v -> {
                if (patientId != null && patientName != null) {
                    showAddRecordDialog(patientId, patientName);
                } else {
                    Toast.makeText(getContext(), "Patient information not available", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            fabAddRecord.setVisibility(View.GONE);
        }

        // Load medical records
        loadMedicalRecords();

        return view;
    }

    /**
     * Load medical records from Firestore based on user role and patient ID
     */
    private void loadMedicalRecords() {
        if (currentUser == null) {
            Toast.makeText(getContext(), "User information not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Query query;

        if (patientId != null) {
            // Loading records for a specific patient
            query = firestore.collection("medicalRecords")
                    .whereEqualTo("patientId", patientId)
                    .orderBy("date", Query.Direction.DESCENDING);
        } else if (currentUser.isAdmin()) {
            // Admin can see all records (though this is not typical for privacy reasons)
            query = firestore.collection("medicalRecords")
                    .orderBy("date", Query.Direction.DESCENDING);
        } else {
            // No valid patient ID and not admin, show error
            Toast.makeText(getContext(), "Cannot load medical records: Patient ID not specified",
                    Toast.LENGTH_SHORT).show();

            // Show empty state
            showEmptyState();
            return;
        }

        // Remove previous listener if it exists
        if (medicalRecordsListener != null) {
            medicalRecordsListener.remove();
        }

        // Set up new listener
        medicalRecordsListener = query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                Log.e(TAG, "Error loading medical records: " + error.getMessage());

                // Provide a more user-friendly error message
                String errorMessage;
                if (error.getMessage() != null && error.getMessage().contains("PERMISSION_DENIED")) {
                    errorMessage = "You don't have permission to access these medical records.";

                    // For debugging - check user roles
                    if (currentUser != null) {
                        Log.d(TAG, "Current user role: " + currentUser.getRoleString() +
                                ", isPatient: " + currentUser.isPatient() +
                                ", isDoctor: " + currentUser.isDoctor() +
                                ", isAdmin: " + currentUser.isAdmin());
                    }

                    // Display empty state instead of error for better UX
                    if (getContext() != null) {
                        showEmptyState();
                        emptyRecordsText.setText("No medical records available");
                    }
                } else {
                    errorMessage = "Error loading medical records: " + error.getMessage();
                }

                if (getContext() != null) {
                    Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // Clear existing records
            recordsList.clear();

            // Process the query results
            if (snapshots != null && !snapshots.isEmpty()) {
                for (DocumentSnapshot document : snapshots) {
                    MedicalRecord record = document.toObject(MedicalRecord.class);
                    if (record != null) {
                        // Set the record ID if it's not already set
                        if (record.getId() == null) {
                            record.setId(document.getId());
                        }
                        recordsList.add(record);
                    }
                }
            }

            // Update adapter
            recordAdapter.notifyDataSetChanged();

            // Show message if no records
            if (recordsList.isEmpty()) {
                showEmptyState();
            } else {
                hideEmptyState();
            }
        });
    }

    /**
     * Show empty state when there are no records
     */
    private void showEmptyState() {
        if (emptyRecordsText != null) {
            emptyRecordsText.setVisibility(View.VISIBLE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
    }

    /**
     * Hide empty state when there are records
     */
    private void hideEmptyState() {
        if (emptyRecordsText != null) {
            emptyRecordsText.setVisibility(View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Show dialog to add a new medical record
     */
    private void showAddRecordDialog(String patientId, String patientName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Add Medical Record");

        // Inflate the custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_medical_record, null);
        builder.setView(dialogView);

        // Get references to the input fields
        final TextInputEditText diagnosisInput = dialogView.findViewById(R.id.edit_diagnosis);
        final TextInputEditText prescriptionInput = dialogView.findViewById(R.id.edit_prescription);
        final TextInputEditText notesInput = dialogView.findViewById(R.id.edit_notes);

        // Set up the buttons
        builder.setPositiveButton("Save", null); // We'll set this up later to prevent dialog dismissal on validation failure
        builder.setNegativeButton("Cancel", null);

        // Create and show the dialog
        final AlertDialog dialog = builder.create();
        dialog.show();

        // Override the positive button click to handle validation
        Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            String diagnosis = diagnosisInput.getText().toString().trim();
            String prescription = prescriptionInput.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();

            // Validate inputs
            if (diagnosis.isEmpty()) {
                diagnosisInput.setError("Diagnosis is required");
                return;
            }

            if (prescription.isEmpty()) {
                prescriptionInput.setError("Prescription is required");
                return;
            }

            // Show loading indicator
            ProgressDialog progressDialog = new ProgressDialog(getContext());
            progressDialog.setMessage("Saving medical record...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            // Create a new medical record
            MedicalRecord newRecord = new MedicalRecord(
                    patientId,
                    currentUser.getId(),
                    patientName,
                    currentUser.getFullName(),
                    System.currentTimeMillis(),
                    diagnosis,
                    prescription
            );

            // Set the notes if provided
            if (!notes.isEmpty()) {
                newRecord.setNotes(notes);
            }

            // Generate a unique ID for the record
            String recordId = UUID.randomUUID().toString();
            newRecord.setId(recordId);

            // Save to Firestore
            firestore.collection("medicalRecords").document(recordId)
                    .set(newRecord)
                    .addOnSuccessListener(aVoid -> {
                        progressDialog.dismiss();
                        dialog.dismiss();
                        Toast.makeText(getContext(), "Medical record added successfully", Toast.LENGTH_SHORT).show();

                        // Send notification to the patient
                        sendNotificationToPatient(patientId, currentUser.getFullName());
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();

                        String errorMessage;
                        if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                            errorMessage = "You don't have permission to add medical records.";
                            Log.e(TAG, "Permission denied error: " + e.getMessage());
                        } else {
                            errorMessage = "Error adding record: " + e.getMessage();
                        }

                        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    });
        });
    }

    /**
     * Send a notification to the patient about the new medical record
     */
    private void sendNotificationToPatient(String patientId, String doctorName) {
        if (patientId == null || getContext() == null) return;

        // Create a notification in Firestore
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", patientId);
        notification.put("title", "New Medical Record");
        notification.put("message", "Dr. " + doctorName + " has added a new medical record to your profile");
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);
        notification.put("type", "medical_record");

        // Save notification to Firestore
        FirebaseFirestore.getInstance().collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> {
                    // Notification saved
                    Log.d(TAG, "Notification saved with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    // Error
                    Log.e(TAG, "Error adding notification: " + e.getMessage());
                });
    }

    /**
     * Show detailed view of a medical record
     */
    private void showRecordDetailsDialog(MedicalRecord record) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Medical Record Details");

        // Inflate the custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_medical_record_details, null);
        builder.setView(dialogView);

        // Get references to the views
        TextView dateText = dialogView.findViewById(R.id.text_record_date_detail);
        TextView patientNameText = dialogView.findViewById(R.id.text_patient_name_detail);
        TextView doctorNameText = dialogView.findViewById(R.id.text_doctor_name_detail);
        TextView diagnosisText = dialogView.findViewById(R.id.text_diagnosis_detail);
        TextView prescriptionText = dialogView.findViewById(R.id.text_prescription_detail);
        TextView notesText = dialogView.findViewById(R.id.text_notes_detail);
        Button downloadButton = dialogView.findViewById(R.id.button_download_pdf);
        Button shareButton = dialogView.findViewById(R.id.button_share_record);

        // Set the data
        String formattedDate = DateTimeUtils.formatDate(new Date(record.getDate()));
        dateText.setText("Date: " + formattedDate);
        patientNameText.setText("Patient: " + record.getPatientName());
        doctorNameText.setText("Doctor: Dr. " + record.getDoctorName());
        diagnosisText.setText(record.getDiagnosis());
        prescriptionText.setText(record.getPrescription());

        // Set notes or hide it if not available
        if (record.getNotes() != null && !record.getNotes().isEmpty()) {
            notesText.setText(record.getNotes());
        } else {
            notesText.setText("No additional notes");
        }

        // TODO: Implement PDF download and sharing functionality

        // Set up the buttons
        builder.setPositiveButton("Close", null);

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onViewDetailsClick(MedicalRecord record) {
        // Show record details when a record is clicked
        showRecordDetailsDialog(record);
    }

    @Override
    public void onDownloadPdfClick(MedicalRecord record) {
        // Show loading indicator
        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Generating PDF...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Generate PDF in a background thread
        new Thread(() -> {
            File pdfFile = PdfGenerator.generateMedicalRecordPdf(getContext(), record);

            // Update UI on the main thread
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (pdfFile != null) {
                        Toast.makeText(getContext(), "PDF generated: " + pdfFile.getName(), Toast.LENGTH_SHORT).show();
                        // TODO: Implement PDF viewing or sharing functionality
                    } else {
                        Toast.makeText(getContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove Firestore listener to prevent memory leaks
        if (medicalRecordsListener != null) {
            medicalRecordsListener.remove();
        }
    }
}