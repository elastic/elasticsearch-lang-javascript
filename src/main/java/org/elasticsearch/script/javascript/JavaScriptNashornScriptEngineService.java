/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.script.javascript;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Scorer;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

public class JavaScriptNashornScriptEngineService extends AbstractComponent
        implements ScriptEngineService {

    public static class NashornScriptValueConverter {
        private static Class<?> CLASS_SCRIPT_OBJECT;
        private static Method METHOD_SCRIPT_OBJECT_IS_ARRAY;
        private static Method METHOD_SCRIPT_OBJECT_ENTRY_SET;
        private static Method METHOD_SCRIPT_OBJECT_SIZE;

        private static Class<?> CLASS_ARRAY_LIKE_ITERATOR;
        private static Method METHOD_ARRAY_ITERATOR;

        private NashornScriptValueConverter() {
        }
        static void setup() {
            try {
                // Use introspection to avoid accessing nashorn specific classes.
                // This way we can compile with jdk6
                CLASS_SCRIPT_OBJECT = JavaScriptNashornScriptEngineService.class.getClassLoader().loadClass("jdk.nashorn.internal.runtime.ScriptObject");
                METHOD_SCRIPT_OBJECT_IS_ARRAY = CLASS_SCRIPT_OBJECT.getMethod("isArray");
                METHOD_SCRIPT_OBJECT_ENTRY_SET = CLASS_SCRIPT_OBJECT.getMethod("entrySet");
                METHOD_SCRIPT_OBJECT_SIZE = CLASS_SCRIPT_OBJECT.getMethod("size");

                CLASS_ARRAY_LIKE_ITERATOR = JavaScriptNashornScriptEngineService.class.getClassLoader().loadClass("jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator");
                METHOD_ARRAY_ITERATOR = CLASS_ARRAY_LIKE_ITERATOR.getDeclaredMethod("arrayLikeIterator", Object.class);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        public static Object unwrapValue(Object value) {
            if (value == null) {
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
                        Iterator<Object> it = (Iterator<Object>) METHOD_ARRAY_ITERATOR.invoke(null, value);
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

    private ScriptEngineManager m;
    private ScriptEngine nashorn;

    @Inject
    public JavaScriptNashornScriptEngineService(Settings settings) {
        super(settings);
        m = new ScriptEngineManager();
        nashorn = m.getEngineByName("nashorn");
        NashornScriptValueConverter.setup();
    }


    @Override
    public Object compile(String script) {
        try {
            Compilable compilableObj = (Compilable) nashorn;
            return compilableObj.compile(script);
        } catch (ScriptException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ExecutableScript executable(Object compiledScript, Map<String, Object> vars) {
        ScriptContext context = createContextWithVars(null, vars);
        return new NashornExecutableScript((CompiledScript) compiledScript, context);
    }

    private ScriptContext createContextWithVars(SearchLookup lookup, Map<String, Object> vars) {
        ScriptContext newContext = new SimpleScriptContext();
        if (lookup == null && vars == null) {
            return newContext;
        }
        Bindings newBindings = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
        if (lookup != null) {
            for (Map.Entry<String, Object> entry : lookup.asMap().entrySet()) {
                newBindings.put(entry.getKey(), NashornScriptValueConverter.wrapValue(entry.getValue()));
            }
        }
        if (vars != null) {
            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                // we need to wrap the List into Object[] because engine doesn't recognize List
                newBindings.put(entry.getKey(), NashornScriptValueConverter.wrapValue(entry.getValue()));
            }
        }
        return newContext;
    }

    @Override
    public Object execute(Object compiledScript, Map<String, Object> vars) {
        try {
            ScriptContext context = createContextWithVars(null, vars);
            return NashornScriptValueConverter.unwrapValue(((CompiledScript) compiledScript).eval(context));
        } catch (ScriptException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Object unwrap(Object value) {
        return NashornScriptValueConverter.unwrapValue(value);
    }

    @Override
    public void close() {

    }

    @Override
    public String[] types() {
        return new String[]{"js", "javascript"};
    }

    @Override
    public String[] extensions() {
        return new String[]{"js"};
    }

    @Override
    public SearchScript search(Object compiledScript, SearchLookup lookup,
            @Nullable Map<String, Object> vars) {
        ScriptContext context = createContextWithVars(lookup, vars);
        return new NashornSearchScript((CompiledScript) compiledScript, context, lookup);
    }

    public static class NashornExecutableScript implements ExecutableScript {

        private final CompiledScript script;

        private final ScriptContext context;
        
        public NashornExecutableScript(CompiledScript script, ScriptContext context) {
            this.script = script;
            this.context = context;
        }

        @Override
        public Object run() {
            try {
                return this.unwrap(script.eval(this.context));
            } catch (ScriptException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public void setNextVar(String name, Object value) {
            this.context.getBindings(ScriptContext.ENGINE_SCOPE).put(name, value);
        }

        @Override
        public Object unwrap(Object value) {
            return NashornScriptValueConverter.unwrapValue(value);
        }
    }

    public static class NashornSearchScript implements SearchScript {
        private final CompiledScript script;

        private final ScriptContext context;

        private final SearchLookup lookup;
        
        public NashornSearchScript(CompiledScript script, ScriptContext context, SearchLookup lookup) {
            this.script = script;
            this.context = context;
            this.lookup = lookup;
        }
        
        @Override
        public Object run() {
            try {
                return this.unwrap(script.eval(this.context));
            } catch (ScriptException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public Object unwrap(Object value) {
            return NashornScriptValueConverter.unwrapValue(value);
        }

        @Override
        public void setNextReader(AtomicReaderContext reader) {
            lookup.setNextReader(reader);
        }

        @Override
        public void setScorer(Scorer scorer) {
            lookup.setScorer(scorer);
        }

        @Override
        public void setNextDocId(int doc) {
            lookup.setNextDocId(doc);
        }

        @Override
        public void setNextSource(Map<String, Object> source) {
            lookup.source().setNextSource(source);
        }

        @Override
        public void setNextScore(float score) {
            this.context.getBindings(ScriptContext.ENGINE_SCOPE).put("_score", score);
        }

        @Override
        public void setNextVar(String name, Object value) {
            this.context.getBindings(ScriptContext.ENGINE_SCOPE).put(name, value);
        }

        @Override
        public float runAsFloat() {
            return ((Number) run()).floatValue();
        }

        @Override
        public long runAsLong() {
            return ((Number) run()).longValue();
        }

        @Override
        public double runAsDouble() {
            return ((Number) run()).doubleValue();
        }
    }
}
