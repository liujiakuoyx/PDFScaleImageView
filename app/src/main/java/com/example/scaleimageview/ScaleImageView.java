package com.example.scaleimageview;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.util.HashSet;
import java.util.Set;

/**
 * create by liujiakuo 2019/5/17
 */
public class ScaleImageView extends View {
    private static final int VERTICAL_MIN_DISTANCE = 50;//发生fling最小坐标差
    private static final int FLING_MIN_VERTICAL = 500;//发生fling最小速度
    private static final int DEFAULT_ANIM_DURATION = 500;//动画执行时间
    private static final int MIN_MOVE_VALUE = 5;//发生重绘阈值
    private static final int DEFAULT_THRESHOLD_VALUE_VALUE = 160;
    private static final int QUICK_SCALE_THRESHOLD_VALUE = 20;
    private static final float START_QUICK_SCALE_MIN_VALUE = 0.03f;
    //动画差值
    public static final int EASE_OUT_QUAD = 1;
    public static final int EASE_IN_OUT_QUAD = 2;
    private static final Set<Integer> VALID_EASING_STYLES = new HashSet<Integer>() {
        {
            add(EASE_OUT_QUAD);
            add(EASE_IN_OUT_QUAD);
        }
    };
    /**
     * 最小缩放比模式
     */
    public static final int SCALE_TYPE_CENTER_INSIDE = 1;//默认值，选取bitmap与view宽高比最小的一个，并在另一个方向上居中
    public static final int SCALE_TYPE_CENTER_CROP = 2;//选取bitmap与view宽高比最大的一个，并在另一个方向上居中
    public static final int SCALE_TYPE_CUSTOM = 3;//使得view与bitmap两个方向上的缩放都大于minScale并小于maxScale
    private static final Set<Integer> VALID_SCALE_TYPES = new HashSet<Integer>() {
        {
            add(SCALE_TYPE_CENTER_INSIDE);
            add(SCALE_TYPE_CENTER_CROP);
            add(SCALE_TYPE_CUSTOM);
        }
    };
    /**
     * 滑动模式
     */
    public static final int PAN_LIMIT_INSIDE = 1;//默认值，不允许在屏幕外平移图像
    public static final int PAN_LIMIT_OUTSIDE = 2;//允许在屏幕外平移图像，最大图像边界与view边界对其
    public static final int PAN_LIMIT_CENTER = 3;//允许在屏幕外平移图像，最大图像边界到view中心
    private static final Set<Integer> VALID_PAN_LIMITS = new HashSet<Integer>() {
        {
            add(PAN_LIMIT_INSIDE);
            add(PAN_LIMIT_OUTSIDE);
            add(PAN_LIMIT_CENTER);
        }
    };
    /**
     * 双击缩放模式
     */
    public static final int ZOOM_FOCUS_FIXED = 1;
    public static final int ZOOM_FOCUS_CENTER = 2;
    public static final int ZOOM_FOCUS_CENTER_IMMEDIATE = 3;
    private static final Set<Integer> VALID_ZOOM_STYLES = new HashSet<Integer>() {
        {
            add(ZOOM_FOCUS_FIXED);
            add(ZOOM_FOCUS_CENTER);
            add(ZOOM_FOCUS_CENTER_IMMEDIATE);
        }
    };
    private int mDoubleTapZoomStyle = ZOOM_FOCUS_FIXED;

    private float mDoubleTapZoomScale = 1F;
    //最小scale规则
    private int mMinimumScaleType = SCALE_TYPE_CENTER_INSIDE;
    private int mPanLimit = PAN_LIMIT_INSIDE;
    private OnAnimationEventListener mAnimationEventListener;

    //双击、fling动画
    private Anim mAnim;
    //当前位移
    private PointF vTranslate;
    private ScaleAndTranslate mSatTemp;
    private PointF vCenterStart;
    private PointF vTranslateStart;
    private PointF vTranslateBefore;
    //缩放状态
    private boolean isZooming;
    //滑动状态
    private boolean isPanning;
    /**
     * 此标记用来标识，在此之前发生了双击事件，为了后续判断是否在move中进行缩放
     */
    private boolean isQuickScaling;

    private int mMaxTouchCount;
    private float mQuickScaleLastDistance;
    private final float mQuickScaleThreshold;
    private boolean mQuickScaleMoved;//正在双击拖动缩放
    private PointF mQuickScaleVLastPoint;
    private PointF mQuickScaleSCenter;
    private PointF mQuickScaleVStart;

    private Bitmap mSourceBitmap;
    private int mBitmapWidth;
    private int mBitmapHeight;
    //就绪状态
    private boolean mReadySent;
    //fling、双击、单击检测
    private GestureDetector mDetector;

    private float mScale = 0f;
    private float mMaxScale = 2f;
    private float mMinScale = minScale();

    private float mDensity;
    //move达到了发生重绘的阈值
    private boolean mConsumed;
    //是否置顶，设置为true会造成居中无效
    private boolean mIsSticky;
    //是否可以进行缩放
    private boolean mZoomEnabled = true;
    private float mScaleStart;
    private float vDistStart;
    //是否可以双击之后拖动缩放
    private boolean mQuickScaleEnabled = true;
    /**
     * 当{@link #mDoubleTapZoomStyle} = ZOOM_FOCUS_CENTER_IMMEDIATE
     * 发生的缩放不会伴随动画，会在瞬间完成
     * 这里的参数用来记录下次重绘的时候要发生的改变
     */
    private Float mPendingScale;
    private PointF mSrcPendingCenter;
    private Paint mBitmapPaint;
    private Matrix mMatrix;

