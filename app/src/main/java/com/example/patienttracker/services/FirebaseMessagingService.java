package com.example.patienttracker.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.patienttracker.R;
import com.example.patienttracker.activities.MainActivity;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Service to handle Firebase Cloud Messaging (FCM) notifications.
 * Displays notifications to the user and handles incoming data messages.
 */
public class FirebaseMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {
    private static final String TAG = "FCMService";
    
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        
        // Check if message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            
            // Handle the data message
            // This could include updating local database, triggering an update, etc.
            String title = remoteMessage.getData().get("title");
            String message = remoteMessage.getData().get("message");
            String userId = remoteMessage.getData().get("userId");
            
            // Store the notification in Firebase Database
            if (userId != null) {
                FirebaseUtil.sendNotification(userId, title, message, success -> {
                    Log.d(TAG, "Notification saved to database: " + success);
                });
            }
            
            // Show the notification to the user
            sendNotification(title, message);
        }
        
        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            
            // Show notification directly from FCM
            sendNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody()
            );
        }
    }
    
    /**
     * Create and show a notification
     */
    private void sendNotification(String title, String messageBody) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);
        
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Patient Tracker Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }
        
        // Show the notification
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
    }
    
    /**
     * Called when a new token is generated
     */
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        
        // Save the token to the database for sending targeted notifications
        sendRegistrationToServer(token);
    }
    
    /**
     * Send FCM token to server
     */
    private void sendRegistrationToServer(String token) {
        // Update the FCM token in Firebase Database
        if (FirebaseUtil.getCurrentUser() != null) {
            String userId = FirebaseUtil.getCurrentUser().getUid();
            FirebaseUtil.getDatabaseReference()
                    .child("users")
                    .child(userId)
                    .child("fcmToken")
                    .setValue(token)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated"))
                    .addOnFailureListener(e -> Log.e(TAG, "FCM token update failed", e));
        }
    }
}