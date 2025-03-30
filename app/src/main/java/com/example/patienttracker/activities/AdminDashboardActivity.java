package com.example.patienttracker.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.patienttracker.R;
import com.example.patienttracker.fragments.ApprovalsFragment;
import com.example.patienttracker.fragments.NotificationsFragment;
import com.example.patienttracker.fragments.UsersFragment;
import com.example.patienttracker.utils.FirebaseUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminDashboardActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "AdminDashboardActivity";
    private Toolbar toolbar;
    private BottomNavigationView bottomNavigationView;
    private com.example.patienttracker.models.User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        try {
            // Get user data from intent as individual properties
            String userId = getIntent().getStringExtra("USER_ID");
            String userEmail = getIntent().getStringExtra("USER_EMAIL");
            String userName = getIntent().getStringExtra("USER_NAME");
            int userRole = getIntent().getIntExtra("USER_ROLE", -1);
            int userStatus = getIntent().getIntExtra("USER_STATUS", -1);

            // Log the received data for debugging
            Log.d("AdminDashboardActivity", "Received user data - ID: " + userId +
                    ", Email: " + userEmail +
                    ", Name: " + userName +
                    ", Role: " + userRole +
                    ", Status: " + userStatus);

            // Create User object from individual properties
            if (userId != null && userEmail != null && userName != null && userRole == com.example.patienttracker.models.User.ROLE_ADMIN) {
                currentUser = new com.example.patienttracker.models.User(userId, userEmail, userName, userRole);
                currentUser.setStatus(userStatus);

                // Verify the user object was created properly
                Log.d("AdminDashboardActivity", "Created user object: " +
                        currentUser.getUid() + ", " +
                        currentUser.getEmail() + ", " +
                        currentUser.getFullName() + ", " +
                        currentUser.getRole() + ", " +
                        currentUser.getStatus());

                Toast.makeText(this, "Welcome, " + currentUser.getFullName(), Toast.LENGTH_SHORT).show();
            } else {
                // Invalid user data or not an admin, go back to login
                Log.e("AdminDashboardActivity", "Invalid user data or incorrect role");
                Toast.makeText(this, "Invalid user or insufficient permissions", Toast.LENGTH_LONG).show();
                logout();
                return;
            }
        } catch (Exception e) {
            // Handle any serialization errors
            Log.e("AdminDashboardActivity", "Error loading user data", e);
            Toast.makeText(this, "Error loading user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            logout();
            return;
        }

        // Initialize toolbar without setting as action bar
        try {
            toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                // Set title directly on toolbar without setting it as support action bar
                toolbar.setTitle(getString(R.string.title_admin_dashboard));

                // Add logout menu item to toolbar
                toolbar.inflateMenu(R.menu.menu_admin_dashboard);
                toolbar.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.action_logout) {
                        logout();
                        return true;
                    }
                    return false;
                });
            } else {
                Toast.makeText(this, "Toolbar not found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error setting up the toolbar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        // Initialize bottom navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        // Set default fragment to approvals
        if (savedInstanceState == null) {
            try {
                // If there's no saved instance state, we need to manually load
                // the first fragment to ensure user data is passed to it
                // Use the real ApprovalsFragment instead of placeholder
                Fragment defaultFragment = new com.example.patienttracker.fragments.ApprovalsFragment();
                Bundle args = new Bundle();
                args.putString("VIEW_NAME", "Approvals View");
                args.putSerializable("CURRENT_USER", currentUser);
                defaultFragment.setArguments(args);

                // Load the fragment
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, defaultFragment)
                        .commit();

                // Set the selected item in the navigation
                bottomNavigationView.setSelectedItemId(R.id.nav_approvals);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading default view: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Menu handling moved to toolbar.setOnMenuItemClickListener

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        String title = getString(R.string.title_admin_dashboard);
        String viewName = "";

        try {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_approvals) {
                viewName = "Approvals View";
                title = getString(R.string.title_admin_dashboard) + " - " + getString(R.string.nav_approvals);
            } else if (itemId == R.id.nav_users) {
                viewName = "Users View";
                title = getString(R.string.title_admin_dashboard) + " - " + getString(R.string.nav_users);
            } else if (itemId == R.id.nav_notifications) {
                viewName = "Notifications View";
                title = getString(R.string.title_admin_dashboard) + " - " + getString(R.string.nav_notifications);
            }

            // Use the appropriate fragment based on the selected menu item
            if (itemId == R.id.nav_approvals) {
                // Use the real ApprovalsFragment instead of placeholder
                fragment = new com.example.patienttracker.fragments.ApprovalsFragment();
                Bundle args = new Bundle();
                args.putString("VIEW_NAME", viewName);
                args.putSerializable("CURRENT_USER", currentUser);
                fragment.setArguments(args);
            } else if (itemId == R.id.nav_users) {
                // Use the real UsersFragment instead of placeholder
                fragment = new com.example.patienttracker.fragments.UsersFragment();
                Bundle args = new Bundle();
                args.putString("VIEW_NAME", viewName);
                args.putSerializable("CURRENT_USER", currentUser);
                fragment.setArguments(args);
            } else if (itemId == R.id.nav_notifications) {
                // Use the real NotificationsFragment instead of placeholder
                fragment = new com.example.patienttracker.fragments.NotificationsFragment();
                Bundle args = new Bundle();
                args.putString("VIEW_NAME", viewName);
                args.putSerializable("CURRENT_USER", currentUser);
                fragment.setArguments(args);
            }

            if (fragment != null) {
                loadFragment(fragment);
                toolbar.setTitle(title);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading view: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    private void loadFragment(Fragment fragment) {
        if (fragment == null) {
            Log.e("AdminDashboardActivity", "Cannot load null fragment");
            Toast.makeText(this, "Error: Failed to load view", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            Log.d("AdminDashboardActivity", "Loaded fragment: " + fragment.getClass().getSimpleName());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("AdminDashboardActivity", "Error loading fragment: " + e.getMessage());
            Toast.makeText(this, "Error loading view: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void logout() {
        try {
            // Make sure all fragments are removed to clean up their listeners
            getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

            // Remove all active fragments
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment != null) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                }
            }

            // Now sign out
            FirebaseUtil.signOut();

            // Navigate to login screen
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            // Show a success message
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            // Finish this activity
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error during logout: " + e.getMessage(), e);
            // Still try to sign out and navigate even if there was an error
            FirebaseUtil.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    /**
     * Static Fragment class for Approvals view
     */
    public static class ApprovalsPlaceholderFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return createPlaceholderView(getContext(), getArguments());
        }
    }

    /**
     * Static Fragment class for Users view
     */
    public static class UsersPlaceholderFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return createPlaceholderView(getContext(), getArguments());
        }
    }

    /**
     * Static Fragment class for Notifications view
     */
    public static class NotificationsPlaceholderFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return createPlaceholderView(getContext(), getArguments());
        }
    }

    /**
     * Helper method to create the placeholder view
     */
    private static View createPlaceholderView(Context context, Bundle args) {
        if (context == null) return null;

        String viewName = "";
        com.example.patienttracker.models.User user = null;

        if (args != null) {
            viewName = args.getString("VIEW_NAME", "");
            user = (com.example.patienttracker.models.User) args.getSerializable("CURRENT_USER");

            // Log whether we received the user object
            if (user != null) {
                Log.d("AdminDashboardActivity", "Received user data: " + user.getFullName() + ", " + user.getEmail());
            } else {
                Log.e("AdminDashboardActivity", "User data is null in createPlaceholderView");
            }
        } else {
            Log.e("AdminDashboardActivity", "Args bundle is null in createPlaceholderView");
        }

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(30, 50, 30, 30);

        // Add title
        TextView titleText = new TextView(context);
        titleText.setText("Admin Dashboard - " + viewName);
        titleText.setTextSize(22);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 30);
        layout.addView(titleText);

        // Add the current admin's information
        if (user != null) {
            // Name
            TextView nameLabel = new TextView(context);
            nameLabel.setText("Admin Name:");
            nameLabel.setTextSize(16);
            nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            layout.addView(nameLabel);

            TextView nameText = new TextView(context);
            nameText.setText(user.getFullName());
            nameText.setTextSize(16);
            nameText.setPadding(0, 0, 0, 20);
            layout.addView(nameText);

            // Email
            TextView emailLabel = new TextView(context);
            emailLabel.setText("Email Address:");
            emailLabel.setTextSize(16);
            emailLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            layout.addView(emailLabel);

            TextView emailText = new TextView(context);
            emailText.setText(user.getEmail());
            emailText.setTextSize(16);
            emailText.setPadding(0, 0, 0, 20);
            layout.addView(emailText);

            // Sample feature description for this view
            TextView featureLabel = new TextView(context);
            featureLabel.setText("\nIn this section you can:");
            featureLabel.setTextSize(18);
            featureLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            featureLabel.setPadding(0, 20, 0, 10);
            layout.addView(featureLabel);

            // Different description based on view
            TextView featureText = new TextView(context);
            if (viewName.equals("Approvals View")) {
                featureText.setText("• Review and approve doctor registration requests\n"
                        + "• Manage doctor credentials and verification\n"
                        + "• Process pending approvals in the system");
            } else if (viewName.equals("Users View")) {
                featureText.setText("• View and manage all registered users\n"
                        + "• Edit user account information\n"
                        + "• Deactivate or reset user accounts");
            } else if (viewName.equals("Notifications View")) {
                featureText.setText("• Send announcements to all users\n"
                        + "• Configure system notification settings\n"
                        + "• View notification history and delivery status");
            }
            featureText.setTextSize(16);
            layout.addView(featureText);
        } else {
            TextView errorText = new TextView(context);
            errorText.setText("User information is not available");
            errorText.setTextSize(16);
            errorText.setGravity(Gravity.CENTER);
            layout.addView(errorText);
        }

        return layout;
    }
}