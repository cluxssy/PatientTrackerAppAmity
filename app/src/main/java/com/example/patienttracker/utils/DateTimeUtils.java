package com.example.patienttracker.utils;

import android.text.format.DateFormat;
import android.text.format.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility class for handling date and time formatting throughout the app.
 * Provides consistent date/time formatting across the application.
 */
public class DateTimeUtils {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
    private static final SimpleDateFormat FILE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    /**
     * Format Date object to string (e.g., "Jan 01, 2025")
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        return DATE_FORMAT.format(date);
    }

    /**
     * Format Date object to time string (e.g., "10:30 AM")
     */
    public static String formatTime(Date date) {
        if (date == null) return "";
        return TIME_FORMAT.format(date);
    }

    /**
     * Format Date object to date-time string (e.g., "Jan 01, 2025 10:30 AM")
     */
    public static String formatDateTime(Date date) {
        if (date == null) return "";
        return DATE_TIME_FORMAT.format(date);
    }

    /**
     * Format Date for use in file names (e.g., "20250101_103000")
     */
    public static String formatForFileName(Date date) {
        if (date == null) date = new Date();
        return FILE_NAME_DATE_FORMAT.format(date);
    }

    /**
     * Convert timestamp to Date object
     */
    public static Date timestampToDate(long timestamp) {
        return new Date(timestamp);
    }

    /**
     * Get current date as timestamp
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * Format timestamp to date string
     */
    public static String formatTimestamp(long timestamp) {
        return formatDate(new Date(timestamp));
    }

    /**
     * Format timestamp to time string
     */
    public static String formatTimestampToTime(long timestamp) {
        return formatTime(new Date(timestamp));
    }

    /**
     * Format timestamp to date-time string
     */
    public static String formatTimestampToDateTime(long timestamp) {
        return formatDateTime(new Date(timestamp));
    }

    /**
     * Determine if date is today
     */
    public static boolean isToday(Date date) {
        if (date == null) return false;

        Calendar today = Calendar.getInstance();
        Calendar targetDate = Calendar.getInstance();
        targetDate.setTime(date);

        return today.get(Calendar.YEAR) == targetDate.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == targetDate.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Format relative time (e.g., "2 hours ago", "yesterday", etc.)
     */
    public static String getRelativeTimeSpan(long timestamp) {
        return DateUtils.getRelativeTimeSpanString(
                timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString();
    }
}