package org.easydarwin.blogdemos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/**
 * @CreadBy ：DramaScript
 * @date 2017/8/23
 */
public class VideoSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder mSurfaceHolder;
    private static Matrix matrix = new Matrix();

    public VideoSurfaceView(Context context) {
        super(context);
        init();
    }

    public VideoSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }

    //画图方法
    public void drawImg(Bitmap bitmap) {
        Canvas canvas = mSurfaceHolder.lockCanvas();
        if (canvas == null || mSurfaceHolder == null) {
            return;
        }
        try {
            if (bitmap != null) {
                //画布宽和高
                int height = getHeight();
                int width = getWidth();
                //生成合适的图像
                bitmap = getReduceBitmap(bitmap, width, height);

                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.FILL);
                //清屏
                paint.setColor(Color.BLACK);
                canvas.drawRect(new Rect(0, 0, getWidth(), getHeight()), paint);
                //Log.d("ImageSurfaceView_IMG",path);
                //画图
                canvas.drawBitmap(bitmap, matrix, paint);
            }
            //解锁显示
            mSurfaceHolder.unlockCanvasAndPost(canvas);
        } catch (Exception ex) {
            Log.e("ImageSurfaceView", ex.getMessage());
            return;
        } finally {
            //资源回收
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    //缩放图片
    private Bitmap getReduceBitmap(Bitmap bitmap, int w, int h) {
        int width = bitmap.getWidth();
        int hight = bitmap.getHeight();
        Matrix matrix = new Matrix();
        float wScake = ((float) w / width);
        float hScake = ((float) h / hight);
        matrix.postScale(wScake, hScake);
        return Bitmap.createBitmap(bitmap, 0, 0, width, hight, matrix, true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        drawImg(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
