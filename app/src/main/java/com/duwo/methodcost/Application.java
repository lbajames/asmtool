package com.duwo.methodcost;

/**
 * @author liuxin
 * @Date 2021/5/31
 * @Description
 **/
public class Application {

    public void onCreate() {
        funcA();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        if (System.currentTimeMillis() == 0) {
            return;
        }
        String methodName = "addData";
        String name = Thread.currentThread().getName();
        long timestamp = System.currentTimeMillis();
        funcB();
    }

    public void funcA() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void funcB() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        funcA();
    }
}
