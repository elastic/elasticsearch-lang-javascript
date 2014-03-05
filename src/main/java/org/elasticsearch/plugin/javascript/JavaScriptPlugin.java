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
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.script.javascript.JavaScriptNashornScriptEngineService;
import org.elasticsearch.script.javascript.JavaScriptScriptEngineService;

/**
 *
 */
public class JavaScriptPlugin extends AbstractPlugin {
    private static final String RHINO = "rhino";
    private static final String NASHORN = "nashorn";
    private static final String SCRIPT_JAVASCRIPT_ENGINE = "script.javascript.engine";
    private final Settings settings;

    public JavaScriptPlugin(Settings settings) {
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

    /**
     *
     *  script.javascript.engine = rhino/nashorn
     *
     */
    public void onModule(ScriptModule module) {
        if (NASHORN.equalsIgnoreCase(settings.get(SCRIPT_JAVASCRIPT_ENGINE,RHINO))) {
            if (!isJDK8()) {
                throw new SettingsException("Javascript engine - Nashorn - specified by script.javascript.engine requires JDK8.");
            };
            module.addScriptEngine(JavaScriptNashornScriptEngineService.class);
        } else if (RHINO.equalsIgnoreCase(settings.get(SCRIPT_JAVASCRIPT_ENGINE, RHINO))) {
            module.addScriptEngine(JavaScriptScriptEngineService.class);
        } else {
            throw new SettingsException("Invalid engine name specified by script.javascript.engine.");
        }
    }

    private boolean isJDK8() {
        return Double.parseDouble(System.getProperty("java.version").substring(0,  System.getProperty("java.version").lastIndexOf("."))) >= 1.8;
    }
}
