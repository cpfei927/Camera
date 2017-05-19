package com.cpfei.camera;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;

import java.lang.reflect.Method;

/**
 * Created by cpfei on 2017/5/16.
 */

public class ViewUtils {

    public static void addVirtualKeyPadding(Context context, View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), view.getPaddingBottom()
                            + getVirtualKeyHeight(context));
        }
    }

    public static int getVirtualKeyHeight(Context context) {
        if (context == null || !(context instanceof Activity)) {
            return 0;
        }
        Display display = ((Activity) context).getWindowManager()
                .getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);
        int h = dm.heightPixels;
        int rawH = dm.heightPixels;
        @SuppressWarnings("rawtypes")
        Class c;
        try {
            c = Class.forName("android.view.Display");
            @SuppressWarnings("unchecked")
            Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
            method.invoke(display, dm);
            rawH = dm.heightPixels;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
        return rawH - h;
    }


}
