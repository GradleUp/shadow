package shade.filter;

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

import org.codehaus.plexus.util.SelectorUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author David Blevins
 */
public class SimpleFilter
    implements Filter
{

    private Set<File> jars;

    private Set<String> includes;

    private Set<String> excludes;

    public SimpleFilter( Set<File> jars, Set<String> includes, Set<String> excludes )
    {
        this.jars = ( jars != null ) ? new HashSet<File>( jars ) : new HashSet<File>();
        this.includes = normalizePatterns( includes );
        this.excludes = normalizePatterns( excludes );
    }

    public boolean canFilter( File jar )
    {
        return jars.contains( jar );
    }

    public boolean isFiltered( String classFile )
    {
        String path = normalizePath( classFile );

        return !( isIncluded( path ) && !isExcluded( path ) );
    }

    public boolean isSpecificallyIncluded( String classFile )
    {
        if ( includes == null || includes.isEmpty() )
        {
            return false;
        }

        String path = normalizePath( classFile );

        return isIncluded( path );
    }

    private boolean isIncluded( String classFile )
    {
        if ( includes == null || includes.isEmpty() )
        {
            return true;
        }

        return matchPaths( includes, classFile );
    }

    private boolean isExcluded( String classFile )
    {
        if ( excludes == null || excludes.isEmpty() )
        {
            return false;
        }

        return matchPaths( excludes, classFile );
    }

    private boolean matchPaths( Set<String> patterns, String classFile )
    {
        for ( String pattern : patterns )
        {

            if ( SelectorUtils.matchPath( pattern, classFile ) )
            {
                return true;
            }
        }

        return false;
    }

    private String normalizePath( String path )
    {
        return ( path != null ) ? path.replace( File.separatorChar == '/' ? '\\' : '/', File.separatorChar ) : null;
    }

    private Set<String> normalizePatterns( Set<String> patterns )
    {
        Set<String> result = new HashSet<String>();

        if ( patterns != null )
        {
            for ( String pattern : patterns )
            {
                pattern = normalizePath( pattern );

                if ( pattern.endsWith( File.separator ) )
                {
                    pattern += "**";
                }

                result.add( pattern );
            }
        }

        return result;
    }

    public void finished()
    {
    }
}
