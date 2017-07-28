package com.example.mvp.myimageloader.util;

import android.graphics.Bitmap;
import android.widget.ImageView;

import java.io.Serializable;

/**
 * Created by MVP on 2017/7/28.
 */

public class LoadResult implements Serializable {
    public ImageView imageView;
    public String url;
    public Bitmap bitmap;

    public LoadResult(ImageView imageView, String url, Bitmap bitmap) {
        this.imageView = imageView;
        this.url = url;
        this.bitmap = bitmap;
    }
}
