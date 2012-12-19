package shade.resource;

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

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Appends multiple occurrences of some XML file.
 */
public class XmlAppendingTransformer
    implements ResourceTransformer
{
    public static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    boolean ignoreDtd = true;

    String resource;

    Document doc;

    public boolean canTransformResource( String r )
    {
        if ( resource != null && resource.equalsIgnoreCase( r ) )
        {
            return true;
        }

        return false;
    }

    public void processResource( String resource, InputStream is, List<Relocator> relocators )
        throws IOException
    {
        Document r;
        try
        {
            SAXBuilder builder = new SAXBuilder( false );
            builder.setExpandEntities( false );
            if ( ignoreDtd )
            {
                builder.setEntityResolver( new EntityResolver()
                {
                    public InputSource resolveEntity( String publicId, String systemId )
                        throws SAXException, IOException
                    {
                        return new InputSource( new StringReader( "" ) );
                    }
                } );
            }
            r = builder.build( is );
        }
        catch ( JDOMException e )
        {
            throw new RuntimeException( "Error processing resource " + resource + ": " + e.getMessage(), e );
        }

        if ( doc == null )
        {
            doc = r;
        }
        else
        {
            Element root = r.getRootElement();

            for ( @SuppressWarnings( "unchecked" )
            Iterator<Attribute> itr = root.getAttributes().iterator(); itr.hasNext(); )
            {
                Attribute a = itr.next();
                itr.remove();

                Element mergedEl = doc.getRootElement();
                Attribute mergedAtt = mergedEl.getAttribute( a.getName(), a.getNamespace() );
                if ( mergedAtt == null )
                {
                    mergedEl.setAttribute( a );
                }
            }

            for ( @SuppressWarnings( "unchecked" )
            Iterator<Content> itr = root.getChildren().iterator(); itr.hasNext(); )
            {
                Content n = itr.next();
                itr.remove();

                doc.getRootElement().addContent( n );
            }
        }
    }

    public boolean hasTransformedResource()
    {
        return doc != null;
    }

    public void modifyOutputStream( JarOutputStream jos )
        throws IOException
    {
        jos.putNextEntry( new JarEntry( resource ) );

        new XMLOutputter( Format.getPrettyFormat() ).output( doc, jos );

        doc = null;
    }
}
