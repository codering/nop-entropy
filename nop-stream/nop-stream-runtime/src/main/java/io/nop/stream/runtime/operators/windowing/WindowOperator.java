/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.runtime.operators.windowing;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.nop.api.core.annotations.core.Internal;
import io.nop.commons.tuple.Tuple2;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.TimestampedCollector;
import io.nop.stream.core.operators.Triggerable;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.MergingWindowAssigner;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.Window;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.nop.api.core.util.Guard.checkArgument;

/**
 * An operator that implements the logic for windowing based on a {@link WindowAssigner} and {@link
 * Trigger}.
 *
 * <p>When an element arrives it gets assigned a key using a {@link KeySelector} and it gets
 * assigned to zero or more windows using a {@link WindowAssigner}. Based on this, the element is
 * put into panes. A pane is the bucket of elements that have the same key and same {@code Window}.
 * An element can be in multiple panes if it was assigned to multiple windows by the {@code
 * WindowAssigner}.
 *
 * <p>Each pane gets its own instance of the provided {@code Trigger}. This trigger determines when
 * the contents of the pane should be processed to emit results. When a trigger fires, the given
 * {@link InternalWindowFunction} is invoked to produce the results that are emitted for the pane to
 * which the {@code Trigger} belongs.
 *
 * @param <K>   The type of key returned by the {@code KeySelector}.
 * @param <IN>  The type of the incoming elements.
 * @param <OUT> The type of elements emitted by the {@code InternalWindowFunction}.
 * @param <W>   The type of {@code Window} that the {@code WindowAssigner} assigns.
 */
@Internal
public class WindowOperator<K, IN, ACC, OUT, W extends Window>
        extends AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>>
        implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W> {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------------
    // Configuration values and user functions
    // ------------------------------------------------------------------------

    protected final WindowAssigner<? super IN, W> windowAssigner;

    private final KeySelector<IN, K> keySelector;

    private final Trigger<? super IN, ? super W> trigger;

    // private final StateDescriptor<? extends AppendingState<IN, ACC>, ?> windowStateDescriptor;

    /**
     * For serializing the key in checkpoints.
     */
    protected final TypeSerializer<K> keySerializer;

    /**
     * For serializing the window in checkpoints.
     */
    protected final TypeSerializer<W> windowSerializer;

    /**
     * The allowed lateness for elements. This is used for:
     *
     * <ul>
     *   <li>Deciding if an element should be dropped from a window due to lateness.
     *   <li>Clearing the state of a window if the system time passes the {@code window.maxTimestamp
     *       + allowedLateness} landmark.
     * </ul>
     */
    protected final long allowedLateness;

    /**
     * {@link OutputTag} to use for late arriving events. Elements for which {@code
     * window.maxTimestamp + allowedLateness} is smaller than the current watermark will be emitted
     * to this.
     */
    protected final OutputTag<IN> lateDataOutputTag;

    private static final String LATE_ELEMENTS_DROPPED_METRIC_NAME = "numLateRecordsDropped";

    protected transient Counter numLateRecordsDropped;

    // ------------------------------------------------------------------------
    // State that is not checkpointed
    // ------------------------------------------------------------------------

    /** The state in which the window contents is stored. Each window is a namespace */
    // private transient InternalAppendingState<K, W, IN, ACC, ACC> windowState;

    /**
     * The {@link #windowState}, typed to merging state for merging windows. Null if the window
     * state is not mergeable.
     */
    //private transient InternalMergingState<K, W, IN, ACC, ACC> windowMergingState;

    /** The state that holds the merging window metadata (the sets that describe what is merged). */
    //private transient InternalListState<K, VoidNamespace, Tuple2<W, W>> mergingSetsState;

    /**
     * This is given to the {@code InternalWindowFunction} for emitting elements with a given
     * timestamp.
     */
    protected transient TimestampedCollector<OUT> timestampedCollector;

    protected transient Context triggerContext = new Context(null, null);

    protected transient WindowContext processContext;

    protected transient WindowAssigner.WindowAssignerContext windowAssignerContext;

    // ------------------------------------------------------------------------
    // State that needs to be checkpointed
    // ------------------------------------------------------------------------

    protected transient InternalTimerService<W> internalTimerService;

    /**
     * Creates a new {@code WindowOperator} based on the given policies and user functions.
     */
    public WindowOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            TypeSerializer<W> windowSerializer,
            KeySelector<IN, K> keySelector,
            TypeSerializer<K> keySerializer,
            // StateDescriptor<? extends AppendingState<IN, ACC>, ?> windowStateDescriptor,
            InternalWindowFunction<ACC, OUT, K, W> windowFunction,
            Trigger<? super IN, ? super W> trigger,
            long allowedLateness,
            OutputTag<IN> lateDataOutputTag) {

        super(windowFunction);

        checkArgument(allowedLateness >= 0);

//        checkArgument(
//                windowStateDescriptor == null || windowStateDescriptor.isSerializerInitialized(),
//                "window state serializer is not properly initialized");

        this.windowAssigner = checkNotNull(windowAssigner);
        this.windowSerializer = checkNotNull(windowSerializer);
        this.keySelector = checkNotNull(keySelector);
        this.keySerializer = checkNotNull(keySerializer);
        // this.windowStateDescriptor = windowStateDescriptor;
        this.trigger = checkNotNull(trigger);
        this.allowedLateness = allowedLateness;
        this.lateDataOutputTag = lateDataOutputTag;

        // setChainingStrategy(ChainingStrategy.ALWAYS);
    }

    @Override
    public void open() throws Exception {
        super.open();

        this.numLateRecordsDropped = null; // metrics.counter(LATE_ELEMENTS_DROPPED_METRIC_NAME);
        timestampedCollector = new TimestampedCollector<>(output);

        internalTimerService = null; // getInternalTimerService("window-timers", windowSerializer, this);

        triggerContext = new Context(null, null);
        processContext = new WindowContext(null);

        windowAssignerContext =
                new WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return internalTimerService.currentProcessingTime();
                    }
                };

        // create (or restore) the state that hold the actual window contents
        // NOTE - the state may be null in the case of the overriding evicting window operator
