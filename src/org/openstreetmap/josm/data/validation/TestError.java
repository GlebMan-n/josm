// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.validation;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.data.osm.event.NodeMovedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesAddedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent;
import org.openstreetmap.josm.data.osm.event.RelationMembersChangedEvent;
import org.openstreetmap.josm.data.osm.event.TagsChangedEvent;
import org.openstreetmap.josm.data.osm.event.WayNodesChangedEvent;
import org.openstreetmap.josm.data.validation.util.MultipleNameVisitor;
import org.openstreetmap.josm.tools.AlphanumComparator;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.I18n;

/**
 * Validation error
 * @since 3669
 */
public class TestError implements Comparable<TestError>, DataSetListener {
    /** is this error on the ignore list */
    private boolean ignored;
    /** Severity */
    private Severity severity;
    /** The error message */
    private String message;
    /** Deeper error description */
    private final String description;
    private final String descriptionEn;
    /** The affected primitives */
    private Collection<? extends OsmPrimitive> primitives;
    /** The primitives or way segments to be highlighted */
    private final Collection<?> highlighted;
    /** The tester that raised this error */
    private Test tester;
    /** Internal code used by testers to classify errors */
    private final int code;
    /** If this error is selected */
    private boolean selected;
    /** Supplying a command to fix the error */
    private final Supplier<Command> fixingCommand;

    /**
     * A builder for a {@code TestError}.
     * @since 11129
     */
    public static final class Builder {
        private final Test tester;
        private final Severity severity;
        private final int code;
        private String message;
        private String description;
        private String descriptionEn;
        private Collection<? extends OsmPrimitive> primitives;
        private Collection<?> highlighted;
        private Supplier<Command> fixingCommand;

        private Builder(Test tester, Severity severity, int code) {
            this.tester = tester;
            this.severity = severity;
            this.code = code;
        }

        /**
         * Sets the error message.
         *
         * @param message The error message
         * @return {@code this}
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Sets the error message.
         *
         * @param message       The the message of this error group
         * @param description   The translated description of this error
         * @param descriptionEn The English description (for ignoring errors)
         * @return {@code this}
         */
        public Builder messageWithManuallyTranslatedDescription(String message, String description, String descriptionEn) {
            this.message = message;
            this.description = description;
            this.descriptionEn = descriptionEn;
            return this;
        }

        /**
         * Sets the error message.
         *
         * @param message The the message of this error group
         * @param marktrDescription The {@linkplain I18n#marktr prepared for i18n} description of this error
         * @param args The description arguments to be applied in {@link I18n#tr(String, Object...)}
         * @return {@code this}
         */
        public Builder message(String message, String marktrDescription, Object... args) {
            this.message = message;
            this.description = I18n.tr(marktrDescription, args);
            this.descriptionEn = new MessageFormat(marktrDescription, Locale.ENGLISH).format(args);
            return this;
        }

        /**
         * Sets the primitives affected by this error.
         *
         * @param primitives the primitives affected by this error
         * @return {@code this}
         */
        public Builder primitives(OsmPrimitive... primitives) {
            return primitives(Arrays.asList(primitives));
        }

        /**
         * Sets the primitives affected by this error.
         *
         * @param primitives the primitives affected by this error
         * @return {@code this}
         */
        public Builder primitives(Collection<? extends OsmPrimitive> primitives) {
            CheckParameterUtil.ensureThat(this.primitives == null, "primitives already set");
            CheckParameterUtil.ensureParameterNotNull(primitives, "primitives");
            this.primitives = primitives;
            if (this.highlighted == null) {
                this.highlighted = primitives;
            }
            return this;
        }

        /**
         * Sets the primitives to highlight when selecting this error.
         *
         * @param highlighted the primitives to highlight
         * @return {@code this}
         * @see ValidatorVisitor#visit(OsmPrimitive)
         */
        public Builder highlight(OsmPrimitive... highlighted) {
            return highlight(Arrays.asList(highlighted));
        }

