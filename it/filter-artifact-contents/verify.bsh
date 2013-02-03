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
import java.io.*;
import java.util.jar.*;

String[] wanted =
{
    "a.properties",
    "org/apache/a.properties",
    "org/apache/maven/a.properties",
    "b.properties",
    "org/apache/maven/b.properties",
};

String[] unwanted =
{
    "META-INF/maven/org.apache.maven.its.shade.fac/a/pom.properties",
    "org/a.properties",
    "org/b.properties",
    "org/apache/b.properties",
    "org/apache/maven/b/b.properties",
};

JarFile jarFile = new JarFile( new File( basedir, "target/test-1.0.jar" ) );

for ( String path : wanted )
{
    if ( jarFile.getEntry( path ) == null )
    {
        throw new IllegalStateException( "wanted path is missing: " + path );
    }
}

for ( String path : unwanted )
{
    if ( jarFile.getEntry( path ) != null )
    {
        throw new IllegalStateException( "unwanted path is present: " + path );
    }
}

jarFile.close();
