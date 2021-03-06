// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.HashSet;
import java.util.Set;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;

/**
 * Checks for self-intersecting ways.
 */
public class SelfIntersectingWay extends Test {

    protected static final int SELF_INTERSECT = 401;

    /**
     * Constructs a new {@code SelfIntersectingWay} test.
     */
    public SelfIntersectingWay() {
        super(tr("Self-intersecting ways"),
                tr("This test checks for ways " +
                        "that contain some of their nodes more than once."));
    }

    @Override
    public void visit(Way w) {
        Set<Node> nodes = new HashSet<>();
        for (int i = 1; i < w.getNodesCount() - 1; i++) {
            Node n = w.getNode(i);
            if (nodes.contains(n)) {
                errors.add(TestError.builder(this, Severity.WARNING, SELF_INTERSECT)
                        .message(tr("Self-intersecting ways"))
                        .primitives(w)
                        .highlight(n)
                        .build());
                break;
            } else {
                nodes.add(n);
            }
        }
    }
}
