package local.kleine.sdcpp;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

class sdIOThread extends Thread implements Runnable {
    public static volatile Process process = null;
    private volatile SDActivity myActivity;

    sdIOThread(SDActivity parent, List<String> arguments, String sdWorkPath, String sdLibraryPath ) {
        myActivity = parent;
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
            myActivity.debugMsg(msg);
            myActivity.restoreScreenBrightness();
        }
    }

    public void updateActivity(SDActivity activity) {
        myActivity = activity;
    }

    public void processDestroy() {
        if (process != null) {
            try {
                process.destroy();
                myActivity.processInfo("sd.cpp aborted");
            } catch (Exception ignored) {
            } finally {
                process = null;
            }
        }
    }

    @Override
    public void run() {
        if (process != null) {
            myActivity.addScreenDimmer();
            myActivity.lockScreenOn();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while (process != null && (line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        myActivity.debugMsg(line);
                    }
                }
                if (process != null) {
                    myActivity.subFinished(process.waitFor(), "");
                } else {
                    myActivity.subFinished(0, "cancelled");
                }
            } catch (java.io.IOException e) {
                myActivity.subFinished(0, "cancelled");
            } catch (Exception e) {
                myActivity.subFinished(0, "exception during SD execution: " + e.getMessage());
            } finally {
                try {
                    sleep(250);                 // to not display "destroyed" twice
                } catch (Exception ignored) {}  // if process was already cancelled
                processDestroy();
                myActivity.restoreScreenBrightness();
            }
        }
    }
}

