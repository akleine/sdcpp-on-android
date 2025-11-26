package local.kleine.sdcpp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Activity myActivity;
    private Process process;
    private static final String sdFileName = "libsd.so"; // "sd" executable needs renaming because it is now located inside jniLibs
    private String sdProgramPath, outputImagePath, selectedModelfile, selectedSampler, taesdModel, loraPath;
    private final String sdWorkPath = android.os.Environment.
            getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).
            getAbsolutePath();
    private EditText promptEditor, negativeEditor, seedEditor, stepsEditor,
            widthEditor, heightEditor, cfgscaleEditor, threadsEditor;
    private CheckBox taesdchecker;
    private TextView loraPathView;
    private ListView sdLogView;
    private ImageView imageOutputView;
    private ArrayList<String> outputArrayList;
    private ArrayAdapter<String> arrayAdapter;
    private boolean lastProgressBar = true;               // helper for message log
    private final String[] samplerArr = {"euler", "euler_a", "heun", "dpm2", "dpm++2s_a", "dpm++2m",
            "dpm++2mv2", "ipndm", "ipndm_v", "lcm", "ddim_trailing", "tcd"};
    private List<String> fileList, samplerList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myActivity = this;
        if (!Environment.isExternalStorageManager()) {
            setContentView(R.layout.activity_permissions);
            Button requestPermissionButton = findViewById(R.id.requestPermissionButton);
            requestPermissionButton.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", this.getPackageName(), null);
                    intent.setData(uri);
                    activityResultLaunch.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Error requesting permission" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            runSDcpp();
        }
    }

    final ActivityResultLauncher<Intent> activityResultLaunch = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Environment.isExternalStorageManager()) {
                    runSDcpp();
                }
            });

    public void runSDcpp() {
        androidx.appcompat.app.ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.hide();                                             // request all visible space available
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); // needed for sd log output
        setContentView(R.layout.activity_main);
        File file = new File(this.getApplicationInfo().nativeLibraryDir, sdFileName);
        if (!(file.exists() && file.length() > 0)) {
            Toast.makeText(this, "sd executable not found,\nplease rebuild this app", Toast.LENGTH_LONG).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
        sdProgramPath = file.getAbsolutePath();
        sdLogView = findViewById(R.id.sdLogView);
        outputArrayList = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(this, R.layout.custom_list_item, R.id.output_item_line, outputArrayList);
        sdLogView.setAdapter(arrayAdapter);
        imageOutputView = findViewById(R.id.outputImageView);
        promptEditor = findViewById(R.id.stringprompt);
        negativeEditor = findViewById(R.id.stringnegprompt);
        stepsEditor = findViewById(R.id.stringsteps);
        seedEditor = findViewById(R.id.stringseed);
        widthEditor = findViewById(R.id.stringwidth);
        heightEditor = findViewById(R.id.stringheight);
        seedEditor = findViewById(R.id.stringseed);
        threadsEditor = findViewById(R.id.stringthreads);
        cfgscaleEditor = findViewById(R.id.stringcfgscale);
        Button submitButton = findViewById(R.id.submitButton);
        fileList = listExternalFiles(sdWorkPath);
        if (fileList.isEmpty()) {
            submitButton.setEnabled(false);
            fileList.add("At first copy SD model file to this device.");
        }
        Spinner spinner1 = findViewById(R.id.spinner1);
        spinner1.setOnItemSelectedListener(new ItemSelectedListener());
        ArrayAdapter<String> adapter1 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileList);
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner1.setAdapter(adapter1);

        Spinner spinner2 = findViewById(R.id.spinner2);
        spinner2.setOnItemSelectedListener(new ItemSelectedListener());
        samplerList = Arrays.asList(samplerArr);
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, samplerList);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner2.setAdapter(adapter2);

        TextView taesdview = findViewById(R.id.taesdmodel);
        taesdview.setText(taesdModel);
        taesdchecker = findViewById(R.id.taesdchecker);
        taesdchecker.setEnabled(!taesdModel.isEmpty());
        taesdchecker.setChecked(!taesdModel.isEmpty());
        loraPathView = findViewById(R.id.lorapath);
        loraPathView.setText(loraPath);
        submitButton.setOnClickListener(v ->
        {
            View wrapperLinearLayout = findViewById(R.id.wrapperLinearLayout);
            wrapperLinearLayout.setVisibility(View.GONE);
            String prompt = promptEditor.getText().toString();
            if (prompt.isEmpty()) {
                prompt = "something";
            }
            String negative = negativeEditor.getText().toString();
            outputImagePath = sdWorkPath + "/output" + System.currentTimeMillis() / 1000L + ".png";
            String[] arguments = new String[]{sdProgramPath,
                    "-m", selectedModelfile,
                    "-n", negative,
                    "-p", prompt,
                    "-v",
                    "-o", outputImagePath,
                    "--lora-model-dir", loraPath,
                    "--sampling-method", selectedSampler,
                    "--taesd", taesdchecker.isChecked() ? taesdModel : "",
                    "--threads", check(threadsEditor.getText().toString(), "-1"),
                    "--cfg-scale", check(cfgscaleEditor.getText().toString(), "7.0"),
                    "--seed", check(seedEditor.getText().toString(), "-1"),
                    "--steps", checkSteps(stepsEditor.getText().toString(), "25"),
                    "--width", checkDimension(widthEditor.getText().toString(), "512"),
                    "--height", checkDimension(heightEditor.getText().toString(), "512"),
                    // "--scheduler", "discrete" "karras",
            };
            new sdIOThread((MainActivity) myActivity, arguments, sdWorkPath).start();
        });
        Button closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            setResult(Activity.RESULT_OK);
            sdIOThread.processDestroy();
            finish();
        });
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

    public void subFinished(int exitcode) {
        runOnUiThread(() -> {
            File file = new File(outputImagePath);
            if (exitcode == 0 && file.exists()) {
                imageOutputView.setVisibility(View.VISIBLE);
                displayResultImageFile(file);
            } else {
                String errMsg;
                if (exitcode == 0 && !file.exists()) {
                    errMsg = "Result image not found";
                } else {
                    if (exitcode == 998) {
                        errMsg = "can not run SD process";
                    } else {
                        if (exitcode == 999)
                            errMsg = "Exception during SD execution";
                        else
                            errMsg = "SD process failed with code: " + exitcode;
                    }
                }
                Toast.makeText(myActivity, errMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void debugMsg(final String msg) {
        runOnUiThread(() -> {
            boolean isProgressBar = msg.startsWith("  |");
            final String clearToEOL = "\u001B[K";
            String text = msg + "\n";
            if (lastProgressBar && !isProgressBar) {
                outputArrayList.add("");                          // at log begin and at progress bar end
            }
            if (text.contains(clearToEOL)) {
                text = text.replace(clearToEOL, "");
            }
            int last = outputArrayList.size() - 1;
            outputArrayList.set(last, text);
            lastProgressBar = isProgressBar;
            if (!isProgressBar && msg.length() > 1) {
                outputArrayList.add("");                          // new line if no progress bar
            }
            arrayAdapter.notifyDataSetChanged();
            sdLogView.setSelection(last);
        });
    }

    private String checkDimension(String input, @SuppressWarnings("SameParameterValue") String def) {
        input = input.replaceAll("\\s", "");
        if (!input.isEmpty()) {
            try {
                int number = Integer.parseInt(input);
                int rounded = (number + 63) / 64 * 64;      // round up for 64
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
        List<String> fileList = new ArrayList<>();
        File storageRoot = new File(storagePath);
        if (storageRoot.exists() && storageRoot.canRead()) {
            listFilesRecursively(storageRoot, fileList, 1);
        }
        return fileList;
    }

    private void listFilesRecursively(File directory, List<String> fileList, int depth) {
        if (depth > 3) return;                              // limit recursion depth
        try {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        listFilesRecursively(file, fileList, depth + 1);
                    } else {
                        String fileName = file.getName();
                        if (fileName.contains(".ckpt") || fileName.contains(".safetensors")) {
                            if (fileName.contains("taesd")) {
                                taesdModel = file.getAbsolutePath();
                            } else if (fileName.contains("lora")) {
                                loraPath = file.getParent();
                            } else {

                                if (file.length() > (500 * 1024 * 1024)) {
                                    fileList.add(file.getAbsolutePath());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (process != null) {
            if (process.isAlive()) {
                processInfo(null, "onDestroy\naborted sd.cpp");
                process.destroy();
            }
        }
    }

    void processInfo(Process pr, String info) {
        if (pr != null) {
            process = pr;
        }
        if (process != null) {
            Toast.makeText(myActivity, info + "\n" + process, Toast.LENGTH_SHORT).show();
        }
    }

    public class ItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (parent.getId() == R.id.spinner1) {
                selectedModelfile = fileList.get(position);
            } else {
                selectedSampler = samplerList.get(position);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
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