//        if (windowStateDescriptor != null) {
//            windowState =
//                    (InternalAppendingState<K, W, IN, ACC, ACC>)
//                            getOrCreateKeyedState(windowSerializer, windowStateDescriptor);
//        }

        // create the typed and helper states for merging windows
        if (windowAssigner instanceof MergingWindowAssigner) {

            // store a typed reference for the state of merging windows - sanity check
//            if (windowState instanceof InternalMergingState) {
//                windowMergingState = (InternalMergingState<K, W, IN, ACC, ACC>) windowState;
//            } else if (windowState != null) {
//                throw new IllegalStateException(
//                        "The window uses a merging assigner, but the window state is not mergeable.");
//            }

            @SuppressWarnings("unchecked") final Class<Tuple2<W, W>> typedTuple = (Class<Tuple2<W, W>>) (Class<?>) Tuple2.class;

//            final TupleSerializer<Tuple2<W, W>> tupleSerializer =
//                    new TupleSerializer<>(
//                            typedTuple, new TypeSerializer[] {windowSerializer, windowSerializer});
//
//            final ListStateDescriptor<Tuple2<W, W>> mergingSetsStateDescriptor =
//                    new ListStateDescriptor<>("merging-window-set", tupleSerializer);
//
//            // get the state that stores the merging sets
//            mergingSetsState =
//                    (InternalListState<K, VoidNamespace, Tuple2<W, W>>)
//                            getOrCreateKeyedState(
//                                    VoidNamespaceSerializer.INSTANCE, mergingSetsStateDescriptor);
//            mergingSetsState.setCurrentNamespace(VoidNamespace.INSTANCE);
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        timestampedCollector = null;
        triggerContext = null;
        processContext = null;
        windowAssignerContext = null;
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        final Collection<W> elementWindows =
                windowAssigner.assignWindows(
                        element.getValue(), element.getTimestamp(), windowAssignerContext);

        // if element is handled by none of assigned elementWindows
        boolean isSkippedElement = true;

        final K key = null; //this.<K>getKeyedStateBackend().getCurrentKey();

        if (windowAssigner instanceof MergingWindowAssigner) {
            MergingWindowSet<W> mergingWindows = getMergingWindowSet();

            for (W window : elementWindows) {

                // adding the new window might result in a merge, in that case the actualWindow
                // is the merged window and we work with that. If we don't merge then
                // actualWindow == window
                W actualWindow =
                        mergingWindows.addWindow(
                                window,
                                new MergingWindowSet.MergeFunction<W>() {
                                    @Override
                                    public void merge(
                                            W mergeResult,
                                            Collection<W> mergedWindows,
                                            W stateWindowResult,
                                            Collection<W> mergedStateWindows)
                                            throws Exception {

                                        if ((windowAssigner.isEventTime()
                                                && mergeResult.maxTimestamp() + allowedLateness
                                                <= internalTimerService
                                                .currentWatermark())) {
                                            throw new UnsupportedOperationException(
                                                    "The end timestamp of an "
                                                            + "event-time window cannot become earlier than the current watermark "
                                                            + "by merging. Current watermark: "
                                                            + internalTimerService
                                                            .currentWatermark()
                                                            + " window: "
                                                            + mergeResult);
                                        } else if (!windowAssigner.isEventTime()) {
                                            long currentProcessingTime =
                                                    internalTimerService.currentProcessingTime();
                                            if (mergeResult.maxTimestamp()
                                                    <= currentProcessingTime) {
                                                throw new UnsupportedOperationException(
                                                        "The end timestamp of a "
                                                                + "processing-time window cannot become earlier than the current processing time "
                                                                + "by merging. Current processing time: "
                                                                + currentProcessingTime
                                                                + " window: "
                                                                + mergeResult);
                                            }
                                        }

                                        triggerContext.key = key;
                                        triggerContext.window = mergeResult;

                                        triggerContext.onMerge(mergedWindows);

                                        for (W m : mergedWindows) {
                                            triggerContext.window = m;
                                            triggerContext.clear();
                                            deleteCleanupTimer(m);
                                        }

                                        // merge the merged state windows into the newly resulting
                                        // state window
//                                        windowMergingState.mergeNamespaces(
//                                                stateWindowResult, mergedStateWindows);
                                    }
                                });

                // drop if the window is already late
                if (isWindowLate(actualWindow)) {
                    mergingWindows.retireWindow(actualWindow);
                    continue;
                }
                isSkippedElement = false;

                W stateWindow = mergingWindows.getStateWindow(actualWindow);
                if (stateWindow == null) {
                    throw new IllegalStateException(
                            "Window " + window + " is not in in-flight window set.");
                }

//                windowState.setCurrentNamespace(stateWindow);
//                windowState.add(element.getValue());

                triggerContext.key = key;
                triggerContext.window = actualWindow;

                TriggerResult triggerResult = triggerContext.onElement(element);

                if (triggerResult.isFire()) {
                    ACC contents = null;//windowState.get();
                    if (contents != null) {
                        emitWindowContents(actualWindow, contents);
                    }
                }

                if (triggerResult.isPurge()) {
                    //windowState.clear();
                }
                registerCleanupTimer(actualWindow);
            }

            // need to make sure to update the merging state in state
            mergingWindows.persist();
        } else {
            for (W window : elementWindows) {

                // drop if the window is already late
                if (isWindowLate(window)) {
                    continue;
                }
                isSkippedElement = false;

//                windowState.setCurrentNamespace(window);
//                windowState.add(element.getValue());

                triggerContext.key = key;
                triggerContext.window = window;

                TriggerResult triggerResult = triggerContext.onElement(element);

                if (triggerResult.isFire()) {
                    ACC contents = null; //windowState.get();
                    if (contents != null) {
                        emitWindowContents(window, contents);
                    }
                }

                if (triggerResult.isPurge()) {
                    //windowState.clear();
                }
                registerCleanupTimer(window);
            }
        }

        // side output input event if
        // element not handled by any window
        // late arriving tag has been set
        // windowAssigner is event time and current timestamp + allowed lateness no less than
        // element timestamp
        if (isSkippedElement && isElementLate(element)) {
            if (lateDataOutputTag != null) {
                sideOutput(element);
            } else {
                //this.numLateRecordsDropped.inc();
            }
        }
    }

    @Override
    public void onEventTime(InternalTimer<K, W> timer) throws Exception {
        triggerContext.key = timer.getKey();
        triggerContext.window = timer.getNamespace();

        MergingWindowSet<W> mergingWindows;

        if (windowAssigner instanceof MergingWindowAssigner) {
            mergingWindows = getMergingWindowSet();
            W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
            if (stateWindow == null) {
                // Timer firing for non-existent window, this can only happen if a
                // trigger did not clean up timers. We have already cleared the merging
                // window and therefore the Trigger state, however, so nothing to do.
                return;
            } else {
                //windowState.setCurrentNamespace(stateWindow);
            }
        } else {
            //windowState.setCurrentNamespace(triggerContext.window);
            mergingWindows = null;
        }

        TriggerResult triggerResult = triggerContext.onEventTime(timer.getTimestamp());

        if (triggerResult.isFire()) {
            ACC contents = null; //windowState.get();
            if (contents != null) {
                emitWindowContents(triggerContext.window, contents);
            }
        }

        if (triggerResult.isPurge()) {
            //windowState.clear();
        }

        if (windowAssigner.isEventTime()
                && isCleanupTime(triggerContext.window, timer.getTimestamp())) {
            //clearAllState(triggerContext.window, windowState, mergingWindows);
        }

        if (mergingWindows != null) {
            // need to make sure to update the merging state in state
            mergingWindows.persist();
        }
    }

    @Override
    public void onProcessingTime(InternalTimer<K, W> timer) throws Exception {
        triggerContext.key = timer.getKey();
        triggerContext.window = timer.getNamespace();

        MergingWindowSet<W> mergingWindows;

        if (windowAssigner instanceof MergingWindowAssigner) {
            mergingWindows = getMergingWindowSet();
            W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
            if (stateWindow == null) {
                // Timer firing for non-existent window, this can only happen if a
                // trigger did not clean up timers. We have already cleared the merging
                // window and therefore the Trigger state, however, so nothing to do.
                return;
            } else {
                //windowState.setCurrentNamespace(stateWindow);
            }
        } else {
            //windowState.setCurrentNamespace(triggerContext.window);
            mergingWindows = null;
        }

        TriggerResult triggerResult = triggerContext.onProcessingTime(timer.getTimestamp());

        if (triggerResult.isFire()) {
            ACC contents = null; //windowState.get();
            if (contents != null) {
                emitWindowContents(triggerContext.window, contents);
            }
        }

        if (triggerResult.isPurge()) {
            //windowState.clear();
        }

        if (!windowAssigner.isEventTime()
                && isCleanupTime(triggerContext.window, timer.getTimestamp())) {
            // clearAllState(triggerContext.window, windowState, mergingWindows);
        }

        if (mergingWindows != null) {
            // need to make sure to update the merging state in state
            mergingWindows.persist();
        }
    }

