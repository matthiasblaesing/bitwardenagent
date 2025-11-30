/*
 * Copyright 2025 matthias.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.doppelhelix.app.bitwardenagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.doppelhelix.app.bitwardenagent.impl.Util.isWindows;

public class Configuration {
    private static final System.Logger LOG = System.getLogger(Configuration.class.getName());
    private static final Configuration INSTANCE = new Configuration();

    public static final String PROP_START_UNIX_DOMAIN_SOCKET_SERVER = "startUnixDomainSocketServer";
    public static final String PROP_ALLOW_ALL_ACCESS = "allowAllAccess";
    public static final String PROP_ALLOW_ACCESS = "allowAccess";

    public static Configuration getConfiguration() {
        return INSTANCE;
    }

    private final Path configPath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ConfigurationObserver> observer = new CopyOnWriteArrayList();
    private Map<String, Object> configData = new ConcurrentHashMap<>();

    private Configuration() {
            configPath = isWindows()
                ? Path.of(System.getenv("APPDATA"), "BitwardenAgent", "config.json")
                : Path.of(System.getenv("HOME"), ".config/BitwardenAgent", "config.json");
            configData = new HashMap<>();
            readConfig();
    }

    public void addObserver(ConfigurationObserver configurationObserver) {
        Objects.requireNonNull(configurationObserver);
        this.observer.add(configurationObserver);
    }

    public void removeObserver(ConfigurationObserver configurationObserver) {
        Objects.requireNonNull(configurationObserver);
        this.observer.remove(configurationObserver);
    }

    public void setStartUnixDomainSocketServer(boolean value) {
        configData.put(PROP_START_UNIX_DOMAIN_SOCKET_SERVER, value);
        writeConfig();
        this.observer.forEach(co -> co.updatedValue(PROP_START_UNIX_DOMAIN_SOCKET_SERVER, value));
    }

    public boolean isStartUnixDomainSocketServer() {
        try {
            return (boolean) configData.getOrDefault(PROP_START_UNIX_DOMAIN_SOCKET_SERVER, false);
        } catch (ClassCastException ex) {
            return false;
        }
    }

    public void setAllowAllAccess(boolean value) {
        configData.put(PROP_ALLOW_ALL_ACCESS, value);
        writeConfig();
        this.observer.forEach(co -> co.updatedValue(PROP_ALLOW_ALL_ACCESS, value));
    }

    public boolean isAllowAllAccess() {
        try {
            return (boolean) configData.getOrDefault(PROP_ALLOW_ALL_ACCESS, false);
        } catch (ClassCastException ex) {
            return false;
        }
    }

    public void addAllowAccess(String id) {
        Set<String> write = new HashSet<>(getAllowAccess());
        write.add(id);
        updateAllowAccess(write);
    }

    public void removeAllowAccess(String id) {
        Set<String> write = new HashSet<>(getAllowAccess());
        write.remove(id);
        updateAllowAccess(write);
    }

    private void updateAllowAccess(Set<String> newSet) {
        List<String> data = new ArrayList<>(newSet);
        List<String> roData = Collections.unmodifiableList(data);
        configData.put(PROP_ALLOW_ACCESS, data);
        writeConfig();
        this.observer.forEach(co -> co.updatedValue(PROP_ALLOW_ACCESS, roData));
    }

    public Collection<String> getAllowAccess() {
        try {
            return Collections.unmodifiableList((List<String>) configData.getOrDefault(PROP_ALLOW_ACCESS, Collections.emptyList()));
        } catch (ClassCastException ex) {
            return Collections.emptyList();
        }
    }

    private void writeConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            objectMapper.writeValue(configPath.toFile(), configData);
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.ERROR, (String) "Failed to write configuration", ex);
        }
    }

    private void readConfig() {
        if (Files.exists(configPath)) {
            try {
                configData = objectMapper.readValue(configPath.toFile(), new TypeReference<ConcurrentHashMap<String, Object>>() {
                });
            } catch (IOException ex) {
                LOG.log(System.Logger.Level.ERROR, (String) "Failed to read configuration", ex);
            }
        }
    }

    public interface ConfigurationObserver {
        public void updatedValue(String name, Object value);
    }
}
