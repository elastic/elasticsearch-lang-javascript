package org.elasticsearch.script.javascript.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.elasticsearch.script.javascript.JavascriptNashornScriptEngineService;

public class NashornScriptValueConverter {
	private static Class<?> CLASS_SCRIPT_OBJECT;
    private static Method METHOD_SCRIPT_OBJECT_IS_ARRAY;
    private static Method METHOD_SCRIPT_OBJECT_ENTRY_SET;
    private static Method METHOD_SCRIPT_OBJECT_SIZE;

    private static Method METHOD_ARRAY_ITERATOR;

    private static Object EMPTY;
    private static Object UNDEFINED;

    private NashornScriptValueConverter() {
    }
    public static void setup() {
        try {
            // Use introspection to avoid accessing nashorn specific classes.
            // This way we can compile with jdk6
        	ClassLoader cl = JavascriptNashornScriptEngineService.class.getClassLoader();
        	CLASS_SCRIPT_OBJECT = cl.loadClass("jdk.nashorn.internal.runtime.ScriptObject");
            METHOD_SCRIPT_OBJECT_IS_ARRAY = CLASS_SCRIPT_OBJECT.getMethod("isArray");
            METHOD_SCRIPT_OBJECT_ENTRY_SET = CLASS_SCRIPT_OBJECT.getMethod("entrySet");
            METHOD_SCRIPT_OBJECT_SIZE = CLASS_SCRIPT_OBJECT.getMethod("size");

            Class<?> arrayLikeIteratorClass = cl.loadClass("jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator");
            METHOD_ARRAY_ITERATOR = arrayLikeIteratorClass.getDeclaredMethod("arrayLikeIterator", Object.class);

            Class<?> undefinedClass = cl.loadClass("jdk.nashorn.internal.runtime.Undefined");
            EMPTY = undefinedClass.getDeclaredMethod("getEmpty").invoke(null);
            UNDEFINED = undefinedClass.getDeclaredMethod("getUndefined").invoke(null);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    public static Object unwrapValue(Object value) {
        if (value == null || value == UNDEFINED || value == EMPTY) {
            return null;
        } else if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            ArrayList<Object> list = new ArrayList<Object>(array.length);
            for (int i = 0; i < array.length; i++) {
                list.add(unwrapValue(array[i]));
            }
            value = list;
        } else if (CLASS_SCRIPT_OBJECT.isInstance(value)) {
            try {
                if ((Boolean)METHOD_SCRIPT_OBJECT_IS_ARRAY.invoke(value)) {
                    ArrayList<Object> list = new ArrayList<Object>();
                    Iterator<?> it = (Iterator<?>) METHOD_ARRAY_ITERATOR.invoke(null, value);
                    while (it.hasNext()) {
                        list.add(unwrapValue(it.next()));
                    }
                    value = list;
                } else {
                    Map<Object, Object> copyMap = new HashMap<Object, Object>((Integer)METHOD_SCRIPT_OBJECT_SIZE.invoke(value));
                    for (Map.Entry<Object,Object> entry : (Iterable<Map.Entry<Object,Object>>) METHOD_SCRIPT_OBJECT_ENTRY_SET.invoke(value)) {
                        copyMap.put(entry.getKey(), unwrapValue(entry.getValue()));
                    }
                    value = copyMap;
                }
            } catch(InvocationTargetException ite) {
                throw new RuntimeException(ite);
            } catch(IllegalAccessException iae) {
                throw new RuntimeException(iae);
            }
        } else if (value instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            Map<Object, Object> copyMap = new HashMap<Object, Object>(map.size());
            for (Map.Entry<Object,Object> entry : map.entrySet()) {
                copyMap.put(entry.getKey(), unwrapValue(entry.getValue()));
            }
            value = copyMap;
        }
        return value;
    }

    public static Object wrapValue(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Collection) {
            Collection<Object> collection = (Collection<Object>) value;
            Object[] array = new Object[collection.size()];
            int index = 0;
            for (Object obj : collection) {
                array[index++] = wrapValue(obj);
            }
            value = array;
        }
        return value;
    }
}