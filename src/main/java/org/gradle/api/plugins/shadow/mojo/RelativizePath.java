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

package org.gradle.api.plugins.shadow.mojo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public final class RelativizePath
{
    private RelativizePath() {
        //
    }

    /**
     * relativize a pathname. 
     * @param thing Absolute File of something. (e.g., a parent pom)
     * @param relativeTo base to relativize it do. (e.g., a pom into which a relative pathname to the 'thing' is to be installed).
     * @return
     */
    static String convertToRelativePath(File thing, File relativeTo) {
        StringBuilder relativePath = null;
        
        if ( thing.getParentFile().equals( relativeTo.getParentFile() ))
        {
            return thing.getName(); // a very simple relative path.
        }
        
        List<String> thingDirectories = RelativizePath.parentDirs( thing );
        List<String> relativeToDirectories = RelativizePath.parentDirs( relativeTo );
    
        //Get the shortest of the two paths
        int length = thingDirectories.size() < relativeToDirectories.size() ? thingDirectories.size() : relativeToDirectories.size();
    
        int lastCommonRoot = -1; // index of the lowest directory down from the root that the two have in common.
        int index;
    
        //Find common root
        for ( index = 0; index < length; index++ ) 
        {
            if ( thingDirectories.get( index ).equals(relativeToDirectories.get( index )))
            {
                lastCommonRoot = index;
            } else {
                break;
            }
        }
        if (lastCommonRoot != -1) { // possible on Windows or other multi-root cases.
            //Build up the relative path
            relativePath = new StringBuilder();
            // add ..'s to get from the base up to the common point
            for ( index = lastCommonRoot + 1; index < relativeToDirectories.size(); index++ ) 
            {
                relativePath.append( "../" );
            }
            
            // now add down from the common point to the actual 'thing' item. 
            for ( index = lastCommonRoot + 1; index < thingDirectories.size(); index++ ) 
            {
                relativePath.append( thingDirectories.get( index ) + "/" );
            }
            relativePath.append( thing.getName() );
            return relativePath.toString();
        }
        return null;
    }

    static List<String> parentDirs( File of )
    {
        List<String> results = new ArrayList<String>();
        for ( File p = of.getParentFile() ; p != null ; p = p.getParentFile() )
        {
            if ( !"".equals(p.getName()) )
            {
                results.add( p.getName() );
            }
        }
        
        Collections.reverse( results );
        return results;
    }
    
    

}
