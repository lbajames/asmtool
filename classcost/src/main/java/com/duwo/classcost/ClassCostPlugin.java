package com.duwo.classcost;

/**
 * @author liuxin
 * @Date 2021/5/31
 * @Description
 **/

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.impldep.org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.ALOAD;
import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.ASTORE;
import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.ATHROW;
import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.LLOAD;
import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.LSTORE;

public class ClassCostPlugin extends Transform implements Plugin<Project> {

    private static final String TAG = "test_asm ::::::::::::::::::::";
    private static int api = Opcodes.ASM6;
    private Project project;
    private String[] handleClass = new String[]{
            "AppController", "ApplicationLike", "Application"
    };

    private HandleClassList handleClassList;

    @Override
    public void apply(Project project) {
        this.project = project;
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.registerTransform(this);
        handleClassList = project.getExtensions().create("class_cost_filter", HandleClassList.class);
    }

    @Override
    public String getName() {
        return "classcost_asm";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        for (TransformInput transformInput : transformInvocation.getInputs()) {
            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                handleDirectory(directoryInput);
                //作为下一个输出
                File dest = transformInvocation.getOutputProvider().getContentLocation(directoryInput.getName(),
                        directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);
                FileUtils.copyDirectory(directoryInput.getFile(), dest);
            }

            for (JarInput jarInput : transformInput.getJarInputs()) {
                handleJar(jarInput);
                //作为下一个输出
                File dest = transformInvocation.getOutputProvider().getContentLocation(jarInput.getName(),
                        jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
                FileUtils.copyFile(jarInput.getFile(), dest);
            }
        }
    }

    private void handleDirectory(DirectoryInput directoryInput) throws IOException {
        ArrayList<String> fileList = new ArrayList<>();
        getFile(directoryInput.getFile(), fileList);
        for (String fileName : fileList) {
            if (handleClassList.isHandleClass(fileName)) {
                String className = fileName.replace(".class", "")
                        .replaceAll("/", ".");
                File origin = new File(fileName);
                ClassReader classReader = new ClassReader(IOUtils.toByteArray(new FileInputStream(origin)));
                ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                MethodCostClassVisitor methodCostClassVisitor = new MethodCostClassVisitor(project, className, api, classWriter);
                //不是用expand_frame，避免transform过程中classNotFound问题
                //https://stackoverflow.com/questions/11292701/error-while-instrumenting-class-files-asm-classwriter-getcommonsuperclass
                classReader.accept(methodCostClassVisitor, ClassReader.SKIP_FRAMES);
                byte[] code = classWriter.toByteArray();
                File tmp = new File(fileName + ".tmp");
                FileOutputStream fileOutputStream = new FileOutputStream(tmp);
                fileOutputStream.write(code);
                origin.delete();
                tmp.renameTo(origin);
            }
        }
    }

    private void getFile(File dir, ArrayList<String> list) {
        if (dir.isDirectory()) {
            for (String file : dir.list()) {
                File subFile = new File(dir.getAbsolutePath() + File.separator + file);
                if (subFile.isDirectory()) {
                    getFile(subFile, list);
                } else {
                    list.add(subFile.getAbsolutePath());
                }
            }
        } else {
            list.add(dir.getAbsolutePath());
        }
    }

