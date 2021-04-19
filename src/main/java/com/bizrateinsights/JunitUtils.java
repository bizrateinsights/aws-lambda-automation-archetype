package com.bizrateinsights;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Log4j2
@UtilityClass
public class JunitUtils {

    /**
     * Gets all test classes with "_Test" in the name, similar to how pytest works in a python environment.
     *
     * @param pkg = base package that contains all test classes
     */
    public List<String> getJunitTestClasses(String pkg){
        Reflections reflections = new Reflections(pkg,
                new SubTypesScanner(false));
        Set<Class<? extends Object>> allClasses =
                reflections.getSubTypesOf(Object.class);

        List<String> allJunitClasses = new ArrayList<>();
        for(Class<? extends Object> clazz : allClasses){
            try {
                if (clazz.getCanonicalName().contains("_Test")) {
                    allJunitClasses.add(clazz.getCanonicalName());
                }
            }catch (Exception e){
                //Eat exception. Not all classes support getCanonicalName(), but any and all junit classes should.
            }
        }

        return allJunitClasses;
    }

    /**
     * Gets all tests annotated with @Test with junit
     * @param fullClassName = full class name (I.E. com.automationlambda.xyz)
     */
    @SneakyThrows
    public List<String> getTestsInJunitClass(String fullClassName){
        Class<?> clazz = Class.forName(fullClassName);
        Method[] allMethods = clazz.getDeclaredMethods();

        List<String> testMethods = new ArrayList<>();
        for (final Method method : allMethods)
        {
            if (method.isAnnotationPresent(Test.class) && !method.isAnnotationPresent(Ignore.class))
            {
                testMethods.add(method.getName());
            }
        }
        return testMethods;
    }

    /**
     * Get test cases with a method name that matches the filter string
     * @param fullClassName = full class name (I.E. com.automationlambda.xyz)
     * @param filter = filter string
     * @return
     */
    @SneakyThrows
    public List<String> getTestsInJunitClass(String fullClassName, String filter){
        Class<?> clazz = Class.forName(fullClassName);
        Method[] allMethods = clazz.getDeclaredMethods();

        List<String> testMethods = new ArrayList<>();
        for (final Method method : allMethods)
        {
            if (method.isAnnotationPresent(Test.class) && !method.isAnnotationPresent(Ignore.class) && method.getName().contains(filter))
            {
                testMethods.add(method.getName());
            }
        }
        return testMethods;
    }

    @SneakyThrows
    public Result runJunitTest(String clazz, String testMethod) {
        BlockJUnit4ClassRunner runner = new BlockJUnit4ClassRunner(Class.forName(clazz));
        runner.filter(new MethodFilter(testMethod));
        return new JUnitCore().run(runner);
    }

}

