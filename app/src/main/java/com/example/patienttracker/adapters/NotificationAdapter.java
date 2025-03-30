package com.example.patienttracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patienttracker.R;
import com.example.patienttracker.models.Notification;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying notifications in a RecyclerView.
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {
    
    private final Context context;
    private final List<Notification> notificationList;
    private final SimpleDateFormat dateFormat;
    
    public NotificationAdapter(Context context, List<Notification> notificationList) {
        this.context = context;
        this.notificationList = notificationList;
        this.dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    }
    
    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notificationList.get(position);
        
        // Set title and message
        holder.textTitle.setText(notification.getTitle());
        holder.textMessage.setText(notification.getMessage());
        
        // Format and set date
        String formattedDate = dateFormat.format(new Date(notification.getTimestamp()));
        holder.textDate.setText(formattedDate);
        
        // Set read status
        if (notification.isRead()) {
            holder.imageReadStatus.setVisibility(View.INVISIBLE);
        } else {
            holder.imageReadStatus.setVisibility(View.VISIBLE);
        }
        
        // Set click listener to mark as read when clicked
        holder.itemView.setOnClickListener(v -> {
            if (!notification.isRead()) {
                notification.setRead(true);
                holder.imageReadStatus.setVisibility(View.INVISIBLE);
                notifyItemChanged(position);
                
                // TODO: Update read status in Firebase
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return notificationList != null ? notificationList.size() : 0;
    }
    
    /**
     * ViewHolder for notification items
     */
    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle, textMessage, textDate;
        ImageView imageReadStatus;
        
        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_notification_title);
            textMessage = itemView.findViewById(R.id.text_notification_message);
            textDate = itemView.findViewById(R.id.text_notification_date);
            imageReadStatus = itemView.findViewById(R.id.image_notification_read);
        }
    }
}