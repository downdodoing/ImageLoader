package com.example.mvp.myimageloader.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by MVP on 2017/7/28.
 *
 * 图片压缩功能实现
 */

public class ImageResizer {
    public static final String TAG = "ImageResizer";

    /**
     * 从资源文件中加载Bitmap
     *
     * @param res       表示系统资源
     * @param resId     资源ID
     * @param reqWidth  展示时的宽度
     * @param reqHeight 展示时的高度
     * @return
     */
    public Bitmap decodeSampleBitmapFromResoureces(Resources res, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        //设置为true则，BitmapFactory则只会解析图片的宽/高信息，不会真正的去加载图片
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        //计算出图片的缩放比例
        options.inSampleSize = calculateSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public Bitmap decodeSampleBitmapFromFileDescriptor(FileDescriptor fd, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        options.inSampleSize = calculateSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    //计算出图片的缩放比例
    public int calculateSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (reqWidth == 0 || reqHeight == 0) {
            return 1;
        }
        int width = options.outWidth;
        int height = options.outHeight;
        //官方文档指出sampleSize的大小应该总是2的指数，如果不是则会向下进行取整为最接近2的指数代替
        int sampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            //为了使图片缩放后不失真，缩放后的大小需要大于等于需要展示的大小，以免被拉伸
            int halfWidth = width / 2;
            int halfHeight = height / 2;

            if (halfHeight / sampleSize > reqHeight && (halfWidth / sampleSize > reqWidth)) {
                sampleSize *= 2;
            }
        }
        return sampleSize;
    }
}
