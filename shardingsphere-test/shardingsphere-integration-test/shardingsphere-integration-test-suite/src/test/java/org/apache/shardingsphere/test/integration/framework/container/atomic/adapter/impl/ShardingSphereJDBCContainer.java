/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.test.integration.framework.container.atomic.adapter.impl;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.yaml.config.pojo.YamlRootConfiguration;
import org.apache.shardingsphere.infra.yaml.engine.YamlEngine;
import org.apache.shardingsphere.test.integration.env.EnvironmentPath;
import org.apache.shardingsphere.test.integration.framework.container.atomic.adapter.AdapterContainer;
import org.apache.shardingsphere.test.integration.framework.container.atomic.storage.StorageContainer;
import org.apache.shardingsphere.test.integration.framework.param.model.ParameterizedArray;
import org.testcontainers.lifecycle.Startable;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ShardingSphere JDBC container.
 */
public final class ShardingSphereJDBCContainer extends AdapterContainer {
    
    private final AtomicBoolean isHealthy = new AtomicBoolean();
    
    private Map<String, DataSource> dataSourceMap;
    
    private final AtomicReference<DataSource> dataSourceProvider = new AtomicReference<>();
    
    private final AtomicReference<DataSource> dataSourceForReaderProvider = new AtomicReference<>();
    
    public ShardingSphereJDBCContainer(final ParameterizedArray parameterizedArray) {
        super("ShardingSphere-JDBC", "ShardingSphere-JDBC", true, parameterizedArray);
    }
    
    @Override
    public void start() {
        super.start();
        dataSourceMap = findStorageContainer().getDataSourceMap();
        isHealthy.set(true);
    }
    
    private StorageContainer findStorageContainer() {
        Optional<Startable> result = getDependencies().stream().filter(each -> each instanceof StorageContainer).findFirst();
        Preconditions.checkState(result.isPresent());
        return (StorageContainer) result.get();
    }
    
    /**
     * Get data source.
     *
     * @param serverLists server list
     * @return data source
     */
    public DataSource getDataSource(final String serverLists) {
        DataSource dataSource = dataSourceProvider.get();
        if (Objects.isNull(dataSource)) {
            if (Strings.isNullOrEmpty(serverLists)) {
                try {
                    dataSourceProvider.set(
                            YamlShardingSphereDataSourceFactory.createDataSource(dataSourceMap, new File(EnvironmentPath.getRulesConfigurationFile(getParameterizedArray().getScenario()))));
                } catch (final SQLException | IOException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                dataSourceProvider.set(createGovernanceDataSource(serverLists));
            }
        }
        return dataSourceProvider.get();
    }
    
    /**
     * Get governance data source for reader.
     *
     * @param serverLists server list
     * @return data source
     */
    public DataSource getDataSourceForReader(final String serverLists) {
        DataSource dataSource = dataSourceForReaderProvider.get();
        if (Objects.isNull(dataSource)) {
            dataSourceForReaderProvider.set(createGovernanceDataSource(serverLists));
        }
        return dataSourceForReaderProvider.get();
    }
    
    private DataSource createGovernanceDataSource(final String serverLists) {
        try {
            YamlRootConfiguration rootConfig = YamlEngine.unmarshal(new File(EnvironmentPath.getRulesConfigurationFile(getParameterizedArray().getScenario())), YamlRootConfiguration.class);
            rootConfig.getMode().getRepository().getProps().setProperty("server-lists", serverLists);
            return YamlShardingSphereDataSourceFactory.createDataSource(dataSourceMap, YamlEngine.marshal(rootConfig).getBytes(StandardCharsets.UTF_8));
        } catch (final SQLException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    @Override
    public boolean isHealthy() {
        return isHealthy.get();
    }
}