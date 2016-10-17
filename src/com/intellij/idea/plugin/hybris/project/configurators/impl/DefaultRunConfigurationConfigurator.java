/*
 * This file is part of "hybris integration" plugin for Intellij IDEA.
 * Copyright (C) 2014-2016 Alexander Bartash <AlexanderBartash@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.intellij.idea.plugin.hybris.project.configurators.impl;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.idea.plugin.hybris.common.HybrisConstants;
import com.intellij.idea.plugin.hybris.common.utils.HybrisI18NBundleUtils;
import com.intellij.idea.plugin.hybris.project.configurators.RunConfigurationConfigurator;
import com.intellij.idea.plugin.hybris.project.descriptors.ConfigHybrisModuleDescriptor;
import com.intellij.idea.plugin.hybris.project.descriptors.HybrisProjectDescriptor;
import com.intellij.idea.plugin.hybris.project.descriptors.PlatformHybrisModuleDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

/**
 * Created by Martin Zdarsky-Jones (martin.zdarsky@hybris.com) on 17/10/2016.
 */
public class DefaultRunConfigurationConfigurator implements RunConfigurationConfigurator {

    @Override
    public void configure(@NotNull final HybrisProjectDescriptor hybrisProjectDescriptor, @NotNull final Project project) {

        final RunManager runManager = RunManager.getInstance(project);
        if (runManager == null) {
            return;
        }

        final RemoteConfigurationType remoteConfigurationType = ConfigurationTypeUtil.findConfigurationType(RemoteConfigurationType.class);
        final ConfigurationFactory configurationFactory = remoteConfigurationType.getConfigurationFactories()[0];
        final String debugName = HybrisI18NBundleUtils.message("hybris.project.import.run.configuration.debug");
        final RunnerAndConfigurationSettings runner = runManager.createRunConfiguration(debugName, configurationFactory);
        final RemoteConfiguration remoteConfiguration = (RemoteConfiguration) runner.getConfiguration();
        remoteConfiguration.PORT = getDebugPort(hybrisProjectDescriptor, project);
        runner.setSingleton(true);
        runner.setActivateToolWindowBeforeRun(true);
        runManager.addConfiguration(runner, true);
        runManager.setSelectedConfiguration(runner);
    }

    private String getDebugPort(@NotNull final HybrisProjectDescriptor hybrisProjectDescriptor, @NotNull final Project project) {
        ConfigHybrisModuleDescriptor configDescriptor = getConfigDescriptor(hybrisProjectDescriptor);
        if (configDescriptor != null) {
            String port = findPortProperty(configDescriptor.getRootDirectory(), HybrisConstants.LOCAL_PROPERTIES);
            if (port != null) {
                return port;
            }
        }
        PlatformHybrisModuleDescriptor platformDescriptor = getPlatformDescriptor(hybrisProjectDescriptor);
        if (platformDescriptor != null) {
            String port = findPortProperty(platformDescriptor.getRootDirectory(), HybrisConstants.PROJECT_PROPERTIES);
            if (port != null) {
                return port;
            }
        }
        return HybrisConstants.DEBUG_PORT;
    }


    private String findPortProperty(final File rootDirectory, final String fileName) {
        File propertiesFile = new File(rootDirectory, fileName);
        if (!propertiesFile.exists()) {
            return null;
        }

        Properties properties = new Properties();
        try {
            FileReader fr = new FileReader(propertiesFile);
            properties.load(fr);
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        }

        final String debugOptions = (String) properties.get(HybrisConstants.TOMCAT_JAVA_DEBUG_OPTIONS);
        if (debugOptions == null) {
            return null;
        }
        final Optional<String> transport = Arrays.stream(debugOptions.split(" "))
                                                 .filter(e -> e.startsWith(HybrisConstants.X_RUNJDWP_TRANSPORT))
                                                 .findAny();
        if (!transport.isPresent()) {
            return null;
        }
        final Optional<String> address = Arrays.stream(transport.get().split(","))
                                               .filter(e -> e.startsWith(HybrisConstants.ADDRESS))
                                               .findAny();
        if (!address.isPresent()) {
            return null;
        }
        return address.get().split("=")[1];
    }

    private ConfigHybrisModuleDescriptor getConfigDescriptor(final HybrisProjectDescriptor hybrisProjectDescriptor) {
        return (ConfigHybrisModuleDescriptor) hybrisProjectDescriptor
            .getFoundModules()
            .stream()
            .filter(e->e instanceof ConfigHybrisModuleDescriptor)
            .findFirst()
            .orElse(null);
    }

    private PlatformHybrisModuleDescriptor getPlatformDescriptor(final HybrisProjectDescriptor hybrisProjectDescriptor) {
        return (PlatformHybrisModuleDescriptor) hybrisProjectDescriptor
            .getFoundModules()
            .stream()
            .filter(e->e instanceof PlatformHybrisModuleDescriptor)
            .findFirst()
            .orElse(null);
    }
}