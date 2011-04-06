/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.stanbol.commons.stanboltools.datafileprovider.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.stanboltools.datafileprovider.DataFileProvider;
import org.apache.stanbol.commons.stanboltools.datafileprovider.DataFileProviderEvent;
import org.apache.stanbol.commons.stanboltools.datafileprovider.DataFileProviderLog;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The main DatafileProvider, delegates to other DataFileProvider if 
 *  the requested file is not found in its datafiles folder.
 *  
 *  Must have the lowest service ranking of all DatafileProvider, so
 *  that this is the default one which delegates to others. 
 */
@Component(immediate=true, metatype=true)
@Service
@Property(name=Constants.SERVICE_RANKING, intValue=0)
public class MainDataFileProvider implements DataFileProvider, DataFileProviderLog {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Property(value="./datafiles")
    public static final String DATA_FILES_FOLDER_PROP = "data.files.folder";
    private File dataFilesFolder;
    
    @Property(intValue=100)
    public static final String MAX_EVENTS_PROP = "max.events";
    private int maxEvents;
    
    /** List of past events, up to maxEvents in size */
    private final List<DataFileProviderEvent> events = new LinkedList<DataFileProviderEvent>();
    
    /** Track providers to which we can delegate */
    private ServiceTracker providersTracker;
    
    @Activate
    protected void activate(ComponentContext ctx) throws ConfigurationException {
        dataFilesFolder = new File(requireProperty(ctx.getProperties(), DATA_FILES_FOLDER_PROP, String.class));
        maxEvents = requireProperty(ctx.getProperties(), MAX_EVENTS_PROP, Integer.class).intValue();
        
        providersTracker = new ServiceTracker(ctx.getBundleContext(), DataFileProvider.class.getName(), null);
        providersTracker.open();
        
        log.info("Activated, max.events {}, data files folder {}", maxEvents, dataFilesFolder.getAbsolutePath());
    }
    
    @Deactivate
    protected void deactivate(ComponentContext ctx) {
        if(providersTracker != null) {
            providersTracker.close();
            providersTracker = null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private <ResultType> ResultType requireProperty(Dictionary<?, ?> props, String name, Class<ResultType> clazz) 
    throws ConfigurationException {
        final Object o = props.get(name);
        if(o == null) {
            throw new ConfigurationException(name, "Missing required configuration property: " + name);
        }
        if( !( clazz.isAssignableFrom(o.getClass()))) {
            throw new ConfigurationException(name, "Property is not a " + clazz.getName());
        }
        return (ResultType)o;
    }

    /** @inheritDoc */
    @Override
    public Iterator<DataFileProviderEvent> iterator() {
        // Iterate on a copy of our list to avoid concurrency issues
        final List<DataFileProviderEvent> copy = new LinkedList<DataFileProviderEvent>();
        synchronized (events) {
            copy.addAll(events);
        }
        return copy.iterator();
    }

    /** @inheritDoc */
    @Override
    public int maxEventsCount() {
        return maxEvents;
    }

    /** @inheritDoc */
    @Override
    public int size() {
        return events.size();
    }

    /** @inheritDoc */
    @SuppressWarnings("unchecked")
    @Override
    public InputStream getInputStream(String bundleSymbolicName,
            String filename, Map<String, String> comments) throws IOException {
        InputStream result = null;
        String fileUrl = null;
        
        // First look for the file in our data folder,
        // with and without bundle symbolic name prefix
        final String [] candidateNames = {
                bundleSymbolicName + "-" + filename,
                filename
        };
        for(String name : candidateNames) {
            final File f = new File(dataFilesFolder, name);
            log.debug("Looking for file {}", f.getAbsolutePath());
            if(f.exists() && f.canRead()) {
                log.debug("File found in data files folder: {}", filename);
                result = new FileInputStream(f);
                fileUrl = "file://" + f.getAbsolutePath();
                break;
            }
        }
        
        // Then, if not found, query other DataFileProviders,
        // ordered by service ranking
        if(result == null) {
            // Sort providers by service ranking
            final List<ServiceReference> refs = Arrays.asList(providersTracker.getServiceReferences());
            Collections.sort(refs);
            for(ServiceReference ref: refs) {
                final Object o = providersTracker.getService(ref);
                if(o == this) {
                    continue;
                }
                final DataFileProvider dfp = (DataFileProvider)o;
                result = dfp.getInputStream(bundleSymbolicName, filename, comments);
                if(result == null) {
                    log.debug("{} does not provide file {}", dfp, filename);
                } else {
                    fileUrl = dfp.getClass().getName() + "://" + filename;
                }
            }
        }
        
        // Add event
        final DataFileProviderEvent event = new DataFileProviderEvent(
                bundleSymbolicName, filename, 
                comments, fileUrl);
        
        synchronized (events) {
            if(events.size() >= maxEvents) {
                events.remove(0);
            }
            events.add(event);
        }

        if(result == null) {
            throw new IOException("File not found: " + filename);
        }
        
        log.info("Successfully loaded file {}", event);
        return result;
    }
}