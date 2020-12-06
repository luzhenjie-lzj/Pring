package top.lings.pring;

import javafx.application.Application;
import top.lings.pring.annotation.Autowired;
import top.lings.pring.annotation.Component;
import top.lings.pring.annotation.ComponentScan;
import top.lings.pring.annotation.Controller;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class PringApplication extends Application {

    private final Map<Class<?>, Object> container = new HashMap<>();

    @Override
    public void init() throws Exception {
        initializeBeans();
    }

    /**
     * scan specified packages then initialize beans.
     */
    private void initializeBeans() throws Exception {
        ComponentScan annotation = getClass().getAnnotation(ComponentScan.class);
        if (annotation == null) {
            throw new Exception("ComponentScan annotation don't set in PringApplication");
        }
        String[] valuesOfComponentScan = annotation.value();
        String fileName = getClass().getResource(getClass().getSimpleName() + ".class").getFile();
        for (String value : valuesOfComponentScan) {
            String temp = value.replaceAll("\\.", "/");
            if (fileName.contains(temp)) {
                fileName = fileName.substring(0, fileName.indexOf(temp) + temp.length());
                File file = new File(fileName);
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
     * instantiate beans recursively
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
                boolean instantiate = false;
                for (Annotation annotation : clazz.getAnnotations()) {
                    if (annotation.annotationType() == Component.class || annotation.annotationType() == Controller.class) {
                        instantiate = true;
                    }
                }
                if (instantiate) {
                    container.put(clazz, clazz.getDeclaredConstructor().newInstance());
                }
            }
        } else {
            for (File subFile : Objects.requireNonNull(file.listFiles())) {
                if (subFile.isDirectory()) {
                    instantiateBeans(packageName + "." + subFile.getName(), subFile);
                } else {
                    instantiateBeans(packageName, subFile);
                }
            }
        }
    }

    /**
     * fill bean with attribute variable which is annotated by @Autowired
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