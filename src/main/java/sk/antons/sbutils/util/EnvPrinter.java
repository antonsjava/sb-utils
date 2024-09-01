/*
 * Copyright 2018 Anton Straka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sk.antons.sbutils.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.boot.env.ConfigTreePropertySource;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Prints spring environment.
 *
 * First collect all property names defined by sources which are explicitlu configured in files.
 * Define which of them are secured (secureSourcePredicate or securePropertyPredicate)
 *
 * secureSourcePredicate is by default sourceName.contains("/secrets")
 *
 * securePropertyPredicate is by default propName.endsWith(".password") or propName.endsWith(".key")
 * @author antons
 */
public class EnvPrinter {
    private Environment env;
    private Predicate<String> secureSourcePredicate = n -> n.contains("/secrets");
    private Predicate<String> securePropertyPredicate = n -> (n.endsWith(".password") || n.endsWith(".key"));
    public EnvPrinter(Environment env) {
        this.env = env;
        init();
    }
    public static EnvPrinter instamce(Environment env) { return new EnvPrinter(env); }
    public Predicate<String> secureSourcePredicate() { return secureSourcePredicate; }
    public EnvPrinter secureSourcePredicate(Predicate<String> value) { this.secureSourcePredicate = value; return this; }
    public Predicate<String> securePropertyPredicate() { return securePropertyPredicate; }
    public EnvPrinter securePropertyPredicate(Predicate<String> value) { this.securePropertyPredicate = value; return this; }

    private List<String> propertyNames = new ArrayList<>();
    private Set<String> securedPropertyNames = new HashSet<>();

    private void init() {
        if(env instanceof AbstractEnvironment) {
            for(Iterator it = ((AbstractEnvironment) env).getPropertySources().iterator(); it.hasNext(); ) {
                PropertySource propertySource = (PropertySource) it.next();
                String sourceName = propertySource.getName();
                if("systemProperties".equals(sourceName)) continue;
                if("systemEnvironment".equals(sourceName)) continue;
                if (propertySource instanceof MapPropertySource) {
                    Map<String, Object> map = (((MapPropertySource) propertySource).getSource());
                    if(map != null) {
                        for(String key : map.keySet()) {
                            if(!propertyNames.contains(key)) propertyNames.add(key);
                            if((secureSourcePredicate != null) && secureSourcePredicate.test(sourceName)) securedPropertyNames.add(key);
                            else if((securePropertyPredicate != null) && securePropertyPredicate.test(key)) securedPropertyNames.add(key);
                        }
                    }
                } else if (propertySource instanceof ConfigTreePropertySource) {
                    String[] list = (((ConfigTreePropertySource) propertySource).getPropertyNames());
                    if(list != null) {
                        for(String key : list) {
                            if(!propertyNames.contains(key)) propertyNames.add(key);
                            if((secureSourcePredicate != null) && secureSourcePredicate.test(sourceName)) securedPropertyNames.add(key);
                            else if((securePropertyPredicate != null) && securePropertyPredicate.test(key)) securedPropertyNames.add(key);
                        }
                    }
                }
            }
        }
        Collections.sort(propertyNames);
    }

    /**
     * Prints info source by source.
     * @return info
     */
    public String printBySources() {
        StringBuilder sb = new StringBuilder();
        if(env instanceof AbstractEnvironment) {
            for(Iterator it = ((AbstractEnvironment) env).getPropertySources().iterator(); it.hasNext(); ) {
                PropertySource propertySource = (PropertySource) it.next();
                String sourceName = propertySource.getName();
                if("systemProperties".equals(sourceName) || "systemEnvironment".equals(sourceName)) {
                    sb.append("\n-- source: ").append(propertySource.getName()).append(" --");
                    Map<String, Object> map = (((MapPropertySource) propertySource).getSource());
                    if(map != null) {
                        for(String key : propertyNames) {
                            Object v = map.get(key);
                            if(v == null) continue;
                            String value = "" + v;
                            if(securedPropertyNames.contains(key)) value = value.replaceAll(".", "*");
                            sb.append('\n').append(key).append(": ").append(value);
                        }
                    }
                } else if (propertySource instanceof MapPropertySource) {
                    sb.append("\n-- source: ").append(propertySource.getName()).append(" --");
                    Map<String, Object> map = (((MapPropertySource) propertySource).getSource());
                    if(map != null) {
                        List<String> list = new ArrayList<>(map.keySet());
                        Collections.sort(list);
                        for(String key : list) {
                            String value = "" + map.get(key);
                            if(value == null) value = "";
                            if(securedPropertyNames.contains(key)) value = value.replaceAll(".", "*");
                            sb.append('\n').append(key).append(": ").append(value);
                        }
                    }
                } else if (propertySource instanceof ConfigTreePropertySource) {
                    sb.append("\n-- source: ").append(propertySource.getName()).append(" --");
                    String[] list = (((ConfigTreePropertySource) propertySource).getPropertyNames());
                    if(list != null) {
                        Arrays.sort(list);
                        for(String key : list) {
                            String value = propertySource.getProperty(key).toString();
                            if(value == null) value = "";
                            if(securedPropertyNames.contains(key)) value = value.replaceAll(".", "*");
                            sb.append('\n').append(key).append(": ").append(value);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Prints info for all collected property names
     * @return info
     */
    public String print() {
        StringBuilder sb = new StringBuilder();
        for(String key : propertyNames) {
            String value = env.getProperty(key);
            if(value == null) continue;
            if(securedPropertyNames.contains(key)) value = value.replaceAll(".", "*");
            sb.append('\n').append(key).append(": ").append(value);
        }
        return sb.toString();
    }
}
