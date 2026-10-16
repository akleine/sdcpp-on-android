package local.kleine.sdcpp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SDActivity extends AppCompatActivity {

    public static final int EXIT_CODE_DO_RESTART   = 1001;

    private static volatile String outputImagePath = "";
    private static volatile ArrayList<String> outputArrayList = null;
    private static sdIOThread sd_thread = null;
    private Activity myActivity;
    private String sdProgramPath, selectedModelfile, selectedSampler, selectedScheduler, taesdModel, taesdXLModel, helperPath,
            libPath, sdFileName; // Note: "sd" or "sd_cli" executable needs renaming because it is now located inside jniLibs

    private final static String SDlibopenCL = "libsdopenCL.so";
    private final static String SDlib = "libsd.so";
    private final String sdWorkPath = android.os.Environment.
            getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).
            getAbsolutePath();
    private EditText promptEditor, negativeEditor, seedEditor, stepsEditor,
            widthEditor, heightEditor, cfgscaleEditor, optionsEditor;
    private CheckBox taesdchecker, taesdXLchecker, embeddchecker, cpuchecker;
    private ListView sdLogView;
    private View coverView;
    private Button closeButton;
    private ImageView imageOutputView;
    private ArrayAdapter<String> arrayAdapter;
    private boolean lastProgressBar = true;               // helper for message log
    private final String[] samplerArr = {
            "euler",
            "euler_a",
            "heun",
            "dpm2",
            "dpm++2s_a",
            "dpm++2m",
            "dpm++2mv2",
            "ipndm",
            "ipndm_v",
            "lcm" /* LCM_POS 9 */,
            "ddim_trailing",
            "tcd",
            "res_multistep",
            "res_2s",
            "er_sde",
            "euler_cfg_pp",
            "euler_a_cfg_pp",
            "euler_ge",
            "dpm++2m_sde",
            "dpm++2m_sde_bt",
            "lms",
    };
    private static final int LCM_POS = 9;

    private final String[] schedulerArr = {
            "discrete",
            "karras",
            "exponential",
            "ays",
            "gits",
            "sgm_uniform",
            "simple",
            "smoothstep",
            "kl_optimal",
            "lcm",
            "bong_tangent",
            "ltx2",
            "logit_normal",
            "flux2",
            "flux",
            "beta",
            "llada_image",
    };

    private List<String> fileList, samplerList, schedulerList;
    private static String lastMsg = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myActivity = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            setContentView(R.layout.activity_permissions);
            Button requestPermissionButton = findViewById(R.id.requestPermissionButton);
            requestPermissionButton.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    activityResultLauncher.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Error requesting permission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                /* && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M  //*/// min sdk == 24
                && !haveOldPermissions()) {
            setContentView(R.layout.activity_permissions);
            Button requestPermissionButton = findViewById(R.id.requestPermissionButton);
            // shouldShowRequestPermissionRationale() logic:
            // 1st launch     -> false
            // agree          -> false
            // 1st rejection ,-> true (user probably needs an explanation)
            // 2nd+ rejection -> false
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // the user rejected once, on 2nd reject the access will be
                // disabled permanently, therefore redirecting to settings
                requestPermissionButton.setText(R.string.application_settings);
                requestPermissionButton.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        activityResultLauncher.launch(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Error opening settings" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // 1st launch, suggest to open permission request
                requestPermissionButton.setText(R.string.request_write);
                requestPermissionButton.setOnClickListener(v -> {
                    try {
                        permissionRequestLauncher.launch(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        });
                    } catch (Exception e) {
                        Toast.makeText(this, "Error requesting permissions" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            runSDcpp();
        }
    }

    final boolean haveNewPermissions() {
        // true if Android 11+ and have full access permissions
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && Environment.isExternalStorageManager();
    }

    final boolean haveOldPermissions() {
        // true if Android 6...10 and have read + write permissions
        return /* Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && //*/// min sdk == 24
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                        && PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        && PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (haveNewPermissions() || haveOldPermissions()) {
                    runSDcpp();
                } else {
                    setResult(EXIT_CODE_DO_RESTART);
                    finish();
                }
            });

    final ActivityResultLauncher<String[]> permissionRequestLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                // can be simplified to haveOldPermissions()
                if (/*  Build.VERSION.SDK_INT < Build.VERSION_CODES.M || //*/
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
                                (Boolean.TRUE.equals(permissions.get(Manifest.permission.READ_EXTERNAL_STORAGE)) &&
                                 Boolean.TRUE.equals(permissions.get(Manifest.permission.WRITE_EXTERNAL_STORAGE)))) {
                    runSDcpp();
                } else {
                    setResult(EXIT_CODE_DO_RESTART);
                    finish();
                }
            });

    final View.OnClickListener cancelGenerationListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View v) {
            View coverView = myActivity.findViewById(R.id.coverView);
            coverView.setOnTouchListener(null);
            coverView.setVisibility(View.GONE);
            restoreScreenBrightness();
            if (sd_thread != null) {
                sd_thread.processDestroy();
                sd_thread.updateActivity(null);
            }
            sd_thread = null;
            if (outputArrayList != null) {
                outputArrayList.clear();
            }
            outputArrayList = null;
            outputImagePath = "";
            lastMsg = "";
            Intent intent = new Intent(SDActivity.this, MainActivity.class);
            intent.putExtra("result", "ready");
            setResult(RESULT_OK, intent);  // do not restart, notification will be in subFinished()
            finish();
        }
    };

    private void setupWindow() {
        // setup, common for settings and log/result views
        // needs initialized outputArrayList
        androidx.appcompat.app.ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.hide();                                                      // request all visible space available
        }

        // not needed, rotations are handled
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED); // needed for sd log output

        setContentView(R.layout.activity_main);
        sdLogView = findViewById(R.id.sdLogView);
        arrayAdapter = new ArrayAdapter<>(this, R.layout.custom_list_item, R.id.output_item_line, outputArrayList);
        sdLogView.setAdapter(arrayAdapter);
        imageOutputView = findViewById(R.id.outputImageView);
        coverView = findViewById(R.id.coverView);
        closeButton = findViewById(R.id.closeButton);
    }

    @SuppressLint("SetTextI18n")
    public void runSDcpp() {
        if (sd_thread != null) {
            // SD.cpp is launched or was finished, displaying the log
            setupWindow();
            findViewById(R.id.wrapperView).setVisibility(View.GONE);
            addScreenDimmer();
            if (sdIOThread.process != null) lockScreenOn();  // still running, prevent sleeping
            findViewById(R.id.closeButton).setOnClickListener(cancelGenerationListener);
            sd_thread.updateActivity((SDActivity) myActivity);
            return;
        }
        boolean canUseOpenCL = false;
        final File nativeLibDir = new File(getApplicationInfo().nativeLibraryDir);
        final String[] libs = nativeLibDir.list();
        if (libs != null) {
            for (String lib : libs) {
                if (lib.equals(SDlibopenCL)) {
                    canUseOpenCL = true;
                    break;
                }
            }
        }

        outputArrayList = new ArrayList<>();  // initialize log
        setupWindow();
        promptEditor = findViewById(R.id.stringprompt);
        promptEditor.addTextChangedListener(createTextWatcher());
        negativeEditor = findViewById(R.id.stringnegprompt);
        optionsEditor = findViewById(R.id.stringopt);
        stepsEditor = findViewById(R.id.stringsteps);
        seedEditor = findViewById(R.id.stringseed);
        widthEditor = findViewById(R.id.stringwidth);
        heightEditor = findViewById(R.id.stringheight);
        seedEditor = findViewById(R.id.stringseed);
        cfgscaleEditor = findViewById(R.id.stringcfgscale);
        Button submitButton = findViewById(R.id.submitButton);

        fileList = listExternalFiles(sdWorkPath);
        if (fileList.isEmpty()) {
            submitButton.setEnabled(false);
            fileList.add("At first copy SD model file to this device.");
        }
        Collections.sort(fileList);
        Spinner model_spinner = findViewById(R.id.model_spinner);
        model_spinner.setOnItemSelectedListener(new ItemSelectedListener());
        ArrayAdapter<String> model_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileList);
        model_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        model_spinner.setAdapter(model_adapter);

        Spinner sampler_spinner = findViewById(R.id.sampler_spinner);
        sampler_spinner.setOnItemSelectedListener(new ItemSelectedListener());
        samplerList = Arrays.asList(samplerArr);
        ArrayAdapter<String> sampler_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, samplerList);
        sampler_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampler_spinner.setAdapter(sampler_adapter);

        Spinner scheduler_spinner = findViewById(R.id.scheduler_spinner);
        scheduler_spinner.setOnItemSelectedListener(new ItemSelectedListener());
        schedulerList = Arrays.asList(schedulerArr);
        ArrayAdapter<String> scheduler_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, schedulerList);
        scheduler_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scheduler_spinner.setAdapter(scheduler_adapter);

        TextView taesdview = findViewById(R.id.taesdmodel);
        if (!taesdModel.isEmpty()) {
            taesdview.setText(taesdModel.substring(taesdModel.lastIndexOf('/') + 1) + "  (in lora path)");
        }
        TextView taesdXLview = findViewById(R.id.taesdXLmodel);
        if (!taesdXLModel.isEmpty()) {
            taesdXLview.setText(taesdXLModel.substring(taesdXLModel.lastIndexOf('/') + 1) + "  (in lora path)");
        }
        taesdchecker = findViewById(R.id.taesdchecker);
        taesdchecker.setOnCheckedChangeListener(new onCheckedChangeListener());
        taesdXLchecker = findViewById(R.id.taesdXLchecker);
        taesdXLchecker.setOnCheckedChangeListener(new onCheckedChangeListener());
        taesdchecker.setEnabled(!taesdModel.isEmpty());
        taesdXLchecker.setEnabled(!taesdXLModel.isEmpty());
        embeddchecker = findViewById(R.id.embeddchecker);
        embeddchecker.setEnabled(!helperPath.isEmpty());
        cpuchecker = findViewById(R.id.usecpu);
        cpuchecker.setChecked(!canUseOpenCL);
        cpuchecker.setEnabled(canUseOpenCL);
        TextView loraPathView = findViewById(R.id.lorapath);
        loraPathView.setText(helperPath);
        submitButton.setOnClickListener(v -> {
            View wrapperView = findViewById(R.id.wrapperView);
            wrapperView.setVisibility(View.GONE);
            String prompt = promptEditor.getText().toString();
            if (prompt.isEmpty()) {
                prompt = "something";
            }
            String negative = negativeEditor.getText().toString();
            outputImagePath = sdWorkPath + "/output" + (System.currentTimeMillis() / 1000L) + ".jpg";
            String taesdoption = "";
            if (taesdchecker.isChecked()) {
                taesdoption = taesdModel;
            }
            if (taesdXLchecker.isChecked()) {
                taesdoption = taesdXLModel;
            }
/* 0 */     ArrayList<String> arguments = new ArrayList<>(Arrays.asList("",
/* 1 2 */           "-m", selectedModelfile,
/* 3 4 */           "-p", prompt,
/* 5 6 */           "-n", negative,
/* 7 8 */           "-o", outputImagePath,
/* 9 10 */          "--lora-model-dir", helperPath,
/* 11 12 */         "--embd-dir", embeddchecker.isChecked() ? helperPath : "",
/* 13 14 */         "--sampling-method", selectedSampler,
/* 15 16 */         "--taesd", taesdoption,
/* 17 18 */         "--cfg-scale", check(cfgscaleEditor.getText().toString(), "7.0"),
/* 19 20 */         "--seed", check(seedEditor.getText().toString(), "-1"),
/* 21 22 */         "--steps", checkSteps(stepsEditor.getText().toString(), "25"),
/* 23 24 */         "--width", checkDimension(widthEditor.getText().toString(), "512"),
/* 25 26 */         "--height", checkDimension(heightEditor.getText().toString(), "512"),
/* 27 28 */         "--scheduler", selectedScheduler,
/* 27 28 */         //"--schedule", selectedScheduler,
/* 29 */            "-v"
                    // "--mmap"  // --params-backend seems to work better
            ));
            libPath = nativeLibDir.toString();
            String libVendorPath = "/vendor/lib64";
            if (cpuchecker.isChecked()) {
                sdFileName = SDlib;
                arguments.add("--vae-tiling");  // currently not for openCL
                // for some budget devices with low RAM and NO usable openCL drivers:
                if (selectedModelfile.toUpperCase().contains("SSD")) {
                    arguments.add("--type");
                    arguments.add("q8_0");
                } else {
                    if (selectedModelfile.toUpperCase().contains("XL")) {
                        if (!selectedModelfile.toUpperCase().contains("GGUF")) {
                            arguments.add("--type");
                            arguments.add("q8_0"); //"q4_0";
                        } // else keep as GGUF is
                    } else {
                        if (selectedModelfile.toUpperCase().contains("NITRO")) {
                            arguments.add("--type");
                            arguments.add("q4_0");
                            arguments.add("--scheduler");
                            arguments.add("sgm_uniform");
                            arguments.add("--timestep-shift");
                            arguments.add("250");
                        }
                    }
                }
            } else {
                sdFileName = SDlibopenCL;
                libPath += ":" + libVendorPath;
                // this settings seem to work quite well at openCL/ADRENO 810
                arguments.add("--diffusion-conv-direct");
                arguments.add("--vae-conv-direct");
                arguments.add("--type");
                arguments.add("q4_0");  // "f16";    (optimal for huge VRAM)
                arguments.add("-t");    // for q4_0  (less VRAM)
                arguments.add("1");     // for q4_0  (less VRAM)
            }
            File file = new File(nativeLibDir, sdFileName);
            if (!(file.exists() && file.length() > 0)) {
                Toast.makeText(myActivity, sdFileName + " not found,\nplease rebuild this app", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            sdProgramPath = file.getAbsolutePath();
            arguments.set(0, sdProgramPath);

            final String anySpace = /* Build.VERSION.SDK_INT < Build.VERSION_CODES.N ?
                    "[\\s\\t\\n\\r\\f\\xA0\\x0B\\x85\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000\\u2028\\u2029]" :  //*/
                    "[\\h\\v\\s]";
            String[] args = optionsEditor.getText().toString()
                    // trim*trim
                    .replaceAll("(^" + anySpace + "+)|(" + anySpace + "+$)", "")
                    // arg1 "arg2"  "arg3 with spaces"   ""
                    .split(anySpace + "+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            if (!args[0].isEmpty()) arguments.addAll(Arrays.asList(args));

            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputImagePath + ".sh"));
                args = arguments.toArray(args).clone();  // always expanded
                if (libPath.contains(libVendorPath)) {
                    args[0]= "LD_LIBRARY_PATH=" + libVendorPath + " sd \\\n";
                } else {
                    args[0] = "sd \\\n";
                }
                args[4] = "\"" + args[4]
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        + "\" \\\n";  // prompt
                args[6] = "\"" + args[6]
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        + "\" \\\n";  // negative prompt
                args[8] = "output.jpg \\\n";
                args[10] = "\"" + args[10] + "\"";
                args[12] = "\"" + args[12] + "\"";
                args[16] = "\"" + args[16] + "\"";
                String cmdline = String.join(" ", args);
                writer.write(cmdline);
                writer.close();
            } catch (IOException e) {
                Toast.makeText(myActivity, "Error writing cmdfile", Toast.LENGTH_SHORT).show();
            }
            if (sd_thread != null) {
                try {
                    sd_thread.processDestroy();
                } catch (Exception ignored) {}
            }
            sd_thread = new sdIOThread((SDActivity) myActivity, arguments, sdWorkPath, libPath);
            sd_thread.start();
        });

        TextView spm = findViewById(R.id.stringpromptmsg);
        spm.setOnLongClickListener(v -> {
            AppCompatDelegate acd = getDelegate();
            acd.setLocalNightMode(acd.getLocalNightMode() == AppCompatDelegate.MODE_NIGHT_YES ?
                    AppCompatDelegate.MODE_NIGHT_NO :
                    AppCompatDelegate.MODE_NIGHT_YES
            );
            return true;
        });

        Button closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(cancelGenerationListener);
    }

    @NonNull
    private Bitmap rotateBitmap(Bitmap source, @SuppressWarnings("SameParameterValue") float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void displayResultImageFile(@NonNull File imgFile) {
        Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
        int h = myBitmap.getHeight();
        int b = myBitmap.getWidth();
        if (h > b) {
            myBitmap = rotateBitmap(myBitmap, 270);
        }
        imageOutputView.setImageBitmap(myBitmap);
    }

    public void subFinished(int exitcode, final String msg) {
        runOnUiThread(() -> {
            File file = new File(outputImagePath);
            if (exitcode == 0 && file.exists()) {
                imageOutputView.setVisibility(View.VISIBLE);
                StringBuilder sb = new StringBuilder();
                for (String line : outputArrayList)
                    sb.append(line).append("\n");
                String alllog = sb.toString();
                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(outputImagePath + ".log.txt"));
                    writer.write(alllog);
                    writer.close();
                } catch (IOException e) {
                    Toast.makeText(myActivity, "Error writing logfile", Toast.LENGTH_SHORT).show();
                }
                displayResultImageFile(file);

            } else {
                String errMsg = !msg.isEmpty() ? msg
                        : (exitcode == 0 && !file.exists()) ? "Result image not found"
                        : "SD process failed with code: " + exitcode;
                Toast.makeText(myActivity, errMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideDecor(Window w, boolean h) {
        if (h) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {  // Android 9+ supports cutouts
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ?  // Android 11+
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS :
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12+ because of BEHAVIOR_DEFAULT
                w.setDecorFitsSystemWindows(false);
                WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars() |
                            WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                View d = w.getDecorView();
                d.setSystemUiVisibility(
                        d.getSystemUiVisibility()
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {  // Android 9+ supports cutouts
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                w.setAttributes(lp);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12+ because of BEHAVIOR_DEFAULT
                w.setDecorFitsSystemWindows(true);
                WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    c.show(WindowInsets.Type.statusBars() |
                            WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
                }
            } else {
                View d = w.getDecorView();
                d.setSystemUiVisibility(
                        d.getSystemUiVisibility() & ~(
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                );
            }
        }
    }

    public void lockScreenOn() {
        runOnUiThread(() -> {
            Window viewWindow = getWindow();
            viewWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams lp = viewWindow.getAttributes();
            lp.screenBrightness = 0.0f;
            viewWindow.setAttributes(lp);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    public void addScreenDimmer() {
        runOnUiThread(() -> {
            Window viewWindow = getWindow();
            viewWindow.setNavigationBarColor(Color.BLACK);
            viewWindow.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            coverView.setAlpha(0.0f);
            closeButton.setVisibility(View.VISIBLE);

            GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                    boolean v = closeButton.getVisibility() == View.VISIBLE;
                    coverView.setAlpha(v ? 1.0f : 0.0f);
                    closeButton.setVisibility(v ? View.GONE : View.VISIBLE);
                    return true;
                }
                @Override
                public boolean onDoubleTap(@NonNull MotionEvent e) {
                    boolean v = closeButton.getVisibility() == View.VISIBLE;
                    closeButton.setVisibility(v ? View.GONE : View.VISIBLE);
                    hideDecor(viewWindow, v);
                    return true;
                }
            });
            coverView.setOnTouchListener((View v, MotionEvent e) -> {
                gestureDetector.onTouchEvent(e);
                return sdLogView.dispatchTouchEvent(e);
            });
            coverView.setVisibility(View.VISIBLE);
        });
    }

    public void restoreScreenBrightness() {
        runOnUiThread(() -> {
            Window viewWindow = getWindow();
            viewWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams lp = viewWindow.getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            viewWindow.setAttributes(lp);
        });
    }

    public void debugMsg(final String msg) {
        runOnUiThread(() -> {
            boolean isProgressBar = msg.startsWith("  |");
            final String clearToEOL = "\u001B[K";
            String text = msg;
            if (lastProgressBar && !isProgressBar) {
                outputArrayList.add("");  // at log begin and at progress bar end
            }
            if (text.contains(clearToEOL)) {
                text = text.replace(clearToEOL, "");
            }
            int last = outputArrayList.size() - 1;
            if (!lastMsg.equals(text)) {
                outputArrayList.set(last, text);
                lastProgressBar = isProgressBar;
                if (!isProgressBar) {
                    outputArrayList.add("");  // new line if no progress bar
                }
                arrayAdapter.notifyDataSetChanged();
                sdLogView.setSelection(last);
            }
            lastMsg = text;
        });
    }

    private String checkDimension(String input, @SuppressWarnings("SameParameterValue") String def) {
        input = input.replaceAll("\\s", "");
        if (!input.isEmpty()) {
            try {
                int number = Integer.parseInt(input);
                int rounded = (number + 63) / 64 * 64;            // round up for 64
                def = Integer.toString(rounded);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private String checkSteps(String input, @SuppressWarnings("SameParameterValue") String def) {
        input = input.replaceAll("\\s", "");
        if (!input.isEmpty()) {
            try {
                int number = Integer.parseInt(input);
                if (number > 0)
                    return input;
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private String check(String input, @SuppressWarnings("SameParameterValue") String def) {
        input = input.replaceAll("\\s", "");
        if (!input.isEmpty()) {
            try {
                Integer.parseInt(input);
                return input;
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    @NonNull
    private List<String> listExternalFiles(String storagePath) {
        taesdModel = taesdXLModel = helperPath = "";
        List<String> fileList = new ArrayList<>();
        File storageRoot = new File(storagePath);
        if (storageRoot.exists() && storageRoot.canRead()) {
            listFilesRecursively(storageRoot, fileList);
        }
        return fileList;
    }

    private void listFilesRecursively(File directory, List<String> fileList) {
        try {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        listFilesRecursively(file, fileList);
                    } else {
                        String fileName = file.getName();
                        if (fileName.endsWith(".ckpt") ||
                                fileName.endsWith(".gguf") ||
                                fileName.endsWith(".pth") ||
                                fileName.endsWith(".safetensors")) {
                            if (fileName.contains("taesdxl")) {
                                taesdXLModel = file.getAbsolutePath();
                            } else {
                                if (fileName.contains("taesd")) {
                                    taesdModel = file.getAbsolutePath();
                                } else if (fileName.contains("lora")) {
                                    helperPath = file.getParent();      // for LoRA and taesd etc
                                } else {
                                    if (file.length() > (500 * 1024 * 1024)) {
                                        fileList.add(file.getAbsolutePath());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (
                SecurityException ignored) {
        }
    }

   /* @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    //*/

    final public void processInfo(String info) {
        final Process process = sdIOThread.process;
        if (process != null) runOnUiThread(() -> Toast.makeText(myActivity, info + "\n" + process, Toast.LENGTH_SHORT).show());
    }

    TextWatcher createTextWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.toString().contains("<lora:")) {
                    // here some lazy checks:
                    if (editable.toString().toUpperCase().contains("LCM") || editable.toString().toUpperCase().contains("VEGA")) {
                        Spinner sampler_spinner = findViewById(R.id.sampler_spinner);
                        sampler_spinner.setSelection(LCM_POS);
                    }
                }
            }
        };
    }

    public class ItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (parent.getId() == R.id.model_spinner) {
                selectedModelfile = fileList.get(position);
                // here follow some lazy checks for easy defaults:
                if (selectedModelfile.toUpperCase().contains("SDXS")) {
                    stepsEditor.setText("1");
                    cfgscaleEditor.setText("1");
                    taesdXLchecker.setChecked(false);
                    taesdchecker.setChecked(false);
                } else {
                    if (selectedModelfile.toUpperCase().contains("SSD")
                            || selectedModelfile.toUpperCase().contains("VEGA")
                            || selectedModelfile.toUpperCase().contains("XL")) {
                        if (!taesdXLModel.isEmpty()) {
                            taesdXLchecker.setChecked(true);
                        }
                    } else {
                        if (!taesdModel.isEmpty()) {
                            taesdchecker.setChecked(true);
                        }
                    }
                }
            } else {
                if (parent.getId() == R.id.sampler_spinner) {
                    selectedSampler = samplerList.get(position);
                    if (selectedSampler.contains("lcm")) {
                        stepsEditor.setText("4");
                        cfgscaleEditor.setText("1");
                    }
                }
                if (parent.getId() == R.id.scheduler_spinner)
                    selectedScheduler = schedulerList.get(position);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    public class onCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            if (checked) {
                if (compoundButton.getId() == R.id.taesdXLchecker) {
                    taesdchecker.setChecked(false);
                } else {
                    if (compoundButton.getId() == R.id.taesdchecker) {
                        taesdXLchecker.setChecked(false);
                    }
                }
            }
        }
    }

/*
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        processInfo(null, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        processInfo(null, "onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        processInfo(null, "onResume");
    }

    @Override
    protected void onStart() {
        super.onStart();
        processInfo(null, "onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        processInfo(null, "onRestart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (process != null)
            processInfo(null, "onStop");
    }
*/
}
