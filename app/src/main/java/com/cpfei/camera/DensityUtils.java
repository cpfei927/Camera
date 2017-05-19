package com.cpfei.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.lang.reflect.Field;

public class DensityUtils {

    public static int dip2px(Context context, float dipValue) {
        if (context == null) {
            return 0;
        }
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public static int px2dip(Context context, float pxValue) {
        if (context == null) {
            return 0;
        }
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }
    
    /**
     * 
     * @date 2015-4-13
     * @param context
     * @return values[0] width ,values[1] height
     * @Description get the device height and width
     *
     */
    public static int[] getScreenWidthAndHeight(Context context){
        WindowManager vm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        vm.getDefaultDisplay().getMetrics(outMetrics);
        int[] values = new int[2];
        values[0] = outMetrics.widthPixels;
        values[1] = outMetrics.heightPixels;
        return values;
    }

    /**
     * 用于获取状态栏的高度。
     * 
     * @return 返回状态栏高度的像素值。
     */
    public static int getStatusBarHeight(Activity ctx) {
            int statusBarHeight = 0;
            try {
                Class<?> c = Class.forName("com.android.internal.R$dimen");
                Object o = c.newInstance();
                Field field = c.getField("status_bar_height");
                int x = (Integer) field.get(o);
                statusBarHeight = ctx.getResources().getDimensionPixelSize(x);
            } catch (Exception e) {
                e.printStackTrace();
                Rect frame = new Rect();
                ctx.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
                statusBarHeight = frame.top;
            }
            return statusBarHeight;
    }
    
    
}
