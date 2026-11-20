package local.kleine.sdcpp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileSelector {
    public interface actionForFileSelector {
        void actOn(File selectedItem);
    }

    private AlertDialog currentDialog;
    private File currentDirectory;
    final private SDActivity sda;
    final int maxsize;
    final String fileextensions;
    final String endings;

    public FileSelector(SDActivity sda, int maxsize, String fileextensions, String endings) {
        this.sda = sda;
        this.maxsize = maxsize;
        this.fileextensions = fileextensions;
        this.endings = endings;
    }

    public void recursiveFolderBrowser(@NonNull File directory, actionForFileSelector action) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        FileFilter navigationFilter = file -> {
            if (file.isDirectory()) {
                return true;
            }
            return ((maxsize == -1 || file.length() < maxsize) &&
                    file.getName().matches("(?i)^.*(" + fileextensions + ")" + endings));
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
                recursiveFolderBrowser(currentDirectory, action);
                return;
            }
            if (selectedItem.isDirectory()) {
                currentDirectory = selectedItem;
                recursiveFolderBrowser(currentDirectory, action);
            } else {
                action.actOn(selectedItem);
            }
        });
        builder.setNegativeButton(R.string.close_dialog, (dialog, which) -> currentDirectory = new File(sda.sdWorkPath));
        currentDialog = builder.create();
        currentDialog.show();
    }
}
