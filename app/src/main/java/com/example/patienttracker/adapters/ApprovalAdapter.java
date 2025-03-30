package com.example.patienttracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.patienttracker.R;
import com.example.patienttracker.interfaces.ApprovalActionListener;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ApprovalAdapter extends RecyclerView.Adapter<ApprovalAdapter.ApprovalViewHolder> {

    private final Context context;
    private final List<User> doctorsList;
    private final ApprovalActionListener listener;
    private final SimpleDateFormat dateFormat;

    public ApprovalAdapter(Context context, List<User> doctorsList, ApprovalActionListener listener) {
        this.context = context;
        this.doctorsList = doctorsList;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    @NonNull
    @Override
    public ApprovalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_approval, parent, false);
        return new ApprovalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ApprovalViewHolder holder, int position) {
        User doctor = doctorsList.get(position);

        // Set doctor name and email
        holder.tvName.setText(doctor.getFullName());
        holder.tvEmail.setText(doctor.getEmail());

        // Set profile image if available
        if (doctor.getPhotoUrl() != null && !doctor.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(doctor.getPhotoUrl())
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ivProfile);
        } else {
            // Try to load from Firebase Storage directly
            StorageReference profileRef = FirebaseUtil.getUserProfileImageRef(doctor.getUid());
            Glide.with(context)
                    .load(profileRef)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ivProfile);
        }

        // Set registration date
        if (doctor.getRegisteredAt() != null) {
            String formattedDate = dateFormat.format(doctor.getRegisteredAt().toDate());
            holder.tvDate.setText(context.getString(R.string.registered_on, formattedDate));
        } else {
            holder.tvDate.setText(R.string.unknown_date);
        }

        // Set phone number if available
        if (doctor.getPhone() != null && !doctor.getPhone().isEmpty()) {
            holder.tvPhone.setText(doctor.getPhone());
            holder.tvPhone.setVisibility(View.VISIBLE);
        } else {
            holder.tvPhone.setVisibility(View.GONE);
        }

        // Set specialization if available
        if (doctor.getSpecialization() != null && !doctor.getSpecialization().isEmpty()) {
            holder.tvSpecialization.setText(doctor.getSpecialization());
            holder.tvSpecialization.setVisibility(View.VISIBLE);
        } else {
            holder.tvSpecialization.setVisibility(View.GONE);
        }

        // Set button click listeners
        holder.btnApprove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApprove(doctor);
            }
        });

        holder.btnReject.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReject(doctor);
            }
        });
    }

    @Override
    public int getItemCount() {
        return doctorsList.size();
    }

    public static class ApprovalViewHolder extends RecyclerView.ViewHolder {

        ImageView ivProfile;
        TextView tvName, tvEmail, tvPhone, tvSpecialization, tvDate;
        Button btnApprove, btnReject;

        public ApprovalViewHolder(@NonNull View itemView) {
            super(itemView);

            ivProfile = itemView.findViewById(R.id.iv_profile);
            tvName = itemView.findViewById(R.id.tv_name);
            tvEmail = itemView.findViewById(R.id.tv_email);
            tvPhone = itemView.findViewById(R.id.tv_phone);
            tvSpecialization = itemView.findViewById(R.id.tv_specialization);
            tvDate = itemView.findViewById(R.id.tv_date);
            btnApprove = itemView.findViewById(R.id.btn_approve);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }
    }
}