    private void handleJar(JarInput jarInput) throws IOException {
        JarFile jarFile = new JarFile(jarInput.getFile());
        Enumeration<JarEntry> enumeration = jarFile.entries();
        File tmpFile = new File(jarInput.getFile().getParent() + File.pathSeparator + "classes_temp.jar");
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile));
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = enumeration.nextElement();
            String entryName = jarEntry.getName();
            ZipEntry zipEntry = new ZipEntry(entryName);
            InputStream inputStream = jarFile.getInputStream(jarEntry);

            if (handleClassList.isHandleClass(entryName)) {
                String className = entryName.replace(".class", "")
                        .replaceAll("/", ".");
                jarOutputStream.putNextEntry(zipEntry);
                ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream));
                ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                MethodCostClassVisitor classVisitor = new MethodCostClassVisitor(project, className, api, classWriter);
                classReader.accept(classVisitor, ClassReader.SKIP_FRAMES);
                byte[] code = classWriter.toByteArray();
                jarOutputStream.write(code);
                jarOutputStream.closeEntry();
            } else {
                jarOutputStream.putNextEntry(zipEntry);
                jarOutputStream.write(IOUtils.toByteArray(inputStream));
            }
        }
        jarOutputStream.close();
        jarFile.close();
        tmpFile.renameTo(jarInput.getFile());
    }

    public static class MethodCostClassVisitor extends ClassVisitor {

        Project project;

        private String className;

        public MethodCostClassVisitor(Project project, String className, int api, ClassVisitor cv) {
            super(api, cv);
            this.project = project;
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("<init>")) {
                return methodVisitor;
            }
            return new MethodCostMethodVisitor(project, className, name,
                    Type.getArgumentTypes(desc).length, api, methodVisitor);
        }


        @Override
        public void visitOuterClass(String owner, String name, String desc) {
            super.visitOuterClass(owner, name, desc);
        }
    }

    public static class MethodCostMethodVisitor extends MethodVisitor {

        private Project project;

        private String methodName;

        private String className;

        private int argc;

        private int maxStack;

        public MethodCostMethodVisitor(Project project, String className, String methodName, int argc, int api, MethodVisitor mv) {
            super(api, mv);
            this.methodName = methodName;
            String[] classNamePath = className.split("\\.");
            this.className = classNamePath[classNamePath.length-1];
            project.getLogger().error("*************: " + this.className);
            this.argc = argc; //参数的个数
            this.maxStack = argc + 1; //加上this
            this.project = project;
        }

        @Override
        public void visitCode() {
            /**
             * 函数开始的地方，加上插桩；
             * 局部变量表从0（this）开始，在局部变量表插入保存一个局部变量，currentMills
             */
            recordCurrentTime(mv, argc + 1);
            super.visitCode();
        }

        @Override
        public void visitInsn(int opcode) {
            //返回的地方，加上插桩
            if ((opcode <= Opcodes.RETURN && opcode >= Opcodes.IRETURN) || (opcode == ATHROW)) {
                recordDuration(mv, className + "." + className + "." + methodName, argc + 1);
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            /**
             * 函数最开始占用了一个局部变量
             * 所以后续的局部变量表操作都加一
             */
            if (var >= argc + 1) {
                super.visitVarInsn(opcode, var + 1);
            } else {
                super.visitVarInsn(opcode, var);
            }
            /**
             * 记录已经使用的局部变量表的最大位置
             */
            maxStack = Math.max(var + 1, maxStack);
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            if (var > argc + 1) {
                super.visitIincInsn(var + 1, increment);
            } else {
                super.visitVarInsn(var, increment);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (name.contains("<init>")) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
            /**
             * 在最大位置的下一个位置保存我们自己的局部变量
             */
            recordCurrentTime(mv, maxStack + 1);
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (owner.contains("/")) {
                String[] paths = owner.split("/");
                recordDuration(mv, className + "." + paths[paths.length - 1] + "." + name, maxStack + 1);
            } else {
                recordDuration(mv, className + "." + owner + "." + name, maxStack + 1);
            }
        }

        private void recordCurrentTime(MethodVisitor mv, int localIndex) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
            mv.visitVarInsn(LSTORE, localIndex);
        }

        private void recordDuration(MethodVisitor mv, String methodName, int localIndex) {
            //此时栈顶是Thread对象引用，正好可以作为下一条invokeVirtual指令的操作数
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            //执行完getName,此时栈顶是name
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;", false);
            //将栈顶name弹出保存到局部变量表
            mv.visitVarInsn(ASTORE, localIndex + 1);

            mv.visitLdcInsn(methodName); //获取常量，此时常量保存在栈顶
            mv.visitVarInsn(ASTORE, localIndex + 2); //将栈顶的数据保存到局部变量表


            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
            //这个如果用ISTORE就溢出了，Long是64位的。
            mv.visitVarInsn(LSTORE, localIndex + 3);

            //将addData参数放到栈顶
            mv.visitVarInsn(ALOAD, localIndex + 1);
            mv.visitVarInsn(ALOAD, localIndex + 2);
            mv.visitVarInsn(LLOAD, localIndex);
            mv.visitVarInsn(LLOAD, localIndex + 3);
            mv.visitMethodInsn(INVOKESTATIC, "com/duwo/methodcost/ClassCostManager", "addData", "(Ljava/lang/String;Ljava/lang/String;JJ)V", false);
        }
    }
}

