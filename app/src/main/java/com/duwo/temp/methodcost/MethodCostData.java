package com.duwo.methodcost;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

/**
 * @author liuxin
 * @Date 2021/5/31
 * @Description
 **/
@SuppressWarnings("MultipleStringLiterals")
public class MethodCostData {
    public String threadName;
    public long startMills;
    public long endMills;
    public String methodName;

    @NonNull
    @NotNull
    @Override
    public String toString() {
        return threadName + " ***** " + methodName + " ***** " + startMills + " ***** " + endMills;
    }
}
