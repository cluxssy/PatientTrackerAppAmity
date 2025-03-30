package com.example.patienttracker.utils;

import android.view.MenuItem;
import android.view.View;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Utility class for managing notification badges on menu items
 */
public class BadgeHelper {

    /**
     * Display a badge with count on a menu item
     *
     * @param navigationView The BottomNavigationView that contains the menu item
     * @param itemId The ID of the menu item to add the badge to
     * @param count The count to display on the badge
     */
    public static void showBadge(BottomNavigationView navigationView, int itemId, int count) {
        // Remove existing badge if count is zero
        if (count <= 0) {
            removeBadge(navigationView, itemId);
            return;
        }

        // Get or create the badge for this item
        BadgeDrawable badge = navigationView.getOrCreateBadge(itemId);
        badge.setVisible(true);
        badge.setNumber(count);

        // Set badge appearance
        badge.setBackgroundColor(0xFFFF0000); // Red color
    }

    /**
     * Remove a badge from a menu item
     *
     * @param navigationView The BottomNavigationView that contains the menu item
     * @param itemId The ID of the menu item to remove the badge from
     */
    public static void removeBadge(BottomNavigationView navigationView, int itemId) {
        if (navigationView.getBadge(itemId) != null) {
            navigationView.removeBadge(itemId);
        }
    }
}