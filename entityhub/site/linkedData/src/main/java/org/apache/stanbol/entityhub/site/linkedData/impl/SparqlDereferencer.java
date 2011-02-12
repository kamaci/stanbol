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
package org.apache.stanbol.entityhub.site.linkedData.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Reference;
import org.apache.stanbol.entityhub.core.site.AbstractEntityDereferencer;
import org.apache.stanbol.entityhub.model.clerezza.RdfValueFactory;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.slf4j.LoggerFactory;


/**
 *
 * @author Rupert Westenthaler
 *
 */
@Component(
        name="org.apache.stanbol.entityhub.site.SparqlDereferencer",
        factory="org.apache.stanbol.entityhub.site.SparqlDereferencerFactory",
        policy=ConfigurationPolicy.REQUIRE, //the baseUri and the SPARQL Endpoint are required
        specVersion="1.1"
        )
public class SparqlDereferencer extends AbstractEntityDereferencer {
    private final RdfValueFactory valueFactory = RdfValueFactory.getInstance();

    public SparqlDereferencer(){
        super(LoggerFactory.getLogger(SparqlDereferencer.class));
    }
    /**
     * The key used to define the baseUri for instances of this Service
     * TODO: Maybe define this constants in the ReferenceManager Interface
     */
    public static final String SPARQL_ENDPOINT_URI = "org.apache.stanbol.entityhub.servicesapi.site.sparqlEndpointUri";

    @Reference
    protected Parser parser;


    /*
     * TODO: Supports only Triple serialisations as content types.
     * To support other types one would need to create a select query and
     * format the output accordingly.
     * However it is not clear if such a functionality is needed.
     */
    @Override
    public InputStream dereference(String uri, String contentType) throws IOException {
        if(uri==null){
            return null;
        }
        UriRef reference = new UriRef(uri);
        StringBuilder query = new StringBuilder();
        query.append("CONSTRUCT { ");
        query.append(reference);
        query.append(" ?p ?o } WHERE { ");
        query.append(reference);
        query.append(" ?p ?o }");

        //String format = SupportedFormat.RDF_XML;
        return SparqlEndpointUtils.sendSparqlRequest(getAccessUri(),query.toString(),contentType);
    }

    public Representation dereference(String uri) throws IOException {
        long start = System.currentTimeMillis();
        String format = SupportedFormat.RDF_XML;
        InputStream in = dereference(uri, format);
        long queryEnd = System.currentTimeMillis();
        log.info("  > DereferenceTime: "+(queryEnd-start));
        if(in != null){
            MGraph rdfData = new SimpleMGraph(parser.parse(in, format));
            long parseEnd = System.currentTimeMillis();
            log.info("  > ParseTime: "+(parseEnd-queryEnd));
            return valueFactory.createRdfRepresentation(new UriRef(uri), rdfData);
        } else {
            return null;
        }
    }

//    /**
//     * We need also to check for the endpointURI of the SPARQL service. So override
//     * the default implementation and check for the additional property!
//     */
//    @Activate
//    @Override
//    public void activate(ComponentContext context) {
//        //super config
//        super.activate(context);
//        log.info("  init sparql endpoint property");
//    }
//    @Deactivate
//    @Override
//    protected void deactivate(ComponentContext context) {
//        super.deactivate(context);
//    }
}
