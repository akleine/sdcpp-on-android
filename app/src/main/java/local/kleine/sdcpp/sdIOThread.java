package local.kleine.sdcpp;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class sdIOThread extends Thread implements Runnable {
    public volatile Process process = null;
    public volatile boolean isRunning = false;
    final private static Object process_lock = new Object();
    private volatile SDActivity myActivity;
    private boolean wasNotInterrupted = false;

    // helpers for message log
    private String lastMsg = "";
    private boolean lastProgressBar = false;

    final public String outputImagePath;

    final public ArrayList<String> outputArrayList = new ArrayList<>();

    sdIOThread(SDActivity parent, List<String> arguments, String sdWorkPath, String sdLibraryPath, String outputImage ) {
        myActivity = parent;
        outputImagePath = outputImage;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(arguments);
            if (!sdLibraryPath.isEmpty()) {
                Map<String, String> environment = processBuilder.environment();
                environment.put("LD_LIBRARY_PATH", sdLibraryPath);
            }
            processBuilder.directory(new File(sdWorkPath));
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            myActivity.processInfo("sd.cpp started");
        } catch (Exception e) {
            processDestroy();
            String msg = myActivity.getString(R.string.can_not_run_sd_process) + e.getMessage();
            myActivity.addScreenDimmer();
            myActivity.subFinished(0, msg);
            debugMsg(msg);
            myActivity.restoreScreenBrightness();
        }
    }

    public void updateActivity(SDActivity activity) {
        myActivity = activity;
    }

    public void processDestroy() {
        synchronized (process_lock) {
            if (process != null) {
                try {
                    process.destroy();
                    if (!wasNotInterrupted) myActivity.processInfo("sd.cpp aborted");
                } catch (Exception ignored) {
                } finally {
                    process = null;
                }
            }
        }
    }

    private boolean updateLog(final String msg) {
        final String clearToEOL = "\u001B[K";
        String text = msg;
        if (text.contains(clearToEOL)) {
            text = text.replace(clearToEOL, "");
        }
        if (lastMsg.equals(text)) {
            return false;  // nothing changed
        }
        boolean isProgressBar = text.startsWith("  |");
        if (!isProgressBar || !lastProgressBar) {
            outputArrayList.add(text);  // new line or start of bar
        } else {
            outputArrayList.set(outputArrayList.size() - 1, text);  // update the bae in same line
        }
        lastProgressBar = isProgressBar;
        lastMsg = text;
        return true;
    }

    private void debugMsg(final String line) {
        if (updateLog(line)) {
            final SDActivity a = myActivity;
            if (a != null && !a.isFinishing() && !a.isDestroyed()) {
                a.updateLogView();
            }
        }
    }

    @Override
    public void run() {
        if (process != null) {
            isRunning = true;
            myActivity.addScreenDimmer();
            myActivity.lockScreenOn();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                // synchronization is needless because i/o exception
                // will be handled as cancellation (process == null)
                while (process != null && (line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        debugMsg(line);
                    }
                }
                if (process != null) {
                    myActivity.subFinished(process.waitFor(), "");
                    wasNotInterrupted = true;
                } else {
                    myActivity.subFinished(0, "cancelled");
                    // close button calls finish() too early
                    // and toast doesn't have time to display
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                }
            } catch (java.io.IOException e) {
                myActivity.subFinished(0, "cancelled");
                try { Thread.sleep(500); } catch (Exception ignored) {}
            } catch (Exception e) {
                myActivity.subFinished(0, "exception during SD execution: " + e.getMessage());
            } finally {
                // zero process to indicate that it was stopped / finished
                processDestroy();
                myActivity.restoreScreenBrightness();
            }
            // allowing SDActivity to finish only after
            // all toasts were put into UI message queue
            isRunning = false;
        }
    }
}

