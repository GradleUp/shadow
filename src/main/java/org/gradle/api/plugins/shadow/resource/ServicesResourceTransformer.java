package org.gradle.api.plugins.shadow.resource;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.gradle.api.plugins.shadow.relocation.Relocator;
import org.codehaus.plexus.util.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Resources transformer that appends entries in META-INF/services resources into
 * a single resource. For example, if there are several META-INF/services/org.apache.maven.project.ProjectBuilder
 * resources spread across many JARs the individual entries will all be concatenated into a single
 * META-INF/services/org.apache.maven.project.ProjectBuilder resource packaged into the resultant JAR produced
 * by the shading process.
 *
 * @author jvanzyl
 */
public class ServicesResourceTransformer
    implements ResourceTransformer
{

    private static final String SERVICES_PATH = "META-INF/services";

    private Map<String, ServiceStream> serviceEntries = new HashMap<String, ServiceStream>();

    public boolean canTransformResource( String resource )
    {
        if ( resource.startsWith( SERVICES_PATH ) )
        {
            return true;
        }

        return false;
    }

    public void processResource( String resource, InputStream is, List<Relocator> relocators )
        throws IOException
    {
        ServiceStream out = serviceEntries.get( resource );
        if ( out == null )
        {
            out = new ServiceStream();
            serviceEntries.put( resource, out );
        }

        out.append( is );
        is.close();
    }

    public boolean hasTransformedResource()
    {
        return serviceEntries.size() > 0;
    }

    public void modifyOutputStream( JarOutputStream jos )
        throws IOException
    {
        for ( Map.Entry<String, ServiceStream> entry : serviceEntries.entrySet() )
        {
            String key = entry.getKey();
            ServiceStream data = entry.getValue();

            jos.putNextEntry( new JarEntry( key ) );
            IOUtil.copy( data.toInputStream(), jos );
            data.reset();
        }
    }

    static class ServiceStream
        extends ByteArrayOutputStream
    {

        public ServiceStream()
        {
            super( 1024 );
        }

        public void append( InputStream is )
            throws IOException
        {
            if ( count > 0 && buf[count - 1] != '\n' && buf[count - 1] != '\r' )
            {
                write( '\n' );
            }

            IOUtil.copy( is, this );
        }

        public InputStream toInputStream()
        {
            return new ByteArrayInputStream( buf, 0, count );
        }

    }

}
