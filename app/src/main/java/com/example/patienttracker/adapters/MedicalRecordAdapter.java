package com.example.patienttracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patienttracker.R;
import com.example.patienttracker.models.MedicalRecord;
import com.example.patienttracker.utils.DateTimeUtils;

import java.util.Date;
import java.util.List;

/**
 * Adapter for displaying medical records in a RecyclerView
 */
public class MedicalRecordAdapter extends RecyclerView.Adapter<MedicalRecordAdapter.MedicalRecordViewHolder> {

    private final Context context;
    private final List<MedicalRecord> recordsList;
    private final OnRecordClickListener listener;

    /**
     * Interface for handling record item click events
     */
    public interface OnRecordClickListener {
        void onViewDetailsClick(MedicalRecord record);
        void onDownloadPdfClick(MedicalRecord record);
    }

    /**
     * Constructor
     */
    public MedicalRecordAdapter(Context context, List<MedicalRecord> recordsList, OnRecordClickListener listener) {
        this.context = context;
        this.recordsList = recordsList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MedicalRecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_medical_record, parent, false);
        return new MedicalRecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicalRecordViewHolder holder, int position) {
        MedicalRecord record = recordsList.get(position);

        // Format and set the date
        String formattedDate = DateTimeUtils.formatDate(new Date(record.getDate()));
        holder.dateText.setText(formattedDate);

        // Set doctor name
        holder.doctorNameText.setText("Dr. " + record.getDoctorName());

        // Set diagnosis preview
        holder.diagnosisPreviewText.setText("Diagnosis: " + record.getDiagnosis());

        // Set click listeners
        holder.viewDetailsButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewDetailsClick(record);
            }
        });

        holder.downloadPdfButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownloadPdfClick(record);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recordsList.size();
    }

    /**
     * ViewHolder for medical record items
     */
    static class MedicalRecordViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        TextView doctorNameText;
        TextView diagnosisPreviewText;
        Button viewDetailsButton;
        Button downloadPdfButton;

        MedicalRecordViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.text_record_date);
            doctorNameText = itemView.findViewById(R.id.text_doctor_name);
            diagnosisPreviewText = itemView.findViewById(R.id.text_diagnosis_preview);
            viewDetailsButton = itemView.findViewById(R.id.button_view_details);
            downloadPdfButton = itemView.findViewById(R.id.button_download_pdf);
        }
    }
}