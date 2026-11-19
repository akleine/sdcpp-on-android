package local.kleine.sdcpp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SDActivity extends AppCompatActivity {
    public static final int EXIT_CODE_DO_RESTART = 1001;
    private SDViewModel vm = null;
    private ArrayList<String> logList = null;
    private static sdIOThread sd_thread = null;
    private final Activity myActivity;
    @NonNull private String selectedModelfile = "";
    private String selectedSampler, selectedScheduler, taesdModel, taesdXLModel;
    private String sdProgramPath, helperPath, libPath, sdFileName; // Note: "sd" or "sd_cli" executable needs renaming because it is now located inside jniLibs
    private final static String libVendorPath = "/vendor/lib64";  // no support possible for 32 bit with openCL
    private final static String ChklibopenCL = "libchkopenCL.so";
    private final static String SDlibopenCL = "libsdopenCL.so";
    private final static String SDlib = "libsd.so";
    private final static String defExt = ".jpg";
    @NonNull private String selectedExt = defExt;
    private final static String DEFAULT_QUANTIZER = "default";  // see @array/quant_entries
    @NonNull private String selectedQuant = DEFAULT_QUANTIZER;
    private File nativeLibDir;
    protected final String sdWorkPath = android.os.Environment.
            getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).
            getAbsolutePath();
    private final static String anySpace = /* Build.VERSION.SDK_INT < Build.VERSION_CODES.N ?
                    "[\\s\\t\\n\\r\\f\\xA0\\x0B\\x85\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000\\u2028\\u2029]" :  //*/
            "\\s";

    private EditText promptEditor, negativeEditor, seedEditor, stepsEditor,
            widthEditor, heightEditor, cfgscaleEditor, optionsEditor;
    private CheckBox taesdchecker, taesdXLchecker, embeddchecker, gpuchecker, writeCmdlineChecker, vaetilingchecker, convdirectchecker;
    private View wrapperView;
    private ListView sdLogView;
    private View coverView;
    private Button closeButton;
    private Spinner sampler_spinner;
    private Spinner scheduler_spinner;
    private Spinner model_spinner;
    private Spinner ext_spinner;
    private Spinner quant_spinner;
    private ImageView imageOutputView;
    private ArrayAdapter<String> arrayAdapter;

    // see @array/sampler_entries
    private static final String SAMPLER_LCM = "lcm";
    // see @array/scheduler_entries
    private static final String SCHEDULER_DISCRETE = "discrete";
    private static final String SCHEDULER_SGM_UNIFORM = "sgm_uniform";

    private List<String> fileList;


    public SDActivity() {
        super();
        myActivity = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(this).get(SDViewModel.class);
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
            // 1st rejection  -> true (user probably needs an explanation)
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

    final View.OnClickListener cancelGenerationListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            coverView.setOnTouchListener(null);
            coverView.setVisibility(View.GONE);
            restoreScreenBrightness();
            if (sd_thread != null) {
                sd_thread.processDestroy();
            }
            Intent intent = new Intent(SDActivity.this, MainActivity.class);
            intent.putExtra("result", "ready");
            setResult(RESULT_OK, intent);  // do not restart, notification will be in subFinished()
            // waiting for sdIOThread to show all toasts before SDActivity.finish()
            // with 1 second timeout
            new Thread() {
                public void run() {
                    long t0 = System.currentTimeMillis();
                    while (sd_thread != null && sd_thread.isRunning
                            && System.currentTimeMillis() - t0 < 1000) {
                        try {
                            //noinspection BusyWait
                            Thread.sleep(100);
                        } catch (Exception ignored) {
                        }
                    }
                    runOnUiThread(() -> {
                        sd_thread = null;
                        finish();
                    });
                }
            }.start();
        }
    };

    private void setupLogWindow() {
        // setup, common for settings and log/result views
        androidx.appcompat.app.ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.hide();                                                      // request all visible space available
        }

        // not needed, rotations are handled
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED); // needed for sd log output

        setContentView(R.layout.activity_main);
        sdLogView = findViewById(R.id.sdLogView);
        logList = (sd_thread == null) ? new ArrayList<>()
                : new ArrayList<>(sd_thread.outputArrayList);
        arrayAdapter = new ArrayAdapter<>(this, R.layout.custom_list_item, R.id.output_item_line, logList);
        sdLogView.setAdapter(arrayAdapter);
        imageOutputView = findViewById(R.id.outputImageView);
        coverView = findViewById(R.id.coverView);
        closeButton = findViewById(R.id.closeButton);
        wrapperView = findViewById(R.id.wrapperView);
    }

    @SuppressLint("SetTextI18n")
    public void runSDcpp() {
        if (sd_thread != null) {
            // SD.cpp is launched or was finished, displaying the log
            setupLogWindow();
            wrapperView.setVisibility(View.GONE);
            addScreenDimmer();
            if (sd_thread.process != null)
                lockScreenOn();  // still running, prevent sleeping
            sd_thread.updateActivity((SDActivity) myActivity);
            File file = new File(sd_thread.outputImagePath);
            closeButton.setOnClickListener(cancelGenerationListener);
            if (file.exists()) {
                imageOutputView.setVisibility(View.VISIBLE);
                displayResultImageFile(file);
            }
            return;
        }
        nativeLibDir = new File(getApplicationInfo().nativeLibraryDir);
        boolean canUseOpenCL = false;
        if (checkOpenCLdriver()) {
            final String[] libs = nativeLibDir.list();
            if (libs != null) {
                for (String lib : libs) {
                    if (lib.equals(SDlibopenCL)) {
                        canUseOpenCL = true;
                        break;
                    }
                }
            }
        }
        setupLogWindow();


        Button fileSelectButton = findViewById(R.id.fileSelectButton);
        fileSelectButton.setOnClickListener(new SDUIoptionsLoader());
        promptEditor = findViewById(R.id.stringprompt);
        setupEditText(promptEditor);
        promptEditor.addTextChangedListener(createTextWatcher());
        negativeEditor = findViewById(R.id.stringnegprompt);
        setupEditText(negativeEditor);
        optionsEditor = findViewById(R.id.stringopt);
        setupEditText(optionsEditor);
        stepsEditor = findViewById(R.id.stringsteps);
        setupEditText(stepsEditor);
        seedEditor = findViewById(R.id.stringseed);
        setupEditText(seedEditor);
        widthEditor = findViewById(R.id.stringwidth);
        setupEditText(widthEditor);
        heightEditor = findViewById(R.id.stringheight);
        setupEditText(heightEditor);
        cfgscaleEditor = findViewById(R.id.stringcfgscale);
        setupEditText(cfgscaleEditor);
        Button submitButton = findViewById(R.id.submitButton);

        fileList = listExternalFiles(sdWorkPath);
        if (fileList.isEmpty()) {
            submitButton.setEnabled(false);
            fileList.add("At first copy SD model file to this device.");
        }
        Collections.sort(fileList);
        model_spinner = findViewById(R.id.model_spinner);
        model_spinner.setOnItemSelectedListener(new ItemSelectedListener());
        ArrayAdapter<String> model_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileList);
        model_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        model_spinner.setAdapter(model_adapter);
        setupSpinner(model_spinner);
        sampler_spinner = findViewById(R.id.sampler_spinner);
        setupSpinner(sampler_spinner);
        scheduler_spinner = findViewById(R.id.scheduler_spinner);
        setupSpinner(scheduler_spinner);
        ext_spinner = findViewById(R.id.ext_spinner);
        setupSpinner(ext_spinner);
        quant_spinner = findViewById(R.id.quant_spinner);
        setupSpinner(quant_spinner);

        // animator setup
        LinearLayout animatedLayout = findViewById(R.id.details_animation);
        View detailsHeader = findViewById(R.id.details_header);
        View detailsFooter = findViewById(R.id.details_footer);
        View detailsArrow = findViewById(R.id.details_arrow);
        LinearLayout detailsWrapper = findViewById(R.id.details_wrapper);
        LinearLayout detailsLayout = findViewById(R.id.details_layout);
        TextView detailsCaption = findViewById(R.id.details_caption);
        if (vm.make_open) {
            ViewGroup.LayoutParams wlp = detailsWrapper.getLayoutParams();
            wlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            detailsWrapper.setLayoutParams(wlp);
            detailsArrow.setRotation(0);
            detailsCaption.setText(getString(R.string.more_settings_opened));
        } else {
            detailsLayout.post(() -> {
                // measuring height of hidden settings layout
                // after the sizes of controls have been measured
                ViewGroup.LayoutParams lp = detailsLayout.getLayoutParams();
                detailsLayout.measure(
                        View.MeasureSpec.makeMeasureSpec(detailsLayout.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                lp.height = detailsLayout.getMeasuredHeight();
                detailsLayout.setLayoutParams(lp);
            });
        }
        View.OnClickListener detailsTogggler = v -> {
            ViewGroup.LayoutParams wlp = detailsWrapper.getLayoutParams();
            ViewGroup.LayoutParams lp = detailsLayout.getLayoutParams();
            vm.make_open = wlp.height != ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = vm.make_open ? ViewGroup.LayoutParams.WRAP_CONTENT : detailsLayout.getHeight();
            detailsLayout.setLayoutParams(lp);
            TransitionManager.beginDelayedTransition(animatedLayout, new AutoTransition()
                    .setDuration(500).setInterpolator(new DecelerateInterpolator())
            );
            wlp.height = vm.make_open ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            detailsWrapper.setLayoutParams(wlp);
            detailsArrow.setRotation(vm.make_open ? 0 : 270);
            detailsCaption.setText(getString(vm.make_open ? R.string.more_settings_opened : R.string.more_settings_closed));
        };
        detailsHeader.setOnClickListener(detailsTogggler);
        detailsFooter.setOnClickListener(detailsTogggler);

        // checkboxes may switch each other, therefore
        // all references are needed before setup
        taesdchecker = findViewById(R.id.taesdchecker);
        taesdXLchecker = findViewById(R.id.taesdXLchecker);
        embeddchecker = findViewById(R.id.embeddchecker);
        gpuchecker = findViewById(R.id.usegpu);
        vaetilingchecker = findViewById(R.id.vaetiling);
        convdirectchecker = findViewById(R.id.convdirect);
        writeCmdlineChecker = findViewById(R.id.writecmdchecker);
        CheckBox writeLogChecker = findViewById(R.id.writelogchecker);

        setupCheckBox(taesdchecker, true, taesdCheckListener);  // suggest by default
        if (taesdModel.isEmpty()) {
            // override user choice if Taesd is not available anymore
            taesdchecker.setChecked(false);  // off state does not affect anything
            taesdchecker.setEnabled(false);
        } else {
            TextView taesdview = findViewById(R.id.taesdmodel);
            taesdview.setText(taesdModel.substring(taesdModel.lastIndexOf('/') + 1) + "  (in lora path)");
        }
        setupCheckBox(taesdXLchecker, true, taesdCheckListener);  // suggest by default
        if (taesdXLModel.isEmpty()) {
            // override user choice if TaesdXL is not available anymore
            taesdXLchecker.setChecked(false);  // off state does not affect anything
            taesdXLchecker.setEnabled(false);
        } else {
            TextView taesdXLview = findViewById(R.id.taesdXLmodel);
            taesdXLview.setText(taesdXLModel.substring(taesdXLModel.lastIndexOf('/') + 1) + "  (in lora path)");
        }
        setupCheckBox(embeddchecker, false, onCheckedChangeListener);  // disable embeddings by default
        if (helperPath.isEmpty()) {
            embeddchecker.setChecked(false);  // off state does not affect anything
            embeddchecker.setEnabled(false);
        }
        setupCheckBox(gpuchecker, true, new GpuCheckListener());  // enable GPU by default
        if (!canUseOpenCL) {
            if (gpuchecker.isChecked())        // off state affects quantizaton, therefore
                gpuchecker.setChecked(false);  // skipping unneccessary switching
            gpuchecker.setEnabled(false);
        }
        setupCheckBox(vaetilingchecker, true, onCheckedChangeListener);  // enable by default to reduce memory
        setupCheckBox(convdirectchecker, true, onCheckedChangeListener);
        setupCheckBox(writeCmdlineChecker, true, onCheckedChangeListener);  // write command script by default
        setupCheckBox(writeLogChecker, true, onCheckedChangeListener);  // save log by default

        TextView loraPathView = findViewById(R.id.lorapath);
        loraPathView.setText(helperPath);
        submitButton.setOnClickListener(v -> {
            wrapperView.setVisibility(View.GONE);
            vm.savePreferences();
            String prompt = promptEditor.getText().toString();
            if (prompt.isEmpty()) {
                prompt = "something";
            }
            String negative = negativeEditor.getText().toString();
            String localOutputImagePath = sdWorkPath + "/output" + (System.currentTimeMillis() / 1000L) + selectedExt;
            String taesdoption = "";
            if (taesdchecker.isChecked()) {
                taesdoption = taesdModel;
            }
            if (taesdXLchecker.isChecked()) {
                taesdoption = taesdXLModel;
            }
            /* 0 */
            ArrayList<String> arguments = new ArrayList<>(Arrays.asList("",
                    /* 1 2 */           "-m", sdWorkPath + File.separator + selectedModelfile,
                    /* 3 4 */           "-p", prompt,
                    /* 5 6 */           "-n", negative,
                    /* 7 8 */           "-o", localOutputImagePath,
                    /* 9 10 */          "--lora-model-dir", helperPath,
                    /* 11 12 */         "--embd-dir", embeddchecker.isChecked() ? helperPath : "",
                    /* 13 14 */         "--sampling-method", selectedSampler,
                    /* 15 16 */         "--taesd", taesdoption,
                    /* 17 18 */         "--cfg-scale", checkFloat(cfgscaleEditor.getText().toString(), "7.0"),
                    /* 19 20 */         "--seed", checkInt(seedEditor.getText().toString(), "-1"),
                    /* 21 22 */         "--steps", checkSteps(stepsEditor.getText().toString(), "25"),
                    /* 23 24 */         "--width", checkDimension(widthEditor.getText().toString(), "512"),
                    /* 25 26 */         "--height", checkDimension(heightEditor.getText().toString(), "512"),
                    /* 27 28 */         "--scheduler", selectedScheduler,
                    /* 29 */            "-v"
                    // "--mmap"  // --params-backend seems to work better
            ));
            // we can use arguments.add() even if overwriting an earlier argument, no problem for sd.cpp,
            // and we can overwrite earlier arguments by using optionsEditor's contents (at the end of list)
            libPath = nativeLibDir.toString();      // get openCL libs
            String tempFolderPath = "";
            String model = selectedModelfile.toUpperCase();
            if (model.contains("NITROSD-REAL") ||
                    model.contains("DIFF-INSTRUCTSTAR")) {
                arguments.add("--timestep-shift");
                arguments.add(model.contains("NITRO") ? "250" : "400");
                arguments.add("--auto-fit");
                arguments.add("off");
            }
            if (vaetilingchecker.isChecked())
                arguments.add("--vae-tiling");
            if (convdirectchecker.isChecked()) {
                arguments.add("--diffusion-conv-direct");
                arguments.add("--vae-conv-direct");
            }
            if (!selectedQuant.equals(DEFAULT_QUANTIZER)) {
                arguments.add("--type");
                arguments.add(selectedQuant);
            }
            if (model.contains("MINIT2I") || model.contains("ZIMAGE")) {
                arguments.set(1, "--diffusion-model"); // replace "-m"
                // Currently other needed options like "--t5xxl" or "--llm" or "--vae" must be entered via "additional options"
            }
            if (!gpuchecker.isChecked()) {
                sdFileName = SDlib;
            } else {
                sdFileName = SDlibopenCL;
                tempFolderPath = getApplicationContext().getCacheDir().getAbsolutePath();
                libPath += ":" + libVendorPath;
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
            String[] args = optionsEditor.getText().toString()
                    // trim*trim
                    .replaceAll("(^" + anySpace + "+)|(" + anySpace + "+$)", "")
                    // arg1 "arg2"  "arg3 with spaces"   ""
                    .split(anySpace + "+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (int i = 0; i < args.length; i++) {
                if (("-o".equals(args[i]) || "--output".equals(args[i])) && i < args.length - 1) {
                    i++; // get next arg and remove dangerous chars
                    args[i] = args[i].replaceAll("[^a-zA-Z0-9._-]", "") + selectedExt;
                    localOutputImagePath = sdWorkPath + "/" + args[i];
                }
            }
            if (!args[0].isEmpty()) arguments.addAll(Arrays.asList(args));
            if (writeCmdlineChecker.isChecked()) {
                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(localOutputImagePath + ".sh"));
                    args = arguments.toArray(args).clone();  // always expanded
                    if (libPath.contains(libVendorPath)) {
                        args[0] = "LD_LIBRARY_PATH=" + libVendorPath + " sd-cli";
                    } else {
                        args[0] = "sd";
                    }
                    args[4] = "\"" + args[4]
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            + "\"";  // prompt
                    args[6] = "\"" + args[6]
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            + "\"";  // negative prompt
                    args[8] = "output" + selectedExt;
                    args[10] = "\"" + args[10] + "\"";
                    args[12] = "\"" + args[12] + "\"";
                    args[16] = "\"" + args[16] + "\"";
                    String cmdline = String.join(" ", args);
                    cmdline = cmdline.replaceAll("\\s+(?=-(?!\\d))", " \\\\\n\t");
                    // adding some pseudo args as extra info for later file usage in parseShellScript()
                    cmdline = cmdline.concat(" \n exit # --script ends here--");
                    cmdline = cmdline.concat(taesdchecker.isChecked() ? "\n\t--use-taesd" : "\n\t--no-taesd");
                    cmdline = cmdline.concat(taesdXLchecker.isChecked() ? "\n\t--use-taesdXL" : "\n\t--no-taesdXL");
                    cmdline = cmdline.concat(convdirectchecker.isChecked() ? "" : "\n\t--no-conv-direct");
                    cmdline = cmdline.concat(vaetilingchecker.isChecked() ? "" : "\n\t--no-vae-tiling");
                    cmdline = cmdline.concat(embeddchecker.isChecked() ? "" : "\n\t--no-embeddings");
                    cmdline = cmdline.concat("\n\t--extra-options \"" + optionsEditor.getText().toString()) + "\"";
                    writer.write(cmdline);
                    writer.close();
                } catch (IOException e) {
                    Toast.makeText(myActivity, "Error writing cmdfile", Toast.LENGTH_SHORT).show();
                }
            }
            if (sd_thread != null) {
                try {
                    sd_thread.processDestroy();
                } catch (Exception ignored) {
                }
            }
            sd_thread = new sdIOThread((SDActivity) myActivity, arguments, sdWorkPath, libPath, tempFolderPath, localOutputImagePath);
            sd_thread.start();

            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(wrapperView.getWindowToken(), 0);
            closeButton.setOnClickListener(cancelGenerationListener);
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

    }

    @NonNull
    private Bitmap rotateBitmap(Bitmap source, @SuppressWarnings("SameParameterValue") float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void displayResultImageFile(@NonNull File imgFile) {
        Bitmap myBitmap = vm.getBitmap();
        if (myBitmap == null) {
            myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            vm.setBitmap(myBitmap);
        }
        if (myBitmap == null) {
            Toast.makeText(myActivity, "Cannot decode image: " + sd_thread.outputImagePath.substring(sdWorkPath.length() + 1), Toast.LENGTH_SHORT).show();
            return;
        }
        int bh = myBitmap.getHeight();
        int bw = myBitmap.getWidth();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int dh = dm.heightPixels;
        int dw = dm.widthPixels;
        boolean verticalImage = bh >= bw;
        boolean verticalScreen = dh >= dw;
        if (bh != bw && dh != dw && verticalImage != verticalScreen) {
            myBitmap = rotateBitmap(myBitmap, verticalImage ? 270 : 90);
        }
        imageOutputView.setImageBitmap(myBitmap);
    }

    public void subFinished(int exitcode, final String msg) {
        runOnUiThread(() -> {
            File file = new File(sd_thread.outputImagePath);
            if (exitcode == 0 && file.exists()) {
                imageOutputView.setVisibility(View.VISIBLE);
                if (vm.getPrefBoolean(SDViewModel.prefsKeyLog, true)) {
                    StringBuilder sb = new StringBuilder();
                    for (String line : sd_thread.outputArrayList)
                        sb.append(line).append("\n");
                    String alllog = sb.toString();
                    try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(sd_thread.outputImagePath + ".log.txt"));
                        writer.write(alllog);
                        writer.close();
                    } catch (IOException e) {
                        Toast.makeText(myActivity, "Error writing logfile", Toast.LENGTH_SHORT).show();
                    }
                }
                Toast.makeText(myActivity, msg, Toast.LENGTH_LONG).show();  // success, display time
                displayResultImageFile(file);
            } else {
                // image doesn't exist or non-zero exit code
                String errMsg = !msg.isEmpty() ? msg  // error or interrupt
                        : (exitcode == 0) ? "Result image not found"  // condition with image was checked earlier
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

                public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 == null) return false;
                    float dX = e2.getX() - e1.getX();
                    float dY = e1.getY() - e2.getY();
                    if (Math.abs(dX) > Math.abs(dY)) {
                        if (dX > 0) {
                            lockScreenOn();                      // right swipe
                        } else {
                            restoreScreenBrightness();           // left swipe
                        }                                        // Up-/Down Swipe is needed for LOG view
                        return true;
                    }
                    return false;
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

    public void updateLogView() {
        runOnUiThread(() -> {
// Synchronization takes ~8 times more microseconds (300 => 2400 per output line)
// and needed only if ConcurrentModificationException
// or IndexOutOfBoundsException exceptions occur.
// They should not happen because stdout read cycle
// waits for readline() and has enough time to copy values to logList
//            synchronized (sd_thread.outputArrayList) {
            int countA = logList.size();
            int countL = sd_thread.outputArrayList.size();
            boolean doUpdate = false;
            if (countA != 0 && countL >= countA) {
                int lastA = countA - 1;
                String line = sd_thread.outputArrayList.get(lastA);
                if (!line.equals(logList.get(lastA))) {
                    logList.set(lastA, line);
                    doUpdate = true;
                }
            }
            if (countL > countA) {
                for (int lastA = countA; lastA < countL; lastA++)
                    logList.add(sd_thread.outputArrayList.get(lastA));
                doUpdate = true;
            }
            if (doUpdate) {
                arrayAdapter.notifyDataSetChanged();
                sdLogView.setSelection(countL - 1);
            }
//            }  // synchronized
        });
    }

    private String checkDimension(String input, @SuppressWarnings("SameParameterValue") String def) {
        input = input.replaceAll(anySpace, "");
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
        input = input.replaceAll(anySpace, "");
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

    private String checkInt(String input, @SuppressWarnings("SameParameterValue") String def) {
        input = input.replaceAll(anySpace, "");
        if (!input.isEmpty()) {
            try {
                Integer.parseInt(input);
                return input;
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private String checkFloat(String input, @SuppressWarnings("SameParameterValue") String def) {
        input = input.replaceAll(anySpace, "");
        if (!input.isEmpty()) {
            try {
                Float.parseFloat(input);
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
                                        fileList.add(file.getAbsolutePath().substring(sdWorkPath.length() + 1));
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

    private boolean checkOpenCLdriver() {
        File file = new File(nativeLibDir, ChklibopenCL);
        if (!(file.exists() && file.length() > 0)) {
            // no autodetection library found, allow to select
            // OpenCL SD.cpp version manually if present
            return true;
        } else {
            boolean result = false;
            try {
                Process process;
                ProcessBuilder processBuilder = new ProcessBuilder(
                        new ArrayList<>(Collections.singletonList(file.getAbsolutePath())));
                Map<String, String> environment = processBuilder.environment();
                environment.put("LD_LIBRARY_PATH", libVendorPath);
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                result = process.waitFor() == 0;
            } catch (Exception ignored) {
            }
            return result;
        }
    }

    final public void processInfo(String info) {
        final Process process = (sd_thread == null) ? null : sd_thread.process;
        if (process != null)
            runOnUiThread(() -> Toast.makeText(myActivity, info + "\n" + process, Toast.LENGTH_SHORT).show());
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
                        selectSpinnerString(sampler_spinner, SAMPLER_LCM);
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
                // here follow some lazy checks for easy defaults - just for the moment
                String model = selectedModelfile.toUpperCase();
                boolean isSDXS = model.contains("SDXS");
                boolean isSTAR = model.contains("STAR");
                boolean isNITRO = model.contains("NITRO");
                if (isSDXS || isSTAR || isNITRO) {
                    stepsEditor.setText("1");
                    cfgscaleEditor.setText("1");
                    selectSpinnerString(scheduler_spinner,
                            isSDXS ? SCHEDULER_DISCRETE : SCHEDULER_SGM_UNIFORM);
                }
                if (isSDXS) {
                    taesdXLchecker.setChecked(false);  // off state doesn't affect others
                    taesdchecker.setChecked(false);
                } else if (isSTAR || isNITRO
                            || model.contains("XL")
                            || model.contains("SSD")
                            || model.contains("VEGA")) {
                    if (!taesdXLModel.isEmpty()) {
                        taesdXLchecker.setChecked(true);
                    }
                }
            } else if (parent.getId() == R.id.sampler_spinner) {
                selectedSampler = sampler_spinner.getSelectedItem().toString();
                if (selectedSampler.contains("lcm")) {
                    stepsEditor.setText("4");
                    cfgscaleEditor.setText("1");
                }
            } else if (parent.getId() == R.id.scheduler_spinner) {
                selectedScheduler = scheduler_spinner.getSelectedItem().toString();
            } else if (parent.getId() == R.id.ext_spinner) {
                selectedExt = ext_spinner.getSelectedItem().toString();
            } else if (parent.getId() == R.id.quant_spinner) {
                selectedQuant = quant_spinner.getSelectedItem().toString();
            }
            String key = spinnersMap.get(parent.getId());
            if (key != null) {
                vm.setPrefString(key, parent.getSelectedItem().toString());  // spinner item cannot be null when selected
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    final private ItemSelectedListener spinnerListener = new ItemSelectedListener();

    final private static Map<Integer, String> spinnersMap = new HashMap<>();

    static {
        spinnersMap.put(R.id.model_spinner, SDViewModel.prefsKeyModel);
        spinnersMap.put(R.id.sampler_spinner, SDViewModel.prefsKeySampler);
        spinnersMap.put(R.id.scheduler_spinner, SDViewModel.prefsKeyScheduler);
        spinnersMap.put(R.id.ext_spinner, SDViewModel.prefsKeyExt);
        spinnersMap.put(R.id.quant_spinner, SDViewModel.prefsKeyQuant);
    }

    private static void selectSpinnerString(@NonNull Spinner spinner, @NonNull String val) {
        // selects position 0 when string is absent
        // cast to ArrayAdapter<String> is safe for SpinnerAdapter with strings
        //noinspection unchecked
        spinner.setSelection(Math.max(0, ((ArrayAdapter<String>) spinner.getAdapter()).getPosition(val)));
    }

    private void setupSpinner(@NonNull Spinner spinner) {
        String key = spinnersMap.get(spinner.getId());
        if (key == null) {
            // id-key pair must be defined in spinnersMap
            Toast.makeText(myActivity, "No key for " + getResources().getResourceEntryName(spinner.getId()), Toast.LENGTH_LONG).show();
            return;
        }
        Object item = spinner.getSelectedItem();
        String to = item == null ? "" : item.toString();
        String tn = vm.getPrefString(key, to);
        if (!tn.equals(to))
            selectSpinnerString(spinner, tn);
        spinner.setOnItemSelectedListener(spinnerListener);
    }

    final private static Map<Integer, String> checkboxesMap = new HashMap<>();

    static {
        checkboxesMap.put(R.id.usegpu, SDViewModel.prefsKeyGpu);
        checkboxesMap.put(R.id.embeddchecker, SDViewModel.prefsKeyEmbed);
        checkboxesMap.put(R.id.taesdchecker, SDViewModel.prefsKeyTaesd);
        checkboxesMap.put(R.id.taesdXLchecker, SDViewModel.prefsKeyTaesdXL);
        checkboxesMap.put(R.id.writecmdchecker, SDViewModel.prefsKeyCmd);
        checkboxesMap.put(R.id.writelogchecker, SDViewModel.prefsKeyLog);
        checkboxesMap.put(R.id.vaetiling, SDViewModel.prefsKeyVaeTiling);
        checkboxesMap.put(R.id.convdirect, SDViewModel.prefsKeyConvDirect);
    }

    private class DefCheckListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            String key = checkboxesMap.get(compoundButton.getId());
            if (key != null)  // known CheckBox
                vm.setPrefBoolean(key, checked);
        }
    }

    final private DefCheckListener onCheckedChangeListener = new DefCheckListener();

    final private class TaesdCheckListener extends DefCheckListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            if (checked)
                (compoundButton.getId() == R.id.taesdXLchecker ? taesdchecker : taesdXLchecker).setChecked(false);
            super.onCheckedChanged(compoundButton, checked);
        }
    }

    final private TaesdCheckListener taesdCheckListener = new TaesdCheckListener();

    final private class GpuCheckListener extends DefCheckListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            vaetilingchecker.setChecked(!checked);  // currently not for openCL
            convdirectchecker.setChecked(checked);
            String model = selectedModelfile.toUpperCase();
            if (gpuchecker.isChecked()) {
                // these settings seem to work quite well at openCL/ADRENO 810
                // "f16" is optimal for huge VRAM or very small models like SDXS
                selectSpinnerString(quant_spinner,
                        model.contains("SDXS") ? "f16" : "q4_0");
            } else {  // cpu
                // for some budget devices with low RAM and NO usable openCL drivers:
                if (model.contains("SSD")) {
                    selectSpinnerString(quant_spinner, "q8_0");
                } else if (model.contains("XL")) {
                    if (!model.contains("GGUF")) {
                        selectSpinnerString(quant_spinner, "q8_0");
                    } // else keep as GGUF is
                } else if (model.contains("NITROSD-REAL") ||
                        model.contains("DIFF-INSTRUCTSTAR")) {
                    selectSpinnerString(quant_spinner, "q4_0");
                }
            }
            super.onCheckedChanged(compoundButton, checked);  // save state in preferences
        }
    }

    private void setupCheckBox(CheckBox checkBox, boolean initValue, CompoundButton.OnCheckedChangeListener listener) {
        String key = checkboxesMap.get(checkBox.getId());
        if (key == null) {
            // id-key pair must be defined in checkboxesMap
            Toast.makeText(myActivity, "No key for " + getResources().getResourceEntryName(checkBox.getId()), Toast.LENGTH_LONG).show();
            return;
        }
        boolean current = checkBox.isChecked();
        boolean needed = vm.prefKeyExist(key) ? vm.getPrefBoolean(key, current) : initValue;
        if (current != needed)
            checkBox.setChecked(needed);
        // setting the listener after the state change,
        // to not override user settings during setup
        checkBox.setOnCheckedChangeListener(listener);
    }

    final private static Map<Integer, String> editsMap = new HashMap<>();

    static {
        editsMap.put(R.id.stringprompt, SDViewModel.prefsKeyPrompt);
        editsMap.put(R.id.stringnegprompt, SDViewModel.prefsKeyNegPrompt);
        editsMap.put(R.id.stringopt, SDViewModel.prefsKeyOptions);
        editsMap.put(R.id.stringsteps, SDViewModel.prefsKeySteps);
        editsMap.put(R.id.stringseed, SDViewModel.prefsKeySeed);
        editsMap.put(R.id.stringwidth, SDViewModel.prefsKeyWidth);
        editsMap.put(R.id.stringheight, SDViewModel.prefsKeyHeight);
        editsMap.put(R.id.stringcfgscale, SDViewModel.prefsKeyCfgScale);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //processInfo("onPause");

        // saving content with editFocusListener
        View v = getCurrentFocus();
        if (v != null && editsMap.get(v.getId()) != null)  // known EditText
            v.clearFocus();
        vm.savePreferences();
    }

    private void setupEditText(EditText editText) {
        String key = editsMap.get(editText.getId());
        if (key == null) {
            // id-key pair must be defined in editsMap
            Toast.makeText(myActivity, "No key for " + getResources().getResourceEntryName(editText.getId()), Toast.LENGTH_LONG).show();
            return;
        }
        String to = editText.getText().toString();
        String tn = vm.getPrefString(key, to);
        if (!tn.equals(to))
            editText.setText(tn);
        editText.setOnFocusChangeListener(editFocusListener);
    }

    final private View.OnFocusChangeListener editFocusListener = (v, hasFocus) -> {
        if (!hasFocus) {
            String key = editsMap.get(v.getId());
            if (key != null)  // known EditText
                vm.setPrefString(key, ((EditText) v).getText().toString());
        }
    };
}
