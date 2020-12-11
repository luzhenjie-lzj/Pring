package top.lings.pring;

import javafx.application.Application;
import top.lings.pring.annotation.Autowired;
import top.lings.pring.annotation.Component;
import top.lings.pring.annotation.ComponentScan;
import top.lings.pring.annotation.Controller;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public abstract class PringApplication extends Application {

    private final Map<Class<?>, Object> container = new HashMap<>();

    @Override
    public void init() throws Exception {
        initializeBeans();
    }

    /**
     * Scan specified packages then initialize beans.
     */
    private void initializeBeans() throws Exception {
        ComponentScan annotation = getClass().getAnnotation(ComponentScan.class);
        if (annotation == null) {
            throw new Exception("ComponentScan annotation don't set in PringApplication");
        }
        String[] valuesOfComponentScan = annotation.value();
        String fileName = getClass().getResource(getClass().getSimpleName() + ".class").getFile();
        if (fileName.contains(".jar")) {
            instantiateBeansInJar(fileName, valuesOfComponentScan);
        } else {
            instantiateBeansNotInJar(fileName, valuesOfComponentScan);
        }
    }

    /**
     * If classes are in jar,then use this
     *
     * @param fileName              PringApplication class file name
     * @param valuesOfComponentScan value of @ComponentScan
     */
    private void instantiateBeansInJar(String fileName, String[] valuesOfComponentScan) throws Exception {
        fileName = fileName.substring(0, fileName.indexOf(".jar") + 4).replaceAll("file:/", "");
        JarFile jarFile = new JarFile(new File(fileName));
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            for (String value : valuesOfComponentScan) {
                value = value.replaceAll("\\.", "/");
                if (entry.getName().contains(value) && entry.getName().endsWith(".class")) {
                    String name = entry.getName();  //name will prefix with the the folder in the jar package
                    name = name.substring(0, name.lastIndexOf(".class"));
                    if (name.contains("/")) {
                        name = name.replaceAll("/", ".");
                    }
                    if (name.contains("\\")) {
                        name = name.replaceAll("\\\\", ".");
                    }
                    Class<?> clazz = Class.forName(name);
                    if (instantiateBeanOrNot(clazz)) {
                        instantiateBean(clazz);
                    }
                }
            }
        }
    }

    /**
     * If classes are not in jar,then use this
     *
     * @param fileName              PringApplication class file name
     * @param valuesOfComponentScan value of @ComponentScan
     */
    private void instantiateBeansNotInJar(String fileName, String[] valuesOfComponentScan) throws Exception {
        for (String value : valuesOfComponentScan) {
            String rootFolderName = value;
            if (rootFolderName.contains(".")) {
                rootFolderName = "/" + value.substring(0, value.indexOf(".")) + "/";
            } else {
                rootFolderName = "/" + rootFolderName + "/";
            }
            String followingFolder = value.substring(value.indexOf(".") + 1).replaceAll("\\.", "/");
            if (fileName.contains(rootFolderName)) {
                StringBuilder fileNameBuilder = new StringBuilder(fileName.substring(0, fileName.indexOf(rootFolderName) + rootFolderName.length()));
                if (!followingFolder.equals(value)) {
                    //which means value contains "."
                    fileNameBuilder.append(followingFolder);
                }
                File file = new File(fileNameBuilder.toString());
                if (!file.exists()) {
                    throw new Exception("Invalid package name set in component scan:" + value);
                }
                if (file.isFile()) {
                    throw new Exception("Expect directory but file:" + value);
                } else {
                    //instantiate beans
                    instantiateBeans(value, file);
                    //fill beans
                    fillBeans();
                }
            } else {
                throw new Exception("Invalid package name set in component scan:" + value);
            }
        }
    }

    /**
     * Instantiate beans recursively
     *
     * @param packageName package name of package in which file is stored,it is
     *                    expressed using "." instead of file-separator
     * @param file        scanned file
     */
    private void instantiateBeans(String packageName, File file) throws Exception {
        if (file.isFile()) {
            String fileName = file.getName();
            if (fileName.endsWith("class")) {
                String className = packageName + "." + fileName.substring(0, fileName.indexOf(".class"));
                Class<?> clazz = Class.forName(className);
                if (instantiateBeanOrNot(clazz)) {
                    instantiateBean(clazz);
                }
            }
        } else {
            for (File subFile : Objects.requireNonNull(file.listFiles())) {
                if (subFile.isDirectory()) {
                    instantiateBeans(packageName + "." + subFile.getName().replaceAll("!", ""), subFile);
                } else {
                    instantiateBeans(packageName, subFile);
                }
            }
        }
    }

    /**
     * Determine whether to instantiate object based on annotation
     *
     * @param clazz class of to be instantiated object
     * @return true:instantiate  false:don't instantiate
     */
    private boolean instantiateBeanOrNot(Class<?> clazz) {
        boolean instantiate = false;
        for (Annotation annotation : clazz.getAnnotations()) {
            if (annotation.annotationType() == Component.class || annotation.annotationType() == Controller.class) {
                instantiate = true;
            }
        }
        return instantiate;
    }

    /**
     * Instantiate specific type of object
     *
     * @param clazz class of object
     */
    private void instantiateBean(Class<?> clazz) throws Exception {
        Constructor<?> annotatedConstructor = null;
        for (Constructor<?> constructor : clazz.getConstructors()) {
            for (Annotation annotation : constructor.getAnnotations()) {
                if (annotation.annotationType() == Autowired.class) {
                    annotatedConstructor = constructor;
                    break;
                }
            }
            if (annotatedConstructor != null) {
                break;
            }
        }
        if (container.get(clazz) == null) {
            if (annotatedConstructor == null) {
                container.put(clazz, clazz.getDeclaredConstructor().newInstance());
            } else {
                Class<?>[] classes = annotatedConstructor.getParameterTypes();
                Object[] parameters = new Object[classes.length];
                for (int i = 0; i < classes.length; i++) {
                    instantiateBean(classes[i]);
                    parameters[i] = container.get(classes[i]);
                }
                container.put(clazz, annotatedConstructor.newInstance(parameters));
            }
        }
    }

    /**
     * Fill bean with attribute variable which is annotated by @Autowired
     */
    private void fillBeans() {
        container.forEach((clazz, object) -> {
            for (Field declaredField : clazz.getDeclaredFields()) {
                Annotation[] annotations = declaredField.getDeclaredAnnotations();
                boolean fill = false;
                for (Annotation annotation : annotations) {
                    if (annotation.annotationType() == Autowired.class) {
                        fill = true;
                    }
                }
                if (fill) {
                    declaredField.setAccessible(true);
                    Object value = container.get(declaredField.getType());
                    try {
                        if (value == null) {
                            throw new Exception("Can't find instance of " + declaredField.getType());
                        }
                        declaredField.set(object, value);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        return (T) container.get(clazz);
    }
}