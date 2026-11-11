package local.kleine.sdcpp;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import androidx.preference.PreferenceManager;
import androidx.lifecycle.AndroidViewModel;

public class SDViewModel extends AndroidViewModel {
    // EditText
    public static final String prefsKeyPrompt    = "prefs_prompt";
    public static final String prefsKeyNegPrompt = "prefs_neg_prompt";
    public static final String prefsKeyOptions   = "prefs_options";
    public static final String prefsKeySteps     = "prefs_steps";
    public static final String prefsKeySeed      = "prefs_seed";
    public static final String prefsKeyWidth     = "prefs_width";
    public static final String prefsKeyHeight    = "prefs_height";
    public static final String prefsKeyCfgScale  = "prefs_cfgscale";
    // CheckBox
    public static final String prefsKeyGpu     = "prefs_gpu";
    public static final String prefsKeyEmbed   = "prefs_embeddings";
    public static final String prefsKeyTaesd   = "prefs_taesd";
    public static final String prefsKeyTaesdXL = "prefs_taesdxl";
    public static final String prefsKeyCmd     = "prefs_write_cmd";
    public static final String prefsKeyLog     = "prefs_write_log";
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor prefsEditor;
    private boolean prefsChanged = false;
    private Bitmap _image = null;

    public SDViewModel (Application app) {
        super(app);
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        prefsEditor = prefs.edit();
    }

    public Bitmap getBitmap() {
        return (_image == null || _image.isRecycled()) ? null : _image;
    }
    public void setBitmap(Bitmap b) {
        if (_image != null && !_image.isRecycled())
            _image.recycle();
        _image = (b == null || b.isRecycled()) ? null : b;
    }
    @Override
    protected void onCleared() {
        super.onCleared();
        // explicitly using recycle() to avoid delays of garbage collector
        if (_image != null) {
            if (!_image.isRecycled())
                _image.recycle();
            _image = null;
        }
        savePreferences();
    }

    public void savePreferences () {
        if (prefsChanged && prefsEditor != null && prefsEditor.commit())
            prefsChanged = false;
    }

    public boolean prefKeyExist(String prefName) {
        return prefs.contains(prefName);
    }

    public String getPrefString(String prefName, String defValue) {
        String value;
        try {
            value = prefs.getString(prefName, defValue);
        } catch (Exception ignored) {
            // deleting erroneous element and returning default
            // as if it doesn't exist
            prefsEditor.remove(prefName);
            prefsChanged = true;
            value = defValue;
        }
        return value;
    }

    public void setPrefString(String prefName, String value) {
        boolean needWrite = !prefs.contains(prefName);  // force write 1st time
        if (!needWrite) {
            try {
                needWrite = !prefs.getString(prefName, "").equals(value);
            } catch (Exception ignored) {
                // something was wrong, clearing old value
                // and resetting it from scratch
                prefsEditor.remove(prefName);
                needWrite = true;
            }
        }
        if (needWrite) {
            prefsEditor.putString(prefName, value);
            prefsChanged = true;
        }
    }

    public boolean getPrefBoolean(String prefName, boolean defValue) {
        boolean value;
        try {
            value = prefs.getBoolean(prefName, defValue);
        } catch (Exception ignored) {
            prefsEditor.remove(prefName);
            prefsChanged = true;
            value = defValue;
        }
        return value;
    }

    public void setPrefBoolean(String prefName, boolean value) {
        boolean needWrite = !prefs.contains(prefName);  // force write 1st time
        if (!needWrite) {
            try {
                needWrite = prefs.getBoolean(prefName, false) != value;
            } catch (Exception ignored) {
                prefsEditor.remove(prefName);
                needWrite = true;
            }
        }
        if (needWrite) {
            prefsEditor.putBoolean(prefName, value);
            prefsChanged = true;
        }
    }

}
