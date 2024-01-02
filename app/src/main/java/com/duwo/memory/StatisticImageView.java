package com.duwo.memory;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * @author liuxin
 * @Date 2021/7/7
 * @Description
 **/
public class StatisticImageView extends AppCompatImageView {
    public StatisticImageView(@NonNull  Context context) {
        super(context);
    }

    public StatisticImageView(@NonNull  Context context, @Nullable  AttributeSet attrs) {
        super(context, attrs);
    }

    public StatisticImageView(@NonNull  Context context, @Nullable  AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
