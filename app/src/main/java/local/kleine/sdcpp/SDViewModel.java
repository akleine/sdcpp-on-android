package local.kleine.sdcpp;

import android.graphics.Bitmap;
import androidx.lifecycle.ViewModel;

public class SDViewModel extends ViewModel {
    private Bitmap _image = null;
    public Bitmap getBitmap() {
        if (_image != null && !_image.isRecycled()) {
            return _image;
        } else {
            return null;
        }
    }
    public void setBitmap(Bitmap b) {
        if (_image != null && !_image.isRecycled()) _image.recycle();
        _image = b;
    }
    @Override
    protected void onCleared() {
        super.onCleared();
        // explicitly using recycle() to avoid delays of garbage collector
        if (_image != null) {
            if (!_image.isRecycled()) _image.recycle();
            _image = null;
        }
    }
}
