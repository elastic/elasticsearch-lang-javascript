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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.script.javascript.support.NashornExecutableScript;
import org.elasticsearch.script.javascript.support.NashornScriptValueConverter;
import org.elasticsearch.script.javascript.support.NashornSearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

/**
 * Nashorn ScriptEngineService.
 */
public class JavascriptNashornScriptEngineService extends AbstractComponent
        implements ScriptEngineService {

	/**
	 * Global objects are not thread-safe.
	 * We create one ScriptEngine per thread and configure the ScriptEngine to reuse the same Global
	 * with --global-per-engine
	 */
    private static final ThreadLocal<ScriptEngine> currentEngine = new ThreadLocal<ScriptEngine>();
    /**
     * CompiledScripts actually are referring to their Global object.
     * If one attempts to execute a compiled Script in a different thread it gets recompiled.
     * So we cache the script compiled object for each thread.
     */
    private static final ThreadLocal<Map<String,CompiledScript>> compiledScripts = new ThreadLocal<Map<String,CompiledScript>>();

    @Inject
    public JavascriptNashornScriptEngineService(Settings settings) {
        super(settings);
        NashornScriptValueConverter.setup();
        
    }

    /**
     * @return The ScriptEngine to use. It is tied to the current thread and configured to reuse the same Global
     * for all script executions on that thread.
     */
    private ScriptEngine getEngine() {
        ScriptEngine engine = currentEngine.get();
        if (engine != null) {
        	return engine;
        }
//        System.err.println("Creating a new engine " + engineNumber++);
        // setup the engine to share the definition of the Ecma script built-in objects: aka NashornGlobal.
//      System.setProperty("nashorn.args", "--global-per-engine");
//      ScriptEngineManager m = new ScriptEngineManager();
//      nashorn = m.getEngineByName("nashorn");
      // changing the system properties is not allowed by the tests. :(

//      NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
//      ScriptEngine engine = factory.getScriptEngine(new String[] { "--global-per-engine" });
//      We cant use nashorn APIs so we need to go through introspection
        try {
      	    String className = "jdk.nashorn.api.scripting.NashornScriptEngineFactory";
	      	Class<?> factoryClass = this.getClass().getClassLoader().loadClass(className);
	      	Method getScriptEngineMethod = factoryClass.getMethod("getScriptEngine", String[].class);
	      	engine = (ScriptEngine)getScriptEngineMethod.invoke(factoryClass.newInstance(), new Object[]{new String[] { "--global-per-engine" }});
	      	currentEngine.set(engine);
        } catch(Throwable t) {
        	t.printStackTrace();
        	throw new RuntimeException("Could not find the nashorn engine", t);
        }
        return engine;
    }

    /**
     * @return The CompiledScript to use. It is tied to the current thread.
     */
    private CompiledScript getCompiledScript(String script) {
    	Map<String,CompiledScript> compiled = compiledScripts.get();
    	if (compiled == null) {
    		compiled = new HashMap<String, CompiledScript>();
    		compiledScripts.set(compiled);
    	}
    	CompiledScript cs = compiled.get(script);
    	if (cs != null) {
    		return cs;
    	}
        try {
        	Compilable compilableObj = (Compilable) getEngine();
            cs = compilableObj.compile(script);
            compiled.put(script, cs);
            return cs;
        } catch (ScriptException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * A Nashorn CompiledScript is only useful for a given Global object itself attached to the current Thread.
	 * So we compile once in order to check that the script is well written.
	 * But we don't cache it.
     */
    @Override
    public Object compile(String script) {
        try {
        	Compilable compilableObj = (Compilable) getEngine();
            compilableObj.compile(script);
            return script;
        } catch (ScriptException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ExecutableScript executable(Object compiledScript, Map<String, Object> vars) {
        Bindings bindings = createBindings(null, vars);
        ScriptContext context = createContext(bindings);
        return new NashornExecutableScript(getCompiledScript((String)compiledScript), context, bindings);
    }

    private ScriptContext createContext(Bindings bindings) {
        ScriptContext newContext = new SimpleScriptContext();
        Bindings builtinBindings = getEngine().getContext().getBindings(ScriptContext.ENGINE_SCOPE);
        newContext.setBindings(builtinBindings, ScriptContext.ENGINE_SCOPE);
        newContext.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
        return newContext;
    }
    
    private Bindings createBindings(SearchLookup lookup, Map<String, Object> vars) {
        Bindings bindings = getEngine().createBindings();
        if (lookup != null) {
            for (Map.Entry<String, Object> entry : lookup.asMap().entrySet()) {
                bindings.put(entry.getKey(), NashornScriptValueConverter.wrapValue(entry.getValue()));
            }
        }
        if (vars != null) {
            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                bindings.put(entry.getKey(), NashornScriptValueConverter.wrapValue(entry.getValue()));
            }
        }
        return bindings;
    }

    @Override
    public Object execute(Object compiledScript, Map<String, Object> vars) {
        try {
        	Bindings bindings = createBindings(null, vars);
            ScriptContext context = createContext(bindings);
            return NashornScriptValueConverter.unwrapValue((getCompiledScript((String)compiledScript)).eval(context));
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
    	Bindings bindings = createBindings(lookup, vars);
        ScriptContext context = createContext(bindings);
        return new NashornSearchScript(getCompiledScript((String)compiledScript), context, bindings, lookup);
    }
}
