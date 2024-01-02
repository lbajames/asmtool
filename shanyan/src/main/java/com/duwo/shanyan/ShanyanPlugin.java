package com.duwo.shanyan;

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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.impldep.org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class ShanyanPlugin extends Transform implements Plugin<Project> {

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
        return "shanyan_asm";
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
            for (JarInput jarInput : transformInput.getJarInputs()) {
                if (jarInput.getName().equals("com.xckj:shanyan:2.3.1.6")) {
                    project.getLogger().error("test_asm :::::::::::::::::::: " + jarInput.getName());
                    String jarName = jarInput.getName();
                    String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
                    if (jarName.endsWith(".jar")) {
                        jarName = jarName.substring(0, jarName.length() - 4);
                    }
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

                        if (isShanyanActivity(entryName)) {
                            project.getLogger().error("============处理闪验返回问题==========");
                            jarOutputStream.putNextEntry(zipEntry);
                            ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream));
                            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                            ShanyanClassVisitor classVisitor = new ShanyanClassVisitor(project, api, classWriter);
                            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
                            byte[] code = classWriter.toByteArray();
                            jarOutputStream.write(code);
                        } else {
                            jarOutputStream.putNextEntry(zipEntry);
                            jarOutputStream.write(IOUtils.toByteArray(inputStream));
                        }
                        jarOutputStream.closeEntry();
                    }
                    jarOutputStream.close();
                    File dest = transformInvocation.getOutputProvider().
                            getContentLocation(jarName + md5Name, jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
                    FileUtils.copyFile(tmpFile, dest);
                    tmpFile.delete();
                    jarFile.close();
                } else {
                    File dest = transformInvocation.getOutputProvider().
                            getContentLocation(jarInput.getName(), jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
                    FileUtils.copyFile(jarInput.getFile(), dest);
                }
            }

            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                File dest = transformInvocation.getOutputProvider()
                        .getContentLocation(directoryInput.getName(),
                                directoryInput.getContentTypes(), directoryInput.getScopes(),
                                Format.DIRECTORY);
                FileUtils.copyDirectory(directoryInput.getFile(), dest);
            }
        }

    }

    private boolean isShanyanActivity(String className) {
        return className.endsWith("ShanYanOneKeyActivity.class")
                || className.endsWith("CmccLoginActivity.class");
    }

    public static class ShanyanClassVisitor extends ClassVisitor {

        Project project;

        public ShanyanClassVisitor(Project project, int api, ClassVisitor cv) {
            super(api, cv);
            this.project = project;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            if ("onKeyDown".equals(name)) {
                return new ShanyanKeyDownMethodVisitor(project, api, mv);
            }
            return mv;
        }
    }

    public static class ShanyanKeyDownMethodVisitor extends MethodVisitor {

        private Project project;

        private int jumpIndex;

        private int opIndex;

        private Label gotoLabel;

        public ShanyanKeyDownMethodVisitor(Project project, int api, MethodVisitor mv) {
            super(api, mv);
            this.project = project;
        }

        @Override
        public void visitCode() {
            super.visitCode();
        }

        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            super.visitJumpInsn(opcode, label);
            project.getLogger().error(opcode + " " + label.toString());
            if (opcode == Opcodes.IFNE && jumpIndex == 0) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/duwo/shanyan/ShanyanUtil", "getIsTab", "()I", false);
                gotoLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, gotoLabel);
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IRETURN);
                jumpIndex++;
            }
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            project.getLogger().error(opcode + " " + var);
            if (opcode == Opcodes.ALOAD && var == 0 && opIndex == 0) {
                mv.visitLabel(gotoLabel);
                opIndex++;
            }
            super.visitVarInsn(opcode, var);
        }


    }

}
