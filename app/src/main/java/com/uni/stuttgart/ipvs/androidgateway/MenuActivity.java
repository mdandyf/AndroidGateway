package com.uni.stuttgart.ipvs.androidgateway;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.uni.stuttgart.ipvs.androidgateway.settings.SettingsActivity;

/**
 * Created by mdand on 2/19/2018.
 */

public class MenuActivity extends AppCompatActivity {
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                return true;
            case R.id.action_logout:
                startActivity(new Intent(getApplicationContext(), LogoutActivity.class));
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }
}
