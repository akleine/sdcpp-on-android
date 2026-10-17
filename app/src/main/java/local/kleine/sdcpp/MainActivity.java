package local.kleine.sdcpp;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class MainActivity extends AppCompatActivity {
    private MainActivity mainActivity;
    private ActivityResultLauncher<Intent> launcher;
    private boolean ready = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = this;
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long availMB = mi.availMem / (1024 * 1024);
        Toast.makeText(mainActivity, "available Memory: " + availMB + " MB", Toast.LENGTH_LONG).show();
        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    switch (result.getResultCode()) {
                        case RESULT_OK:
                        case RESULT_CANCELED:
                            // don't restart
                            ready = true;
                            break;
                        case SDActivity.EXIT_CODE_DO_RESTART:
                            ready = false;
                            break;
                        default:
                            Toast.makeText(mainActivity, "ERROR: process killed, not enough memory", Toast.LENGTH_SHORT).show();
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
            mainActivity.finish();
        }
    }
}