//    @Override
//    public OperatorAttributes getOperatorAttributes() {
//        boolean isOutputOnlyAfterEndOfStream =
//                windowAssigner instanceof GlobalWindows
//                        && trigger instanceof GlobalWindows.EndOfStreamTrigger;
//        return new OperatorAttributesBuilder()
//                .setOutputOnlyAfterEndOfStream(isOutputOnlyAfterEndOfStream)
//                .build();
//    }

    /**
     * Drops all state for the given window and calls {@link Trigger#clear(Window,
     * Trigger.TriggerContext)}.
     *
     * <p>The caller must ensure that the correct key is set in the state backend and the
     * triggerContext object.
     */
//    private void clearAllState(
//            W window, AppendingState<IN, ACC> windowState, MergingWindowSet<W> mergingWindows)
//            throws Exception {
//        windowState.clear();
//        triggerContext.clear();
//        processContext.window = window;
//        processContext.clear();
//        if (mergingWindows != null) {
//            mergingWindows.retireWindow(window);
//            mergingWindows.persist();
//        }
//    }

    /**
     * Emits the contents of the given window using the {@link InternalWindowFunction}.
     */
    @SuppressWarnings("unchecked")
    private void emitWindowContents(W window, ACC contents) throws Exception {
        timestampedCollector.setAbsoluteTimestamp(window.maxTimestamp());
        processContext.window = window;
        userFunction.process(
                triggerContext.key, window, processContext, contents, timestampedCollector);
    }

    /**
     * Write skipped late arriving element to SideOutput.
     *
     * @param element skipped late arriving element to side output
     */
    protected void sideOutput(StreamRecord<IN> element) {
        output.collect(lateDataOutputTag, element);
    }

    /**
     * Retrieves the {@link MergingWindowSet} for the currently active key. The caller must ensure
     * that the correct key is set in the state backend.
     *
     * <p>The caller must also ensure to properly persist changes to state using {@link
     * MergingWindowSet#persist()}.
     */
    protected MergingWindowSet<W> getMergingWindowSet() throws Exception {
        @SuppressWarnings("unchecked")
        MergingWindowAssigner<? super IN, W> mergingAssigner =
                (MergingWindowAssigner<? super IN, W>) windowAssigner;
        return new MergingWindowSet<>(mergingAssigner
                //, mergingSetsState
        );
    }

    /**
     * Returns {@code true} if the watermark is after the end timestamp plus the allowed lateness of
     * the given window.
     */
    protected boolean isWindowLate(W window) {
        return (windowAssigner.isEventTime()
                && (cleanupTime(window) <= internalTimerService.currentWatermark()));
    }

    /**
     * Decide if a record is currently late, based on current watermark and allowed lateness.
     *
     * @param element The element to check
     * @return The element for which should be considered when sideoutputs
     */
    protected boolean isElementLate(StreamRecord<IN> element) {
        return (windowAssigner.isEventTime())
                && (element.getTimestamp() + allowedLateness
                <= internalTimerService.currentWatermark());
    }

    /**
     * Registers a timer to cleanup the content of the window.
     *
     * @param window the window whose state to discard
     */
    protected void registerCleanupTimer(W window) {
        long cleanupTime = cleanupTime(window);
        if (cleanupTime == Long.MAX_VALUE) {
            // don't set a GC timer for "end of time"
            return;
        }

        if (windowAssigner.isEventTime()) {
            triggerContext.registerEventTimeTimer(cleanupTime);
        } else {
            triggerContext.registerProcessingTimeTimer(cleanupTime);
        }
    }

    /**
     * Deletes the cleanup timer set for the contents of the provided window.
     *
     * @param window the window whose state to discard
     */
    protected void deleteCleanupTimer(W window) {
        long cleanupTime = cleanupTime(window);
        if (cleanupTime == Long.MAX_VALUE) {
            // no need to clean up because we didn't set one
            return;
        }
        if (windowAssigner.isEventTime()) {
            triggerContext.deleteEventTimeTimer(cleanupTime);
        } else {
            triggerContext.deleteProcessingTimeTimer(cleanupTime);
        }
    }

    /**
     * Returns the cleanup time for a window, which is {@code window.maxTimestamp +
     * allowedLateness}. In case this leads to a value greater than {@link Long#MAX_VALUE} then a
     * cleanup time of {@link Long#MAX_VALUE} is returned.
     *
     * @param window the window whose cleanup time we are computing.
     */
    private long cleanupTime(W window) {
        if (windowAssigner.isEventTime()) {
            long cleanupTime = window.maxTimestamp() + allowedLateness;
            return cleanupTime >= window.maxTimestamp() ? cleanupTime : Long.MAX_VALUE;
        } else {
            return window.maxTimestamp();
        }
    }

    /**
     * Returns {@code true} if the given time is the cleanup time for the given window.
     */
    protected final boolean isCleanupTime(W window, long time) {
        return time == cleanupTime(window);
    }

    /**
     * Base class for per-window {@link KeyedStateStore KeyedStateStores}. Used to allow per-window
     * state access for {@link
     * org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction}.
     */
    public abstract class AbstractPerWindowStateStore { //extends DefaultKeyedStateStore {

        // we have this in the base class even though it's not used in MergingKeyStore so that
        // we can always set it and ignore what actual implementation we have
        protected W window;

//        public AbstractPerWindowStateStore(
//                KeyedStateBackend<?> keyedStateBackend, ExecutionConfig executionConfig) {
//            super(
//                    keyedStateBackend,
//                    new SerializerFactory() {
//                        @Override
//                        public <T> TypeSerializer<T> createSerializer(
//                                TypeInformation<T> typeInformation) {
//                            return typeInformation.createSerializer(
//                                    executionConfig.getSerializerConfig());
//                        }
//                    });
//        }
    }

    /**
     * Special {@link AbstractPerWindowStateStore} that doesn't allow access to per-window state.
     */
    public class MergingWindowStateStore extends AbstractPerWindowStateStore {

//        public MergingWindowStateStore(
//                KeyedStateBackend<?> keyedStateBackend, ExecutionConfig executionConfig) {
//            super(keyedStateBackend, executionConfig);
//        }

//        @Override
//        public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
//            throw new UnsupportedOperationException(
//                    "Per-window state is not allowed when using merging windows.");
//        }
//
//        @Override
//        public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
//            throw new UnsupportedOperationException(
//                    "Per-window state is not allowed when using merging windows.");
//        }
//
//        @Override
//        public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
//            throw new UnsupportedOperationException(
//                    "Per-window state is not allowed when using merging windows.");
//        }
//
//        @Override
//        public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
//                AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
//            throw new UnsupportedOperationException(
//                    "Per-window state is not allowed when using merging windows.");
//        }
//
//        @Override
//        public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
//            throw new UnsupportedOperationException(
//                    "Per-window state is not allowed when using merging windows.");
//        }
    }

    /**
     * Regular per-window state store for use with {@link
     * org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction}.
     */
    public class PerWindowStateStore extends AbstractPerWindowStateStore {

//        public PerWindowStateStore(
//                KeyedStateBackend<?> keyedStateBackend, ExecutionConfig executionConfig) {
//            super(keyedStateBackend, executionConfig);
//        }
//
//        @Override
//        protected <S extends State> S getPartitionedState(StateDescriptor<S, ?> stateDescriptor)
//                throws Exception {
//            return keyedStateBackend.getPartitionedState(window, windowSerializer, stateDescriptor);
//        }
    }

    /**
     * A utility class for handling {@code ProcessWindowFunction} invocations. This can be reused by
     * setting the {@code key} and {@code window} fields. No internal state must be kept in the
     * {@code WindowContext}.
     */
    public class WindowContext implements InternalWindowFunction.InternalWindowContext {
        protected W window;

        protected AbstractPerWindowStateStore windowState;

        public WindowContext(W window) {
            this.window = window;
            this.windowState = null;
//                    windowAssigner instanceof MergingWindowAssigner
//                            ? new MergingWindowStateStore(
//                            getKeyedStateBackend(), getExecutionConfig())
//                            : new PerWindowStateStore(getKeyedStateBackend(), getExecutionConfig());
        }

        @Override
        public String toString() {
            return "WindowContext{Window = " + window.toString() + "}";
        }

        public void clear() throws Exception {
            userFunction.clear(window, this);
        }

        @Override
        public long currentProcessingTime() {
            return internalTimerService.currentProcessingTime();
        }

        @Override
        public long currentWatermark() {
            return internalTimerService.currentWatermark();
        }

        @Override
        public KeyedStateStore windowState() {
            this.windowState.window = this.window;
            return null; //this.windowState;
        }

        @Override
        public KeyedStateStore globalState() {
            return null; //WindowOperator.this.getKeyedStateStore();
        }

        @Override
        public <X> void output(OutputTag<X> outputTag, X value) {
            if (outputTag == null) {
                throw new IllegalArgumentException("OutputTag must not be null.");
            }
            output.collect(outputTag, new StreamRecord<>(value, window.maxTimestamp()));
        }
    }

    /**
     * {@code Context} is a utility for handling {@code Trigger} invocations. It can be reused by
     * setting the {@code key} and {@code window} fields. No internal state must be kept in the
     * {@code Context}
     */
    public class Context implements Trigger.OnMergeContext {
        protected K key;
        protected W window;

        protected Collection<W> mergedWindows;

        public Context(K key, W window) {
            this.key = key;
            this.window = window;
        }

//        @Override
//        public MetricGroup getMetricGroup() {
//            return WindowOperator.this.getMetricGroup();
//        }

        public long getCurrentWatermark() {
            return internalTimerService.currentWatermark();
        }

//        @Override
//        public <S extends Serializable> ValueState<S> getKeyValueState(
//                String name, Class<S> stateType, S defaultState) {
//            checkNotNull(stateType, "The state type class must not be null");
//
//            TypeInformation<S> typeInfo;
//            try {
//                typeInfo = TypeExtractor.getForClass(stateType);
//            } catch (Exception e) {
//                throw new RuntimeException(
//                        "Cannot analyze type '"
//                                + stateType.getName()
//                                + "' from the class alone, due to generic type parameters. "
//                                + "Please specify the TypeInformation directly.",
//                        e);
//            }
//
//            return getKeyValueState(name, typeInfo, defaultState);
//        }

//        @Override
//        public <S extends Serializable> ValueState<S> getKeyValueState(
//                String name, TypeInformation<S> stateType, S defaultState) {
//
//            checkNotNull(name, "The name of the state must not be null");
//            checkNotNull(stateType, "The state type information must not be null");
//
//            ValueStateDescriptor<S> stateDesc =
//                    new ValueStateDescriptor<>(
//                            name,
//                            stateType.createSerializer(getExecutionConfig().getSerializerConfig()),
//                            defaultState);
//            return getPartitionedState(stateDesc);
//        }
//
//        @SuppressWarnings("unchecked")
//        public <S extends State> S getPartitionedState(StateDescriptor<S, ?> stateDescriptor) {
//            try {
//                return WindowOperator.this.getPartitionedState(
//                        window, windowSerializer, stateDescriptor);
//            } catch (Exception e) {
//                throw new RuntimeException("Could not retrieve state", e);
//            }
//        }
//
//        @Override
//        public <S extends MergingState<?, ?>> void mergePartitionedState(
//                StateDescriptor<S, ?> stateDescriptor) {
//            if (mergedWindows != null && mergedWindows.size() > 0) {
//                try {
//                    S rawState =
//                            getKeyedStateBackend()
//                                    .getOrCreateKeyedState(windowSerializer, stateDescriptor);
//
//                    if (rawState instanceof InternalMergingState) {
//                        @SuppressWarnings("unchecked")
//                        InternalMergingState<K, W, ?, ?, ?> mergingState =
//                                (InternalMergingState<K, W, ?, ?, ?>) rawState;
//                        mergingState.mergeNamespaces(window, mergedWindows);
//                    } else {
//                        throw new IllegalArgumentException(
//                                "The given state descriptor does not refer to a mergeable state (MergingState)");
//                    }
//                } catch (Exception e) {
//                    throw new RuntimeException("Error while merging state.", e);
//                }
//            }
//        }

        @Override
        public long getCurrentProcessingTime() {
            return internalTimerService.currentProcessingTime();
        }

        @Override
        public void registerProcessingTimeTimer(long time) {
            internalTimerService.registerProcessingTimeTimer(window, time);
        }

        @Override
        public void registerEventTimeTimer(long time) {
            internalTimerService.registerEventTimeTimer(window, time);
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            internalTimerService.deleteProcessingTimeTimer(window, time);
        }

        @Override
        public void deleteEventTimeTimer(long time) {
            internalTimerService.deleteEventTimeTimer(window, time);
        }

        public TriggerResult onElement(StreamRecord<IN> element) throws Exception {
            return trigger.onElement(element.getValue(), element.getTimestamp(), window, this);
        }

        public TriggerResult onProcessingTime(long time) throws Exception {
            return trigger.onProcessingTime(time, window, this);
        }

        public TriggerResult onEventTime(long time) throws Exception {
            return trigger.onEventTime(time, window, this);
        }

        public void onMerge(Collection<W> mergedWindows) throws Exception {
            this.mergedWindows = mergedWindows;
            trigger.onMerge(window, this);
        }

        public void clear() throws Exception {
            trigger.clear(window, this);
        }

        @Override
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            return null;
        }

        @Override
        public String toString() {
            return "Context{" + "key=" + key + ", window=" + window + '}';
        }
    }

    /**
     * Internal class for keeping track of in-flight timers.
     */
    protected static class Timer<K, W extends Window> implements Comparable<Timer<K, W>> {
        protected long timestamp;
        protected K key;
        protected W window;

        public Timer(long timestamp, K key, W window) {
            this.timestamp = timestamp;
            this.key = key;
            this.window = window;
        }

        @Override
        public int compareTo(Timer<K, W> o) {
            return Long.compare(this.timestamp, o.timestamp);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Timer<?, ?> timer = (Timer<?, ?>) o;

            return timestamp == timer.timestamp
                    && key.equals(timer.key)
                    && window.equals(timer.window);
        }

        @Override
        public int hashCode() {
            int result = (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + key.hashCode();
            result = 31 * result + window.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Timer{"
                    + "timestamp="
                    + timestamp
                    + ", key="
                    + key
                    + ", window="
                    + window
                    + '}';
        }
    }

    // ------------------------------------------------------------------------
    // Getters for testing
    // ------------------------------------------------------------------------

    @VisibleForTesting
    public Trigger<? super IN, ? super W> getTrigger() {
        return trigger;
    }

    @VisibleForTesting
    public KeySelector<IN, K> getKeySelector() {
        return keySelector;
    }

    @VisibleForTesting
    public WindowAssigner<? super IN, W> getWindowAssigner() {
        return windowAssigner;
    }

//    @VisibleForTesting
//    public StateDescriptor<? extends AppendingState<IN, ACC>, ?> getStateDescriptor() {
//        return windowStateDescriptor;
//    }
}
