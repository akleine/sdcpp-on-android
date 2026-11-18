package local.kleine.sdcpp;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SDUIoptionsLoader implements View.OnClickListener {
    final private static int MAXSCRIPTFILESIZE = 2048;
    private SDActivity sda;
    private File currentDirectory;
    private AlertDialog currentDialog;
    private String usedfileextensions;

    final private static Map<String, Integer> optionsUIIdMap = new HashMap<>();

    static {
        //-- real options of sd.cpp --
        optionsUIIdMap.put("-p", R.id.stringprompt);
        optionsUIIdMap.put("-n", R.id.stringnegprompt);
        optionsUIIdMap.put("-m", R.id.model_spinner);
        optionsUIIdMap.put("--steps", R.id.stringsteps);
        optionsUIIdMap.put("--seed", R.id.stringseed);
        optionsUIIdMap.put("--cfg-scale", R.id.stringcfgscale);
        optionsUIIdMap.put("--width", R.id.stringwidth);
        optionsUIIdMap.put("--height", R.id.stringheight);
        optionsUIIdMap.put("--sampling-method", R.id.sampler_spinner);
        optionsUIIdMap.put("--scheduler", R.id.scheduler_spinner);
        //-- pseudo options for setting UI elements --
        optionsUIIdMap.put("--use-taesd", R.id.taesdchecker);
        optionsUIIdMap.put("--no-taesd", R.id.taesdchecker); // "--no-" == inversed
        optionsUIIdMap.put("--use-taesdXL", R.id.taesdXLchecker);
        optionsUIIdMap.put("--no-taesdXL", R.id.taesdXLchecker);
        optionsUIIdMap.put("--vae-tiling", R.id.vaetiling);
        optionsUIIdMap.put("--no-vae-tiling", R.id.vaetiling);
        optionsUIIdMap.put("--diffusion-conv-direct", R.id.convdirect);
        optionsUIIdMap.put("--no-diffusion-conv-direct", R.id.convdirect);
        optionsUIIdMap.put("--use-embeddings", R.id.embeddchecker);
        optionsUIIdMap.put("--no-embeddings", R.id.embeddchecker);
        optionsUIIdMap.put("--extra-options", R.id.stringopt);
        // different option but selection is made via the same spinner:
        optionsUIIdMap.put("--diffusion-model", R.id.model_spinner);
        //-- usual options for compatibilty with "foreign" scripts --
        optionsUIIdMap.put("--prompt", R.id.stringprompt);
        optionsUIIdMap.put("--negative-prompt", R.id.stringnegprompt);
        optionsUIIdMap.put("-W", R.id.stringwidth);
        optionsUIIdMap.put("-H", R.id.stringheight);
    }

    @Override
    public void onClick(View view) {
        sda = (SDActivity) view.getContext();
        final String[] extensionsArr = sda.getResources().getStringArray(R.array.image_extension_entries);
        usedfileextensions = String.join("|", extensionsArr);
        recursiveFolderBrowser(new File(sda.sdWorkPath));
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|([^\\s\\\\]+)").matcher(input);
        while (m.find()) {
            tokens.add(m.group(1) != null ? m.group(1) : m.group(2));
            // group 1: "words inside quotation marks"  group 2: all_others
        }
        return tokens;
    }

    private void parseShellScript(String input) {
        View v;
        List<String> tokens = tokenize(input);
        for (int i = 0; i < tokens.size(); i++) {
            String option = tokens.get(i);
            if (optionsUIIdMap.containsKey(option)) {
                v = sda.findViewById(optionsUIIdMap.get(option));
                if (v instanceof EditText) {
                    ((EditText) v).setText(tokens.get(++i));
                } else {
                    if (v instanceof Spinner) {
                        String optionValue = tokens.get(++i);
                        if (v.equals(sda.findViewById(R.id.model_spinner))) {
                            // entries of this spinner are shortened
                            optionValue = optionValue.substring(sda.sdWorkPath.length() + 1);
                        }
                        //noinspection unchecked
                        ArrayAdapter<String> adapter = (ArrayAdapter<String>) ((Spinner) v).getAdapter();
                        if (adapter.getPosition(optionValue) >= 0) {
                            ((Spinner) v).setSelection(adapter.getPosition(optionValue));
                        }
                    } else {
                        if (v instanceof CheckBox) {
                            ((CheckBox) v).setChecked((!option.contains("--no-")));
                        }
                    }
                }
            }
        }
    }

    private void recursiveFolderBrowser(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        FileFilter navigationFilter = file -> {
            if (file.isDirectory()) {
                return true;
            }
            return (file.length() < MAXSCRIPTFILESIZE &&
                    file.getName().matches("(?i)^.*\\.(" + usedfileextensions + ")\\.sh$"));
        };
        File[] items = directory.listFiles(navigationFilter);
        if (items == null) items = new File[0];
        Arrays.sort(items, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });
        List<String> displayNames = new ArrayList<>();
        List<File> fileObjects = new ArrayList<>();
        if (!directory.getAbsolutePath().equals(sda.sdWorkPath)) {
            displayNames.add("📁 .. ");
            fileObjects.add(directory.getParentFile());
        }
        for (File item : items) {
            if (item.isDirectory()) {
                displayNames.add("📁 " + item.getName() + "/");
            } else {
                displayNames.add("📄 " + item.getName());
            }
            fileObjects.add(item);
        }
        CharSequence[] itemsArray = displayNames.toArray(new CharSequence[0]);
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(sda);
        builder.setTitle(directory.getName());
        builder.setItems(itemsArray, (dialog, which) -> {
            File selectedItem = fileObjects.get(which);
            if (!directory.getAbsolutePath().equals(sda.sdWorkPath) && which == 0) {
                currentDirectory = selectedItem;
                recursiveFolderBrowser(currentDirectory);
                return;
            }
            if (selectedItem.isDirectory()) {
                currentDirectory = selectedItem;
                recursiveFolderBrowser(currentDirectory);
            } else {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(selectedItem));
                    String cmdline = reader.lines().collect(Collectors.joining());
                    parseShellScript(cmdline);
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        });
        builder.setNegativeButton(R.string.close_dialog, (dialog, which) -> currentDirectory = new File(sda.sdWorkPath));
        currentDialog = builder.create();
        currentDialog.show();
    }
}
