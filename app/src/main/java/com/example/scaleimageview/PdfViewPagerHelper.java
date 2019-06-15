package com.example.scaleimageview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.io.File;
import java.io.IOException;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PdfViewPagerHelper implements ViewPager.OnPageChangeListener,
        PdfScaleImageView.OnLoadPageCallback {

    private ViewPager mPreviewPdfView;
    private PdfPagerAdapter mPdfPagerAdapter;
    private int mPdfPageCount = 0;

    private ParcelFileDescriptor mFileDescriptor;
    private PdfRenderer mPdfRenderer;
    //当前打开的pdf page
    private PdfRenderer.Page mCurrentPage;

    private int mCurrentPosition;
    private PdfPageListener mPdfPageListener;
    private Context mContext;
    private boolean isAttached;

    public PdfViewPagerHelper(Context context) {
        this.mContext = context;
    }

    public boolean attachViewPager(ViewPager pdfViewPager, String pdfFilePath) throws IOException {
        if (pdfViewPager == null || TextUtils.isEmpty(pdfFilePath)) {
            return false;
        }
        this.mFileDescriptor = ParcelFileDescriptor.open(new File(pdfFilePath), ParcelFileDescriptor.MODE_READ_ONLY);
        mPreviewPdfView = pdfViewPager;
        if (mFileDescriptor != null) {
            mPdfRenderer = new PdfRenderer(mFileDescriptor);
            mPdfPageCount = mPdfRenderer.getPageCount();
            mPdfPagerAdapter = new PdfPagerAdapter();
            mPreviewPdfView.addOnPageChangeListener(this);
            mPreviewPdfView.post(new Runnable() {
                @Override
                public void run() {
                    mPreviewPdfView.setAdapter(mPdfPagerAdapter);
                    mPreviewPdfView.setVisibility(View.VISIBLE);
                }
            });
            isAttached = true;
            return true;
        }
        return false;
    }

    public void setPdfPageListener(PdfPageListener listener) {
        mPdfPageListener = listener;
    }

    /**
     * get pdf page count
     */
    public int getPdfPageCount() {
        return !isAttached || mPdfRenderer == null ? 0 : mPdfRenderer.getPageCount();
    }

    /**
     * 加载补丁
     */
    public void loadPart() {
        if (isAttached && mPdfPagerAdapter.getCurrentPdfImageView() != null) {
            mPdfPagerAdapter.getCurrentPdfImageView().loadPart();
        }
    }

    /**
     * 获取当前pdf Page对象
     *
     * @param index
     * @return
     */
    private PdfRenderer.Page getPdfPage(int index) {
        if (mCurrentPage != null) {
            mCurrentPage.close();
            mCurrentPage = null;
        }
        if (mPdfRenderer != null && mPdfPageCount > index) {
            return mCurrentPage = mPdfRenderer.openPage(index);
        }
        return null;
    }

    /**
     * 设置PDF bitmap
     */
    private void setImageBitmap(int position, PdfScaleImageView imageView) {
        PdfRenderer.Page pdfPage = getPdfPage(position);
        if (pdfPage != null) {
            imageView.showPdfPage(mCurrentPage);
        }
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {
    }

    @Override
    public void onPageSelected(int i) {
        if (mPdfPageListener != null) {
            mPdfPageListener.onPageSelected(i);
        }
        mCurrentPosition = i;
        if (mPdfPagerAdapter != null && mPdfPagerAdapter.getCurrentPdfImageView() != null) {
            mPdfPagerAdapter.getCurrentPdfImageView().reset(false);
        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    /**
     * 加载补丁
     */
    @Override
    public void onLoad(PdfScaleImageView imageView, Bitmap bitmap, int renderMode) {
        if (mPdfRenderer == null || bitmap == null) {
            return;
        }
        PdfRenderer.Page pdfPage = getPdfPage(mCurrentPosition);
        if (pdfPage != null) {
            pdfPage.render(bitmap, null, imageView.getPartMatrix(), renderMode);
        }
    }

    /**
     * close PDF IO object
     */
    public void close() {
        if (mCurrentPage != null) {
            mCurrentPage.close();
        }
        if (mPdfRenderer != null) {
            mPdfRenderer.close();
        }
        if (mFileDescriptor != null) {
            try {
                mFileDescriptor.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        isAttached = false;
    }

    class PdfPagerAdapter extends PagerAdapter {
        private PdfScaleImageView mCurrentPdfImageView;

        @Override
        public int getCount() {
            return mPdfRenderer != null ? mPdfRenderer.getPageCount() : 0;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            return view == o;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
            if (object instanceof PdfScaleImageView) {
                ((PdfScaleImageView) object).recycle();
            }
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            if (object instanceof PdfScaleImageView) {
                mCurrentPdfImageView = (PdfScaleImageView) object;
            }
        }

        public PdfScaleImageView getCurrentPdfImageView() {
            return mCurrentPdfImageView;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, final int position) {
            final PdfScaleImageView imageView = new PdfScaleImageView(mContext);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(imageView);
            imageView.setPanLimit(PdfScaleImageView.PAN_LIMIT_INSIDE);
            imageView.setMaxScale(15f);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPdfPageListener != null) {
                        mPdfPageListener.onItemClick(position);
                    }
                }
            });
            imageView.setOnLoadPageCallback(PdfViewPagerHelper.this);
            if (imageView.getHeight() != 0 && imageView.getWidth() != 0) {
                setImageBitmap(position, imageView);
            } else {
                imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (imageView.getHeight() > 0 && imageView.getWidth() > 0) {
                            setImageBitmap(position, imageView);
                            imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    }
                });
            }
            return imageView;
        }
    }

    public interface PdfPageListener {
        void onPageSelected(int i);

        void onItemClick(int i);
    }
}