        /**
         * Sets the primitives to highlight when selecting this error.
         *
         * @param highlighted the primitives to highlight
         * @return {@code this}
         * @see ValidatorVisitor#visit(OsmPrimitive)
         */
        public Builder highlight(Collection<? extends OsmPrimitive> highlighted) {
            CheckParameterUtil.ensureParameterNotNull(highlighted, "highlighted");
            this.highlighted = highlighted;
            return this;
        }

        /**
         * Sets the way segments to highlight when selecting this error.
         *
         * @param highlighted the way segments to highlight
         * @return {@code this}
         * @see ValidatorVisitor#visit(WaySegment)
         */
        public Builder highlightWaySegments(Collection<WaySegment> highlighted) {
            CheckParameterUtil.ensureParameterNotNull(highlighted, "highlighted");
            this.highlighted = highlighted;
            return this;
        }

        /**
         * Sets the node pairs to highlight when selecting this error.
         *
         * @param highlighted the node pairs to highlight
         * @return {@code this}
         * @see ValidatorVisitor#visit(List)
         */
        public Builder highlightNodePairs(Collection<List<Node>> highlighted) {
            CheckParameterUtil.ensureParameterNotNull(highlighted, "highlighted");
            this.highlighted = highlighted;
            return this;
        }

        /**
         * Sets a supplier to obtain a command to fix the error.
         *
         * @param fixingCommand the fix supplier
         * @return {@code this}
         */
        public Builder fix(Supplier<Command> fixingCommand) {
            CheckParameterUtil.ensureThat(this.fixingCommand == null, "fixingCommand already set");
            this.fixingCommand = fixingCommand;
            return this;
        }

        /**
         * Returns a new test error with the specified values
         *
         * @return a new test error with the specified values
         * @throws IllegalArgumentException when {@link #message} or {@link #primitives} is null/empty.
         */
        public TestError build() {
            CheckParameterUtil.ensureParameterNotNull(message, "message not set");
            CheckParameterUtil.ensureParameterNotNull(primitives, "primitives not set");
            CheckParameterUtil.ensureThat(!primitives.isEmpty(), "primitives is empty");
            if (this.highlighted == null) {
                this.highlighted = Collections.emptySet();
            }
            return new TestError(this);
        }
    }

    /**
     * Starts building a new {@code TestError}
     * @param tester The tester
     * @param severity The severity of this error
     * @param code The test error reference code
     * @return a new test builder
     * @since 11129
     */
    public static Builder builder(Test tester, Severity severity, int code) {
        return new Builder(tester, severity, code);
    }

    private TestError(Builder builder) {
        this.tester = builder.tester;
        this.severity = builder.severity;
        this.message = builder.message;
        this.description = builder.description;
        this.descriptionEn = builder.descriptionEn;
        this.primitives = builder.primitives;
        this.highlighted = builder.highlighted;
        this.code = builder.code;
        this.fixingCommand = builder.fixingCommand;
    }

