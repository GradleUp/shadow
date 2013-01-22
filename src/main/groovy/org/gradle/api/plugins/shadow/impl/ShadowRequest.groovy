/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
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

package org.gradle.api.plugins.shadow.impl

import org.gradle.api.plugins.shadow.ShadowStats

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
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

import org.gradle.api.plugins.shadow.filter.Filter
import org.gradle.api.plugins.shadow.relocation.Relocator
import org.gradle.api.plugins.shadow.transformers.Transformer

/**
 * Parameter object used to pass multitude of args to Caster.shade()
 * @since 2.0
 *
 * Modified from org.apache.maven.plugins.shade.ShadeRequest.java
 */
class ShadowRequest {

    List<File> jars

    File uberJar

    List<Filter> filters

    List<Relocator> relocators

    List<Transformer> resourceTransformers

    boolean shadeSourcesContent

    ShadowStats stats

}
