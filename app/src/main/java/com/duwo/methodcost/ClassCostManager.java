package com.duwo.methodcost;

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;

/**
 * @author liuxin
 * @Date 2021/5/31
 * @Description
 **/
public class ClassCostManager {

    private static ArrayList<MethodCostData> methodCostList = new ArrayList<>();

    public static void addData(String threadName, String methodName, long startMills, long endMills) {
        if (TextUtils.isEmpty(threadName) || TextUtils.isEmpty(methodName)) {
            throw new RuntimeException("threadName or methodName can not be empty");
        }
        if (startMills == 0 && endMills == 0) {
            throw new RuntimeException("startMills and endMills can not be both zero");
        }
        MethodCostData methodCostData = new MethodCostData();
        methodCostData.threadName = threadName;
        methodCostData.methodName = methodName;
        methodCostData.startMills = startMills;
        methodCostData.endMills = endMills;
        methodCostList.add(methodCostData);
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
}
