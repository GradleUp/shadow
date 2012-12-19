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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarOutputStream;

/**
 * Prevents duplicate copies of the license
 */
public class ApacheLicenseResourceTransformer
    implements ResourceTransformer
{

    private static final String LICENSE_PATH = "META-INF/LICENSE";

    private static final String LICENSE_TXT_PATH = "META-INF/LICENSE.txt";

    public boolean canTransformResource( String resource )
    {
        return LICENSE_PATH.equalsIgnoreCase( resource )
            || LICENSE_TXT_PATH.regionMatches( true, 0, resource, 0, LICENSE_TXT_PATH.length() );
    }

    public void processResource( String resource, InputStream is, List<Relocator> relocators )
        throws IOException
    {

    }

    public boolean hasTransformedResource()
    {
        return false;
    }

    public void modifyOutputStream( JarOutputStream os )
        throws IOException
    {
    }
}
