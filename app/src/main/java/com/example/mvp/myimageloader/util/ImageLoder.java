package com.example.mvp.myimageloader.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.LruCache;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by MVP on 2017/7/28.
 */

public class ImageLoder {

    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLryCache;
    private Context mContext;
    //用于记录DiskLruCache的创建状态
    private boolean mDiskLruCacheCreated;

    public static final int DISK_CAHCE_SIZE = 1024 * 1024 * 50;//50M

    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //线程池中核心的线程数
    public static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    //最大线程数
    public static final int MAXINUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    //线程闲置超时时间
    public static final long KEEP_ALIVE = 10L;

    public static ImageLoder builder(Context context) {
        return new ImageLoder(context);
    }

    private ImageLoder(Context context) {
        mContext = context.getApplicationContext();
        //获取设备最大的运行时内存
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            //用于计算Bitmap的大小
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };

        File diskCacheDir = getDiskCacheDir(mContext, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        //缓存文件的大小大于需要被缓存的文件的大小
        if (diskCacheDir.length() > DISK_CAHCE_SIZE) {
            try {
                mDiskLryCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CAHCE_SIZE);
                mDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //添加图片到内存缓存
    private void addBitmapToMemeory(String key, Bitmap bitmap) {
        if (getBitmapFromMemeoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    //获取内存缓存中的图片
    private Bitmap getBitmapFromMemeoryCache(String key) {
        return mMemoryCache.get(key);
    }

    //从网络中获取图片
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("不能在UI线程中访问网络");
        }
        if (null == mDiskLryCache) {
            return null;
        }
        String key = UrlToKey.hashKeyFromUrl(url);
        //通过Editor来完成磁盘缓存的添加操作
        DiskLruCache.Editor editor = mDiskLryCache.edit(key);
        if (null != editor) {
            OutputStream out = editor.newOutputStream(0);
            if (DownloadUrlToNetwork.downloadUrlToStream(url, out)) {
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskLryCache.flush();
        }
        return loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    //直接从磁盘缓存中读取图片
    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("不能在UI线程中访问网络");
        }
        if (null == mDiskLryCache) {
            return null;
        }

        Bitmap bitmap = null;
        String key = UrlToKey.hashKeyFromUrl(url);
        DiskLruCache.Snapshot snapshot = mDiskLryCache.get(key);
        if (null != snapshot) {
            FileInputStream in = (FileInputStream) snapshot.getInputStream(0);
            FileDescriptor fileDescriptor = in.getFD();
            bitmap = ImageResizer.decodeSampleBitmapFromFileDescriptor(fileDescriptor, reqWidth, reqHeight);
            if (null != bitmap) {
                addBitmapToMemeory(key, bitmap);
            }
        }
        return bitmap;
    }

    private File getDiskCacheDir(Context mContext, String fileName) {

        boolean externaStorageAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        String dir = "";
        if (externaStorageAvailable) {
            dir = mContext.getExternalCacheDir().getPath();
        } else {
            dir = mContext.getCacheDir().getPath();
        }

        File file = new File(dir + File.separator + fileName);

        return file;
    }

    //同步加载
    public Bitmap loadBitmap(String url, int reqWidth, int reqHeight) {
        Bitmap bitmap = getBitmapFromMemeoryCache(url);
        if (null != bitmap) {
            return bitmap;
        }
        try {
            bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight);
            if (null != bitmap) {
                return bitmap;
            }
            bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (null == bitmap && !mDiskLruCacheCreated) {
            bitmap = DownloadUrlToNetwork.downloadBitmapFromUrl(url);
        }
        return bitmap;
    }

    //异步加载
    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(1, url);
        Bitmap bitmap = getBitmapFromMemeoryCache(url);
        if (null != bitmap) {
            imageView.setImageBitmap(bitmap);
            return;
        }
        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, reqWidth, reqHeight);
                if (null != bitmap) {
                    LoadResult loadResult = new LoadResult(imageView, url, bitmap);
                    mMainHandler.obtainMessage(1, loadResult).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "ImageeLoader#" + mCount.getAndIncrement());
        }
    };
    private static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXINUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(), sThreadFactory);
    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LoadResult loadResult = (LoadResult) msg.obj;
            ImageView imageView = loadResult.imageView;
            //imageView.setImageBitmap(loadResult.bitmap);
            String uri = (String) imageView.getTag(1);
            if (uri.equals(loadResult.url)) {
                imageView.setImageBitmap(loadResult.bitmap);
            }
        }
    };
}
