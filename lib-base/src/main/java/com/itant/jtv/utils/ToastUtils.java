package com.itant.jtv.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;

import com.itant.jtv.R;
import com.miekir.mvvm.context.GlobalContext;
import com.miekir.mvvm.tools.ThreadTools;

/**
 * @author Miekir
 * @date 2020/7/5 0:08
 * Description: Toast工具
 */
@Keep
public final class ToastUtils {

    private ToastUtils() {}

    private static class Factory {
        public static ToastUtils INSTANCE = new ToastUtils();
    }

    private static ToastUtils getInstance() {
        return Factory.INSTANCE;
    }

    private int mVerticalMargin = 0;

    /**
     * @param text 要弹出的语句
     */
    public static void showShort(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }

        Context context = GlobalContext.getContext();
        if (context == null) {
            return;
        }

        initMargin(context);

        if (ThreadTools.isMainThread()) {
            showShortOnMain(context, text);
        } else {
            GlobalContext.runOnUiThread(() -> showShortOnMain(context, text));
        }
    }

    /**
     * @param text 要弹出的语句
     */
    public static void showLong(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }

        Context context = GlobalContext.getContext();
        if (context == null) {
            return;
        }

        initMargin(context);

        if (ThreadTools.isMainThread()) {
            showLongOnMain(context, text);
        } else {
            GlobalContext.runOnUiThread(() -> showLongOnMain(context, text));
        }
    }

    /**
     * 短Toast，需要保证在主线程执行
     * @param context 上下文
     * @param text 内容
     */
    private static void showShortOnMain(Context context, String text) {
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        View view = LayoutInflater.from(GlobalContext.getContext()).inflate(R.layout.view_toast, null);
        TextView tvToast = view.findViewById(R.id.tvToast);
        tvToast.setText(text);
        toast.setView(view);
        toast.setGravity(Gravity.BOTTOM | Gravity.END, getInstance().mVerticalMargin, getInstance().mVerticalMargin);
        toast.show();
    }

    /**
     * 长Toast，需要保证在主线程执行
     * @param context 上下文
     * @param text 内容
     */
    private static void showLongOnMain(Context context, String text) {
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        View view = LayoutInflater.from(GlobalContext.getContext()).inflate(R.layout.view_toast, null);
        TextView tvToast = view.findViewById(R.id.tvToast);
        tvToast.setText(text);
        toast.setView(view);
        toast.setGravity(Gravity.BOTTOM | Gravity.END, getInstance().mVerticalMargin, getInstance().mVerticalMargin);
        toast.show();
    }

    private static synchronized void initMargin(Context context) {
        if (context == null) {
            return;
        }
        if (getInstance().mVerticalMargin == 0) {
            getInstance().mVerticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, context.getResources().getDisplayMetrics());
        }
    }
}
