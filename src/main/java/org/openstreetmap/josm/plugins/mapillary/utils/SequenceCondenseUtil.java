// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.utils;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.plugins.mapillary.data.mapillary.MapillaryAbstractImage;
import org.openstreetmap.josm.tools.Geometry;

/**
 * Condense sequences going along a road and (roughly) pointed in the same direction
 */
public final class SequenceCondenseUtil {
  public static final double MIN_LANE_WIDTH = 2.7; // meters, from US DOT FHWA
  public static final double MAX_LANE_WIDTH = 3.6; // meters, from US DOT FHWA

  public enum Direction {
    FORWARD(7 * Math.PI / 4, Math.PI / 4), BACKWARD(3 * Math.PI / 4, 5 * Math.PI / 4),
    LEFT(5 * Math.PI / 4, 7 * Math.PI / 4), RIGHT(Math.PI / 4, 3 * Math.PI / 4);

    private final double minAngle;
    private final double maxAngle;

    Direction(double minAngle, double maxAngle) {
      this.minAngle = minAngle;
      this.maxAngle = maxAngle;
    }

    public double getMinAngle() {
      return minAngle;
    }
    public double getMaxAngle() {
      return maxAngle;
    }
    public boolean insideArc(double angle) {
      return ((angle > minAngle && angle < maxAngle)
          || (minAngle > maxAngle && (angle > minAngle || angle < maxAngle)));
    }
  }

  private static final Map<Way, Map<Direction, Set<MapillaryAbstractImage>>> IMAGE_MAPPING = new HashMap<>();

  private SequenceCondenseUtil() {
    // Hide the constructor
  }

  /**
   * Get the current condensed map of images
   *
   * @return The map of images to ways
   */
  public static Map<Way, Map<Direction, Set<MapillaryAbstractImage>>> getCondensedImages() {
    return IMAGE_MAPPING;
  }

  public static void parseImages(DataSet dataSet, Set<MapillaryAbstractImage> images) {
    for (MapillaryAbstractImage image : images) {
      BBox searchBox = new BBox();
      searchBox.addLatLon(image.getCoor(), 0.005);
      Collection<Way> searchWays = dataSet.searchWays(searchBox);
      Node node = new Node(image.getCoor());
      Way closest = Geometry.getClosestPrimitive(node, searchWays);
      double distance = Geometry.getDistance(node, closest);
      if (distance < getWidth(closest) / 2) {
        WaySegment closestWaySegment = Geometry.getClosestWaySegment(closest, node);
        double angle = Geometry.getSegmentAngle(closestWaySegment.getFirstNode().getEastNorth(),
            closestWaySegment.getSecondNode().getEastNorth());
        double directionOfImage = image.getMovingCa() - angle;
        for (Direction dir : Direction.values()) {
          if (dir.insideArc(directionOfImage)) {
            Map<Direction, Set<MapillaryAbstractImage>> mapping = IMAGE_MAPPING.computeIfAbsent(closest,
                k -> new EnumMap<>(Direction.class));
            Set<MapillaryAbstractImage> set = mapping.computeIfAbsent(dir, k -> new HashSet<>());
            set.add(image);
            break;
          }
        }
      }
    }
  }

  /**
   * Get the width for a way (may be assumed)
   *
   * @param way The way to get the width for
   * @return An assumed width (may come from a width key)
   */
  public static double getWidth(IWay<Node> way) {
    if (way.hasKey("width")) {
      return Double.valueOf(way.get("width"));
    }
    if (way.hasKey("lane")) {
      return Integer.valueOf(way.get("lane")) * MAX_LANE_WIDTH;
    }
    // Currently assuming 1 lane
    return 1 * MAX_LANE_WIDTH;
  }
}
