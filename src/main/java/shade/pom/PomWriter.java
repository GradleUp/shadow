package shade.pom;

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

import org.apache.maven.model.Model;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.Format;

import java.io.IOException;
import java.io.Writer;

/**
 * @author Jason van Zyl
 */
public class PomWriter
{
    public static void write( Writer w, Model newModel )
        throws IOException
    {
        write( w, newModel, false );
    }

    public static void write( Writer w, Model newModel, boolean namespaceDeclaration )
        throws IOException
    {
        Element root = new Element( "project" );

        if ( namespaceDeclaration )
        {
            String modelVersion = newModel.getModelVersion();

            Namespace pomNamespace = Namespace.getNamespace( "", "http://maven.apache.org/POM/" + modelVersion );

            root.setNamespace( pomNamespace );

            Namespace xsiNamespace = Namespace.getNamespace( "xsi", "http://www.w3.org/2001/XMLSchema-instance" );

            root.addNamespaceDeclaration( xsiNamespace );

            if ( root.getAttribute( "schemaLocation", xsiNamespace ) == null )
            {
                root.setAttribute( "schemaLocation",
                                   "http://maven.apache.org/POM/" + modelVersion + " http://maven.apache.org/maven-v"
                                       + modelVersion.replace( '.', '_' ) + ".xsd", xsiNamespace );
            }
        }

        Document doc = new Document( root );

        MavenJDOMWriter writer = new MavenJDOMWriter();

        String encoding = newModel.getModelEncoding() != null ? newModel.getModelEncoding() : "UTF-8";

        Format format = Format.getPrettyFormat().setEncoding( encoding );

        writer.write( newModel, doc, w, format );
    }
}
