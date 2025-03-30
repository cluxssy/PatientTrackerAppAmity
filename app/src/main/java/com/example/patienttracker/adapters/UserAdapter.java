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
import com.example.patienttracker.interfaces.UserActionListener;
import com.example.patienttracker.models.User;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final Context context;
    private final List<User> usersList;
    private final UserActionListener listener;
    private final SimpleDateFormat dateFormat;

    public UserAdapter(Context context, List<User> usersList, UserActionListener listener) {
        this.context = context;
        this.usersList = usersList;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = usersList.get(position);

        // Set user name and email
        holder.tvName.setText(user.getFullName());
        holder.tvEmail.setText(user.getEmail());

        // Set profile image if available
        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ivProfile);
        } else {
            // Try to load from Firebase Storage directly
            StorageReference profileRef = FirebaseUtil.getUserProfileImageRef(user.getUid());
            Glide.with(context)
                    .load(profileRef)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ivProfile);
        }

        // Set role and status
        holder.tvRole.setText(user.getRoleString());
        holder.tvStatus.setText(user.getStatusString());

        // Set color for status
        int statusColor;
        switch (user.getStatus()) {
            case User.STATUS_ACTIVE:
                statusColor = context.getResources().getColor(R.color.approved_color, null);
                break;
            case User.STATUS_PENDING:
                statusColor = context.getResources().getColor(R.color.pending_color, null);
                break;
            case User.STATUS_REJECTED:
                statusColor = context.getResources().getColor(R.color.rejected_color, null);
                break;
            default:
                statusColor = context.getResources().getColor(R.color.text_secondary, null);
                break;
        }
        holder.tvStatus.setTextColor(statusColor);

        // Set registration date
        if (user.getRegisteredAt() != null) {
            String formattedDate = dateFormat.format(user.getRegisteredAt().toDate());
            holder.tvDate.setText(context.getString(R.string.registered_on, formattedDate));
        } else if (user.getRegisteredAtString() != null && !user.getRegisteredAtString().isEmpty()) {
            holder.tvDate.setText(context.getString(R.string.registered_on, user.getRegisteredAtString()));
        } else {
            holder.tvDate.setText(R.string.unknown_date);
        }

        // Set phone number if available
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            holder.tvPhone.setText(user.getPhone());
            holder.tvPhone.setVisibility(View.VISIBLE);
        } else {
            holder.tvPhone.setVisibility(View.GONE);
        }

        // Show activate/deactivate buttons based on current status
        if (user.isAdmin()) {
            // Hide both buttons for admin users
            holder.btnActivate.setVisibility(View.GONE);
            holder.btnDeactivate.setVisibility(View.GONE);
        } else if (user.isActive()) {
            // User is active, show deactivate button
            holder.btnActivate.setVisibility(View.GONE);
            holder.btnDeactivate.setVisibility(View.VISIBLE);
        } else {
            // User is inactive, show activate button
            holder.btnActivate.setVisibility(View.VISIBLE);
            holder.btnDeactivate.setVisibility(View.GONE);
        }

        // Set button click listeners
        holder.btnActivate.setOnClickListener(v -> {
            if (listener != null) {
                listener.onActivate(user);
            }
        });

        holder.btnDeactivate.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeactivate(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return usersList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {

        ImageView ivProfile;
        TextView tvName, tvEmail, tvRole, tvStatus, tvPhone, tvDate;
        Button btnActivate, btnDeactivate;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);

            ivProfile = itemView.findViewById(R.id.iv_profile);
            tvName = itemView.findViewById(R.id.tv_name);
            tvEmail = itemView.findViewById(R.id.tv_email);
            tvRole = itemView.findViewById(R.id.tv_role);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvPhone = itemView.findViewById(R.id.tv_phone);
            tvDate = itemView.findViewById(R.id.tv_date);
            btnActivate = itemView.findViewById(R.id.btn_activate);
            btnDeactivate = itemView.findViewById(R.id.btn_deactivate);
        }
    }
}