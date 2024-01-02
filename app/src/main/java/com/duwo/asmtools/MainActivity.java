package com.duwo.asmtools;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.LayoutInflaterCompat;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.duwo.memory.StatisticImageView;
import com.duwo.methodcost.Application;
import com.duwo.methodcost.MethodCostManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LayoutInflater.from(this).setFactory2(new LayoutInflater.Factory2() {
            @Nullable
            @Override
            public View onCreateView(@Nullable View parent,
                                     @NonNull  String name,
                                     @NonNull  Context context,
                                     @NonNull  AttributeSet attrs) {
                if (name.equals("ImageView") || name.equals("androidx.appcompat.widget.AppCompatImageView")) {
                    return new StatisticImageView(context, attrs);
                }
                return null;
            }

            @Nullable
            @Override
            public View onCreateView(@NonNull  String name,
                                     @NonNull  Context context,
                                     @NonNull  AttributeSet attrs) {
                if (name.equals("ImageView") || name.equals("androidx.appcompat.widget.AppCompatImageView")) {
                    return new StatisticImageView(context, attrs);
                }
                return null;
            }
        });
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        long current = System.currentTimeMillis();
        Log.d("lx11", "onCreate: " + new Exception().getStackTrace()[1].getMethodName()
                + " " + Thread.currentThread().getStackTrace()[1].getMethodName());
        Log.d("lx11", "onCreate: " + (System.currentTimeMillis() - current));
        new Application().onCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MethodCostManager.print();
    }
}