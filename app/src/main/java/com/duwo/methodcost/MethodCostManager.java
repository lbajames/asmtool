package com.duwo.methodcost;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;

/**
 * @author liuxin
 * @Date 2021/5/31
 * @Description
 **/
public class MethodCostManager {

    private static ArrayList<MethodCostData> methodCostList = new ArrayList<>();

    private static Handler handler = new Handler(Looper.myLooper());

    public static void addData(String threadName, String methodName, long startMills, long endMills) {
        if (TextUtils.isEmpty(threadName) || TextUtils.isEmpty(methodName)) {
            throw new RuntimeException("threadName or methodName can not be empty");
        }
        if (startMills == 0 && endMills == 0) {
            throw new RuntimeException("startMills and endMills can not be both zero");
        }
    }

    public static void print() {
        for (MethodCostData methodCostData : methodCostList) {
            Log.d("lx11", "print: " + methodCostData.threadName + " " + methodCostData.methodName + " "
                    + methodCostData.startMills + " " + methodCostData.endMills);
        }
    }

    public static void addData() {

    }

    public static void assembleData() {

    }

    public void mergeStartAndEndData() {
        for (int i = 0; i < methodCostList.size(); ++i) {
            MethodCostData methodCostData = methodCostList.get(i);
            if (methodCostData.startMills > 0 && methodCostData.endMills > 0) {
                continue;
            }
            if (methodCostData.startMills == 0 && methodCostData.endMills == 0) {
                continue;
            }
            for (int j = 1; j < 10; ++j) {
                MethodCostData pairedCostData = methodCostList.get(0);
                if (pairedCostData.methodName.equals(methodCostData.methodName)
                        && pairedCostData.threadName.equals(methodCostData.threadName)) {

                }
                if (methodCostData.startMills == 0 && pairedCostData.endMills == 0) {
                    methodCostData.startMills = pairedCostData.startMills;
                    pairedCostData.startMills = 0;
                }
                if (methodCostData.endMills == 0 && pairedCostData.startMills == 0) {
                    methodCostData.endMills = pairedCostData.endMills;
                    pairedCostData.endMills = 0;
                }
            }
        }
    }
}
