package com.example.patienttracker.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patienttracker.R;
import com.example.patienttracker.models.Appointment;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment to display and manage appointments.
 * Patients can view their own appointments.
 * Doctors can view all their scheduled appointments.
 */
public class AppointmentsFragment extends Fragment {

    private RecyclerView recyclerView;
    private FloatingActionButton fabAddAppointment;
    private List<Appointment> appointmentList;
    // Will need to create an adapter
    // private AppointmentAdapter appointmentAdapter;
    private User currentUser;
    private DatabaseReference appointmentsRef;

    public AppointmentsFragment() {
        // Required empty constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_appointments, container, false);
        
        // Get current user (will be passed from parent activity)
        if (getArguments() != null) {
            currentUser = (User) getArguments().getSerializable("USER");
        }
        
        // Initialize Firebase reference
        appointmentsRef = FirebaseUtil.getDatabaseReference().child("appointments");
        
        // Initialize UI components
        recyclerView = view.findViewById(R.id.recycler_appointments);
        fabAddAppointment = view.findViewById(R.id.fab_add_appointment);
        
        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        appointmentList = new ArrayList<>();
        
        // Set up adapter
        // appointmentAdapter = new AppointmentAdapter(getContext(), appointmentList);
        // recyclerView.setAdapter(appointmentAdapter);
        
        // Set up FAB click listener (for adding new appointments)
        fabAddAppointment.setOnClickListener(v -> {
            // Will open appointment booking dialog or activity
            // showAppointmentBookingDialog();
            Toast.makeText(getContext(), "Add appointment feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Load appointments
        loadAppointments();
        
        return view;
    }
    
    /**
     * Load appointments from Firebase based on user role
     */
    private void loadAppointments() {
        if (currentUser == null) {
            Toast.makeText(getContext(), "User information not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Query based on user role
        if (currentUser.isPatient()) {
            // Patient sees own appointments
            appointmentsRef.orderByChild("patientId").equalTo(currentUser.getId())
                    .addValueEventListener(appointmentsListener);
        } else if (currentUser.isDoctor()) {
            // Doctor sees appointments where they are the doctor
            appointmentsRef.orderByChild("doctorId").equalTo(currentUser.getId())
                    .addValueEventListener(appointmentsListener);
        } else {
            // Admin can see all appointments
            appointmentsRef.addValueEventListener(appointmentsListener);
        }
    }
    
    /**
     * Firebase listener for appointments
     */
    private final ValueEventListener appointmentsListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            appointmentList.clear();
            
            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                Appointment appointment = snapshot.getValue(Appointment.class);
                if (appointment != null) {
                    appointmentList.add(appointment);
                }
            }
            
            // Update adapter
            // appointmentAdapter.notifyDataSetChanged();
            
            // Show message if no appointments
            if (appointmentList.isEmpty()) {
                // Show empty state
            }
        }
        
        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            Toast.makeText(getContext(), "Error loading appointments: " + databaseError.getMessage(), 
                    Toast.LENGTH_SHORT).show();
        }
    };
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove Firebase listeners
        if (currentUser != null) {
            if (currentUser.isPatient()) {
                appointmentsRef.orderByChild("patientId").equalTo(currentUser.getId())
                        .removeEventListener(appointmentsListener);
            } else if (currentUser.isDoctor()) {
                appointmentsRef.orderByChild("doctorId").equalTo(currentUser.getId())
                        .removeEventListener(appointmentsListener);
            } else {
                appointmentsRef.removeEventListener(appointmentsListener);
            }
        }
    }
}