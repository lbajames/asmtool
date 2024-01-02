package com.duwo.methodcost;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * @author liuxin
 * @Date 2021/6/19
 * @Description
 **/
@SuppressWarnings({"MultipleStringLiterals", "Indentation"})
public class ClassCostManager {
    private static final int ADD_MESSAGE = 1;
    private static final int PRINT_MESSAGE = 2;
    private static final int QUIT = 3;
    private static final String TAG = "ClassCostStatistic";

    private static HashMap<String, ArrayList<MethodCostData>> methodCostMap = new HashMap<>();

    private static volatile Handler handler;
    private static volatile Thread thread;

    public static void init() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                handler = new ClassCostManager.CusHandler();
                Looper.loop();
            }
        });
        thread.start();
    }

    public static void addData(String threadName, String methodName, long startMills, long endMills) {
        if (handler == null) {
            return;
        }

        if (methodName.contains("MethodCostManager.addData")) {
            return;
        }

        Message message = Message.obtain();
        MethodCostData methodCostData = new MethodCostData();
        methodCostData.threadName = threadName;
        methodCostData.methodName = methodName;
        methodCostData.startMills = startMills;
        methodCostData.endMills = endMills;

        message.obj = methodCostData;
        message.what = ADD_MESSAGE;
        handler.sendMessage(message);
    }


    public static void print() {

        handler.sendEmptyMessage(PRINT_MESSAGE);
    }

    private static void handleAddMessage(MethodCostData methodCostData) {
        ArrayList<MethodCostData> arrayList = methodCostMap.get(methodCostData.threadName);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            methodCostMap.put(methodCostData.threadName, arrayList);
        }
        arrayList.add(methodCostData);
    }

    private static void handlePrint() {
        for (Map.Entry<String, ArrayList<MethodCostData>> entry : methodCostMap.entrySet()) {
            printThreadProcInfo(entry.getKey(), entry.getValue());
        }
    }

    private static void printThreadProcInfo(String threadName, ArrayList<MethodCostData> arrayList) {
        Log.d(TAG, "\n\n\n");
        if (arrayList.size() == 0) {
            Log.d(TAG, "******* Thread Begin ********* " + "ThreadName: " + threadName);
            return;
        }

        final long[] startTime = {arrayList.get(0).startMills};
        final long[] endTime = {arrayList.get(0).endMills};
        Collections.sort(arrayList, new Comparator<MethodCostData>() {
            @Override
            public int compare(MethodCostData o1, MethodCostData o2) {
                startTime[0] = Math.min(Math.max(o1.startMills, o2.startMills), startTime[0]);
                endTime[0] = Math.max(Math.max(o1.endMills, o2.endMills), endTime[0]);
                if ((o1.startMills - o2.startMills) == 0) {
                    return (int) (o2.endMills - o1.endMills);
                } else {
                    return (int) (o1.startMills - o2.startMills);
                }
            }
        });

        String startTimeStr;
        String endTimeStr;
        if (arrayList.size() > 0) {
            //print head
            startTimeStr = String.valueOf(startTime[0]);
            endTimeStr = String.valueOf(endTime[0]);

            Log.d(TAG, "******* Thread Begin ********* " + "ThreadName: " + threadName
                    + " from: " + startTimeStr.substring(startTimeStr.length() - 6)
                    + " to: " + endTimeStr.substring(endTimeStr.length() - 6)
                    + " " + (endTime[0] - startTime[0]));
        }

        //print method call(中序遍历求深度）
        Stack<Long> levelList = new Stack<>();
        for (MethodCostData methodCostData : arrayList) {
            while (!levelList.empty()) {
                long levelEndTime = levelList.peek();
                if (methodCostData.startMills >= levelEndTime) {
                    levelList.pop();
                    if (levelList.empty()) {
                        break;
                    } else {
                        continue;
                    }
                } else {
                    break;
                }
            }
            levelList.push(methodCostData.endMills);
            int level = levelList.size();

            StringBuilder sb = new StringBuilder();
            sb.append(threadName + " |");
            for (int i = 0; i < level; ++i) {
                sb.append("====");
            }
            startTimeStr = String.valueOf(methodCostData.startMills);
            endTimeStr = String.valueOf(methodCostData.endMills);
            sb.append("| " + String.format("%-60s", methodCostData.methodName) + "  "
                    + startTimeStr.substring(startTimeStr.length() - 6) + "  "
                    + endTimeStr.substring(endTimeStr.length() - 6) + " "
                    + (methodCostData.endMills - methodCostData.startMills));
            sb.append("   ");

            Log.d(TAG, sb.toString());
        }
    }

    public static class CusHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ADD_MESSAGE) {
                handleAddMessage((MethodCostData) msg.obj);
            } else if (msg.what == PRINT_MESSAGE) {
                handlePrint();
            }
        }
    }
}