    /**
     * Constructs a new {@code TestError}.
     * @param tester The tester
     * @param severity The severity of this error
     * @param message The error message
     * @param description The translated description
     * @param descriptionEn The English description
     * @param code The test error reference code
     * @param primitives The affected primitives
     * @param highlighted OSM primitives to highlight
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public TestError(Test tester, Severity severity, String message, String description, String descriptionEn,
            int code, Collection<? extends OsmPrimitive> primitives, Collection<?> highlighted) {
        this.tester = tester;
        this.severity = severity;
        this.message = message;
        this.description = description;
        this.descriptionEn = descriptionEn;
        this.primitives = primitives;
        this.highlighted = highlighted;
        this.code = code;
        this.fixingCommand = null;
    }

    /**
     * Constructs a new {@code TestError} without description.
     * @param tester The tester
     * @param severity The severity of this error
     * @param message The error message
     * @param code The test error reference code
     * @param primitives The affected primitives
     * @param highlighted OSM primitives to highlight
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public TestError(Test tester, Severity severity, String message, int code, Collection<? extends OsmPrimitive> primitives,
            Collection<?> highlighted) {
        this(tester, severity, message, null, null, code, primitives, highlighted);
    }

    /**
     * Constructs a new {@code TestError}.
     * @param tester The tester
     * @param severity The severity of this error
     * @param message The error message
     * @param description The translated description
     * @param descriptionEn The English description
     * @param code The test error reference code
     * @param primitives The affected primitives
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public TestError(Test tester, Severity severity, String message, String description, String descriptionEn,
            int code, Collection<? extends OsmPrimitive> primitives) {
        this(tester, severity, message, description, descriptionEn, code, primitives, primitives);
    }

    /**
     * Constructs a new {@code TestError} without description.
     * @param tester The tester
     * @param severity The severity of this error
     * @param message The error message
     * @param code The test error reference code
     * @param primitives The affected primitives
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public TestError(Test tester, Severity severity, String message, int code, Collection<? extends OsmPrimitive> primitives) {
        this(tester, severity, message, null, null, code, primitives, primitives);
    }

    /**
     * Constructs a new {@code TestError} without description, for a single primitive.
     * @param tester The tester
     * @param severity The severity of this error
     * @param message The error message
     * @param code The test error reference code
     * @param primitive The affected primitive
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public TestError(Test tester, Severity severity, String message, int code, OsmPrimitive primitive) {
        this(tester, severity, message, null, null, code, Collections.singletonList(primitive), Collections
                .singletonList(primitive));
    }

    /**
     * Constructs a new {@code TestError} for a single primitive.
     * @param tester The tester
     * @param severity The severity of this error
     * @param message The error message
     * @param description The translated description
     * @param descriptionEn The English description
     * @param code The test error reference code
     * @param primitive The affected primitive
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public TestError(Test tester, Severity severity, String message, String description, String descriptionEn,
            int code, OsmPrimitive primitive) {
        this(tester, severity, message, description, descriptionEn, code, Collections.singletonList(primitive));
    }

    /**
     * Gets the error message
     * @return the error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the error message
     * @return the error description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the error message
     * @param message The error message
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the list of primitives affected by this error
     * @return the list of primitives affected by this error
     */
    public Collection<? extends OsmPrimitive> getPrimitives() {
        return primitives;
    }

    /**
     * Gets the list of primitives affected by this error and are selectable
     * @return the list of selectable primitives affected by this error
     */
    public Collection<? extends OsmPrimitive> getSelectablePrimitives() {
        List<OsmPrimitive> selectablePrimitives = new ArrayList<>(primitives.size());
        for (OsmPrimitive o : primitives) {
            if (o.isSelectable()) {
                selectablePrimitives.add(o);
            }
        }
        return selectablePrimitives;
    }

    /**
     * Sets the list of primitives affected by this error
     * @param primitives the list of primitives affected by this error*
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public void setPrimitives(List<? extends OsmPrimitive> primitives) {
        this.primitives = primitives;
    }

    /**
     * Gets the severity of this error
     * @return the severity of this error
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Sets the severity of this error
     * @param severity the severity of this error
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    /**
     * Returns the ignore state for this error.
     * @return the ignore state for this error
     */
    public String getIgnoreState() {
        Collection<String> strings = new TreeSet<>();
        StringBuilder ignorestring = new StringBuilder(getIgnoreSubGroup());
        for (OsmPrimitive o : primitives) {
            // ignore data not yet uploaded
            if (o.isNew())
                return null;
            String type = "u";
            if (o instanceof Way) {
                type = "w";
            } else if (o instanceof Relation) {
                type = "r";
            } else if (o instanceof Node) {
                type = "n";
            }
            strings.add(type + '_' + o.getId());
        }
        for (String o : strings) {
            ignorestring.append(':').append(o);
        }
        return ignorestring.toString();
    }

