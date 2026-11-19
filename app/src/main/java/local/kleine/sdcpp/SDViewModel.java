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
    public static final String prefst5xxlOptions   = "prefs_t5xxl";
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
    // Spinner
    public static final String prefsKeyModel     = "prefs_model";
    public static final String prefsKeySampler   = "prefs_sampler";
    public static final String prefsKeyScheduler = "prefs_scheduler";
    public static final String prefsKeyExt       = "prefs_ext";
    public static final String prefsKeyQuant       = "prefs_quant";
    /*
    ** caches previous values, since SharedPreferences.Editor
    ** does not have methods to get temporary values from its memory
    **
    ** single Map<String, String> is used for booleans as well
    ** to guaranty uniqueness of keys
    **/
    private final HashMap<String, String> prefCache = new HashMap<>();

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor prefsEditor;
    private boolean prefsChanged = false;
    private Bitmap cached_image = null;
    public volatile boolean make_open = false;  // detailed settings are hidden in layout by default

    public SDViewModel (Application app) {
        super(app);
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        prefsEditor = prefs.edit();
    }

    public Bitmap getBitmap() {
        return (cached_image == null || cached_image.isRecycled()) ? null : cached_image;
    }
    public void setBitmap(Bitmap b) {
        if (cached_image != null && !cached_image.isRecycled())
            cached_image.recycle();
        cached_image = (b == null || b.isRecycled()) ? null : b;
    }
    @Override
    protected void onCleared() {
        super.onCleared();
        // explicitly using recycle() to avoid delays of garbage collector
        if (cached_image != null) {
            if (!cached_image.isRecycled())
                cached_image.recycle();
            cached_image = null;
        }
        savePreferences();
    }

    /*
    ** commit() is used instead of apply() because savePreferences()
    ** is called from onPause() and delayed apply() might not write data
    ** before the app will be closed
    **/
    public void savePreferences () {
        if (prefsChanged && prefsEditor != null && prefsEditor.commit())
            prefsChanged = false;
    }

    public boolean prefKeyExist(final @NonNull String prefName) {
        return prefCache.containsKey(prefName) || prefs.contains(prefName);
    }

    @NonNull
    public String getPrefString(final @NonNull String prefName, final @NonNull String defValue) {
        String value = prefCache.get(prefName);
        if (value == null) {  // not cached yet
            try {
                value = prefs.getString(prefName, defValue);  // defValue if not exist
            } catch (Exception ignored) {
                // deleting erroneous element and returning default
                // as if it doesn't exist
                prefsEditor.remove(prefName);
                prefsChanged = true;
                // masking erroneous value by cache
                // to not reread it in every get() until it will be committed
                prefCache.put(prefName, defValue);
                value = defValue;
            }
        }
        // value is either prefCache(prefName), prefs(prefName) or defValue
        return value;
    }

    public void setPrefString(final @NonNull String prefName, final @NonNull String value) {
        boolean needWrite = false;
        String cached = prefCache.get(prefName);
        if (cached == null && prefs.contains(prefName)) {
            try {
                cached = prefs.getString(prefName, "");  // defValue is bypassed with contains()
            } catch (Exception ignored) {
                // something was wrong, clearing old value
                // and forcibly resetting it from scratch
                prefsEditor.remove(prefName);
                needWrite = true;
            }
        }
        // cached is either prefCache(prefName), prefs(prefName) or null if no valid value
        needWrite |= !value.equals(cached);
        if (needWrite) {
            prefsEditor.putString(prefName, value);
            prefsChanged = true;
            prefCache.put(prefName, value);
        }
    }

    final private static String stringTrue = Boolean.toString(true);

    public boolean getPrefBoolean(final @NonNull String prefName, final boolean defValue) {
        boolean value;
        final String cached = prefCache.get(prefName);
        if (cached != null) {
            value = stringTrue.equals(cached);
        } else if (prefs.contains(prefName)) {
            try {
                value = prefs.getBoolean(prefName, defValue);  // defValue if not exist
            } catch (Exception ignored) {
                // deleting erroneous element and returning default
                // as if it doesn't exist
                prefsEditor.remove(prefName);
                prefsChanged = true;
                // masking erroneous value by cache
                // to not reread it in every get() until it will be committed
                prefCache.put(prefName, Boolean.toString(defValue));
                value = defValue;
            }
        } else {
            value = defValue;  // no cache, no prefs
        }
        // value is either prefCache(prefName), prefs(prefName) or defValue
        return value;
    }

    public void setPrefBoolean(final @NonNull String prefName, final boolean value) {
        boolean needWrite = false;
        String cached = prefCache.get(prefName);
        if (cached == null && prefs.contains(prefName)) {
            try {
                cached = Boolean.toString(prefs.getBoolean(prefName, false));
            } catch (Exception ignored) {
                // same as in setPrefString()
                prefsEditor.remove(prefName);
                needWrite = true;
            }
        }
        // cached is prefCache, prefs or null (not exist or error)
        final String strValue = Boolean.toString(value);
        needWrite |= !strValue.equals(cached);
        if (needWrite) {
            prefsEditor.putBoolean(prefName, value);
            prefsChanged = true;
            prefCache.put(prefName, strValue);
        }
    }

}
