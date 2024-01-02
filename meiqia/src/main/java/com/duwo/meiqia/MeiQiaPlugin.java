package com.duwo.meiqia;

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

import javax.swing.JProgressBar;

public class MeiQiaPlugin extends Transform implements Plugin<Project> {

    private Project project;

    private static int api = Opcodes.ASM6;

    @Override
    public void apply(Project project) {
        this.project = project;
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.registerTransform(this);
    }

    @Override
    public String getName() {
        return "meiqia_asm";
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
                if (jarInput.getName().equals("com.meiqia:meiqiasdk:3.6.6")) {
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

                        if (entryName.endsWith("MQConversationActivity.class")) {
                            project.getLogger().error("============处理美洽权限问题==========");
                            jarOutputStream.putNextEntry(zipEntry);
                            ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream));
                            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                            MeiQiaClassVisitor classVisitor = new MeiQiaClassVisitor(project, api, classWriter);
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

    public static class MeiQiaClassVisitor extends ClassVisitor {

        Project project;

        public MeiQiaClassVisitor(Project project, int api, ClassVisitor cv) {
            super(api, cv);
            this.project = project;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            if ("checkAudioPermission".equals(name)
                    || "checkStorageAndCameraPermission".equals(name)
                    || "checkStoragePermission".equals(name) ) {
                return new MeiQiaPermissionMethodVisitor(project, api, mv, name);
            }
            return mv;
        }
    }

    public static class MeiQiaPermissionMethodVisitor extends MethodVisitor {

        private Project project;

        private String name;

        public MeiQiaPermissionMethodVisitor(Project project, int api, MethodVisitor mv, String name) {
            super(api, mv);
            this.project = project;
            this.name = name;
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
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (name.equals("requestPermissions")) {
                if (this.name.equals("checkAudioPermission")) {
                    mv.visitInsn(Opcodes.POP);
                    mv.visitInsn(Opcodes.POP);
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitInsn(Opcodes.ICONST_2);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/duwo/meiqia/MeiqiaUtil", "onPermissionRequest", "(Landroid/app/Activity;II)V", false);
                } else if (this.name.equals("checkStorageAndCameraPermission")) {
                    mv.visitInsn(Opcodes.POP);
                    mv.visitInsn(Opcodes.POP);
                    mv.visitInsn(Opcodes.ICONST_2);
                    mv.visitVarInsn(Opcodes.ILOAD, 1);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/duwo/meiqia/MeiqiaUtil", "onPermissionRequest", "(Landroid/app/Activity;II)V", false);
                } else if (this.name.equals("checkStoragePermission")) {
                    mv.visitInsn(Opcodes.POP);
                    mv.visitInsn(Opcodes.POP);
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/duwo/meiqia/MeiqiaUtil", "onPermissionRequest", "(Landroid/app/Activity;II)V", false);
                }
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }

}
