package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.File;
import java.io.FileFilter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public class Pack {


    private static String module = ":test";

    /**
     * assignModuleSource
     * assign ModuleSource from package
     */
    public static void assignModuleSource(Map<Class, String> moduleSource, List<String> blockTypes ) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        List<Class<?>> classesList = getClassesFromPackage("test");

        for(Class blockClass : classesList){
            Annotation annotation = blockClass.getAnnotation(BlockType.class);
            if(annotation == null) continue;
            Class<? extends Annotation> blockType = annotation.annotationType();
            String blockTypeName = (String)blockType.getDeclaredMethod("type").invoke(annotation);

            for(String blockTypeStr :blockTypes){
                if(blockTypeName.equals(blockTypeStr)){
                    moduleSource.put(blockClass, module);
                    break;
                }
            }
        }

    }


    /**
     * get classes from package
     */
    public static List<Class<?>> getClassesFromPackage(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        boolean recursive = true;
        String packageDirName = packageName.replace('.', '/');
        Enumeration<URL> dirs;

        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            while (dirs.hasMoreElements()) {

                URL url = dirs.nextElement();
                String protocol = url.getProtocol();

                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    findClassInPackageByFile(packageName, filePath, recursive, classes);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return classes;
    }

    /**
     * get all the classes under the corresponding package path
     */
    public static void findClassInPackageByFile(String packageName, String filePath, final boolean recursive, List<Class<?>> classes) {
        File dir = new File(filePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] dirFiles = dir.listFiles(new FileFilter() {

            public boolean accept(File file) {
                boolean acceptDir = recursive && file.isDirectory();
                boolean acceptClass = file.getName().endsWith("class");
                return acceptDir || acceptClass;
            }
        });

        for (File file : dirFiles) {
            if (file.isDirectory()) {
                findClassInPackageByFile(packageName + "." + file.getName(), file.getAbsolutePath(), recursive, classes);
            } else {
                String className = file.getName().substring(0, file.getName().length() - 6);
                try {
                    classes.add(Thread.currentThread().getContextClassLoader().loadClass(packageName + "." + className));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }





}
