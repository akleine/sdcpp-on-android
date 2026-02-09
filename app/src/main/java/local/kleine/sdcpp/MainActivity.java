package local.kleine.sdcpp;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class MainActivity extends AppCompatActivity {
    private Activity myActivity;
    private ActivityResultLauncher<Intent> launcher;
    private boolean ready = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myActivity = this;
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long availMB = (long) mi.availMem / (1024 * 1024);
        Toast.makeText(myActivity, "available Memory: "+ availMB + " MB", Toast.LENGTH_SHORT).show();
        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // Toast.makeText(myActivity, result.getData().getStringExtra("result"), Toast.LENGTH_SHORT).show();
                        myActivity.finish();
                        ready = true;
                    } else {
                        Toast.makeText(myActivity, "ERROR: process killed, not enough memory", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ready) {
            Intent intent = new Intent(this, SDActivity.class);
            launcher.launch(intent);
        } else {
            myActivity.finish();
        }
    }

}
