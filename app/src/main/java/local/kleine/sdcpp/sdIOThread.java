package local.kleine.sdcpp;

import android.app.Activity;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

class sdIOThread extends Thread implements Runnable {
    private static Process process;
    private final MainActivity myActivity;

    sdIOThread(MainActivity parent, String[] arguments, String sdWorkPath) {
        myActivity = parent;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(arguments);
            processBuilder.directory(new File(sdWorkPath));
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            myActivity.processInfo(process, "sd.cpp started");
        } catch (Exception e) {
            myActivity.subFinished(998);
            myActivity.setResult(Activity.RESULT_CANCELED);
            myActivity.finishAndRemoveTask();
        }
    }

    protected static void processDestroy() {
        if (process != null) {
            process.destroy();                                      // avoid resource leaks
        }
    }

    @Override
    public void run() {
        try (InputStream inputStream = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String outputLine = line;
                myActivity.runOnUiThread(() -> myActivity.debugMsg(outputLine));
            }
            int exitCode = process.waitFor();
            myActivity.runOnUiThread(() -> myActivity.subFinished(exitCode));
        } catch (Exception e) {
            myActivity.runOnUiThread(() -> myActivity.subFinished(999));
        } finally {
            processDestroy();
        }
    }
}

