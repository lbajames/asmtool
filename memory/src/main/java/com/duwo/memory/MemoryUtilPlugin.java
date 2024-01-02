package com.duwo.memory;

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
import static org.gradle.internal.impldep.org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * @author liuxin
 * @Date 2021/7/7
 * @Description
 **/
public class MemoryUtilPlugin extends Transform implements Plugin<Project> {
    private static final String TAG = "test_asm ::::::::::::::::::::";
    private static int api = Opcodes.ASM6;
    private Project project;

    @Override
    public void apply(Project project) {
        this.project = project;
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.registerTransform(this);
    }

    @Override
    public String getName() {
        return "mem_util";
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
            String className = isHandleClass(fileName);
            if (className != null && className.length() > 1) {
                File origin = new File(fileName);
                ClassReader classReader = new ClassReader(IOUtils.toByteArray(new FileInputStream(origin)));
                ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                MemoryUtilClassVisitor memoryUtilClassVisitor = new MemoryUtilClassVisitor(project, className, api, classWriter);
                //不是用expand_frame，避免transform过程中classNotFound问题
                //https://stackoverflow.com/questions/11292701/error-while-instrumenting-class-files-asm-classwriter-getcommonsuperclass
                classReader.accept(memoryUtilClassVisitor, ClassReader.SKIP_FRAMES);
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

    private String isHandleClass(String fileName) {
        if (fileName.endsWith(".class")) {
            if (fileName.contains("/StatisticImageView.class")) {
                project.getLogger().error("*********************:  " + fileName);
                return fileName.replace(".class", "")
                        .replaceAll("/", ".");
            }
        }
        return "";
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

            String className = isHandleClass(entryName);
            if (className != null && className.length() > 1) {
                jarOutputStream.putNextEntry(zipEntry);
                ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream));
                ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                MemoryUtilClassVisitor classVisitor = new MemoryUtilClassVisitor(project, className, api, classWriter);
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

    public static class MemoryUtilClassVisitor extends ClassVisitor {

        Project project;

        private String className;

        public MemoryUtilClassVisitor(Project project, String className, int api, ClassVisitor cv) {
            super(api, cv);
            this.project = project;
            this.className = className;
            project.getLogger().error("********************: " + className);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("onDraw")) {
                return new MemoryUtilMethodVisitor(project, className, name,
                        Type.getArgumentTypes(desc).length, api, methodVisitor);
            }
            return methodVisitor;
        }


        @Override
        public void visitOuterClass(String owner, String name, String desc) {
            super.visitOuterClass(owner, name, desc);
        }
    }

    public static class MemoryUtilMethodVisitor extends MethodVisitor {

        private Project project;

        private String methodName;

        private String className;

        public MemoryUtilMethodVisitor(Project project, String className, String methodName, int argc, int api, MethodVisitor mv) {
            super(api, mv);
            this.methodName = methodName;
            this.className = className;
            this.project = project;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESTATIC, "com/duwo/memory/MemoryUtil", "checkImageOnDraw", "(Landroid/view/View;)V", false);
        }
    }
}
