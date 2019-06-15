package com.example.scaleimageview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PdfScaleImageView extends ScaleImageView {
    //补丁Biemap
    private Bitmap mPartBitmap;
    //加载的callback
    private OnLoadPageCallback mLoadPageCallback;
    //缩放pdf来充满bitmap
    private float mPdfScale;
    private RectF mDesRect;
    private Rect mSrcRect;

    private LoadPartTask mLoadPartTask;
    private int mCurrentOrientation;

    public PdfScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
        init();
    }

    public PdfScaleImageView(Context context) {
        super(context);
        init();
    }

    private void init() {
        mCurrentOrientation = getContext().getResources().getConfiguration().orientation;
        changePdfMinimumScaleType();
    }

    @Override
    protected boolean subDraw(Canvas canvas) {
        if (mPartBitmap != null && getAnim() == null) {
            //绘制清晰的补丁，绘制完释放
            if (mDesRect == null) {
                mDesRect = new RectF(0, 0, getWidth(), getHeight());
            }

            if (mSrcRect == null) {
                mSrcRect = new Rect(0, 0, mPartBitmap.getWidth(), mPartBitmap.getHeight());
            } else {
                mSrcRect.set(0, 0, mPartBitmap.getWidth(), mPartBitmap.getHeight());
            }
            canvas.drawBitmap(mPartBitmap, mSrcRect, mDesRect, getBitmapPaint());
            mPartBitmap.recycle();
            mPartBitmap = null;
            //跳过父类draw
            return true;
        }
        return false;
    }

    @Override
    protected void onNoAnimUpEvent() {
        //手指离开时，没有触发动画，应该加载高清补丁
        loadPart();
    }

    @Override
    protected void onMoveRedraw() {
        //move事件触发了重绘
        cancelLoadTask();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mDesRect == null) {
            mDesRect = new RectF(0, 0, getWidth(), getHeight());
        } else {
            mDesRect.set(0, 0, getWidth(), getHeight());
        }
        int orientation = getContext().getResources().getConfiguration().orientation;
        if (mCurrentOrientation != orientation) {
            mCurrentOrientation = orientation;
            if (getSourceBitmap() != null) {
                reset(false);
                changePdfMinimumScaleType();
            }
        }
    }

    /**
     * 设置横竖屏时，不同的缩放比，横屏时宽度充满，并且置顶
     */
    private void changePdfMinimumScaleType() {
        if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setSticky(true);
            setMinimumScaleType(SCALE_TYPE_CENTER_CROP);
        } else {
            setSticky(false);
            setMinimumScaleType(SCALE_TYPE_CENTER_INSIDE);
        }
    }

    /*加载pdf*/
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void showPdfPage(final PdfRenderer.Page page) {
        if (page == null) {
            return;
        }
        if (getWidth() != 0 && getHeight() != 0) {
            setPdfBitmap(page);
        } else {
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (getHeight() > 0 && getWidth() > 0) {
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        setPdfBitmap(page);
                    }
                }
            });
        }
    }

    @Override
    public void reset(boolean newImage) {
        super.reset(newImage);
        invalidate();
    }

    private void setPdfBitmap(PdfRenderer.Page page) {
        if (page == null) {
            return;
        }
        //PDF缩放
        mPdfScale = getPdfScale(page);
        int pdfBitmapWidth = (int) (page.getWidth() * mPdfScale);
        int pdfBitmapHeight = (int) (page.getHeight() * mPdfScale);
        Matrix matrix = new Matrix();
        matrix.postScale(mPdfScale, mPdfScale);
        Bitmap bitmap = Bitmap.createBitmap(pdfBitmapWidth, pdfBitmapHeight, Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        setImageBitmap(bitmap);
    }

    private float getPdfScale(PdfRenderer.Page page) {
        float iWidth = getWidth();
        float iHeight = getHeight();
        float pWidth = page.getWidth();
        float pHeight = page.getHeight();
        float scale;
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            //竖屏缩放取最小
            scale = Math.min((iWidth / pWidth), (iHeight / pHeight));
        } else {
            //横屏缩放取宽比
            scale = iWidth / pWidth;
        }

        return scale != 0 ? scale : 1;
    }

    public void loadPart() {
        if (getTranslate() == null) {
            return;
        }
        cancelLoadTask();
        //加载补丁
        mLoadPartTask = new LoadPartTask();
        mLoadPartTask.execute();
    }

    private void cancelLoadTask() {
        if (mLoadPartTask != null && !mLoadPartTask.isCancelled()) {
            mLoadPartTask.cancel(true);
        }
    }

    //缩放 fling动画停止后加载part
    @Override
    protected void onAnimationFinished() {
        loadPart();
    }

    public void setOnLoadPageCallback(OnLoadPageCallback callback) {
        this.mLoadPageCallback = callback;
    }

    /**
     * 加载part的asyncTask
     */
    private class LoadPartTask extends AsyncTask<Float, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Float... floats) {
            if (mLoadPageCallback == null || isCancelled()) {
                return null;
            }
            Bitmap partBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            mLoadPageCallback.onLoad(PdfScaleImageView.this, partBitmap, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return partBitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mPartBitmap = bitmap;
            if (mPartBitmap != null) {
                invalidate();
            }
        }
    }

    /**
     * 获取part的matrix
     */
    public Matrix getPartMatrix() {
        Matrix matrix = new Matrix();
        matrix.postScale(mPdfScale, mPdfScale);
        matrix.postScale(getScale(), getScale());
        matrix.postTranslate(getTranslate().x, getTranslate().y);
        return matrix;
    }

    public interface OnLoadPageCallback {
        void onLoad(PdfScaleImageView imageView, Bitmap bitmap, int renderMode);
    }
}
