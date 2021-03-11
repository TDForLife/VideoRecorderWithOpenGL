package com.icechn.videorecorder.tools;

import android.content.Context;

/**
 * Created by linkaipeng on 2016/9/2.
 */
public class DensityUtil {

    public static final float ROUNDING_VALUE = 0.5f;

    /**
     * convert dip to pixel
     */
    public static int dip2px(Context pContext, float pDipValue) {
        float scale = pContext.getResources().getDisplayMetrics().density;
        return (int) (pDipValue * scale + ROUNDING_VALUE);
    }

    /**
     * convert dip to pixel
     */
    public static float dip2pxF(Context pContext, float pDipValue) {
        float scale = pContext.getResources().getDisplayMetrics().density;
        return pDipValue * scale + ROUNDING_VALUE;
    }

    /**
     * convert pixel to dip
     */
    public static int px2dip(Context pContext, float pPxValue) {
        float scale = pContext.getResources().getDisplayMetrics().density;
        return (int) (pPxValue / scale + ROUNDING_VALUE);
    }

    /**
     * convert pixel to sp
     *
     * @param pxValue
     * @return
     */
    public static int px2sp(Context context, float pxValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (pxValue / fontScale + 0.5f);
    }
}
