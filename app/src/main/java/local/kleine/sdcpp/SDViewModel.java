package local.kleine.sdcpp;

import android.graphics.Bitmap;
import androidx.lifecycle.ViewModel;

public class SDViewModel extends ViewModel {
    private Bitmap _image = null;
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
    }
}
