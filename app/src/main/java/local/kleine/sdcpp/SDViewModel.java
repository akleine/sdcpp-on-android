package local.kleine.sdcpp;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.lifecycle.AndroidViewModel;

import java.util.HashMap;

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
    public static final String prefsKeyGpu         = "prefs_gpu";
    public static final String prefsKeyEmbed       = "prefs_embeddings";
    public static final String prefsKeyTaesd       = "prefs_taesd";
    public static final String prefsKeyTaesdXL     = "prefs_taesdxl";
    public static final String prefsKeyCmd         = "prefs_write_cmd";
    public static final String prefsKeyLog         = "prefs_write_log";
    public static final String prefsKeyVaeTiling   = "prefs_vae_tiling";
    public static final String prefsKeyConvDirect  = "prefs_conv_direct";

    // caches previous values, since SharedPreferences.Editor
    // does not have methods to get temporary values from its memory
    private final HashMap<String, String> prefCache = new HashMap<>();

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor prefsEditor;
    private boolean prefsChanged = false;
    private Bitmap _image = null;
    public volatile boolean make_open = false;  // detailed settings are hidden in layout by default

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

    public boolean prefKeyExist(@NonNull String prefName) {
        return prefs.contains(prefName);
    }

    public String getPrefString(@NonNull String prefName, String defValue) {
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

    public void setPrefString(@NonNull String prefName, final @NonNull String value) {
        boolean needWrite = false;
        String cached = prefCache.get(prefName);
        if (cached == null && prefs.contains(prefName)) {
            try {
                cached = prefs.getString(prefName, "");
            } catch (Exception ignored) {
                // something was wrong, clearing old value
                // and resetting it from scratch
                prefsEditor.remove(prefName);
                needWrite = true;
            }
        }
        needWrite |= !value.equals(cached);
        if (needWrite) {
            prefsEditor.putString(prefName, value);
            prefsChanged = true;
            prefCache.put(prefName, value);
        }
    }

    public boolean getPrefBoolean(@NonNull String prefName, boolean defValue) {
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

    public void setPrefBoolean(@NonNull String prefName, final boolean value) {
        boolean needWrite = false;
        String cached = prefCache.get(prefName);
        if (cached == null && prefs.contains(prefName)) {
            try {
                cached = Boolean.toString(prefs.getBoolean(prefName, false));
            } catch (Exception ignored) {
                prefsEditor.remove(prefName);
                needWrite = true;
            }
        }
        final String newvalue = Boolean.toString(value);
        needWrite |= !newvalue.equals(cached);
        if (needWrite) {
            prefsEditor.putBoolean(prefName, value);
            prefsChanged = true;
            prefCache.put(prefName, newvalue);
        }
    }

}
