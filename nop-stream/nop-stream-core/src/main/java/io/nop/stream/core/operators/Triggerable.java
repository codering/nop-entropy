/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.core.operators;


import io.nop.api.core.annotations.core.Internal;

/**
 * Interface for things that can be called by {@link InternalTimerService}.
 *
 * @param <K> Type of the keys to which timers are scoped.
 * @param <N> Type of the namespace to which timers are scoped.
 */
@Internal
public interface Triggerable<K, N> {

    /** Invoked when an event-time timer fires. */
    void onEventTime(InternalTimer<K, N> timer) throws Exception;

    /** Invoked when a processing-time timer fires. */
    void onProcessingTime(InternalTimer<K, N> timer) throws Exception;
}