    public ScaleImageView(Context context) {
        this(context, null);
    }

    public ScaleImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScaleImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
        mQuickScaleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, QUICK_SCALE_THRESHOLD_VALUE, context.getResources().getDisplayMetrics());
    }

    public final void setImageResource(@IdRes int resId) {
        setImageBitmap(BitmapFactory.decodeResource(getResources(), resId));
    }

    public final void setImageDrawable(@NonNull Drawable drawable) {
        setImageBitmap(drawable2Bitmap(drawable));
    }

    public final void setImageBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        reset(true);
        mSourceBitmap = bitmap;
        mBitmapWidth = bitmap.getWidth();
        mBitmapHeight = bitmap.getHeight();
        if (checkReady()) {
            invalidate();
            requestLayout();
        }
    }

    private void init(Context context) {
        setGestureDetector(context);
        //设置默认的最小缩放比
        setMinimumDpi(DEFAULT_THRESHOLD_VALUE_VALUE);
        //设置默认双击放大值
        setDoubleTapZoomDpi(DEFAULT_THRESHOLD_VALUE_VALUE);
        mDensity = getResources().getDisplayMetrics().density;
    }

    /**
     * 资源准备就绪
     */
    private boolean checkReady() {
        boolean ready = getWidth() > 0 && getHeight() > 0 && mBitmapWidth > 0 && mBitmapHeight > 0 && mSourceBitmap != null;
        if (!mReadySent && ready) {
            preDraw();
            mReadySent = true;
        }
        return ready;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (canInterruptAnim()) {
            return true;
        }

        if (vTranslate == null) {
            return true;
        }

        if (!isQuickScaling && (mDetector != null && mDetector.onTouchEvent(event))) {
            isZooming = false;
            isPanning = false;
            mMaxTouchCount = 0;
            return true;
        }

        initTranslate();

        return onTouchEventInternal(event) || super.onTouchEvent(event);
    }

    private boolean onTouchEventInternal(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
            case MotionEvent.ACTION_POINTER_2_DOWN:
                onDown(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                return onMove(event);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_2_UP:
                return onUp(event);
            case MotionEvent.ACTION_CANCEL:
                mConsumed = false;
                break;
        }
        return false;
    }

    private void onDown(MotionEvent event) {
        int touchCount = event.getPointerCount();
        mConsumed = false;
        mAnim = null;
        requestDisallowInterceptTouchEvent(true);
        mMaxTouchCount = Math.max(mMaxTouchCount, touchCount);
        if (touchCount >= 2) {
            //多指缩放
            if (mZoomEnabled) {
                float distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                mScaleStart = mScale;
                vDistStart = distance;
                vTranslateStart.set(vTranslate.x, vTranslate.y);
                vCenterStart.set((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2);
            } else {
                mMaxTouchCount = 0;
            }
        } else if (!isQuickScaling) {
            //单手指滑动，并且不是双tap拖动缩放
            vTranslateStart.set(vTranslate.x, vTranslate.y);
            vCenterStart.set(event.getX(), event.getY());
        }
    }

    private boolean onMove(MotionEvent event) {
        int touchCount = event.getPointerCount();
        mConsumed = false;
        if (mMaxTouchCount > 0) {
            if (touchCount >= 2) {
                float vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                float vCenterEndX = (event.getX(0) + event.getX(1)) / 2;
                float vCenterEndY = (event.getY(0) + event.getY(1)) / 2;

                if (mZoomEnabled && (distance(vCenterStart.x, vCenterEndX, vCenterStart.y, vCenterEndY) > MIN_MOVE_VALUE
                        || Math.abs(vDistEnd - vDistStart) > MIN_MOVE_VALUE || isPanning)) {
                    isZooming = true;
                    isPanning = true;
                    mConsumed = true;

                    double previousScale = mScale;
                    //计算缩放比
                    mScale = Math.min(mMaxScale, (vDistEnd / vDistStart) * mScaleStart);

                    if (mScale <= minScale()) {
                        //小于等于最小缩放比，重置参数，不做位移缩放等操作。
                        vDistStart = vDistEnd;
                        mScaleStart = minScale();
                        vCenterStart.set(vCenterEndX, vCenterEndY);
                        vTranslateStart.set(vTranslate);
                    } else {
                        //以两手指夹点进行位移，缩放。
                        float vLeftStart = vCenterStart.x - vTranslateStart.x;
                        float vTopStart = vCenterStart.y - vTranslateStart.y;
                        float vLeftNow = vLeftStart * (mScale / mScaleStart);
                        float vTopNow = vTopStart * (mScale / mScaleStart);
                        vTranslate.x = vCenterEndX - vLeftNow;
                        vTranslate.y = vCenterEndY - vTopNow;
                        if ((previousScale * mBitmapHeight < getHeight() && mScale * mBitmapHeight >= getHeight()) ||
                                (previousScale * mBitmapWidth < getWidth() && mScale * mBitmapWidth >= getWidth())) {
                            fitToBounds(true);
                            vCenterStart.set(vCenterEndX, vCenterEndY);
                            vTranslateStart.set(vTranslate);
                            mScaleStart = mScale;
                            vDistStart = vDistEnd;
                        }
                    }

                    fitToBounds(true);
                }
            } else if (isQuickScaling) {
                //处理双击拖动缩放
                float dist = Math.abs(mQuickScaleVStart.y - event.getY()) * 2 + mQuickScaleThreshold;

                if (mQuickScaleLastDistance == -1f) {
                    mQuickScaleLastDistance = dist;
                }
                //放大还是缩小
                boolean isUpwards = event.getY() > mQuickScaleVLastPoint.y;
                mQuickScaleVLastPoint.set(0, event.getY());

                //计算缩放的比例
                float spanDiff = Math.abs(1 - (dist / mQuickScaleLastDistance)) * 0.5f;

                if (spanDiff > START_QUICK_SCALE_MIN_VALUE || mQuickScaleMoved) {
                    //发生双击拖动缩放
                    mQuickScaleMoved = true;
                    float multiplier = 1;
                    if (mQuickScaleLastDistance > 0) {
                        multiplier = isUpwards ? (1 + spanDiff) : (1 - spanDiff);
                    }

                    double previousScale = mScale;
                    mScale = Math.max(minScale(), Math.min(mMaxScale, mScale * multiplier));
                    float vLeftStart = vCenterStart.x - vTranslateStart.x;
                    float vTopStart = vCenterStart.y - vTranslateStart.y;
                    float vLeftNow = vLeftStart * (mScale / mScaleStart);
                    float vTopNow = vTopStart * (mScale / mScaleStart);
                    vTranslate.x = vCenterStart.x - vLeftNow;
                    vTranslate.y = vCenterStart.y - vTopNow;

                    if ((previousScale * mBitmapHeight < getHeight() && mScale * mBitmapHeight >= getHeight()) ||
                            (previousScale * mBitmapWidth < getWidth() && mScale * mBitmapWidth >= getWidth())) {
                        fitToBounds(true);
                        vCenterStart.set(CoordUtils.sourceToViewCoord(vTranslate, mScale, mQuickScaleSCenter));
                        vTranslateStart.set(vTranslate);
                        mScaleStart = mScale;
                        dist = 0;
                    }

                }
                mQuickScaleLastDistance = dist;
                fitToBounds(true);
                mConsumed = true;
            } else if (!isZooming) {
                //单手指滑动
                float dx = Math.abs(event.getX() - vCenterStart.x);
                float dy = Math.abs(event.getY() - vCenterStart.y);
                //开始重绘的移动阈值
                float offset = mDensity * MIN_MOVE_VALUE;
                if (dx > offset || dy > offset || isPanning) {
                    //达到滑动阈值
                    mConsumed = true;
                    vTranslate.x = vTranslateStart.x + (event.getX() - vCenterStart.x);
                    vTranslate.y = vTranslateStart.y + (event.getY() - vCenterStart.y);

                    float lastX = vTranslate.x;
                    float lastY = vTranslate.y;
                    fitToBounds(true);
                    boolean atXEdge = lastX != vTranslate.x;
                    boolean atYEdge = lastY != vTranslate.y;
                    boolean edgeXSwipe = atXEdge && dx > dy && !isPanning;
                    boolean edgeYSwipe = atYEdge && dy > dx && !isPanning;
                    boolean yPan = lastY == vTranslate.y && dy > offset * 3;
                    if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || isPanning)) {
                        isPanning = true;
                    } else if (dx > offset || dy > offset) {
                        //已经滑动到边缘，告诉父类可以拦截事件(如在ViewPager中，触发翻页)
                        mMaxTouchCount = 0;
                        requestDisallowInterceptTouchEvent(false);
                    }
                }
            }
        }
        //是否通知重绘
        if (mConsumed) {
            invalidate();
            onMoveRedraw();
            return true;
        }
        return false;
    }

    private boolean onUp(MotionEvent event) {
        int touchCount = event.getPointerCount();
        if (isQuickScaling) {
            isQuickScaling = false;
            if (!mQuickScaleMoved) {
                //之前发生了双击，但是没有触发拖动缩放
                doubleTapZoom(mQuickScaleSCenter, vCenterStart);
            }
        }
        if (mMaxTouchCount > 0 && (isZooming || isPanning)) {
            if (isZooming && touchCount == 2) {
                isPanning = true;
                vTranslateStart.set(vTranslate.x, vTranslate.y);
                if (event.getActionIndex() == 1) {
                    vCenterStart.set(event.getX(0), event.getY(0));
                } else {
                    vCenterStart.set(event.getX(1), event.getY(1));
                }
            }
            if (touchCount < 3) {
                isZooming = false;
            }
            if (touchCount < 2) {
                isPanning = false;
                mMaxTouchCount = 0;
            }
            if (mAnim == null && mConsumed && event.getAction() == MotionEvent.ACTION_UP) {
                onNoAnimUpEvent();
            }
            return true;
        }
        if (touchCount == 1) {
            isZooming = false;
            isPanning = false;
            mMaxTouchCount = 0;
        }
        if (mAnim == null && mConsumed && event.getAction() == MotionEvent.ACTION_UP) {
            onNoAnimUpEvent();
        }
        return true;
    }

    /**
     * 手指离开时，没有触发动画
     */
    protected void onNoAnimUpEvent() {
    }

    /**
     * move事件发生了重绘
     */
    protected void onMoveRedraw() {
    }

    private void initTranslate() {
        if (vTranslateStart == null) {
            vTranslateStart = new PointF(0, 0);
        }
        if (vTranslateBefore == null) {
            vTranslateBefore = new PointF(0, 0);
        }
        if (vCenterStart == null) {
            vCenterStart = new PointF(0, 0);
        }
        vTranslateBefore.set(vTranslate);
    }

    private float distance(float x0, float x1, float y0, float y1) {
        float x = x0 - x1;
        float y = y0 - y1;
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * 判断动画是否可以中断
     */
    private boolean canInterruptAnim() {
        if (mAnim != null && !mAnim.interruptible) {
            //禁止父类拦截事件
            requestDisallowInterceptTouchEvent(true);
            return true;
        } else if (mAnim != null && mAnim.listener != null) {
            mAnim.listener.onInterruptedByUser();
            mAnim = null;
        }
        return false;
    }

    /**
     * 拦截事件
     */
    private void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private void setGestureDetector(final Context context) {
        mDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (canFling(e1, e2, velocityX, velocityY)) {
                    PointF vTranslateEnd = new PointF(vTranslate.x + (velocityX * 0.25f), vTranslate.y + (velocityY * 0.25f));
                    float sCenterXEnd = ((getWidth() >> 1) - vTranslateEnd.x) / mScale;
                    float sCenterYEnd = ((getHeight() >> 1) - vTranslateEnd.y) / mScale;
                    new AnimationBuilder(new PointF(sCenterXEnd, sCenterYEnd))
                            .withEasing(EASE_OUT_QUAD)
                            .withPanLimited(false)
                            .withOnAnimationEventListener(mAnimationEventListener == null ? new DefaultAnimationListener() : mAnimationEventListener)
                            .start();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                //双击缩放
                if (mZoomEnabled && mReadySent && vTranslate != null) {
                    if (mQuickScaleEnabled) {
                        //在这里不直接处理双支缩放
                        //记录双击参数，在OnTouchEvent里面处理拖动缩放
                        vCenterStart = new PointF(e.getX(), e.getY());
                        vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
                        mScaleStart = mScale;
                        isQuickScaling = true;
                        isZooming = true;
                        mQuickScaleLastDistance = -1F;
                        mQuickScaleSCenter = CoordUtils.viewToSourceCoord(vTranslate, mScale, vCenterStart);
                        mQuickScaleVStart = new PointF(e.getX(), e.getY());
                        mQuickScaleVLastPoint = new PointF(mQuickScaleSCenter.x, mQuickScaleSCenter.y);
                        mQuickScaleMoved = false;
                        //交给onTouchEvent处理
                        return false;
                    } else {
                        //不支持双击拖动缩放
                        doubleTapZoom(CoordUtils.viewToSourceCoord(vTranslate, mScale, new PointF(e.getX(), e.getY())), new PointF(e.getX(), e.getY()));
                        return true;
                    }
                }
                return super.onDoubleTapEvent(e);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();
                return true;
            }
        });
    }

    public Bitmap getSourceBitmap() {
        return mSourceBitmap;
    }

    public Paint getBitmapPaint() {
        return mBitmapPaint;
    }

    /**
     * 双击缩放
     */
    private void doubleTapZoom(PointF sCenter, PointF vFocus) {
        float doubleTapZoomScale = Math.min(mMaxScale, this.mDoubleTapZoomScale);
        //zoomIn true放大，false缩小
        boolean zoomIn = mScale <= doubleTapZoomScale * 0.9;
        float targetScale = zoomIn ? doubleTapZoomScale : minScale();
        if (targetScale == mScale) {
            return;
        }
        if (mDoubleTapZoomStyle == ZOOM_FOCUS_CENTER_IMMEDIATE) {
            setScaleAndCenter(targetScale, sCenter);
        } else if (mDoubleTapZoomStyle == ZOOM_FOCUS_CENTER || !zoomIn) {
            new AnimationBuilder(targetScale, sCenter)
                    .withInterruptible(false)
                    .withDuration(DEFAULT_ANIM_DURATION)
                    .withOnAnimationEventListener(mAnimationEventListener == null ? new DefaultAnimationListener() : mAnimationEventListener)
                    .start();
        } else if (mDoubleTapZoomStyle == ZOOM_FOCUS_FIXED) {
            new AnimationBuilder(targetScale, sCenter, vFocus)
                    .withInterruptible(false)
                    .withDuration(DEFAULT_ANIM_DURATION)
                    .withOnAnimationEventListener(mAnimationEventListener == null ? new DefaultAnimationListener() : mAnimationEventListener)
                    .start();
        }
        invalidate();
    }

    /**
     * 是否达到fling条件
     */
    private boolean canFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return mReadySent && vTranslate != null && e1 != null && e2 != null &&
                (Math.abs(e1.getX() - e2.getX()) > VERTICAL_MIN_DISTANCE ||
                        Math.abs(e1.getY() - e2.getY()) > VERTICAL_MIN_DISTANCE) &&
                (Math.abs(velocityX) > FLING_MIN_VERTICAL ||
                        Math.abs(velocityY) > FLING_MIN_VERTICAL) && !isZooming;
    }

    public final void setScaleAndCenter(float scale, PointF sCenter) {
        this.mAnim = null;
        this.mPendingScale = scale;
        this.mSrcPendingCenter = sCenter;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        createPaints();
        if (!checkReady()) {
            return;
        }
        //子类draw
        if (subDraw(canvas)) {
            return;
        }
        preDraw();
        //如果动画不为空，开始计算动画
        if (mAnim != null) {
            exeAnim();
        }
        //绘制bitmap
        drawBitmap(canvas);
    }

    /**
     * 绘制之前需要调整参数
     */
    private void preDraw() {
        if (getWidth() == 0 || getHeight() == 0 || mBitmapWidth <= 0 || mBitmapHeight <= 0) {
            return;
        }
        //没有动画缩放时，调整图像到一个中心点
        if (mSrcPendingCenter != null && mPendingScale != null) {
            mScale = mPendingScale;
            if (vTranslate == null) {
                vTranslate = new PointF();
            }
            vTranslate.x = (getWidth() >> 1) - (mScale * mSrcPendingCenter.x);
            vTranslate.y = (getHeight() >> 1) - (mScale * mSrcPendingCenter.y);
            mSrcPendingCenter = null;
            mPendingScale = null;
            fitToBounds(true);
        }
        fitToBounds(false);
    }

    /**
     * 子类继承，在父类draw之前做一些额外绘制，
     *
     * @return true 不会再执行父类draw
     */
    protected boolean subDraw(Canvas canvas) {
        return false;
    }

    /**
     * 执行动画
     */
    private void exeAnim() {
        if (vTranslateBefore == null) {
            vTranslateBefore = new PointF(0, 0);
        }
        vTranslateBefore.set(vTranslate);

        long scaleElapsed = System.currentTimeMillis() - mAnim.time;
        boolean finished = scaleElapsed > mAnim.duration;
        scaleElapsed = Math.min(scaleElapsed, mAnim.duration);
        mScale = ease(mAnim.easing, scaleElapsed, mAnim.scaleStart, mAnim.scaleEnd - mAnim.scaleStart, mAnim.duration);
        //差值器计算当前要发生缩放位移的目标值
        float vFocusNowX = ease(mAnim.easing, scaleElapsed, mAnim.vFocusStart.x, mAnim.vFocusEnd.x - mAnim.vFocusStart.x, mAnim.duration);
        float vFocusNowY = ease(mAnim.easing, scaleElapsed, mAnim.vFocusStart.y, mAnim.vFocusEnd.y - mAnim.vFocusStart.y, mAnim.duration);
        // 转换坐标
        vTranslate.x -= CoordUtils.sourceToViewX(vTranslate, mScale, mAnim.sCenterEnd.x) - vFocusNowX;
        vTranslate.y -= CoordUtils.sourceToViewY(vTranslate, mScale, mAnim.sCenterEnd.y) - vFocusNowY;

        fitToBounds(finished || (mAnim.scaleStart == mAnim.scaleEnd));
        if (finished) {
            if (mAnim.listener != null) {
                mAnim.listener.onComplete();
            }
            mAnim = null;
        }
        invalidate();
    }

    /**
     * 动画差值器
     */
    private float ease(int type, long time, float from, float change, long duration) {
        switch (type) {
            case EASE_IN_OUT_QUAD:
                return easeInOutQuad(time, from, change, duration);
            case EASE_OUT_QUAD:
                return easeOutQuad(time, from, change, duration);
            default:
                throw new IllegalStateException("Unexpected easing type: " + type);
        }
    }

    private float easeOutQuad(long time, float from, float change, long duration) {
        float progress = (float) time / (float) duration;
        return -change * progress * (progress - 2) + from;
    }

    private float easeInOutQuad(long time, float from, float change, long duration) {
        float timeF = time / (duration / 2f);
        if (timeF < 1) {
            return (change / 2f * timeF * timeF) + from;
        } else {
            timeF--;
            return (-change / 2f) * (timeF * (timeF - 2) - 1) + from;
        }
    }

    /**
     * 绘制bitmap
     */
    private void drawBitmap(Canvas canvas) {
        if (mSourceBitmap != null) {
            float xScale = mScale, yScale = mScale;
            if (mMatrix == null) {
                mMatrix = new Matrix();
            }
            mMatrix.reset();
            mMatrix.postScale(xScale, yScale);
            mMatrix.postTranslate(vTranslate.x, vTranslate.y);
            canvas.drawBitmap(mSourceBitmap, mMatrix, mBitmapPaint);
        }
    }

    private void createPaints() {
        if (mBitmapPaint == null) {
            mBitmapPaint = new Paint();
            mBitmapPaint.setAntiAlias(true);
            mBitmapPaint.setFilterBitmap(true);
            mBitmapPaint.setDither(true);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        boolean resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
        boolean resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
        int width = parentWidth;
        int height = parentHeight;
        if (mBitmapWidth > 0 && mBitmapHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = mBitmapWidth;
                height = mBitmapHeight;
            } else if (resizeHeight) {
                height = (int) ((((double) mBitmapHeight / (double) mBitmapWidth) * width));
            } else if (resizeWidth) {
                width = (int) ((((double) mBitmapWidth / (double) mBitmapHeight) * height));
            }
        }
        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());
        setMeasuredDimension(width, height);
    }

    /**
     * 获取中心点
     */
    public final PointF getCenter() {
        int mX = getWidth() >> 1;
        int mY = getHeight() >> 1;
        return CoordUtils.viewToSourceCoord(vTranslate, mScale, mX, mY);
    }

    /**
     * 获取合适范围的缩放比例
     */
    private float limitedScale(float targetScale) {
        targetScale = Math.max(minScale(), targetScale);
        targetScale = Math.min(mMaxScale, targetScale);
        return targetScale;
    }

    private float minScale() {
        int vPadding = getPaddingBottom() + getPaddingTop();
        int hPadding = getPaddingLeft() + getPaddingRight();
        if (mMinimumScaleType == SCALE_TYPE_CENTER_CROP) {
            return Math.max((getWidth() - hPadding) / (float) mBitmapWidth, (getHeight() - vPadding) / (float) mBitmapHeight);
        } else if (mMinimumScaleType == SCALE_TYPE_CUSTOM && mMinScale > 0) {
            return mMinScale;
        } else {
            return Math.min((getWidth() - hPadding) / (float) mBitmapWidth, (getHeight() - vPadding) / (float) mBitmapHeight);
        }
    }

    public void setMinimumScaleType(int type) {
        if (VALID_SCALE_TYPES.contains(type)) {
            mMinimumScaleType = type;
        }
    }

    public void setPanLimit(int limit) {
        if (VALID_PAN_LIMITS.contains(limit)) {
            mPanLimit = limit;
        }
    }

    /**
     * 调整参数，是的图像边界不会超出置顶范围
     */
    private void fitToBounds(boolean center, ScaleAndTranslate sat) {
        if (mPanLimit == PAN_LIMIT_OUTSIDE && mReadySent) {
            //可以滑出屏幕
            center = false;
        }

        PointF vTranslate = sat.vTranslate;
        float scale = limitedScale(sat.scale);
        float scaleWidth = scale * mBitmapWidth;
        float scaleHeight = scale * mBitmapHeight;

        //正方向最大滑动距离
        if (mPanLimit == PAN_LIMIT_CENTER && mReadySent) {
            vTranslate.x = Math.max(vTranslate.x, (getWidth() >> 1) - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, (getHeight() >> 1) - scaleHeight);
        } else if (center) {
            vTranslate.x = Math.max(vTranslate.x, getWidth() - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, getHeight() - scaleHeight);
        } else {
            vTranslate.x = Math.max(vTranslate.x, -scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, -scaleHeight);
        }

        float xPaddingRatio = getPaddingLeft() > 0 || getPaddingRight() > 0 ? getPaddingLeft() / (float) (getPaddingLeft() + getPaddingRight()) : 0.5f;
        float yPaddingRatio = getPaddingTop() > 0 || getPaddingBottom() > 0 ? getPaddingTop() / (float) (getPaddingTop() + getPaddingBottom()) : 0.5f;

        float maxTx;
        float maxTy;
        if (mPanLimit == PAN_LIMIT_CENTER && mReadySent) {
            maxTx = Math.max(0, getWidth() >> 1);
            maxTy = Math.max(0, getHeight() >> 1);
        } else if (center) {
            maxTx = Math.max(0, (getWidth() - scaleWidth) * xPaddingRatio);
            maxTy = Math.max(0, (getHeight() - scaleHeight) * yPaddingRatio);
        } else {
            maxTx = Math.max(0, getWidth());
            maxTy = Math.max(0, getHeight());
        }
        //负方向最大滑动距离
        vTranslate.x = Math.min(vTranslate.x, maxTx);
        vTranslate.y = Math.min(vTranslate.y, maxTy);

        sat.scale = scale;
    }

    /**
     * 调整参数，图像边界不超出规定范围
     */
    private void fitToBounds(boolean center) {
        boolean init = false;
        if (vTranslate == null) {
            init = true;
            vTranslate = new PointF(0, 0);
        }
        if (mSatTemp == null) {
            mSatTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }
        mSatTemp.scale = mScale;
        mSatTemp.vTranslate.set(vTranslate);
        fitToBounds(center, mSatTemp);
        mScale = mSatTemp.scale;
        vTranslate.set(mSatTemp.vTranslate);
        if (init && !mIsSticky) {
            //mIsSticky为true，图像不居中显示
            vTranslate.set(vTranslateForSCenter(mBitmapWidth >> 1, mBitmapHeight >> 1, mScale));
        }
    }

    public void setSticky(boolean sticky) {
        this.mIsSticky = sticky;
    }

    public Anim getAnim() {
        return mAnim;
    }

    private class DefaultAnimationListener implements OnAnimationEventListener {

        @Override
        public void onComplete() {
            post(new Runnable() {
                @Override
                public void run() {
                    onAnimationFinished();
                }
            });
        }

        @Override
        public void onInterruptedByUser() {

        }

        @Override
        public void onInterruptedByNewAnim() {

        }
    }

    protected void onAnimationFinished() {

    }

    /**
     * 双击、fling动画
     */
    private static class Anim {
        private float scaleStart; // Scale at start of anim
        private float scaleEnd; // Scale at end of anim (target)
        private PointF sCenterStart; // Source center point at start
        private PointF sCenterEnd; // Source center point at end, adjusted for pan limits
        private PointF vFocusStart; // View point that was double tapped
        private PointF vFocusEnd; // Where the view focal point should be moved to during the anim
        private long duration = 500; // How long the anim takes
        private boolean interruptible = true; // Whether the anim can be interrupted by a touch
        private int easing = EASE_IN_OUT_QUAD; // Easing style
        private long time = System.currentTimeMillis(); // Start time
        private OnAnimationEventListener listener; // Event listener
    }

    public final class AnimationBuilder {

        private final float targetScale;
        private final PointF targetSCenter;
        private final PointF vFocus;
        private long duration = DEFAULT_ANIM_DURATION;
        private int easing = EASE_IN_OUT_QUAD;
        private boolean interruptible = true;
        private boolean panLimited = true;
        private OnAnimationEventListener listener;

        private AnimationBuilder(PointF sCenter) {
            this.targetScale = mScale;
            this.targetSCenter = sCenter;
            this.vFocus = null;
        }

        private AnimationBuilder(float scale, PointF sCenter) {
            this.targetScale = scale;
            this.targetSCenter = sCenter;
            this.vFocus = null;
        }

        private AnimationBuilder(float scale, PointF sCenter, PointF vFocus) {
            this.targetScale = scale;
            this.targetSCenter = sCenter;
            this.vFocus = vFocus;
        }

        public AnimationBuilder withDuration(long duration) {
            this.duration = duration;
            return this;
        }

        public AnimationBuilder withInterruptible(boolean interruptible) {
            this.interruptible = interruptible;
            return this;
        }

        public AnimationBuilder withEasing(int easing) {
            if (VALID_EASING_STYLES.contains(easing)) {
                this.easing = easing;
            }
            return this;
        }

        public AnimationBuilder withOnAnimationEventListener(OnAnimationEventListener listener) {
            this.listener = listener;
            return this;
        }

        private AnimationBuilder withPanLimited(boolean panLimited) {
            this.panLimited = panLimited;
            return this;
        }

        public void start() {
            if (mAnim != null && mAnim.listener != null) {
                mAnim.listener.onInterruptedByNewAnim();
            }

            int vxCenter = getPaddingLeft() + (getWidth() - getPaddingRight() - getPaddingLeft()) >> 1;
            int vyCenter = getPaddingTop() + (getHeight() - getPaddingBottom() - getPaddingTop()) >> 1;
            float targetScale = limitedScale(this.targetScale);
            PointF targetSCenter = panLimited ? limitedSCenter(this.targetSCenter.x, this.targetSCenter.y, targetScale, new PointF()) : this.targetSCenter;
            mAnim = new Anim();
            mAnim.scaleStart = mScale;
            mAnim.scaleEnd = targetScale;
            mAnim.time = System.currentTimeMillis();
            mAnim.sCenterStart = getCenter();
            mAnim.sCenterEnd = targetSCenter;
            mAnim.vFocusStart = CoordUtils.sourceToViewCoord(vTranslate, mScale, targetSCenter);
            mAnim.vFocusEnd = new PointF(
                    vxCenter,
                    vyCenter
            );
            mAnim.duration = duration;
            mAnim.interruptible = interruptible;
            mAnim.easing = easing;
            mAnim.time = System.currentTimeMillis();
            mAnim.listener = listener;

            if (vFocus != null) {
                //动画结束中心点
                float vTranslateXEnd = vFocus.x - (targetScale * mAnim.sCenterStart.x);
                float vTranslateYEnd = vFocus.y - (targetScale * mAnim.sCenterStart.y);
                ScaleAndTranslate satEnd = new ScaleAndTranslate(targetScale, new PointF(vTranslateXEnd, vTranslateYEnd));
                // 修正 动画结束中心点，不超出屏幕规定边界
                fitToBounds(true, satEnd);
                // 调整动画结束后，不超出屏幕规定边界
                mAnim.vFocusEnd = new PointF(
                        vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
                        vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd)
                );
            }

            invalidate();
        }

    }

    public final void setDoubleTapZoomScale(float doubleTapZoomScale) {
        this.mDoubleTapZoomScale = doubleTapZoomScale;
    }

    public final void setDoubleTapZoomDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setDoubleTapZoomScale(averageDpi / dpi);
    }

    public final void setMinimumDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setMaxScale(averageDpi / dpi);
    }

    public final void setMaxScale(float mMaxScale) {
        this.mMaxScale = mMaxScale;
    }

    private PointF limitedSCenter(float sCenterX, float sCenterY, float scale, PointF sTarget) {
        PointF vTranslate = vTranslateForSCenter(sCenterX, sCenterY, scale);
        int vxCenter = getPaddingLeft() + (getWidth() - getPaddingRight() - getPaddingLeft()) >> 1;
        int vyCenter = getPaddingTop() + (getHeight() - getPaddingBottom() - getPaddingTop()) >> 1;
        float sx = (vxCenter - vTranslate.x) / scale;
        float sy = (vyCenter - vTranslate.y) / scale;
        sTarget.set(sx, sy);
        return sTarget;
    }

    private PointF vTranslateForSCenter(float sCenterX, float sCenterY, float scale) {
        int vxCenter = getPaddingLeft() + (getWidth() - getPaddingRight() - getPaddingLeft()) >> 1;
        int vyCenter = getPaddingTop() + (getHeight() - getPaddingBottom() - getPaddingTop()) >> 1;
        if (mSatTemp == null) {
            mSatTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }
        mSatTemp.scale = scale;
        mSatTemp.vTranslate.set(vxCenter - (sCenterX * scale), vyCenter - (sCenterY * scale));
        fitToBounds(true, mSatTemp);
        return mSatTemp.vTranslate;
    }

    private static class ScaleAndTranslate {
        private ScaleAndTranslate(float scale, PointF vTranslate) {
            this.scale = scale;
            this.vTranslate = vTranslate;
        }

        private float scale;
        private PointF vTranslate;
    }

    public void recycle() {
        reset(true);
        mBitmapPaint = null;
    }

    public PointF getTranslate() {
        return vTranslate;
    }

    public float getScale() {
        return mScale;
    }

    /**
     * set anim listener,if listener not null,do not call {@link #onAnimationFinished()}
     */
    public void setAnimationEventListener(OnAnimationEventListener listener) {
        this.mAnimationEventListener = listener;
    }

    public void setDoubleTapZoomStyle(int doubleTapZoomStyle) {
        if (VALID_ZOOM_STYLES.contains(doubleTapZoomStyle)) {
            this.mDoubleTapZoomStyle = doubleTapZoomStyle;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        PointF sCenter = getCenter();
        if (mReadySent && sCenter != null) {
            this.mAnim = null;
            this.mPendingScale = mScale;
            this.mSrcPendingCenter = sCenter;
        }
    }

    /**
     * 重置各种参数
     *
     * @param newImage 是否重置图片资源
     */
    public void reset(boolean newImage) {
        mScale = 0f;
        mScaleStart = 0f;
        vTranslate = null;
        vTranslateStart = null;
        vTranslateBefore = null;
        mPendingScale = 0f;
        mSrcPendingCenter = null;
        isZooming = false;
        isPanning = false;
        isQuickScaling = false;
        mMaxTouchCount = 0;
        vCenterStart = null;
        vDistStart = 0;
        mQuickScaleLastDistance = 0f;
        mQuickScaleMoved = false;
        mQuickScaleSCenter = null;
        mQuickScaleVLastPoint = null;
        mQuickScaleVStart = null;
        mAnim = null;
        mSatTemp = null;
        mMatrix = null;
        if (newImage) {
            if (mSourceBitmap != null) {
                mSourceBitmap.recycle();
            }
            mBitmapWidth = 0;
            mBitmapHeight = 0;
            mReadySent = false;
            mSourceBitmap = null;
        }
        setGestureDetector(getContext());
    }

    private Bitmap drawable2Bitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof NinePatchDrawable) {
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(),
                    drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            return bitmap;
        } else {
            return null;
        }
    }

    /**
     * view坐标/bitmap坐标 转换
     */
    private static class CoordUtils {

        /**
         * view坐标转bitmap坐标
         */
        public static final PointF viewToSourceCoord(PointF translate, float scale, float vx, float vy) {
            return viewToSourceCoord(translate, scale, vx, vy, new PointF());
        }

        public final static PointF viewToSourceCoord(PointF translate, float scale, float vx, float vy, PointF sTarget) {
            if (translate == null) {
                return null;
            }
            sTarget.set(viewToSourceX(translate, scale, vx), viewToSourceY(translate, scale, vy));
            return sTarget;
        }

        public final static PointF viewToSourceCoord(PointF translate, float scale, PointF vxy) {
            return viewToSourceCoord(translate, scale, vxy.x, vxy.y, new PointF());
        }

        private static float viewToSourceX(PointF translate, float scale, float vx) {
            if (translate == null) {
                return Float.NaN;
            }
            return (vx - translate.x) / scale;
        }

        private static float viewToSourceY(PointF translate, float scale, float vy) {
            if (translate == null) {
                return Float.NaN;
            }
            return (vy - translate.y) / scale;
        }

        /**
         * bitmap坐标转view坐标
         */
        public final static PointF sourceToViewCoord(PointF translate, float scale, PointF sxy) {
            return sourceToViewCoord(translate, scale, sxy.x, sxy.y, new PointF());
        }

        public final static PointF sourceToViewCoord(PointF translate, float scale, float sx, float sy, PointF vTarget) {
            if (translate == null) {
                return null;
            }
            vTarget.set(sourceToViewX(translate, scale, sx), sourceToViewY(translate, scale, sy));
            return vTarget;
        }

        private static float sourceToViewX(PointF translate, float scale, float sx) {
            if (translate == null) {
                return Float.NaN;
            }
            return (sx * scale) + translate.x;
        }

        private static float sourceToViewY(PointF translate, float scale, float sy) {
            if (translate == null) {
                return Float.NaN;
            }
            return (sy * scale) + translate.y;
        }
    }

    public interface OnAnimationEventListener {

        //完成
        void onComplete();

        //用户打断
        void onInterruptedByUser();

        //被另外的一个动画打断
        void onInterruptedByNewAnim();

    }
}