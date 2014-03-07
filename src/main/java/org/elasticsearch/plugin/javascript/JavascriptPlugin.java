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

package org.elasticsearch.plugin.javascript;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.ScriptModule;

/**
 * Javascript scripting support.
 * <p>
 * The engine is either Nashorn either Rhino.<br/>
 * The setting <code>script.javascript.engine</code> must be one-of:
 * <ul>
 * <li> <code>auto</code> or unspecified: Nashorn for Java 8+; Rhino for Java 6 and Java 7.</li>
 * <li> <code>nashorn</code> for Nashorn and Java 8 is required.</li>
 * <li> <code>rhino</code> for Rhino and Java 6, Java 7 or the rhino.jar are required.</li>
 * </ul>
 */
public class JavascriptPlugin extends AbstractPlugin {

    private static final String RHINO = "rhino";
    private static final String NASHORN = "nashorn";
    private static final String AUTO = "auto";
    private static final String SCRIPT_JAVASCRIPT_ENGINE = "script.javascript.engine";
    
    private static final String NASHORN_SERVICE_CLASS = "org.elasticsearch.script.javascript.JavascriptNashornScriptEngineService";
    private static final String RHINO_SERVICE_CLASS = "org.elasticsearch.script.javascript.JavascriptRhinoScriptEngineService";
    
    private final Settings settings;

    public JavascriptPlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "lang-javascript";
    }

    @Override
    public String description() {
        return "JavaScript plugin allowing to add javascript scripting support";
    }

    public void onModule(ScriptModule module) {
        String javascriptEngine = settings.get(SCRIPT_JAVASCRIPT_ENGINE, AUTO);
        String engineClassname = null;
        if (javascriptEngine == null || AUTO.equalsIgnoreCase(javascriptEngine)) {
            if (isJDK8()) {
                engineClassname = NASHORN_SERVICE_CLASS;
            } else {
                engineClassname = RHINO_SERVICE_CLASS;
            }
        } else if (NASHORN.equalsIgnoreCase(javascriptEngine)) {
            if (!isJDK8()) {
                throw new SettingsException("Javascript engine - Nashorn - specified by " + SCRIPT_JAVASCRIPT_ENGINE + " requires JDK8.");
            };
            engineClassname = NASHORN_SERVICE_CLASS;
        } else if (RHINO.equalsIgnoreCase(settings.get(SCRIPT_JAVASCRIPT_ENGINE, RHINO))) {
            engineClassname = RHINO_SERVICE_CLASS;
        } else {
            throw new SettingsException("Invalid engine name specified by script.javascript.engine.");
        }
        Class<ScriptEngineService> engineClass;
        try {
            engineClass = (Class<ScriptEngineService>) this.getClass().getClassLoader().loadClass(engineClassname);
        } catch (ClassNotFoundException e) {
            throw new SettingsException("Unable to load the javascript engine class " + engineClassname);
        }
        module.addScriptEngine(engineClass);
    }

    private boolean isJDK8() {
        return Double.parseDouble(System.getProperty("java.version").substring(0, System.getProperty("java.version").lastIndexOf("."))) >= 1.8;
    }
}