    public String getIgnoreSubGroup() {
        String ignorestring = getIgnoreGroup();
        if (descriptionEn != null) {
            ignorestring += '_' + descriptionEn;
        }
        return ignorestring;
    }

    public String getIgnoreGroup() {
        return Integer.toString(code);
    }

    public void setIgnored(boolean state) {
        ignored = state;
    }

    public boolean isIgnored() {
        return ignored;
    }

    /**
     * Gets the tester that raised this error
     * @return the tester that raised this error
     */
    public Test getTester() {
        return tester;
    }

    /**
     * Set the tester that raised the error.
     * @param tester te tester
     * @deprecated Use {@link #builder} instead. Will be removed in 2016-12.
     */
    @Deprecated
    public void setTester(Test tester) {
        this.tester = tester;
    }

    /**
     * Gets the code
     * @return the code
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns true if the error can be fixed automatically
     *
     * @return true if the error can be fixed
     */
    public boolean isFixable() {
        return fixingCommand != null || ((tester != null) && tester.isFixable(this));
    }

    /**
     * Fixes the error with the appropriate command
     *
     * @return The command to fix the error
     */
    public Command getFix() {
        // obtain fix from the error
        final Command fix = fixingCommand != null ? fixingCommand.get() : null;
        if (fix != null) {
            return fix;
        }

        // obtain fix from the tester
        if (tester == null || !tester.isFixable(this) || primitives.isEmpty())
            return null;

        return tester.fixError(this);
    }

    /**
     * Sets the selection flag of this error
     * @param selected if this error is selected
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @SuppressWarnings("unchecked")
    public void visitHighlighted(ValidatorVisitor v) {
        for (Object o : highlighted) {
            if (o instanceof OsmPrimitive) {
                v.visit((OsmPrimitive) o);
            } else if (o instanceof WaySegment) {
                v.visit((WaySegment) o);
            } else if (o instanceof List<?>) {
                v.visit((List<Node>) o);
            }
        }
    }

    /**
     * Returns the selection flag of this error
     * @return true if this error is selected
     * @since 5671
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Returns The primitives or way segments to be highlighted
     * @return The primitives or way segments to be highlighted
     * @since 5671
     */
    public Collection<?> getHighlighted() {
        return highlighted;
    }

    @Override
    public int compareTo(TestError o) {
        if (equals(o)) return 0;

        MultipleNameVisitor v1 = new MultipleNameVisitor();
        MultipleNameVisitor v2 = new MultipleNameVisitor();

        v1.visit(getPrimitives());
        v2.visit(o.getPrimitives());
        return AlphanumComparator.getInstance().compare(v1.toString(), v2.toString());
    }

    @Override public void primitivesRemoved(PrimitivesRemovedEvent event) {
        // Remove purged primitives (fix #8639)
        try {
            primitives.removeAll(event.getPrimitives());
        } catch (UnsupportedOperationException e) {
            if (event.getPrimitives().containsAll(primitives)) {
                primitives = Collections.emptyList();
            } else {
                Main.warn(e, "Unable to remove primitives from "+this+'.');
            }
        }
    }

    @Override public void primitivesAdded(PrimitivesAddedEvent event) {
        // Do nothing
    }

    @Override public void tagsChanged(TagsChangedEvent event) {
        // Do nothing
    }

    @Override public void nodeMoved(NodeMovedEvent event) {
        // Do nothing
    }

    @Override public void wayNodesChanged(WayNodesChangedEvent event) {
        // Do nothing
    }

    @Override public void relationMembersChanged(RelationMembersChangedEvent event) {
        // Do nothing
    }

    @Override public void otherDatasetChange(AbstractDatasetChangedEvent event) {
        // Do nothing
    }

    @Override public void dataChanged(DataChangedEvent event) {
        // Do nothing
    }

    @Override
    public String toString() {
        return "TestError [tester=" + tester + ", code=" + code + ", message=" + message + ']';
    }
}
