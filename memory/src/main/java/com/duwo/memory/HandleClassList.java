package com.duwo.memory;

/**
 * @author liuxin
 * @Date 2021/6/19
 * @Description
 **/
public class HandleClassList {
    public String classList;

    public HandleClassList() {

    }

    public String getClassList() {
        return classList;
    }

    public void setClassList(String classList) {
        this.classList = classList;
    }

    public boolean isHandleClass(String className) {
        String[] classes = classList.split(",");
        for (String targetClassName : classes) {
            if (className.contains(targetClassName + ".class")) {
                return true;
            }
        }
        return false;
    }
}
